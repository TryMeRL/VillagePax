package com.villagepax.sim.build;

import java.util.Optional;

/**
 * На какой отметке положить пол здания, стоящего на земле.
 * <p>
 * Прежде пол брался по высоте <b>одного угла</b> — того, что оказался
 * якорем. На склоне это означало лотерею: выпал верхний угол — дом задран
 * над склоном, крыльцо спускается по воздуху четырьмя ступенями; выпал
 * нижний — полдома уходит в холм. В сохранении заказчика стояло и то,
 * и другое, и жалоба была одна: «куча лестниц».
 * <p>
 * Теперь пол выбирается так, как выбрал бы его плотник: по <b>всей
 * площадке</b> — чтобы меньше всего земли пришлось срывать и подсыпать, —
 * и по <b>входу</b> — чтобы порог встал на ступень выше земли перед
 * дверью. Площадка весит вдвое больше входа: обрыв у двери поправит
 * откос, а утопленный целиком дом не поправит ничто.
 * <p>
 * Чистая арифметика над числами высот, поэтому проверяется без игры.
 */
public final class FloorChoice {

    /** Земли в колонне нет: вода, обрыв, незагруженный чанк. */
    public static final int NO_GROUND = Integer.MIN_VALUE;

    /** Во сколько раз блок средней земляной работы дороже блока несовпадения с входом. */
    private static final double EARTHWORK_WEIGHT = 2.0;

    private FloorChoice() {
    }

    /**
     * Выбранный пол.
     *
     * @param y             отметка пола — высота первой пустой клетки над землёй,
     *                      как у якоря здания
     * @param meanDeviation сколько в среднем на клетку следа срывать или подсыпать
     * @param mismatch      насколько пол разошёлся с отметкой, удобной для входа
     */
    public record Floor(int y, double meanDeviation, int mismatch) {
    }

    /**
     * Лучшая отметка пола для площадки с такими высотами.
     *
     * @param heights высоты хода по всем клеткам следа
     * @param allowed насколько любая клетка может отстоять от пола — уклон народа
     * @param ideal   пол, при котором порог встаёт на ступень над землёй у двери,
     *                или {@link #NO_GROUND}, если входа нет или перед ним пусто
     * @return отметка или пусто, если площадка неровнее, чем позволено
     */
    public static Optional<Floor> choose(int[] heights, int allowed, int ideal) {
        if (heights.length == 0) {
            return Optional.empty();
        }
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int height : heights) {
            if (height == NO_GROUND) {
                // Дыра в площадке — это не склон, а место, где строить нельзя.
                return Optional.empty();
            }
            low = Math.min(low, height);
            high = Math.max(high, height);
        }

        Floor best = null;
        double bestCost = Double.MAX_VALUE;
        // Годны только отметки, от которых ни одна клетка не дальше
        // позволенного: сверху её держит нижняя точка, снизу — верхняя.
        for (int floor = high - allowed; floor <= low + allowed; floor++) {
            double deviation = meanDeviation(heights, floor);
            int mismatch = ideal == NO_GROUND ? 0 : Math.abs(floor - ideal);
            double cost = EARTHWORK_WEIGHT * deviation + mismatch;
            // При равной цене — ближе ко входу, а из равных и по входу —
            // нижняя: вкопанный дом выглядит вкопанным, поднятый — на тумбе.
            if (best == null || cost < bestCost - 1e-9
                    || (Math.abs(cost - bestCost) <= 1e-9 && mismatch < best.mismatch())) {
                best = new Floor(floor, deviation, mismatch);
                bestCost = cost;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Сколько в среднем на клетку отстоит земля от этой отметки. */
    public static double meanDeviation(int[] heights, int floor) {
        long total = 0;
        for (int height : heights) {
            total += Math.abs(height - floor);
        }
        return heights.length == 0 ? 0 : (double) total / heights.length;
    }
}
