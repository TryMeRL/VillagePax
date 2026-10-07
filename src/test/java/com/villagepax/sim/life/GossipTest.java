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
import java.util.UUID;

import static com.villagepax.sim.life.Gossip.Topic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** О чём судачат на вечерней площади: только правда, и не о себе. */
class GossipTest {

    private static final UUID SPEAKER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @Test
    void theTruthAboutOthersIsWhatTheyTalkAbout() {
        List<Gossip.Person> people = List.of(
                new Gossip.Person(SPEAKER, "Ren", true, true, true, false),
                new Gossip.Person(OTHER, "Sora", true, true, true, true));
        List<Gossip.Item> items = Gossip.items(SPEAKER, people, true, true, Optional.of("Steve"));

        assertTrue(items.contains(new Gossip.Item(Topic.HUNGRY, "Sora")));
        assertTrue(items.contains(new Gossip.Item(Topic.HOMELESS, "Sora")));
        assertTrue(items.contains(new Gossip.Item(Topic.NEWCOMER, "Sora")));
        assertTrue(items.contains(new Gossip.Item(Topic.NEWBORN, "Sora")));
        assertTrue(items.contains(new Gossip.Item(Topic.FESTIVAL, "")));
        assertTrue(items.contains(new Gossip.Item(Topic.RAIDED, "")));
        assertTrue(items.contains(new Gossip.Item(Topic.PLAYER, "Steve")));
        assertFalse(items.stream().anyMatch(item -> item.about().equals("Ren")),
                "о себе не сплетничают: " + items);
    }

    @Test
    void thereIsAlwaysTheVillage() {
        List<Gossip.Item> items = Gossip.items(SPEAKER,
                List.of(new Gossip.Person(SPEAKER, "Ren", false, false, false, false)),
                false, false, Optional.empty());
        assertEquals(List.of(new Gossip.Item(Topic.VILLAGE, "")), items);
    }

    @Test
    void everyTopicHasWordsAndThereAreReplies() throws IOException {
        JsonObject ru = JsonParser.parseString(Files.readString(Path.of("src", "main", "resources",
                "assets", "villagepax", "lang", "ru_ru.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (Topic topic : Topic.values()) {
            for (int n = 1; n <= 3; n++) {
                assertTrue(ru.has(topic.base() + "." + n), "Сплетне " + topic + " нечего сказать: "
                        + topic.base() + "." + n);
            }
        }
        for (int n = 1; n <= 5; n++) {
            assertTrue(ru.has(Gossip.REPLY + "." + n), "Нет ответа " + Gossip.REPLY + "." + n);
        }
    }
}
