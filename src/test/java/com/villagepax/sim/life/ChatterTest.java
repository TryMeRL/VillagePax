package com.villagepax.sim.life;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Optional;

import static com.villagepax.sim.life.Chatter.Topic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * О чём житель скажет: срочное — первым и одно, иначе что-нибудь из того,
 * что правда сейчас; об игроке — по доверию.
 */
class ChatterTest {

    /** Спокойный взрослый пахарь, незнакомец перед ним. */
    private static Chatter.Situation calm() {
        return new Chatter.Situation(false, false, Optional.empty(), false, false, false, false,
                false, false, false, 0, Optional.of("farmer"), false);
    }

    @Test
    void aSiegeDrownsEverythingElse() {
        Chatter.Situation besieged = new Chatter.Situation(true, true, Optional.of("Ren"), true,
                true, true, true, true, true, false, 50, Optional.of("farmer"), false);
        assertEquals(List.of(Topic.BESIEGED), Chatter.topics(besieged));
    }

    @Test
    void hungerComesBeforeSmallTalk() {
        Chatter.Situation hungry = new Chatter.Situation(false, true, Optional.empty(), false,
                true, true, false, false, true, false, 50, Optional.of("farmer"), false);
        assertEquals(List.of(Topic.HUNGRY), Chatter.topics(hungry));
    }

    @Test
    void thePlayerIsGreetedByTrust() {
        assertTrue(Chatter.topics(withTrust(60)).contains(Topic.FRIEND));
        assertTrue(Chatter.topics(withTrust(0)).contains(Topic.STRANGER));
        assertTrue(Chatter.topics(withTrust(-10)).contains(Topic.WARY));
        assertFalse(Chatter.topics(withTrust(-10)).contains(Topic.FRIEND));
    }

    @Test
    void theOwnerIsTheOwner() {
        Chatter.Situation home = new Chatter.Situation(false, false, Optional.empty(), false, false,
                false, false, false, false, true, 0, Optional.of("farmer"), false);
        List<Topic> topics = Chatter.topics(home);
        assertTrue(topics.contains(Topic.OWNER));
        assertFalse(topics.contains(Topic.STRANGER));
    }

    @Test
    void aChildDoesNotTalkShop() {
        Chatter.Situation child = new Chatter.Situation(false, false, Optional.empty(), false, true,
                false, true, false, false, false, 0, Optional.empty(), true);
        List<Topic> topics = Chatter.topics(child);
        assertFalse(topics.contains(Topic.WORK));
        assertFalse(topics.contains(Topic.NO_BUILDER));
        assertTrue(topics.contains(Topic.FESTIVAL));
    }

    @Test
    void everyTruthHasItsTopic() {
        Chatter.Situation busy = new Chatter.Situation(false, false, Optional.of("Ren"), true, true,
                true, true, true, true, false, 0, Optional.of("farmer"), false);
        assertTrue(Chatter.topics(busy).containsAll(List.of(Topic.NEWBORN, Topic.RAIDED,
                Topic.FESTIVAL, Topic.HOMELESS, Topic.NO_BUILDER, Topic.NEWCOMER, Topic.RAIN,
                Topic.STRANGER, Topic.WORK)));
    }

    @Test
    void thereIsAlwaysSomethingToSay() {
        assertFalse(Chatter.topics(calm()).isEmpty());
        assertEquals(Topic.STRANGER, Chatter.choose(List.of(Topic.STRANGER, Topic.WORK), bound -> 0));
        assertEquals(Topic.WORK, Chatter.choose(List.of(Topic.STRANGER, Topic.WORK), bound -> 1));
    }

    /**
     * У каждой темы есть что сказать — хотя бы три общие фразы; у дела —
     * хотя бы две на каждое ремесло. Ключ собирается из частей, и
     * {@code LangTest} забытый пул не увидит: он всплыл бы голым ключом
     * над головой жителя.
     */
    @Test
    void everyTopicHasWords() throws IOException {
        JsonObject ru = JsonParser.parseString(Files.readString(Path.of("src", "main", "resources",
                "assets", "villagepax", "lang", "ru_ru.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (Topic topic : Topic.values()) {
            if (topic == Topic.WORK) {
                continue;
            }
            for (int n = 1; n <= 3; n++) {
                String key = topic.base() + "." + n;
                assertTrue(ru.has(key), "Теме " + topic + " нечего сказать: нет " + key);
            }
        }
        try (var professions = Files.list(Path.of("src", "main", "resources", "data", "villagepax",
                "villagepax", "professions"))) {
            for (Path file : professions.toList()) {
                String trade = file.getFileName().toString().replace(".json", "");
                for (int n = 1; n <= 2; n++) {
                    String key = Topic.WORK.base() + "." + trade + "." + n;
                    assertTrue(ru.has(key), "Ремеслу " + trade + " нечего сказать о деле: нет " + key);
                }
            }
        }
    }

    private static Chatter.Situation withTrust(int trust) {
        return new Chatter.Situation(false, false, Optional.empty(), false, false, false, false,
                false, false, false, trust, Optional.of("farmer"), false);
    }
}
