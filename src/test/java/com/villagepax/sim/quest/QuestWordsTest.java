package com.villagepax.sim.quest;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * У каждой просьбы есть слова — на обоих языках.
 * <p>
 * Квест без перевода выглядит в игре так: старейшина подходит и говорит
 * {@code villagepax.quest.maya_guard_2}. Это не мелкая небрежность —
 * просьба и есть <b>всё</b> содержание квеста: цели и награды игрок
 * прочитает в окне числами, а зачем это нужно, ему говорят только здесь.
 * Двадцать восемь файлов глазами не сверяет никто.
 * <p>
 * Читаются и файлы квестов, и словари <b>с диска</b>, а не из игры:
 * поднимать Minecraft ради сверки списка строк — две минуты вместо
 * секунды. Заодно проверка ловит и обратное — строку в словаре,
 * которую никто не произносит: такие остаются после переименований
 * и живут годами.
 */
class QuestWordsTest {

    private static final Path QUESTS =
            Path.of("src/main/resources/data/villagepax/villagepax/quests");

    private static JsonObject dictionary(String language) {
        String path = "/assets/villagepax/lang/" + language + ".json";
        try (InputStream stream = QuestWordsTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("нет файла языка: " + path);
            }
            return JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Ключи реплик всех квестов датапака. */
    private static Set<String> dialogues() {
        Set<String> keys = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(QUESTS)) {
            files.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                try {
                    JsonObject quest = JsonParser.parseString(
                            Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                    keys.add(quest.get("dialogue").getAsString());
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            });
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return keys;
    }

    @Test
    void everyQuestSpeaksInBothLanguages() {
        JsonObject russian = dictionary("ru_ru");
        JsonObject english = dictionary("en_us");

        Set<String> keys = dialogues();
        assertFalse(keys.isEmpty(), "квестов не нашлось вовсе — проверять нечего");

        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!russian.has(key)) {
                missing.add("ru_ru: " + key);
            }
            if (!english.has(key)) {
                missing.add("en_us: " + key);
            }
        }

        assertTrue(missing.isEmpty(), "квесты без слов: " + missing);
    }

    /**
     * И ни одна реплика не переведена в пустоту.
     * <p>
     * Ключ есть, перевод есть, житель молчит: ровно тот случай, который
     * проверка выше пропустила бы, а игрок принял бы за поломку мода.
     */
    @Test
    void noQuestIsTranslatedToSilence() {
        JsonObject russian = dictionary("ru_ru");
        JsonObject english = dictionary("en_us");

        List<String> empty = new ArrayList<>();
        for (String key : dialogues()) {
            if (russian.has(key) && russian.get(key).getAsString().isBlank()) {
                empty.add("ru_ru: " + key);
            }
            if (english.has(key) && english.get(key).getAsString().isBlank()) {
                empty.add("en_us: " + key);
            }
        }

        assertTrue(empty.isEmpty(), "реплики, переведённые в молчание: " + empty);
    }

    /**
     * Слова, которые никто не произносит, — мусор.
     * <p>
     * Проверка обратная первой, и нужна она по опыту: реплики
     * переименовывают, а старые строки в словаре остаются и живут годами,
     * потому что удалить их некому. Ключ {@code villagepax.quest.*}
     * бывает двух родов: реплика квеста и служебная строка разговора
     * («нечего предложить», «пока не доверяю»); вторые сюда не входят,
     * и потому список исключений короткий и на виду.
     */
    @Test
    void noWordsAreSpokenByNobody() {
        Set<String> spoken = dialogues();
        Set<String> chatter = Set.of(
                "villagepax.quest.said", "villagepax.quest.done", "villagepax.quest.not_yet",
                "villagepax.quest.nothing_left", "villagepax.quest.too_far",
                "villagepax.quest.standing_up", "villagepax.quest.needs_colony");

        List<String> orphans = new ArrayList<>();
        for (String key : dictionary("ru_ru").keySet()) {
            if (!key.startsWith("villagepax.quest.")) {
                continue;
            }
            if (key.startsWith("villagepax.quest.objective.")
                    || key.startsWith("villagepax.quest.screen.")
                    // Подписи вкладок разговора — тоже не реплики.
                    || key.startsWith("villagepax.quest.tab.")
                    || chatter.contains(key) || spoken.contains(key)) {
                continue;
            }
            orphans.add(key);
        }

        assertTrue(orphans.isEmpty(), "слова без квеста: " + orphans);
    }
}
