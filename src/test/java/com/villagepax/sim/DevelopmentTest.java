package com.villagepax.sim;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentTest {

    @Test
    void villagesAppearAtEveryStageButCapital() {
        Map<SettlementLevel, Integer> seen = new EnumMap<>(SettlementLevel.class);
        for (long seed = 0; seed < 3000; seed++) {
            Villages.Development development = Villages.developmentOf(new Random(seed));
            seen.merge(development.level(), 1, Integer::sum);
            assertTrue(development.buildings() >= 0 && development.buildings() < 16);
        }
        assertTrue(seen.getOrDefault(SettlementLevel.HAMLET, 0) > 600, seen.toString());
        assertTrue(seen.getOrDefault(SettlementLevel.VILLAGE, 0) > 900, seen.toString());
        assertTrue(seen.getOrDefault(SettlementLevel.TOWN, 0) > 600, seen.toString());
        assertEquals(0, seen.getOrDefault(SettlementLevel.CAPITAL, 0));
    }

    @Test
    void theSamePlaceGrowsTheSameWay() {
        assertEquals(Villages.developmentOf(new Random(42)), Villages.developmentOf(new Random(42)));
    }
}
