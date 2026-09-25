package com.villagepax.sim;

import com.villagepax.core.building.BuildingType;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Что деревне народа строить дальше — по её нуждам, а не по списку.
 * <p>
 * Заказчик: «чтоб прям хотелось жить в них, а то жители могут глупить
 * и плохо построить». Прежде деревня каждое утро брала <b>первое</b>
 * здание из списка народа, которое помещалось, — а первой в списке шла
 * лавка без уровня и без предела. Деревня ставила лавку за лавкой, пока
 * хватало места, и дом новорождённому не ставила никогда.
 * <p>
 * Теперь порядок — как у живой деревни:
 * <ol>
 *   <li>негде спать — строится дом: без кровати житель не придёт, а свой
 *       уйдёт;</li>
 *   <li>мало полей — поле, по одному на каждые {@value #PEOPLE_PER_FARM}
 *       жителей: голодная деревня не растёт;</li>
 *   <li>нет мастерской, лавки, храма, склада — по одному каждого, когда
 *       уровень деревни их открывает, в том порядке, в каком их называет
 *       народ;</li>
 *   <li>всё есть — ещё один дом, пока деревня не упёрлась в свой предел.</li>
 * </ol>
 * Правило чистое — ни мира, ни датапака, — и проверяется без игры.
 */
public final class VillagePlanner {

    /** Кроватей про запас: новорождённому и путнику должно быть где лечь. */
    public static final int SPARE_BEDS = 2;

    /** На столько жителей хватает одного поля. */
    public static final int PEOPLE_PER_FARM = 5;

    private VillagePlanner() {
    }

    /**
     * Чего деревня хочет, по убыванию нужды. Первое, что встанет на землю,
     * и будет строиться; остальное — если для первого не нашлось места.
     *
     * @param village   деревня
     * @param available здания народа в его порядке
     * @param beds      сколько кроватей в деревне сейчас
     * @param types     что известно о каждом типе здания
     * @param farming   работает ли в этом типе пахарь
     */
    public static List<Identifier> wishes(Settlement village, List<Identifier> available, int beds,
                                          Function<Identifier, Optional<BuildingType>> types,
                                          Function<Identifier, Boolean> farming) {
        Map<Identifier, Long> built = village.buildings().stream()
                .collect(Collectors.groupingBy(Building::type, Collectors.counting()));
        int people = village.population();
        boolean room = people < village.maxCitizens();

        List<Identifier> homes = new ArrayList<>();
        List<Identifier> farms = new ArrayList<>();
        List<Identifier> others = new ArrayList<>();
        for (Identifier type : available) {
            BuildingType kind = types.apply(type).orElse(null);
            if (kind == null || kind.isTownHall()
                    || village.level().ordinal() < kind.minLevel().ordinal()) {
                continue;
            }
            if (kind.role() == BuildingType.Role.HOME) {
                homes.add(type);
            } else if (farming.apply(type)) {
                farms.add(type);
            } else {
                others.add(type);
            }
        }

        Set<Identifier> wishes = new LinkedHashSet<>();
        if (room && beds < people + SPARE_BEDS) {
            wishes.addAll(homes);
        }
        long fields = farms.stream().mapToLong(type -> built.getOrDefault(type, 0L)).sum();
        if (fields * PEOPLE_PER_FARM < Math.max(1, people)) {
            wishes.addAll(farms);
        }
        for (Identifier type : others) {
            if (built.getOrDefault(type, 0L) == 0) {
                wishes.add(type);
            }
        }
        if (room) {
            wishes.addAll(homes);
        }
        return List.copyOf(wishes);
    }
}
