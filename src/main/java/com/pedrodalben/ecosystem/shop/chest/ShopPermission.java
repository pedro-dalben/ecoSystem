package com.pedrodalben.ecosystem.shop.chest;

import net.minecraft.world.entity.player.Player;

/**
 * Permission nodes for the chest shop system.
 * Each node maps to a configurable op-level in EcoSystemConfig.
 *
 * Inspired by ChestShop-3's Permission enum, adapted for NeoForge
 * where we use op-levels instead of Bukkit permission nodes.
 */
public enum ShopPermission {

    /** Create player shops */
    SHOP_CREATE("ecosystem.shop.create", 0),

    /** Create admin shops (unlimited stock) */
    SHOP_CREATE_ADMIN("ecosystem.shop.create.admin", 2),

    /** Buy from any shop */
    SHOP_BUY("ecosystem.shop.buy", 0),

    /** Sell to any shop */
    SHOP_SELL("ecosystem.shop.sell", 0),

    /** Destroy own shops */
    SHOP_DESTROY("ecosystem.shop.destroy", 0),

    /** Destroy other players' shops */
    SHOP_DESTROY_OTHER("ecosystem.shop.destroy.other", 2),

    /** Inspect any shop details */
    SHOP_INSPECT("ecosystem.shop.inspect", 2);

    private final String node;
    private final int defaultOpLevel;

    ShopPermission(String node, int defaultOpLevel) {
        this.node = node;
        this.defaultOpLevel = defaultOpLevel;
    }

    public String getNode() {
        return node;
    }

    public int getDefaultOpLevel() {
        return defaultOpLevel;
    }

    /**
     * Check if a player has this permission.
     * Uses the configured op-level from EcoSystemConfig, falling back
     * to the default op-level defined in this enum.
     */
    public boolean hasPermission(Player player) {
        int requiredLevel = getConfiguredOpLevel();
        return player.hasPermissions(requiredLevel);
    }

    /**
     * Get the configured op-level for this permission from EcoSystemConfig.
     * Falls back to the default if not configured.
     */
    private int getConfiguredOpLevel() {
        return switch (this) {
            case SHOP_CREATE -> com.pedrodalben.ecosystem.core.EcoSystemConfig.PERM_SHOP_CREATE.get();
            case SHOP_CREATE_ADMIN -> com.pedrodalben.ecosystem.core.EcoSystemConfig.PERM_SHOP_CREATE_ADMIN.get();
            case SHOP_BUY -> com.pedrodalben.ecosystem.core.EcoSystemConfig.PERM_SHOP_BUY.get();
            case SHOP_SELL -> com.pedrodalben.ecosystem.core.EcoSystemConfig.PERM_SHOP_SELL.get();
            case SHOP_DESTROY -> com.pedrodalben.ecosystem.core.EcoSystemConfig.PERM_SHOP_DESTROY.get();
            case SHOP_DESTROY_OTHER -> com.pedrodalben.ecosystem.core.EcoSystemConfig.PERM_SHOP_DESTROY_OTHER.get();
            case SHOP_INSPECT -> com.pedrodalben.ecosystem.core.EcoSystemConfig.PERM_SHOP_INSPECT.get();
        };
    }
}
