package com.villagepax.block;

import com.villagepax.screen.BoardNet;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Доска заданий: щит на двух столбах, на нём листки на гвоздях.
 * <p>
 * Висит у ратуши каждой деревни народа. Щелчок открывает доску — по листку
 * на ремесло, у которого сегодня есть просьба, — и сдают прямо у неё: не
 * надо искать лесоруба по всей деревне, чтобы отдать ему саженцы.
 */
public class NoticeBoardBlock extends FurnitureBlock {

    public NoticeBoardBlock(VoxelShape shape, Settings settings) {
        super(shape, settings);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity server) {
            BoardNet.open(server, pos);
        }
        return ActionResult.CONSUME;
    }
}
