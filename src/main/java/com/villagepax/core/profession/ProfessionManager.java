package com.villagepax.core.profession;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Загружает профессии из датапаков при старте сервера и по {@code /reload}.
 * <p>
 * Файлы лежат в {@code data/<пространство имён>/villagepax/professions/<имя>.json} —
 * вложенная папка мода по той же причине, что у культур и схем: не отбирать
 * у других модов общее имя и не пытаться разбирать их файлы своим кодеком.
 */
public class ProfessionManager extends JsonDataLoader implements IdentifiableResourceReloadListener {

    public static final Identifier ID = new Identifier(VillagePax.MOD_ID, "professions");

    private static final String FOLDER = VillagePax.MOD_ID + "/professions";
    private static final Gson GSON = new Gson();

    private static volatile Map<Identifier, Profession> professions = Map.of();

    public ProfessionManager() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        Map<Identifier, Profession> loaded = new HashMap<>();

        prepared.forEach((id, json) -> Profession.CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> VillagePax.LOGGER.error("Профессия {} не загружена: {}", id, error))
                .ifPresent(profession -> loaded.put(id, profession)));

        professions = Collections.unmodifiableMap(loaded);

        if (loaded.isEmpty()) {
            VillagePax.LOGGER.warn("Не загружено ни одной профессии — жители работать не будут");
        } else {
            VillagePax.LOGGER.info("Загружено профессий: {} ({})", loaded.size(), String.join(", ",
                    loaded.keySet().stream().map(Identifier::toString).sorted().toList()));
        }
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    public static Map<Identifier, Profession> all() {
        return professions;
    }

    public static Set<Identifier> ids() {
        return professions.keySet();
    }

    public static Optional<Profession> get(Identifier id) {
        return Optional.ofNullable(professions.get(id));
    }

    /**
     * Профессии в порядке нужности: сперва самая важная.
     * <p>
     * Порядок доопределён идентификатором, а не оставлен на волю карты:
     * иначе при равном приоритете пришедшие жители получали бы разные
     * профессии от запуска к запуску.
     */
    public static List<Identifier> byHiringPriority() {
        List<Identifier> ordered = new ArrayList<>(professions.keySet());
        ordered.sort(Comparator
                .comparingInt((Identifier id) -> -professions.get(id).hiringPriority())
                .thenComparing(Identifier::toString));
        return ordered;
    }
}
