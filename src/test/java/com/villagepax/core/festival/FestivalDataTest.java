package com.villagepax.core.festival;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Праздники встроенных народов сходятся с остальными данными мода.
 * <p>
 * Праздник называет народ, ключи слов и предметы лавки, и ни одна из этих
 * ссылок не проверяется при загрузке так, чтобы игра упала: народ без
 * праздника просто не празднует, неизвестный ключ рисуется ключом, а приз,
 * которого нет, лавка молча пропустит. Поэтому сверка — здесь, с диска,
 * без запуска игры.
 */
class FestivalDataTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path DATA = RESOURCES.resolve(Path.of("data", "villagepax", "villagepax"));
    private static final Path LANG = RESOURCES.resolve(Path.of("assets", "villagepax", "lang"));
    private static final Path JAVA = Path.of("src", "main", "java", "com", "villagepax");

    private static List<String> namesIn(String folder) throws IOException {
        try (Stream<Path> files = Files.list(DATA.resolve(folder))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }

    private static Map<String, Festival> festivals() throws IOException {
        Map<String, Festival> read = new LinkedHashMap<>();
        for (String name : namesIn("festivals")) {
            String json = Files.readString(DATA.resolve(Path.of("festivals", name + ".json")),
                    StandardCharsets.UTF_8);
            Festival festival = Festival.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .result().orElse(null);
            assertTrue(festival != null, "праздник " + name + " не читается");
            read.put(name, festival);
        }
        return read;
    }

    private static String lang(String code) throws IOException {
        return Files.readString(LANG.resolve(code + ".json"), StandardCharsets.UTF_8);
    }

    @Test
    void everyPeopleHasItsFestivalAndEveryFestivalItsPeople() throws IOException {
        Map<String, Festival> festivals = festivals();
        List<String> cultures = namesIn("cultures");
        List<String> wrong = new ArrayList<>();
        for (String culture : cultures) {
            boolean has = festivals.values().stream()
                    .anyMatch(festival -> festival.culture().toString().equals("villagepax:" + culture));
            if (!has) {
                wrong.add(culture + ": праздника нет");
            }
        }
        festivals.forEach((name, festival) -> {
            if (!cultures.contains(festival.culture().getPath())) {
                wrong.add(name + ": народа " + festival.culture() + " нет");
            }
        });
        assertFalse(festivals.isEmpty(), "праздников нет вовсе — сверять нечего");
        assertTrue(wrong.isEmpty(), "праздники и народы: " + wrong);
    }

    /** Соседние народы гуляют в разные дни: праздник, совпавший у всех, — один праздник на всех. */
    @Test
    void builtInPeoplesCelebrateOnDifferentDays() throws IOException {
        Set<Integer> phases = new HashSet<>();
        List<String> twins = new ArrayList<>();
        festivals().forEach((name, festival) -> {
            if (!phases.add(Math.floorMod(festival.moonPhase(), 8))) {
                twins.add(name + " в фазу " + festival.moonPhase());
            }
        });
        assertTrue(twins.isEmpty(), "праздники в один день: " + twins);
    }

    @Test
    void huntsHaveTokensAndChasesHaveCritters() throws IOException {
        List<String> wrong = new ArrayList<>();
        festivals().forEach((name, festival) -> {
            for (Festival.Contest contest : festival.contests()) {
                if (contest.kind() == ContestKind.HUNT && contest.token().isEmpty()) {
                    wrong.add(name + ": поиск без вещиц");
                }
                if (contest.kind() == ContestKind.CHASE && contest.critter().isEmpty()) {
                    wrong.add(name + ": ловля без зверька");
                }
            }
            if (festival.contests().stream().map(Festival.Contest::kind).distinct().count()
                    != ContestKind.values().length) {
                wrong.add(name + ": не все три состязания");
            }
        });
        assertTrue(wrong.isEmpty(), "состязания: " + wrong);
    }

    @Test
    void everyNameIsInBothDictionaries() throws IOException {
        String russian = lang("ru_ru");
        String english = lang("en_us");
        List<String> keys = new ArrayList<>();
        for (Festival festival : festivals().values()) {
            keys.add(festival.name());
            festival.contests().forEach(contest -> keys.add(contest.name()));
        }
        for (ContestKind kind : ContestKind.values()) {
            keys.add("villagepax.contest.rule." + kind.id());
        }
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!russian.contains("\"" + key + "\"")) {
                missing.add(key + " (ru)");
            }
            if (!english.contains("\"" + key + "\"")) {
                missing.add(key + " (en)");
            }
        }
        assertTrue(missing.isEmpty(), "слова праздников без перевода: " + missing);
    }

    /**
     * Приз лавки — предмет, который мод регистрирует, или ванильный.
     * <p>
     * Имена берутся из исходников реестров, как в сверке ресурсов блоков:
     * поднимать игру ради списка незачем.
     */
    @Test
    void everyPrizeIsARealItem() throws IOException {
        Set<String> registered = new HashSet<>();
        Pattern call = Pattern.compile("(?:register|add|hat)\\(\"([a-z0-9_]+)\"");
        for (Path source : List.of(JAVA.resolve(Path.of("block", "ModBlocks.java")),
                JAVA.resolve(Path.of("item", "ModItems.java")),
                JAVA.resolve(Path.of("item", "festival", "ModFestivalItems.java")))) {
            Matcher found = call.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (found.find()) {
                registered.add("villagepax:" + found.group(1));
            }
        }
        List<String> unknown = new ArrayList<>();
        festivals().forEach((name, festival) -> {
            for (Festival.Prize prize : festival.prizes()) {
                String id = prize.item().toString();
                if (!id.startsWith("minecraft:") && !registered.contains(id)) {
                    unknown.add(name + ": " + id);
                }
            }
            if (festival.prizes().isEmpty()) {
                unknown.add(name + ": лавка пуста");
            }
        });
        assertTrue(unknown.isEmpty(), "призы, которых нет в игре: " + unknown);
    }
}
