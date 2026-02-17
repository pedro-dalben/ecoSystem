package com.pedrodalben.ecosystem.currency;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all currencies. Loaded from currencies.json.
 * Thread-safe via volatile reference swap on reload.
 */
public class CurrencyRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("EcoSystem");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CURRENCIES_FILE = "currencies.json";

    private volatile Map<String, Currency> currencies = new ConcurrentHashMap<>();

    public CurrencyRegistry() {
    }

    /**
     * Load currencies from the config directory, creating a default file if absent.
     */
    public void loadFromDirectory(Path configDir) {
        Path file = configDir.resolve(CURRENCIES_FILE);

        if (!Files.exists(file)) {
            createDefaultCurrenciesFile(file);
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonArray array = GSON.fromJson(reader, JsonArray.class);
            Map<String, Currency> newMap = new ConcurrentHashMap<>();

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                Currency currency = parseCurrency(obj);
                if (currency != null) {
                    newMap.put(currency.getId(), currency);
                }
            }

            this.currencies = newMap;
        } catch (Exception e) {
            LOGGER.error("EcoSystem: Failed to load currencies from {}", file, e);
        }
    }

    private Currency parseCurrency(JsonObject obj) {
        try {
            String id = obj.get("id").getAsString();
            String displayName = obj.has("displayName") ? obj.get("displayName").getAsString() : id;
            CurrencyType type = CurrencyType.valueOf(obj.get("type").getAsString().toUpperCase());

            ResourceLocation itemId = null;
            int unitAmount = 1;
            if (type == CurrencyType.ITEM_BACKED) {
                String itemStr = obj.has("itemId") ? obj.get("itemId").getAsString() : "minecraft:diamond";
                itemId = ResourceLocation.parse(itemStr);
                unitAmount = obj.has("unitAmount") ? obj.get("unitAmount").getAsInt() : 1;
            }

            int precision = obj.has("precision") ? obj.get("precision").getAsInt() : 0;
            boolean shopEnabled = !obj.has("shopEnabled") || obj.get("shopEnabled").getAsBoolean();
            String taxProfile = obj.has("taxProfile") ? obj.get("taxProfile").getAsString() : "default";
            String symbol = obj.has("symbol") ? obj.get("symbol").getAsString() : "";

            return new Currency(id, displayName, type, itemId, unitAmount, precision,
                    shopEnabled, taxProfile, symbol);
        } catch (Exception e) {
            LOGGER.error("EcoSystem: Failed to parse currency: {}", obj, e);
            return null;
        }
    }

    public Currency getCurrency(String id) {
        return currencies.get(id);
    }

    public Collection<Currency> getAllCurrencies() {
        return Collections.unmodifiableCollection(currencies.values());
    }

    public List<String> getCurrencyIds() {
        return new ArrayList<>(currencies.keySet());
    }

    public boolean exists(String id) {
        return currencies.containsKey(id);
    }

    /**
     * Get the default currency (usually "money").
     * If "money" doesn't exist, returns the first available currency, or null if
     * empty.
     */
    public Currency getDefaultCurrency() {
        Currency c = currencies.get("money");
        if (c != null)
            return c;
        if (!currencies.isEmpty())
            return currencies.values().iterator().next();
        return null;
    }

    /**
     * Dynamically add a currency (admin command).
     */
    public void addCurrency(Currency currency) {
        currencies.put(currency.getId(), currency);
    }

    /**
     * Remove a currency (admin command).
     */
    public boolean removeCurrency(String id) {
        return currencies.remove(id) != null;
    }

    /**
     * Save current currencies to file.
     */
    public void saveToDirectory(Path configDir) {
        Path file = configDir.resolve(CURRENCIES_FILE);
        JsonArray array = new JsonArray();
        for (Currency c : currencies.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", c.getId());
            obj.addProperty("displayName", c.getDisplayName());
            obj.addProperty("type", c.getType().name());
            if (c.isItemBacked() && c.getItemId() != null) {
                obj.addProperty("itemId", c.getItemId().toString());
                obj.addProperty("unitAmount", c.getUnitAmount());
            }
            obj.addProperty("precision", c.getPrecision());
            obj.addProperty("shopEnabled", c.isShopEnabled());
            obj.addProperty("taxProfile", c.getTaxProfileId());
            obj.addProperty("symbol", c.getSymbol());
            array.add(obj);
        }

        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(array, writer);
        } catch (IOException e) {
            LOGGER.error("EcoSystem: Failed to save currencies to {}", file, e);
        }
    }

    private void createDefaultCurrenciesFile(Path file) {
        String defaultJson = """
                [
                  {
                    "id": "money",
                    "displayName": "§6Coins",
                    "type": "VIRTUAL",
                    "precision": 2,
                    "shopEnabled": true,
                    "taxProfile": "default",
                    "symbol": "$"
                  },
                  {
                    "id": "gems",
                    "displayName": "§bGems",
                    "type": "ITEM_BACKED",
                    "itemId": "minecraft:diamond",
                    "unitAmount": 1,
                    "precision": 0,
                    "shopEnabled": true,
                    "taxProfile": "default",
                    "symbol": "💎"
                  }
                ]
                """;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaultJson);
            LOGGER.info("EcoSystem: Created default currencies.json");
        } catch (IOException e) {
            LOGGER.error("EcoSystem: Failed to create default currencies file", e);
        }
    }
}
