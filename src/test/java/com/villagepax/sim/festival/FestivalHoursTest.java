package com.villagepax.sim.festival;

import com.villagepax.sim.work.Schedule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Часы праздника — решение по игре, и числа здесь написаны руками.
 * <p>
 * Деревня народа гуляет весь день: игрок, пришедший в любой час, застаёт
 * праздник. Колония гуляет с послеобеденной работы: утро и обед как обычно,
 * и за праздник она платит половиной рабочего дня. Состязания — до заката,
 * потом фейерверк.
 */
class FestivalHoursTest {

    @Test
    void aVillageRevelsAllDayButAsleep() {
        assertTrue(FestivalDay.revels(true, Schedule.MORNING_WORK));
        assertTrue(FestivalDay.revels(true, Schedule.MEAL));
        assertTrue(FestivalDay.revels(true, Schedule.DAY_WORK));
        assertTrue(FestivalDay.revels(true, Schedule.LEISURE));
        assertFalse(FestivalDay.revels(true, Schedule.SLEEP));
    }

    @Test
    void aColonyWorksTillLunch() {
        assertFalse(FestivalDay.revels(false, Schedule.MORNING_WORK));
        assertFalse(FestivalDay.revels(false, Schedule.MEAL));
        assertTrue(FestivalDay.revels(false, Schedule.DAY_WORK));
        assertTrue(FestivalDay.revels(false, Schedule.LEISURE));
        assertFalse(FestivalDay.revels(false, Schedule.SLEEP));
    }

    /** Фейерверк — с заката до сна, и так каждый вечер праздника, а не только первый. */
    @Test
    void fireworksBurnFromDuskTillSleep() {
        assertFalse(Fireworks.isEvening(11_999));
        assertTrue(Fireworks.isEvening(12_000));
        assertTrue(Fireworks.isEvening(12_999));
        assertFalse(Fireworks.isEvening(13_000));
        assertFalse(Fireworks.isEvening(0));
        assertTrue(Fireworks.isEvening(8 * 24_000 + 12_500));
    }

    @Test
    void contestsRunTillDusk() {
        assertTrue(FestivalDay.contestsOpen(true, 0));
        assertTrue(FestivalDay.contestsOpen(true, 11_999));
        assertFalse(FestivalDay.contestsOpen(true, 12_000));
        assertFalse(FestivalDay.contestsOpen(false, 6_999));
        assertTrue(FestivalDay.contestsOpen(false, 7_000));
        assertTrue(FestivalDay.contestsOpen(false, 11_999));
        assertFalse(FestivalDay.contestsOpen(false, 12_000));
        // Время мира растёт без границ: сутки считаются по кругу.
        assertTrue(FestivalDay.contestsOpen(true, 24_000L * 57 + 3_000));
        assertFalse(FestivalDay.contestsOpen(false, 24_000L * 57 + 3_000));
    }
}
