package com.villagepax.sim.work;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Выучка стражи: строй разбирается по кругу и не зависит от того,
 * в каком порядке поселение помнит своих стражников.
 */
class GuardKindTest {

    @Test
    void theRotaIsSwordBowMedicAndAround() {
        assertEquals(GuardKind.SWORD, GuardKind.byRank(0));
        assertEquals(GuardKind.BOW, GuardKind.byRank(1));
        assertEquals(GuardKind.MEDIC, GuardKind.byRank(2));
        assertEquals(GuardKind.SWORD, GuardKind.byRank(3));
    }

    @Test
    void aLoneGuardIsASwordsman() {
        UUID only = UUID.randomUUID();
        assertEquals(GuardKind.SWORD, GuardKind.of(List.of(only), only, Optional.empty()));
    }

    @Test
    void theOrderOfTheListDoesNotMatter() {
        List<UUID> guards = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            guards.add(UUID.randomUUID());
        }
        List<UUID> reversed = new ArrayList<>(guards);
        java.util.Collections.reverse(reversed);
        for (UUID guard : guards) {
            assertEquals(GuardKind.of(guards, guard, Optional.empty()),
                    GuardKind.of(reversed, guard, Optional.empty()));
        }
    }

    @Test
    void threeGuardsMakeAWholeLine() {
        List<UUID> guards = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<GuardKind> kinds = guards.stream()
                .map(guard -> GuardKind.of(guards, guard, Optional.empty())).toList();
        assertTrue(kinds.containsAll(List.of(GuardKind.SWORD, GuardKind.BOW, GuardKind.MEDIC)));
    }

    @Test
    void aDrillBeatsTheRota() {
        UUID only = UUID.randomUUID();
        assertEquals(GuardKind.MEDIC, GuardKind.of(List.of(only), only, Optional.of(GuardKind.MEDIC)));
    }

    @Test
    void theMedicDoesNotFight() {
        assertFalse(GuardKind.MEDIC.fights());
        assertTrue(GuardKind.BOW.fights());
        assertEquals(Optional.of(GuardKind.BOW), GuardKind.byId("bow"));
        assertEquals(Optional.empty(), GuardKind.byId("lancer"));
    }
}
