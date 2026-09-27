package com.villagepax.core.festival;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.item.FireworkRocketItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Праздник читается из датапака целиком — или не читается вовсе.
 * <p>
 * Вид состязания решает, что будет происходить на ярмарке, и описка в нём
 * обязана ронять чтение громко: молча прочитанный праздник без поиска —
 * это ярмарка, на которой затейник не знает, во что играть.
 */
class FestivalCodecTest {

    private static final String NORMAN = """
            {"culture": "villagepax:norman", "name": "villagepax.festival.norman", "moon_phase": 0,
             "fireworks": {"colors": [16711680, 16766720], "shape": "star"},
             "contests": [
               {"kind": "chase", "name": "a", "critter": "pig"},
               {"kind": "archery", "name": "b"},
               {"kind": "hunt", "name": "c", "token": "egg"}],
             "prizes": [{"item": "minecraft:firework_rocket", "count": 3, "price": 1}]}""";

    private static Festival parse(String json) {
        return Festival.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result().orElse(null);
    }

    @Test
    void aFestivalReadsWhole() {
        Festival festival = parse(NORMAN);
        assertNotNull(festival);
        assertEquals(0, festival.moonPhase());
        assertEquals(3, festival.contests().size());
        assertEquals(ContestKind.CHASE, festival.contests().get(0).kind());
        assertEquals(Critter.PIG, festival.contests().get(0).critter().orElseThrow());
        assertEquals(TokenKind.EGG, festival.contests().get(2).token().orElseThrow());
        assertEquals("star", festival.fireworks().shape());
        assertEquals(FireworkRocketItem.Type.STAR, festival.fireworks().type());
        assertEquals(2, festival.fireworks().colors().size());
        assertEquals(3, festival.prizes().get(0).count());
        assertEquals(1, festival.prizes().get(0).price());
    }

    /** Без числа поиск прячет десять вещиц, ловля выпускает трёх зверьков, стрельба — восемь выстрелов. */
    @Test
    void piecesHaveTheirDefaults() {
        Festival festival = parse(NORMAN);
        assertEquals(3, festival.contests().get(0).pieces());
        assertEquals(8, festival.contests().get(1).pieces());
        assertEquals(10, festival.contests().get(2).pieces());
    }

    @Test
    void aCountInTheDataWinsOverTheDefault() {
        Festival festival = parse(NORMAN.replace("\"token\": \"egg\"", "\"token\": \"egg\", \"count\": 6"));
        assertNotNull(festival);
        assertEquals(6, festival.contests().get(2).pieces());
    }

    @Test
    void anUnknownKindIsAnErrorNotASilence() {
        assertNull(parse(NORMAN.replace("\"archery\"", "\"race\"")));
    }

    @Test
    void anUnknownCritterIsAnErrorNotASilence() {
        assertNull(parse(NORMAN.replace("\"pig\"", "\"goat\"")));
    }

    /**
     * Битый фейерверк — ошибка, а не белое умолчание: автор написал цвета,
     * и праздник с чужими огнями он принял бы за поломку мода.
     */
    @Test
    void aBrokenFireworkIsAnErrorNotAPlainOne() {
        assertNull(parse(NORMAN.replace("[16711680, 16766720]", "[\"red\"]")));
    }

    /**
     * Незнакомая форма — ошибка, как и битый цвет: «sparkle» молча стал бы
     * шаром, и автор искал бы, почему у его народа не те огни.
     */
    @Test
    void anUnknownShapeIsAnError() {
        assertNull(parse(NORMAN.replace("\"star\"", "\"sparkle\"")));
    }

    /** Фейерверк без цветов — ошибка: искре ракеты нечем гореть, и клиент падает. */
    @Test
    void aFireworkWithoutColoursIsAnError() {
        assertNull(parse(NORMAN.replace("[16711680, 16766720]", "[]")));
    }

    /** Без фейерверка в данных праздник всё равно кончается фейерверком — белым. */
    @Test
    void fireworksHaveAPlainDefault() {
        Festival festival = parse(NORMAN.replace(
                "\"fireworks\": {\"colors\": [16711680, 16766720], \"shape\": \"star\"},", ""));
        assertNotNull(festival);
        assertEquals(Festival.Fireworks.PLAIN, festival.fireworks());
    }
}
