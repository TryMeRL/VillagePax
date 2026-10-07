package com.villagepax.sim.life;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HappeningsTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    private static Settlement village(int people) {
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан", BlockPos.ORIGIN);
        for (int i = 0; i < people; i++) {
            village.addCitizen(new Citizen(UUID.randomUUID(), "Жан" + i, "Кан", NORMAN, Gender.MALE,
                    Citizen.Life.UNKNOWN, Optional.empty(), 70, 20, Optional.empty(), Optional.empty(),
                    Optional.empty(), 20.0f));
        }
        return village;
    }

    @Test
    void theSameDayIsTheSameHappening() {
        Settlement village = village(6);
        for (long day = 0; day < 200; day++) {
            assertEquals(Happenings.of(village, day), Happenings.of(village, day));
        }
    }

    @Test
    void aboutAThirdOfDaysHaveSomething() {
        Settlement village = village(8);
        Map<Happenings.Kind, Integer> seen = new EnumMap<>(Happenings.Kind.class);
        int days = 4000;
        for (long day = 0; day < days; day++) {
            seen.merge(Happenings.of(village, day).kind(), 1, Integer::sum);
        }
        int none = seen.getOrDefault(Happenings.Kind.NONE, 0);
        assertTrue(none > days * 0.6 && none < days * 0.8, seen.toString());
        for (Happenings.Kind kind : Happenings.Kind.values()) {
            assertTrue(seen.getOrDefault(kind, 0) > 0, "не выпал " + kind + ": " + seen);
        }
    }

    @Test
    void aQuarrelIsBetweenTwoDifferentPeopleOfTheVillage() {
        Settlement village = village(5);
        int quarrels = 0;
        for (long day = 0; day < 2000; day++) {
            Happenings.Today today = Happenings.of(village, day);
            if (today.kind() == Happenings.Kind.QUARREL) {
                quarrels++;
                assertNotEquals(today.one(), today.other());
                assertTrue(village.citizen(today.one().orElseThrow()).isPresent());
                assertTrue(village.citizen(today.other().orElseThrow()).isPresent());
            }
        }
        assertTrue(quarrels > 0);
    }

    @Test
    void anEmptyPlaceHasNoNameDaysNorQuarrelsNorHarvest() {
        Settlement empty = village(0);
        for (long day = 0; day < 500; day++) {
            assertEquals(Happenings.Kind.NONE, Happenings.of(empty, day).kind());
            assertEquals(1, Happenings.harvestTimes(empty, day));
        }
    }

    @Test
    void aBumperHarvestDoublesTheCrop() {
        Settlement village = village(6);
        for (long day = 0; day < 500; day++) {
            boolean bumper = Happenings.of(village, day).kind() == Happenings.Kind.BUMPER_HARVEST;
            assertEquals(bumper ? 2 : 1, Happenings.harvestTimes(village, day));
        }
    }
}
