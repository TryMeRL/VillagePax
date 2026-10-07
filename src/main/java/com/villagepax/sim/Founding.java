package com.villagepax.sim;

import com.villagepax.core.culture.Culture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Правила основания колонии.
 * <p>
 * Намеренно отделены от предмета-чертежа и ничего не знают о мире: сюда входят
 * только те проверки, которые можно прогнать без запущенной игры, и потому они
 * покрыты обычными тестами. Проверка грунта требует мира и потому осталась
 * в предмете, её проверяет игровой тест.
 */
public final class Founding {

    public static final String KEY_UNKNOWN_CULTURE = "villagepax.found.unknown_culture";
    public static final String KEY_ALREADY_OWNER = "villagepax.found.already_owner";
    public static final String KEY_TOO_CLOSE = "villagepax.found.too_close";

    /** Запасное имя, если у культуры не заполнен список названий поселений. */
    public static final String FALLBACK_NAME = "Безымянное";

    /** Запасное имя строителя, если у культуры не заполнены списки имён. */
    public static final String FALLBACK_BUILDER_NAME = "Строитель";

    /** Профессия первого жителя. Данными станет в задаче 1.9. */
    public static final Identifier PROFESSION_BUILDER =
            new Identifier(com.villagepax.VillagePax.MOD_ID, "builder");

    private Founding() {
    }

    public static FoundingOutcome attempt(SettlementManager manager,
                                          UUID player,
                                          Identifier cultureId,
                                          Culture culture,
                                          BlockPos center,
                                          Random random) {
        if (culture == null) {
            return FoundingOutcome.Refused.of(KEY_UNKNOWN_CULTURE, cultureId.toString());
        }

        Optional<Settlement> existing = colonyOf(manager, player);
        if (existing.isPresent()) {
            return FoundingOutcome.Refused.of(KEY_ALREADY_OWNER, existing.get().name());
        }

        Settlement candidate = Settlement.found(cultureId, Owner.of(player),
                pickName(culture, random, namesOnTheMap(manager)), center);

        Optional<Settlement> conflict = manager.conflictWith(candidate);
        if (conflict.isPresent()) {
            return FoundingOutcome.Refused.of(KEY_TOO_CLOSE, conflict.get().name());
        }

        return new FoundingOutcome.Founded(candidate);
    }

    /**
     * Одна колония на игрока.
     * <p>
     * Настройки в моде теперь есть, но этого ограничения в них <b>нет
     * намеренно</b>: заказчик оставил одну колонию, разобрав мод на предмет
     * лишнего. Вторая колония означала бы, что игрок делит внимание между
     * двумя стройками, а смотреть, как работает одна, — и есть занятие.
     * Понадобится снять — снимается здесь, в одном месте.
     */
    public static Optional<Settlement> colonyOf(SettlementManager manager, UUID player) {
        return manager.all().stream()
                .filter(settlement -> settlement.owner().isOwnedBy(player))
                .findFirst();
    }

    public static String pickName(Culture culture, Random random) {
        return pickName(culture, random, List.of());
    }

    /**
     * Название поселения, которого на карте ещё нет.
     * <p>
     * Две «Кан» на карте путают и компас, и чат: «обоз из Кан» — из какой?
     * Повтор берётся, только когда народ исчерпал свой список.
     *
     * @param onTheMap названия, которые уже носят поселения мира
     */
    public static String pickName(Culture culture, Random random, Collection<String> onTheMap) {
        List<String> pool = culture.namePools().settlement();
        if (pool.isEmpty()) {
            return FALLBACK_NAME;
        }
        return rarest(pool, onTheMap, random);
    }

    /**
     * Кто как звался и как зовётся теперь.
     *
     * @param citizen кого переименовали
     * @param was     прежнее полное имя
     * @param now     новое полное имя
     */
    public record Renamed(UUID citizen, String was, String now) {
    }

    /**
     * Развести двойников: у второго и следующего с тем же полным именем —
     * свободное имя своего пола.
     * <p>
     * Для миров, начатых при именнике в семь имён: там двойники уже живут,
     * и новое правило выбора их не коснулось бы никогда. Первый по списку
     * остаётся как был — его игрок знает дольше всех; отчество не трогается,
     * оно память о родителе. Именник исчерпан — двойник остаётся: имя
     * из воздуха хуже повтора.
     */
    public static List<Renamed> tellTwinsApart(Settlement settlement, Culture culture, Random random) {
        List<Renamed> renamed = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Citizen citizen : settlement.citizens()) {
            if (seen.add(citizen.fullName())) {
                continue;
            }
            List<String> pool = citizen.gender() == Gender.MALE
                    ? culture.namePools().male() : culture.namePools().female();
            List<String> taken = settlement.citizens().stream().map(Citizen::firstName).toList();
            if (pool.isEmpty()) {
                continue;
            }
            String name = rarest(pool, taken, random);
            String full = citizen.lastName().isEmpty() ? name : name + " " + citizen.lastName();
            if (seen.contains(full)) {
                continue;
            }
            String was = citizen.fullName();
            citizen.rename(name, citizen.lastName());
            seen.add(full);
            renamed.add(new Renamed(citizen.id(), was, full));
        }
        return renamed;
    }

    /** Названия всех поселений мира: их и обходит новое. */
    public static List<String> namesOnTheMap(SettlementManager manager) {
        return manager.all().stream().map(Settlement::name).toList();
    }

    /**
     * Самое редкое из списка: сперва никем не занятое, потом занятое
     * однажды, и так далее. Среди равных — наугад, и одним броском: число
     * бросков не зависит от занятых, и тот же сид даёт то же имя в пустом
     * поселении, что и до этой правки.
     */
    private static String rarest(List<String> pool, Collection<String> taken, Random random) {
        Map<String, Integer> uses = new HashMap<>();
        for (String name : taken) {
            uses.merge(name, 1, Integer::sum);
        }
        int least = Integer.MAX_VALUE;
        for (String name : pool) {
            least = Math.min(least, uses.getOrDefault(name, 0));
        }
        List<String> rarest = new ArrayList<>();
        for (String name : pool) {
            if (uses.getOrDefault(name, 0) == least && !rarest.contains(name)) {
                rarest.add(name);
            }
        }
        return rarest.get(random.nextInt(rarest.size()));
    }

    /**
     * Первый житель колонии — строитель.
     * <p>
     * Решение заказчика, и оно же условие работоспособности: без строителя
     * не встанет ни одно здание, а первое здание нельзя построить, не имея
     * жителя. Остальные жители приходят под жильё в задаче 1.8.
     */
    public static Citizen firstBuilder(Identifier cultureId, Culture culture, Random random) {
        return firstBuilder(cultureId, culture, random, List.of());
    }

    /** То же, но среди уже записанных соседей: имя не повторит их имён. */
    public static Citizen firstBuilder(Identifier cultureId, Culture culture, Random random,
                                       Collection<Citizen> neighbours) {
        Citizen builder = newCitizen(cultureId, culture, random, neighbours);
        builder.setProfession(PROFESSION_BUILDER);
        return builder;
    }

    /** Житель с именем из списков народа и без профессии. */
    public static Citizen newCitizen(Identifier cultureId, Culture culture, Random random) {
        return newCitizen(cultureId, culture, random, List.of());
    }

    /**
     * Житель для этого поселения: с именем, которого среди соседей нет,
     * и того пола, которого среди них меньше.
     * <p>
     * Имя — жалоба заказчика: «пятнадцать одинаковых имён скучно видеть».
     * Именник у народа на полсотни имён, и повтор берётся, только когда
     * он исчерпан, — и тогда самое редкое имя, а не первое попавшееся.
     * <p>
     * Пол — ради семей: свадьба бывает только между мужчиной и женщиной,
     * и колония, в которую случай привёл шестерых мужчин, не вырастет
     * никогда. При равенстве решает бросок.
     *
     * @param neighbours кто уже живёт там, куда придёт новый
     */
    public static Citizen newCitizen(Identifier cultureId, Culture culture, Random random,
                                     Collection<Citizen> neighbours) {
        // Бросок делается всегда: тот же сид в пустом поселении даёт того же
        // жителя, что и до этой правки, — на это опираются проверки.
        boolean male = random.nextBoolean();
        long men = neighbours.stream().filter(one -> one.gender() == Gender.MALE).count();
        long women = neighbours.size() - men;
        if (men != women) {
            male = men < women;
        }
        List<String> pool = male ? culture.namePools().male() : culture.namePools().female();
        List<String> fallback = male ? culture.namePools().female() : culture.namePools().male();
        List<String> taken = neighbours.stream().map(Citizen::firstName).toList();

        String name = pool.isEmpty() ? null : rarest(pool, taken, random);
        if (name == null && !fallback.isEmpty()) {
            name = rarest(fallback, taken, random);
        }
        if (name == null) {
            // Недописанный датапак не должен лишать игрока строителя.
            name = FALLBACK_BUILDER_NAME;
        }

        return Citizen.newborn(name, "", cultureId, male ? Gender.MALE : Gender.FEMALE);
    }
}
