package com.villagepax.sim.games;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villagepax.sim.life.Nature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Фраза выбирается из одного мешка: сперва фразы нрава, за ними общие.
 * Номера идут подряд с единицы: словарь пополняется дописыванием, без правки кода.
 */
class LinesTest {

    private static final String BASE = "villagepax.games.say.win";

    @Test
    void aNatureSpeaksFromItsOwnPool() {
        Set<String> known = Set.of(BASE + ".ambitious.1", BASE + ".ambitious.2", BASE + ".1");
        assertEquals(BASE + ".ambitious.2",
                Lines.pick(Say.WIN, Nature.AMBITIOUS, known::contains, bound -> 1));
    }

    /**
     * Нрав окрашивает речь, а не заменяет её: общий запас звучит и у того,
     * у кого есть свой. Иначе нрав с одной своей фразой твердил бы её вечно.
     */
    @Test
    void theCommonPoolSpeaksForANatureToo() {
        String base = "villagepax.say.hungry";
        Set<String> known = Set.of(base + ".lazy.1", base + ".1", base + ".2", base + ".3");
        assertEquals(base + ".3", Lines.pickKey(base, Nature.LAZY, known::contains, bound -> bound - 1));
        assertEquals(base + ".1", Lines.pickKey(base, Nature.LAZY, known::contains, bound -> 1));
        assertEquals(base + ".lazy.1", Lines.pickKey(base, Nature.LAZY, known::contains, bound -> 0));
    }

    @Test
    void withoutItsOwnPoolTheCommonOneSpeaks() {
        Set<String> known = Set.of(BASE + ".1", BASE + ".2", BASE + ".3");
        assertEquals(BASE + ".3",
                Lines.pick(Say.WIN, Nature.COWARD, known::contains, bound -> bound - 1));
    }

    @Test
    void aGapEndsThePool() {
        Set<String> known = Set.of(BASE + ".1", BASE + ".3");
        assertEquals(BASE + ".1",
                Lines.pick(Say.WIN, Nature.EVEN, known::contains, bound -> bound - 1));
    }

    /** Подбор по любой базе — им говорят и игры, и жизнь жителя. */
    @Test
    void anyBaseSpeaksTheSameWay() {
        String base = "villagepax.say.hungry";
        Set<String> known = Set.of(base + ".lazy.1", base + ".1", base + ".2");
        assertEquals(base + ".lazy.1", Lines.pickKey(base, Nature.LAZY, known::contains, bound -> 0));
        assertEquals(base + ".2", Lines.pickKey(base, Nature.EVEN, known::contains, bound -> 1));
        assertEquals(base + ".1", Lines.pickKey(base, Nature.EVEN, key -> false, bound -> 0));
    }

    /** Совсем пустой запас — всё равно ключ: игрок увидит его и поймёт, что забыли слово. */
    @Test
    void anEmptyPoolStillNamesAKey() {
        assertEquals(BASE + ".1", Lines.pick(Say.WIN, Nature.EVEN, key -> false, bound -> 0));
    }

    /**
     * У каждого случая есть что сказать — хотя бы три общие фразы.
     * <p>
     * Ключ из частей {@code LangTest} не видит: забытый пул всплыл бы только
     * в игре, голым ключом над головой соперника.
     */
    @Test
    void everyCaseHasWords() throws IOException {
        JsonObject ru = JsonParser.parseString(Files.readString(Path.of("src", "main", "resources",
                "assets", "villagepax", "lang", "ru_ru.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (Say say : Say.values()) {
            for (int n = 1; n <= 3; n++) {
                String key = "villagepax.games.say." + say.id() + "." + n;
                assertTrue(ru.has(key), "Случаю " + say + " нечего сказать: нет " + key);
            }
        }
    }
}
