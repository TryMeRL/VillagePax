package com.villagepax.sim.build;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Пол здания по рельефу: вся площадка и вход, а не один угол.
 * <p>
 * Прежде пол брался по высоте угла-якоря. На склоне это давало дом,
 * задранный на верхнюю отметку, и крыльцо в четыре ступени по воздуху —
 * в сохранении заказчика ровно так стояла изба пони.
 */
class FloorChoiceTest {

    /** Высоты хода по клеткам следа: строки одинаковы, меняется только x. */
    private static int[] rows(int depth, int... columns) {
        int[] heights = new int[columns.length * depth];
        for (int z = 0; z < depth; z++) {
            System.arraycopy(columns, 0, heights, z * columns.length, columns.length);
        }
        return heights;
    }

    @Test
    void flatGroundKeepsItsLevel() {
        int[] flat = new int[49];
        Arrays.fill(flat, 70);

        FloorChoice.Floor floor = FloorChoice.choose(flat, 2, 70).orElseThrow();
        assertEquals(70, floor.y());
        assertEquals(0.0, floor.meanDeviation());
    }

    @Test
    void aSlopeIsCutAndFilledFromTheMiddle() {
        // Угол-якорь взял бы 70 или 73 — и треть дома висела бы
        // или ушла бы в холм на три блока.
        int[] slope = rows(7, 70, 70, 71, 71, 72, 72, 73);

        FloorChoice.Floor floor = FloorChoice.choose(slope, 2, FloorChoice.NO_GROUND)
                .orElseThrow();
        assertEquals(71, floor.y());
    }

    @Test
    void aDropAtTheDoorDoesNotSinkTheWholeHouse() {
        // Обрыв в два блока прямо у входа — дело откоса и крыльца,
        // а не повод утопить в землю весь дом.
        int[] flat = new int[49];
        Arrays.fill(flat, 70);

        assertEquals(70, FloorChoice.choose(flat, 2, 68).orElseThrow().y());
    }

    @Test
    void aGentleDoorSideWinsATie() {
        // Поровну двух отметок: берётся та, что вровень со входом.
        int[] halves = rows(4, 70, 70, 71, 71);

        assertEquals(71, FloorChoice.choose(halves, 2, 71).orElseThrow().y());
        assertEquals(70, FloorChoice.choose(halves, 2, 70).orElseThrow().y());
    }

    @Test
    void withoutADoorATieGoesDownhill() {
        // Утопленный на полблока дом выглядит вкопанным, а поднятый —
        // стоящим на тумбе; из равных берётся нижний.
        int[] halves = rows(4, 70, 70, 71, 71);

        assertEquals(70, FloorChoice.choose(halves, 2, FloorChoice.NO_GROUND)
                .orElseThrow().y());
    }

    @Test
    void tooRoughGroundHasNoFloor() {
        assertTrue(FloorChoice.choose(rows(3, 70, 71), 0, 70).isEmpty());
        assertTrue(FloorChoice.choose(rows(3, 70, 75), 2, 70).isEmpty());
    }

    @Test
    void aMissingColumnHasNoFloor() {
        int[] holed = new int[9];
        Arrays.fill(holed, 70);
        holed[4] = FloorChoice.NO_GROUND;

        assertTrue(FloorChoice.choose(holed, 2, 70).isEmpty());
    }

    @Test
    void meanDeviationCountsEveryCell() {
        assertEquals(6.0 / 7.0, FloorChoice.meanDeviation(
                new int[]{70, 70, 71, 71, 72, 72, 73}, 71), 1e-9);
    }
}
