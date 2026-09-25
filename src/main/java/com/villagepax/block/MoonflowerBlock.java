package com.villagepax.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.FlowerBlock;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Лунник: эльфийский цветок, который светится и искрит по ночам.
 * <p>
 * Днём — просто бледный цветок у тропы. Ночью над ним поднимаются
 * искорки, и цветник у эльфийского дома горит, как роса под луной.
 * Свет ровный и слабый (семь из пятнадцати): им отмечают дорожку,
 * а не освещают двор.
 */
public class MoonflowerBlock extends FlowerBlock {

    public MoonflowerBlock(Settings settings) {
        // Похлёбка из лунника даёт ночное зрение — у ванильных цветов
        // у каждого своя похлёбка, и у эльфийского она к месту.
        super(StatusEffects.NIGHT_VISION, 8, settings);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        super.randomDisplayTick(state, world, pos, random);
        if (world.isDay() || random.nextInt(3) != 0) {
            return;
        }
        world.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.3 + random.nextDouble() * 0.4,
                pos.getY() + 0.6 + random.nextDouble() * 0.3,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                0.0, 0.015, 0.0);
    }
}
