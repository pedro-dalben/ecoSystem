package com.pedrodalben.ecosystem.shop.gui;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches shop catalog data from YAML/JSON files.
 * Supports GUIShop-style slot-indexed pages with item types.
 *
 * <p>
 * File structure (YAML example):
 * 
 * <pre>
 * title: "Building Blocks"
 * currency: "money"
 * rows: 6
 * pages:
 *   Page0:
 *     "0":
 *       type: SHOP
 *       id: "minecraft:cobblestone"
 *       displayName: "Cobblestone"
 *       buyPrice: 5
 *       sellPrice: 2
 *     "8":
 *       type: NAVIGATION
 *       id: "minecraft:arrow"
 *       displayName: "Next Page"
 *       navAction: NEXT_PAGE
 *   Page1:
 *     "0":
 *       type: SHOP
 *       id: "minecraft:stone"
 *       ...
 * </pre>
 */
public class ShopCatalog {
  private static final Logger LOGGER = LoggerFactory.getLogger("EcoSystem");
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  // New format: shopId → ShopDefinition
  private volatile Map<String, ShopDefinition> shops = new ConcurrentHashMap<>();

  // Legacy compat: currencyId → List of categories (for ChestShopMenu backward
  // compat)
  private volatile Map<String, List<ShopCategory>> catalogByCurrency = new ConcurrentHashMap<>();
  private volatile Map<String, ShopItem> itemIndex = new ConcurrentHashMap<>();

  // ==================== LEGACY RECORDS (backward compat for ChestShopMenu)
  // ====================

  public record ShopCategory(String name, String icon, List<ShopItem> items) {
  }

  public record ShopItem(String itemId, String displayName, long buyPrice, long sellPrice,
      boolean canBuy, boolean canSell, String categoryName) {
  }

  // ==================== LOADING ====================

  /**
   * Load all shop files from the shops/ subdirectory.
   * Supports .json files (YAML support can be added when SnakeYAML is confirmed
   * on classpath).
   *
   * @return list of validation warnings/errors for feedback in /eco admin reload
   */
  public List<String> loadFromDirectory(Path configDir) {
    Path shopsDir = configDir.resolve("shops");
    List<String> issues = new ArrayList<>();

    if (!Files.exists(shopsDir)) {
      try {
        Files.createDirectories(shopsDir);
        createDefaultShopFile(shopsDir);
      } catch (IOException e) {
        issues.add("Failed to create shops directory: " + e.getMessage());
        LOGGER.error("EcoSystem: Failed to create shops directory", e);
        return issues;
      }
    }

    Map<String, ShopDefinition> newShops = new ConcurrentHashMap<>();
    Map<String, List<ShopCategory>> newCatalog = new ConcurrentHashMap<>();
    Map<String, ShopItem> newIndex = new ConcurrentHashMap<>();

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(shopsDir,
        path -> {
          String name = path.getFileName().toString().toLowerCase();
          return name.endsWith(".json") || name.endsWith(".yml") || name.endsWith(".yaml");
        })) {
      for (Path file : stream) {
        List<String> fileIssues = loadShopFile(file, newShops, newCatalog, newIndex);
        issues.addAll(fileIssues);
      }
    } catch (IOException e) {
      issues.add("Failed to read shops directory: " + e.getMessage());
      LOGGER.error("EcoSystem: Failed to read shops directory", e);
    }

    this.shops = newShops;
    this.catalogByCurrency = newCatalog;
    this.itemIndex = newIndex;

    LOGGER.info("EcoSystem: Loaded {} shops with {} total pages",
        newShops.size(),
        newShops.values().stream().mapToInt(ShopDefinition::getPageCount).sum());

    return issues;
  }

  private List<String> loadShopFile(Path file,
      Map<String, ShopDefinition> newShops,
      Map<String, List<ShopCategory>> newCatalog,
      Map<String, ShopItem> newIndex) {

    List<String> issues = new ArrayList<>();
    String fileName = file.getFileName().toString();
    String shopId = fileName.replaceAll("\\.(json|yml|yaml)$", "");

    try (Reader reader = Files.newBufferedReader(file)) {
      JsonObject root;
      if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
        // Parse YAML via lightweight approach: read as text, convert key structures
        String content = Files.readString(file);
        root = parseYamlToJson(content, issues, fileName);
        if (root == null) {
          return issues;
        }
      } else {
        root = GSON.fromJson(reader, JsonObject.class);
      }

      if (root == null) {
        issues.add(fileName + ": Empty or invalid file");
        return issues;
      }

      // Detect format: new (pages-based) or legacy (categories-based)
      if (root.has("pages")) {
        loadNewFormat(root, shopId, fileName, newShops, newCatalog, newIndex, issues);
      } else if (root.has("categories")) {
        loadLegacyFormat(root, shopId, fileName, newShops, newCatalog, newIndex, issues);
      } else {
        issues.add(fileName + ": Missing 'pages' or 'categories' key");
      }

    } catch (Exception e) {
      issues.add(fileName + ": Parse error: " + e.getMessage());
      LOGGER.error("EcoSystem: Failed to parse shop file: {}", fileName, e);
    }

    return issues;
  }

  // ==================== NEW FORMAT (GUIShop-style) ====================

  private void loadNewFormat(JsonObject root, String shopId, String fileName,
      Map<String, ShopDefinition> newShops,
      Map<String, List<ShopCategory>> newCatalog,
      Map<String, ShopItem> newIndex,
      List<String> issues) {

    String title = root.has("title") ? root.get("title").getAsString() : shopId;
    String currencyId = root.has("currency") ? root.get("currency").getAsString() : "money";
    int rows = root.has("rows") ? root.get("rows").getAsInt() : ShopDefinition.DEFAULT_ROWS;

    if (rows < 1 || rows > 6) {
      issues.add(fileName + ": 'rows' must be 1-6, got " + rows + ". Using 6.");
      rows = ShopDefinition.DEFAULT_ROWS;
    }

    int maxSlot = rows * ShopDefinition.SLOTS_PER_ROW - 1;
    JsonObject pagesObj = root.getAsJsonObject("pages");
    List<CatalogPage> pages = new ArrayList<>();
    List<ShopItem> allItems = new ArrayList<>();

    // Sort page keys (Page0, Page1, ...)
    List<String> pageKeys = new ArrayList<>(pagesObj.keySet());
    pageKeys.sort(Comparator.comparingInt(k -> {
      try {
        return Integer.parseInt(k.replaceAll("[^0-9]", ""));
      } catch (NumberFormatException e) {
        return 999;
      }
    }));

    for (String pageKey : pageKeys) {
      JsonElement pageEl = pagesObj.get(pageKey);
      if (!pageEl.isJsonObject()) {
        issues.add(fileName + ": " + pageKey + " is not an object");
        continue;
      }

      JsonObject pageObj = pageEl.getAsJsonObject();
      Map<Integer, CatalogEntry> slots = new LinkedHashMap<>();

      for (Map.Entry<String, JsonElement> slotEntry : pageObj.entrySet()) {
        int slot;
        try {
          slot = Integer.parseInt(slotEntry.getKey());
        } catch (NumberFormatException e) {
          issues.add(fileName + ": " + pageKey + ": Invalid slot key '"
              + slotEntry.getKey() + "' (must be a number)");
          continue;
        }

        if (slot < 0 || slot > maxSlot) {
          issues.add(fileName + ": " + pageKey + ": Slot " + slot
              + " out of range (0-" + maxSlot + ")");
          continue;
        }

        JsonObject entryObj = slotEntry.getValue().getAsJsonObject();
        CatalogEntry entry = parseEntry(entryObj, currencyId, fileName, pageKey, slot, issues);
        if (entry != null) {
          slots.put(slot, entry);

          // Also build legacy index for backward compat
          if (entry.type() == CatalogEntry.EntryType.SHOP) {
            ShopItem legacyItem = new ShopItem(
                entry.itemId(), entry.displayName(),
                entry.buyPrice(), entry.sellPrice(),
                entry.allowBuy(), entry.allowSell(), title);
            allItems.add(legacyItem);
            newIndex.put(currencyId + ":" + entry.itemId(), legacyItem);
          }
        }
      }

      pages.add(new CatalogPage(slots));
    }

    ShopDefinition def = new ShopDefinition(shopId, title, currencyId, rows, pages);
    newShops.put(shopId, def);

    // Legacy compat: add as a single category
    if (!allItems.isEmpty()) {
      ShopCategory legacyCategory = new ShopCategory(title, "minecraft:chest", allItems);
      newCatalog.computeIfAbsent(currencyId, k -> new ArrayList<>()).add(legacyCategory);
    }
  }

  private CatalogEntry parseEntry(JsonObject obj, String defaultCurrency,
      String fileName, String pageKey, int slot, List<String> issues) {

    // Type (default SHOP)
    String typeStr = obj.has("type") ? obj.get("type").getAsString().toUpperCase() : "SHOP";
    CatalogEntry.EntryType type;
    try {
      type = switch (typeStr) {
        case "SHOP" -> CatalogEntry.EntryType.SHOP;
        case "DECORATIVE", "DUMMY", "BLANK" -> CatalogEntry.EntryType.DECORATIVE;
        case "NAVIGATION", "NAV" -> CatalogEntry.EntryType.NAVIGATION;
        default -> {
          issues.add(fileName + ": " + pageKey + " slot " + slot
              + ": Unknown type '" + typeStr + "', defaulting to SHOP");
          yield CatalogEntry.EntryType.SHOP;
        }
      };
    } catch (Exception e) {
      type = CatalogEntry.EntryType.SHOP;
    }

    // Item ID (required for all types)
    String itemId = obj.has("id") ? obj.get("id").getAsString() : null;
    if (itemId == null || itemId.isEmpty()) {
      // Also check 'itemId' key for backward compat
      itemId = obj.has("itemId") ? obj.get("itemId").getAsString() : null;
    }
    if (itemId == null || itemId.isEmpty()) {
      if (type != CatalogEntry.EntryType.DECORATIVE) {
        issues.add(fileName + ": " + pageKey + " slot " + slot + ": Missing 'id'");
        return null;
      }
      itemId = "minecraft:gray_stained_glass_pane"; // Default filler item
    }

    // Add minecraft namespace if missing
    if (!itemId.contains(":")) {
      itemId = "minecraft:" + itemId.toLowerCase();
    }

    String displayName = obj.has("displayName") ? obj.get("displayName").getAsString()
        : obj.has("shop-name") ? obj.get("shop-name").getAsString()
            : itemId;

    // Lore
    List<String> lore = new ArrayList<>();
    if (obj.has("lore") && obj.get("lore").isJsonArray()) {
      for (JsonElement el : obj.getAsJsonArray("lore")) {
        lore.add(el.getAsString());
      }
    } else if (obj.has("shop-lore") && obj.get("shop-lore").isJsonArray()) {
      for (JsonElement el : obj.getAsJsonArray("shop-lore")) {
        lore.add(el.getAsString());
      }
    }

    String currencyId = obj.has("currency") ? obj.get("currency").getAsString() : defaultCurrency;

    // For SHOP entries
    if (type == CatalogEntry.EntryType.SHOP) {
      long buyPrice = 0;
      boolean allowBuy = true;
      if (obj.has("buyPrice")) {
        buyPrice = obj.get("buyPrice").getAsLong();
      } else if (obj.has("buy-price")) {
        JsonElement bp = obj.get("buy-price");
        if (bp.isJsonPrimitive() && bp.getAsJsonPrimitive().isBoolean()) {
          allowBuy = bp.getAsBoolean();
          buyPrice = 0;
        } else {
          buyPrice = bp.getAsLong();
        }
      }

      long sellPrice = 0;
      boolean allowSell = true;
      if (obj.has("sellPrice")) {
        sellPrice = obj.get("sellPrice").getAsLong();
      } else if (obj.has("sell-price")) {
        JsonElement sp = obj.get("sell-price");
        if (sp.isJsonPrimitive() && sp.getAsJsonPrimitive().isBoolean()) {
          allowSell = sp.getAsBoolean();
          sellPrice = 0;
        } else {
          sellPrice = sp.getAsLong();
        }
      }

      // Override flags if explicit
      if (obj.has("allowBuy"))
        allowBuy = obj.get("allowBuy").getAsBoolean();
      if (obj.has("canBuy"))
        allowBuy = obj.get("canBuy").getAsBoolean();
      if (obj.has("allowSell"))
        allowSell = obj.get("allowSell").getAsBoolean();
      if (obj.has("canSell"))
        allowSell = obj.get("canSell").getAsBoolean();

      // If price is 0 and no explicit flag, disable that side
      if (buyPrice <= 0 && !obj.has("allowBuy") && !obj.has("canBuy"))
        allowBuy = false;
      if (sellPrice <= 0 && !obj.has("allowSell") && !obj.has("canSell"))
        allowSell = false;

      int quantity = obj.has("quantity") ? obj.get("quantity").getAsInt() : 1;

      return CatalogEntry.shop(itemId, displayName, lore, currencyId,
          buyPrice, sellPrice, quantity, allowBuy, allowSell);
    }

    // For NAVIGATION entries
    if (type == CatalogEntry.EntryType.NAVIGATION) {
      String targetShop = obj.has("target-shop") ? obj.get("target-shop").getAsString()
          : obj.has("targetShop") ? obj.get("targetShop").getAsString() : null;

      CatalogEntry.NavigationAction navAction = null;
      if (obj.has("navAction")) {
        try {
          navAction = CatalogEntry.NavigationAction.valueOf(
              obj.get("navAction").getAsString().toUpperCase());
        } catch (IllegalArgumentException e) {
          issues.add(fileName + ": " + pageKey + " slot " + slot
              + ": Unknown navAction '" + obj.get("navAction").getAsString() + "'");
        }
      }

      return CatalogEntry.navigation(itemId, displayName, targetShop, navAction);
    }

    // DECORATIVE
    return CatalogEntry.decorative(itemId, displayName);
  }

  // ==================== LEGACY FORMAT (categories-based) ====================

  private void loadLegacyFormat(JsonObject root, String shopId, String fileName,
      Map<String, ShopDefinition> newShops,
      Map<String, List<ShopCategory>> newCatalog,
      Map<String, ShopItem> newIndex,
      List<String> issues) {

    String currencyId = root.has("currency") ? root.get("currency").getAsString() : "money";
    List<ShopCategory> categories = new ArrayList<>();
    List<CatalogPage> pages = new ArrayList<>();

    if (root.has("categories")) {
      for (JsonElement catEl : root.getAsJsonArray("categories")) {
        JsonObject catObj = catEl.getAsJsonObject();
        String catName = catObj.get("name").getAsString();
        String catIcon = catObj.has("icon") ? catObj.get("icon").getAsString() : "minecraft:chest";

        List<ShopItem> items = new ArrayList<>();
        Map<Integer, CatalogEntry> pageSlots = new LinkedHashMap<>();
        int slotIndex = 0;

        if (catObj.has("items")) {
          for (JsonElement itemEl : catObj.getAsJsonArray("items")) {
            JsonObject itemObj = itemEl.getAsJsonObject();
            String itemId = itemObj.get("itemId").getAsString();
            String displayName = itemObj.has("displayName")
                ? itemObj.get("displayName").getAsString()
                : itemId;
            long buyPrice = itemObj.has("buyPrice") ? itemObj.get("buyPrice").getAsLong() : 0;
            long sellPrice = itemObj.has("sellPrice") ? itemObj.get("sellPrice").getAsLong() : 0;
            boolean canBuy = !itemObj.has("canBuy") || itemObj.get("canBuy").getAsBoolean();
            boolean canSell = !itemObj.has("canSell") || itemObj.get("canSell").getAsBoolean();

            ShopItem item = new ShopItem(itemId, displayName, buyPrice, sellPrice, canBuy, canSell,
                catName);
            items.add(item);
            newIndex.put(currencyId + ":" + itemId, item);

            // Also build new-format entry
            CatalogEntry entry = CatalogEntry.shop(itemId, displayName, List.of(),
                currencyId, buyPrice, sellPrice, 1, canBuy, canSell);
            pageSlots.put(slotIndex++, entry);
          }
        }
        categories.add(new ShopCategory(catName, catIcon, items));

        if (!pageSlots.isEmpty()) {
          pages.add(new CatalogPage(pageSlots));
        }
      }
    }

    newCatalog.computeIfAbsent(currencyId, k -> new ArrayList<>()).addAll(categories);

    // Build ShopDefinition from legacy data
    String title = root.has("title") ? root.get("title").getAsString() : shopId;
    ShopDefinition def = new ShopDefinition(shopId, title, currencyId,
        ShopDefinition.DEFAULT_ROWS, pages);
    newShops.put(shopId, def);
  }

  // ==================== YAML PARSING ====================

  /**
   * Lightweight YAML-to-JSON parser for simple shop files.
   * Handles the subset of YAML used in shop configs (maps, lists, scalars).
   */
  @Nullable
  private JsonObject parseYamlToJson(String yamlContent, List<String> issues, String fileName) {
    try {
      // Use Gson to parse if the content happens to be JSON
      if (yamlContent.trim().startsWith("{")) {
        return GSON.fromJson(yamlContent, JsonObject.class);
      }

      // Simple YAML parser for shop config subset
      return parseSimpleYaml(yamlContent);
    } catch (Exception e) {
      issues.add(fileName + ": YAML parse error: " + e.getMessage());
      LOGGER.error("EcoSystem: YAML parse error in {}", fileName, e);
      return null;
    }
  }

  /**
   * Minimal YAML parser that handles the shop config structure.
   * Supports: key-value pairs, nested maps (indentation), arrays, strings,
   * numbers, booleans.
   */

  private JsonObject parseSimpleYaml(String content) {
    // Use a recursive descent parser on indentation levels
    String[] lines = content.split("\n");
    Map<String, Object> result = new LinkedHashMap<>();
    parseYamlBlock(lines, 0, lines.length, 0, result);
    return mapToJsonObject(result);
  }

  private int getIndent(String line) {
    int indent = 0;
    for (char c : line.toCharArray()) {
      if (c == ' ')
        indent++;
      else
        break;
    }
    return indent;
  }

  private void parseYamlBlock(String[] lines, int start, int end, int baseIndent,
      Map<String, Object> target) {
    int i = start;
    while (i < end) {
      String line = lines[i];
      String trimmed = line.trim();

      // Skip empty lines and comments
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        i++;
        continue;
      }

      int indent = getIndent(line);
      if (indent < baseIndent)
        break;
      if (indent > baseIndent) {
        i++;
        continue;
      }

      // Check for list item
      if (trimmed.startsWith("- ")) {
        i++;
        continue;
      }

      // Key-value pair
      int colonIdx = trimmed.indexOf(':');
      if (colonIdx < 0) {
        i++;
        continue;
      }

      String key = trimmed.substring(0, colonIdx).trim();
      // Remove surrounding quotes from key
      if ((key.startsWith("\"") && key.endsWith("\"")) ||
          (key.startsWith("'") && key.endsWith("'"))) {
        key = key.substring(1, key.length() - 1);
      }

      String valueStr = trimmed.substring(colonIdx + 1).trim();

      if (valueStr.isEmpty()) {
        // Nested block — find all lines at indent+2
        int childIndent = -1;
        int blockEnd = i + 1;
        boolean isList = false;

        // Find child indent
        for (int j = i + 1; j < end; j++) {
          String cl = lines[j].trim();
          if (cl.isEmpty() || cl.startsWith("#"))
            continue;
          childIndent = getIndent(lines[j]);
          isList = cl.startsWith("- ");
          break;
        }

        if (childIndent > indent) {
          // Find block end
          for (int j = i + 1; j < end; j++) {
            String cl = lines[j].trim();
            if (cl.isEmpty() || cl.startsWith("#"))
              continue;
            if (getIndent(lines[j]) < childIndent) {
              blockEnd = j;
              break;
            }
            blockEnd = j + 1;
          }

          if (isList) {
            List<Object> list = new ArrayList<>();
            parseYamlList(lines, i + 1, blockEnd, childIndent, list);
            target.put(key, list);
          } else {
            Map<String, Object> child = new LinkedHashMap<>();
            parseYamlBlock(lines, i + 1, blockEnd, childIndent, child);
            target.put(key, child);
          }
          i = blockEnd;
        } else {
          target.put(key, "");
          i++;
        }
      } else {
        // Inline value
        if (valueStr.startsWith("[") && valueStr.endsWith("]")) {
          // Inline array
          String inner = valueStr.substring(1, valueStr.length() - 1);
          List<Object> list = new ArrayList<>();
          for (String item : inner.split(",")) {
            list.add(parseScalar(item.trim()));
          }
          target.put(key, list);
        } else {
          target.put(key, parseScalar(valueStr));
        }
        i++;
      }
    }
  }

  private void parseYamlList(String[] lines, int start, int end, int baseIndent,
      List<Object> target) {
    int i = start;
    while (i < end) {
      String line = lines[i];
      String trimmed = line.trim();

      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        i++;
        continue;
      }

      int indent = getIndent(line);
      if (indent < baseIndent)
        break;

      if (trimmed.startsWith("- ")) {
        String value = trimmed.substring(2).trim();
        target.add(parseScalar(value));
      }
      i++;
    }
  }

  private Object parseScalar(String value) {
    if (value.isEmpty())
      return "";

    // Remove quotes
    if ((value.startsWith("\"") && value.endsWith("\"")) ||
        (value.startsWith("'") && value.endsWith("'"))) {
      return value.substring(1, value.length() - 1);
    }

    // Boolean
    if (value.equalsIgnoreCase("true"))
      return true;
    if (value.equalsIgnoreCase("false"))
      return false;

    // Number
    try {
      if (value.contains("."))
        return Double.parseDouble(value);
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      // Not a number
    }

    return value;
  }

  @SuppressWarnings("unchecked")
  private JsonObject mapToJsonObject(Map<String, Object> map) {
    JsonObject obj = new JsonObject();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      obj.add(entry.getKey(), toJsonElement(entry.getValue()));
    }
    return obj;
  }

  @SuppressWarnings("unchecked")
  private JsonElement toJsonElement(Object value) {
    if (value instanceof Map) {
      return mapToJsonObject((Map<String, Object>) value);
    } else if (value instanceof List) {
      JsonArray arr = new JsonArray();
      for (Object item : (List<?>) value) {
        arr.add(toJsonElement(item));
      }
      return arr;
    } else if (value instanceof Boolean b) {
      return new JsonPrimitive(b);
    } else if (value instanceof Long l) {
      return new JsonPrimitive(l);
    } else if (value instanceof Double d) {
      return new JsonPrimitive(d);
    } else if (value instanceof Number n) {
      return new JsonPrimitive(n);
    } else {
      return new JsonPrimitive(String.valueOf(value));
    }
  }

  // ==================== PUBLIC API ====================

  /** Get a shop definition by ID. */
  @Nullable
  public ShopDefinition getShop(String shopId) {
    return shops.get(shopId);
  }

  /** Get all loaded shop IDs. */
  public Set<String> getShopIds() {
    return Collections.unmodifiableSet(shops.keySet());
  }

  /** Get all loaded shop definitions. */
  public Collection<ShopDefinition> getAllShops() {
    return Collections.unmodifiableCollection(shops.values());
  }

  /** Get a specific catalog entry by shopId, page index, and slot. */
  @Nullable
  public CatalogEntry getEntry(String shopId, int page, int slot) {
    ShopDefinition def = shops.get(shopId);
    return def != null ? def.getEntry(page, slot) : null;
  }

  // ==================== LEGACY API (backward compat for ChestShopMenu)
  // ====================

  public List<ShopCategory> getCategories(String currencyId) {
    return catalogByCurrency.getOrDefault(currencyId, Collections.emptyList());
  }

  @Nullable
  public ShopItem getItem(String currencyId, String itemId) {
    return itemIndex.get(currencyId + ":" + itemId);
  }

  public int getCategoryCount() {
    return catalogByCurrency.values().stream().mapToInt(List::size).sum();
  }

  // ==================== DEFAULT FILE GENERATION ====================

  private void createDefaultShopFile(Path shopsDir) {
    String defaultJson = """
        {
          "title": "General Store",
          "currency": "money",
          "rows": 6,
          "pages": {
            "Page0": {
              "0": {
                "type": "SHOP",
                "id": "minecraft:cobblestone",
                "displayName": "Cobblestone",
                "buyPrice": 5,
                "sellPrice": 2,
                "quantity": 1
              },
              "1": {
                "type": "SHOP",
                "id": "minecraft:oak_planks",
                "displayName": "Oak Planks",
                "buyPrice": 10,
                "sellPrice": 4,
                "quantity": 1
              },
              "2": {
                "type": "SHOP",
                "id": "minecraft:stone",
                "displayName": "Stone",
                "buyPrice": 8,
                "sellPrice": 3,
                "quantity": 1
              },
              "3": {
                "type": "SHOP",
                "id": "minecraft:bricks",
                "displayName": "Bricks",
                "buyPrice": 20,
                "sellPrice": 8,
                "quantity": 1
              },
              "4": {
                "type": "SHOP",
                "id": "minecraft:glass",
                "displayName": "Glass",
                "buyPrice": 15,
                "sellPrice": 5,
                "quantity": 1
              },
              "9": {
                "type": "SHOP",
                "id": "minecraft:coal",
                "displayName": "Coal",
                "buyPrice": 10,
                "sellPrice": 5,
                "quantity": 1
              },
              "10": {
                "type": "SHOP",
                "id": "minecraft:iron_ingot",
                "displayName": "Iron Ingot",
                "buyPrice": 50,
                "sellPrice": 25,
                "quantity": 1
              },
              "11": {
                "type": "SHOP",
                "id": "minecraft:gold_ingot",
                "displayName": "Gold Ingot",
                "buyPrice": 100,
                "sellPrice": 50,
                "quantity": 1
              },
              "12": {
                "type": "SHOP",
                "id": "minecraft:diamond",
                "displayName": "Diamond",
                "buyPrice": 500,
                "sellPrice": 250,
                "quantity": 1
              },
              "13": {
                "type": "SHOP",
                "id": "minecraft:emerald",
                "displayName": "Emerald",
                "buyPrice": 200,
                "sellPrice": 100,
                "quantity": 1
              },
              "18": {
                "type": "SHOP",
                "id": "minecraft:bread",
                "displayName": "Bread",
                "buyPrice": 8,
                "sellPrice": 3,
                "quantity": 1
              },
              "19": {
                "type": "SHOP",
                "id": "minecraft:cooked_beef",
                "displayName": "Steak",
                "buyPrice": 15,
                "sellPrice": 6,
                "quantity": 1
              },
              "20": {
                "type": "SHOP",
                "id": "minecraft:golden_apple",
                "displayName": "Golden Apple",
                "buyPrice": 1000,
                "sellPrice": 0,
                "allowSell": false,
                "quantity": 1
              },
              "27": {
                "type": "SHOP",
                "id": "minecraft:iron_pickaxe",
                "displayName": "Iron Pickaxe",
                "buyPrice": 200,
                "sellPrice": 0,
                "allowSell": false,
                "quantity": 1
              },
              "28": {
                "type": "SHOP",
                "id": "minecraft:iron_sword",
                "displayName": "Iron Sword",
                "buyPrice": 150,
                "sellPrice": 0,
                "allowSell": false,
                "quantity": 1
              },
              "29": {
                "type": "SHOP",
                "id": "minecraft:diamond_pickaxe",
                "displayName": "Diamond Pickaxe",
                "buyPrice": 2000,
                "sellPrice": 0,
                "allowSell": false,
                "quantity": 1
              },
              "45": {
                "type": "DECORATIVE",
                "id": "minecraft:gray_stained_glass_pane",
                "displayName": " "
              },
              "46": {
                "type": "DECORATIVE",
                "id": "minecraft:gray_stained_glass_pane",
                "displayName": " "
              },
              "47": {
                "type": "DECORATIVE",
                "id": "minecraft:gray_stained_glass_pane",
                "displayName": " "
              },
              "48": {
                "type": "NAVIGATION",
                "id": "minecraft:arrow",
                "displayName": "Previous Page",
                "navAction": "PREV_PAGE"
              },
              "49": {
                "type": "DECORATIVE",
                "id": "minecraft:nether_star",
                "displayName": "General Store"
              },
              "50": {
                "type": "NAVIGATION",
                "id": "minecraft:arrow",
                "displayName": "Next Page",
                "navAction": "NEXT_PAGE"
              },
              "51": {
                "type": "DECORATIVE",
                "id": "minecraft:gray_stained_glass_pane",
                "displayName": " "
              },
              "52": {
                "type": "DECORATIVE",
                "id": "minecraft:gray_stained_glass_pane",
                "displayName": " "
              },
              "53": {
                "type": "DECORATIVE",
                "id": "minecraft:gray_stained_glass_pane",
                "displayName": " "
              }
            }
          }
        }
        """;
    try {
      Files.writeString(shopsDir.resolve("default_shop.json"), defaultJson);
      LOGGER.info("EcoSystem: Created default shop catalog");
    } catch (IOException e) {
      LOGGER.error("EcoSystem: Failed to create default shop file", e);
    }
  }
}
