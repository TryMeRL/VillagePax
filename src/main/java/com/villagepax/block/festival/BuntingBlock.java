package com.villagepax.block.festival;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Гирлянда флажков: бечева с флажками в цветах народа.
 * <p>
 * Висит в воздухе и не держится ни за что — так её и вешают между
 * столбами ярмарки, а игрок — между своими. Сквозь неё проходят:
 * гирлянда над головой, о которую спотыкаются, была бы не убранством,
 * а загородкой.
 * <p>
 * Ставится поперёк взгляда: тот, кто вешает флажки перед собой, хочет
 * видеть их лицом, а не ребром.
 */
public class BuntingBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;

    private static final VoxelShape ALONG_X = Block.createCuboidShape(0, 5, 7.5, 16, 13, 8.5);
    private static final VoxelShape ALONG_Z = Block.createCuboidShape(7.5, 5, 0, 8.5, 13, 16);

    public BuntingBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(AXIS, Direction.Axis.X));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return state.get(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        return getDefaultState().with(AXIS,
                context.getHorizontalPlayerFacing().rotateYClockwise().getAxis());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        if (rotation == BlockRotation.CLOCKWISE_90 || rotation == BlockRotation.COUNTERCLOCKWISE_90) {
            return state.with(AXIS, state.get(AXIS) == Direction.Axis.X
                    ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }
}
