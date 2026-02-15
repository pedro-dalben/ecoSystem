package com.pedrodalben.ecosystem.shop.chest;

import com.pedrodalben.ecosystem.core.EcoSystemConfig;
import com.pedrodalben.ecosystem.core.LangKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event handler for sign-based shop creation, inspired by ChestShop-3.
 *
 * When a player places a sign and writes 4 lines matching the shop format,
 * this handler validates the input and creates a ShopTerminalBlockEntity
 * on an adjacent chest or on the sign's own position.
 *
 * <p>
 * Format:
 * 
 * <pre>
 *   Line 0: Owner name (or "Admin Shop")
 *   Line 1: Quantity (1–999999)
 *   Line 2: Price ("B 50", "S 25", "B 50:S 25")
 *   Line 3: Item ID ("minecraft:diamond")
 * </pre>
 */
public class ChestShopSignHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("EcoSystem");

    /**
     * Attempt to create a shop from a sign.
     * Called from a server-side context (e.g., a command or direct invocation).
     *
     * @param player  the player creating the shop
     * @param signPos the position of the sign
     * @param lines   the 4 sign lines
     * @return true if shop was created successfully
     */
    public static boolean tryCreateFromSign(ServerPlayer player, BlockPos signPos, String[] lines) {
        if (!EcoSystemConfig.ENABLE_CHEST_SHOP.get()) {
            player.sendSystemMessage(Component.translatable(LangKeys.ERROR_FEATURE_DISABLED));
            return false;
        }

        // Parse sign lines
        ChestShopSignParser.ParseResult result = ChestShopSignParser.parse(lines);
        if (!result.valid()) {
            player.sendSystemMessage(Component.translatable(
                    result.errorKey() != null ? result.errorKey() : LangKeys.ERROR_GENERIC));
            return false;
        }

        // Permission checks
        if (result.adminShop()) {
            if (!ShopPermission.SHOP_CREATE_ADMIN.hasPermission(player)) {
                player.sendSystemMessage(Component.translatable(LangKeys.ERROR_NO_PERMISSION));
                return false;
            }
        } else {
            if (!ShopPermission.SHOP_CREATE.hasPermission(player)) {
                player.sendSystemMessage(Component.translatable(LangKeys.ERROR_NO_PERMISSION));
                return false;
            }

            // For player shops: verify the owner name matches the player
            // (unless they have admin create permission)
            if (!result.ownerName().equalsIgnoreCase(player.getGameProfile().getName())
                    && !ShopPermission.SHOP_CREATE_ADMIN.hasPermission(player)) {
                player.sendSystemMessage(Component.translatable(LangKeys.SIGN_ERROR_NOT_YOUR_NAME));
                return false;
            }
        }

        Level level = player.level();

        // For player shops: find an adjacent chest
        BlockPos chestPos = null;
        if (!result.adminShop()) {
            chestPos = findAdjacentChest(level, signPos);
            if (chestPos == null) {
                player.sendSystemMessage(Component.translatable(LangKeys.SHOP_CONFIG_NO_CHEST));
                return false;
            }
        }

        // Check if there's already a shop terminal at or near this sign
        // We place the shop data on the sign's block position
        // First, ensure a ShopTerminalBlockEntity can be associated
        // In NeoForge, we need the ShopTerminalBlock to exist — so we place it
        // For the sign-based approach, we instead store data in a companion block
        // entity
        // OR we use the existing ShopTerminalBlock approach.
        //
        // Implementation: We look for an existing ShopTerminalBlock adjacent to the
        // sign.
        // If none exists, we create shop data on the chest's block entity via NBT.
        // For simplicity, we'll look for an adjacent ShopTerminalBlock to configure.
        BlockPos terminalPos = findAdjacentTerminal(level, signPos);

        if (terminalPos == null) {
            // No terminal block found — tell the player to place one
            player.sendSystemMessage(Component.translatable(LangKeys.SIGN_ERROR_NO_TERMINAL));
            return false;
        }

        BlockEntity be = level.getBlockEntity(terminalPos);
        if (!(be instanceof ShopTerminalBlockEntity terminal)) {
            player.sendSystemMessage(Component.translatable(LangKeys.ERROR_SHOP_NOT_FOUND));
            return false;
        }

        // Configure the terminal from sign data
        terminal.clearListings();
        terminal.setOwnerName(result.ownerName());
        terminal.setAdminShop(result.adminShop());

        if (!result.adminShop()) {
            terminal.setOwnerUuid(player.getUUID());
            terminal.setLinkedChestPos(chestPos);
        } else {
            terminal.setOwnerUuid(null);
            terminal.setLinkedChestPos(null);
        }

        // Add the listing from sign
        terminal.addListing(new ShopTerminalBlockEntity.ShopListing(
                result.itemId(),
                result.itemDisplayName(),
                result.buyPrice(),
                result.sellPrice(),
                result.canBuy(),
                result.canSell()));

        terminal.setChanged();

        // Notify player
        String shopType = result.adminShop()
                ? Component.translatable(LangKeys.SHOP_ADMIN_MODE).getString()
                : Component.translatable(LangKeys.SHOP_PLAYER_MODE).getString();

        player.sendSystemMessage(Component.translatable(LangKeys.SIGN_SHOP_CREATED,
                shopType, result.itemDisplayName(), result.quantity()));

        LOGGER.info("EcoSystem: {} created {} shop at {} for item {} (B:{}/S:{})",
                player.getGameProfile().getName(),
                result.adminShop() ? "admin" : "player",
                terminalPos.toShortString(),
                result.itemId(),
                result.buyPrice(),
                result.sellPrice());

        return true;
    }

    /**
     * Find an adjacent chest block entity.
     */
    private static BlockPos findAdjacentChest(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos adj = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(adj);
            if (be instanceof ChestBlockEntity) {
                return adj;
            }
        }
        return null;
    }

    /**
     * Find an adjacent ShopTerminalBlockEntity.
     */
    private static BlockPos findAdjacentTerminal(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos adj = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(adj);
            if (be instanceof ShopTerminalBlockEntity) {
                return adj;
            }
        }
        return null;
    }
}
