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
 * @param paletteIndex индекс блока в палитре схемы, или {@link #NO_BLOCK}; у расчистки
 *                     под посев — то, что в эту клетку посеют потом
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

        if (category == BuildCategory.CLEAR && paletteIndex < NO_BLOCK) {
            throw new IllegalArgumentException(
                    "расчистке под посев нужен блок посева, а индекс палитры " + paletteIndex);
        }
        if (category != BuildCategory.CLEAR && paletteIndex < 0) {
            throw new IllegalArgumentException(
                    "шагу " + category.id() + " нужен блок, а индекс палитры " + paletteIndex);
        }
    }

    public static BuildStep clearing(BlockPos pos) {
        return new BuildStep(pos, BuildCategory.CLEAR, NO_BLOCK);
    }

    /**
     * Расчистка под посев: клетка пустеет вместе со всем объёмом, а сеют
     * в неё последним шагом.
     * <p>
     * Грядку нельзя оставить породой до самого посева: пашня, легшая под
     * камень, через тик сама становится землёй, и морковь потом осыпается
     * с неё. Какой посев сюда придёт, шаг помнит затем, чтобы ремонт
     * не перепахивал засеянное: уже стоящая морковь расчистке не мешает.
     *
     * @param sown индекс посева в палитре схемы
     */
    public static BuildStep clearingFor(BlockPos pos, int sown) {
        if (sown < 0) {
            throw new IllegalArgumentException("расчистке под посев нужен посев: " + sown);
        }
        return new BuildStep(pos, BuildCategory.CLEAR, sown);
    }

    public static BuildStep placing(BlockPos pos, BuildCategory category, int paletteIndex) {
        return new BuildStep(pos, category, paletteIndex);
    }

    public boolean placesBlock() {
        return category != BuildCategory.CLEAR;
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
