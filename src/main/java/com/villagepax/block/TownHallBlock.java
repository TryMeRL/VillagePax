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
     * Снос ратуши <b>не</b> распускает колонию.
     * <p>
     * Это то же правило, что и для войны: здания повреждаются и восстанавливаются,
     * но двести часов работы не должны исчезать от одного неверного клика.
     * Поселение остаётся, ратушу нужно отстроить заново.
     */
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
