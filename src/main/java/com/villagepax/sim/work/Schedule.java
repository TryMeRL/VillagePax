package com.villagepax.sim.work;

import com.villagepax.core.Named;

/**
 * Распорядок суток жителя.
 * <p>
 * Чистая арифметика по времени мира, и потому проверяется без запуска игры.
 * Сутки Minecraft — 24000 тиков: 0 — рассвет, 6000 — полдень, 12000 — закат,
 * 18000 — полночь.
 * <p>
 * Распорядок — выбор <b>чем заняться</b>, то есть решение стратегическое.
 * Тактике он ничего не добавляет: до кровати, до сундука с едой и до стройки
 * житель идёт одним и тем же способом. Именно поэтому распорядок не потребовал
 * перехода на {@code Brain}, хотя в задаче 1.7б это выглядело неизбежным.
 */
public enum Schedule implements Named {

    /** Утренняя работа. */
    MORNING_WORK("morning_work"),

    /** Обед: голодный житель идёт к еде, сытый работает дальше. */
    MEAL("meal"),

    /** Послеобеденная работа. */
    DAY_WORK("day_work"),

    /** Досуг: житель не работает. Это то, что делает деревню живой. */
    LEISURE("leisure"),

    /** Сон. Житель идёт к своему месту и ложится. */
    SLEEP("sleep");

    /** Полные сутки Minecraft. */
    public static final long DAY_LENGTH = 24_000L;

    private static final long MEAL_FROM = 5_000L;
    private static final long DAY_WORK_FROM = 7_000L;
    private static final long LEISURE_FROM = 11_000L;

    /** С какого часа суток досуг — вечерний сбор, песня и зов. */
    public static final long LEISURE_START = LEISURE_FROM;
    private static final long SLEEP_FROM = 13_000L;

    private final String id;

    Schedule(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public boolean isWork() {
        return this == MORNING_WORK || this == DAY_WORK;
    }

    /**
     * Чем житель занят в это время суток.
     *
     * @param timeOfDay время мира; отрицательное и большее суток приводится
     *                  к суткам, потому что {@code World.getTimeOfDay} растёт
     *                  без границ, а команда {@code /time set} умеет и убавлять
     */
    public static Schedule at(long timeOfDay) {
        long time = Math.floorMod(timeOfDay, DAY_LENGTH);

        if (time < MEAL_FROM) {
            return MORNING_WORK;
        }
        if (time < DAY_WORK_FROM) {
            return MEAL;
        }
        if (time < LEISURE_FROM) {
            return DAY_WORK;
        }
        if (time < SLEEP_FROM) {
            return LEISURE;
        }
        return SLEEP;
    }

    /** Номер игрового дня: по нему считаются суточные нужды. */
    public static long dayOf(long timeOfDay) {
        return Math.floorDiv(timeOfDay, DAY_LENGTH);
    }
}
