package com.villagepax.block.festival;

import net.minecraft.block.BlockState;
import net.minecraft.block.CakeBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Праздничный пирог на столе ярмарки: едят по куску, как торт.
 * <p>
 * От ванильного торта отличается двумя вещами, и обе ради того, чтобы
 * праздник мог за собой убрать.
 * <ul>
 *   <li><b>Стоит без опоры.</b> Стол мебель, а не куб, и ванильный торт
 *       на нём не держится: при первом же обновлении соседа он осыпался
 *       бы в ничто, и стол праздника оставался бы пустым.</li>
 *   <li><b>Свечу не принимает.</b> Торт со свечой — другой, ванильный
 *       блок, и уборка на следующий день его бы не узнала: на столе
 *       ярмарки навсегда остался бы чужой торт.</li>
 * </ul>
 * Ничего не роняет, если его сломать: пирог едят, а не уносят.
 */
public class FeastPieBlock extends CakeBlock {

    public FeastPieBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient()) {
            if (tryEat(world, pos, state, player).isAccepted()) {
                return ActionResult.SUCCESS;
            }
            if (player.getStackInHand(hand).isEmpty()) {
                return ActionResult.CONSUME;
            }
        }
        return tryEat(world, pos, state, player);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
                                                BlockState neighbour, WorldAccess world,
                                                BlockPos pos, BlockPos neighbourPos) {
        return state;
    }
}
