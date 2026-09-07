package com.villagepax.sim.build;

import net.minecraft.util.math.BlockPos;

import java.util.Comparator;

/**
 * Один шаг стройки: что билдер делает в этой точке.
 * <p>
 * Блок задан <b>индексом в палитре схемы</b>, а не самим блокстейтом. Это
 * держит шаг чистым: порядок стройки и его детерминированность проверяются
 * без запуска игры, потому что реестр блоков для сравнения индексов не нужен.
 *
 * @param pos          позиция относительно якоря схемы
 * @param category     фаза стройки, к которой относится шаг
 * @param paletteIndex индекс блока в палитре схемы, или {@link #NO_BLOCK}
 */
public record BuildStep(BlockPos pos, BuildCategory category, int paletteIndex) {

    /** Расчистке ставить нечего: она только освобождает место. */
    public static final int NO_BLOCK = -1;

    /**
     * Порядок стройки. Обязан быть <b>полным</b>: иначе два разбора одной схемы
     * дали бы разные последовательности, и стройка перестала бы быть
     * воспроизводимой — а вместе с ней и игровой тест на неё.
     * <p>
     * Позиция шага уникальна в плане, значит четвёрка «категория, слой, x, z»
     * ничьей не допускает.
     */
    public static final Comparator<BuildStep> ORDER = Comparator
            .comparingInt((BuildStep step) -> step.category.ordinal())
            .thenComparingInt(BuildStep::layerKey)
            .thenComparingInt(step -> step.pos.getX())
            .thenComparingInt(step -> step.pos.getZ());

    public BuildStep {
        // BlockPos.Mutable наследует BlockPos, поэтому запись может незаметно
        // получить меняющуюся позицию — обход схемы курсором ровно это и даёт.
        pos = pos.toImmutable();

        if (category == BuildCategory.CLEAR && paletteIndex != NO_BLOCK) {
            throw new IllegalArgumentException(
                    "расчистка не ставит блок, а индекс палитры задан: " + paletteIndex);
        }
        if (category != BuildCategory.CLEAR && paletteIndex < 0) {
            throw new IllegalArgumentException(
                    "шагу " + category.id() + " нужен блок, а индекс палитры " + paletteIndex);
        }
    }

    public static BuildStep clearing(BlockPos pos) {
        return new BuildStep(pos, BuildCategory.CLEAR, NO_BLOCK);
    }

    public static BuildStep placing(BlockPos pos, BuildCategory category, int paletteIndex) {
        return new BuildStep(pos, category, paletteIndex);
    }

    public boolean placesBlock() {
        return paletteIndex != NO_BLOCK;
    }

    /**
     * Ключ слоя с учётом направления обхода: расчистка идёт сверху вниз,
     * установка снизу вверх. Координаты блоков ограничены игрой намного
     * раньше, чем смена знака могла бы переполниться.
     */
    private int layerKey() {
        return category.topDown() ? -pos.getY() : pos.getY();
    }
}
