package com.villagepax.core.faith;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.item.Item;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Загружает пантеоны из датапаков при старте сервера и по {@code /reload}.
 * <p>
 * Файлы лежат в {@code data/<пространство имён>/villagepax/gods/<имя>.json} —
 * вложенная папка мода по той же причине, что у культур, профессий, схем,
 * типов зданий и столов торга: не отбирать у других модов общее имя.
 * <p>
 * Ключ — <b>опознаватель файла</b>, а не культура: богов у народа несколько,
 * и культура ключом быть не может. Зато она поле, и пантеон народа —
 * выборка по нему.
 */
public class Gods extends JsonDataLoader implements IdentifiableResourceReloadListener {

    public static final Identifier ID = new Identifier(VillagePax.MOD_ID, "gods");

    private static final String FOLDER = VillagePax.MOD_ID + "/gods";
    private static final Gson GSON = new Gson();

    private static volatile Map<Identifier, God> gods = Map.of();

    public Gods() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager,
                         Profiler profiler) {
        Map<Identifier, God> loaded = new LinkedHashMap<>();

        prepared.entrySet().stream()
                // Порядок загрузки датапака не определён, а пантеон игрок
                // видит списком. Сортировка по имени файла делает список
                // одинаковым от запуска к запуску — иначе боги в пульте
                // перетасовывались бы при каждом /reload.
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Identifier::toString)))
                .forEach(entry -> God.CODEC
                        .parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(error ->
                                VillagePax.LOGGER.error("Бог {} не загружен: {}",
                                        entry.getKey(), error))
                        .ifPresent(god -> loaded.put(entry.getKey(), god)));

        gods = Collections.unmodifiableMap(loaded);
        VillagePax.LOGGER.info("Загружено богов: {}", loaded.size());

        audit(loaded);
    }

    /**
     * Жалуется в лог на пантеон, в котором двое принимают одну жертву.
     * <p>
     * Правило игры — «что кладёшь на алтарь, тому и молишься»: бога
     * выбирает вещь, а не кнопка. Два бога, принимающие пшеницу, делают
     * это правило неопределённым, и мод был бы вынужден выбирать за
     * игрока. Проверка живёт здесь, при загрузке, потому что увидеть это
     * в игре можно только по тому, что благосклонность растёт не у того.
     * <p>
     * Жалоба, а не отказ: датапак чужого мода не должен ронять загрузку.
     * Разбирается же двусмысленность детерминированно — берётся первый
     * по опознавателю, и об этом сказано вслух.
     */
    private static void audit(Map<Identifier, God> loaded) {
        Map<Identifier, Map<Identifier, Identifier>> perCulture = new LinkedHashMap<>();

        loaded.forEach((id, god) -> {
            Map<Identifier, Identifier> taken =
                    perCulture.computeIfAbsent(god.culture(), key -> new LinkedHashMap<>());
            god.offerings().keySet().forEach(item -> {
                Identifier already = taken.put(item, id);
                if (already != null) {
                    VillagePax.LOGGER.warn("У народа {} жертву {} принимают двое — {} и {}; "
                                    + "взят будет {}", god.culture(), item, already, id,
                            already.compareTo(id) <= 0 ? already : id);
                }
            });
        });
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    public static Map<Identifier, God> all() {
        return gods;
    }

    public static Optional<God> get(Identifier id) {
        return Optional.ofNullable(gods.get(id));
    }

    /**
     * Пантеон народа: опознаватели богов в порядке показа.
     * <p>
     * Пусто — народ никому не молится. Законное умолчание, а не ошибка:
     * храма у такого народа просто не будет.
     */
    public static List<Identifier> of(Identifier culture) {
        List<Identifier> pantheon = new ArrayList<>();
        gods.forEach((id, god) -> {
            if (god.culture().equals(culture)) {
                pantheon.add(id);
            }
        });
        return pantheon;
    }

    /**
     * Кому в этом народе предназначена эта вещь.
     * <p>
     * Сердце правила «что кладёшь, тому и молишься». Двусмысленность
     * разрешается первым по опознавателю — тем же выбором, о котором
     * загрузчик уже пожаловался в лог. Молчаливого выбора «того, у кого
     * меньше очков» здесь нет намеренно: он менялся бы от игры к игре,
     * и одна и та же пшеница уходила бы то одному, то другому.
     */
    public static Optional<Identifier> whoTakes(Identifier culture, Item item) {
        return of(culture).stream()
                .filter(id -> gods.get(id).accepts(item))
                .min(Comparator.comparing(Identifier::toString));
    }

    /** Бог этого народа в этом домене, если он есть. */
    public static Optional<Identifier> inDomain(Identifier culture, Domain domain) {
        return of(culture).stream()
                .filter(id -> gods.get(id).domain() == domain)
                .min(Comparator.comparing(Identifier::toString));
    }
}
