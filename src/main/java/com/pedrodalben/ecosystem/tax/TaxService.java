package com.pedrodalben.ecosystem.tax;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.pedrodalben.ecosystem.currency.Currency;
import com.pedrodalben.ecosystem.ledger.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for calculating taxes on transactions.
 * Loads tax profiles from taxes.json.
 */
public class TaxService {
    private static final Logger LOGGER = LoggerFactory.getLogger("EcoSystem");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TAXES_FILE = "taxes.json";

    private volatile Map<String, TaxProfile> profiles = new ConcurrentHashMap<>();

    public TaxService() {
    }

    public void loadFromDirectory(Path configDir) {
        Path file = configDir.resolve(TAXES_FILE);

        if (!Files.exists(file)) {
            createDefaultTaxesFile(file);
        }

        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            Map<String, TaxProfile> newProfiles = new ConcurrentHashMap<>();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String profileId = entry.getKey();
                JsonObject profileObj = entry.getValue().getAsJsonObject();
                TaxProfile profile = parseProfile(profileId, profileObj);
                if (profile != null) {
                    newProfiles.put(profileId, profile);
                }
            }

            this.profiles = newProfiles;
        } catch (Exception e) {
            LOGGER.error("EcoSystem: Failed to load taxes from {}", file, e);
        }
    }

    private TaxProfile parseProfile(String id, JsonObject obj) {
        try {
            Map<TransactionType, TaxRate> rates = new EnumMap<>(TransactionType.class);

            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                try {
                    TransactionType type = TransactionType.valueOf(entry.getKey().toUpperCase());
                    JsonObject rateObj = entry.getValue().getAsJsonObject();
                    TaxRate rate = parseRate(rateObj);
                    rates.put(type, rate);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("EcoSystem: Unknown transaction type '{}' in tax profile '{}'", entry.getKey(), id);
                }
            }

            return new TaxProfile(id, rates);
        } catch (Exception e) {
            LOGGER.error("EcoSystem: Failed to parse tax profile '{}'", id, e);
            return null;
        }
    }

    private TaxRate parseRate(JsonObject obj) {
        long flat = obj.has("flat") ? obj.get("flat").getAsLong() : 0;
        double percent = obj.has("percent") ? obj.get("percent").getAsDouble() : 0;
        long min = obj.has("min") ? obj.get("min").getAsLong() : 0;
        long max = obj.has("max") ? obj.get("max").getAsLong() : Long.MAX_VALUE;
        TaxDestination dest = TaxDestination.SINK;
        if (obj.has("destination")) {
            try {
                dest = TaxDestination.valueOf(obj.get("destination").getAsString().toUpperCase());
            } catch (IllegalArgumentException e) {
                LOGGER.warn("EcoSystem: Unknown tax destination: {}", obj.get("destination").getAsString());
            }
        }
        return new TaxRate(flat, percent, min, max, dest);
    }

    /**
     * Calculate tax for a given currency + transaction type + amount (in minor
     * units).
     */
    public TaxResult calculateTax(Currency currency, TransactionType type, long amount) {
        TaxProfile profile = profiles.get(currency.getTaxProfileId());
        if (profile == null) {
            return TaxResult.noTax(amount);
        }

        TaxRate rate = profile.getRateFor(type);
        if (rate.equals(TaxRate.ZERO)) {
            return TaxResult.noTax(amount);
        }

        // Calculate: flat + percent of amount
        long percentTax = Math.round(amount * rate.percent() / 100.0);
        long rawTax = rate.flatAmount() + percentTax;

        // Clamp to min/max
        long tax = Math.max(rate.minTax(), Math.min(rate.maxTax(), rawTax));

        // Ensure tax doesn't exceed the amount
        tax = Math.min(tax, amount);

        long net = amount - tax;
        return new TaxResult(amount, tax, net, rate.destination());
    }

    public int getProfileCount() {
        return profiles.size();
    }

    private void createDefaultTaxesFile(Path file) {
        String defaultJson = """
                {
                  "default": {
                    "DEPOSIT": { "flat": 0, "percent": 0, "min": 0, "max": 0, "destination": "SINK" },
                    "WITHDRAW": { "flat": 0, "percent": 1.0, "min": 0, "max": 1000, "destination": "SINK" },
                    "PAY": { "flat": 0, "percent": 2.5, "min": 1, "max": 5000, "destination": "SERVER_ACCOUNT" },
                    "SHOP_BUY": { "flat": 0, "percent": 0, "min": 0, "max": 0, "destination": "SINK" },
                    "SHOP_SELL": { "flat": 0, "percent": 5.0, "min": 1, "max": 10000, "destination": "SINK" }
                  }
                }
                """;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaultJson);
            LOGGER.info("EcoSystem: Created default taxes.json");
        } catch (IOException e) {
            LOGGER.error("EcoSystem: Failed to create default taxes file", e);
        }
    }
}
