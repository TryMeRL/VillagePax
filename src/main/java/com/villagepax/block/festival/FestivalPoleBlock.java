package com.villagepax.block.festival;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

/**
 * Праздничный столб: майское дерево, шест воладоров, радужный столб.
 * <p>
 * Столб складывается из звеньев, поставленных одно на другое, и верхнее
 * звено носит венец — венок с лентами, раму воладоров, звезду. Верх
 * пересчитывается сам, когда над звеном ставят или снимают следующее:
 * столб, достроенный на звено, не должен нести венец посередине.
 * <p>
 * Это сердце праздника: вокруг него на ярмарке водят хоровод, от него
 * бьёт вечерний фейерверк. Ярмарка узнаёт своё сердце по тегу
 * {@code villagepax:festival_hearts}, а не по имени блока, — у каждого
 * народа оно своё.
 */
public class FestivalPoleBlock extends Block {

    /** Верхнее ли это звено — то, что носит венец. */
    public static final BooleanProperty TOP = BooleanProperty.of("top");

    private static final VoxelShape POLE = Block.createCuboidShape(6, 0, 6, 10, 16, 10);

    public FestivalPoleBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(TOP, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TOP);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return POLE;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState above = context.getWorld().getBlockState(context.getBlockPos().up());
        return getDefaultState().with(TOP, !above.isOf(this));
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
                                                BlockState neighbour, WorldAccess world,
                                                BlockPos pos, BlockPos neighbourPos) {
        return direction == Direction.UP ? state.with(TOP, !neighbour.isOf(this)) : state;
    }
}
