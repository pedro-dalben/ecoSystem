package com.pedrodalben.ecosystem.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.pedrodalben.ecosystem.core.LangKeys;
import com.pedrodalben.ecosystem.core.ServerEvents;
import com.pedrodalben.ecosystem.currency.Currency;
import com.pedrodalben.ecosystem.currency.CurrencyRegistry;
import com.pedrodalben.ecosystem.currency.CurrencyType;
import com.pedrodalben.ecosystem.ledger.LedgerService;
import com.pedrodalben.ecosystem.ledger.Transaction;
import com.pedrodalben.ecosystem.ledger.TransactionResult;
import com.pedrodalben.ecosystem.shop.chest.ShopTerminalBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * All /eco commands using Brigadier.
 */
public class EcoCommand {

    private static final SuggestionProvider<CommandSourceStack> CURRENCY_SUGGESTIONS = (context, builder) -> {
        CurrencyRegistry registry = ServerEvents.getCurrencyRegistry();
        if (registry != null) {
            return SharedSuggestionProvider.suggest(registry.getCurrencyIds(), builder);
        }
        return builder.buildFuture();
    };

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("eco")
                // /eco balance [currency] [player]
                .then(Commands.literal("balance")
                        .executes(ctx -> balanceAll(ctx))
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .suggests(CURRENCY_SUGGESTIONS)
                                .executes(ctx -> balanceCurrency(ctx))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(src -> src.hasPermission(2))
                                        .executes(ctx -> balanceOther(ctx)))))

                // /eco pay <player> <currency> <amount>
                .then(Commands.literal("pay")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("currency", StringArgumentType.word())
                                        .suggests(CURRENCY_SUGGESTIONS)
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                                .executes(ctx -> pay(ctx))))))

                // /eco deposit <currency> [amount]
                .then(Commands.literal("deposit")
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .suggests(CURRENCY_SUGGESTIONS)
                                .executes(ctx -> deposit(ctx, -1))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .executes(ctx -> deposit(ctx, 0)))))

                // /eco withdraw <currency> <amount>
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("currency", StringArgumentType.word())
                                .suggests(CURRENCY_SUGGESTIONS)
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                        .executes(ctx -> withdraw(ctx)))))

                // /eco admin ...
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))

                        // /eco admin give <player> <currency> <amount>
                        .then(Commands.literal("give")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("currency", StringArgumentType.word())
                                                .suggests(CURRENCY_SUGGESTIONS)
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                                        .executes(ctx -> adminGive(ctx))))))

                        // /eco admin take <player> <currency> <amount>
                        .then(Commands.literal("take")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("currency", StringArgumentType.word())
                                                .suggests(CURRENCY_SUGGESTIONS)
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                                        .executes(ctx -> adminTake(ctx))))))

                        // /eco admin set <player> <currency> <amount>
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("currency", StringArgumentType.word())
                                                .suggests(CURRENCY_SUGGESTIONS)
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                                        .executes(ctx -> adminSet(ctx))))))

                        // /eco admin reload
                        .then(Commands.literal("reload")
                                .executes(ctx -> adminReload(ctx)))

                        // /eco admin currency list
                        .then(Commands.literal("currency")
                                .then(Commands.literal("list")
                                        .executes(ctx -> currencyList(ctx)))
                                .then(Commands.literal("create")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands
                                                        .argument("displayName", StringArgumentType.greedyString())
                                                        .executes(ctx -> currencyCreate(ctx))))))

                        // /eco shop ...
                        .then(Commands.literal("shop")
                                .then(Commands.literal("inspect")
                                        .requires(src -> src.hasPermission(2))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> shopInspect(ctx))))
                                .then(Commands.literal("remove")
                                        .requires(src -> src.hasPermission(2))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(ctx -> shopRemove(ctx)))))

                        // /eco audit last <n> [player] [currency]
                        .then(Commands.literal("audit")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("last")
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                                .executes(ctx -> auditLast(ctx))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(ctx -> auditLastPlayer(ctx))
                                                        .then(Commands.argument("currency", StringArgumentType.word())
                                                                .suggests(CURRENCY_SUGGESTIONS)
                                                                .executes(ctx -> auditLastPlayerCurrency(ctx)))))))));
    }

    // ==================== BALANCE COMMANDS ====================

    private static int balanceAll(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.COMMAND_ONLY_PLAYERS));
            return 0;
        }
        LedgerService ledger = ServerEvents.getLedgerService();
        CurrencyRegistry registry = ServerEvents.getCurrencyRegistry();
        if (ledger == null || registry == null)
            return 0;

        MutableComponent msg = Component.translatable(LangKeys.COMMAND_BALANCE_HEADER).append("\n");
        for (Currency c : registry.getAllCurrencies()) {
            long balance = ledger.getBalance(player.getUUID(), c.getId());
            msg.append(Component.translatable(LangKeys.COMMAND_BALANCE_ENTRY,
                    c.getDisplayName(), c.formatAmount(balance))).append("\n");
        }
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int balanceCurrency(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null)
            return 0;

        String currencyId = StringArgumentType.getString(ctx, "currency");
        Currency currency = getCurrency(ctx, currencyId);
        if (currency == null)
            return 0;

        LedgerService ledger = ServerEvents.getLedgerService();
        long balance = ledger.getBalance(player.getUUID(), currencyId);
        ctx.getSource().sendSuccess(
                () -> Component.translatable(LangKeys.COMMAND_BALANCE_ENTRY,
                        currency.getDisplayName(), currency.formatAmount(balance)),
                false);
        return 1;
    }

    private static int balanceOther(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String currencyId = StringArgumentType.getString(ctx, "currency");
        Currency currency = getCurrency(ctx, currencyId);
        if (currency == null)
            return 0;

        LedgerService ledger = ServerEvents.getLedgerService();
        long balance = ledger.getBalance(target.getUUID(), currencyId);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.COMMAND_BALANCE_OTHER,
                target.getName(), currency.getDisplayName(), currency.formatAmount(balance)), false);
        return 1;
    }

    // ==================== PAY COMMAND ====================

    private static int pay(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayer();
        if (sender == null)
            return 0;

        ServerPlayer receiver = EntityArgument.getPlayer(ctx, "player");
        String currencyId = StringArgumentType.getString(ctx, "currency");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        Currency currency = getCurrency(ctx, currencyId);
        if (currency == null)
            return 0;

        LedgerService ledger = ServerEvents.getLedgerService();
        long minorUnits = currency.toMinorUnits(amount);

        TransactionResult result = ledger.pay(sender.getUUID(), receiver.getUUID(), currency, minorUnits);

        if (result.success()) {
            ctx.getSource().sendSuccess(() -> result.message(), false);
            receiver.sendSystemMessage(Component.translatable(LangKeys.COMMAND_RECEIVED,
                    currency.formatAmount(result.transaction().netAmount()),
                    sender.getName()));
        } else {
            ctx.getSource().sendFailure(result.message());
        }
        return result.success() ? 1 : 0;
    }

    // ==================== DEPOSIT / WITHDRAW ====================

    private static int deposit(CommandContext<CommandSourceStack> ctx, int flag)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null)
            return 0;

        String currencyId = StringArgumentType.getString(ctx, "currency");
        Currency currency = getCurrency(ctx, currencyId);
        if (currency == null)
            return 0;

        if (!currency.isItemBacked()) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.COMMAND_NOT_ITEM_BACKED));
            return 0;
        }

        long amount;
        if (flag < 0) {
            amount = -1; // deposit all
        } else {
            double humanAmount = DoubleArgumentType.getDouble(ctx, "amount");
            amount = currency.toMinorUnits(humanAmount);
        }

        LedgerService ledger = ServerEvents.getLedgerService();
        TransactionResult result = ledger.depositFromInventory(player, currency, amount);

        if (result.success()) {
            ctx.getSource().sendSuccess(() -> result.message(), false);
        } else {
            ctx.getSource().sendFailure(result.message());
        }
        return result.success() ? 1 : 0;
    }

    private static int withdraw(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null)
            return 0;

        String currencyId = StringArgumentType.getString(ctx, "currency");
        double humanAmount = DoubleArgumentType.getDouble(ctx, "amount");

        Currency currency = getCurrency(ctx, currencyId);
        if (currency == null)
            return 0;

        if (!currency.isItemBacked()) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.COMMAND_NOT_ITEM_BACKED));
            return 0;
        }

        LedgerService ledger = ServerEvents.getLedgerService();
        long minorUnits = currency.toMinorUnits(humanAmount);
        TransactionResult result = ledger.withdrawToInventory(player, currency, minorUnits);

        if (result.success()) {
            ctx.getSource().sendSuccess(() -> result.message(), false);
        } else {
            ctx.getSource().sendFailure(result.message());
        }
        return result.success() ? 1 : 0;
    }

    // ==================== ADMIN COMMANDS ====================

    private static int adminGive(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String currencyId = StringArgumentType.getString(ctx, "currency");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        Currency currency = getCurrency(ctx, currencyId);
        if (currency == null)
            return 0;

        LedgerService ledger = ServerEvents.getLedgerService();
        TransactionResult result = ledger.adminGive(target.getUUID(), currency, currency.toMinorUnits(amount));

        if (result.success()) {
            ctx.getSource().sendSuccess(() -> result.message(), false);
            target.sendSystemMessage(Component.translatable(LangKeys.COMMAND_GAVE_ADMIN,
                    currency.formatAmount(currency.toMinorUnits(amount))));
        } else {
            ctx.getSource().sendFailure(result.message());
        }
        return 1;
    }

    private static int adminTake(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String currencyId = StringArgumentType.getString(ctx, "currency");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        Currency currency = getCurrency(ctx, currencyId);
        if (currency == null)
            return 0;

        LedgerService ledger = ServerEvents.getLedgerService();
        TransactionResult result = ledger.adminTake(target.getUUID(), currency, currency.toMinorUnits(amount));

        ctx.getSource().sendSuccess(() -> result.message(), false);
        return 1;
    }

    private static int adminSet(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String currencyId = StringArgumentType.getString(ctx, "currency");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");

        Currency currency = getCurrency(ctx, currencyId);
        if (currency == null)
            return 0;

        LedgerService ledger = ServerEvents.getLedgerService();
        TransactionResult result = ledger.adminSet(target.getUUID(), currency, currency.toMinorUnits(amount));

        ctx.getSource().sendSuccess(() -> result.message(), false);
        return 1;
    }

    private static int adminReload(CommandContext<CommandSourceStack> ctx) {
        java.util.List<String> issues = ServerEvents.reload();

        int shopCount = ServerEvents.getShopCatalog() != null
                ? ServerEvents.getShopCatalog().getShopIds().size()
                : 0;

        ctx.getSource().sendSuccess(
                () -> Component.translatable(LangKeys.SHOP_RELOAD_SUCCESS, String.valueOf(shopCount)), true);

        if (!issues.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.translatable(LangKeys.SHOP_RELOAD_WARNINGS), false);
            for (String issue : issues) {
                ctx.getSource().sendFailure(Component.literal("§e⚠ " + issue));
            }
        }
        return 1;
    }

    // ==================== CURRENCY ADMIN ====================

    private static int currencyList(CommandContext<CommandSourceStack> ctx) {
        CurrencyRegistry registry = ServerEvents.getCurrencyRegistry();
        if (registry == null)
            return 0;

        MutableComponent msg = Component.literal("§6=== Currencies ===\n");
        for (Currency c : registry.getAllCurrencies()) {
            msg.append(Component.literal("§7- §f" + c.getId() + " §7(" + c.getDisplayName() + "§7) type=" +
                    c.getType().name() + " precision=" + c.getPrecision() +
                    (c.isItemBacked() ? " item=" + c.getItemId() : "") + "\n"));
        }
        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int currencyCreate(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        String displayName = StringArgumentType.getString(ctx, "displayName");

        CurrencyRegistry registry = ServerEvents.getCurrencyRegistry();
        if (registry.exists(id)) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.COMMAND_CURRENCY_EXISTS, id));
            return 0;
        }

        Currency currency = new Currency(id, displayName, CurrencyType.VIRTUAL, null, 1, 0,
                true, "default", "");
        registry.addCurrency(currency);

        // Save to file
        if (ServerEvents.getServer() != null) {
            java.nio.file.Path configDir = ServerEvents.getServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("serverconfig").resolve("ecosystem");
            registry.saveToDirectory(configDir);
        }

        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.COMMAND_CURRENCY_CREATED, id), true);
        return 1;
    }

    // ==================== AUDIT ====================

    private static int auditLast(CommandContext<CommandSourceStack> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        List<Transaction> txs = ServerEvents.getAuditLogger().queryLast(count, null, null);
        sendAuditResults(ctx, txs);
        return 1;
    }

    private static int auditLastPlayer(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        List<Transaction> txs = ServerEvents.getAuditLogger().queryLast(count, target.getUUID(), null);
        sendAuditResults(ctx, txs);
        return 1;
    }

    private static int auditLastPlayerCurrency(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String currency = StringArgumentType.getString(ctx, "currency");
        List<Transaction> txs = ServerEvents.getAuditLogger().queryLast(count, target.getUUID(), currency);
        sendAuditResults(ctx, txs);
        return 1;
    }

    private static void sendAuditResults(CommandContext<CommandSourceStack> ctx, List<Transaction> txs) {
        if (txs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.COMMAND_NO_TRANSACTIONS), false);
            return;
        }
        MutableComponent msg = Component.translatable(LangKeys.COMMAND_AUDIT_HEADER).append("\n");
        for (Transaction tx : txs) {
            String from = tx.from() != null ? tx.from().toString().substring(0, 8) + "..." : "---";
            String to = tx.to() != null ? tx.to().toString().substring(0, 8) + "..." : "---";
            msg.append(Component.translatable(LangKeys.COMMAND_AUDIT_ENTRY,
                    tx.timestamp().toString().substring(11, 19),
                    tx.type().name(),
                    from, to, tx.amount(), tx.taxAmount())).append("\n");
        }
        ctx.getSource().sendSuccess(() -> msg, false);
    }

    // ==================== SHOP ADMIN COMMANDS ====================

    private static int shopInspect(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        BlockEntity be = ctx.getSource().getLevel().getBlockEntity(pos);

        if (!(be instanceof ShopTerminalBlockEntity terminal)) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.INSPECT_NO_SHOP));
            return 0;
        }

        MutableComponent msg = Component.translatable(LangKeys.INSPECT_HEADER).append("\n");
        msg.append(Component.translatable(LangKeys.INSPECT_OWNER, terminal.getOwnerName())).append("\n");
        msg.append(Component.translatable(LangKeys.INSPECT_TYPE,
                terminal.isAdminShop() ? "Admin" : "Player")).append("\n");
        msg.append(Component.translatable(LangKeys.INSPECT_CURRENCY, terminal.getCurrencyId())).append("\n");

        if (terminal.getLinkedChestPos() != null) {
            msg.append(Component.translatable(LangKeys.INSPECT_CHEST,
                    terminal.getLinkedChestPos().toShortString())).append("\n");
        }

        for (int i = 0; i < terminal.getListings().size(); i++) {
            ShopTerminalBlockEntity.ShopListing l = terminal.getListings().get(i);
            String buy = l.canBuy() ? String.valueOf(l.buyPrice()) : "—";
            String sell = l.canSell() ? String.valueOf(l.sellPrice()) : "—";
            msg.append(Component.translatable(LangKeys.INSPECT_LISTING,
                    String.valueOf(i), l.displayName(), buy, sell)).append("\n");

            if (!terminal.isAdminShop()) {
                int stock = terminal.countStock(l.itemId());
                msg.append(Component.translatable(LangKeys.INSPECT_STOCK, String.valueOf(stock))).append("\n");
            }
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int shopRemove(CommandContext<CommandSourceStack> ctx) {
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        BlockEntity be = ctx.getSource().getLevel().getBlockEntity(pos);

        if (!(be instanceof ShopTerminalBlockEntity terminal)) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.INSPECT_NO_SHOP));
            return 0;
        }

        // Clear all shop data
        terminal.clearListings();
        terminal.setOwnerName("");
        terminal.setOwnerUuid(null);
        terminal.setAdminShop(false);
        terminal.setLinkedChestPos(null);
        terminal.setChanged();

        ctx.getSource().sendSuccess(
                () -> Component.translatable(LangKeys.SHOP_REMOVED, pos.toShortString()), true);
        return 1;
    }

    // ==================== HELPERS ====================

    private static Currency getCurrency(CommandContext<CommandSourceStack> ctx, String id) {
        CurrencyRegistry registry = ServerEvents.getCurrencyRegistry();
        if (registry == null) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.ERROR_GENERIC));
            return null;
        }
        Currency c = registry.getCurrency(id);
        if (c == null) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.COMMAND_CURRENCY_UNKNOWN, id));
            return null;
        }
        return c;
    }
}
