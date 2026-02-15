package com.pedrodalben.ecosystem.net;

import com.pedrodalben.ecosystem.core.EcoSystemMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Server→Client payload: Synchronizes player's balance data.
 */
public record BalanceSyncPayload(Map<String, Long> balances) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BalanceSyncPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(EcoSystemMod.MOD_ID, "balance_sync"));

    public static final StreamCodec<FriendlyByteBuf, BalanceSyncPayload> STREAM_CODEC = StreamCodec
            .of(BalanceSyncPayload::encode, BalanceSyncPayload::decode);

    private static void encode(FriendlyByteBuf buf, BalanceSyncPayload payload) {
        buf.writeVarInt(payload.balances.size());
        for (Map.Entry<String, Long> entry : payload.balances.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }

    private static BalanceSyncPayload decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, Long> balances = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf();
            long value = buf.readLong();
            balances.put(key, value);
        }
        return new BalanceSyncPayload(balances);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
