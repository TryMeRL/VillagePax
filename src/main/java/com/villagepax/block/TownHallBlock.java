package com.villagepax.block;

import com.villagepax.block.entity.TownHallBlockEntity;
import com.villagepax.sim.SettlementManager;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Ратуша — центр поселения и точка входа в его интерфейс.
 */
public class TownHallBlock extends BlockWithEntity {

    public TownHallBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TownHallBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        // BlockWithEntity по умолчанию невидим — это нужно только тем блокам,
        // которые целиком рисуются рендерером блок-энтити.
        return BlockRenderType.MODEL;
    }

    /**
     * Правый щелчок открывает хранилище колонии.
     * <p>
     * До задачи 1.10 это единственное, что игрок может делать с ратушей, —
     * и этого достаточно, чтобы билдеру было из чего строить.
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof TownHallBlockEntity hall) {
            player.openHandledScreen(hall);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    /**
     * Снос ратуши <b>не</b> распускает колонию.
     * <p>
     * Это то же правило, что и для войны: здания повреждаются и восстанавливаются,
     * но двести часов работы не должны исчезать от одного неверного клика.
     * Поселение остаётся, ратушу нужно отстроить заново.
     */
    /** Содержимое хранилища при сносе высыпается, а не исчезает. */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof TownHallBlockEntity hall) {
            ItemScatterer.spawn(world, pos, hall);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (world instanceof ServerWorld serverWorld) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof TownHallBlockEntity townHall) {
                townHall.settlementId()
                        .flatMap(id -> SettlementManager.get(serverWorld).byId(id))
                        .ifPresent(settlement -> player.sendMessage(
                                Text.translatable("villagepax.town_hall.destroyed", settlement.name()), false));
            }
        }
        super.onBreak(world, pos, state, player);
    }
}
