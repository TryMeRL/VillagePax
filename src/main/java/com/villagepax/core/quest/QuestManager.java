package com.villagepax.core.quest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Загружает квесты из датапаков при старте сервера и по {@code /reload}.
 * <p>
 * Файлы лежат в {@code data/<пространство имён>/villagepax/quests/<путь>.json} —
 * тем же порядком, что культуры и профессии, и по той же причине: вложенная
 * папка мода не отбирает у других модов общее имя {@code quests}.
 */
public class QuestManager extends JsonDataLoader implements IdentifiableResourceReloadListener {

    public static final Identifier ID = new Identifier(VillagePax.MOD_ID, "quests");
    private static final String FOLDER = VillagePax.MOD_ID + "/quests";
    private static final Gson GSON = new Gson();

    private static volatile Map<Identifier, Quest> quests = Map.of();

    public QuestManager() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager,
                         Profiler profiler) {
        Map<Identifier, Quest> loaded = new HashMap<>();

        prepared.forEach((id, json) -> Quest.CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> VillagePax.LOGGER.error("Квест {} не загружен: {}", id, error))
                .ifPresent(quest -> loaded.put(id, quest)));

        quests = Collections.unmodifiableMap(loaded);
        VillagePax.LOGGER.info("Загружено квестов: {}", loaded.size());
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    public static Map<Identifier, Quest> all() {
        return quests;
    }

    public static Set<Identifier> ids() {
        return quests.keySet();
    }

    public static Optional<Quest> get(Identifier id) {
        return Optional.ofNullable(quests.get(id));
    }

    /**
     * Начало цепочки: квест, на который никто не ссылается как на следующий.
     * <p>
     * Выводится, а не объявляется полем: поле «первый» рассыпалось бы, стоило
     * автору датапака добавить вторую цепочку и забыть его переставить.
     */
    public static Optional<Identifier> firstOf(Identifier giver, Identifier culture) {
        Set<Identifier> linked = quests.values().stream()
                .map(Quest::next)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(java.util.stream.Collectors.toSet());

        return quests.entrySet().stream()
                .filter(entry -> entry.getValue().giver().equals(giver))
                // Народ важен не меньше выдающего: цепочек с одним и тем же
                // старейшиной столько же, сколько народов, и голова у каждой
                // своя. Без этого игрок услышал бы от майя норманнскую просьбу.
                .filter(entry -> entry.getValue().fitsCulture(culture))
                .map(Map.Entry::getKey)
                .filter(id -> !linked.contains(id))
                .sorted(java.util.Comparator.comparing(Identifier::toString))
                .findFirst();
    }
}
