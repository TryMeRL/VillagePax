package com.villagepax.core.building;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Загружает типы зданий из датапаков при старте сервера и по {@code /reload}.
 * <p>
 * Файлы лежат в {@code data/<пространство имён>/villagepax/buildings/<имя>.json} —
 * вложенная папка мода по той же причине, что у культур, профессий и схем:
 * не отбирать у других модов общее имя и не пытаться разбирать их файлы
 * своим кодеком.
 * <p>
 * Опознаватель типа <b>совпадает с типом здания</b>, каким его хранит
 * поселение: файл {@code norman/house.json} описывает тип
 * {@code villagepax:norman/house}. Схема для уровня по-прежнему выводится
 * припиской {@code _lvlN} — это соглашение осталось, и осталось намеренно:
 * оно не наделяет здание правами, а лишь называет файл, и лишнее поле
 * в каждом типе здания было бы обрядом без смысла.
 */
public class BuildingTypes extends JsonDataLoader implements IdentifiableResourceReloadListener {

    public static final Identifier ID = new Identifier(VillagePax.MOD_ID, "buildings");

    private static final String FOLDER = VillagePax.MOD_ID + "/buildings";
    private static final Gson GSON = new Gson();

    private static volatile Map<Identifier, BuildingType> types = Map.of();

    public BuildingTypes() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager,
                         Profiler profiler) {
        Map<Identifier, BuildingType> loaded = new HashMap<>();

        prepared.forEach((id, json) -> BuildingType.CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error ->
                        VillagePax.LOGGER.error("Тип здания {} не загружен: {}", id, error))
                .ifPresent(type -> loaded.put(id, type)));

        types = Collections.unmodifiableMap(loaded);

        if (loaded.isEmpty()) {
            // Не ошибка: датапак вправе не описывать типов вовсе. Но тогда
            // ни одно здание не станет ни ратушей, ни жильём, ни мастерской,
            // и колония не вырастет — об этом надо сказать громко.
            VillagePax.LOGGER.warn("Не загружено ни одного типа здания — "
                    + "ни ратуши, ни жилья, ни мастерских у колоний не будет");
        } else {
            VillagePax.LOGGER.info("Загружено типов зданий: {}", loaded.size());
        }
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    public static Map<Identifier, BuildingType> all() {
        return types;
    }

    public static Optional<BuildingType> get(Identifier type) {
        return Optional.ofNullable(types.get(type));
    }

    /**
     * Ратуша ли это.
     * <p>
     * Умолчание — <b>нет</b>. Молчание датапака не должно наделять здание
     * правами: неописанное здание строится и чинится, но уровня колонии
     * не даёт.
     */
    public static boolean isTownHall(Identifier type) {
        return get(type).filter(BuildingType::isTownHall).isPresent();
    }

    /** Жильё ли это: считать ли кровати в нём местами для жителей. */
    public static boolean isHome(Identifier type) {
        return get(type).filter(known -> known.role() == BuildingType.Role.HOME).isPresent();
    }

    /** Работает ли в этом здании названная профессия. */
    public static boolean employs(Identifier type, Identifier profession) {
        return get(type).filter(known -> known.employs(profession)).isPresent();
    }

    /**
     * Ключ названия здания.
     * <p>
     * Из данных, а если типа нет — по прежнему соглашению об именовании:
     * пусть в интерфейсе будет хоть какое-то имя, а не пустая строка.
     */
    public static String displayName(Identifier type) {
        return get(type).map(BuildingType::displayName)
                .orElseGet(() -> "villagepax.building." + type.getPath().replace('/', '.'));
    }

    /** Что деревня народа ставит сразу при появлении. */
    public static List<Identifier> starting(List<Identifier> available) {
        List<Identifier> starting = new ArrayList<>();
        for (Identifier type : available) {
            if (get(type).filter(BuildingType::starting).isPresent()) {
                starting.add(type);
            }
        }
        return starting;
    }

    /** Тип, в котором работает эта профессия, из перечисленных культурой. */
    public static Optional<Identifier> workplaceOf(List<Identifier> available,
                                                   Identifier profession) {
        for (Identifier type : available) {
            if (employs(type, profession)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    public static Set<Identifier> ids() {
        return types.keySet();
    }
}
