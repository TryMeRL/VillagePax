package com.villagepax.sim;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Путевые записки обязаны читаться на обоих языках.
 * <p>
 * Страница книги — это ключ перевода, а ключ без перевода выглядит в игре
 * как {@code villagepax.guide.trade} посреди страницы. Заметить это можно
 * только открыв книгу — то есть никогда, если открывать её будет игрок,
 * а не автор. Поэтому сверяется списком.
 * <p>
 * Читается файл языка <b>с диска</b>, а не из игры: запуск Minecraft ради
 * сверки двух списков строк был бы двумя минутами ожидания вместо одной
 * секунды.
 */
class GuidePagesTest {

    /** Грубый разбор: ключи в файле языка лежат в кавычках перед двоеточием. */
    private static Set<String> keysOf(String language) {
        String path = "/assets/villagepax/lang/" + language + ".json";
        try (InputStream stream = GuidePagesTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("нет файла языка: " + path);
            }
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> keys = new LinkedHashSet<>();

            int at = 0;
            while (true) {
                int open = json.indexOf('"', at);
                if (open < 0) {
                    break;
                }
                int close = json.indexOf('"', open + 1);
                if (close < 0) {
                    break;
                }
                String candidate = json.substring(open + 1, close);
                int colon = json.indexOf(':', close);
                int nextQuote = json.indexOf('"', close + 1);
                if (colon > 0 && (nextQuote < 0 || colon < nextQuote)) {
                    keys.add(candidate);
                    at = colon + 1;
                    // Пропустить значение целиком: в нём бывают и кавычки.
                    int valueOpen = json.indexOf('"', at);
                    at = valueOpen < 0 ? close + 1 : skipString(json, valueOpen) + 1;
                } else {
                    at = close + 1;
                }
            }
            return keys;
        } catch (Exception broken) {
            throw new IllegalStateException("файл языка не читается: " + path, broken);
        }
    }

    /** Конец строки в JSON с учётом экранированных кавычек. */
    private static int skipString(String json, int open) {
        for (int at = open + 1; at < json.length(); at++) {
            char here = json.charAt(at);
            if (here == '\\') {
                at++;
            } else if (here == '"') {
                return at;
            }
        }
        return json.length() - 1;
    }

    @Test
    void everyPageIsTranslatedInBothLanguages() {
        Set<String> russian = keysOf("ru_ru");
        Set<String> english = keysOf("en_us");

        List<String> missing = new ArrayList<>();
        for (String page : Guide.pageKeys()) {
            if (!russian.contains(page)) {
                missing.add("ru_ru: " + page);
            }
            if (!english.contains(page)) {
                missing.add("en_us: " + page);
            }
        }
        assertTrue(missing.isEmpty(), "страницы книги без перевода: " + missing);
    }

    /** Пустая или дважды повторённая страница — это ошибка списка, а не книги. */
    @Test
    void pagesAreDistinctAndPresent() {
        List<String> pages = Guide.pageKeys();
        assertFalse(pages.isEmpty(), "книга без страниц");
        assertEquals(pages.size(), Set.copyOf(pages).size(), "страница повторяется дважды");
        assertEquals(pages.size(), Guide.pageCount());
    }

    /**
     * Первая страница — о том, что это за мод, последняя — о мире.
     * <p>
     * Порядок страниц и есть порядок первого часа: найти, заговорить,
     * основать. Перестановка списка ломает рассказ, и заметить это
     * иначе нельзя.
     */
    @Test
    void theBookOpensWithWhatThisIs() {
        assertEquals("villagepax.guide.what", Guide.pageKeys().get(0));
        assertEquals("villagepax.guide.find", Guide.pageKeys().get(1));
    }
}
