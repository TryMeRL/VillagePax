package com.villagepax.sim;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Путевые записки помещаются в книгу — и написаны на обоих языках.
 * <p>
 * Книга — единственное место, где мод объясняет себя игроку: «всё, что он
 * прочтёт, — это то, что оказалось у него в руках». И у неё есть жёсткий
 * предел, о котором ничто не предупреждает: страница подписанной книги
 * рисует <b>четырнадцать строк шириной в сто четырнадцать точек</b>,
 * а всё, что не влезло, просто не рисуется.
 * <p>
 * Ни ошибки, ни обрезанного слова, ни многоточия — текст обрывается
 * посреди фразы, и выглядит это не как поломка, а как будто автор так
 * и задумал. Три страницы жили в моде переполненными, и конца страницы
 * про жизнь и смерть жителей не видел никто.
 * <p>
 * Ширина считается по ванильным долям знака, а не по их числу: в строке
 * помещается девятнадцать обычных букв, но тридцать точек с запятыми.
 * Считать знаки значило бы запрещать короткие строки и пропускать длинные.
 */
class GuideBookTest {

    /** Ширина страницы книги в точках — {@code BookScreen.MAX_TEXT_WIDTH}. */
    private static final int PAGE_WIDTH = 114;

    /** И сколько строк она рисует. Пятнадцатую не увидит никто. */
    private static final int PAGE_LINES = 14;

    private static final Path SOURCE =
            Path.of("src", "main", "java", "com", "villagepax", "sim", "Guide.java");

    private static final Pattern PAGE_KEY = Pattern.compile("\"(villagepax\\.guide\\.[a-z_]+)\"");

    /**
     * Доли знаков ванильного шрифта. Остальные — шесть, включая кириллицу.
     * <p>
     * Написано руками по шрифту игры, а не выведено из чего-то: это
     * внешняя мера, и вывести её неоткуда.
     */
    private static final Map<Character, Integer> NARROW = Map.ofEntries(
            Map.entry(' ', 4), Map.entry('!', 2), Map.entry(',', 2), Map.entry('.', 2),
            Map.entry(':', 2), Map.entry(';', 2), Map.entry('i', 2), Map.entry('|', 2),
            Map.entry('\'', 2), Map.entry('`', 3), Map.entry('l', 3), Map.entry('I', 4),
            Map.entry('[', 4), Map.entry(']', 4), Map.entry('t', 4), Map.entry('(', 5),
            Map.entry(')', 5), Map.entry('f', 5), Map.entry('k', 5), Map.entry('<', 5),
            Map.entry('>', 5), Map.entry('"', 5), Map.entry('{', 5), Map.entry('}', 5));

    @Test
    void everyPageIsWrittenInBothLanguages() throws IOException {
        JsonObject russian = dictionary("ru_ru");
        JsonObject english = dictionary("en_us");
        List<String> missing = new ArrayList<>();

        for (String key : pageKeys()) {
            if (!russian.has(key)) {
                missing.add("ru_ru: " + key);
            }
            if (!english.has(key)) {
                missing.add("en_us: " + key);
            }
        }

        assertTrue(missing.isEmpty(), "Страница книги без перевода: " + missing);
    }

    @Test
    void everyPageFitsOnAPage() throws IOException {
        List<String> keys = pageKeys();
        List<String> spilled = new ArrayList<>();

        for (String language : List.of("ru_ru", "en_us")) {
            JsonObject words = dictionary(language);
            for (String key : keys) {
                if (!words.has(key)) {
                    continue;
                }
                int lines = linesOf(words.get(key).getAsString());
                if (lines > PAGE_LINES) {
                    spilled.add(language + " " + key.substring(key.lastIndexOf('.') + 1)
                            + ": " + lines + " строк из " + PAGE_LINES);
                }
            }
        }

        assertFalse(keys.isEmpty(), "В Guide не нашлось ни одной страницы — "
                + "проверка сверяет пустоту с пустотой");
        assertTrue(spilled.isEmpty(), "Конца страницы игрок не увидит: " + spilled);
    }

    /** Во сколько строк ляжет этот текст на странице книги. */
    private static int linesOf(String page) {
        int lines = 0;
        for (String paragraph : page.split("\n", -1)) {
            lines += wrap(paragraph);
        }
        return lines;
    }

    /**
     * Сколько строк займёт один абзац, если переносить его по словам.
     * <p>
     * Пустой абзац — это пустая строка, и она тоже занимает место:
     * ровно так пробел между абзацами и съедает страницу.
     */
    private static int wrap(String paragraph) {
        int lines = 1;
        int used = 0;
        for (String word : paragraph.split(" ", -1)) {
            int width = widthOf(word);
            int space = used == 0 ? 0 : widthOf(" ");
            if (used + space + width > PAGE_WIDTH && used > 0) {
                lines++;
                used = width;
            } else {
                used += space + width;
            }
        }
        return lines;
    }

    private static int widthOf(String word) {
        int width = 0;
        for (int at = 0; at < word.length(); at++) {
            char letter = word.charAt(at);
            if (letter == '§') {
                // Код оформления знаков не рисует и места не занимает.
                at++;
                continue;
            }
            width += NARROW.getOrDefault(letter, 6);
        }
        return width;
    }

    /** Ключи страниц — из самого {@code Guide}, а не из своего списка. */
    private static List<String> pageKeys() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        int start = source.indexOf("PAGES = List.of(");
        int end = source.indexOf(");", start);
        List<String> keys = new ArrayList<>();
        Matcher found = PAGE_KEY.matcher(source.substring(start, end));
        while (found.find()) {
            keys.add(found.group(1));
        }
        return keys;
    }

    private static JsonObject dictionary(String language) {
        String path = "/assets/villagepax/lang/" + language + ".json";
        try (InputStream stream = GuideBookTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("нет файла языка: " + path);
            }
            return JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
