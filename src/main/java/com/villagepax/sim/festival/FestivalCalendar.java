package com.villagepax.sim.festival;

/**
 * Календарь праздников: у народа праздник раз в лунный месяц, в свою фазу луны.
 * <p>
 * Чистая арифметика по номеру дня, и потому ничего не хранится: день праздника
 * не записан ни в поселении, ни в мире, его спрашивают у календаря. Хранимый
 * «следующий праздник» разошёлся бы с календарём при первой же команде
 * {@code /time set}, а вычисленный разойтись не может.
 * <p>
 * Фаза — та же, что рисует небо: ванильная {@code getMoonPhase} делит время
 * на сутки и берёт остаток от восьми. Поэтому норманнская ярмарка в полнолуние —
 * это действительно полная луна над площадью, а праздник фонарей ямато —
 * тёмная ночь новолуния.
 */
public final class FestivalCalendar {

    /** Лунный месяц Minecraft: восемь фаз, по одной на сутки. */
    public static final int LUNAR_MONTH = 8;

    private FestivalCalendar() {
    }

    /** Фаза луны дня: 0 — полнолуние, 4 — новолуние. */
    public static int moonPhase(long day) {
        return (int) Math.floorMod(day, (long) LUNAR_MONTH);
    }

    /** Праздник ли в этот день у народа с этой фазой. */
    public static boolean isFestivalDay(long day, int phase) {
        return moonPhase(day) == Math.floorMod(phase, LUNAR_MONTH);
    }

    /** Сколько дней до праздника: 0 — он сегодня. */
    public static int daysUntil(long today, int phase) {
        return Math.floorMod(Math.floorMod(phase, LUNAR_MONTH) - moonPhase(today), LUNAR_MONTH);
    }
}
