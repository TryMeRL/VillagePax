package com.villagepax.sim.festival;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Зазывала говорит разное и всегда то, что есть в словаре.
 * <p>
 * Реплика выбирается по минуте: затейник зазывает раз в минуту, и одна и та
 * же строка два раза подряд читалась бы заевшей пластинкой. Ключ, которого
 * нет в словаре, в чате выглядел бы как {@code villagepax.entertainer.call.3}
 * — и никто, кроме игрока, этого бы не увидел.
 */
class HeraldsTest {

    private static final Path LANG = Path.of("src", "main", "resources", "assets", "villagepax",
            "lang");

    @Test
    void theBarkerHasTenLinesAndAllAreTranslated() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (long minute = 0; minute < 60; minute++) {
            keys.add(Heralds.barkerLine(0, minute));
            keys.add(Heralds.barkerLine(3, minute));
        }
        assertEquals(10, keys.size(), "реплик зазывалы: " + keys);
        List<String> missing = new ArrayList<>();
        for (String code : List.of("ru_ru", "en_us")) {
            String lang = Files.readString(LANG.resolve(code + ".json"), StandardCharsets.UTF_8);
            for (String key : keys) {
                if (!lang.contains("\"" + key + "\"")) {
                    missing.add(code + ": " + key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "реплики без перевода: " + missing);
    }

    @Test
    void theBarkerDoesNotRepeatHimselfMinuteToMinute() {
        for (long minute = 0; minute < 30; minute++) {
            assertNotEquals(Heralds.barkerLine(0, minute), Heralds.barkerLine(0, minute + 1));
            assertNotEquals(Heralds.barkerLine(5, minute), Heralds.barkerLine(5, minute + 1));
        }
    }

    /** В праздник — зовёт играть; в будни — считает дни. */
    @Test
    void onTheDayHeCallsToPlayAndOtherwiseCountsTheDays() {
        assertTrue(Heralds.barkerLine(0, 7).startsWith("villagepax.entertainer.today."));
        assertTrue(Heralds.barkerLine(2, 7).startsWith("villagepax.entertainer.call."));
    }
}
