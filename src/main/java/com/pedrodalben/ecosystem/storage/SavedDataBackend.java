package com.pedrodalben.ecosystem.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pedrodalben.ecosystem.ledger.AccountKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SavedData-based storage backend. Uses NeoForge's SavedData system
 * to persist economy data with the world. Attached to the Overworld.
 */
public class SavedDataBackend implements StorageBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger("EcoSystem");
    private static final String DATA_NAME = "ecosystem_balances";

    private final MinecraftServer server;
    private EconomySavedData savedData;

    public SavedDataBackend(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void init() {
        ServerLevel overworld = server.overworld();
        DimensionDataStorage dataStorage = overworld.getDataStorage();

        savedData = dataStorage.computeIfAbsent(
                EconomySavedData.factory(),
                DATA_NAME);

        LOGGER.info("EcoSystem: SavedData backend initialized");
    }

    @Override
    public void shutdown() {
        if (savedData != null) {
            savedData.setDirty();
        }
        LOGGER.info("EcoSystem: SavedData backend shut down");
    }

    @Override
    public Map<AccountKey, Long> loadAllBalances() {
        if (savedData == null)
            return new HashMap<>();
        return savedData.getAllBalances();
    }

    @Override
    public Map<String, Long> loadPlayerBalances(UUID playerUuid) {
        if (savedData == null)
            return new HashMap<>();
        return savedData.getPlayerBalances(playerUuid);
    }

    @Override
    public void saveBalances(Map<AccountKey, Long> balances) {
        if (savedData == null)
            return;
        for (Map.Entry<AccountKey, Long> entry : balances.entrySet()) {
            savedData.setBalance(entry.getKey().playerUuid(),
                    entry.getKey().currencyId(), entry.getValue());
        }
        savedData.setDirty();
    }

    @Override
    public void savePlayerBalances(UUID playerUuid, Map<String, Long> balances) {
        if (savedData == null)
            return;
        for (Map.Entry<String, Long> entry : balances.entrySet()) {
            savedData.setBalance(playerUuid, entry.getKey(), entry.getValue());
        }
        savedData.setDirty();
    }

    /**
     * NeoForge SavedData implementation for storing economy balances.
     * Uses NBT serialization (CompoundTag) for compatibility with the world save
     * system.
     */
    public static class EconomySavedData extends SavedData {
        // playerUUID -> currencyId -> balance
        private final Map<UUID, Map<String, Long>> data = new ConcurrentHashMap<>();

        public EconomySavedData() {
        }

        /**
         * Creates a factory for this SavedData type.
         */
        public static SavedData.Factory<EconomySavedData> factory() {
            return new SavedData.Factory<>(
                    EconomySavedData::new,
                    EconomySavedData::load,
                    null // No DataFixTypes needed
            );
        }

        /**
         * Load from NBT (called by the data storage system).
         */
        public static EconomySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
            EconomySavedData instance = new EconomySavedData();

            if (tag.contains("players", Tag.TAG_LIST)) {
                ListTag playersList = tag.getList("players", Tag.TAG_COMPOUND);
                for (int i = 0; i < playersList.size(); i++) {
                    CompoundTag playerTag = playersList.getCompound(i);
                    UUID uuid = playerTag.getUUID("uuid");
                    Map<String, Long> currencies = new ConcurrentHashMap<>();

                    CompoundTag balancesTag = playerTag.getCompound("balances");
                    for (String key : balancesTag.getAllKeys()) {
                        currencies.put(key, balancesTag.getLong(key));
                    }

                    instance.data.put(uuid, currencies);
                }
            }

            return instance;
        }

        /**
         * Save to NBT (called by the data storage system).
         */
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag playersList = new ListTag();

            for (Map.Entry<UUID, Map<String, Long>> playerEntry : data.entrySet()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID("uuid", playerEntry.getKey());

                CompoundTag balancesTag = new CompoundTag();
                for (Map.Entry<String, Long> balEntry : playerEntry.getValue().entrySet()) {
                    balancesTag.putLong(balEntry.getKey(), balEntry.getValue());
                }
                playerTag.put("balances", balancesTag);

                playersList.add(playerTag);
            }

            tag.put("players", playersList);
            return tag;
        }

        // Internal accessors

        public Map<AccountKey, Long> getAllBalances() {
            Map<AccountKey, Long> result = new HashMap<>();
            for (Map.Entry<UUID, Map<String, Long>> playerEntry : data.entrySet()) {
                for (Map.Entry<String, Long> balEntry : playerEntry.getValue().entrySet()) {
                    result.put(new AccountKey(playerEntry.getKey(), balEntry.getKey()),
                            balEntry.getValue());
                }
            }
            return result;
        }

        public Map<String, Long> getPlayerBalances(UUID playerUuid) {
            Map<String, Long> currencies = data.get(playerUuid);
            return currencies != null ? new HashMap<>(currencies) : new HashMap<>();
        }

        public void setBalance(UUID playerUuid, String currencyId, long balance) {
            data.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                    .put(currencyId, balance);
        }
    }
}
