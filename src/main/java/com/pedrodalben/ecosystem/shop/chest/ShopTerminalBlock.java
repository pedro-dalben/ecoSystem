package com.pedrodalben.ecosystem.shop.chest;

import com.mojang.serialization.MapCodec;
import com.pedrodalben.ecosystem.core.EcoSystemMod;
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

            // Owner or op can configure
            if (terminalBE.isOwner(player.getUUID()) || player.hasPermissions(2)) {
                // Sneak-click for config
                if (player.isShiftKeyDown()) {
                    serverPlayer.openMenu(ShopTerminalConfigMenu.provider(pos, terminalBE));
                    return InteractionResult.SUCCESS;
                }
            }

            // Regular click: open the shop view
            if (terminalBE.isConfigured()) {
                serverPlayer.openMenu(ChestShopMenu.provider(pos, terminalBE));
            } else {
                serverPlayer.sendSystemMessage(Component.translatable(LangKeys.SHOP_NOT_CONFIGURED));
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopTerminalBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!oldState.is(newState.getBlock())) {
            // Clean up block entity
            super.onRemove(oldState, level, pos, newState, movedByPiston);
        }
    }
}
