package com.pedrodalben.ecosystem.core;

import com.pedrodalben.ecosystem.audit.AuditLogger;
import com.pedrodalben.ecosystem.currency.CurrencyRegistry;
import com.pedrodalben.ecosystem.ledger.LedgerService;
import com.pedrodalben.ecosystem.shop.gui.ShopCatalog;
import com.pedrodalben.ecosystem.storage.SavedDataBackend;
import com.pedrodalben.ecosystem.storage.StorageBackend;
import com.pedrodalben.ecosystem.tax.TaxService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class ServerEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger("EcoSystem");

    private static StorageBackend storageBackend;
    private static LedgerService ledgerService;
    private static CurrencyRegistry currencyRegistry;
    private static TaxService taxService;
    private static AuditLogger auditLogger;
    private static ShopCatalog shopCatalog;
    private static MinecraftServer server;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path configDir = worldDir.resolve("serverconfig").resolve("ecosystem");

        LOGGER.info("EcoSystem: Initializing economy system...");

        // Ensure config directory exists
        configDir.toFile().mkdirs();

        // Validate lang files (Dev/Debug check)
        if (ServerEvents.class.getResource("/assets/ecosystem/lang/en_us.json") == null) {
            LOGGER.error("EcoSystem: CRITICAL - en_us.json not found in classpath!");
        }
        if (ServerEvents.class.getResource("/assets/ecosystem/lang/pt_br.json") == null) {
            LOGGER.warn("EcoSystem: pt_br.json not found in classpath - Portuguese localization disabled.");
        }

        // Load currencies
        currencyRegistry = new CurrencyRegistry();
        currencyRegistry.loadFromDirectory(configDir);
        LOGGER.info("EcoSystem: Loaded {} currencies", currencyRegistry.getAllCurrencies().size());

        // Load tax profiles
        taxService = new TaxService();
        taxService.loadFromDirectory(configDir);
        LOGGER.info("EcoSystem: Loaded {} tax profiles", taxService.getProfileCount());

        // Initialize audit logger
        auditLogger = new AuditLogger(worldDir.resolve("data"));
        auditLogger.init();

        // Initialize storage backend
        if (EcoSystemConfig.STORAGE_BACKEND.get() == EcoSystemConfig.StorageType.SAVEDDATA) {
            storageBackend = new SavedDataBackend(server);
        } else {
            // JDBC backend (Phase 7)
            LOGGER.warn("EcoSystem: JDBC backend not yet implemented, falling back to SavedData");
            storageBackend = new SavedDataBackend(server);
        }
        storageBackend.init();

        // Initialize ledger service
        ledgerService = new LedgerService(storageBackend, currencyRegistry, taxService, auditLogger);
        ledgerService.init();
        LOGGER.info("EcoSystem: Loaded {} player balances", ledgerService.getLoadedAccountCount());

        // Load shop catalog
        shopCatalog = new ShopCatalog();
        shopCatalog.loadFromDirectory(configDir);
        LOGGER.info("EcoSystem: Loaded {} shop categories", shopCatalog.getCategoryCount());

        LOGGER.info("EcoSystem: Economy system initialized successfully!");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("EcoSystem: Shutting down economy system...");

        if (ledgerService != null) {
            ledgerService.flushAll();
        }
        if (storageBackend != null) {
            storageBackend.shutdown();
        }
        if (auditLogger != null) {
            auditLogger.shutdown();
        }

        LOGGER.info("EcoSystem: Economy system shut down.");
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ledgerService != null) {
                ledgerService.loadPlayerData(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ledgerService != null) {
                ledgerService.flushPlayer(player.getUUID());
            }
        }
    }

    // Static accessors for services (singleton pattern for the server lifecycle)
    public static LedgerService getLedgerService() {
        return ledgerService;
    }

    public static CurrencyRegistry getCurrencyRegistry() {
        return currencyRegistry;
    }

    public static TaxService getTaxService() {
        return taxService;
    }

    public static AuditLogger getAuditLogger() {
        return auditLogger;
    }

    public static ShopCatalog getShopCatalog() {
        return shopCatalog;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static StorageBackend getStorageBackend() {
        return storageBackend;
    }

    /**
     * Reload all config files (currencies, taxes, shop catalogs).
     *
     * @return list of validation issues from shop catalog loading (empty if clean)
     */
    public static java.util.List<String> reload() {
        if (server == null)
            return java.util.List.of("Server not available");
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path configDir = worldDir.resolve("serverconfig").resolve("ecosystem");

        currencyRegistry.loadFromDirectory(configDir);
        taxService.loadFromDirectory(configDir);
        java.util.List<String> issues = shopCatalog.loadFromDirectory(configDir);

        LOGGER.info("EcoSystem: Configuration reloaded. {} shops loaded, {} issues.",
                shopCatalog.getShopIds().size(), issues.size());
        for (String issue : issues) {
            LOGGER.warn("EcoSystem: Shop validation: {}", issue);
        }

        return issues;
    }
}
