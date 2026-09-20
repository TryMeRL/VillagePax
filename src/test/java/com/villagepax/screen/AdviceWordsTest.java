package com.villagepax.screen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * У каждого совета есть слова — на обоих языках.
 * <p>
 * Совет в пульте — единственная строка мода, которая отвечает на вопрос
 * «что делать дальше». Без перевода она выглядит как
 * {@code villagepax.advice.no_temple}, и это хуже молчания: молчание
 * игрок спишет на то, что всё хорошо, а ключ — на поломку.
 * <p>
 * Проверка стоит дёшево и ловит ровно ту ошибку, которую делают чаще
 * всего: добавили ступень лестницы, а строку дописать забыли. В игре
 * это видно только у той колонии, которая до этой ступени дошла, —
 * то есть, скорее всего, никогда.
 */
class AdviceWordsTest {

    private static JsonObject dictionary(String language) {
        String path = "/assets/villagepax/lang/" + language + ".json";
        try (InputStream stream = AdviceWordsTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("нет файла языка: " + path);
            }
            return JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Test
    void everyAdviceSpeaksInBothLanguages() {
        JsonObject russian = dictionary("ru_ru");
        JsonObject english = dictionary("en_us");

        List<String> missing = new ArrayList<>();
        for (String key : Advice.keys()) {
            if (!russian.has(key)) {
                missing.add("ru_ru: " + key);
            }
            if (!english.has(key)) {
                missing.add("en_us: " + key);
            }
        }

        assertTrue(missing.isEmpty(), "советы без слов: " + missing);
    }

    /**
     * И ни один совет не переведён в пустоту: подсказка, которой не видно,
     * — это то же молчание, только дороже.
     */
    @Test
    void noAdviceIsTranslatedToSilence() {
        JsonObject russian = dictionary("ru_ru");
        JsonObject english = dictionary("en_us");

        List<String> empty = new ArrayList<>();
        for (String key : Advice.keys()) {
            if (russian.has(key) && russian.get(key).getAsString().isBlank()) {
                empty.add("ru_ru: " + key);
            }
            if (english.has(key) && english.get(key).getAsString().isBlank()) {
                empty.add("en_us: " + key);
            }
        }

        assertTrue(empty.isEmpty(), "советы, переведённые в молчание: " + empty);
    }

    /**
     * Список советов не врёт о своей длине.
     * <p>
     * {@code keys()} перечисляет лестницу руками, и в этом её слабое
     * место: добавить ступень и забыть строку в списке — ровно та ошибка,
     * от которой список и заведён. Число написано здесь <b>отдельно</b>
     * и сверяется с длиной: дописал ступень — поправь и число, и это
     * ровно та минута внимания, которой всё лечится.
     */
    @Test
    void theLadderIsAsLongAsItClaims() {
        assertEquals(16, Advice.keys().size(),
                "ступеней в лестнице совета стало другое число — проверь, "
                        + "что новая попала и в keys(), и в оба словаря");
        assertEquals(Advice.keys().size(), List.copyOf(new java.util.LinkedHashSet<>(
                        Advice.keys())).size(),
                "в списке советов есть повторы");
    }
}
