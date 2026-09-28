package com.villagepax.sim.games;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Память игр переживает перезапуск: счёт, потраченное за день и бодрость после игры. */
class GamesLedgerTest {

    private static final UUID BERTHA = UUID.nameUUIDFromBytes("bertha".getBytes());
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player".getBytes());

    @Test
    void everythingSurvivesARestart() {
        GamesLedger ledger = new GamesLedger();
        ledger.record(BERTHA, PLAYER, Rivalry.Result.LOST, 9);
        ledger.record(BERTHA, PLAYER, Rivalry.Result.LOST, 9);
        ledger.spend(BERTHA, 9, 3);
        ledger.markCheer(BERTHA, 9);

        GamesLedger back = GamesLedger.fromNbt(ledger.writeNbt(new NbtCompound()));

        assertEquals(new Rivalry(0, 2, -2, 9, 2), back.rivalry(BERTHA, PLAYER));
        assertEquals(3, back.spent(BERTHA, 9));
        assertTrue(back.cheered(BERTHA, 9));
    }

    @Test
    void yesterdaysSpendingIsForgottenToday() {
        GamesLedger ledger = new GamesLedger();
        ledger.spend(BERTHA, 9, 3);
        assertEquals(0, ledger.spent(BERTHA, 10));
        ledger.spend(BERTHA, 10, 2);
        assertEquals(2, ledger.spent(BERTHA, 10));
    }

    @Test
    void aStrangerHasNoRecord() {
        assertEquals(Rivalry.NONE, new GamesLedger().rivalry(BERTHA, PLAYER));
        assertFalse(new GamesLedger().cheered(BERTHA, 1));
    }

    /** Ушедшего жителя и позавчерашнюю бодрость память не держит: мир живёт годами. */
    @Test
    void pruneForgetsTheGoneAndTheOld() {
        GamesLedger ledger = new GamesLedger();
        UUID gone = UUID.nameUUIDFromBytes("gone".getBytes());
        ledger.record(gone, PLAYER, Rivalry.Result.WON, 9);
        ledger.record(BERTHA, PLAYER, Rivalry.Result.WON, 9);
        ledger.markCheer(BERTHA, 7);
        ledger.prune(Set.of(BERTHA), 9);
        assertEquals(Rivalry.NONE, ledger.rivalry(gone, PLAYER));
        assertEquals(1, ledger.rivalry(BERTHA, PLAYER).won());
        assertFalse(ledger.cheered(BERTHA, 7));
    }
}
