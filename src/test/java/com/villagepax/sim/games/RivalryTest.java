package com.villagepax.sim.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Житель помнит счёт с игроком и обижается после трёх поражений подряд за вечер. */
class RivalryTest {

    @Test
    void theRecordCounts() {
        Rivalry r = Rivalry.NONE.after(Rivalry.Result.WON, 5).after(Rivalry.Result.LOST, 5);
        assertEquals(1, r.won());
        assertEquals(1, r.lost());
        assertEquals(-1, r.streak());
    }

    @Test
    void threeLossesInAnEveningSulk() {
        Rivalry r = Rivalry.NONE;
        for (int i = 0; i < 3; i++) {
            r = r.after(Rivalry.Result.LOST, 7);
        }
        assertTrue(r.sulks(7));
        assertFalse(r.sulks(8));
    }

    @Test
    void yesterdaysLossesDoNotCountTonight() {
        Rivalry r = Rivalry.NONE.after(Rivalry.Result.LOST, 6).after(Rivalry.Result.LOST, 6)
                .after(Rivalry.Result.LOST, 7);
        assertFalse(r.sulks(7));
        assertEquals(-3, r.streak());
    }

    @Test
    void aWinBreaksTheEveningStreak() {
        Rivalry r = Rivalry.NONE.after(Rivalry.Result.LOST, 7).after(Rivalry.Result.LOST, 7)
                .after(Rivalry.Result.WON, 7).after(Rivalry.Result.LOST, 7);
        assertFalse(r.sulks(7));
    }

    /** Ничья не прибавляет и не обрывает: «подряд» считают проигрыши. */
    @Test
    void aTieKeepsTheCount() {
        Rivalry r = Rivalry.NONE.after(Rivalry.Result.LOST, 7).after(Rivalry.Result.LOST, 7)
                .after(Rivalry.Result.EVEN, 7).after(Rivalry.Result.LOST, 7);
        assertTrue(r.sulks(7));
    }
}
