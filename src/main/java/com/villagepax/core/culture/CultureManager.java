package com.villagepax.core.culture;

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
import java.util.Set;

/**
 * Загружает культуры из датапаков при старте сервера и по {@code /reload}.
 * <p>
 * Файлы лежат в {@code data/<пространство имён>/villagepax/cultures/<имя>.json}.
 * Вложенная папка мода нужна, чтобы не отбирать у других модов общее имя
 * {@code cultures} и не пытаться разбирать их файлы своим кодеком.
 */
public class CultureManager extends JsonDataLoader implements IdentifiableResourceReloadListener {

    public static final Identifier ID = new Identifier(VillagePax.MOD_ID, "cultures");
    private static final String FOLDER = VillagePax.MOD_ID + "/cultures";
    private static final Gson GSON = new Gson();

    private static volatile Map<Identifier, Culture> cultures = Map.of();

    public CultureManager() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        Map<Identifier, Culture> loaded = new HashMap<>();

        prepared.forEach((id, json) -> Culture.CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> VillagePax.LOGGER.error("Культура {} не загружена: {}", id, error))
                .ifPresent(culture -> {
                    loaded.put(id, culture);
                    // Черта — включатель кода, и описка в ней означает
                    // народ без своего поведения. Молчать об этом нельзя.
                    Traits.audit(id, culture.traits());
                }));

        cultures = Collections.unmodifiableMap(loaded);

        if (loaded.isEmpty()) {
            VillagePax.LOGGER.warn("Не загружено ни одной культуры — поселения появляться не будут");
        } else {
            VillagePax.LOGGER.info("Загружено культур: {} ({})", loaded.size(), String.join(", ",
                    loaded.keySet().stream().map(Identifier::toString).sorted().toList()));
        }
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    public static Map<Identifier, Culture> all() {
        return cultures;
    }

    public static Set<Identifier> ids() {
        return cultures.keySet();
    }

    public static Culture get(Identifier id) {
        return cultures.get(id);
    }
}
