package com.pedrodalben.ecosystem.core;

import net.neoforged.neoforge.common.ModConfigSpec;

public class EcoSystemConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Storage
    public static final ModConfigSpec.EnumValue<StorageType> STORAGE_BACKEND;
    public static final ModConfigSpec.IntValue FLUSH_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue MAX_PENDING_WRITES;

    // Features
    public static final ModConfigSpec.BooleanValue ENABLE_GUI_SHOP;
    public static final ModConfigSpec.BooleanValue ENABLE_CHEST_SHOP;

    // JDBC
    public static final ModConfigSpec.ConfigValue<String> JDBC_URL;
    public static final ModConfigSpec.ConfigValue<String> JDBC_USERNAME;
    public static final ModConfigSpec.ConfigValue<String> JDBC_PASSWORD;
    public static final ModConfigSpec.IntValue JDBC_POOL_SIZE;

    // Audit
    public static final ModConfigSpec.BooleanValue AUDIT_ENABLED;
    public static final ModConfigSpec.IntValue AUDIT_MAX_FILE_SIZE_MB;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("storage");
        STORAGE_BACKEND = BUILDER
                .comment("Storage backend to use: SAVEDDATA (default, file-based) or JDBC (database)")
                .defineEnum("backend", StorageType.SAVEDDATA);
        FLUSH_INTERVAL_SECONDS = BUILDER
                .comment("How often (in seconds) to flush cached data to disk/DB")
                .defineInRange("flushIntervalSeconds", 30, 5, 300);
        MAX_PENDING_WRITES = BUILDER
                .comment("Maximum pending write operations before forcing a flush")
                .defineInRange("maxPendingWrites", 100, 10, 10000);
        BUILDER.pop();

        BUILDER.push("features");
        ENABLE_GUI_SHOP = BUILDER
                .comment("Enable the GUI-based shop system")
                .define("enableGuiShop", true);
        ENABLE_CHEST_SHOP = BUILDER
                .comment("Enable the chest-based shop system")
                .define("enableChestShop", true);
        BUILDER.pop();

        BUILDER.push("jdbc");
        JDBC_URL = BUILDER
                .comment("JDBC connection URL (e.g., jdbc:mysql://localhost:3306/economy)")
                .define("url", "jdbc:sqlite:economy.db");
        JDBC_USERNAME = BUILDER
                .comment("Database username")
                .define("username", "root");
        JDBC_PASSWORD = BUILDER
                .comment("Database password")
                .define("password", "");
        JDBC_POOL_SIZE = BUILDER
                .comment("Connection pool size (HikariCP)")
                .defineInRange("poolSize", 5, 1, 20);
        BUILDER.pop();

        BUILDER.push("audit");
        AUDIT_ENABLED = BUILDER
                .comment("Enable transaction audit logging")
                .define("enabled", true);
        AUDIT_MAX_FILE_SIZE_MB = BUILDER
                .comment("Maximum audit log file size in MB before rotation")
                .defineInRange("maxFileSizeMB", 10, 1, 100);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public enum StorageType {
        SAVEDDATA,
        JDBC
    }
}
