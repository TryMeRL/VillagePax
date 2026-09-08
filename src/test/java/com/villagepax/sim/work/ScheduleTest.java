package com.villagepax.sim.work;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTest {

    @Test
    void dawnStartsTheWorkingDay() {
        assertEquals(Schedule.MORNING_WORK, Schedule.at(0));
        assertEquals(Schedule.MORNING_WORK, Schedule.at(4_999));
    }

    @Test
    void middayIsForEating() {
        assertEquals(Schedule.MEAL, Schedule.at(5_000));
        assertEquals(Schedule.MEAL, Schedule.at(6_999));
    }

    @Test
    void afternoonIsWorkAgain() {
        assertEquals(Schedule.DAY_WORK, Schedule.at(7_000));
        assertEquals(Schedule.DAY_WORK, Schedule.at(10_999));
    }

    @Test
    void eveningIsLeisureAndNightIsSleep() {
        assertEquals(Schedule.LEISURE, Schedule.at(11_000));
        assertEquals(Schedule.LEISURE, Schedule.at(12_999));
        assertEquals(Schedule.SLEEP, Schedule.at(13_000));
        assertEquals(Schedule.SLEEP, Schedule.at(23_999));
    }

    /**
     * {@code World.getTimeOfDay} растёт без границ, а {@code /time set} умеет
     * и убавлять. Без приведения к суткам житель на второй день перестал бы
     * ложиться спать вовсе.
     */
    @Test
    void timeWrapsAroundToTheSameDayParts() {
        for (long day = 0; day < 5; day++) {
            long start = day * Schedule.DAY_LENGTH;

            assertEquals(Schedule.MORNING_WORK, Schedule.at(start), "день " + day);
            assertEquals(Schedule.MEAL, Schedule.at(start + 6_000), "день " + day);
            assertEquals(Schedule.SLEEP, Schedule.at(start + 18_000), "день " + day);
        }
    }

    @Test
    void negativeTimeIsStillAValidTimeOfDay() {
        assertEquals(Schedule.SLEEP, Schedule.at(-1_000), "за тысячу тиков до рассвета — ночь");
        assertEquals(Schedule.MORNING_WORK, Schedule.at(-Schedule.DAY_LENGTH));
    }

    @Test
    void everyTickOfTheDayHasAPart() {
        for (long time = 0; time < Schedule.DAY_LENGTH; time += 97) {
            assertTrue(Schedule.at(time) != null, "тик " + time);
        }
    }

    @Test
    void workCoversBothWorkingParts() {
        assertTrue(Schedule.MORNING_WORK.isWork());
        assertTrue(Schedule.DAY_WORK.isWork());
        assertFalse(Schedule.MEAL.isWork());
        assertFalse(Schedule.LEISURE.isWork());
        assertFalse(Schedule.SLEEP.isWork());
    }

    @Test
    void dayNumberAdvancesAtDawn() {
        assertEquals(0, Schedule.dayOf(0));
        assertEquals(0, Schedule.dayOf(23_999));
        assertEquals(1, Schedule.dayOf(24_000));
        assertEquals(7, Schedule.dayOf(7 * Schedule.DAY_LENGTH + 500));
        assertEquals(-1, Schedule.dayOf(-1), "до начала мира день тоже считается");
    }
}
