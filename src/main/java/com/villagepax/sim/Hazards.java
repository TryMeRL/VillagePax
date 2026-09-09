package com.villagepax.sim;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.BlockPos;

/**
 * Что жжётся и колется.
 * <p>
 * Написано по следам смерти жителя: билдер сгорел на костре, перестраивая
 * дом. Костёр стоял в очаге посреди комнаты, житель оказался в нём — и
 * <b>не смог выйти</b>. Это не выдумка, а известная ванильная ловушка:
 * узел пути в огне непроходим, а значит непроходима и точка, из которой
 * путь начинается. Мобу, оказавшемуся в костре, навигация не строит
 * маршрут вообще — он стоит и горит.
 * <p>
 * Отсюда правило: жители <b>не наступают</b> на опасное, им <b>не ставят</b>
 * опасное под ноги, и если они всё-таки там оказались — их оттуда снимают.
 * Три меры вместо одной потому, что попасть в костёр можно тремя разными
 * путями, и закрыть надо все.
 */
public final class Hazards {

    private Hazards() {
    }

    /**
     * Повредит ли этот блок тому, кто в нём или на нём стоит.
     * <p>
     * Костёр считается опасным и потушенным: разница в одном состоянии
     * блока, а житель, наступивший на потушенный очаг, всё равно выглядит
     * так, будто ему всё равно, куда идти.
     */
    public static boolean hurts(BlockState state) {
        return state.isIn(BlockTags.FIRE)
                || state.isOf(Blocks.CAMPFIRE)
                || state.isOf(Blocks.SOUL_CAMPFIRE)
                || state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.WITHER_ROSE)
                || state.isOf(Blocks.POWDER_SNOW);
    }

    /**
     * Опасно ли <b>стоять</b> в этой точке: под ногами, в ногах или
     * на уровне головы.
     * <p>
     * Все три уровня, а не только пол: в костёр можно войти ногами,
     * а под лавой можно свариться, не касаясь её.
     */
    public static boolean standingHurts(BlockView world, BlockPos spot) {
        return hurts(world.getBlockState(spot.down()))
                || hurts(world.getBlockState(spot))
                || hurts(world.getBlockState(spot.up()));
    }
}
