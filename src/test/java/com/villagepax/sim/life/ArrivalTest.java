package com.villagepax.sim.life;

import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.trade.MarketDay;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Новости въезда: только правда о сегодняшнем дне. */
class ArrivalTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    private static List<String> keys(List<Text> lines) {
        return lines.stream()
                .map(line -> ((TranslatableTextContent) line.getContent()).getKey())
                .toList();
    }

    @Test
    void aVillageTellsOfItsMarketAndItsGrowth() {
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан", BlockPos.ORIGIN);
        village.setLevel(SettlementLevel.VILLAGE);
        long market = MarketDay.weekday(NORMAN);

        List<String> today = keys(Arrival.news(village, market));
        assertTrue(today.contains("villagepax.arrival.market_today"), today.toString());
        assertTrue(today.contains("villagepax.arrival.growth"), today.toString());

        List<String> eve = keys(Arrival.news(village, market - 1 + MarketDay.WEEK));
        assertTrue(eve.contains("villagepax.arrival.market_tomorrow"), eve.toString());
    }

    @Test
    void aColonyHasNoMarketAndNoGrowthNews() {
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Моя", BlockPos.ORIGIN);
        colony.setLevel(SettlementLevel.TOWN);
        List<String> news = keys(Arrival.news(colony, MarketDay.weekday(NORMAN)));
        assertFalse(news.contains("villagepax.arrival.market_today"), news.toString());
        assertFalse(news.contains("villagepax.arrival.growth"), news.toString());
    }
}
