package com.villagepax.core;

import com.google.gson.JsonElement;
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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Всё, что датапак называет по имени, названо и на обоих языках.
 * <p>
 * Народ, здание, ремесло, бог и реплика квеста несут в json <b>ключ</b>,
 * а не текст: правила живут в общем коде и ничего не должны знать о языке
 * игрока. Цена этого решения ровно одна — ключ и словарь можно развести,
 * и тогда игрок видит в пульте {@code villagepax.building.dwarf.gatehouse}
 * посреди списка зданий.
 * <p>
 * Само это не находится никогда. Json грузится без ошибки, экран рисуется
 * без ошибки, в логе тишина; заметить можно, только открыв тот самый экран
 * у того самого народа. Пять народов, полсотни зданий и полторы сотни
 * реплик — и одна забытая строка теряется в них навсегда.
 * <p>
 * Это третья проверка того же рода, что {@code BlockResourcesTest}
 * и {@code CitizenFacesTest}: <b>объявлено в одном месте, нарисовано
 * в другом, и никто их не сверяет</b>. Каждая из трёх ловила настоящую
 * дыру в день, когда её написали.
 */
class DatapackWordsTest {

    private static final Path DATA =
            Path.of("src", "main", "resources", "data", "villagepax", "villagepax");

    /** Где искать имена и под каким полем они лежат. */
    private static final Map<String, String> NAMED = Map.of(
            "cultures", "display_name",
            "buildings", "display_name",
            "professions", "display_name",
            "gods", "display_name",
            "quests", "dialogue");

    @Test
    void everythingTheDatapackNamesSpeaksInBothLanguages() throws IOException {
        JsonObject russian = dictionary("ru_ru");
        JsonObject english = dictionary("en_us");
        List<String> missing = new ArrayList<>();

        for (Map.Entry<String, String> section : NAMED.entrySet()) {
            for (Path file : filesIn(section.getKey())) {
                JsonElement named = read(file).get(section.getValue());
                if (named == null) {
                    // Поле необязательное: у квеста без реплики проверять
                    // нечего, и это законно.
                    continue;
                }
                String key = named.getAsString();
                if (!russian.has(key)) {
                    missing.add("ru_ru: " + key + " (" + file.getFileName() + ")");
                }
                if (!english.has(key)) {
                    missing.add("en_us: " + key + " (" + file.getFileName() + ")");
                }
            }
        }

        assertTrue(missing.isEmpty(), "Датапак называет то, чего нет в словаре: " + missing);
    }

    /** Все json раздела, включая вложенные папки народов. */
    private static List<Path> filesIn(String folder) throws IOException {
        Path root = DATA.resolve(folder);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static JsonObject dictionary(String language) {
        String path = "/assets/villagepax/lang/" + language + ".json";
        try (InputStream stream = DatapackWordsTest.class.getResourceAsStream(path)) {
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
