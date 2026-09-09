package com.villagepax.core.config;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Настройки читаются кодеком, поэтому проверяются без запуска игры.
 * <p>
 * Главное здесь — <b>устойчивость к тому, что напишет игрок</b>. Файл
 * настроек правят руками, и в нём будет всё: пропущенные поля, лишние,
 * числа за границами и противоречия. Каждый из этих случаев обязан
 * кончаться работающим модом, а не отсутствующим.
 */
class ConfigTest {

    private static DataResult<Config> parse(String json) {
        return Config.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    @Test
    void roundTripsThroughJson() {
        Config config = new Config(false, 32, 2.5, 3, 9, 128, 4, 20, false, false);

        DataResult<com.google.gson.JsonElement> encoded =
                Config.CODEC.encodeStart(JsonOps.INSTANCE, config);
        Config back = Config.CODEC.parse(JsonOps.INSTANCE,
                        encoded.result().orElseThrow(() -> new AssertionError("не закодировалось")))
                .result().orElseThrow(() -> new AssertionError("не прочиталось"));

        assertEquals(config, back);
    }

    /** Файл, в котором игрок оставил одну строку, обязан работать. */
    @Test
    void missingFieldsFallBackToDefaults() {
        Config config = parse("{\"autonomous_villages\": false}")
                .result().orElseThrow(() -> new AssertionError("одна строка не прочиталась"));

        assertEquals(false, config.autonomousVillages());
        assertEquals(Config.DEFAULT.hungerWarnDays(), config.hungerWarnDays());
        assertEquals(Config.DEFAULT.roadReserve(), config.roadReserve());
        assertEquals(Config.DEFAULT.populationScale(), config.populationScale());
    }

    /** Пустой файл — это просто значения по умолчанию, а не отказ. */
    @Test
    void emptyFileIsAllDefaults() {
        assertEquals(Config.DEFAULT, parse("{}")
                .result().orElseThrow(() -> new AssertionError("пустой файл не прочитался")));
    }

    /** Незнакомое поле не роняет чтение: кодек читает только известные. */
    @Test
    void unknownFieldsAreIgnored() {
        Config config = parse("{\"mortality\": true, \"tax_rate\": 0.2}")
                .result().orElseThrow(() -> new AssertionError("лишние поля уронили чтение"));

        assertEquals(Config.DEFAULT, config);
    }

    /**
     * Число за границами <b>не принимается, но и не роняет мод</b>: кодек
     * подставляет значение по умолчанию.
     * <p>
     * Так устроен DFU: {@code optionalFieldOf} глотает ошибку вложенного
     * кодека и молча берёт значение по умолчанию. Проверяется здесь именно
     * это поведение, а не желаемое, — потому что оно и будет у игрока.
     */
    @Test
    void impossibleNumbersFallBackInsteadOfBreakingTheMod() {
        Config tooFast = parse("{\"ticks_per_decision\": 0}")
                .result().orElseThrow(() -> new AssertionError("мод перестал читать настройки"));
        assertEquals(Config.DEFAULT.ticksPerDecision(), tooFast.ticksPerDecision());

        Config noPeople = parse("{\"population_scale\": 0.0}")
                .result().orElseThrow(() -> new AssertionError("мод перестал читать настройки"));
        assertEquals(Config.DEFAULT.populationScale(), noPeople.populationScale());

        Config negative = parse("{\"road_reserve\": -5}")
                .result().orElseThrow(() -> new AssertionError("мод перестал читать настройки"));
        assertEquals(Config.DEFAULT.roadReserve(), negative.roadReserve());
    }

    /**
     * И об этом игроку говорят.
     * <p>
     * Молчание тут — худшее из возможных: игрок написал число, мод его
     * не взял, а понять это можно только по поведению деревни через
     * несколько игровых дней.
     */
    @Test
    void rejectedValuesAreNamedInTheLog() {
        com.google.gson.JsonElement written =
                JsonParser.parseString("{\"ticks_per_decision\": 0, \"road_reserve\": 8}");
        var complaints = Configs.disagreements(written);

        assertEquals(1, complaints.size(), "жаловаться надо только на непринятое: " + complaints);
        assertTrue(complaints.get(0).startsWith("ticks_per_decision"), complaints.get(0));
    }

    /** Незнакомое поле — не повод жаловаться: это может быть чужая версия. */
    @Test
    void unknownFieldsDrawNoComplaint() {
        com.google.gson.JsonElement written = JsonParser.parseString("{\"mortality\": true}");
        assertTrue(Configs.disagreements(written).isEmpty());
    }

    /**
     * Порядок «сперва предупредили, потом ушёл» обязан сохраняться. С
     * обратными сроками житель уходил бы, ни разу не предупредив, и игрок
     * не понял бы, за что.
     */
    @Test
    void warningAlwaysComesBeforeLeaving() {
        Config swapped = parse("{\"hunger_warn_days\": 8, \"hunger_leave_days\": 3}")
                .result().orElseThrow(() -> new AssertionError("не прочиталось"));

        assertTrue(swapped.hungerLeaveDays() > swapped.hungerWarnDays(),
                "уход должен быть позже предупреждения");
        assertEquals(9, swapped.hungerLeaveDays());
    }

    /**
     * Таблица допустимых значений обязана покрывать все поля файла: поле
     * без записи в ней принималось бы молча, а на этой проверке всё
     * и держится.
     */
    @Test
    void everyFieldHasAnAllowedRange() {
        com.google.gson.JsonElement full = Config.CODEC.encodeStart(JsonOps.INSTANCE,
                new Config(false, 32, 2.5, 3, 9, 128, 4, 20, false, false))
                .result().orElseThrow();

        for (String key : full.getAsJsonObject().keySet()) {
            assertTrue(Config.RANGES.containsKey(key), "поле без проверки диапазона: " + key);
        }
        assertEquals(full.getAsJsonObject().size(), Config.RANGES.size(),
                "таблица диапазонов и поля файла разошлись");
    }

    @Test
    void defaultsMatchWhatTheModShipped() {
        assertEquals(true, Config.DEFAULT.autonomousVillages());
        assertEquals(4, Config.DEFAULT.hungerWarnDays());
        assertEquals(6, Config.DEFAULT.hungerLeaveDays());
        assertEquals(16, Config.DEFAULT.roadReserve());
        assertEquals(10, Config.DEFAULT.ticksPerDecision());

        // Ноль значит «шаг сетки берётся из культуры», а не «деревни вплотную».
        assertEquals(0, Config.DEFAULT.villageSpacingChunks());
        assertNotEquals(0.0, Config.DEFAULT.populationScale());
    }
}
