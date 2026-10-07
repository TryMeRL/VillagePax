package com.villagepax.sim.trade;

import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.Standing;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Рыночный день: раз в неделю, у каждого народа свой, и только у деревни, а не у хутора. */
class MarketDayTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    private static Settlement village(SettlementLevel level, Owner owner) {
        Settlement village = Settlement.found(NORMAN, owner, "Кан", BlockPos.ORIGIN);
        village.setLevel(level);
        return village;
    }

    @Test
    void everyPeopleHasItsOwnWeekday() {
        Set<Integer> days = new HashSet<>();
        for (String people : new String[]{"norman", "maya", "pony", "nord", "yamato", "dwarf", "elf"}) {
            days.add(MarketDay.weekday(new Identifier("villagepax", people)));
        }
        assertEquals(MarketDay.WEEK, days.size(), "у двух народов рынок в один день");
    }

    @Test
    void aVillageTradesOnItsWeekdayAndAHamletNever() {
        long market = MarketDay.weekday(NORMAN) + MarketDay.WEEK * 3L;
        assertTrue(MarketDay.isOn(village(SettlementLevel.VILLAGE, Owner.AUTONOMOUS), market));
        assertFalse(MarketDay.isOn(village(SettlementLevel.VILLAGE, Owner.AUTONOMOUS), market + 1));
        assertFalse(MarketDay.isOn(village(SettlementLevel.HAMLET, Owner.AUTONOMOUS), market),
                "у хутора рынка нет");
        assertFalse(MarketDay.isOn(village(SettlementLevel.TOWN, Owner.of(UUID.randomUUID())), market),
                "колония игрока рынок не устраивает");
        assertEquals(0, MarketDay.daysUntil(NORMAN, market));
        assertEquals(6, MarketDay.daysUntil(NORMAN, market + 1));
    }

    /** На рынке чужак торгует как друг, а обиженный — как был: рынок для пришедших с миром. */
    @Test
    void theMarketTreatsStrangersAsFriendsButNotEnemies() {
        Settlement town = village(SettlementLevel.TOWN, Owner.AUTONOMOUS);
        long market = MarketDay.weekday(NORMAN);
        assertEquals(Standing.FRIEND.from(), MarketDay.tradeTrust(town, 0, market));
        assertEquals(-10, MarketDay.tradeTrust(town, -10, market));
        assertEquals(Standing.FRIEND.from() + 30, MarketDay.tradeTrust(town, Standing.FRIEND.from() + 30, market));
        assertEquals(0, MarketDay.tradeTrust(town, 0, market + 1));
    }
}
