package com.villagepax.core.festival;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.registry.Registries;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Загружает праздники народов из датапаков при старте сервера и по {@code /reload}.
 * <p>
 * Файлы лежат в {@code data/<пространство имён>/villagepax/festivals/<имя>.json} —
 * вложенная папка мода по той же причине, что у богов и торга: не отбирать
 * у других модов общее имя.
 * <p>
 * Ключ — опознаватель файла, а народ — поле, как у богов. Праздник у народа
 * один: второй файл того же народа — ошибка датапака, о ней говорится в лог,
 * а праздником остаётся первый по имени файла.
 */
public class Festivals extends JsonDataLoader implements IdentifiableResourceReloadListener {

    public static final Identifier ID = new Identifier(VillagePax.MOD_ID, "festivals");

    private static final String FOLDER = VillagePax.MOD_ID + "/festivals";
    private static final Gson GSON = new Gson();

    private static volatile Map<Identifier, Festival> festivals = Map.of();

    public Festivals() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager,
                         Profiler profiler) {
        Map<Identifier, Festival> loaded = new LinkedHashMap<>();

        prepared.entrySet().stream()
                // Порядок загрузки датапака не определён, а «первый по имени»
                // обязан быть одним и тем же от запуска к запуску.
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> Festival.CODEC
                        .parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(error -> VillagePax.LOGGER.error("Праздник {} не загружен: {}",
                                entry.getKey(), error))
                        .ifPresent(festival -> loaded.put(entry.getKey(), festival)));

        festivals = Collections.unmodifiableMap(loaded);
        VillagePax.LOGGER.info("Загружено праздников: {}", loaded.size());

        loaded.forEach(Festivals::audit);
        Map<Identifier, Identifier> perCulture = new LinkedHashMap<>();
        loaded.forEach((id, festival) -> {
            Identifier first = perCulture.putIfAbsent(festival.culture(), id);
            if (first != null) {
                VillagePax.LOGGER.warn("У народа {} два праздника — {} и {}; праздником будет {}",
                        festival.culture(), first, id, first);
            }
        });
    }

    /**
     * Жалобы на праздник, который прочитался, но в игре не сработает.
     * <p>
     * Жалоба, а не отказ: датапак чужого мода не должен ронять загрузку.
     * Но всё, что игрок увидел бы как поломку, — сказано вслух: поиск
     * без вещиц, ловля без зверьков, приз, которого нет в игре.
     */
    private static void audit(Identifier id, Festival festival) {
        if (festival.moonPhase() < 0 || festival.moonPhase() > 7) {
            VillagePax.LOGGER.warn("Праздник {}: фаза луны {} вне 0..7 — возьмётся по кругу",
                    id, festival.moonPhase());
        }
        for (Festival.Contest contest : festival.contests()) {
            if (contest.kind() == ContestKind.HUNT && contest.token().isEmpty()) {
                VillagePax.LOGGER.warn("Праздник {}: поиск «{}» без вещиц — не начнётся",
                        id, contest.name());
            }
            if (contest.kind() == ContestKind.CHASE && contest.critter().isEmpty()) {
                VillagePax.LOGGER.warn("Праздник {}: ловля «{}» без зверька — не начнётся",
                        id, contest.name());
            }
        }
        for (Festival.Prize prize : festival.prizes()) {
            if (prize.price() < 1) {
                VillagePax.LOGGER.warn("Праздник {}: приз {} даром — цена {}", id, prize.item(),
                        prize.price());
            }
            if (!Registries.ITEM.containsId(prize.item())) {
                VillagePax.LOGGER.warn("Праздник {}: приза {} нет в игре", id, prize.item());
            }
        }
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    public static Map<Identifier, Festival> all() {
        return festivals;
    }

    /** Праздник народа — первый по имени файла, если их несколько. */
    public static Optional<Festival> of(Identifier culture) {
        return festivals.entrySet().stream()
                .filter(entry -> entry.getValue().culture().equals(culture))
                .min(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .map(Map.Entry::getValue);
    }
}
