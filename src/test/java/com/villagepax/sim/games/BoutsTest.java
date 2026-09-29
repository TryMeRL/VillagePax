package com.villagepax.sim.games;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Отказ говорит причину — правило мода. Ключ причины собран из частей,
 * и {@code LangTest} его не видит: забытая строка всплыла бы голым ключом
 * над рукой игрока.
 */
class BoutsTest {

    @Test
    void everyRefusalHasAReason() throws IOException {
        for (String lang : new String[]{"ru_ru", "en_us"}) {
            JsonObject words = JsonParser.parseString(Files.readString(Path.of("src", "main", "resources",
                    "assets", "villagepax", "lang", lang + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Bouts.Verdict verdict : Bouts.Verdict.values()) {
                if (verdict != Bouts.Verdict.YES) {
                    assertTrue(words.has(verdict.reasonKey()),
                            lang + ": у отказа " + verdict + " нет причины " + verdict.reasonKey());
                }
            }
        }
    }
}
