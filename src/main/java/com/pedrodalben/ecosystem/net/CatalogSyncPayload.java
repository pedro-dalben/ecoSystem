package com.pedrodalben.ecosystem.net;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import com.pedrodalben.ecosystem.shop.gui.CatalogEntry;
import com.pedrodalben.ecosystem.shop.gui.CatalogPage;
import com.pedrodalben.ecosystem.shop.gui.ShopDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server→Client payload: Sends a ShopDefinition so the client can render the
 * shop screen.
 */
public record CatalogSyncPayload(
        String shopId,
        String title,
        String currencyId,
        int rows,
        List<PageData> pages) implements CustomPacketPayload {

    public record PageData(Map<Integer, EntryData> slots) {
    }

    public record EntryData(
            String itemId,
            String displayName,
            List<String> lore,
            String currencyId,
            long buyPrice,
            long sellPrice,
            int quantity,
            boolean allowBuy,
            boolean allowSell,
            String type,
            String targetShop,
            String navAction) {
    }

    public static final CustomPacketPayload.Type<CatalogSyncPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(EcoSystemMod.MOD_ID, "catalog_sync"));

    public static final StreamCodec<FriendlyByteBuf, CatalogSyncPayload> STREAM_CODEC = StreamCodec
            .of(CatalogSyncPayload::encode, CatalogSyncPayload::decode);

    /**
     * Create a CatalogSyncPayload from a ShopDefinition.
     */
    public static CatalogSyncPayload fromDefinition(ShopDefinition def) {
        List<PageData> pageDataList = new ArrayList<>();
        for (CatalogPage page : def.pages()) {
            Map<Integer, EntryData> slotData = new LinkedHashMap<>();
            for (Map.Entry<Integer, CatalogEntry> entry : page.getSlots().entrySet()) {
                CatalogEntry e = entry.getValue();
                slotData.put(entry.getKey(), new EntryData(
                        e.itemId(), e.displayName(), e.lore(), e.currencyId(),
                        e.buyPrice(), e.sellPrice(), e.quantity(),
                        e.allowBuy(), e.allowSell(),
                        e.type().name(),
                        e.targetShop() != null ? e.targetShop() : "",
                        e.navAction() != null ? e.navAction().name() : ""));
            }
            pageDataList.add(new PageData(slotData));
        }
        return new CatalogSyncPayload(def.shopId(), def.title(), def.currencyId(),
                def.rows(), pageDataList);
    }

    /**
     * Reconstruct a ShopDefinition from this payload (client-side).
     */
    public ShopDefinition toDefinition() {
        List<CatalogPage> catalogPages = new ArrayList<>();
        for (PageData pd : pages) {
            Map<Integer, CatalogEntry> slots = new LinkedHashMap<>();
            for (Map.Entry<Integer, EntryData> entry : pd.slots().entrySet()) {
                EntryData ed = entry.getValue();
                CatalogEntry.EntryType type;
                try {
                    type = CatalogEntry.EntryType.valueOf(ed.type());
                } catch (IllegalArgumentException e) {
                    type = CatalogEntry.EntryType.SHOP;
                }

                CatalogEntry.NavigationAction navAct = null;
                if (!ed.navAction().isEmpty()) {
                    try {
                        navAct = CatalogEntry.NavigationAction.valueOf(ed.navAction());
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                }

                CatalogEntry ce = new CatalogEntry(
                        ed.itemId(), ed.displayName(), ed.lore(), ed.currencyId(),
                        ed.buyPrice(), ed.sellPrice(), ed.quantity(),
                        ed.allowBuy(), ed.allowSell(), type,
                        ed.targetShop().isEmpty() ? null : ed.targetShop(),
                        navAct);
                slots.put(entry.getKey(), ce);
            }
            catalogPages.add(new CatalogPage(slots));
        }
        return new ShopDefinition(shopId, title, currencyId, rows, catalogPages);
    }

    // ==================== CODEC ====================

    private static void encode(FriendlyByteBuf buf, CatalogSyncPayload payload) {
        buf.writeUtf(payload.shopId);
        buf.writeUtf(payload.title);
        buf.writeUtf(payload.currencyId);
        buf.writeVarInt(payload.rows);
        buf.writeVarInt(payload.pages.size());

        for (PageData page : payload.pages) {
            buf.writeVarInt(page.slots.size());
            for (Map.Entry<Integer, EntryData> entry : page.slots.entrySet()) {
                buf.writeVarInt(entry.getKey());
                EntryData ed = entry.getValue();
                buf.writeUtf(ed.itemId);
                buf.writeUtf(ed.displayName);
                buf.writeVarInt(ed.lore.size());
                for (String line : ed.lore) {
                    buf.writeUtf(line);
                }
                buf.writeUtf(ed.currencyId);
                buf.writeLong(ed.buyPrice);
                buf.writeLong(ed.sellPrice);
                buf.writeVarInt(ed.quantity);
                buf.writeBoolean(ed.allowBuy);
                buf.writeBoolean(ed.allowSell);
                buf.writeUtf(ed.type);
                buf.writeUtf(ed.targetShop);
                buf.writeUtf(ed.navAction);
            }
        }
    }

    private static CatalogSyncPayload decode(FriendlyByteBuf buf) {
        String shopId = buf.readUtf();
        String title = buf.readUtf();
        String currencyId = buf.readUtf();
        int rows = buf.readVarInt();
        int pageCount = buf.readVarInt();

        List<PageData> pages = new ArrayList<>();
        for (int p = 0; p < pageCount; p++) {
            int slotCount = buf.readVarInt();
            Map<Integer, EntryData> slots = new LinkedHashMap<>();
            for (int s = 0; s < slotCount; s++) {
                int slot = buf.readVarInt();
                String itemId = buf.readUtf();
                String displayName = buf.readUtf();
                int loreCount = buf.readVarInt();
                List<String> lore = new ArrayList<>();
                for (int l = 0; l < loreCount; l++) {
                    lore.add(buf.readUtf());
                }
                String eCurrencyId = buf.readUtf();
                long buyPrice = buf.readLong();
                long sellPrice = buf.readLong();
                int quantity = buf.readVarInt();
                boolean allowBuy = buf.readBoolean();
                boolean allowSell = buf.readBoolean();
                String type = buf.readUtf();
                String targetShop = buf.readUtf();
                String navAction = buf.readUtf();

                slots.put(slot, new EntryData(itemId, displayName, lore, eCurrencyId,
                        buyPrice, sellPrice, quantity, allowBuy, allowSell,
                        type, targetShop, navAction));
            }
            pages.add(new PageData(slots));
        }

        return new CatalogSyncPayload(shopId, title, currencyId, rows, pages);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
