package com.pedrodalben.ecosystem.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pedrodalben.ecosystem.ledger.Transaction;
import com.pedrodalben.ecosystem.ledger.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Asynchronous audit logger that writes transactions as JSONL to a file.
 * Uses a bounded queue + single writer thread to avoid blocking the server
 * tick.
 */
public class AuditLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("EcoSystem");
    private static final Gson GSON = new GsonBuilder().create();
    private static final String AUDIT_FILE = "ecosystem_audit.jsonl";

    private final Path dataDir;
    private ExecutorService writerExecutor;
    private final BlockingQueue<String> writeQueue = new LinkedBlockingQueue<>(10000);

    public AuditLogger(Path dataDir) {
        this.dataDir = dataDir;
    }

    public void init() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOGGER.error("EcoSystem: Failed to create audit directory", e);
        }

        writerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "EcoSystem-AuditWriter");
            t.setDaemon(true);
            return t;
        });

        // Start the writer loop
        writerExecutor.submit(this::writerLoop);
    }

    public void shutdown() {
        // Flush remaining entries
        List<String> remaining = new ArrayList<>();
        writeQueue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            writeEntries(remaining);
        }

        if (writerExecutor != null) {
            writerExecutor.shutdownNow();
            try {
                writerExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Log a transaction asynchronously. Non-blocking.
     */
    public void log(Transaction tx) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("txId", tx.txId().toString());
            entry.put("timestamp", tx.timestamp().toString());
            entry.put("type", tx.type().name());
            entry.put("currency", tx.currencyId());
            entry.put("amount", tx.amount());
            entry.put("tax", tx.taxAmount());
            entry.put("net", tx.netAmount());
            entry.put("from", tx.from() != null ? tx.from().toString() : null);
            entry.put("to", tx.to() != null ? tx.to().toString() : null);
            entry.put("context", tx.context());

            String json = GSON.toJson(entry);
            if (!writeQueue.offer(json)) {
                LOGGER.warn("EcoSystem: Audit queue full, dropping entry: {}", tx.txId());
            }
        } catch (Exception e) {
            LOGGER.error("EcoSystem: Failed to serialize audit entry", e);
        }
    }

    /**
     * Query last N transactions, optionally filtered by player and/or currency.
     * Reads from the audit file (synchronous — should only be called from
     * commands).
     */
    public List<Transaction> queryLast(int n, @Nullable UUID player, @Nullable String currency) {
        Path file = dataDir.resolve(AUDIT_FILE);
        if (!Files.exists(file))
            return Collections.emptyList();

        List<Transaction> results = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            // Read all lines into a list (for "last N")
            List<String> allLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                allLines.add(line);
            }

            // Process from end to start
            for (int i = allLines.size() - 1; i >= 0 && results.size() < n; i--) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entry = GSON.fromJson(allLines.get(i), Map.class);
                    if (entry == null)
                        continue;

                    String fromStr = (String) entry.get("from");
                    String toStr = (String) entry.get("to");

                    // Apply filters
                    if (player != null) {
                        String playerStr = player.toString();
                        if (!playerStr.equals(fromStr) && !playerStr.equals(toStr)) {
                            continue;
                        }
                    }
                    if (currency != null && !currency.equals(entry.get("currency"))) {
                        continue;
                    }

                    // Parse transaction
                    Transaction tx = new Transaction(
                            UUID.fromString((String) entry.get("txId")),
                            Instant.parse((String) entry.get("timestamp")),
                            TransactionType.valueOf((String) entry.get("type")),
                            (String) entry.get("currency"),
                            ((Number) entry.get("amount")).longValue(),
                            ((Number) entry.get("tax")).longValue(),
                            ((Number) entry.get("net")).longValue(),
                            fromStr != null ? UUID.fromString(fromStr) : null,
                            toStr != null ? UUID.fromString(toStr) : null,
                            (String) entry.get("context"));
                    results.add(tx);
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        } catch (IOException e) {
            LOGGER.error("EcoSystem: Failed to read audit log", e);
        }

        return results;
    }

    private void writerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Batch: wait for at least one entry, then drain all available
                String first = writeQueue.take();
                List<String> batch = new ArrayList<>();
                batch.add(first);
                writeQueue.drainTo(batch, 100);

                writeEntries(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void writeEntries(List<String> entries) {
        Path file = dataDir.resolve(AUDIT_FILE);
        try (FileWriter writer = new FileWriter(file.toFile(), true)) {
            for (String entry : entries) {
                writer.write(entry);
                writer.write('\n');
            }
            writer.flush();
        } catch (IOException e) {
            LOGGER.error("EcoSystem: Failed to write audit entries", e);
        }
    }
}
