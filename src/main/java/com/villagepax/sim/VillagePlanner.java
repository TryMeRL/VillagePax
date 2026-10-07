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
 *   <li>всё есть — ещё один дом, пока кроватей не станет на весь предел
 *       деревни с запасом;</li>
 *   <li>деревня тесна — жителей под предел, — а живущим есть где спать
 *       и что есть: ратуша поднимается на ступень, {@link #readyToGrow}.
 *       С ней растут предел, граница и список зданий: хутор становится
 *       деревней, деревня — городом.</li>
 * </ol>
 * Домов у народа может быть несколько видов; строится тот, которого
 * меньше, — улица из одинаковых коробок не улица, — а в городе первыми
 * идут городские дома.
 * <p>
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
        Map<Identifier, Integer> homeLevel = new java.util.HashMap<>();
        for (Identifier type : available) {
            BuildingType kind = types.apply(type).orElse(null);
            if (kind == null || kind.isTownHall()
                    || village.level().ordinal() < kind.minLevel().ordinal()) {
                continue;
            }
            if (kind.role() == BuildingType.Role.HOME) {
                homes.add(type);
                homeLevel.put(type, kind.minLevel().ordinal());
            } else if (farming.apply(type)) {
                farms.add(type);
            } else {
                others.add(type);
            }
        }
        // Дома — по разнообразию: городские первыми, среди равных — того
        // вида, которого меньше. Порядок народа решает только при равенстве:
        // сортировка устойчива.
        homes.sort(java.util.Comparator
                .comparingInt((Identifier type) -> -homeLevel.get(type))
                .thenComparingLong(type -> built.getOrDefault(type, 0L)));

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
        // Про запас — пока кроватей не на весь предел. Прежде дом ставился
        // всегда, пока жителей меньше предела, — и деревня без еды, к которой
        // никто не шёл, ставила дом за домом, пока не кончалась земля.
        if (room && beds < village.maxCitizens() + SPARE_BEDS) {
            wishes.addAll(homes);
        }
        return List.copyOf(wishes);
    }

    /**
     * Пора ли ратуше на ступень выше.
     * <p>
     * Тогда, когда деревне тесно, а не когда прошло столько-то дней:
     * жителей — сколько позволяет ступень, спать есть где всем, полей
     * хватает на всех. Основатели предела не набирают: только что
     * вставшая деревня сперва принимает пришлых и родит детей. Всё прочее — мастерские, храм, лавка — сюда не входит
     * нарочно: здание, которому на этом рельефе нет места, держало бы
     * деревню хутором вечно. Поэтому зовут это правило <b>после</b>
     * {@link #wishes}: всё, что можно поставить, деревня ставит раньше.
     *
     * @param fields сколько полей стоит в деревне
     */
    public static boolean readyToGrow(Settlement village, int beds, long fields) {
        int people = village.population();
        return !village.level().isMax()
                && people >= village.maxCitizens()
                && beds >= people
                && fields * PEOPLE_PER_FARM >= Math.max(1, people);
    }
}
