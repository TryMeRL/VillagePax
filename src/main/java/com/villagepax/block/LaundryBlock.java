package com.villagepax.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Бельё на верёвке: висит, колышется и капает.
 * <p>
 * Заказчик назвал его первым среди некрасивого: «на руке изменена,
 * а в игре всё так же ущербно». Он был прав дважды. Картинку я
 * перерисовал, а <b>модель осталась плитой</b> — одним ящиком с одной
 * и той же картинкой на всех шести гранях; в мире это читалось доской,
 * а не рубахой. Теперь это верёвка и две рубахи под ней, каждая своей
 * толщины и повёрнутая по-своему.
 * <p>
 * И капли. Декор, который просто стоит, — это наклейка; мокрое бельё
 * капает, и от этой мелочи двор оживает сильнее, чем от любой новой
 * текстуры. Частицы рисует клиент и только рядом с игроком: сервер
 * о них не знает вовсе.
 */
public class LaundryBlock extends Block {

    /** Раз в сколько тиков в среднем срывается капля. Реже — не заметно. */
    private static final int DRIP_CHANCE = 6;

    /** След белья: верёвка поверху и полотно под ней. */
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(0, 14.5, 7, 16, 15.5, 9),
            createCuboidShape(1.5, 3, 7, 15, 14.5, 9));

    public LaundryBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return SHAPE;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(DRIP_CHANCE) != 0) {
            return;
        }
        // Из-под полотна, а не из середины блока: капля должна срываться
        // с подола, иначе она висит в воздухе.
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double y = pos.getY() + 0.15 + random.nextDouble() * 0.15;
        double z = pos.getZ() + 0.5;
        world.addParticle(ParticleTypes.FALLING_WATER, x, y, z, 0.0, 0.0, 0.0);
    }
}
