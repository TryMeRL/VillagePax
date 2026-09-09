package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.ModTags;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.work.Needs;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Снимок колонии для экрана ратуши.
 * <p>
 * Клиент не знает о симуляции: {@code Settlement}, {@code Citizen} и
 * {@code Building} живут на сервере и в клиентский код не попадают. Экран
 * получает эту запись — готовые к показу поля, собранные сервером.
 * <p>
 * Три причины, и все существенные:
 * <ol>
 *   <li><b>Клиент не может пересчитать то, чего не видит.</b> Свободные
 *       кровати, еда на складе и нехватка материалов считаются по блокам
 *       мира, и половина этих блоков в выгруженных чанках.</li>
 *   <li><b>Это запись</b>, значит {@code equals} бесплатен: пересобрал,
 *       сравнил с отправленным, отправил только при отличии. Иначе экран
 *       съедал бы сеть на пустом месте.</li>
 *   <li><b>Проверяется без клиента.</b> {@link #of} — обычный серверный
 *       вызов, и игровой тест сверяет снимок с настоящей колонией.</li>
 * </ol>
 * Слова вместо чисел там, где игрок принимает решение: настроение жителя
 * едет перечислением, а не сытостью, — строку подберёт клиент.
 */
public record TownHallView(
        String name,
        Identifier culture,
        String level,
        int population,
        int maxCitizens,
        int beds,
        int freeBeds,
        int meals,
        int daysOfFood,
        int containers,
        Optional<Construction> construction,
        List<BuildingLine> buildings,
        List<CitizenLine> citizens,
        ItemTally stock,
        List<Identifier> offers,
        List<ProfessionLine> professions) {

    /** Здание, которое строится прямо сейчас, и чего ему не хватает. */
    public record Construction(Identifier type, int level, int step, int steps, ItemTally missing) {

        public static final Codec<Construction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(Construction::type),
                Codec.INT.fieldOf("level").forGetter(Construction::level),
                Codec.INT.fieldOf("step").forGetter(Construction::step),
                Codec.INT.fieldOf("steps").forGetter(Construction::steps),
                ItemTally.CODEC.fieldOf("missing").forGetter(Construction::missing)
        ).apply(instance, Construction::new));
    }

    /**
     * Профессия, которую игрок может дать жителю, и ключ её названия.
     * <p>
     * Ключ едет вместе с именем, потому что профессии — данные датапака:
     * на клиенте, подключённом к выделенному серверу, их файлов нет вовсе,
     * и вывести название по соглашению значило бы завести второе описание
     * там, где уже есть первое.
     */
    public record ProfessionLine(Identifier id, String displayName) {

        public static final Codec<ProfessionLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("id").forGetter(ProfessionLine::id),
                Codec.STRING.fieldOf("display_name").forGetter(ProfessionLine::displayName)
        ).apply(instance, ProfessionLine::new));
    }

    public record BuildingLine(UUID id, Identifier type, int level, BuildProgress progress,
                               BlockPos anchor, boolean canUpgrade) {

        public static final Codec<BuildingLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Uuids.CODEC.fieldOf("id").forGetter(BuildingLine::id),
                Identifier.CODEC.fieldOf("type").forGetter(BuildingLine::type),
                Codec.INT.fieldOf("level").forGetter(BuildingLine::level),
                BuildProgress.CODEC.fieldOf("progress").forGetter(BuildingLine::progress),
                BlockPos.CODEC.fieldOf("anchor").forGetter(BuildingLine::anchor),
                Codec.BOOL.fieldOf("can_upgrade").forGetter(BuildingLine::canUpgrade)
        ).apply(instance, BuildingLine::new));
    }

    /**
     * Житель в списке. {@code leavingSoon} отдельным полем, а не пятым
     * настроением: «уйдёт скоро» — не самочувствие, а предупреждение,
     * и оно бывает при любом настроении.
     */
    public record CitizenLine(UUID id, String name, Optional<Identifier> profession,
                              boolean housed, Optional<Identifier> workplace,
                              Mood mood, boolean leavingSoon) {

        public static final Codec<CitizenLine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Uuids.CODEC.fieldOf("id").forGetter(CitizenLine::id),
                Codec.STRING.fieldOf("name").forGetter(CitizenLine::name),
                Identifier.CODEC.optionalFieldOf("profession").forGetter(CitizenLine::profession),
                Codec.BOOL.fieldOf("housed").forGetter(CitizenLine::housed),
                Identifier.CODEC.optionalFieldOf("workplace").forGetter(CitizenLine::workplace),
                Mood.CODEC.fieldOf("mood").forGetter(CitizenLine::mood),
                Codec.BOOL.fieldOf("leaving_soon").forGetter(CitizenLine::leavingSoon)
        ).apply(instance, CitizenLine::new));
    }

    public static final Codec<TownHallView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(TownHallView::name),
            Identifier.CODEC.fieldOf("culture").forGetter(TownHallView::culture),
            Codec.STRING.fieldOf("level").forGetter(TownHallView::level),
            Codec.INT.fieldOf("population").forGetter(TownHallView::population),
            Codec.INT.fieldOf("max_citizens").forGetter(TownHallView::maxCitizens),
            Codec.INT.fieldOf("beds").forGetter(TownHallView::beds),
            Codec.INT.fieldOf("free_beds").forGetter(TownHallView::freeBeds),
            Codec.INT.fieldOf("meals").forGetter(TownHallView::meals),
            Codec.INT.fieldOf("days_of_food").forGetter(TownHallView::daysOfFood),
            Codec.INT.fieldOf("containers").forGetter(TownHallView::containers),
            Construction.CODEC.optionalFieldOf("construction").forGetter(TownHallView::construction),
            BuildingLine.CODEC.listOf().fieldOf("buildings").forGetter(TownHallView::buildings),
            CitizenLine.CODEC.listOf().fieldOf("citizens").forGetter(TownHallView::citizens),
            ItemTally.CODEC.fieldOf("stock").forGetter(TownHallView::stock),
            Identifier.CODEC.listOf().fieldOf("offers").forGetter(TownHallView::offers),
            ProfessionLine.CODEC.listOf().fieldOf("professions").forGetter(TownHallView::professions)
    ).apply(instance, TownHallView::new));

    /** Списки копируются: снимок обязан быть неизменяемым, его сравнивают. */
    public TownHallView {
        buildings = List.copyOf(buildings);
        citizens = List.copyOf(citizens);
        offers = List.copyOf(offers);
        professions = List.copyOf(professions);
    }

    /**
     * Собрать снимок колонии.
     * <p>
     * Читает блоки мира — кровати и сундуки, — поэтому зовётся только когда
     * экран открыт: игрок в этот момент стоит у ратуши, и колония загружена.
     */
    public static TownHallView of(ServerWorld world, Settlement settlement) {
        Warehouse warehouse = Warehouse.of(world, settlement);
        ItemTally stock = warehouse.tally();

        return new TownHallView(
                settlement.name(),
                settlement.culture(),
                settlement.level().id(),
                settlement.population(),
                settlement.level().maxCitizens(),
                Housing.sleepingSpots(world, settlement).size(),
                Housing.freeSpots(world, settlement),
                meals(stock),
                daysOfFood(stock, settlement.population()),
                warehouse.containerCount(),
                construction(world, settlement, warehouse),
                buildings(settlement),
                citizens(settlement),
                stock,
                offers(settlement),
                knownProfessions());
    }

    /**
     * Кем можно сделать жителя — в порядке нужности из данных, том же,
     * в котором профессии достаются пришедшим сами.
     * <p>
     * Имя не {@code professions()}: у записи с таким полем это уже занятое
     * имя метода доступа, и компилятор отказывается прямо на этом.
     */
    private static List<ProfessionLine> knownProfessions() {
        List<ProfessionLine> lines = new ArrayList<>();
        for (Identifier id : ProfessionManager.byHiringPriority()) {
            ProfessionManager.get(id)
                    .ifPresent(known -> lines.add(new ProfessionLine(id, known.displayName())));
        }
        return lines;
    }

    private static List<BuildingLine> buildings(Settlement settlement) {
        List<BuildingLine> lines = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            lines.add(new BuildingLine(building.id(), building.type(), building.level(),
                    building.progress(), building.anchor(),
                    building.isOperational() && BuildOrders.canUpgrade(building)));
        }
        return lines;
    }

    private static List<CitizenLine> citizens(Settlement settlement) {
        List<CitizenLine> lines = new ArrayList<>();
        for (Citizen citizen : settlement.citizens()) {
            lines.add(new CitizenLine(
                    citizen.id(),
                    citizen.fullName(),
                    citizen.profession(),
                    !citizen.isHomeless(),
                    citizen.workplace().flatMap(settlement::building).map(Building::type),
                    moodOf(citizen),
                    citizen.discontent() >= Needs.warnAfterDays()));
        }
        return lines;
    }

    private static Mood moodOf(Citizen citizen) {
        if (citizen.saturation() <= 0) {
            return Mood.STARVING;
        }
        if (Needs.isHungry(citizen)) {
            return Mood.HUNGRY;
        }
        return citizen.isUnhappy() ? Mood.UNHAPPY : Mood.CONTENT;
    }

    /**
     * Текущая стройка — первая незаконченная, та же, за которую берётся
     * билдер. Показывать все сразу незачем: билдер всё равно делает их
     * по одной, и «не хватает» относится к той, что идёт.
     */
    private static Optional<Construction> construction(ServerWorld world, Settlement settlement,
                                                       Warehouse warehouse) {
        for (Building building : settlement.buildings()) {
            if (!BuildJob.isUnderConstruction(building)) {
                continue;
            }
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            int steps = schematic.plan().steps().size();
            return Optional.of(new Construction(building.type(), building.level(),
                    building.nextStep(), steps, missing(warehouse, schematic, building, steps)));
        }
        return Optional.empty();
    }

    /**
     * Чего не хватает, чтобы стройка дошла до конца.
     * <p>
     * Считается по всему остатку плана и <b>с учётом склада</b>: игрок
     * действует по этому списку, и «не хватает» для него значит «нет ни
     * у стройки, ни в сундуках», а не «не поднесено к площадке».
     */
    private static ItemTally missing(Warehouse warehouse, Schematic schematic, Building building,
                                     int lookahead) {
        Map<Identifier, Integer> shortfall = new LinkedHashMap<>();

        Materials.shortfall(schematic, building, lookahead).forEach((item, count) -> {
            int elsewhere = warehouse.count(item);
            if (count > elsewhere) {
                shortfall.put(Registries.ITEM.getId(item), count - elsewhere);
            }
        });
        return new ItemTally(shortfall);
    }

    /** Сколько на складе съедобного — в штуках, как игрок его и видит. */
    private static int meals(ItemTally stock) {
        int meals = 0;
        for (Map.Entry<Identifier, Integer> entry : stock.contents().entrySet()) {
            if (food(entry.getKey())) {
                meals += entry.getValue();
            }
        }
        return meals;
    }

    /**
     * На сколько дней хватит еды.
     * <p>
     * Число, по которому игрок действительно решает, ехать ли за хлебом:
     * «14 порций» ничего не говорит, «на два дня» говорит всё.
     */
    private static int daysOfFood(ItemTally stock, int population) {
        int nourishment = 0;
        for (Map.Entry<Identifier, Integer> entry : stock.contents().entrySet()) {
            Identifier id = entry.getKey();
            if (food(id)) {
                nourishment += Needs.nourishment(Registries.ITEM.get(id)) * entry.getValue();
            }
        }
        return nourishment / (Needs.DAILY_COST * Math.max(1, population));
    }

    private static boolean food(Identifier item) {
        Item known = Registries.ITEM.get(item);
        return known.getDefaultStack().isIn(ModTags.CITIZEN_FOOD);
    }

    /**
     * Что колония вправе заказать: схемы тех зданий, которые перечислены
     * в её культуре.
     * <p>
     * Список — предложение, а не запрет: он решает, что показать в экране.
     * Целость мира держат проверки {@link BuildOrders}, и на них же стоит
     * отладочная команда, которой позволено больше.
     */
    private static List<Identifier> offers(Settlement settlement) {
        Culture culture = CultureManager.get(settlement.culture());
        if (culture == null) {
            return List.of();
        }

        List<Identifier> offers = new ArrayList<>();
        for (Identifier schematic : SchematicLoader.ids()) {
            BuildJob.buildingTypeOf(schematic)
                    .filter(type -> culture.buildings().contains(type))
                    .ifPresent(type -> offers.add(schematic));
        }
        return offers;
    }
}
