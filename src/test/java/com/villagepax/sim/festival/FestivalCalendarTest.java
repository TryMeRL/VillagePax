package com.villagepax.sim.festival;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Календарь праздников — арифметика по номеру дня, и проверяется без игры.
 * <p>
 * Главное здесь — совпасть с небом. Праздник фонарей ямато назначен
 * на новолуние ради тёмной ночи, и календарь, разошедшийся с ванильной
 * фазой хоть на день, устроил бы его под полной луной.
 */
class FestivalCalendarTest {

    @Test
    void theMoonFollowsTheDayLikeVanilla() {
        for (long day = 0; day <= 40; day++) {
            long time = day * 24_000L + 100;
            // Ванильная DimensionType#getMoonPhase — буквально.
            int vanilla = (int) (time / 24000L % 8L + 8L) % 8;
            assertEquals(vanilla, FestivalCalendar.moonPhase(day), "день " + day);
        }
    }

    /**
     * Отрицательных дней в мире не бывает, но {@code /time set} умеет
     * убавлять, и прошлое должно считаться тем же кругом, а не отражаться
     * от нуля: вчерашний день нулевого — седьмая фаза.
     */
    @Test
    void thePastKeepsTheSameCircle() {
        assertEquals(7, FestivalCalendar.moonPhase(-1));
        assertEquals(0, FestivalCalendar.moonPhase(-8));
    }

    @Test
    void aPeopleCelebratesOncePerLunarMonth() {
        int festivals = 0;
        for (long day = 0; day < 80; day++) {
            if (FestivalCalendar.isFestivalDay(day, 4)) {
                festivals++;
            }
        }
        assertEquals(10, festivals);
    }

    @Test
    void daysUntilCountsForwardAndTodayIsZero() {
        assertEquals(0, FestivalCalendar.daysUntil(4, 4));
        assertEquals(3, FestivalCalendar.daysUntil(1, 4));
        assertEquals(7, FestivalCalendar.daysUntil(5, 4));
        assertEquals(1, FestivalCalendar.daysUntil(-1, 0));
    }
}
