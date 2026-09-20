package com.villagepax.sim.faith;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villagepax.core.faith.Domain;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * У каждого отказа веры есть слова — на обоих языках.
 * <p>
 * Вердиктов в вере девятнадцать, и каждый — ответ на вопрос игрока
 * «почему не получилось». Ключ без перевода выглядит в чате как
 * {@code villagepax.faith.miracle.nothing_to_do}: формально это не
 * молчание, а на деле хуже молчания — игрок видит, что мод сломан.
 * <p>
 * Правило мода «отказ обязан говорить причину» заканчивается не в коде,
 * а в словаре, и проверять его надо здесь. Забыть строку легче всего
 * у того отказа, который срабатывает раз в сто игр, — а увидеть это
 * можно только в той самой сотой.
 * <p>
 * Читается файл языка <b>с диска</b>, а не из игры: поднимать Minecraft
 * ради сверки списка строк — две минуты вместо секунды.
 */
class FaithWordsTest {

    private static JsonObject dictionary(String language) {
        String path = "/assets/villagepax/lang/" + language + ".json";
        try (InputStream stream = FaithWordsTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("нет файла языка: " + path);
            }
            return JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception failure) {
            throw new IllegalStateException("не прочитался словарь " + language, failure);
        }
    }

    /** Все ключи, которые вера когда-либо покажет игроку. */
    private static Set<String> keysOfFaith() {
        Set<String> keys = new LinkedHashSet<>();

        for (Offering.Verdict verdict : Offering.Verdict.values()) {
            keys.add(verdict.key());
        }
        for (Blessings.Verdict verdict : Blessings.Verdict.values()) {
            keys.add(verdict.key());
        }
        for (Miracles.Verdict verdict : Miracles.Verdict.values()) {
            keys.add(verdict.key());
        }
        for (Faith.Tier tier : Faith.Tier.values()) {
            keys.add(tier.key());
        }
        for (Domain domain : Domain.values()) {
            keys.add(domain.key());
        }

        // Строки алтаря и артефактов: их собирает не перечисление,
        // поэтому список написан руками — и потому же он здесь короткий.
        keys.add("villagepax.faith.altar.nowhere");
        keys.add("villagepax.faith.altar.head");
        keys.add("villagepax.faith.altar.line");
        keys.add("villagepax.faith.artifact.granted");
        keys.add("villagepax.artifact.not_here");
        keys.add("villagepax.artifact.tooltip.gift");
        for (Domain domain : Domain.values()) {
            keys.add("villagepax.artifact.tooltip." + domain.id());
        }

        return keys;
    }

    @Test
    void everyVerdictHasWordsInBothLanguages() {
        JsonObject russian = dictionary("ru_ru");
        JsonObject english = dictionary("en_us");

        List<String> missing = new ArrayList<>();
        for (String key : keysOfFaith()) {
            if (!russian.has(key)) {
                missing.add("ru_ru: " + key);
            }
            if (!english.has(key)) {
                missing.add("en_us: " + key);
            }
        }

        assertTrue(missing.isEmpty(), "у веры есть ключи без перевода: " + missing);
    }

    /**
     * Пустая строка — то же молчание, только с другой стороны.
     * <p>
     * Ключ есть, перевод есть, игрок не видит ничего: ровно тот случай,
     * который проверка выше пропустила бы.
     */
    @Test
    void noVerdictIsTranslatedToSilence() {
        JsonObject russian = dictionary("ru_ru");
        JsonObject english = dictionary("en_us");

        List<String> empty = new ArrayList<>();
        for (String key : keysOfFaith()) {
            if (russian.has(key) && russian.get(key).getAsString().isBlank()) {
                empty.add("ru_ru: " + key);
            }
            if (english.has(key) && english.get(key).getAsString().isBlank()) {
                empty.add("en_us: " + key);
            }
        }

        assertTrue(empty.isEmpty(), "у веры есть отказы, переведённые в пустоту: " + empty);
    }
}
