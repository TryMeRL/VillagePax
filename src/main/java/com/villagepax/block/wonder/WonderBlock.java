package com.villagepax.block.wonder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Диковинка без лица: стоит одинаково со всех сторон и занимает свой след.
 * <p>
 * Торо, лампа, банка, кадильница, фонтан и барабан круглы или квадратны,
 * и поворачивать их по взгляду поставившего незачем.
 */
public class WonderBlock extends Block {

    private final VoxelShape shape;

    public WonderBlock(VoxelShape shape, Settings settings) {
        super(settings);
        this.shape = shape;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return shape;
    }
}
