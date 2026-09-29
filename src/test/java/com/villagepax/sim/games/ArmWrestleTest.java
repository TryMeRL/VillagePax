package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Армрестлинг в такт — решение по игре, и числа здесь написаны руками.
 * <p>
 * Отметка бежит от края до края за двадцать четыре тика; в зелёном
 * посередине нажатие клонит руку соперника на восемнадцать, мимо — свою
 * на двенадцать. Соперник давит сам каждый тик, и чем он сильнее,
 * тем уже зелёное.
 */
class ArmWrestleTest {

    private static void ticks(ArmWrestle bout, int many) {
        for (int i = 0; i < many; i++) {
            bout.tick();
        }
    }

    /** Проход: отметка доходит до середины, нажатие, отметка доходит до края. */
    private static void passes(ArmWrestle bout, int many) {
        for (int i = 0; i < many; i++) {
            ticks(bout, 12);
            bout.press();
            ticks(bout, 12);
        }
    }

    @Test
    void theMarkerRunsThereAndBack() {
        assertEquals(0.0, ArmWrestle.markerAt(0), 1e-9);
        assertEquals(0.5, ArmWrestle.markerAt(12), 1e-9);
        assertEquals(1.0, ArmWrestle.markerAt(24), 1e-9);
        assertEquals(0.5, ArmWrestle.markerAt(36), 1e-9);
        assertEquals(0.0, ArmWrestle.markerAt(48), 1e-9);
    }

    /** Для кадра — та же отметка, но и между тиками: клиент рисует её плавно. */
    @Test
    void theMarkerGlidesBetweenTicks() {
        assertEquals(0.25, ArmWrestle.markerAt(6.0), 1e-9);
        assertEquals(0.5, ArmWrestle.markerAt(12.0), 1e-9);
        assertEquals(0.75, ArmWrestle.markerAt(30.0), 1e-9);
        assertEquals(0.25, ArmWrestle.markerAt(-6.0), 1e-9);
        assertEquals(ArmWrestle.markerAt(36L), ArmWrestle.markerAt(36.0), 1e-9);
    }

    @Test
    void theStrongerTheNarrowerTheGreen() {
        assertEquals(0.30, new ArmWrestle(0.0, Nature.EVEN).zoneWidth(), 1e-9);
        assertEquals(0.18, new ArmWrestle(0.8, Nature.EVEN).zoneWidth(), 1e-9);
    }

    @Test
    void aHitInTheGreenLeansTheRivalsArm() {
        ArmWrestle bout = new ArmWrestle(0.4, Nature.EVEN);
        ticks(bout, 12);
        double before = bout.balance();
        assertTrue(bout.press());
        assertEquals(before + 18, bout.balance(), 1e-9);
    }

    @Test
    void aMissLeansYours() {
        ArmWrestle bout = new ArmWrestle(0.4, Nature.EVEN);
        ticks(bout, 2);
        double before = bout.balance();
        assertFalse(bout.press());
        assertEquals(before - 12, bout.balance(), 1e-9);
    }

    /** За один проход засчитывается одно нажатие: второе в том же зелёном — промах. */
    @Test
    void oneHitPerPass() {
        ArmWrestle bout = new ArmWrestle(0.4, Nature.EVEN);
        ticks(bout, 12);
        assertTrue(bout.press());
        assertFalse(bout.press());
    }

    @Test
    void theRivalPushesEveryTick() {
        ArmWrestle bout = new ArmWrestle(0.8, Nature.EVEN);
        ticks(bout, 10);
        assertEquals(-10 * (0.2 + 0.5 * 0.8), bout.balance(), 1e-9);
    }

    @Test
    void doingNothingLosesAtTheEdge() {
        ArmWrestle bout = new ArmWrestle(1.0, Nature.EVEN);
        ticks(bout, 200);
        assertEquals(Optional.of(ArmWrestle.Outcome.LOSE), bout.outcome());
    }

    /**
     * Сила 0: давление 0,2 за тик. После четвёртого нажатия (84-й тик)
     * перевес 4 × 18 − 0,2 × 84 = 55,2 — лентяй сдаётся, не дожидаясь края.
     */
    @Test
    void theLazyGivesUpWhenFarBehind() {
        ArmWrestle bout = new ArmWrestle(0.0, Nature.LAZY);
        passes(bout, 4);
        assertEquals(Optional.of(ArmWrestle.Outcome.WIN), bout.outcome());
        assertTrue(bout.balance() < 100);
    }

    /** Те же +55,2 трус терпит; после шестого нажатия +81,6 — сдаётся и он. */
    @Test
    void theCowardHoldsLongerThanTheLazy() {
        ArmWrestle bout = new ArmWrestle(0.0, Nature.COWARD);
        passes(bout, 4);
        assertEquals(Optional.empty(), bout.outcome());
        passes(bout, 2);
        assertEquals(Optional.of(ArmWrestle.Outcome.WIN), bout.outcome());
        assertTrue(bout.balance() < 100);
    }

    /** Сила 1: за проход давление 24 × 0,7 = 16,8, попадание +18 — к концу +30, до края далеко. */
    @Test
    void timeDecidesByWhoIsAhead() {
        ArmWrestle bout = new ArmWrestle(1.0, Nature.AMBITIOUS);
        passes(bout, 25);
        assertEquals(600, bout.elapsed());
        assertEquals(Optional.of(ArmWrestle.Outcome.WIN), bout.outcome());
        assertEquals(30, bout.balance(), 1e-6);
    }

    /** Давление 600 × 0,7 = 420, попадания 24 × 18 = 432, промах −12: к концу ровно ноль. */
    @Test
    void anEvenEndIsADraw() {
        ArmWrestle bout = new ArmWrestle(1.0, Nature.AMBITIOUS);
        ticks(bout, 2);
        assertFalse(bout.press());
        ticks(bout, 22);
        passes(bout, 24);
        assertEquals(Optional.of(ArmWrestle.Outcome.DRAW), bout.outcome());
    }

    @Test
    void nothingMovesAfterTheEnd() {
        ArmWrestle bout = new ArmWrestle(1.0, Nature.EVEN);
        ticks(bout, 200);
        double end = bout.balance();
        ticks(bout, 10);
        assertFalse(bout.press());
        assertEquals(end, bout.balance(), 1e-9);
    }
}
