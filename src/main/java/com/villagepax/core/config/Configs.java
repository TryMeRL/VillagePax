package com.villagepax.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.villagepax.VillagePax;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Файл настроек: чтение, запись значений по умолчанию, перечитывание.
 * <p>
 * Настройки — <b>единственное статическое изменяемое состояние в моде</b>,
 * и это исключение названо здесь, чтобы не выглядело недосмотром. Они не
 * принадлежат ни миру, ни поселению: файл один на установку игры, и живёт
 * он дольше любого сохранения. Поле объявлено {@code volatile}, потому что
 * перечитывает его командный поток, а читают — серверный и клиентский.
 * <p>
 * Битый файл <b>не</b> роняет мод: в лог уходит причина, а в игру — значения
 * по умолчанию. Иначе одна лишняя запятая в json оставляла бы игрока без
 * мода вообще, и понять почему он бы не смог.
 */
public final class Configs {

    private static final String FILE = VillagePax.MOD_ID + ".json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Config current = Config.DEFAULT;

    private Configs() {
    }

    public static Config get() {
        return current;
    }

    /** Путь к файлу настроек: {@code config/villagepax.json}. */
    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE);
    }

    /**
     * Прочитать файл, а если его нет — создать со значениями по умолчанию.
     * <p>
     * Файл создаётся намеренно: настройка, о которой негде узнать, всё равно
     * что отсутствует. Увидев готовый json со всеми полями, игрок правит его,
     * а не ищет список параметров в описании мода.
     * <p>
     * По той же причине файл <b>дописывается</b>, когда в новой версии мода
     * появилась настройка, которой в нём нет: иначе она была бы видна только
     * тем, кто создал файл после обновления. Заданные игроком значения при
     * этом не трогаются — дописывается только недостающее, и только когда
     * файл прочитан целиком.
     */
    public static Config load() {
        Path path = path();

        if (!Files.exists(path)) {
            current = Config.DEFAULT;
            save(current);
            return current;
        }

        try {
            JsonElement json = GSON.fromJson(
                    Files.readString(path, StandardCharsets.UTF_8), JsonElement.class);
            DataResult<Config> parsed = Config.CODEC.parse(JsonOps.INSTANCE, json);

            parsed.error().ifPresent(error -> VillagePax.LOGGER.error(
                    "Настройки не прочитаны, взяты значения по умолчанию: {}", error.message()));
            current = parsed.result().orElse(Config.DEFAULT);
            disagreements(json).forEach(complaint ->
                    VillagePax.LOGGER.warn("Настройка не принята: {}", complaint));
            if (parsed.result().isPresent() && missesFields(json, current)) {
                VillagePax.LOGGER.info("В файл настроек {} дописаны новые поля", path);
                save(current);
            }
        } catch (IOException | RuntimeException broken) {
            VillagePax.LOGGER.error("Файл настроек {} не читается, взяты значения "
                    + "по умолчанию: {}", path, broken.toString());
            current = Config.DEFAULT;
        }
        return current;
    }

    /**
     * Нет ли в прочитанном файле полей, которые знает эта версия мода.
     * <p>
     * Сравнение — с тем, что записал бы кодек: он и есть полный список
     * настроек, второго списка заводить незачем.
     */
    public static boolean missesFields(JsonElement written, Config config) {
        if (written == null || !written.isJsonObject()) {
            return false;
        }
        JsonObject theirs = written.getAsJsonObject();
        return Config.CODEC.encodeStart(JsonOps.INSTANCE, config).result()
                .filter(JsonElement::isJsonObject)
                .map(full -> full.getAsJsonObject().keySet().stream()
                        .anyMatch(key -> !theirs.has(key)))
                .orElse(false);
    }

    /**
     * Настройки, которые игрок написал, а мод не принял.
     * <p>
     * Нужно потому, что {@code orElse} в DFU <b>глотает ошибку вложенного
     * кодека</b> и молча подставляет значение по умолчанию.
     * Написал {@code ticks_per_decision: 0} — получил десять и никакого
     * объяснения. Это то самое молчание, которое заставляет игрока думать,
     * что настройка не работает вовсе.
     * <p>
     * Проверяется по {@link Config#RANGES} — той же таблице допустимых
     * значений, из которой собран кодек. Перегонкой «закодировать назад
     * и сравнить» вышло бы хуже: она заметила бы, что значение другое,
     * но не сказала бы почему, — а «почему» и есть то, что игрок ищет.
     */
    public static List<String> disagreements(JsonElement written) {
        if (written == null || !written.isJsonObject()) {
            return List.of();
        }

        List<String> complaints = new ArrayList<>();
        JsonObject theirs = written.getAsJsonObject();

        for (String key : theirs.keySet()) {
            Codec<?> allowed = Config.RANGES.get(key);
            if (allowed == null) {
                // Незнакомое поле — не ошибка: чужая версия или датапак.
                continue;
            }
            allowed.parse(JsonOps.INSTANCE, theirs.get(key)).error().ifPresent(error ->
                    complaints.add(key + ": " + theirs.get(key) + " не годится ("
                            + error.message() + "), взято значение по умолчанию"));
        }
        return complaints;
    }

    /**
     * Принять настройки и записать их в файл — так, как их поставил экран.
     * <p>
     * Сразу в дело: читают настройки через {@link #get()} на каждом шаге,
     * и встроенный сервер одиночной игры живёт в том же процессе, что и
     * экран. Перезаходить в мир ради новой настройки не нужно.
     */
    public static void apply(Config config) {
        current = config;
        save(config);
    }

    private static void save(Config config) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Config.CODEC.encodeStart(JsonOps.INSTANCE, config)
                    .resultOrPartial(error -> VillagePax.LOGGER.error(
                            "Настройки не записаны: {}", error))
                    .ifPresent(json -> {
                        try {
                            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
                        } catch (IOException failed) {
                            VillagePax.LOGGER.error("Файл настроек {} не записан: {}",
                                    path, failed.toString());
                        }
                    });
        } catch (IOException failed) {
            VillagePax.LOGGER.error("Папку настроек не создать: {}", failed.toString());
        }
    }

    /** Только для тестов: подменить настройки, не касаясь файла. */
    public static void override(Config config) {
        current = config;
    }
}
