package com.villagepax.gametest;

import com.villagepax.block.ModBlocks;
import com.villagepax.core.festival.ContestKind;
import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.sim.festival.Fair;
import com.villagepax.sim.festival.Fairs;
import com.villagepax.sim.festival.Feast;
import com.villagepax.sim.festival.Match;
import com.villagepax.sim.festival.Matches;
import net.minecraft.entity.player.PlayerEntity;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.BuildProgress;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import net.minecraft.util.math.Vec3d;
import com.villagepax.core.war.WarParty;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Hazards;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.tag.BlockTags;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.registry.Registries;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.sim.war.Allies;
import com.villagepax.sim.war.Raids;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.faith.Faith;
import com.villagepax.core.faith.Gods;
import com.villagepax.sim.build.Roads;
import com.villagepax.core.quest.Quest;
import com.villagepax.core.trade.Caravan;
import net.minecraft.inventory.SimpleInventory;
import com.villagepax.sim.Standing;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.trade.Caravans;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.Villages;
import net.minecraft.block.Block;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.screen.BuildOrders;
import com.villagepax.sim.work.Workplaces;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkTicker;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3i;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.Access;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.work.BuilderJob;
import net.minecraft.util.math.Direction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Игровые тесты — всё, что нельзя проверить без запущенного мира:
 * загрузка датапаков, реестры, сохранение состояния, поведение жителей.
 * <p>
 * Здесь — общая основа: постоянные, помощники и вложенные типы. Сами
 * проверки разнесены по классам-наследникам по темам (стройка, ремёсла,
 * набеги, проходимость…), и каждый из них объявлен в описании мода
 * проверок. Прежде всё это было одним классом на восемнадцать тысяч строк,
 * и найти в нём проверку своей механики можно было только поиском.
 */
abstract class GameTestSupport implements FabricGameTest {

    /**
     * Пустой шаблон 32×24×32 — для проверок, которые строят дальше восьми
     * блоков от угла.
     * <p>
     * Раскладчик ставит проверки одной партии рядом, отступая от каждой
     * на размер её шаблона и ещё шесть блоков, и перед началом очищает
     * этот объём. Проверка, объявившая пустой шаблон Fabric (8×8×8), а
     * настилающая пол на тридцать блоков, залезает к соседу: её камень
     * встаёт у него под ногами, его очистка съедает её дома, отряд
     * набега не находит, где встать. Всё это проявлялось лишь при
     * определённом порядке проверок в партии — то есть наугад. Шаблон
     * по настоящей площади проверки убирает соседа с её земли.
     */
    static final String WIDE_STRUCTURE = "villagepax:empty_32";

    static final Identifier NORMAN = new Identifier("villagepax", "norman");

    static final Identifier TOWN_HALL_SCHEMATIC =
            new Identifier("villagepax", "norman/town_hall_lvl1");

    static final Identifier TOWN_HALL_SCHEMATIC_FILE =
            new Identifier("villagepax", "villagepax/schematics/norman/town_hall_lvl1.nbt");

    static Schematic loadedTownHall(TestContext context) {
        Optional<Schematic> schematic = SchematicLoader.get(TOWN_HALL_SCHEMATIC);
        if (schematic.isEmpty()) {
            context.throwGameTestException("Схема " + TOWN_HALL_SCHEMATIC
                    + " не загружена. Загружены: " + SchematicLoader.ids());
        }
        return schematic.orElseThrow();
    }

    static final Identifier TOWN_HALL_TYPE = new Identifier("villagepax", "norman/town_hall");

    static final Identifier HOUSE_SCHEMATIC = new Identifier("villagepax", "norman/house_lvl1");

    static final Identifier HOUSE_TYPE = new Identifier("villagepax", "norman/house");

    static final Identifier LUMBERJACK_SCHEMATIC =
            new Identifier("villagepax", "norman/lumberjack_lvl1");

    static final Identifier LUMBERJACK_TYPE = new Identifier("villagepax", "norman/lumberjack");

    static final Identifier FARM_SCHEMATIC = new Identifier("villagepax", "norman/farm_lvl1");

    static final Identifier FARM_TYPE = new Identifier("villagepax", "norman/farm");

    static final Identifier TOWN_HALL_LVL2 =
            new Identifier("villagepax", "norman/town_hall_lvl2");

    /** Какой блок стоит следующим в плане — тот и должен быть в руке. */
    static Item expectedBlockInHand(Schematic schematic, Building site) {
        List<BuildStep> steps = schematic.plan().steps();
        if (site.nextStep() >= steps.size()) {
            return null;
        }

        BuildStep step = steps.get(site.nextStep());
        return step.placesBlock()
                ? Materials.itemFor(schematic.blockAt(step.paletteIndex())).orElse(null)
                : Items.IRON_PICKAXE;
    }

    /** Сколько шагов пути легло на средний ряд — тот, который мостят. */
    static int pavedSteps(ServerWorld world, CitizenEntity body, BlockPos to,
                                  BlockPos corner) {
        body.getNavigation().stop();
        Path path = body.getNavigation().findPathTo(to, 0);
        if (path == null) {
            return 0;
        }

        int steps = 0;
        for (int index = 0; index < path.getLength(); index++) {
            if (path.getNode(index).getBlockPos().getZ() == corner.getZ() + 1) {
                steps++;
            }
        }
        return steps;
    }

    /** Завезти сырьё на склад стопками: ручной завоз в один вызов. */
    static void raw(ServerWorld world, Warehouse warehouse, BlockPos where,
                            Item item, int count) {
        int left = count;
        while (left > 0) {
            int chunk = Math.min(left, item.getMaxCount());
            warehouse.addOrScatter(world, where, new ItemStack(item, chunk));
            left -= chunk;
        }
    }

    /**
     * Далеко от склада: билдер сам до сундука не дотянется.
     * <p>
     * Двенадцать блоков, а не двадцать. Дело не в правиле — до сундука
     * не дотянуться уже с трёх, — а в том, что мир игровых тестов
     * <b>общий</b>, площадки стоят в нём сеткой, и стройка за двадцать
     * блоков от своей уезжала к соседям, в область, которая не всегда
     * загружена. Тело билдера, отправленное туда, переставало
     * существовать, стройка стояла на нуле, и падало это через раз —
     * причём падало у тех, кто рядом. Нашлось, когда добавление двух
     * новых проверок сдвинуло раскладку.
     */
    static final BlockPos FAR_SITE = new BlockPos(12, 8, 0);

    static final Identifier FOUNDING_1 = new Identifier("villagepax", "norman/founding_1");

    static final Identifier FOUNDING_2 = new Identifier("villagepax", "norman/founding_2");

    static final Identifier FOUNDING_3 = new Identifier("villagepax", "norman/founding_3");

    /**
     * Сколько решений уходит на дом у этого народа. Ноль — не достроил.
     * <p>
     * За собой убирает начисто: план возвращается в воздух, тела
     * исчезают, поселение забывается. Следующий прогон обязан начинаться
     * с того же мира, иначе сравнивать нечего.
     */
    static int decisionsToBuild(TestContext context, ServerWorld world,
                                        SettlementManager manager, Identifier culture,
                                        BlockPos anchor, BlockPos storage, Schematic schematic) {
        world.setBlockState(storage, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(culture, Owner.of(UUID.randomUUID()),
                "Проба", storage);
        manager.add(colony);

        Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 1, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);

        try {
            stockFor(world, colony, schematic);
            // У угла стройки, наискосок: так мерили всегда.
            Citizen builder = hireWithBody(world, colony, BuildJob.BUILDER, anchor.add(-1, 0, -1));
            CitizenEntity body = (CitizenEntity) world
                    .getEntity(builder.entityUuid().orElseThrow());

            for (int round = 1; round <= 600; round++) {
                WorkTicker.decide(world, manager, colony, builder, Schedule.MORNING_WORK);
                if (site.isOperational()) {
                    return round;
                }

                BlockPos target = body.workTarget();
                if (target != null && standable(world, target)) {
                    body.refreshPositionAndAngles(target.getX() + 0.5, target.getY(),
                            target.getZ() + 0.5, 0f, 0f);
                }
            }
            return 0;
        } finally {
            demolish(world, site, schematic);
            discardBodies(world, colony);
            manager.remove(colony.id());
        }
    }

    static final Identifier MAYA = new Identifier("villagepax", "maya");

    static final Identifier MAYA_TOWN_HALL_TYPE =
            new Identifier("villagepax", "maya/town_hall");

    static final Identifier MAYA_HOUSE_TYPE = new Identifier("villagepax", "maya/house");

    /** Где в этой схеме очаг, в координатах мира. */
    static BlockPos hearthOf(Schematic schematic, Building site) {
        List<BuildStep> steps = schematic.plan().steps();
        for (BuildStep step : steps) {
            if (step.placesBlock()
                    && schematic.blockAt(step.paletteIndex()).isOf(Blocks.CAMPFIRE)) {
                return BuildJob.worldPos(site, schematic.size(), step.pos());
            }
        }
        return null;
    }

    /**
     * Снять причину совета, чтобы лестница шагнула дальше.
     *
     * @return удалось ли: ступени, которые в проверке не снимаются,
     *         честно обрывают обход
     */
    static boolean relieve(ServerWorld world, SettlementManager manager,
                                   Settlement colony, UUID player, String advice) {
        switch (advice) {
            case "villagepax.advice.no_beds" -> {
                // Жителю некуда лечь, а строить негде: проще перестать
                // ждать новых, чем ставить дом в проверке про совет.
                while (colony.hasRoomForCitizen()) {
                    Citizen extra = evenNewborn("Гость", "", NORMAN, Gender.FEMALE);
                    colony.addCitizen(extra);
                }
                return true;
            }
            case "villagepax.advice.no_farm" -> {
                Citizen farmer = evenNewborn("Пахарь", "", NORMAN, Gender.MALE);
                farmer.setProfession(FarmJob.FARMER);
                colony.addCitizen(farmer);
                return true;
            }
            case "villagepax.advice.idle_hands" -> {
                colony.citizens().forEach(citizen -> {
                    if (citizen.profession().isEmpty()) {
                        citizen.setProfession(FarmJob.FARMER);
                    }
                });
                return true;
            }
            case "villagepax.advice.empty_purse" -> {
                // Казна наполняется монетой, а не числом: жалование платится
                // из сундуков, и проверка обязана снимать причину тем же
                // способом, каким её снимет игрок.
                Coins.earn(Warehouse.of(world, colony).coins(), Coins.SILVER * 8);
                return true;
            }
            case "villagepax.advice.coin_leaks" -> {
                Citizen trader = evenNewborn("Купец", "", NORMAN, Gender.MALE);
                trader.setProfession(Villages.MERCHANT);
                colony.addCitizen(trader);
                Building stall = new Building(UUID.randomUUID(),
                        new Identifier("villagepax", "norman/market_stall"), 1,
                        colony.center(), BlockRotation.NONE, BuildProgress.DONE, List.of());
                colony.addBuilding(stall);
                trader.setWorkplace(stall.id());
                return true;
            }
            case "villagepax.advice.no_neighbours" -> {
                Settlement neighbours = Settlement.found(MAYA, Owner.AUTONOMOUS, "Йашчилан",
                        colony.center().add(5000, 0, 5000));
                neighbours.addReputation(player, Standing.KNOWN.from());
                manager.add(neighbours);
                return true;
            }
            case "villagepax.advice.no_temple" -> {
                Identifier temple = Faith.templeType(colony.culture()).orElse(null);
                if (temple == null) {
                    return false;
                }
                colony.addBuilding(new Building(UUID.randomUUID(), temple, 1, colony.center(),
                        BlockRotation.NONE, BuildProgress.DONE, List.of()));
                return true;
            }
            case "villagepax.advice.no_faith" -> {
                Gods.of(colony.culture()).stream().findFirst().ifPresent(god ->
                        colony.addFavour(god, Faith.Tier.NOTICED.from()));
                return true;
            }
            case "villagepax.advice.nothing_building" -> {
                colony.addBuilding(new Building(UUID.randomUUID(), HOUSE_TYPE, 1,
                        colony.center(), BlockRotation.NONE, BuildProgress.PLANNED, List.of()));
                return true;
            }
            // «Подними ратушу» — направление, а не беда: снимать его
            // в проверке нечем и незачем.
            default -> {
                return false;
            }
        }
    }

    // ============================ ЖИЗНЬ ============================

    /** Колония с двумя взрослыми, домом и едой: с неё начинается род. */
    static Settlement household(ServerWorld world, SettlementManager manager,
                                        TestContext context, BlockPos hall) {
        Settlement colony = colonyWithBuilder(world, manager, hall);
        raisedTownHall(colony, hall);

        // Оба — взрослые с возрастом: без возраста они «не помнят лет»,
        // а такие не стареют и не умирают, и проверять на них нечего.
        colony.citizens().forEach(citizen -> citizen.setLived(Ages.grownAt()));

        Citizen wife = evenNewborn("Аделиза", "", NORMAN, Gender.FEMALE);
        wife.setLived(Ages.grownAt());
        colony.addCitizen(wife);

        Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 64));
        return colony;
    }

    /** Кровати для всех: роды и приход требуют свободного места. */
    static void bedsFor(Settlement colony, int many) {
        for (int at = 0; at < many; at++) {
            Citizen sleeper = colony.citizens().size() > at ? colony.citizens().get(at) : null;
            if (sleeper != null) {
                sleeper.setBed(null);
            }
        }
    }

    // ======================= ХАРАКТЕРЫ =======================

    /**
     * Житель для проверок: <b>ровный</b>.
     * <p>
     * Характер выводится из опознавателя, а {@code Citizen.newborn} даёт
     * случайный, — значит каждый пятый работник в проверке рождался бы
     * ленивым. В игре ленивый работает вполсилы: делает дело через
     * решение. А в проверке решения идут подряд <b>в одном тике</b>,
     * и «через решение» превращается в «ни разу»: пивовар не варит,
     * билдер не кладёт, ткач не ткёт. Проверки начинали мерцать —
     * каждый шестой прогон ронял случайную из них, обвиняя ремесло
     * в чужом характере.
     * <p>
     * Правило отсюда общее: <b>работник в проверке нанят ради ремесла,
     * а не ради характера</b>. Кому нужен характер — берёт
     * {@link #someoneWith}.
     */
    static Citizen evenNewborn(String first, String last, Identifier culture,
                                       Gender gender) {
        for (int tries = 0; tries < 1000; tries++) {
            Citizen who = Citizen.newborn(first, last, culture, gender);
            if (Natures.of(who) == Nature.EVEN) {
                return who;
            }
        }
        throw new IllegalStateException("ровный характер не достаётся никому");
    }

    /**
     * Житель с нужным характером.
     * <p>
     * Перебором по рождению, а не подобранным руками опознавателем:
     * подобранный пришлось бы переписывать после любой правки смешивания,
     * и проверки характеров молча стали бы проверками одного и того же
     * ровного. Пять характеров равными долями — перебор кончается
     * на пятом жителе.
     */
    static Citizen someoneWith(Nature nature, String name, Gender gender) {
        return someoneWith(nature, name, gender, NORMAN);
    }

    static Citizen someoneWith(Nature nature, String name, Gender gender,
                                       Identifier culture) {
        for (int tries = 0; tries < 1000; tries++) {
            Citizen who = Citizen.newborn(name, "", culture, gender);
            if (Natures.of(who) == nature) {
                return who;
            }
        }
        throw new IllegalStateException("характер " + nature + " не достаётся никому");
    }

    /** Он же, но взрослый: ремесло детям не дают, и в храм они не ходят. */
    static Citizen grownWith(Nature nature, String name, Gender gender) {
        Citizen who = someoneWith(nature, name, gender);
        who.setLived(Ages.grownAt());
        return who;
    }

    // ======================= КВЕСТЫ: ПОРУЧЕНИЯ И НОВЫЕ ЦЕЛИ =======================

    static final Identifier FOUNDING_4 = new Identifier("villagepax", "norman/founding_4");

    static final Identifier FOUNDING_5 = new Identifier("villagepax", "norman/founding_5");

    static final Identifier FOUNDING_6 = new Identifier("villagepax", "norman/founding_6");

    /** Пройти всю писаную цепочку старейшины разом: она — не предмет проверки. */
    static void chainDone(Settlement village, UUID player) {
        for (Identifier step : List.of(FOUNDING_1, FOUNDING_2, FOUNDING_3,
                FOUNDING_4, FOUNDING_5, FOUNDING_6)) {
            village.noteQuestDone(player, step);
        }
    }

    /** Выполнить то, что просят сейчас: вещи в руки, мастерские и боги — в колонию. */
    static void satisfy(ServerWorld world, SettlementManager manager, Settlement village,
                                Settlement colony, UUID player, SimpleInventory hands) {
        Quests.Task task = Quests.task(village, player, Villages.ELDER,
                Schedule.dayOf(world.getTimeOfDay())).orElse(null);
        if (task == null) {
            return;
        }
        for (Quest.Objective objective : task.quest().objectives()) {
            if (objective instanceof Quest.Objective.Deliver deliver) {
                hands.addStack(new ItemStack(deliver.item(), deliver.count()));
            } else if (objective instanceof Quest.Objective.Build build) {
                BuildingTypes.all().forEach((type, known) -> {
                    if (known.employs(build.workplace())
                            && type.getPath().startsWith("norman/")
                            && colony.buildings().stream().noneMatch(
                                    site -> site.type().equals(type))) {
                        colony.addBuilding(new Building(UUID.randomUUID(), type, build.level(),
                                colony.center(), BlockRotation.NONE, BuildProgress.DONE,
                                List.of()));
                    }
                });
            } else if (objective instanceof Quest.Objective.Favour favour) {
                Gods.inDomain(colony.culture(), favour.domain())
                        .ifPresent(god -> colony.addFavour(god, favour.amount()));
            } else if (objective instanceof Quest.Objective.Friendship friendship) {
                manager.all().stream()
                        .filter(other -> other.culture().equals(friendship.culture()))
                        .findFirst()
                        .ifPresent(other -> other.addReputation(player, friendship.trust()));
            }
        }
    }

    // ============================ ФАЗА 4: ВЕРА ============================

    static final Identifier SOWER = new Identifier("villagepax", "norman_sower");

    static final Identifier MASON = new Identifier("villagepax", "norman_mason");

    static final Identifier WATCHMAN = new Identifier("villagepax", "norman_watchman");

    static final Identifier CHAPEL_TYPE = new Identifier("villagepax", "norman/chapel");

    static final Identifier CHAPEL_SCHEMATIC =
            new Identifier("villagepax", "norman/chapel_lvl1");

    /**
     * Построить, делая каждый шаг с того места, которое назвала стратегия,
     * и жалуясь на всё, что при этом пошло не так.
     */
    static List<String> raiseFromStandableSpots(ServerWorld world,
                                                        SettlementManager manager,
                                                        Settlement colony, Building site,
                                                        Schematic schematic, Identifier id) {
        List<String> complaints = new ArrayList<>();
        int steps = schematic.plan().steps().size();
        int midair = 0;
        int inFire = 0;

        for (int round = 0; round < steps * 4 + 200 && !site.isOperational(); round++) {
            BlockPos spot = BuilderJob.standingSpot(world, site).orElse(null);
            if (spot == null) {
                complaints.add(id + ": стройка не назвала места, где стоять, на шаге "
                        + site.nextStep());
                return complaints;
            }
            if (!standable(world, spot)) {
                midair++;
            }
            if (Hazards.standingHurts(world, spot)) {
                inFire++;
            }

            BuildJob.Outcome outcome = BuildJob.advance(world, manager, colony.id(), site.id(),
                    1, Vec3d.ofBottomCenter(spot));
            if (outcome == BuildJob.Outcome.NO_BUILDER
                    || outcome == BuildJob.Outcome.NO_SCHEMATIC
                    || outcome == BuildJob.Outcome.NOT_LOADED
                    || outcome == BuildJob.Outcome.NOT_FOUND) {
                complaints.add(id + ": стройка отказала — " + outcome);
                return complaints;
            }
            if (outcome == BuildJob.Outcome.WAITING_FOR_MATERIALS) {
                complaints.add(id + ": не хватило материалов на шаге " + site.nextStep()
                        + " из " + steps + ", хотя завезли всё по заявке");
                return complaints;
            }
        }

        if (!site.isOperational()) {
            complaints.add(id + ": не достроилось, шаг " + site.nextStep() + " из " + steps);
        }
        if (midair > 0) {
            complaints.add(id + ": " + midair + " раз стоять предлагалось там, "
                    + "где человек стоять не может");
        }
        if (inFire > 0) {
            complaints.add(id + ": " + inFire + " раз стоять предлагалось в огне");
        }
        return complaints;
    }

    /**
     * Общая проверка ремесла с мастерской: здание строится целиком,
     * житель нанимается, и дальше смотрим, куда его посылают.
     */
    static void checkTradeReach(TestContext context, Identifier schematicId,
                                        Identifier buildingType, Identifier profession) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = schematic(context, schematicId);

        // Ратуша ближе двенадцати блоков к стройке и вне её следа: дальше
        // билдер не берёт материалы сам (BuildJob.NEARBY_STORAGE), и
        // мастерская не достроится вовсе. На это я наступил трижды.
        BlockPos hall = context.getAbsolutePos(new BlockPos(11, 9, 2));
        List<BlockPos> ground = new ArrayList<>();

        try {
            for (int x = -3; x <= 18; x++) {
                for (int z = -3; z <= 12; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    ground.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall);
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
            Building workplace = new Building(UUID.randomUUID(), buildingType, 1, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(workplace);

            try {
                stockFor(world, colony, schematic);
                BuildJob.Outcome raised = BuildJob.advance(world, manager, colony.id(),
                        workplace.id(), 20_000);
                if (!workplace.isOperational()) {
                    context.throwGameTestException("Мастерская не достроилась (" + raised
                            + "), проверять нечего: шаг " + workplace.nextStep() + " из "
                            + schematic.plan().steps().size());
                    return;
                }

                // Мастерская, в которой нечего делать, ничего не проверяет:
                // поле приходит уже засеянным, а роща — саженцами. Дадим
                // им работу: урожай поспел, дерево выросло.
                giveWork(world, workplace, schematic);

                Citizen worker = hireWithBody(world, colony, profession,
                        context.getAbsolutePos(new BlockPos(12, 9, 2)));
                Workplaces.assign(world, colony);

                List<String> complaints = watchWhereHeIsSent(world, manager, colony, worker,
                        300, profession.getPath());
                if (!complaints.isEmpty()) {
                    context.throwGameTestException(String.join("\n  ", complaints));
                }
            } finally {
                demolish(world, workplace, schematic);
                discardBodies(world, colony);
                manager.remove(colony.id());
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Гонять решения и смотреть, куда жителя посылают.
     * <p>
     * Тело переносится только туда, где может стоять человек, — как
     * в проверке билдера. Считаются два промаха: точка, в которой стоять
     * нельзя, и точка, в которой жжётся. И третий случай: если жителя
     * не послали никуда ни разу, проверять было нечего, и об этом надо
     * сказать, а не молча зачесть.
     */
    static List<String> watchWhereHeIsSent(ServerWorld world, SettlementManager manager,
                                                   Settlement colony, Citizen worker,
                                                   int rounds, String who) {
        CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());
        List<String> complaints = new ArrayList<>();
        int midair = 0;
        int inFire = 0;
        int sent = 0;

        for (int round = 0; round < rounds; round++) {
            WorkTicker.decide(world, manager, colony, worker, Schedule.MORNING_WORK);

            BlockPos target = body.workTarget();
            if (target == null) {
                continue;
            }
            sent++;

            if (Hazards.standingHurts(world, target)) {
                inFire++;
            }
            if (standable(world, target)) {
                body.refreshPositionAndAngles(target.getX() + 0.5, target.getY(),
                        target.getZ() + 0.5, 0f, 0f);
            } else {
                midair++;
            }
        }

        if (sent == 0) {
            complaints.add(who + ": не послан никуда ни разу — проверка ничего не проверила");
        }
        if (midair > 0) {
            complaints.add(who + ": " + midair + " раз из " + sent
                    + " послан туда, где человек стоять не может");
        }
        if (inFire > 0) {
            complaints.add(who + ": " + inFire + " раз из " + sent + " послан в огонь");
        }
        return complaints;
    }

    /**
     * Дать ремеслу работу: поспевший урожай фермеру, выросшее дерево
     * лесорубу.
     * <p>
     * Без этого проверка ничего не проверяет: и поле, и роща приходят
     * из схемы в том состоянии, в котором делать нечего, — грядки уже
     * вскопаны и засеяны, саженцы уже посажены.
     */
    static void giveWork(ServerWorld world, Building workplace, Schematic schematic) {
        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            BlockState planned = schematic.blockAt(step.paletteIndex());
            BlockPos where = BuildJob.worldPos(workplace, schematic.size(), step.pos());

            if (planned.getBlock() instanceof net.minecraft.block.CropBlock crop) {
                world.setBlockState(where, crop.withAge(crop.getMaxAge()));
            } else if (planned.isOf(Blocks.OAK_SAPLING)) {
                for (int up = 0; up < 4; up++) {
                    world.setBlockState(where.up(up), Blocks.OAK_LOG.getDefaultState());
                }
                world.setBlockState(where.up(4), Blocks.OAK_LEAVES.getDefaultState());
            }
        }
    }

    // ======================= ЧЕРТОГ ГНОМОВ =======================

    static final Identifier DWARF = new Identifier("villagepax", "dwarf");

    /**
     * Насколько высока и полога гора игровых проверок.
     * <p>
     * Не украшение сцены, а условие задачи. Чертог уходит на дюжину блоков
     * под склон, залу нужно ещё восемь и три блока свода над ним: на плоском
     * блине из камня гномы не поселятся, и проверка проверяла бы не их,
     * а собственные декорации.
     */
    static final int PEAK = 26;

    static final int SPREAD = 14;

    /**
     * Насыпать гору: пологий конус, в котором чертогу есть где поместиться.
     */
    static List<BlockPos> raiseMountain(ServerWorld world, BlockPos foot) {
        List<BlockPos> stone = new ArrayList<>();
        for (int dx = -SPREAD; dx <= SPREAD; dx++) {
            for (int dz = -SPREAD; dz <= SPREAD; dz++) {
                // Склон пологий — блок на шесть шагов. Круче нельзя:
                // над сводом каждого зала нужно три блока горы, и на крутом
                // конусе их не остаётся уже в семи шагах от вершины.
                // Гора проверки обязана быть настоящей горой, иначе
                // проверялись бы декорации, а не разметка.
                int top = PEAK - Math.max(Math.abs(dx), Math.abs(dz)) / 6;
                for (int up = 0; up < top; up++) {
                    BlockPos at = foot.add(dx, up, dz);
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    stone.add(at);
                }
            }
        }
        return stone;
    }

    /**
     * Можно ли отсюда выйти под небо, идя только по воздуху.
     * <p>
     * Разлив в рост человека: клетка годится, если в ней и над ней пусто.
     * Так проверяется проходимость, а не просто наличие дыры в камне.
     */
    static boolean escapesToSky(ServerWorld world, BlockPos from) {
        return escapes(world, from, world::isSkyVisible);
    }

    /**
     * Можно ли отсюда дойти пешком туда, где выполняется условие.
     * <p>
     * Один разлив на два зеркальных народа: гном спрашивает «дойду ли
     * до неба», эльф — «спущусь ли на землю». Вопрос у обоих один
     * и тот же и задаётся <b>миру</b>, а не коду: проверяется то, что
     * почувствует игрок, а не то, честно ли билдер позвал нужный метод.
     */
    static boolean escapes(ServerWorld world, BlockPos from,
                                   java.util.function.Predicate<BlockPos> arrived) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(from);
        seen.add(from);

        while (!queue.isEmpty() && seen.size() < 20_000) {
            BlockPos at = queue.poll();
            if (arrived.test(at)) {
                return true;
            }
            for (BlockPos next : stepsFrom(world, at)) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    /** Сколько клеток обошёл разлив и докуда добрался — для сообщения об отказе. */
    static String reach(ServerWorld world, BlockPos from) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(from);
        seen.add(from);
        BlockPos far = from;
        while (!queue.isEmpty() && seen.size() < 20_000) {
            BlockPos at = queue.poll();
            if (at.getSquaredDistance(from) > far.getSquaredDistance(from)) {
                far = at;
            }
            for (BlockPos next : stepsFrom(world, at)) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen.size() + " клеток, дальняя " + far.subtract(from).toShortString();
    }

    /**
     * Куда отсюда можно шагнуть.
     * <p>
     * Не шесть соседей по сторонам света, а <b>шаг ходока</b>: четыре
     * стороны, и в каждой — на своём уровне, на блок вверх или на три
     * вниз. Разница не придирка. Шестью соседями разлив течёт вверх
     * по воздуху и вниз сквозь кроны, и тогда он проверяет не проходимость,
     * а наличие дырки; а с опорой под ногой, но без ступени вверх,
     * он не поднимется по гномьей лестнице, которую сам же и ищет.
     * <p>
     * Вверх — один блок: столько берёт прыжок. Вниз — три: столько
     * падают без урона, и по такому уступу житель сойдёт сам.
     */
    static List<BlockPos> stepsFrom(ServerWorld world, BlockPos at) {
        List<BlockPos> ahead = new ArrayList<>();
        for (Direction way : Direction.Type.HORIZONTAL) {
            for (int dy = 1; dy >= -3; dy--) {
                BlockPos next = at.offset(way).up(dy);
                if (roomToWalk(world, next)) {
                    ahead.add(next);
                    // Ниже первой найденной опоры в этой стороне смотреть
                    // незачем: туда уже не шагнёшь, там пол.
                    break;
                }
            }
        }
        return ahead;
    }

    /**
     * Можно ли сюда шагнуть — в рост, с опорой под ногой и с учётом того,
     * что двери открывают.
     * <p>
     * Закрытая дверь движение перекрывает, и без этой оговорки разлив
     * упирался бы в порог собственной ратуши. Проверка не должна быть
     * строже игры: и житель, и игрок дверь открывают.
     * <p>
     * Опора под ногой — поправка по следам <b>пустой проверки</b>. Без неё
     * разлив шёл по воздуху и честно «спускался» с эльфийского помоста
     * на землю сквозь просветы между стволами: всход можно было убрать
     * целиком, и проверка этого не замечала. Мерцающая проверка хуже
     * отсутствующей, а пустая хуже мерцающей — она не подводит, она врёт.
     */
    static boolean roomToWalk(ServerWorld world, BlockPos at) {
        BlockPos under = at.down();
        return world.getBlockState(under).isSolidBlock(world, under)
                && passable(world.getBlockState(at)) && passable(world.getBlockState(at.up()));
    }

    static boolean passable(BlockState state) {
        return !state.blocksMovement() || state.isIn(BlockTags.DOORS)
                || state.isIn(BlockTags.FENCE_GATES);
    }

    // ======================= КРОНЫ ЭЛЬФОВ =======================

    static final Identifier ELF = new Identifier("villagepax", "elf");

    /** Насколько густ и высок лес игровых проверок. */
    static final int GROVE = 16;

    static final int TRUNK = 9;

    /**
     * Насадить лес: стволы через клетку по нечётным колоннам.
     * <p>
     * Нечётным нарочно. Середина деревни обязана стоять на <b>чистой</b>
     * колонне — в колонне со стволом земли не находит никакой поиск,
     * ствол землёй не считается, — а ствол при этом должен быть в двух
     * шагах. Чётная середина и нечётные деревья дают ровно это.
     */
    static List<BlockPos> raiseForest(ServerWorld world, BlockPos soil) {
        List<BlockPos> planted = new ArrayList<>();
        for (int dx = -GROVE; dx <= GROVE; dx++) {
            for (int dz = -GROVE; dz <= GROVE; dz++) {
                BlockPos at = soil.add(dx, 0, dz);
                world.setBlockState(at, Blocks.DIRT.getDefaultState());
                planted.add(at);
                if (Math.abs(dx) % 2 == 0 || Math.abs(dz) % 2 == 0) {
                    continue;
                }
                for (int up = 1; up <= TRUNK; up++) {
                    BlockPos trunk = soil.add(dx, up, dz);
                    world.setBlockState(trunk, Blocks.OAK_LOG.getDefaultState());
                    planted.add(trunk);
                }
            }
        }
        return planted;
    }

    // ======================= ГРАЖДАНСТВО В ЧУЖОЙ ДЕРЕВНЕ =======================

    static final Identifier NORMAN_HOUSE_TYPE =
            new Identifier("villagepax", "norman/house");

    /** Деревня с готовым домом: без него селить некуда. */
    static Settlement villageWithHouse(ServerWorld world, SettlementManager manager,
                                               BlockPos centre, BlockPos houseAt) {
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", centre);
        world.setBlockState(centre, ModBlocks.TOWN_HALL.getDefaultState());
        village.addBuilding(new Building(UUID.randomUUID(), NORMAN_HOUSE_TYPE, 1, houseAt,
                BlockRotation.NONE, BuildProgress.DONE, List.of()));
        manager.add(village);
        return village;
    }

    /** Свести двоих в пару: запись о браке обязана стоять у обоих. */
    static void marry(Settlement colony, Citizen one, Citizen other) {
        if (colony.citizen(one.id()).isEmpty()) {
            colony.addCitizen(one);
        }
        if (colony.citizen(other.id()).isEmpty()) {
            colony.addCitizen(other);
        }
        one.marry(other.id());
        other.marry(one.id());
    }

    // ======================= ЖАЛОВАНИЕ, НАЛОГ И КРУГ МОНЕТЫ =======================

    /** Колония-деревня с работниками и казной. */
    static Settlement payroll(ServerWorld world, SettlementManager manager,
                                      BlockPos hall, int workers, int coin) {
        Settlement colony = colonyWithBuilder(world, manager, hall);
        colony.setLevel(SettlementLevel.VILLAGE);
        for (int at = 0; at < workers - 1; at++) {
            Citizen hand = evenNewborn("Работник" + at, "", NORMAN, Gender.FEMALE);
            hand.setProfession(FarmJob.FARMER);
            hand.setLived(Ages.grownAt());
            colony.addCitizen(hand);
        }
        if (coin > 0) {
            Coins.earn(Warehouse.of(world, colony).coins(), coin);
        }
        return colony;
    }

    /** Прилавок с купцом за ним: без человека прилавок не торгует. */
    static void openStall(Settlement colony) {
        Citizen trader = evenNewborn("Купец", "", NORMAN, Gender.MALE);
        trader.setProfession(Villages.MERCHANT);
        trader.setLived(Ages.grownAt());
        colony.addCitizen(trader);
        Building stall = new Building(UUID.randomUUID(),
                new Identifier("villagepax", "norman/market_stall"), 1,
                colony.center(), BlockRotation.NONE, BuildProgress.DONE, List.of());
        colony.addBuilding(stall);
        trader.setWorkplace(stall.id());
    }

    // ======================= ТРЕТИЙ НАРОД =======================

    static final Identifier PONY_HOUSE_TYPE = new Identifier("villagepax", "pony/house");

    static final Identifier PONY_HOUSE_SCHEMATIC =
            new Identifier("villagepax", "pony/house_lvl1");

    static final Identifier PONY = new Identifier("villagepax", "pony");

    /** Убрать за схваткой: только свои тела, запись колонии и пол. */
    static void cleanUpFight(ServerWorld world, SettlementManager manager,
                                     Settlement colony, BlockPos hall, List<BlockPos> floor,
                                     CitizenEntity... bodies) {
        for (CitizenEntity body : bodies) {
            if (body != null) {
                body.discard();
            }
        }
        cleanUpVillage(world, manager, colony, hall, floor);
        world.setBlockState(hall, Blocks.AIR.getDefaultState());
    }

    static void cleanUpLooks(ServerWorld world, SettlementManager manager,
                                     Settlement colony, CitizenEntity body, BlockPos hall) {
        body.discard();
        manager.remove(colony.id());
        world.setBlockState(hall, Blocks.AIR.getDefaultState());
    }

    static final Identifier BREWERY_SCHEMATIC =
            new Identifier("villagepax", "norman/brewery_lvl1");

    static final Identifier BREWERY_TYPE =
            new Identifier("villagepax", "norman/brewery");

    /**
     * Записать поселению осаду, которую проверка изображает телами.
     * <p>
     * Без записи тела набега живут до первой секунды: мод убирает бойцов,
     * о чьём отряде поселение не помнит. Правильно так и есть — а проверке
     * остаётся не выдумывать состояний, которых в игре не бывает.
     */
    static void rememberRaid(SettlementManager manager, Settlement colony, UUID party,
                                     BlockPos musters, int fighters) {
        manager.update(colony.id(), state -> state.besiege(
                new WarParty(party, UUID.randomUUID(), NORMAN, musters, fighters, 0L, 9_000L), 0L));
    }

    /**
     * Проходим ли выход целиком: от порога и на четыре шага наружу.
     * <p>
     * Считается ВЫСОТА НОГ, а не место блока: в проёме ноги на уровне
     * самого проёма, снаружи — на блок выше найденной опоры. Мерка
     * блоками давала ошибку в единицу, и из-за неё крыльцо выглядело
     * сделанным при перепаде в два блока.
     * <p>
     * И проверяется <b>весь спуск</b>, а не первая клетка за порогом.
     * Первая редакция смотрела только её — и пропускала обрыв на второй,
     * то есть ровно ту беду, на которую жаловался заказчик: ступень
     * стоит, а войти нельзя. Поймал это поиск пути в соседней проверке,
     * и мерку пришлось растить до его строгости.
     * <p>
     * Вторая редакция останавливалась на первой же ровной клетке — и это
     * была та же ошибка, от которой чинили крыльцо, только переписанная
     * в проверку: ровно перед обрывом клетка как раз ровная. Теперь
     * спуск идёт до конца, а кончается он на краю площадки, где опоры
     * нет вовсе.
     *
     * @return пусто, если пройти можно, иначе рассказ о том, где обрыв
     */
    static String descentTrouble(ServerWorld world, Building building,
                                         Schematic schematic, BlockPos door) {
        Direction out = Access.awayFrom(building, schematic, door);

        // Сторона обязана вести НАРУЖУ следа. Без этого вопроса проверка
        // меряет спуск там, куда показал сам проверяемый код: уйди он
        // в горницу — под ногами ровный пол, обрыва нет, всё «хорошо»,
        // а с улицы в дом по-прежнему не войти. Спрашивается только
        // у тех входов, от которых до края следа вообще можно дойти:
        // внутренняя дверь большого дома наружу и не должна выводить.
        if (leavesFootprint(building, schematic, door) && !leavesFootprint(building, schematic, door, out)) {
            return "сторона " + out + " от входа " + door.toShortString()
                    + " ведёт внутрь следа: крыльцо ляжет в горнице, а не на улице";
        }

        int walk = door.getY();
        for (int step = 1; step <= 4; step++) {
            BlockPos column = door.offset(out, step);
            // Ищем опору только рядом: глубже четырёх блоков — это уже
            // обрыв или край испытательной площадки, а крыльцо мостов
            // не строит и спрашивать с него нечего.
            int ground = Integer.MIN_VALUE;
            for (int y = walk + 2; y >= walk - 4; y--) {
                BlockPos at = column.withY(y);
                if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty()) {
                    ground = y;
                    break;
                }
            }
            if (ground == Integer.MIN_VALUE) {
                // Опоры нет вовсе: это край испытательной площадки,
                // а не порог. Дальше мерить нечего.
                return null;
            }
            int feet = ground + 1;
            if (feet < walk - 1) {
                return "на " + column.toShortString() + " ноги на " + feet
                        + ", а шагом раньше на " + walk + " — обрыв в " + (walk - feet);
            }
            if (feet > walk + 1) {
                return "на " + column.toShortString() + " стена высотой "
                        + (feet - walk);
            }
            walk = feet;
        }
        return null;
    }

    /** Выводит ли эта сторона за след здания — на улицу, а не в горницу. */
    static boolean leavesFootprint(Building building, Schematic schematic,
                                           BlockPos door, Direction way) {
        for (int step = 1; step <= 4; step++) {
            if (!BuildSite.covers(building.anchor(), schematic.size(), building.rotation(),
                    door.offset(way, step))) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли у этого входа вообще выход наружу в четыре шага. */
    static boolean leavesFootprint(Building building, Schematic schematic, BlockPos door) {
        for (Direction way : Direction.Type.HORIZONTAL) {
            if (leavesFootprint(building, schematic, door, way)) {
                return true;
            }
        }
        return false;
    }

    static final Identifier WEAVERY_SCHEMATIC =
            new Identifier("villagepax", "norman/weavery_lvl1");

    static final Identifier WEAVERY_TYPE =
            new Identifier("villagepax", "norman/weavery");

    static final Identifier WEAVER = new Identifier("villagepax", "weaver");

    /** Рассказ о том, почему не дойти: порог, ступени и всё, что рядом. */
    static String walkFailure(String what, String why, ServerWorld world,
                                      Building building, Schematic schematic,
                                      CitizenEntity walker, BlockPos target) {
        StringBuilder story = new StringBuilder(what + ": " + why
                + ". Цель " + target.toShortString() + ", житель "
                + walker.getBlockPos().toShortString()
                + ", час " + Math.floorMod(world.getTimeOfDay(), Schedule.DAY_LENGTH)
                + " (" + Schedule.at(world.getTimeOfDay()) + ")");
        for (BlockPos door : Access.entrances(building, schematic)) {
            story.append(" | порог ").append(door.toShortString())
                    .append(" сам=").append(world.getBlockState(door).getBlock())
                    .append(" под=").append(world.getBlockState(door.down()).getBlock());
        }
        for (BlockPos spot : Access.stepSpots(building, schematic)) {
            // Полоса возможных мест широка, а рассказывать стоит о занятых:
            // пустые клетки только прячут в себе те, где что-то лежит.
            if (!world.getBlockState(spot).isAir()) {
                story.append(" | ступень ").append(spot.toShortString())
                        .append("=").append(world.getBlockState(spot).getBlock());
            }
        }
        // Сколько положит крыльцо, если позвать его прямо сейчас: ноль
        // значит «отказывается», больше нуля — «его не звали».
        story.append(" | крыльцо видит: ").append(Access.story(world, building, schematic))
                .append(" | повторный вызов положил ")
                .append(Access.porch(world, building, schematic));
        return story.toString();
    }

    /**
     * Почему житель не дойдёт до этой точки — или пусто, если дойдёт.
     * <p>
     * Спрашивается сам ванильный поиск пути. Это единственная честная
     * мерка проходимости: всё остальное — наши догадки о том, что он
     * считает проходимым.
     */
    static String whyCannotReach(CitizenEntity walker, BlockPos target) {
        Path path = walker.getNavigation().findPathTo(target, 0);
        if (path == null) {
            return "поиск пути не построил дороги вовсе";
        }
        if (!path.reachesTarget()) {
            BlockPos end = path.getTarget();
            return "дорога обрывается на " + end.toShortString();
        }
        return null;
    }

    /**
     * Место внутри здания, до которого житель обязан доходить.
     * <p>
     * Ищется <b>по миру</b>, а не по меткам схемы: у норманнского дома
     * кровати стоят настоящими блоками, у поля метка одна и та на пугале,
     * и опираться на метки значило бы проверять не то. Годится любая
     * клетка внутри следа, где есть воздух в рост и твёрдая опора под
     * ногами, — ближайшая к середине.
     */
    static BlockPos insideOf(ServerWorld world, Building building, Schematic schematic) {
        Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
        BlockPos anchor = building.anchor();
        BlockPos best = null;
        double closest = Double.MAX_VALUE;
        double midX = anchor.getX() + size.getX() / 2.0;
        double midZ = anchor.getZ() + size.getZ() / 2.0;

        for (int dx = 1; dx < size.getX() - 1; dx++) {
            for (int dz = 1; dz < size.getZ() - 1; dz++) {
                for (int dy = 0; dy < size.getY() - 1; dy++) {
                    BlockPos at = anchor.add(dx, dy, dz);
                    // Проходимость меряется столкновениями, а не воздухом:
                    // фермер стоит ПОСРЕДИ моркови, а морковь — не воздух.
                    boolean standable = !world.getBlockState(at.down())
                            .getCollisionShape(world, at.down()).isEmpty();
                    boolean roomToStand = world.getBlockState(at)
                            .getCollisionShape(world, at).isEmpty()
                            && world.getBlockState(at.up())
                            .getCollisionShape(world, at.up()).isEmpty();
                    if (!standable || !roomToStand) {
                        continue;
                    }
                    double away = Math.abs(at.getX() + 0.5 - midX)
                            + Math.abs(at.getZ() + 0.5 - midZ);
                    if (away < closest) {
                        closest = away;
                        best = at;
                    }
                }
            }
        }
        return best;
    }

    static final Identifier STALL_TYPE =
            new Identifier("villagepax", "norman/market_stall");

    static final Identifier STALL_SCHEMATIC =
            new Identifier("villagepax", "norman/market_stall_lvl1");

    static final Identifier FARMER = new Identifier("villagepax", "farmer");

    /**
     * Снести всё, кроме ратуши: первый надел колонии проверкам стройки
     * только мешает — он встаёт даром и занимает место у ратуши.
     */
    static void razeHolding(ServerWorld world, Settlement colony) {
        for (Building site : colony.buildings()) {
            if (BuildingTypes.isTownHall(site.type())) {
                continue;
            }
            SchematicLoader.get(BuildJob.schematicId(site))
                    .ifPresent(plan -> demolish(world, site, plan));
        }
    }

    /** Сколько клеток следа здания заняты не воздухом. */
    static int raisedBlocks(ServerWorld world, Building site, Schematic schematic) {
        int standing = 0;
        for (Schematic.PalettedBlock block : schematic.blocks()) {
            BlockPos at = BuildJob.worldPos(site, schematic.size(), block.pos());
            if (!world.getBlockState(at).isAir()) {
                standing++;
            }
        }
        return standing;
    }

    static final Identifier MARKET_TYPE = new Identifier("villagepax", "norman/market");

    static final Identifier MARKET_SCHEMATIC =
            new Identifier("villagepax", "norman/market_lvl1");

    /**
     * Сколько раз за шесть дней деревня послала обоз к этой колонии.
     * <p>
     * Гость отпускается сразу: обоз, оставшийся у ворот, не даёт прийти
     * следующему, и «каждый день» превратилось бы в «один раз».
     */
    static int countVisits(ServerWorld world, SettlementManager manager,
                                   Settlement village, Settlement colony) {
        int came = 0;
        for (long day = 0; day < 6; day++) {
            Caravans.sendIfDue(world, manager, village, day);
            Settlement gates = manager.byId(colony.id()).orElseThrow();
            came += gates.visitors().size();
            for (Caravan guest : List.copyOf(gates.visitors())) {
                gates.seeOff(guest.id());
            }
        }
        return came;
    }

    /** Убрать за боем: тела обеих сторон, обе деревни и площадку. */
    static void tidyUpFight(ServerWorld world, SettlementManager manager, WarParty party,
                                    Settlement friend, BlockPos friendAt, Settlement colony,
                                    BlockPos centre, List<BlockPos> floor) {
        Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
        Allies.dismiss(world, party);
        cleanUpVillage(world, manager, friend, friendAt, List.of());
        cleanUpVillage(world, manager, colony, centre, floor);
    }

    static final Identifier TOWER_TYPE = new Identifier("villagepax", "norman/watchtower");

    static final Identifier TOWER_SCHEMATIC =
            new Identifier("villagepax", "norman/watchtower_lvl1");

    /** Сколько хранилищ стоит в следе здания прямо сейчас. */
    static int containersIn(ServerWorld world, Building building, Schematic schematic) {
        int found = 0;
        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            BlockPos where = BuildJob.worldPos(building, schematic.size(), step.pos());
            if (world.getBlockEntity(where) instanceof net.minecraft.inventory.Inventory) {
                found++;
            }
        }
        return found;
    }

    /** Убрать за набегом: свои тела, запись колонии и пол. */
    static void cleanUpRaid(ServerWorld world, SettlementManager manager,
                                    Settlement colony, WarParty party, List<BlockPos> floor) {
        Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
        manager.remove(colony.id());
        for (BlockPos at : floor) {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
        }
        world.setBlockState(colony.center(), Blocks.AIR.getDefaultState());
    }

    /**
     * Убрать деревню за собой начисто — включая память о месте.
     * <p>
     * Забыть место обязательно: места деревень вечны, и мир игровых тестов
     * переживает прогон. Без этого второй запуск подряд не поднимал бы
     * деревню и падал — что и случилось, когда тест был написан.
     */
    static void cleanUpVillage(ServerWorld world, SettlementManager manager,
                                       Settlement village, BlockPos centre, List<BlockPos> meadow) {
        if (village != null) {
            razeVillage(world, village);
            discardBodies(world, village);
            // Убранство улиц — колодец, фонари, цветы — тоже в общем мире
            // проверок, и оставленное мешало бы следующей деревне на том же месте.
            for (BlockPos at : manager.decorOf(village.id())) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            // И скотина у коновязи: узел привязи висит на столбе, которого
            // уже нет, а животное пошло бы бродить по чужим проверкам.
            net.minecraft.util.math.Box square =
                    new net.minecraft.util.math.Box(village.center()).expand(32, 12, 32);
            // Сорвавшийся с привязи попугай уже не «на привязи», а летает
            // по чужим проверкам — поэтому убирается весь скот коновязи.
            world.getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, square,
                    mob -> mob.isLeashed() || mob instanceof net.minecraft.entity.passive.ParrotEntity
                            || mob instanceof net.minecraft.entity.passive.SheepEntity
                            || mob instanceof net.minecraft.entity.passive.WolfEntity
                            || mob instanceof net.minecraft.entity.passive.FoxEntity
                            || mob instanceof net.minecraft.entity.passive.AbstractHorseEntity)
                    .forEach(net.minecraft.entity.Entity::discard);
            world.getEntitiesByClass(net.minecraft.entity.decoration.LeashKnotEntity.class, square,
                    knot -> true).forEach(net.minecraft.entity.Entity::discard);
            manager.remove(village.id());
        }
        manager.forget(centre);
        for (BlockPos at : meadow) {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
        }
    }

    /**
     * Кто мешает деревне встать здесь. Нужно в сообщении об отказе: без
     * этого «деревня не встала» не отличает занятое место от тесноты,
     * и на поиск причины уходит прогон за прогоном.
     */
    static String whoBlocks(SettlementManager manager, BlockPos centre) {
        Settlement probe = Settlement.found(NORMAN, Owner.AUTONOMOUS, "проба", centre);
        return manager.conflictWith(probe)
                .map(clash -> clash.name() + " в " + clash.center().toShortString())
                .orElse("нет");
    }

    /** Убрать деревню за собой: игровые тесты делят один мир. */
    static void razeVillage(ServerWorld world, Settlement village) {
        for (Building site : village.buildings()) {
            SchematicLoader.get(BuildJob.schematicId(site))
                    .ifPresent(schematic -> demolish(world, site, schematic));
        }
        world.setBlockState(village.center(), Blocks.AIR.getDefaultState());
    }

    /** Список декора норманнов — из датапака, а не из догадки теста. */
    static List<Block> decorTable() {
        Culture norman = CultureManager.get(NORMAN);
        List<Block> table = new ArrayList<>();
        if (norman != null) {
            for (Identifier id : norman.decor()) {
                table.add(Registries.BLOCK.get(id));
            }
        }
        return table;
    }

    /**
     * Прогон стройки без телепорта по воздуху: тело переносится только туда,
     * где может стоять человек. Возвращает, сколько раз ему назвали точку,
     * до которой в игре не дойти.
     */
    static int runWorkOnFoot(ServerWorld world, SettlementManager manager,
                                     Settlement colony, Citizen worker, int rounds) {
        CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());
        int impossible = 0;

        for (int round = 0; round < rounds; round++) {
            WorkTicker.decide(world, manager, colony, worker, Schedule.MORNING_WORK);

            BlockPos target = body.workTarget();
            if (target == null) {
                continue;
            }
            if (standable(world, target)) {
                body.refreshPositionAndAngles(target.getX() + 0.5, target.getY(),
                        target.getZ() + 0.5, 0f, 0f);
            } else {
                impossible++;
            }
        }
        return impossible;
    }

    /** Ноги на твёрдом, голова в пустоте — то же, что требует ванильная ходьба. */
    static boolean standable(ServerWorld world, BlockPos spot) {
        return world.getBlockState(spot.down()).isSolidBlock(world, spot.down())
                && world.getBlockState(spot).getCollisionShape(world, spot).isEmpty()
                && world.getBlockState(spot.up()).getCollisionShape(world, spot.up()).isEmpty();
    }

    static final Identifier HOUSE_LVL2 = new Identifier("villagepax", "norman/house_lvl2");

    static final Identifier FARM_LVL2 = new Identifier("villagepax", "norman/farm_lvl2");

    /**
     * Сколько грядок на поле второго уровня.
     * <p>
     * Поле растёт на восток и юг, якорь остаётся тем же: двадцать три
     * грядки первого уровня становятся сорока шестью. Из внутренних
     * сорока девяти клеток вычтены два колодца и тюк пугала.
     */
    static final int BIGGER_FIELD = 46;

    /**
     * Улица от двери дома к ратуше — и обязана быть <b>непрерывной</b>:
     * дорожка с провалами не помогает поиску пути и выглядит хуже, чем её
     * отсутствие.
     * <p>
     * Ожидаемый маршрут больше не выписывается координатами.
     * <p>
     * Раньше здесь стояли восемь угаданных пар, и любая правка правила
     * (например «улица начинается от порога, а не от стены») ломала тест
     * не по делу. Теперь проверяются <b>свойства</b>: маршрут спрашивается
     * у {@code Roads}, и он обязан начинаться прямо перед дверью, не
     * рваться и быть шириной в один тайл.
     */
    static List<BlockPos> streetOf(ServerWorld world, Settlement colony, Building house) {
        return Roads.route(world, colony, house);
    }

    /** Место ратуши и якорь дома, между которыми ляжет улица. */
    static final BlockPos STREET_HALL = new BlockPos(1, 9, 1);

    static final BlockPos STREET_HOUSE = new BlockPos(10, 9, 3);

    /** Высота газона: улица ложится на него, дом стоит на нём же. */
    static final int LAWN = 8;

    /** Ровный газон под колонию: улице надо по чему идти. */
    static List<BlockPos> lawn(ServerWorld world, TestContext context) {
        List<BlockPos> laid = new ArrayList<>();

        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 4; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, LAWN, z));
                world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                laid.add(at);
            }
        }
        return laid;
    }

    /** Дом строится разом: улицу мостят от <b>готового</b> здания. */
    static void raiseHouse(TestContext context, ServerWorld world, SettlementManager manager,
                                   Settlement colony, Building house, Schematic housePlan) {
        stockFor(world, colony, housePlan);
        if (BuildJob.advance(world, manager, colony.id(), house.id(), 10_000)
                != BuildJob.Outcome.FINISHED) {
            context.throwGameTestException("Дом не достроился, мостить нечего");
        }
    }

    static void clearStreet(ServerWorld world, Building house, Schematic housePlan,
                                    List<BlockPos> lawn) {
        demolish(world, house, housePlan);
        for (BlockPos at : lawn) {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
        }
    }

    static Schematic schematic(TestContext context, Identifier id) {
        Optional<Schematic> found = SchematicLoader.get(id);
        if (found.isEmpty()) {
            context.throwGameTestException("Схема " + id + " не загружена. Загружены: "
                    + SchematicLoader.ids());
        }
        return found.orElseThrow();
    }

    static Building plan(Settlement colony, BlockPos anchor, Identifier type,
                                 BlockRotation rotation) {
        Building site = new Building(UUID.randomUUID(), type, 1, anchor, rotation,
                BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);
        return site;
    }

    /**
     * Готовая ратуша как здание колонии — то же, что делает основание.
     * <p>
     * Помощник {@code colonyWithBuilder} ставит только блок, а рост уровня
     * считается по <b>зданию</b>: без него колонии нечего улучшать.
     */
    static Building raisedTownHall(Settlement colony, BlockPos at) {
        // Якорь считается так же, как при основании: блок ратуши — середина
        // её следа, а не угол. Иначе тест проверял бы геометрию, которой
        // в игре не бывает.
        BlockPos anchor = SchematicLoader.get(TOWN_HALL_SCHEMATIC)
                .map(schematic -> BuildOrders.centredAnchor(at, schematic, BlockRotation.NONE))
                .orElse(at);

        Building hall = new Building(UUID.randomUUID(), TOWN_HALL_TYPE, 1, anchor,
                BlockRotation.NONE, BuildProgress.DONE, List.of());
        colony.addBuilding(hall);
        return hall;
    }

    /**
     * Тело жителя, а если его больше нет — новое.
     * <p>
     * Мир игровых тестов не держит чанки вечно, и выгрузка снимает тело
     * вместе с опознавателем в записи жителя. Тест, который на это
     * не рассчитывает, падает через раз и не по своей вине: проверять надо
     * решения жителя, а не то, повезло ли чанку остаться загруженным.
     */
    static CitizenEntity bodyOf(ServerWorld world, Settlement colony, Citizen citizen) {
        CitizenEntity body = citizen.entityUuid()
                .map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .map(CitizenEntity.class::cast)
                .orElse(null);

        return body != null ? body : CitizenSpawner.spawnBody(world, colony, citizen);
    }

    static void runWork(ServerWorld world, SettlementManager manager, Settlement colony,
                                Citizen worker, int rounds, Schedule part) {
        CitizenEntity body = bodyOf(world, colony, worker);

        for (int round = 0; round < rounds; round++) {
            WorkTicker.decide(world, manager, colony, worker, part);

            BlockPos target = body.workTarget();
            if (target != null) {
                body.refreshPositionAndAngles(target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                        0f, 0f);
            }
        }
    }

    /**
     * Прогон стратегии с телепортом вместо ходьбы.
     * <p>
     * Тесты стратегии не должны зависеть от поиска пути: он медленный,
     * зависит от рельефа и способен сорвать тест по причинам, к решениям
     * жителя не относящимся. Что жители действительно ходят, доказывает
     * {@code playerPathRaisesBuildingByItself} на настоящих тиках мира.
     */
    static Citizen hireWithBody(ServerWorld world, Settlement colony, Identifier profession,
                                        BlockPos at) {
        // Ровный по той же причине, что и билдер: работник в проверке
        // нанят ради своего ремесла, а не ради характера.
        Citizen citizen = someoneWith(Nature.EVEN, "Работник", Gender.FEMALE);
        citizen.setLived(Ages.grownAt());
        citizen.setProfession(profession);
        citizen.setPosition(Vec3d.ofBottomCenter(at));
        colony.addCitizen(citizen);
        CitizenSpawner.spawnBody(world, colony, citizen);
        return citizen;
    }

    /** Тела не сохраняются, но живут до выгрузки: игровые тесты делят один мир. */
    static void discardBodies(ServerWorld world, Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            citizen.entityUuid().map(world::getEntity).ifPresent(Entity::discard);
        }
    }

    /** Ратуша ставится настоящим блоком: без неё у колонии нет ни одного хранилища. */
    static Settlement colonyWithBuilder(ServerWorld world, SettlementManager manager, BlockPos center) {
        return colonyWithBuilder(world, manager, center, NORMAN);
    }

    static Settlement colonyWithBuilder(ServerWorld world, SettlementManager manager,
                                                BlockPos center, Identifier culture) {
        world.setBlockState(center, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(culture, Owner.of(UUID.randomUUID()), "Стройка", center);
        // Ровный нарочно: полсилы зависят и от характера, а здесь
        // проверяется ремесло. Ленивый билдер, доставшийся раз в пять
        // рождений, превратил бы половину проверок мода в мерцающие —
        // и обвиняли бы в этом стройку, а не характер.
        Citizen builder = someoneWith(Nature.EVEN, "Rollo", Gender.MALE);
        builder.setLived(Ages.grownAt());
        builder.setProfession(BuildJob.BUILDER);
        colony.addCitizen(builder);
        manager.add(colony);
        return colony;
    }

    static Building plan(Settlement colony, BlockPos anchor, BlockRotation rotation) {
        Building site = new Building(UUID.randomUUID(), TOWN_HALL_TYPE, 1, anchor, rotation,
                BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);
        return site;
    }

    static void stockFor(ServerWorld world, Settlement colony, Schematic schematic) {
        Warehouse warehouse = Warehouse.of(world, colony);
        Materials.required(schematic).forEach((item, count) -> {
            int left = count;
            while (left > 0) {
                int chunk = Math.min(left, item.getMaxCount());
                warehouse.add(new ItemStack(item, chunk));
                left -= chunk;
            }
        });
    }

    /**
     * Поставить здание готовым на месте: запас — в саму стройку, план — за раз.
     * <p>
     * Тем же путём деревня получает подарок ({@code Raising.raise}), только
     * без поиска места: проверке нужно своё. Склад ратуши на большие здания
     * не рассчитан — двенадцать десятков блоков ярмарки в двадцать семь
     * ячеек сундука не лезут, и лишнее высыпалось бы под ноги.
     */
    static Building standUp(TestContext context, ServerWorld world, SettlementManager manager,
                            Settlement settlement, Identifier type, BlockPos anchor) {
        Schematic plan = schematic(context,
                new Identifier(type.getNamespace(), type.getPath() + "_lvl1"));
        Building site = plan(settlement, anchor, type, BlockRotation.NONE);
        Materials.required(plan).forEach((item, count) ->
                site.stock().add(Registries.ITEM.getId(item), count));
        BuildJob.Outcome outcome = BuildJob.advance(world, manager, settlement.id(), site.id(),
                Integer.MAX_VALUE);
        if (outcome != BuildJob.Outcome.FINISHED) {
            context.throwGameTestException(type + " не встала: " + outcome);
        }
        return site;
    }

    /** Убрать за собой: игровые тесты делят один мир. */
    static void demolish(ServerWorld world, Building site, Schematic schematic) {
        for (BuildStep step : schematic.plan().steps()) {
            world.setBlockState(BuildJob.worldPos(site, schematic.size(), step.pos()),
                    Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
        clearPorch(world, site, schematic);
    }

    /**
     * Верх земли мира проверок: плоский мир, трава на этой высоте.
     * <p>
     * Всё, что проверки кладут сами, лежит выше; ниже — сам мир, и его
     * уборка не трогает никогда.
     */
    static final int WORLD_FLOOR = -61;

    /**
     * Убрать откос за зданием: грунт, досыпанный и оставленный вокруг следа.
     * <p>
     * Откос кладёт землю <b>снаружи</b> следа, а снос идёт по следу — та же
     * беда, что уже была с крыльцом. Убирается только природный грунт
     * и только выше земли мира: своё проверки кладут выше неё. Свою землю
     * проверка всё равно убирает сама, так что снять её здесь раньше
     * времени — не потеря.
     */
    static void clearSkirt(ServerWorld world, Building site, Schematic schematic) {
        Vec3i size = BuildSite.rotatedSize(schematic.size(), site.rotation());
        BlockPos anchor = site.anchor();
        int pad = anchor.getY() - 1;
        int reach = Math.max(com.villagepax.sim.build.Grading.MARGIN,
                com.villagepax.sim.build.Grading.APPROACH);
        for (int dx = -reach; dx < size.getX() + reach; dx++) {
            for (int dz = -reach; dz < size.getZ() + reach; dz++) {
                if (dx >= 0 && dz >= 0 && dx < size.getX() && dz < size.getZ()) {
                    continue;
                }
                for (int y = pad - reach - com.villagepax.sim.build.Grading.MAX_CHANGE;
                     y <= pad + reach; y++) {
                    BlockPos at = anchor.add(dx, 0, dz).withY(y);
                    if (y > WORLD_FLOOR
                            && com.villagepax.sim.build.Grading.isEarth(world.getBlockState(at))) {
                        world.setBlockState(at, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }

    /**
     * Убрать и ступени у входов.
     * <p>
     * Крыльцо кладётся <b>снаружи</b> следа здания, а снос проходит только
     * по следу — и ступени оставались в общем мире игровых тестов навсегда.
     * Следующая проверка находила на своём месте чужой булыжник и падала
     * непонятно от чего.
     * <p>
     * Убирается только <b>похожее на ступень</b> и только в столбцах
     * у входа: первая попытка вычищала объём вокруг входа и вырезала землю,
     * на которой стояли соседние проверки, — а падали от этого уже третьи.
     * Вторая считала высоту ступени наперёд, но укладка идёт по земле,
     * и одна ровная клетка сдвигала всю лесенку мимо расчёта: чужой
     * булыжник оставался, а вместо него стиралась целая клетка.
     */
    static void clearPorch(ServerWorld world, Building site, Schematic schematic) {
        for (BlockPos spot : Access.stepSpots(site, schematic)) {
            if (Access.isTread(world.getBlockState(spot))) {
                world.setBlockState(spot, Blocks.AIR.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
        }
    }


    // --- ярмарка на лугу: место состязаний ---

    static final Identifier NORMAN_FAIRGROUND = new Identifier("villagepax", "norman/fairground");
    static final Identifier NORMAN_FAIRGROUND_PLAN = new Identifier("villagepax", "norman/fairground_lvl1");

    /** День норманнского праздника в проверках состязаний: полнолуние. */
    static final long FAIR_DAY = 8;

    /** Утро: состязания открыты. */
    static final long FAIR_MORNING = 1_000;

    /** Деревня с ярмаркой и затейником на своём лугу. */
    record FairGround(Settlement village, Building fair, Fair place, BlockPos hall,
                      List<BlockPos> meadow) {
    }

    /**
     * Деревня народа с ярмаркой и затейником на своём лугу.
     * <p>
     * Луг — свой слой травы поверх земли мира: вещицы прячутся на земле,
     * зверьки бегают по ней, а землю мира уборка не трогает. Ярмарка — в углу
     * площадки, ратуша — в дальнем углу.
     */
    static FairGround fairGround(TestContext context, ServerWorld world, SettlementManager manager) {
        List<BlockPos> meadow = new ArrayList<>();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                meadow.add(at);
            }
        }
        BlockPos hall = context.getAbsolutePos(new BlockPos(30, 2, 30));
        Settlement village = colonyWithBuilder(world, manager, hall);
        village.setOwner(Owner.AUTONOMOUS);
        Building fair = standUp(context, world, manager, village, NORMAN_FAIRGROUND,
                context.getAbsolutePos(new BlockPos(2, 1, 2)));
        hireWithBody(world, village, Villages.ENTERTAINER, Workplaces.stations(fair).get(0));
        Workplaces.assign(world, village);
        return new FairGround(village, fair, Fairs.of(village).orElseThrow(), hall, meadow);
    }

    /** Убрать за ярмаркой на лугу: состязание, стол праздника, ярмарку, деревню, луг. */
    static void clearFairGround(TestContext context, ServerWorld world, SettlementManager manager,
                                FairGround ground) {
        Matches.at(ground.village().id()).ifPresent(match ->
                match.cancel(world, "villagepax.contest.cancel.over"));
        // Состязание накрывает стол праздника — пироги стоят над столами, вне схемы.
        manager.festiveOf(ground.village().id()).ifPresent(memory ->
                Feast.clearUp(world, manager, ground.village(), memory));
        demolish(world, ground.fair(), schematic(context, NORMAN_FAIRGROUND_PLAN));
        discardBodies(world, ground.village());
        manager.remove(ground.village().id());
        world.setBlockState(ground.hall(), Blocks.AIR.getDefaultState());
        ground.meadow().forEach(at -> world.setBlockState(at, Blocks.AIR.getDefaultState()));
    }

    /** Подставной игрок на этом месте: в круге старта и не выбывает. */
    static PlayerEntity playerAt(TestContext context, BlockPos at) {
        PlayerEntity player = context.createMockSurvivalPlayer();
        player.setPosition(Vec3d.ofBottomCenter(at));
        return player;
    }

    /** Номер состязания этого вида у народа деревни. */
    static int contestIndex(Settlement village, ContestKind kind) {
        Festival festival = Festivals.of(village.culture()).orElseThrow();
        for (int i = 0; i < festival.contests().size(); i++) {
            if (festival.contests().get(i).kind() == kind) {
                return i;
            }
        }
        throw new IllegalStateException("у народа нет состязания " + kind);
    }

    /** Начать состязание вида в день праздника, утром; не началось — провал. */
    static Match startContest(TestContext context, ServerWorld world, FairGround ground,
                              PlayerEntity player, ContestKind kind) {
        Matches.Verdict verdict = Matches.start(world, player, ground.village(),
                contestIndex(ground.village(), kind), FAIR_DAY, FAIR_MORNING);
        if (verdict != Matches.Verdict.YES) {
            context.throwGameTestException(kind + " не началось: " + verdict);
        }
        return Matches.at(ground.village().id()).orElseThrow();
    }

    /** Отсчёт позади: очки засчитываются только в игре. */
    static void pastTheCountdown(ServerWorld world) {
        for (int tick = 0; tick <= Match.COUNTDOWN; tick++) {
            Matches.tick(world);
        }
    }
}
