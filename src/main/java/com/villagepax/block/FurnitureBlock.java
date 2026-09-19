package com.villagepax.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Предмет обстановки: стоит лицом к тому, кто поставил.
 * <p>
 * Один класс на всю мебель, потому что разница между скамьёй, столом
 * и полкой — только в очертаниях и модели, а поведение у них общее:
 * повернуться по взгляду, не быть полным кубом и повернуться вместе
 * с домом, когда схему ставят боком.
 * <p>
 * Очертания поворачиваются вместе с блоком: скамья, стоящая поперёк,
 * с прежним следом ловила бы взгляд игрока в пустоте рядом с собой,
 * а сама пропускала бы стрелу насквозь.
 */
public class FurnitureBlock extends Block {

    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;

    private final VoxelShape shape;

    public FurnitureBlock(VoxelShape shape, Settings settings) {
        super(settings);
        this.shape = shape;
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        // Лицом к игроку: он смотрит на стол, а не стол на него.
        return getDefaultState().with(FACING, context.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return turned(shape, state.get(FACING));
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    /**
     * Повернуть очертания вслед за блоком.
     * <p>
     * Считается на каждый вопрос, а не кэшируется: очертаний четыре,
     * вопрос задаётся при наведении курсора, и экономить тут не на чем.
     */
    private static VoxelShape turned(VoxelShape north, Direction facing) {
        return switch (facing) {
            case SOUTH -> flip(north);
            case WEST -> rotate(north, true);
            case EAST -> rotate(north, false);
            default -> north;
        };
    }

    private static VoxelShape flip(VoxelShape shape) {
        VoxelShape[] turned = {net.minecraft.util.shape.VoxelShapes.empty()};
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) ->
                turned[0] = net.minecraft.util.shape.VoxelShapes.union(turned[0],
                        createCuboidShape(minX * 16, minY * 16, (1 - maxZ) * 16,
                                maxX * 16, maxY * 16, (1 - minZ) * 16)));
        return turned[0];
    }

    private static VoxelShape rotate(VoxelShape shape, boolean clockwise) {
        VoxelShape[] turned = {net.minecraft.util.shape.VoxelShapes.empty()};
        shape.forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
            double x1 = clockwise ? minZ : 1 - maxZ;
            double x2 = clockwise ? maxZ : 1 - minZ;
            double z1 = clockwise ? 1 - maxX : minX;
            double z2 = clockwise ? 1 - minX : maxX;
            turned[0] = net.minecraft.util.shape.VoxelShapes.union(turned[0],
                    createCuboidShape(x1 * 16, minY * 16, z1 * 16,
                            x2 * 16, maxY * 16, z2 * 16));
        });
        return turned[0];
    }
}
