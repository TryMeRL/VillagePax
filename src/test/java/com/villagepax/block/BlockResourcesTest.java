package com.villagepax.block;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * У каждого блока мода есть всё, без чего он в игре выглядит поломкой.
 * <p>
 * Блок без блокстейта — фиолетово-чёрный куб. Без модели предмета — такой же
 * куб в руке. Без таблицы добычи — блок, который ломается в ничто, и игрок
 * теряет постройку. Без строки словаря — надпись
 * {@code block.villagepax.thatch_slab} посреди инвентаря.
 * <p>
 * Ни одно из этого не ломает запуск, и потому ни одно не находится само:
 * заметить можно, только поставив блок в игре — то есть никогда, если
 * ставить его будет игрок, а не автор. Одиннадцать блоков строительного
 * набора родились разом, и разом же могли бы родиться без половины файлов.
 * <p>
 * Читается с диска, а не из игры: запуск Minecraft ради сверки списков
 * файлов — это две минуты против одной секунды.
 */
class BlockResourcesTest {

    /** Корень ресурсов мода — от каталога модуля, как его видит Gradle. */
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /**
     * Имена блоков берутся из <b>исходника</b> реестра, а не из реестра:
     * поднимать ради этого игру незачем, а строка {@code register("имя"}
     * в {@code ModBlocks} — тот же самый список, только читаемый без неё.
     */
    private static List<String> blockNames() {
        Path source = Path.of("src", "main", "java", "com", "villagepax", "block",
                "ModBlocks.java");
        List<String> names = new ArrayList<>();
        try {
            String java = Files.readString(source, StandardCharsets.UTF_8);
            int at = 0;
            while (true) {
                int call = java.indexOf("register(\"", at);
                if (call < 0) {
                    break;
                }
                int open = call + "register(\"".length();
                int close = java.indexOf('"', open);
                names.add(java.substring(open, close));
                at = close;
            }
        } catch (Exception broken) {
            throw new IllegalStateException("не читается " + source, broken);
        }
        return names;
    }

    private static boolean exists(String... parts) {
        Path path = RESOURCES;
        for (String part : parts) {
            path = path.resolve(part);
        }
        return Files.exists(path);
    }

    private static String langOf(String language) {
        Path path = RESOURCES.resolve(Path.of("assets", "villagepax", "lang", language + ".json"));
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception broken) {
            throw new IllegalStateException("не читается словарь " + path, broken);
        }
    }

    @Test
    void everyBlockHasItsResources() {
        List<String> names = blockNames();
        assertFalse(names.isEmpty(), "в ModBlocks не нашлось ни одного блока");

        String russian = langOf("ru_ru");
        String english = langOf("en_us");

        List<String> missing = new ArrayList<>();
        for (String name : names) {
            if (name.startsWith("marker_")) {
                // Маркеры игрок не видит: билдер меняет их на воздух,
                // и в мире их не бывает вовсе.
                continue;
            }
            if (!exists("assets", "villagepax", "blockstates", name + ".json")) {
                missing.add(name + ": нет блокстейта — в мире будет чёрно-фиолетовый куб");
            }
            if (!exists("assets", "villagepax", "models", "item", name + ".json")) {
                missing.add(name + ": нет модели предмета — такой же куб в руке");
            }
            if (!exists("data", "villagepax", "loot_tables", "blocks", name + ".json")) {
                missing.add(name + ": нет таблицы добычи — ломается в ничто");
            }
            String key = "\"block.villagepax." + name + "\"";
            if (!russian.contains(key)) {
                missing.add(name + ": нет русского названия");
            }
            if (!english.contains(key)) {
                missing.add(name + ": нет английского названия");
            }
        }

        assertTrue(missing.isEmpty(), "блоки без ресурсов:\n  " + String.join("\n  ", missing));
    }

    /**
     * И каждый построенный блок чем-то добывается.
     * <p>
     * Блок вне {@code mineable}-тегов ломается голыми руками не медленнее,
     * чем киркой, и любой мод, который раздаёт своим инструментам скорость
     * по этим тегам, наш блок не увидит. Это ровно та мелочь, из-за которой
     * мод считают «неродным».
     */
    @Test
    void everyBuildingBlockIsMineableBySomething() {
        String tools = Stream.of("pickaxe", "axe", "hoe", "shovel")
                .map(tool -> {
                    Path path = RESOURCES.resolve(Path.of("data", "minecraft", "tags", "blocks",
                            "mineable", tool + ".json"));
                    try {
                        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
                    } catch (Exception broken) {
                        throw new IllegalStateException("не читается тег " + path, broken);
                    }
                })
                .reduce("", String::concat);

        List<String> missing = new ArrayList<>();
        for (String name : blockNames()) {
            if (name.startsWith("marker_") || name.equals("town_hall")) {
                // Ратуша ломается по своим правилам — у неё и блок-энтити,
                // и целая колония за спиной.
                continue;
            }
            if (!tools.contains("\"villagepax:" + name + "\"")) {
                missing.add(name);
            }
        }

        assertTrue(missing.isEmpty(), "блоки, которые нечем добывать: " + missing);
    }

    /** Файлы словаря читаются и с диска, и из собранных ресурсов — на всякий случай. */
    @Test
    void resourcesLiveWhereExpected() {
        try (InputStream stream = BlockResourcesTest.class
                .getResourceAsStream("/assets/villagepax/lang/ru_ru.json")) {
            assertTrue(stream != null, "словарь не попал в ресурсы сборки");
        } catch (Exception broken) {
            throw new IllegalStateException("словарь не читается", broken);
        }
    }
}
