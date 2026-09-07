package com.villagepax.sim.build;

import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Загружает схемы зданий из датапаков при старте сервера и по {@code /reload}.
 * <p>
 * Файлы лежат в {@code data/<пространство имён>/villagepax/schematics/<путь>.nbt}.
 * Вложенная папка мода нужна по той же причине, что и у культур: в ванильную
 * {@code structures/} кладут файлы все моды, и наш загрузчик не должен пытаться
 * разобрать чужие.
 */
public class SchematicLoader implements SimpleSynchronousResourceReloadListener {

    public static final Identifier ID = new Identifier(VillagePax.MOD_ID, "schematics");

    private static final String FOLDER = VillagePax.MOD_ID + "/schematics";
    private static final String PREFIX = FOLDER + "/";
    private static final String EXTENSION = ".nbt";

    private static volatile Map<Identifier, Schematic> schematics = Map.of();

    /**
     * Прогрев планов вешается на события, наступающие <b>после</b> перезагрузки
     * датапаков: к этому моменту теги уже привязаны к записям реестра, а значит
     * категории блоков считаются правильно. Сам расчёт ленивый и без прогрева
     * тоже верен — прогрев лишь убирает рывок на первом обращении в игре.
     */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> warmUp());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success) {
                warmUp();
            }
        });
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public void reload(ResourceManager manager) {
        RegistryEntryLookup<Block> blocks = Registries.BLOCK.getReadOnlyWrapper();
        Map<Identifier, Schematic> loaded = new HashMap<>();

        manager.findResources(FOLDER, path -> path.getPath().endsWith(EXTENSION))
                .forEach((resourceId, resource) -> read(resourceId, resource, blocks)
                        .ifPresent(schematic -> loaded.put(schematic.id(), schematic)));

        schematics = Collections.unmodifiableMap(loaded);

        if (loaded.isEmpty()) {
            VillagePax.LOGGER.warn("Не загружено ни одной схемы — строить будет нечего");
        } else {
            VillagePax.LOGGER.info("Загружено схем: {} ({})", loaded.size(), String.join(", ",
                    loaded.keySet().stream().map(Identifier::toString).sorted().toList()));
        }
    }

    /**
     * Битая схема логируется и пропускается, а не роняет перезагрузку целиком:
     * то же правило, что и для повреждённой записи поселения. Один плохой файл
     * не должен лишать игрока всех остальных зданий.
     */
    private static Optional<Schematic> read(Identifier resourceId, Resource resource,
                                            RegistryEntryLookup<Block> blocks) {
        // Имя схемы вычисляется внутри try намеренно: файл с пустым или
        // недопустимым именем ронял бы разбор идентификатора, а не одну схему.
        try (InputStream stream = resource.getInputStream()) {
            Identifier id = schematicId(resourceId);
            NbtCompound nbt = NbtIo.readCompressed(stream);
            return Optional.of(SchematicParser.parse(id, nbt, blocks));
        } catch (Exception failure) {
            VillagePax.LOGGER.error("Схема из {} не загружена: {}", resourceId, failure.toString());
            return Optional.empty();
        }
    }

    /** {@code villagepax:villagepax/schematics/norman/town_hall_lvl1.nbt} → {@code villagepax:norman/town_hall_lvl1}. */
    private static Identifier schematicId(Identifier resourceId) {
        String path = resourceId.getPath();
        return new Identifier(resourceId.getNamespace(),
                path.substring(PREFIX.length(), path.length() - EXTENSION.length()));
    }

    /** Считает планы всех схем заранее. Возвращает общее число блоков к установке. */
    public static int warmUp() {
        int blocks = 0;
        for (Schematic schematic : schematics.values()) {
            blocks += schematic.plan().blockCount();
        }
        if (!schematics.isEmpty()) {
            VillagePax.LOGGER.debug("Планы стройки готовы: схем {}, блоков {}", schematics.size(), blocks);
        }
        return blocks;
    }

    public static Map<Identifier, Schematic> all() {
        return schematics;
    }

    public static Set<Identifier> ids() {
        return schematics.keySet();
    }

    public static Optional<Schematic> get(Identifier id) {
        return Optional.ofNullable(schematics.get(id));
    }
}
