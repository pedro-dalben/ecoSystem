package com.pedrodalben.ecosystem.shop.chest;

import com.mojang.serialization.MapCodec;
import com.pedrodalben.ecosystem.core.LangKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Shop Terminal block — player-placed block that acts as a chest shop
 * interface.
 * Links to an adjacent chest for item storage/stock.
 * Supports both player shops (chest-backed) and admin shops (unlimited).
 */
public class ShopTerminalBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<ShopTerminalBlock> CODEC = simpleCodec(ShopTerminalBlock::new);

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    public ShopTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ShopTerminalBlockEntity terminalBE) {
            ServerPlayer serverPlayer = (ServerPlayer) player;

            // Sneak-click for config — owner or players with appropriate permission
            if (player.isShiftKeyDown()) {
                boolean isOwner = terminalBE.isOwner(player.getUUID());
                boolean canConfigOwn = isOwner && ShopPermission.SHOP_CREATE.hasPermission(player);
                boolean canConfigAdmin = terminalBE.isAdminShop()
                        && ShopPermission.SHOP_CREATE_ADMIN.hasPermission(player);
                boolean canConfigOther = !isOwner && ShopPermission.SHOP_DESTROY_OTHER.hasPermission(player);

                if (canConfigOwn || canConfigAdmin || canConfigOther) {
                    serverPlayer.openMenu(ShopTerminalConfigMenu.provider(pos, terminalBE));
                    return InteractionResult.SUCCESS;
                }
            }

            // Regular click: open the shop view
            if (terminalBE.isConfigured()) {
                // Check buy/sell permission
                if (!ShopPermission.SHOP_BUY.hasPermission(player)
                        && !ShopPermission.SHOP_SELL.hasPermission(player)) {
                    serverPlayer.sendSystemMessage(Component.translatable(LangKeys.ERROR_NO_PERMISSION));
                    return InteractionResult.SUCCESS;
                }

                // Prevent owner from trading at own shop (unless admin shop)
                if (terminalBE.isOwner(player.getUUID()) && !terminalBE.isAdminShop()) {
                    serverPlayer.sendSystemMessage(Component.translatable(LangKeys.SIGN_ERROR_OWN_SHOP));
                    return InteractionResult.SUCCESS;
                }

                serverPlayer.openMenu(ChestShopMenu.provider(pos, terminalBE));
            } else {
                serverPlayer.sendSystemMessage(Component.translatable(LangKeys.SHOP_NOT_CONFIGURED));
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock())) {
            // Permission check for shop destruction is done via playerWillDestroy
            super.onRemove(oldState, level, pos, newState, movedByPiston);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShopTerminalBlockEntity terminalBE) {
                boolean isOwner = terminalBE.isOwner(player.getUUID());

                if (isOwner) {
                    if (!ShopPermission.SHOP_DESTROY.hasPermission(player)) {
                        player.sendSystemMessage(Component.translatable(LangKeys.ERROR_NO_PERMISSION));
                        return state; // Block break prevented
                    }
                } else {
                    if (!ShopPermission.SHOP_DESTROY_OTHER.hasPermission(player)) {
                        player.sendSystemMessage(Component.translatable(LangKeys.ERROR_NO_PERMISSION));
                        return state; // Block break prevented
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopTerminalBlockEntity(pos, state);
    }
}
