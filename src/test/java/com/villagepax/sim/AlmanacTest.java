package com.villagepax.sim;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlmanacTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");
    private static final Identifier MAYA = new Identifier("villagepax", "maya");
    private static final Identifier PONY = new Identifier("villagepax", "pony");

    private static String key(Text text) {
        return ((TranslatableTextContent) text.getContent()).getKey();
    }

    private static Object[] args(Text text) {
        return ((TranslatableTextContent) text.getContent()).getArgs();
    }

    @Test
    void dayOneHasNormanMarketTodayAndMayaTomorrow() {
        List<Text> lines = Almanac.lines(1, List.of(NORMAN, MAYA, PONY), Map.of());
        assertEquals("villagepax.almanac.today", key(lines.get(0)));
        assertEquals("villagepax.almanac.moon.1", key((Text) args(lines.get(0))[1]));
        Text market = lines.get(1);
        assertEquals("villagepax.almanac.market", key(market));
        assertEquals("villagepax.culture.norman", key((Text) ((Text) args(market)[0]).getSiblings().get(0)));
        assertEquals("villagepax.culture.maya", key((Text) ((Text) args(market)[1]).getSiblings().get(0)));
        assertEquals(2, lines.size(), "без праздников — только день и рынок");
    }

    @Test
    void festivalsGoSoonestFirst() {
        Map<Identifier, Almanac.Feast> feasts = Map.of(
                NORMAN, new Almanac.Feast("villagepax.festival.norman", 0),
                PONY, new Almanac.Feast("villagepax.festival.pony", 1),
                MAYA, new Almanac.Feast("villagepax.festival.maya", 2));
        List<Text> lines = Almanac.lines(1, List.of(NORMAN, MAYA, PONY), feasts);
        assertEquals("villagepax.almanac.festival_today", key(lines.get(2)));
        assertEquals("villagepax.almanac.festival_tomorrow", key(lines.get(3)));
        assertEquals("villagepax.almanac.festival_in", key(lines.get(4)));
        assertEquals(7, args(lines.get(4))[2]);
    }

    @Test
    void noMarketIsNowhere() {
        List<Text> lines = Almanac.lines(1, List.of(PONY), Map.of());
        assertEquals("villagepax.almanac.nowhere", key((Text) args(lines.get(1))[0]));
    }
}
