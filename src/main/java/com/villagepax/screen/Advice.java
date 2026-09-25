package com.villagepax.screen;

import com.villagepax.core.ModTags;
import com.villagepax.core.building.BuildingTypes;
import java.util.UUID;
import java.util.List;
import com.villagepax.sim.faith.Faith;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.Standing;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Levels;
import com.villagepax.core.faith.Gods;
import com.villagepax.core.building.BuildingType;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.trade.Wages;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.work.Schedule;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Что делать прямо сейчас — одной строкой в пульте.
 * <p>
 * Написано по главной жалобе на моды этого жанра, и своей игрок подтвердил
 * её слово в слово: непонятен не механизм, а <b>следующий шаг</b>. Пульт
 * показывает десяток чисел — жителей, еду, кровати, стройку, — и все они
 * правдивы, но ни одно не говорит, что делать. Игрок стоит над цифрами
 * и не знает, чего колонии не хватает: дома? поля? рук?
 * <p>
 * <b>Один совет за раз, и всегда самый срочный.</b> Список из пяти
 * «неплохо бы» — это тот же десяток чисел, только словами. Лестница
 * причин выстроена по тому, что <b>убивает колонию быстрее</b>: пустая
 * колония → голод → некому строить → негде спать → нечего есть завтра →
 * стройка без материалов → нечего строить. А выше всего — отряд
 * у ворот: остальные беды успеют подождать до завтра, эта — нет.
 * <p>
 * <b>Совет не замолкает и тогда, когда всё хорошо.</b> Прежде лестница
 * кончалась на «нечего строить», и колония, у которой всё построено,
 * получала пустоту — ровно в тот миг, когда у мода начинается долгая
 * игра. А в ней есть куда идти: раздать ремёсла, познакомиться
 * с соседями, поставить храм, начать носить жертвы, поднять ратушу.
 * Ни об одном из этих шагов пульт не говорил, и игрок не знал, что они
 * есть. Награда, о которой не сказали, наградой не ощущается; дорога,
 * о которой не сказали, не ощущается дорогой.
 * <p>
 * Совет — это ключ перевода, а не готовая строка: считает его сервер,
 * потому что здесь склад и здания, а показывает клиент на своём языке.
 */
public final class Advice {

    /** Ниже этого запаса еды колония живёт одним днём и заслуживает совета. */
    private static final int THIN_FOOD_DAYS = 2;

    // Ключи советов — константами, а не строками по месту. Так их можно
    // перечислить, а перечислив — проверить, что у каждого есть слова
    // на обоих языках. Совет без перевода выглядит в пульте как
    // villagepax.advice.no_temple, и это хуже молчания.
    private static final String SIEGE = "villagepax.advice.under_siege";
    private static final String UNDER_YOKE = "villagepax.advice.under_yoke";
    private static final String EMPTY_PURSE = "villagepax.advice.empty_purse";
    private static final String COIN_LEAKS = "villagepax.advice.coin_leaks";
    private static final String DESERTED = "villagepax.advice.deserted";
    private static final String NO_STORAGE = "villagepax.advice.no_storage";
    private static final String NO_FOOD = "villagepax.advice.no_food";
    private static final String THIN_FOOD = "villagepax.advice.thin_food";
    private static final String NO_BUILDER = "villagepax.advice.no_builder";
    private static final String NO_BEDS = "villagepax.advice.no_beds";
    private static final String NO_FARM = "villagepax.advice.no_farm";
    private static final String IDLE_HANDS = "villagepax.advice.idle_hands";
    private static final String NO_NEIGHBOURS = "villagepax.advice.no_neighbours";
    private static final String NO_TEMPLE = "villagepax.advice.no_temple";
    private static final String NO_FAITH = "villagepax.advice.no_faith";
    private static final String NOTHING_BUILDING = "villagepax.advice.nothing_building";
    private static final String RAISE_THE_HALL = "villagepax.advice.raise_the_hall";

    /** Все советы, какие мод умеет дать: их проверяет словарь. */
    public static List<String> keys() {
        return List.of(SIEGE, UNDER_YOKE, DESERTED, NO_STORAGE, NO_FOOD, THIN_FOOD, NO_BUILDER,
                NO_BEDS, NO_FARM, EMPTY_PURSE, IDLE_HANDS, COIN_LEAKS, NO_NEIGHBOURS,
                NO_TEMPLE, NO_FAITH, NOTHING_BUILDING, RAISE_THE_HALL);
    }

    private Advice() {
    }

    /**
     * Самое срочное дело колонии — или пусто, если всё идёт своим ходом.
     * <p>
     * Порядок проверок здесь и есть совет: первая сработавшая и побеждает.
     */
    public static Optional<String> nextStep(ServerWorld world, Settlement colony) {
        if (colony.siege().isPresent()) {
            // Выше пустой колонии: отряд у ворот сделает её такой сегодня,
            // а голод — через неделю. И это единственная беда, у которой
            // есть срок: заплатить можно, пока они идут.
            return Optional.of(SIEGE);
        }
        if (colony.tributeDaysLeft(Schedule.dayOf(world.getTimeOfDay())) > 0
                && !colony.owner().isAutonomous()) {
            // Ниже отряда у ворот, но выше всего остального: дань уносит
            // серебро каждое утро, и игрок должен знать не только о том,
            // что оно уходит, но и как это прекратить.
            return Optional.of(UNDER_YOKE);
        }
        if (colony.citizens().isEmpty()) {
            return Optional.of(DESERTED);
        }

        Warehouse warehouse = Warehouse.of(world, colony);
        if (warehouse.containerCount() == 0) {
            return Optional.of(NO_STORAGE);
        }
        if (!warehouse.hasAny(ModTags.CITIZEN_FOOD)) {
            return Optional.of(NO_FOOD);
        }
        // Еда есть, но на день-два: сказать сейчас, пока не поздно. Молчание
        // до последней моркови — и совет «еды нет» приходил тогда, когда
        // жители уже голодали.
        if (TownHallView.daysOfFood(warehouse.tally(), colony.population()) < THIN_FOOD_DAYS) {
            return Optional.of(THIN_FOOD);
        }
        if (colony.citizens().stream().noneMatch(Advice::isBuilder)) {
            return Optional.of(NO_BUILDER);
        }
        if (Housing.freeSpots(world, colony) <= 0 && colony.hasRoomForCitizen()) {
            return Optional.of(NO_BEDS);
        }
        if (!has(colony, FarmJob.FARMER) && standing(colony, "farm").isEmpty()) {
            return Optional.of(NO_FARM);
        }
        if (Wages.billOf(colony) > 0
                && Wages.treasuryOf(world, colony) < Wages.billOf(colony)) {
            // Ниже всего съестного, и это взвешено: без еды колония теряет
            // человека насовсем, без жалования — только темп. Потеря
            // человека дороже, и голод потому говорит первым.
            return Optional.of(EMPTY_PURSE);
        }

        // --- дальше начинается долгая игра ---
        //
        // Всё, что ниже, колонию не убивает. Но игрок, у которого построено
        // всё нужное, до сих пор получал пустую строку — и решал, что мод
        // кончился. На деле у мода тут только начинается вторая половина.

        if (idleHands(colony) > 0 && freeWorkplaces(colony) > 0) {
            // Человек без дела при пустой мастерской — единственная потеря
            // в этом списке, которую видно числом: колония кормит того,
            // кто ничего не приносит.
            return Optional.of(IDLE_HANDS);
        }
        if (Wages.billOf(colony) > 0 && !Wages.hasMarket(colony)) {
            // Без рынка жалование утекает целиком, и ставка налога
            // не возвращает ни медяка. Молчать об этом нельзя: игрок
            // крутил бы ставку впустую и решил, что налог не работает.
            return Optional.of(COIN_LEAKS);
        }
        if (!knowsAnybody(world, colony)) {
            // Соседи — это ворота ко всему: просьбы, торг, чертежи, союзы.
            // Игрок, не встретивший ни одной деревни, не видел половины мода
            // и не знает об этом.
            return Optional.of(NO_NEIGHBOURS);
        }
        if (canOrderATemple(colony) && !Faith.hasTemple(colony)) {
            return Optional.of(NO_TEMPLE);
        }
        if (Faith.hasTemple(colony) && !anyGodNoticed(colony)) {
            // Храм стоит, а на алтарь не клали: самое частое место, где
            // вера остаётся зданием.
            return Optional.of(NO_FAITH);
        }
        if (colony.buildings().stream().noneMatch(BuildJob::isUnderConstruction)) {
            return Optional.of(NOTHING_BUILDING);
        }
        if (canRaiseTheHall(colony)) {
            // Последним, потому что верно почти всегда: это не беда,
            // а направление. Зато лучше пустоты — за ступенью открывается
            // то, чего у колонии ещё не было.
            return Optional.of(RAISE_THE_HALL);
        }
        return Optional.empty();
    }

    /** Сколько жителей сидит без ремесла. */
    private static int idleHands(Settlement colony) {
        return (int) colony.citizens().stream()
                .filter(citizen -> citizen.profession().isEmpty())
                .count();
    }

    /**
     * Сколько готовых мастерских стоит без работника.
     * <p>
     * Считаются только <b>достроенные</b>: размеченный фундамент работы
     * не даёт, и звать к нему людей было бы обманом.
     */
    private static int freeWorkplaces(Settlement colony) {
        int free = 0;
        for (Building building : colony.buildings()) {
            if (!building.isOperational()) {
                continue;
            }
            Identifier craft = BuildingTypes.get(building.type())
                    .flatMap(BuildingType::profession).orElse(null);
            if (craft != null && !has(colony, craft)) {
                free++;
            }
        }
        return free;
    }

    /**
     * Знает ли колония хоть одну чужую деревню.
     * <p>
     * По <b>доверию</b>, а не по расстоянию: деревня за соседним холмом,
     * с которой игрок не говорил, ему ничего не даёт. Знакомство —
     * это разговор, и начинается оно с первого квеста.
     */
    private static boolean knowsAnybody(ServerWorld world, Settlement colony) {
        UUID owner = colony.owner().player().orElse(null);
        if (owner == null) {
            return true;
        }
        return SettlementManager.get(world).all().stream()
                .anyMatch(other -> other.owner().isAutonomous()
                        && other.reputationOf(owner) >= Standing.KNOWN.from());
    }

    /** Может ли колония уже заказать храм своего народа. */
    private static boolean canOrderATemple(Settlement colony) {
        return Faith.templeType(colony.culture())
                .flatMap(BuildingTypes::get)
                .filter(type -> type.openTo(colony.level()))
                .isPresent();
    }

    /** Заметил ли колонию хоть один бог. */
    private static boolean anyGodNoticed(Settlement colony) {
        return Gods.of(colony.culture()).stream()
                .anyMatch(god -> Faith.tierOf(colony, god).reached(Faith.Tier.NOTICED));
    }

    /**
     * Есть ли куда расти ратуше.
     * <p>
     * Спрашивается <b>схема</b>, а не желание: обещать ступень, до которой
     * в моде нет ратуши, — это та самая ложь, за которую карточка роста
     * уже однажды поплатилась.
     */
    private static boolean canRaiseTheHall(Settlement colony) {
        for (Building building : colony.buildings()) {
            if (building.isOperational() && Levels.isTownHallType(building.type())
                    && SchematicLoader.get(new Identifier(building.type().getNamespace(),
                            building.type().getPath() + "_lvl" + (building.level() + 1)))
                    .isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBuilder(Citizen citizen) {
        return citizen.profession().filter(BuildJob.BUILDER::equals).isPresent();
    }

    private static boolean has(Settlement colony, Identifier profession) {
        return colony.citizens().stream()
                .anyMatch(citizen -> citizen.profession().filter(profession::equals).isPresent());
    }

    /**
     * Стоит ли в колонии готовое здание такой роли.
     * <p>
     * По окончанию имени типа, а не по списку: народ вправе назвать своё
     * поле как угодно в своём пространстве имён, но {@code .../farm} у всех
     * значит поле — на этом же соглашении стоит и выбор мастерских.
     */
    private static Optional<Building> standing(Settlement colony, String role) {
        for (Building building : colony.buildings()) {
            if (building.type().getPath().endsWith("/" + role)
                    && !BuildJob.isUnderConstruction(building)) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

    /** Есть ли у культуры такое здание вообще — на случай народа без полей. */
    public static boolean knows(Settlement colony, String role) {
        return BuildingTypes.all().keySet().stream()
                .anyMatch(type -> type.getPath().endsWith("/" + role));
    }
}
