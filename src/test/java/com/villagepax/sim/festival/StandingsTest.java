package com.villagepax.sim.festival;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Места и ленты — решение по игре, и числа здесь написаны руками.
 * <p>
 * Равные делят место, как на настоящих состязаниях: два первых и сразу
 * третий. Без очков мест нет — стоявший в стороне не получает ленту
 * за то, что пришёл.
 */
class StandingsTest {

    private static Contestant someone(String name) {
        return new Contestant(UUID.nameUUIDFromBytes(name.getBytes()), true, name);
    }

    @Test
    void equalsShareAPlace() {
        Map<Contestant, Integer> scores = new LinkedHashMap<>();
        scores.put(someone("a"), 5);
        scores.put(someone("b"), 5);
        scores.put(someone("c"), 3);
        List<Standings.Placing> placings = Standings.rank(scores);
        assertEquals(3, placings.size());
        assertEquals(List.of(1, 1, 3), placings.stream().map(Standings.Placing::place).toList());
        assertEquals("c", placings.get(2).who().name());
    }

    @Test
    void noPointsNoPlace() {
        Map<Contestant, Integer> scores = new LinkedHashMap<>();
        scores.put(someone("a"), 0);
        scores.put(someone("b"), 2);
        List<Standings.Placing> placings = Standings.rank(scores);
        assertEquals(1, placings.size());
        assertEquals("b", placings.get(0).who().name());
        assertEquals(1, placings.get(0).place());
        assertEquals(2, placings.get(0).score());
    }

    @Test
    void ribbonsGoToTheFirstThree() {
        assertEquals(3, Standings.ribbonsFor(1));
        assertEquals(2, Standings.ribbonsFor(2));
        assertEquals(1, Standings.ribbonsFor(3));
        assertEquals(0, Standings.ribbonsFor(4));
    }
}
