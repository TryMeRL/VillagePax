package com.villagepax.core.trade;

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

/**
 * Загружает столы торга из датапаков при старте сервера и по {@code /reload}.
 * <p>
 * Файлы лежат в {@code data/<пространство имён>/villagepax/trades/<имя>.json} —
 * вложенная папка мода по той же причине, что у культур, профессий, схем и
 * типов зданий: не отбирать у других модов общее имя.
 * <p>
 * Ключ — <b>культура из поля файла</b>, а не имя файла. У народа один стол
 * торга, и искать его надо по народу: житель знает, какого он племени, а как
 * назван файл — не знает. Второй файл на ту же культуру перекрыл бы первый,
 * и об этом сказано в логе: молча выбрать один из двух — значит спрятать
 * ошибку датапака.
 */
public class TradeTables extends JsonDataLoader implements IdentifiableResourceReloadListener {

    public static final Identifier ID = new Identifier(VillagePax.MOD_ID, "trades");

    private static final String FOLDER = VillagePax.MOD_ID + "/trades";
    private static final Gson GSON = new Gson();

    private static volatile Map<Identifier, TradeTable> tables = Map.of();

    public TradeTables() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager,
                         Profiler profiler) {
        Map<Identifier, TradeTable> loaded = new HashMap<>();

        prepared.forEach((id, json) -> TradeTable.CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error ->
                        VillagePax.LOGGER.error("Стол торга {} не загружен: {}", id, error))
                .ifPresent(table -> {
                    TradeTable already = loaded.put(table.culture(), table);
                    if (already != null) {
                        VillagePax.LOGGER.warn("У народа {} два стола торга; взят {}",
                                table.culture(), id);
                    }
                }));

        tables = Collections.unmodifiableMap(loaded);
        VillagePax.LOGGER.info("Загружено столов торга: {}", loaded.size());
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    public static Map<Identifier, TradeTable> all() {
        return tables;
    }

    /**
     * Стол торга народа.
     * <p>
     * Пусто — значит этот народ не торгует вовсе. Это законное умолчание:
     * замкнутое племя, у которого нечего купить, — тоже содержание, и
     * придумывать ему цены за автора датапака мод не станет.
     */
    public static Optional<TradeTable> of(Identifier culture) {
        return Optional.ofNullable(tables.get(culture));
    }
}
