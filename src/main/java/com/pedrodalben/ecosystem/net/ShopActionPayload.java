package com.pedrodalben.ecosystem.net;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client→Server payload: Player requests a shop action (buy/sell).
 * Now includes shopId, slotId and page for precise catalog entry lookup.
 */
public record ShopActionPayload(
        String shopId,
        String currencyId,
        String itemId,
        int slotId,
        int page,
        int quantity,
        Action action) implements CustomPacketPayload {

    public enum Action {
        BUY, SELL
    }

    public static final CustomPacketPayload.Type<ShopActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(EcoSystemMod.MOD_ID, "shop_action"));

    public static final StreamCodec<FriendlyByteBuf, ShopActionPayload> STREAM_CODEC = StreamCodec
            .of(ShopActionPayload::encode, ShopActionPayload::decode);

    private static void encode(FriendlyByteBuf buf, ShopActionPayload payload) {
        buf.writeUtf(payload.shopId);
        buf.writeUtf(payload.currencyId);
        buf.writeUtf(payload.itemId);
        buf.writeVarInt(payload.slotId);
        buf.writeVarInt(payload.page);
        buf.writeVarInt(payload.quantity);
        buf.writeEnum(payload.action);
    }

    private static ShopActionPayload decode(FriendlyByteBuf buf) {
        return new ShopActionPayload(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readEnum(Action.class));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
