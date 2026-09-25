package com.villagepax;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villagepax.core.config.Config;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Словари двух языков говорят об одном и том же — и обо всём, о чём говорит код.
 * <p>
 * Мод пишет игроку ключами, а переводит клиент. Три беды здесь молчаливы:
 * ключа нет в словаре — игрок читает {@code villagepax.command.no_colony}
 * вместо фразы; ключ есть в одном языке и нет в другом — англоязычный
 * игрок видит то же самое; в переводах разное число подстановок — в строке
 * повисает сырое «%s» или пропадает число. Ни одна из трёх не роняет
 * ни сборку, ни игру.
 * <p>
 * Ключи из кода собираются по вызовам с ключом-строкой: {@code Text.translatable}
 * и помощники ответа, которым ключ отдают первым словом ({@code say},
 * {@code tell}). Ключи, собранные из частей ({@code "villagepax.level." + id}),
 * сюда не попадают — их сверяют свои проверки у данных.
 */
class LangTest {

    private static final Path LANG = Path.of("src", "main", "resources", "assets",
            "villagepax", "lang");
    private static final List<Path> SOURCES = List.of(
            Path.of("src", "main", "java"), Path.of("src", "client", "java"));

    private static final Pattern TRANSLATABLE =
            Pattern.compile("Text\\.translatable\\(\\s*\"([^\"]+)\"");
    private static final Pattern HELPER =
            Pattern.compile("\\b(?:say|tell)\\([^,()]+,\\s*\"(villagepax\\.[^\"]+)\"");
    private static final Pattern PLAIN_SLOT = Pattern.compile("%(?!\\d+\\$)s");
    private static final Pattern NUMBERED_SLOT = Pattern.compile("%(\\d+)\\$s");

    @Test
    void bothLanguagesKnowTheSameKeys() throws IOException {
        JsonObject ru = read("ru_ru");
        JsonObject en = read("en_us");
        Set<String> onlyRu = new TreeSet<>(ru.keySet());
        onlyRu.removeAll(en.keySet());
        Set<String> onlyEn = new TreeSet<>(en.keySet());
        onlyEn.removeAll(ru.keySet());
        assertTrue(onlyRu.isEmpty() && onlyEn.isEmpty(),
                "Ключ есть в одном языке и нет в другом: только русский " + onlyRu
                        + ", только английский " + onlyEn);
    }

    @Test
    void everyKeyTheCodeSaysIsTranslated() throws IOException {
        JsonObject ru = read("ru_ru");
        JsonObject en = read("en_us");
        Set<String> asked = keysInCode();
        assertTrue(asked.size() > 50, "Ключей в коде подозрительно мало — разбор сломан: "
                + asked.size());

        List<String> missing = new ArrayList<>();
        for (String key : asked) {
            if (!ru.has(key)) {
                missing.add(key + " (ru)");
            }
            if (!en.has(key)) {
                missing.add(key + " (en)");
            }
        }
        assertTrue(missing.isEmpty(), "Код говорит ключом, которого нет в словаре: " + missing);
    }

    /**
     * Столько же подстановок, сколько в другом языке.
     * <p>
     * Код отдаёт одинаковое число доводов обоим языкам; перевод, в котором
     * подстановкой меньше, молча теряет число, а подстановкой больше —
     * показывает «%s».
     */
    @Test
    void translationsTakeTheSameArguments() throws IOException {
        JsonObject ru = read("ru_ru");
        JsonObject en = read("en_us");
        List<String> uneven = new ArrayList<>();
        for (String key : ru.keySet()) {
            if (!en.has(key)) {
                continue;
            }
            String a = ru.get(key).getAsString();
            String b = en.get(key).getAsString();
            if (count(PLAIN_SLOT, a) != count(PLAIN_SLOT, b)
                    || !numbered(a).equals(numbered(b))) {
                uneven.add(key);
            }
        }
        assertTrue(uneven.isEmpty(), "Разное число подстановок в переводах: " + uneven);
    }

    /**
     * У каждой настройки есть имя и подсказка на обоих языках, у каждого
     * раздела — заголовок.
     * <p>
     * Экран собирает эти ключи из имени поля, и общий сбор ключей из кода
     * их не видит: «villagepax.config.» + поле — начало, а не ключ.
     */
    @Test
    void everySettingIsNamedAndExplained() throws IOException {
        JsonObject ru = read("ru_ru");
        JsonObject en = read("en_us");
        List<String> missing = new ArrayList<>();
        for (Config.Setting setting : Config.SETTINGS) {
            for (String key : List.of("villagepax.config." + setting.key(),
                    "villagepax.config." + setting.key() + ".tooltip",
                    "villagepax.config.group." + setting.group())) {
                if (!ru.has(key)) {
                    missing.add(key + " (ru)");
                }
                if (!en.has(key)) {
                    missing.add(key + " (en)");
                }
            }
        }
        assertTrue(missing.isEmpty(), "Настройка без имени в словаре: " + missing);
    }

    private static Set<String> keysInCode() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (Path root : SOURCES) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String code = Files.readString(file, StandardCharsets.UTF_8);
                    for (Pattern pattern : List.of(TRANSLATABLE, HELPER)) {
                        Matcher found = pattern.matcher(code);
                        while (found.find()) {
                            String key = found.group(1);
                            // Ключ, который дописывается по ходу, — начало,
                            // а не ключ: «villagepax.culture.» + народ.
                            if (!key.endsWith(".")) {
                                keys.add(key);
                            }
                        }
                    }
                }
            }
        }
        return keys;
    }

    private static int count(Pattern pattern, String text) {
        Matcher found = pattern.matcher(text);
        int many = 0;
        while (found.find()) {
            many++;
        }
        return many;
    }

    private static Set<String> numbered(String text) {
        Set<String> slots = new TreeSet<>();
        Matcher found = NUMBERED_SLOT.matcher(text);
        while (found.find()) {
            slots.add(found.group(1));
        }
        return slots;
    }

    private static JsonObject read(String language) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(language + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
