package com.villagepax.gametest;

import com.villagepax.block.LaundryBlock;
import com.villagepax.block.ModBlocks;
import com.villagepax.sim.build.Furnishings;
import com.villagepax.block.entity.RopeBlockEntity;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.ColonyFounder;
import com.villagepax.sim.Founding;
import com.villagepax.sim.FoundingOutcome;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import com.villagepax.core.war.WarParty;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Comfort;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Levels;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Raising;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.ModTags;
import com.villagepax.screen.QuestView;
import com.villagepax.screen.QuestNet;
import com.villagepax.sim.work.CraftJob;
import com.villagepax.sim.war.Peace;
import com.villagepax.sim.diplomacy.Alliance;
import com.villagepax.sim.diplomacy.Tribute;
import com.villagepax.sim.war.Allies;
import com.villagepax.sim.war.Raids;
import com.villagepax.sim.build.Roads;
import com.villagepax.sim.work.Needs;
import com.villagepax.core.trade.TradeTable;
import net.minecraft.inventory.SimpleInventory;
import com.villagepax.sim.Standing;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.trade.Trading;
import com.villagepax.item.ModItems;
import com.villagepax.sim.Villages;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.Housing;
import com.villagepax.screen.Advice;
import com.villagepax.screen.BuildOrders;
import com.villagepax.screen.GhostPlan;
import com.villagepax.screen.TownHallConsole;
import com.villagepax.screen.TownHallView;
import com.villagepax.sim.work.Assignments;
import com.villagepax.sim.work.WorkContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Проходимость: входы, ступени, склоны и ходьба настоящего жителя.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class PassabilityTests extends GameTestSupport {

    // --- проходимость ---

    /**
     * Деревня не лезет на скалу: место выбирается там, куда можно дойти.
     * <p>
     * Написано по настоящей деревне из игры заказчика: дом и ферма встали
     * <b>на восемнадцать блоков выше</b> ратуши в десяти шагах от неё.
     * Стройка встала на середине, потому что билдер туда не добирался,
     * а игрок сказал: «строится высоко и не пройти».
     * <p>
     * Проверяется само правило, а не расстановка: правило — это одна
     * дробь, и ошибиться в ней можно молча, а поймать за руку расстановку
     * нельзя без настоящего рельефа.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void villageDoesNotClimbCliffs(TestContext context) {
        BlockPos centre = new BlockPos(0, 69, 0);

        if (Raising.isWalkableFrom(centre, new BlockPos(7, 87, -7))) {
            context.throwGameTestException("Полка на восемнадцать блоков выше в десяти шагах "
                    + "считается годной — это та самая деревня на скале");
        }
        if (!Raising.isWalkableFrom(centre, new BlockPos(7, 71, 0))) {
            context.throwGameTestException("Холмик в два блока у самой ратуши объявлен "
                    + "непроходимым: так деревню не построить нигде");
        }
        if (!Raising.isWalkableFrom(centre, new BlockPos(35, 78, 0))) {
            context.throwGameTestException("Пологий склон — блок подъёма на три шага — "
                    + "должен считаться проходимым");
        }
        if (Raising.isWalkableFrom(centre, new BlockPos(35, 88, 0))) {
            context.throwGameTestException("Подъём круче одного блока на три шага "
                    + "проходимым не считается");
        }

        context.complete();
    }

    /**
     * Готовый дом стоит на земле, а не на сваях из воздуха.
     * <p>
     * Схема кладётся от своего пола, и на склоне у дома с одной стороны
     * оставалась пустота: ни зайти, ни подойти. Теперь билдер подводит
     * опору — из излишков и не глубже склона.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void finishedBuildingStandsOnTheGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 2, 6));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            // Земля под зданием — но с провалом под одним углом: ровно то,
            // что бывает на склоне.
            Vec3i size = schematic.size();
            for (int dx = 0; dx < size.getX(); dx++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    BlockPos under = anchor.add(dx, -1, dz);
                    boolean hole = dx >= size.getX() - 2;
                    world.setBlockState(under, hole ? Blocks.AIR.getDefaultState()
                            : Blocks.STONE.getDefaultState());
                    floor.add(under);
                    if (hole) {
                        BlockPos bottom = under.down(2);
                        world.setBlockState(bottom, Blocks.STONE.getDefaultState());
                        floor.add(bottom);
                    }
                }
            }

            // Вдвое больше плана: подсыпка идёт только из излишков.
            stockFor(world, colony, schematic);
            stockFor(world, colony, schematic);

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не достроилось");
                return;
            }

            int propped = 0;
            for (int dx = size.getX() - 2; dx < size.getX(); dx++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    if (!world.getBlockState(anchor.add(dx, -1, dz)).isAir()) {
                        propped++;
                    }
                }
            }
            if (propped == 0) {
                context.throwGameTestException("Под домом всё ещё пусто: он висит на воздухе");
            }
        } finally {
            demolish(world, site, schematic);
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Улица спускается с уступа ступенью, а не обрывается на нём.
     * <p>
     * До этой правки дорожка шла только по существующей земле и на любом
     * уступе выше двух блоков просто кончалась: дом на полке оставался
     * без подхода. Игрок сказал про это «не пройти ни к зданиям, ни
     * к фермам».
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "playable")
    public void streetStepsDownALedge(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 1, 2));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(10, 4, 2));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            // Нижняя площадка у ратуши и верхняя полка под домом: между
            // ними уступ в три блока — ровно тот, на котором улица рвалась.
            for (int x = 0; x <= 18; x++) {
                for (int z = 0; z <= 10; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, x < 8 ? 0 : 3, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    ground.add(at);
                }
            }
            stockFor(world, colony, schematic);
            stockFor(world, colony, schematic);
            BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);

            List<BlockPos> route = Roads.route(world, colony, site);
            if (route.isEmpty()) {
                context.throwGameTestException("Улицы нет вовсе");
                return;
            }

            int drops = 0;
            for (int step = 1; step < route.size(); step++) {
                int fall = route.get(step - 1).getY() - route.get(step).getY();
                if (fall > 1) {
                    context.throwGameTestException("Улица падает на " + fall
                            + " блока разом: по такой лестнице не спуститься");
                }
                if (fall == 1) {
                    drops++;
                }
            }
            if (drops == 0) {
                context.throwGameTestException("Уступ в три блока улица не заметила: "
                        + "она идёт по одной высоте и обрывается");
            }
        } finally {
            demolish(world, site, schematic);
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Опустевшая колония оживает, а не остаётся руиной навсегда.
     * <p>
     * Найдено в сохранениях заказчика: колонии с <b>нулём жителей</b>
     * и вечно недостроенным домом. Умерли все — кровать строить стало
     * некому, а без кровати никто не приходил. Выхода не было ни одного,
     * и игрок сказал про это «строить здания не могу».
     * <p>
     * Теперь первому кровать не нужна — он ночует в ратуше, как и самый
     * первый житель при основании. А второму нужна: иначе колония росла бы
     * в чистом поле, и дома были бы не нужны вовсе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void desertedColonyComesBackToLife(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));

        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Пустая", hall);
        manager.add(colony);

        try {
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 16));

            Citizen first = Housing.welcomeNewcomer(world, colony, new java.util.Random(7))
                    .orElse(null);
            if (first == null) {
                context.throwGameTestException("В пустую колонию с едой никто не пришёл — "
                        + "это тупик без выхода");
                return;
            }
            if (first.profession().isEmpty()) {
                context.throwGameTestException("Пришедший без ремесла: строить снова некому");
            }

            // А второму кровать уже нужна: домов в колонии нет.
            if (Housing.welcomeNewcomer(world, colony, new java.util.Random(7)).isPresent()) {
                context.throwGameTestException("Второй пришёл в колонию без кроватей — "
                        + "тогда дома не нужны вовсе");
            }
        } finally {
            world.getEntitiesByClass(CitizenEntity.class,
                            new net.minecraft.util.math.Box(hall).expand(16), any -> true)
                    .forEach(CitizenEntity::discard);
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Колония без строителя говорит об этом, а не молчит.
     * <p>
     * Мод умел жаловаться только устами самого билдера — «не хватает
     * камня». Если билдера нет, жаловаться было некому, и игрок сидел
     * над недостроенным домом без единого объяснения. Это и есть его
     * «строить здания не могу», вид второй.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "playable")
    public void colonyWithoutABuilderSaysSo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, context.getAbsolutePos(new BlockPos(8, 2, 2)),
                BlockRotation.NONE);

        try {
            if (BuilderJob.nobodyBuilds(colony)) {
                context.throwGameTestException("Строитель на месте, а мод считает, "
                        + "что строить некому");
            }

            // Сняли ремесло — и теперь стройка действительно брошена.
            colony.citizens().forEach(citizen -> citizen.setProfession(null));
            if (!BuilderJob.nobodyBuilds(colony)) {
                context.throwGameTestException("Строителя нет, стройка стоит, "
                        + "а мод молчит — ровно на это и жаловался игрок");
            }

            // А без стройки и жаловаться не на что.
            site.setProgress(BuildProgress.DONE);
            if (BuilderJob.nobodyBuilds(colony)) {
                context.throwGameTestException("Всё достроено, а мод всё равно зовёт строителя");
            }
        } finally {
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Колония живёт неделю сама: растёт, кормится и не вымирает.
     * <p>
     * Написано после разбора сохранений заказчика, где нашлись колонии
     * с <b>нулём жителей</b> и вечно недостроенным домом. Каждая отдельная
     * механика — еда, кровати, приток, ферма — была проверена и работала;
     * не был проверен только <b>ход времени в целом</b>, а рушится именно он.
     * <p>
     * Это не проверка одного правила, а лакмус: семь суточных смен подряд
     * с работой фермера между ними. Если хоть одно звено цепи «поле →
     * склад → еда → новый житель» порвётся, колония не вырастет — и здесь
     * это будет видно сразу, а не через неделю у игрока.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "week", tickLimit = 400)
    public void colonyLivesAWeekOnItsOwn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(0, 8, 0));
        BlockPos farmAt = context.getAbsolutePos(new BlockPos(0, 8, 8));

        BlockPos secondAt = context.getAbsolutePos(new BlockPos(10, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, houseAt, HOUSE_TYPE, BlockRotation.NONE);
        Building second = plan(colony, secondAt, HOUSE_TYPE, BlockRotation.NONE);
        Building farm = plan(colony, farmAt, FARM_TYPE, BlockRotation.NONE);

        try {
            // Дом и ферма уже стоят: проверяется жизнь колонии, а не стройка.
            stockFor(world, colony, housePlan);
            if (BuildJob.advance(world, manager, colony.id(), house.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не встал: проверять неделю не на чем");
                return;
            }
            // Второй дом — чтобы колонии было куда расти: в одном доме
            // две кровати, а жителей и так двое.
            stockFor(world, colony, housePlan);
            if (BuildJob.advance(world, manager, colony.id(), second.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Второй дом не встал");
                return;
            }

            stockFor(world, colony, farmPlan);
            BuildJob.Outcome raised = BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);
            if (raised != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ферма не встала: " + raised + ", шаг "
                        + farm.nextStep() + " из " + farmPlan.plan().steps().size()
                        + ", не хватает " + Materials.shortfall(farmPlan, farm,
                        farmPlan.plan().steps().size())
                        + ", на складе " + Warehouse.of(world, colony).tally().contents());
                return;
            }

            // Начальный запас еды — как у игрока, который принёс мешок
            // моркови и ушёл по делам.
            Warehouse.of(world, colony).add(new ItemStack(Items.CARROT, 8));
            Housing.assignBeds(world, colony);
            Workplaces.assign(world, colony);

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, farmAt.up());
            // Телом обзаводятся все: у кого его нет, тот не ест и не работает.
            for (Citizen citizen : colony.citizens()) {
                if (bodyOf(world, colony, citizen) == null) {
                    citizen.setPosition(Vec3d.ofBottomCenter(hall.up()));
                    CitizenSpawner.spawnBody(world, colony, citizen);
                }
            }
            Workplaces.assign(world, colony);
            int before = colony.population();

            for (int day = 0; day < 7; day++) {
                // Поле поспевает к утру: солнце в проверке не светит.
                for (BlockPos plot : FarmJob.plots(farm)) {
                    if (world.getBlockState(plot).getBlock() instanceof CropBlock crop) {
                        world.setBlockState(plot, crop.withAge(crop.getMaxAge()));
                    }
                }
                runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

                // Обедают все: житель ест решением, а решение бывает
                // только у того, кого тикают. Без этого колония голодает
                // при полном складе — и первая же неделя это показала.
                for (Citizen citizen : List.copyOf(colony.citizens())) {
                    if (bodyOf(world, colony, citizen) != null) {
                        runWork(world, manager, colony, citizen, 3, Schedule.MEAL);
                    }
                }
                Needs.newDay(world, manager, colony);
            }

            if (colony.population() <= before) {
                context.throwGameTestException("За неделю в колонию с кроватями, полем "
                        + "и едой никто не пришёл: жителей было " + before + ", стало "
                        + colony.population());
            }
            if (!Warehouse.of(world, colony).hasAny(ModTags.CITIZEN_FOOD)) {
                context.throwGameTestException("Склад пуст за неделю при работающей ферме: "
                        + "колония кормиться сама не умеет");
            }
            // Один голодный день за неделю — не беда: суточная убыль
            // списывается раньше обеда, и житель встречает утро голодным.
            // А вот три подряд означают, что цепь «поле — склад — еда»
            // где-то порвалась: после четырёх житель жалуется, после
            // шести уходит насовсем.
            for (Citizen citizen : colony.citizens()) {
                if (citizen.discontent() >= 3) {
                    context.throwGameTestException("Житель " + citizen.fullName()
                            + " голодает при работающей ферме: недовольство "
                            + citizen.discontent() + ", на складе "
                            + Warehouse.of(world, colony).tally().contents());
                }
            }
        } finally {
            discardBodies(world, colony);
            demolish(world, house, housePlan);
            demolish(world, second, housePlan);
            demolish(world, farm, farmPlan);
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Колония, которой никто не видит, не голодает.
     * <p>
     * <b>Это и было причиной вымерших колоний заказчика.</b> Сутки шли
     * везде, включая незагруженные чанки: сытость убывала каждый день,
     * а поесть житель может только решением, решение бывает только
     * у тела, а тела в выгруженном чанке нет. Игрок уходил исследовать
     * мир на неделю и возвращался к пустой колонии, не сделав ничего
     * плохого — и нашёл это не он, а проверка «живёт ли колония неделю»,
     * когда у неё за ту же неделю жителей стало меньше.
     * <p>
     * Правило парное к несущему правилу мода: нет тела — нет работы,
     * нет мира — нет суток.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "week")
    public void unseenColonyDoesNotStarve(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        long today = Schedule.dayOf(world.getTimeOfDay());

        // Одно поселение здесь, второе — за сто тысяч блоков, где мира нет.
        BlockPos here = context.getAbsolutePos(new BlockPos(2, 2, 2));
        Settlement near = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Рядом", here);
        Settlement far = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Далёкая",
                new BlockPos(150_000, 64, 150_000));

        Citizen watched = evenNewborn("Видимый", "", NORMAN, Gender.MALE);
        Citizen forgotten = evenNewborn("Забытый", "", NORMAN, Gender.MALE);
        watched.setSaturation(30);
        forgotten.setSaturation(30);
        near.addCitizen(watched);
        far.addCitizen(forgotten);

        manager.add(near);
        manager.add(far);

        try {
            // «Вчера видели»: иначе смена суток не наступит вовсе.
            near.setLastDay(today - 1);
            far.setLastDay(today - 1);

            WorkTicker.tick(world);

            if (forgotten.saturation() != 30) {
                context.throwGameTestException("У забытой колонии убыла сытость: "
                        + forgotten.saturation() + " вместо 30. Игрок ушёл за горизонт — "
                        + "и вернулся к пустой колонии");
            }
            if (watched.saturation() >= 30) {
                context.throwGameTestException("У колонии под боком сутки не прошли: "
                        + "сытость " + watched.saturation()
                        + ". Тогда колония не голодает никогда, и еда не нужна вовсе");
            }
            // День забытой колонии засчитан — иначе вернувшийся игрок
            // получил бы голод задним числом за всю неделю разом.
            if (far.lastDay() != today) {
                context.throwGameTestException("Забытая колония осталась во вчера: "
                        + far.lastDay() + ". Тогда голод придёт задним числом");
            }
        } finally {
            discardBodies(world, near);
            manager.remove(near.id());
            manager.remove(far.id());
            world.setBlockState(here, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Пульт говорит, что делать дальше, и говорит самое срочное.
     * <p>
     * Главная жалоба на моды этого жанра — и своя, слово в слово:
     * непонятен не механизм, а следующий шаг. Пульт показывает десяток
     * правдивых чисел, и ни одно не отвечает на единственный вопрос
     * новичка. Проверяется лестница срочности: пустая колония важнее
     * голода, голод важнее строителя, строитель важнее кроватей.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void consoleSaysWhatToDoNext(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));

        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Совет", hall);
        manager.add(colony);

        try {
            // Отряд у ворот важнее даже пустой колонии: у этой беды есть
            // срок, а у остальных — нет.
            WarParty band = new WarParty(UUID.randomUUID(), UUID.randomUUID(), NORMAN,
                    hall, 2, 1L, 2L);
            colony.besiege(band, 0L);
            if (!"villagepax.advice.under_siege".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Отряд у ворот не назван первой бедой: "
                        + Advice.nextStep(world, colony));
            }
            colony.liftSiege();

            // Пусто — и это важнее всего остального.
            if (!"villagepax.advice.deserted".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Пустая колония не названа первой бедой: "
                        + Advice.nextStep(world, colony));
            }

            Citizen builder = evenNewborn("Rollo", "", NORMAN, Gender.MALE);
            builder.setProfession(BuildJob.BUILDER);
            colony.addCitizen(builder);

            // Жители есть, еды нет.
            if (!"villagepax.advice.no_food".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Голод не назван: "
                        + Advice.nextStep(world, colony));
            }

            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 8));

            // Еда есть, строителя нет.
            builder.setProfession(null);
            if (!"villagepax.advice.no_builder".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Отсутствие строителя не названо: "
                        + Advice.nextStep(world, colony));
            }

            // Строитель есть, а спать негде: дом важнее поля, потому что
            // без кровати колония не вырастет вовсе, а еду пока носит игрок.
            builder.setProfession(BuildJob.BUILDER);
            if (!"villagepax.advice.no_beds".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Совет поставить дом не дан: "
                        + Advice.nextStep(world, colony));
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Откуп уводит отряд, но не возвращает доверия.
     * <p>
     * Главная проверка всей задачи, и вторая её половина важнее первой.
     * Что монета уводит отряд — это удобство; что <b>доверие после откупа
     * то же самое</b> — это решение по игре. Кошелёк покупает время, а не
     * дружбу: иначе богатый игрок отменял бы всю дипломатию одним сундуком
     * золота, и ни подарки, ни квесты больше ничего не значили бы.
     * <p>
     * Хвост проверки про то же: через десять дней, когда тишина кончится,
     * отряд выходит снова — потому что обиду никто не отменял.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "peace")
    public void paidPeaceSendsTheBandHomeButBuysNoTrust(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 2, 1));
        BlockPos centre = context.getAbsolutePos(new BlockPos(16, 2, 16));
        List<BlockPos> floor = new ArrayList<>();

        // Ратуша деревни — это её кошель: плату кладут туда же, куда
        // выручку с торга.
        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(village);
        manager.add(colony);

        WarParty party = null;
        try {
            for (int x = 2; x <= 30; x++) {
                for (int z = 2; z <= 30; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            manager.update(village.id(), state -> state.addReputation(player, -60));
            Raids.sendIfDue(world, manager, village, 10L);
            Raids.watch(world, manager, 11L);

            party = manager.byId(colony.id()).orElseThrow().siege().orElse(null);
            if (party == null) {
                context.throwGameTestException("Отряд не вышел: мириться не с кем");
                return;
            }
            if (Raids.bodiesOf(world, party).isEmpty()) {
                context.throwGameTestException("Отряд не встал телами: уводить некого");
                return;
            }

            SimpleInventory purse = new SimpleInventory(36);
            purse.addStack(new ItemStack(ModItems.GOLD_COIN, 4));
            int carried = Coins.total(purse);
            int price = Peace.price(-60);

            Peace.Outcome outcome = Peace.buy(world, manager, village, player, purse, 11L,
                    left -> purse.addStack(left));
            if (!outcome.bought()) {
                context.throwGameTestException("Монету не взяли: " + outcome.verdict()
                        + ", просили " + price + ", при себе " + carried);
                return;
            }
            if (Coins.total(purse) != carried - price) {
                context.throwGameTestException("С игрока взяли " + (carried - Coins.total(purse))
                        + " вместо " + price);
            }
            if (Coins.total(Warehouse.of(world, village).coins()) != price) {
                context.throwGameTestException("В кошель деревни легло "
                        + Coins.total(Warehouse.of(world, village).coins()) + " вместо " + price);
            }

            Settlement paid = manager.byId(village.id()).orElseThrow();
            if (paid.truceDaysLeft(11L) != Peace.TRUCE_DAYS) {
                context.throwGameTestException("Куплено " + paid.truceDaysLeft(11L)
                        + " дней тишины вместо " + Peace.TRUCE_DAYS);
            }
            if (paid.reputationOf(player) != -60) {
                context.throwGameTestException("Откуп поднял доверие до "
                        + paid.reputationOf(player) + ": деньгами покупается тишина, а не дружба");
            }

            // Отряд уходит сейчас же, а не достаивает свой срок.
            if (!Raids.callOff(world, manager, player, village.id())) {
                context.throwGameTestException("Отряд не увели: мир куплен, а бойцы стоят");
            }
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Осада не снята после откупа");
            }
            if (!Raids.bodiesOf(world, party).isEmpty()) {
                context.throwGameTestException("Тела остались стоять после уплаченного мира");
            }

            // Пока идёт перемирие — не приходят, хотя обида на месте.
            Raids.sendIfDue(world, manager, village, 11L + Raids.COOLDOWN_DAYS);
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Набег во время оплаченного перемирия");
            }

            // А когда кончится — приходят снова: обиду не покупали.
            Raids.sendIfDue(world, manager, village, 11L + Peace.TRUCE_DAYS);
            if (manager.byId(colony.id()).orElseThrow().siege().isEmpty()) {
                context.throwGameTestException("Перемирие кончилось, а отряда нет: "
                        + "выходит, откуп отменил обиду насовсем");
            }
        } finally {
            manager.byId(colony.id()).flatMap(Settlement::siege)
                    .ifPresent(one -> Raids.bodiesOf(world, one).forEach(CitizenEntity::discard));
            if (party != null) {
                Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            }
            manager.remove(village.id());
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Отбитый набег покупает дни тишины — и ни очка доверия.
     * <p>
     * Ответ на «я отбился, и что изменилось». До сих пор — ничего: отряды
     * приходили каждые пять дней, сколько бы их ни полегло, и оборона
     * не значила <b>ровно ничего</b>. Теперь каждый павший стоит деревне
     * дней траура, потому что мёртвые не ходят в походы.
     * <p>
     * Доверие при этом не меняется, и это то же решение, что у откупа:
     * убитый боец деревню не примиряет. Кровью, как и монетой, покупается
     * только время.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "peace")
    public void fallenFightersBuyQuietDays(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 2, 1));
        BlockPos centre = context.getAbsolutePos(new BlockPos(16, 2, 16));
        List<BlockPos> floor = new ArrayList<>();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(village);
        manager.add(colony);

        WarParty party = null;
        try {
            for (int x = 2; x <= 30; x++) {
                for (int z = 2; z <= 30; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            manager.update(village.id(), state -> state.addReputation(player, -60));
            Raids.sendIfDue(world, manager, village, 10L);
            Raids.watch(world, manager, 11L);

            party = manager.byId(colony.id()).orElseThrow().siege().orElse(null);
            if (party == null) {
                context.throwGameTestException("Отряд не вышел: бить некого");
                return;
            }
            List<CitizenEntity> band = Raids.bodiesOf(world, party);
            if (band.size() != party.fighters()) {
                context.throwGameTestException("Тел " + band.size() + " на "
                        + party.fighters() + " бойцов");
                return;
            }

            int fighters = party.fighters();
            for (CitizenEntity fighter : band) {
                Raids.fell(world, fighter, 11L);
            }

            Settlement mourning = manager.byId(village.id()).orElseThrow();
            int quiet = fighters * Peace.MOURNING_DAYS;
            if (mourning.truceDaysLeft(11L) != quiet) {
                context.throwGameTestException("За " + fighters + " павших деревня молчит "
                        + mourning.truceDaysLeft(11L) + " дней вместо " + quiet);
            }
            if (mourning.reputationOf(player) != -60) {
                context.throwGameTestException("Убитые бойцы изменили доверие до "
                        + mourning.reputationOf(player) + ": кровь не мирит");
            }

            // Пять дней остывания прошло, а траур — нет.
            Raids.sendIfDue(world, manager, village, 11L + Raids.COOLDOWN_DAYS);
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Деревня, потерявшая " + fighters
                        + " бойцов, вышла снова через " + Raids.COOLDOWN_DAYS + " дней: "
                        + "оборона не значит ничего");
            }

            // А когда отгоревали — выходят: обида-то осталась.
            Raids.sendIfDue(world, manager, village, 11L + quiet);
            if (manager.byId(colony.id()).orElseThrow().siege().isEmpty()) {
                context.throwGameTestException("Траур кончился, а отряда нет: "
                        + "выходит, отбитый набег примирил деревню навсегда");
            }
        } finally {
            manager.byId(colony.id()).flatMap(Settlement::siege)
                    .ifPresent(one -> Raids.bodiesOf(world, one).forEach(CitizenEntity::discard));
            if (party != null) {
                Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            }
            manager.remove(village.id());
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Боец отряда, о котором поселение забыло, уходит сам.
     * <p>
     * Обычно тела уводит сам набег — но запись об осаде может исчезнуть
     * помимо него: старое сохранение, выкупленный мир, правка данных.
     * Без этой проверки вооружённые куклы остались бы стоять у колонии
     * навсегда, и единственным способом от них избавиться было бы
     * перебить их всех.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "peace", tickLimit = 100)
    public void forgottenFighterLeavesOnItsOwn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(4, 2, 4));
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }

        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Моя", centre);
        manager.add(colony);

        CitizenEntity ghost = CitizenSpawner.spawnPuppet(world,
                context.getAbsolutePos(new BlockPos(6, 2, 6)));
        if (ghost == null) {
            context.throwGameTestException("Кукла не встала: проверять нечего");
            return;
        }
        // Отряда с таким опознавателем у колонии нет и не было.
        ghost.linkRaid(colony.id(), UUID.randomUUID());

        context.runAtTick(40, () -> {
            try {
                if (!ghost.isRemoved()) {
                    context.throwGameTestException("Боец забытого отряда всё стоит: "
                            + "такие куклы остаются в мире навсегда");
                }
            } finally {
                ghost.discard();
                manager.remove(colony.id());
                for (BlockPos at : floor) {
                    world.setBlockState(at, Blocks.AIR.getDefaultState());
                }
                world.setBlockState(centre, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /**
     * Деревня за горизонтом не затягивает в память свои чанки.
     * <p>
     * Правило мода «спрашивать блоки в незагруженном чанке нельзя» до сих
     * пор соблюдалось везде, кроме одного места — склада. А суточная смена
     * зовёт склад у <b>каждого</b> поселения мира, включая те, до которых
     * игрок за всю игру не дошёл: каждая деревня раз в игровой день
     * заставляла мир загрузить свои чанки, и все разом в один тик.
     * <p>
     * Проверяется не «быстро ли», а <b>случилось ли</b>: чанк, которого
     * не было в памяти, после суточной смены не должен там оказаться.
     * Это тот редкий случай, когда производительность проверяется точным
     * условием, а не секундомером.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void farVillageDoesNotDragItsChunksIn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        // Далеко за пределами прогона: чанк туда никто не загружал.
        BlockPos away = new BlockPos(220_000, 64, 220_000);
        ChunkPos chunk = new ChunkPos(away);
        if (world.isChunkLoaded(chunk.x, chunk.z)) {
            context.throwGameTestException("Чанк за 220 тысяч блоков уже загружен: "
                    + "проверять нечего");
            return;
        }

        Settlement far = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Дальняя", away);
        manager.add(far);

        try {
            Villages.newDay(world, manager, far);
            if (world.isChunkLoaded(chunk.x, chunk.z)) {
                context.throwGameTestException("Суточная смена загрузила чанк дальней "
                        + "деревни: так каждая деревня мира тянет в память свои чанки "
                        + "раз в игровой день, все разом в один тик");
            }

            // И склад её тоже не дотягивается — того же правила ради.
            if (Warehouse.of(world, far).containerCount() != 0) {
                context.throwGameTestException("У невидимой деревни нашёлся склад");
            }
            if (world.isChunkLoaded(chunk.x, chunk.z)) {
                context.throwGameTestException("Склад дотянулся до незагруженного чанка "
                        + "и заставил мир его поднять");
            }
        } finally {
            manager.remove(far.id());
        }

        context.complete();
    }

    /**
     * Житель переодевается, когда меняет ремесло.
     * <p>
     * Облик едет на клиент отслеживаемым полем, и вся ценность затеи —
     * в том, что он <b>не застывает</b>: игрок даёт человеку ремесло
     * через пульт и должен через секунду увидеть на нём фартук, а не
     * ждать перезахода в мир.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "looks", tickLimit = 120)
    public void citizenChangesClothesWithTheCraft(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos stands = context.getAbsolutePos(new BlockPos(4, 2, 4));

        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Облик", hall);
        manager.add(colony);

        Citizen citizen = evenNewborn("Adeline", "la Fermiere", NORMAN, Gender.FEMALE);
        citizen.setPosition(Vec3d.ofBottomCenter(stands));
        colony.addCitizen(citizen);
        CitizenEntity body = CitizenSpawner.spawnBody(world, colony, citizen);
        if (body == null) {
            manager.remove(colony.id());
            context.throwGameTestException("Тело не встало: смотреть не на кого");
            return;
        }

        if (!body.look().endsWith("norman/female.png")) {
            cleanUpLooks(world, manager, colony, body, hall);
            context.throwGameTestException("Без ремесла облик "
                    + body.look() + ", а ждали будничный норманнский женский");
            return;
        }

        citizen.setProfession(FarmJob.FARMER);

        context.runAtTick(40, () -> {
            try {
                if (body.look() == null || !body.look().endsWith("norman/female_farmer.png")) {
                    context.throwGameTestException("Дали ремесло пахаря, а на человеке "
                            + body.look() + ": убрано=" + body.isRemoved()
                            + ", запись=" + manager.byId(colony.id())
                                    .flatMap(state -> state.citizen(citizen.id()))
                                    .flatMap(Citizen::profession)
                            + ", тел=" + world.getEntitiesByClass(CitizenEntity.class,
                                    new Box(hall).expand(16), alive -> true).size());
                }
            } finally {
                cleanUpLooks(world, manager, colony, body, hall);
            }
            context.complete();
        });
    }

    /**
     * Житель там, где стоит его тело, а не там, где оно появилось.
     * <p>
     * Написано по живой дыре: близость к выдающему при сдаче квеста,
     * подарке и торге сверялась с местом в <b>записи</b>, а оно пишется,
     * только когда тело исчезает. Купец, ушедший от центра новой деревни
     * к своему ларьку, отказывал игроку «слишком далеко», стоя с ним
     * нос к носу. Проверка разводит место появления и место тела дальше
     * восьми шагов, на которых разговор ещё возможен, — и спрашивает
     * тот же вызов, что и сервер при нажатии кнопки.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "whereabouts", tickLimit = 60)
    public void aCitizenIsWhereHisBodyStands(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(3, 2, 3));
        BlockPos appeared = context.getAbsolutePos(new BlockPos(0, 2, 0));
        BlockPos walked = context.getAbsolutePos(new BlockPos(7, 2, 7));

        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Где", hall);
        manager.add(village);

        Citizen merchant = evenNewborn("Gautier", "le Marchand", NORMAN, Gender.MALE);
        merchant.setPosition(Vec3d.ofBottomCenter(appeared));
        village.addCitizen(merchant);

        // Без тела отвечает запись: иначе о жителе в незагруженном чанке
        // не знал бы никто.
        Vec3d unseen = CitizenSpawner.whereNow(world, merchant).orElse(null);
        if (unseen == null || unseen.squaredDistanceTo(Vec3d.ofBottomCenter(appeared)) > 0.01) {
            manager.remove(village.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            context.throwGameTestException("Без тела житель должен быть там, где записан, а он "
                    + unseen);
            return;
        }

        CitizenEntity body = CitizenSpawner.spawnBody(world, village, merchant);
        if (body == null) {
            manager.remove(village.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            context.throwGameTestException("Тело не встало: искать некого");
            return;
        }

        context.runAtTick(5, () -> body.refreshPositionAndAngles(walked.getX() + 0.5,
                walked.getY(), walked.getZ() + 0.5, 0.0f, 0.0f));
        context.runAtTick(6, () -> {
            try {
                Vec3d found = CitizenSpawner.whereNow(world, merchant).orElse(null);
                double off = found == null ? Double.MAX_VALUE
                        : found.squaredDistanceTo(body.getPos());
                if (off > 0.01) {
                    context.throwGameTestException("Тело стоит у " + body.getPos()
                            + ", а житель найден у " + found
                            + " — там, где появился, а не там, где он есть");
                }
                if (Vec3d.ofBottomCenter(appeared).squaredDistanceTo(body.getPos()) <= 8.0 * 8.0) {
                    context.throwGameTestException("Проверка ничего не доказывает: тело всего в "
                            + Math.sqrt(Vec3d.ofBottomCenter(appeared)
                                    .squaredDistanceTo(body.getPos()))
                            + " от места появления — разговор был бы возможен и так");
                }
            } finally {
                body.discard();
                manager.remove(village.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /**
     * Пивоварня открывается деревней — и варит то, чего не добыть киркой.
     * <p>
     * Вся задача одной проверкой, потому что это одна цепь, и рвётся она
     * в любом звене. Заказчик сказал: играть скучно, зацепиться не за что.
     * Зацепиться теперь есть за что ровно потому, что цепь целая: ступень
     * запирает ремесло → ратуша второго уровня открывает его → мастерская
     * даёт работу → работа даёт эль, которого нигде больше нет.
     * <p>
     * Каждое звено проверяется отдельным утверждением, и каждое из них
     * когда-нибудь спасёт: заперто ли до срока, открылось ли вовремя,
     * ушло ли зерно, появился ли эль.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth", tickLimit = 400)
    public void breweryOpensWithTheVillageAndBrewsAle(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, BREWERY_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos shopAt = context.getAbsolutePos(new BlockPos(0, 8, 6));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen brewer = null;

        try {
            // --- заперто, пока колония хутор ---
            if (!(BuildOrders.check(colony, BREWERY_SCHEMATIC, shopAt, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Locked)) {
                context.throwGameTestException("Пивоварню дали разметить на хуторе: "
                        + "ступень ничего не значит");
                return;
            }

            Citizen worker = evenNewborn("Ansel", "le Brasseur", NORMAN, Gender.MALE);
            colony.addCitizen(worker);
            if (Assignments.set(world, manager, colony, worker.id(),
                    Optional.of(CraftJob.BREWER)) != Assignments.Result.LOCKED) {
                context.throwGameTestException("Пивовара наняли на хуторе: "
                        + "ремесло не заперто ступенью");
                return;
            }

            // --- деревня открывает и то и другое ---
            colony.setLevel(SettlementLevel.VILLAGE);
            if (!(BuildOrders.check(colony, BREWERY_SCHEMATIC, shopAt, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Placed)) {
                context.throwGameTestException("Колония стала деревней, а пивоварня "
                        + "всё заперта: ступень не открывает обещанного");
                return;
            }

            Building shop = plan(colony, shopAt, BREWERY_TYPE, BlockRotation.NONE);
            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), shop.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Пивоварня не встала: варить негде");
                return;
            }

            brewer = worker;
            if (Assignments.set(world, manager, colony, brewer.id(),
                    Optional.of(CraftJob.BREWER)) != Assignments.Result.DONE) {
                context.throwGameTestException("В деревне пивовара всё ещё не нанять");
                return;
            }
            brewer.setPosition(Vec3d.ofBottomCenter(shopAt.up()));
            CitizenSpawner.spawnBody(world, colony, brewer);
            Workplaces.assign(world, colony);

            // --- работа: зерно в эль ---
            Warehouse before = Warehouse.of(world, colony);
            before.add(new ItemStack(Items.WHEAT, 12));
            int wheat = before.count(Items.WHEAT);

            runWork(world, manager, colony, brewer, 8, Schedule.MORNING_WORK);

            Warehouse after = Warehouse.of(world, colony);
            if (after.count(ModItems.ALE) <= 0) {
                context.throwGameTestException("Пивовар отработал восемь решений "
                        + "и не сварил ничего: эля на складе " + after.count(ModItems.ALE)
                        + ", зерна " + after.count(Items.WHEAT));
            }
            if (after.count(Items.WHEAT) >= wheat) {
                context.throwGameTestException("Эль взялся из воздуха: зерна было "
                        + wheat + ", осталось " + after.count(Items.WHEAT));
            }
            // Восемь решений в один миг — одна варка: котёл не автомат.
            if (after.count(ModItems.ALE) > 1) {
                context.throwGameTestException("Пивовар сварил " + after.count(ModItems.ALE)
                        + " эля в один миг: варка должна занимать время");
            }
        } finally {
            if (brewer != null) {
                discardBodies(world, colony);
            }
            // Здания может и не быть: проверка падает на первом же
            // утверждении, если ворота ступени сняли, — и уборка не имеет
            // права заслонить собой настоящую причину падения.
            colony.buildings().stream()
                    .filter(one -> one.type().equals(BREWERY_TYPE))
                    .findFirst()
                    .ifPresent(one -> demolish(world, one, plan));
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Деревня встречает игрока живым прилавком, а не двумя отказами.
     * <p>
     * Заказчик назвал торговлю среди того, что «криво работает», и разбор
     * кода объяснил почему. Первая встреча была <b>тупиком с обеих
     * сторон</b>: купить игрок не мог (монеты у него ещё нет и взяться
     * ей неоткуда), продать тоже — у самой деревни кошель был пуст,
     * потому что наполнялся только на суточной смене. Оба прилавка
     * серые, мод выглядит сломанным.
     * <p>
     * Проверяется ровно то, что делает игрок в первые минуты: подходит
     * и пробует и то и другое. Обе сделки обязаны пройти на нулевом
     * доверии — на большее в первую встречу ему рассчитывать не на что.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "trade_first", tickLimit = 300)
    public void villageMeetsThePlayerWithAFullStall(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(16, 2, 16));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = 0; x <= 32; x++) {
                for (int z = 0; z <= 32; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала: помеха="
                        + whoBlocks(manager, centre));
                return;
            }

            Warehouse wares = Warehouse.of(world, village);
            if (Trading.purse(wares) <= 0) {
                context.throwGameTestException("У деревни пустой кошель в день знакомства: "
                        + "продать ей нечего и некому");
            }

            // --- игрок покупает: у него монета, у деревни товар ---
            TradeTable.Deal sells = Trading.dealsOn(village, Trading.Side.VILLAGE_SELLS).get(0);
            SimpleInventory hands = new SimpleInventory(36);
            hands.addStack(new ItemStack(ModItems.COIN, 32));

            Trading.Outcome bought = Trading.trade(village, player, hands,
                    Warehouse.of(world, village), Trading.Side.VILLAGE_SELLS, sells,
                    left -> hands.addStack(left));
            if (bought != Trading.Outcome.DONE) {
                context.throwGameTestException("Купить у деревни нельзя в первый же день: "
                        + bought + ", товар " + sells.item()
                        + ", на складе " + Warehouse.of(world, village).count(sells.item()));
            }

            // --- и продаёт: у деревни монета ---
            TradeTable.Deal buys = Trading.dealsOn(village, Trading.Side.VILLAGE_BUYS).get(0);
            hands.addStack(new ItemStack(buys.item(), buys.count() * 2));
            Trading.Outcome sold = Trading.trade(village, player, hands,
                    Warehouse.of(world, village), Trading.Side.VILLAGE_BUYS, buys,
                    left -> hands.addStack(left));
            if (sold != Trading.Outcome.DONE) {
                context.throwGameTestException("Продать деревне нельзя в первый же день: "
                        + sold + ", товар " + buys.item()
                        + ", в кошеле " + Trading.purse(Warehouse.of(world, village)));
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * На поле можно войти с земли, а не только спрыгнуть в него.
     * <p>
     * Жалоба заказчика, повторённая трижды: «который раз не взобраться
     * на ферму, с неё не смогут забрать посев». Здание стоит на цоколе,
     * под цоколь мод подсыпает опору, и порог оказывается на два блока
     * выше земли. Шаг в один блок делают и человек, и ванильный поиск
     * пути; <b>два не делает никто</b>, и поле стоит нетронутым.
     * <p>
     * Проверяется ровно то, чего не хватало: у калитки снаружи обязана
     * быть ступень не ниже чем на блок под порогом. Всё остальное —
     * дело ног.
     * <p>
     * И проверяется это <b>дважды</b>: на недостроенном поле и на готовом.
     * Заказчик попросил, чтобы войти можно было сразу, — а стройка идёт
     * долго, и дом, в который нельзя войти всю стройку, бесполезен ровно
     * так же, как дом, в который нельзя войти совсем.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "porch", tickLimit = 400)
    public void farmCanBeWalkedIntoFromTheGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        // Поле на два блока выше земли вокруг — ровно то, что получается
        // в игре после подсыпки опоры на склоне. Ступени пойдут на запад
        // от калитки, и место под них оставлено внутри площадки: за её
        // краем начинается соседняя проверка.
        BlockPos farmAt = context.getAbsolutePos(new BlockPos(3, 4, 3));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = null;

        try {
            // Земля кладётся только там, где стоит поле и куда ляжет
            // крыльцо. Мир игровых тестов общий, и площадка, расписанная
            // на двадцать блоков вокруг, затирает соседние проверки —
            // на этом я и попался, получив мигание в чужих проверках
            // подвоза.
            // Площадка с запасом по всем сторонам: спуск с крыльца идёт
            // на три-четыре клетки, и край площадки не должен попадать
            // в эти клетки — иначе проверка объявит обрывом свою границу.
            for (int x = -6; x <= 14; x++) {
                for (int z = -6; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 2, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            farm = plan(colony, farmAt, FARM_TYPE, BlockRotation.NONE);
            stockFor(world, colony, plan);

            // Сперва только цоколь и немного стен: заказчик просил, чтобы
            // войти можно было СРАЗУ, а не после крыши. Стройка идёт долго,
            // и всё это время дом с порогом на высоте пояса бесполезен.
            BuildJob.advance(world, manager, colony.id(), farm.id(), 120);
            if (farm.isOperational()) {
                context.throwGameTestException("Поле достроилось за сто двадцать шагов: "
                        + "проверять «вход до крыши» не на чем");
                return;
            }
            for (BlockPos door : Access.entrances(farm, plan)) {
                String trouble = descentTrouble(world, farm, plan, door);
                if (trouble != null) {
                    context.throwGameTestException("Недостроенное поле не пускает: "
                            + trouble);
                }
            }

            if (BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Поле не встало: входить некуда");
                return;
            }

            List<BlockPos> doors = Access.entrances(farm, plan);
            if (doors.isEmpty()) {
                context.throwGameTestException("У поля не нашлось ни калитки, ни двери");
                return;
            }

            for (BlockPos door : doors) {
                // Снаружи — это в сторону от середины поля; у калитки
                // норманнского поля это запад.
                String trouble = descentTrouble(world, farm, plan, door);
                if (trouble != null) {
                    context.throwGameTestException("На готовое поле не войти: " + trouble);
                }
            }
        } finally {
            if (farm != null) {
                demolish(world, farm, plan);
            }
            cleanUpVillage(world, manager, colony, hall, ground);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Обжитой дом поднимает настроение, а голый — нет.
     * <p>
     * Убранство в моде было с самого начала и не значило ничего: фонарь,
     * ковёр и бельё ставились билдером, стояли и ни на что не влияли.
     * Заказчик попросил, чтобы декор что-то делал, и делает он то,
     * чего от него и ждут.
     * <p>
     * Проверяется разницей, а не числом: два сытых жителя одной колонии,
     * у одного дом есть, у другого нет. Разница в настроении и есть уют.
     * Так проверка переживёт любую правку самих прибавок.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "comfort", tickLimit = 400)
    public void cosyHomeLiftsTheMood(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        // Низко и рядом: мир игровых тестов общий, площадки стоят сеткой,
        // и проверка, расписавшаяся на двадцать блоков вокруг, ломает
        // соседей. На этом я и попался — мигали чужие проверки подвоза.
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(2, 1, 2));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, houseAt, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), house.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не встал: уюту неоткуда взяться");
                return;
            }

            int cosy = Comfort.of(world, house);
            if (cosy <= 0) {
                context.throwGameTestException("В достроенном доме с фонарём и ковром "
                        + "уюта " + cosy + ": убранство снова ничего не значит");
                return;
            }

            Citizen homed = evenNewborn("Adeline", "", NORMAN, Gender.FEMALE);
            Citizen homeless = evenNewborn("Rollo", "", NORMAN, Gender.MALE);
            for (Citizen citizen : List.of(homed, homeless)) {
                citizen.setSaturation(30);
                citizen.setHappiness(50);
                colony.addCitizen(citizen);
            }
            homed.setHome(house.id());

            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 16));
            Needs.newDay(world, manager, colony);

            int withHome = homed.happiness() - 50;
            int without = homeless.happiness() - 50;
            if (withHome <= without) {
                context.throwGameTestException("Дом не согрел: с домом прибавка "
                        + withHome + ", без дома " + without);
            }
            if (withHome - without != cosy) {
                context.throwGameTestException("Уют посчитан не тот: разница "
                        + (withHome - without) + ", а дом стоит " + cosy);
            }
        } finally {
            demolish(world, house, plan);
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * На верёвку вешают и с неё снимают — а что попало не вешают.
     * <p>
     * Решение заказчика: «пусть это будет просто верёвка, но на которую
     * можно будет вешать кожаные вещи, кожу и тканевую одежду». Проверка
     * держит три обещания разом: вещь из тега вешается, снимается та,
     * что повесили последней, и больше четырёх на бечеву не лезет.
     * <p>
     * Ещё одно обещание — что сломанная верёвка возвращает повешенное —
     * проверяется тем же ходом: без него игрок теряет вещи молча.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rope")
    public void ropeHoldsWhatYouHangOnIt(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos at = context.getAbsolutePos(new BlockPos(1, 2, 1));

        try {
            world.setBlockState(at, ModBlocks.LAUNDRY.getDefaultState());
            if (!(world.getBlockEntity(at) instanceof RopeBlockEntity rope)) {
                context.throwGameTestException("У верёвки нет блок-сущности: вешать некуда");
                return;
            }

            if (!rope.isEmpty()) {
                context.throwGameTestException("Поставленная руками верёвка пришла не пустой");
            }

            if (!rope.hang(new ItemStack(Items.LEATHER, 3))) {
                context.throwGameTestException("Кожу на верёвку не повесили");
                return;
            }
            if (rope.hung().get(0).getCount() != 1) {
                context.throwGameTestException("На верёвке повисло "
                        + rope.hung().get(0).getCount() + " штук: вешают по одной, "
                        + "иначе на бечеве будет «кожа ×64»");
            }

            rope.hang(new ItemStack(Items.WHITE_WOOL));
            rope.hang(new ItemStack(Items.LEATHER_BOOTS));
            rope.hang(new ItemStack(Items.WHITE_CARPET));
            if (rope.hang(new ItemStack(Items.LEATHER))) {
                context.throwGameTestException("На верёвку влезло пятое: мест у неё "
                        + RopeBlockEntity.SIZE + ", и складом она быть не должна");
            }

            ItemStack taken = rope.takeDown();
            if (!taken.isOf(Items.WHITE_CARPET)) {
                context.throwGameTestException("Сняли не то, что вешали последним: "
                        + taken.getItem());
            }

            // Сломали — повешенное падает наземь, а не пропадает.
            int before = world.getEntitiesByClass(ItemEntity.class,
                    new Box(at).expand(4), alive -> true).size();
            world.breakBlock(at, false);
            int after = world.getEntitiesByClass(ItemEntity.class,
                    new Box(at).expand(4), alive -> true).size();
            if (after - before < 3) {
                context.throwGameTestException("Сломанная верёвка вернула " + (after - before)
                        + " вещей из трёх: игрок теряет их молча");
            }
        } finally {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
            world.getEntitiesByClass(ItemEntity.class, new Box(at).expand(6), alive -> true)
                    .forEach(ItemEntity::discard);
        }

        context.complete();
    }

    /**
     * Строитель вешает бельё сам, а игрок получает верёвку пустой.
     * <p>
     * Пустая бечева посреди деревенского двора выглядит недоделкой,
     * а верёвка, которая сама родит шерсть в руках игрока, — это
     * бесплатная шерсть. Поэтому вешает <b>строитель</b>, и только он.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rope")
    public void theBuilderHangsTheWashingHimself(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos at = context.getAbsolutePos(new BlockPos(3, 2, 3));

        try {
            world.setBlockState(at, ModBlocks.LAUNDRY.getDefaultState());
            Furnishings.stock(world, at, world.getBlockState(at), at.asLong());

            if (!(world.getBlockEntity(at) instanceof RopeBlockEntity rope) || rope.isEmpty()) {
                context.throwGameTestException("Строитель натянул верёвку и ничего не повесил");
                return;
            }
            if (rope.lastHung() != 1) {
                context.throwGameTestException("Во дворе повисло не две вещи, а "
                        + (rope.lastHung() + 1));
            }
        } finally {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
            world.getEntitiesByClass(ItemEntity.class, new Box(at).expand(6), alive -> true)
                    .forEach(ItemEntity::discard);
        }

        context.complete();
    }

    /**
     * Город открывает ткача, а ткач даёт колонии товар на вывоз.
     * <p>
     * Вторая ступень лестницы, и без неё первая висела в пустоте: до сих
     * пор выше «деревни» не открывалось <b>ничего</b>, потому что и самой
     * ратуши третьего уровня в моде не было. Карточка роста звала игрока
     * туда, куда дойти нельзя.
     * <p>
     * Проверяется вся цепь: в деревне ткач заперт, в городе открыт,
     * ткацкая встаёт, шерсть уходит, сукно появляется. И отдельно —
     * что <b>сукно у деревень в цене</b>: ради этого оно и заведено,
     * иначе это просто ещё одна вещь на складе.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "growth", tickLimit = 400)
    public void townOpensTheWeaverAndClothIsWorthSelling(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, WEAVERY_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos shopAt = context.getAbsolutePos(new BlockPos(3, 6, 3));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen weaver = null;

        try {
            colony.setLevel(SettlementLevel.VILLAGE);
            Citizen worker = evenNewborn("Mahaut", "la Tisserande", NORMAN, Gender.FEMALE);
            colony.addCitizen(worker);
            if (Assignments.set(world, manager, colony, worker.id(), Optional.of(WEAVER))
                    != Assignments.Result.LOCKED) {
                context.throwGameTestException("Ткача наняли в деревне: ступень «город» "
                        + "ничего не значит");
                return;
            }
            if (!(BuildOrders.check(colony, WEAVERY_SCHEMATIC, shopAt, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Locked)) {
                context.throwGameTestException("Ткацкую дали разметить в деревне");
                return;
            }

            colony.setLevel(SettlementLevel.TOWN);
            Building shop = plan(colony, shopAt, WEAVERY_TYPE, BlockRotation.NONE);
            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), shop.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ткацкая не встала");
                return;
            }

            weaver = worker;
            if (Assignments.set(world, manager, colony, weaver.id(), Optional.of(WEAVER))
                    != Assignments.Result.DONE) {
                context.throwGameTestException("В городе ткача всё ещё не нанять");
                return;
            }
            weaver.setPosition(Vec3d.ofBottomCenter(shopAt.up()));
            CitizenSpawner.spawnBody(world, colony, weaver);
            Workplaces.assign(world, colony);

            Warehouse.of(world, colony).add(new ItemStack(Items.WHITE_WOOL, 12));
            int wool = Warehouse.of(world, colony).count(Items.WHITE_WOOL);
            runWork(world, manager, colony, weaver, 8, Schedule.MORNING_WORK);

            Warehouse after = Warehouse.of(world, colony);
            if (after.count(ModItems.CLOTH) <= 0) {
                context.throwGameTestException("Ткач отработал восемь решений и не соткал "
                        + "ничего: сукна " + after.count(ModItems.CLOTH)
                        + ", шерсти " + after.count(Items.WHITE_WOOL));
            }
            if (after.count(Items.WHITE_WOOL) >= wool) {
                context.throwGameTestException("Сукно взялось из воздуха: шерсти было "
                        + wool + ", осталось " + after.count(Items.WHITE_WOOL));
            }

            // Ради чего всё: деревни берут сукно, и берут дорого.
            Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Торг",
                    context.getAbsolutePos(new BlockPos(12, 1, 12)));
            manager.add(village);
            try {
                TradeTable.Deal deal = Trading
                        .find(village, Trading.Side.VILLAGE_BUYS, ModItems.CLOTH, 2)
                        .orElse(null);
                if (deal == null) {
                    context.throwGameTestException("Деревня не скупает сукно: колонии "
                            + "нечем торговать, и монета в мир по-прежнему не приходит");
                    return;
                }
                if (deal.price() < 4) {
                    context.throwGameTestException("За сукно дают " + deal.price()
                            + " — это не товар на вывоз, а безделица");
                }
            } finally {
                manager.remove(village.id());
            }
        } finally {
            if (weaver != null) {
                discardBodies(world, colony);
            }
            colony.buildings().stream()
                    .filter(one -> one.type().equals(WEAVERY_TYPE))
                    .findFirst()
                    .ifPresent(one -> demolish(world, one, plan));
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Ратуша растёт до третьего уровня, и колония становится городом.
     * <p>
     * Ступень «город» была обещана пультом и недостижима в мире: схемы
     * ратуши третьего уровня попросту не существовало, и кнопка
     * «Улучшить» отвечала «выше некуда». Проверка держит обещание —
     * схема есть, улучшение проходит, ступень поднимается.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth", tickLimit = 600)
    public void townHallGrowsToTheThirdLevel(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic third = schematic(context, new Identifier("villagepax",
                "norman/town_hall_lvl3"));

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos at = context.getAbsolutePos(new BlockPos(2, 1, 2));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building seat = null;

        try {
            seat = plan(colony, at, TOWN_HALL_TYPE, BlockRotation.NONE);
            seat.setLevel(3);
            seat.restartBuilding();
            stockFor(world, colony, third);
            if (BuildJob.advance(world, manager, colony.id(), seat.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ратуша третьего уровня не встала: шаг "
                        + seat.nextStep() + " из " + third.plan().steps().size());
                return;
            }

            Levels.refresh(colony);
            if (colony.level() != SettlementLevel.TOWN) {
                context.throwGameTestException("Ратуша третьего уровня стоит, а колония "
                        + "всё ещё " + colony.level().id());
            }
        } finally {
            if (seat != null) {
                demolish(world, seat, third);
            }
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Житель доходит внутрь дома и внутрь поля — своими ногами.
     * <p>
     * Жалоба заказчика, повторённая в четвёртый раз: «жители не могут
     * попасть как в дом, так и на ферму». Три прошлых починки мерили
     * <b>высоту ступени</b> — и мерили верно, а войти всё равно нельзя.
     * Значит, мерили не то.
     * <p>
     * Эта проверка не мерит ничего. Она спрашивает <b>ванильный поиск
     * пути</b> — тот самый, которым ходят жители: построй дорогу отсюда
     * вон туда. Не построил — войти нельзя, и неважно, что там со
     * ступенями. Ровно этот вопрос задаёт себе житель каждую секунду,
     * и ровно на него до сих пор никто не отвечал.
     * <p>
     * Здание ставится на цоколь <b>выше земли вокруг</b> — так, как оно
     * и выходит в игре на склоне, потому что мод сам подсыпает опору.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "walkin", tickLimit = 600)
    public void citizensCanWalkIntoHouseAndFarm(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        // Высота — вместо простора. Делянки игровых проверок стоят
        // в двенадцати блоках друг от друга, а этой площадке нужно
        // пятнадцать: соседи затирали бы её ровно тогда, когда мир общий
        // и проверки идут разом. По высоте соседей нет, и разъехаться
        // вверх дешевле, чем ужимать землю до бесполезной.
        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 41, 0));
        // Земля вокруг на два блока ниже пола зданий: так и выходит
        // в игре на склоне, и ровно так выглядят снимки заказчика.
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(3, 42, 2));
        BlockPos farmAt = context.getAbsolutePos(new BlockPos(3, 42, 10));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);

        Building house;
        Building farm;
        CitizenEntity walker;
        BlockPos inHouse;
        BlockPos inFarm;
        BuildJob.Outcome built;
        BuildJob.Outcome grown;

        // Мир у игровых проверок общий, и брошенная плита камня валит
        // не эту проверку, а соседнюю — через прогон, непонятно отчего.
        // Поэтому за собой убирают оба исхода: и провал посреди стройки,
        // и разбор на двадцатом тике.
        try {
            // Площадка ровно под нужду: дом, поле, место жителя и шаг
            // спуска вокруг. Мир у игровых проверок общий, и лишние
            // двадцать блоков камня — это не запас, а чужая площадка.
            for (int x = -2; x <= 13; x++) {
                for (int z = -2; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 40, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            house = plan(colony, houseAt, HOUSE_TYPE, BlockRotation.NONE);
            stockFor(world, colony, housePlan);
            built = BuildJob.advance(world, manager, colony.id(), house.id(), 20_000);
            farm = plan(colony, farmAt, FARM_TYPE, BlockRotation.NONE);
            stockFor(world, colony, farmPlan);
            grown = BuildJob.advance(world, manager, colony.id(), farm.id(), 20_000);

            Citizen citizen = evenNewborn("Пешеход", "", NORMAN, Gender.MALE);
            citizen.setPosition(Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(1, 41, 6))));
            colony.addCitizen(citizen);
            walker = CitizenSpawner.spawnBody(world, colony, citizen);

            inHouse = insideOf(world, house, housePlan);
            inFarm = insideOf(world, farm, farmPlan);
        } catch (RuntimeException | Error trouble) {
            cleanUpVillage(world, manager, colony, hall, ground);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            throw trouble;
        }

        // Тело обязано отстояться: ванильная навигация отказывает тому,
        // кто ещё не коснулся земли, а только что появившееся тело висит
        // в воздухе до первого тика. Первая редакция этой проверки на том
        // и споткнулась — и хорошо, что споткнулась на себе, а не на игроке.
        context.runAtTick(20, () -> {
            try {
                if (built != BuildJob.Outcome.FINISHED || grown != BuildJob.Outcome.FINISHED) {
                    context.throwGameTestException("Не встало: дом " + built + ", поле " + grown);
                    return;
                }
                if (walker == null || inHouse == null || inFarm == null) {
                    context.throwGameTestException("Некому или некуда идти: тело "
                            + (walker != null) + ", в доме " + inHouse + ", на поле " + inFarm);
                    return;
                }

                String toHouse = whyCannotReach(walker, inHouse);
                if (toHouse != null) {
                    context.throwGameTestException(walkFailure("В дом не войти", toHouse,
                            world, house, housePlan, walker, inHouse));
                }
                String toFarm = whyCannotReach(walker, inFarm);
                if (toFarm != null) {
                    context.throwGameTestException(walkFailure("На поле не войти", toFarm,
                            world, farm, farmPlan, walker, inFarm));
                }
            } finally {
                // Сносить дом и поле отдельно не нужно: уборка деревни
                // разбирает все её здания и разгоняет тела сама.
                cleanUpVillage(world, manager, colony, hall, ground);
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /**
     * Ровная клетка перед обрывом не обманывает крыльцо.
     * <p>
     * Это та самая земля, на которой мод и попался: у порога площадка
     * шириной в шаг, а за ней уступ в два блока. Ровно так лежит склон,
     * подсыпанный опорой, и ровно это видно на снимках заказчика.
     * <p>
     * Прежнее крыльцо доходило до ровной клетки, объявляло дело сделанным
     * и выходило — ни одной ступени. Прежние проверки этого <b>не ловили</b>:
     * они строили на ровной плите, где обрыв начинается сразу за порогом,
     * и одной ступени хватало. Ошибку нашёл заказчик, четвёртый раз подряд.
     * <p>
     * Поэтому земля тут нарочно с уступом, а судит по-прежнему ванильный
     * поиск пути: дойдёт житель внутрь — крыльцо своё дело сделало.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "walkin", tickLimit = 600)
    public void aFlatCellBeforeTheDropDoesNotFoolThePorch(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        // Высота — вместо простора. Делянки игровых проверок стоят
        // в двенадцати блоках друг от друга, а этой площадке нужно
        // пятнадцать: соседи затирали бы её ровно тогда, когда мир общий
        // и проверки идут разом. По высоте соседей нет, и разъехаться
        // вверх дешевле, чем ужимать землю до бесполезной.
        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 82, 0));
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(4, 76, 4));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);

        Building house;
        CitizenEntity walker;
        BlockPos inHouse;
        BuildJob.Outcome built;

        try {
            house = plan(colony, houseAt, HOUSE_TYPE, BlockRotation.NONE);

            // Высота порога спрашивается у чертежа до стройки: землю надо
            // разложить относительно него, а не наугад.
            int door = Access.entrances(house, housePlan).get(0).getY();
            Vec3i size = BuildSite.rotatedSize(housePlan.size(), BlockRotation.NONE);

            for (int dx = -3; dx <= size.getX() + 2; dx++) {
                for (int dz = -3; dz <= size.getZ() + 2; dz++) {
                    boolean under = dx >= 0 && dz >= 0 && dx < size.getX() && dz < size.getZ();
                    boolean ledge = dx >= -1 && dz >= -1 && dx <= size.getX() && dz <= size.getZ();
                    // Под домом — опора, кольцом вокруг — площадка вровень
                    // с порогом, дальше — земля на два блока ниже неё.
                    int top = under ? houseAt.getY() - 1 : (ledge ? door - 1 : door - 3);
                    BlockPos at = houseAt.add(dx, 0, dz).withY(top);
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            stockFor(world, colony, housePlan);
            built = BuildJob.advance(world, manager, colony.id(), house.id(), 20_000);

            Citizen citizen = evenNewborn("Ходок", "", NORMAN, Gender.MALE);
            citizen.setPosition(Vec3d.ofBottomCenter(houseAt.add(-3, 0, -3).withY(door - 2)));
            colony.addCitizen(citizen);
            walker = CitizenSpawner.spawnBody(world, colony, citizen);

            inHouse = insideOf(world, house, housePlan);
        } catch (RuntimeException | Error trouble) {
            cleanUpVillage(world, manager, colony, hall, ground);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            throw trouble;
        }

        context.runAtTick(20, () -> {
            try {
                if (built != BuildJob.Outcome.FINISHED) {
                    context.throwGameTestException("Дом не встал: " + built);
                    return;
                }
                if (walker == null || inHouse == null) {
                    context.throwGameTestException("Некому или некуда идти: тело "
                            + (walker != null) + ", в доме " + inHouse);
                    return;
                }
                String trouble = whyCannotReach(walker, inHouse);
                if (trouble != null) {
                    context.throwGameTestException(walkFailure(
                            "С уступа в дом не войти", trouble,
                            world, house, housePlan, walker, inHouse));
                }
            } finally {
                cleanUpVillage(world, manager, colony, hall, ground);
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /**
     * Дерево у калитки валит сам билдер, и на поле можно войти.
     * <p>
     * Жалоба заказчика: «построил ферму, а зайти нельзя, дерево блокирует».
     * Дерево росло вплотную к калитке, но <b>за следом здания</b>, а план
     * расчищал только след. Крыльцо потом честно клало ступени под стволом,
     * и войти всё равно было нельзя: мерили одно, мешало другое.
     * <p>
     * Теперь подход ко входу — часть плана: три шага от порога в рост
     * человека. Билдер валит дерево сам, тем же шагом расчистки, каким
     * убирает бугор под фундаментом, и остаток ствола не висит над
     * проходом.
     * <p>
     * Судит по-прежнему ванильный поиск пути: «расчищено» — это не когда
     * клетка пуста по нашей мерке, а когда житель дошёл.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "walkin", tickLimit = 600)
    public void theBuilderFellsTheTreeAtTheGate(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        // Высота — вместо простора. Делянки игровых проверок стоят
        // в двенадцати блоках друг от друга, а этой площадке нужно
        // пятнадцать: соседи затирали бы её ровно тогда, когда мир общий
        // и проверки идут разом. По высоте соседей нет, и разъехаться
        // вверх дешевле, чем ужимать землю до бесполезной.
        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 103, 0));
        BlockPos farmAt = context.getAbsolutePos(new BlockPos(4, 103, 4));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);

        Building farm;
        CitizenEntity walker;
        BlockPos inFarm;
        BlockPos trunk;
        BuildJob.Outcome grown;

        try {
            // Ровная земля: единственная помеха в этой проверке — дерево.
            for (int x = -1; x <= 13; x++) {
                for (int z = -1; z <= 13; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 102, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    ground.add(at);
                }
            }

            farm = plan(colony, farmAt, FARM_TYPE, BlockRotation.NONE);

            // Дуб вырос ровно там, где выходят с поля.
            BlockPos gate = Access.entrances(farm, farmPlan).get(0);
            trunk = gate.offset(Access.awayFrom(farm, farmPlan, gate));
            for (int up = 0; up < 5; up++) {
                world.setBlockState(trunk.up(up), Blocks.OAK_LOG.getDefaultState());
            }
            world.setBlockState(trunk.up(5), Blocks.OAK_LEAVES.getDefaultState());

            stockFor(world, colony, farmPlan);
            grown = BuildJob.advance(world, manager, colony.id(), farm.id(), 20_000);

            Citizen citizen = evenNewborn("Прохожий", "", NORMAN, Gender.MALE);
            citizen.setPosition(Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(0, 103, 8))));
            colony.addCitizen(citizen);
            walker = CitizenSpawner.spawnBody(world, colony, citizen);

            inFarm = insideOf(world, farm, farmPlan);

            if (grown != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Поле не встало: " + grown);
            }
            if (walker == null || inFarm == null) {
                context.throwGameTestException("Некому или некуда идти: тело "
                        + (walker != null) + ", на поле " + inFarm);
            }

            // Глазами — сразу: проход в рост человека свободен, и обрубок
            // ствола над ним не висит. Ждать тика тут нечего, а падение
            // со словами читается лучше, чем падение по времени.
            for (int up = 0; up < 5; up++) {
                if (!world.getBlockState(trunk.up(up)).isAir()) {
                    context.throwGameTestException("Дерево у калитки не свалено: на "
                            + trunk.up(up).toShortString() + " стоит "
                            + world.getBlockState(trunk.up(up)).getBlock());
                }
            }
        } catch (RuntimeException | Error trouble) {
            cleanUpVillage(world, manager, colony, hall, ground);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            throw trouble;
        }

        context.runAtTick(20, () -> {
            try {
                // А ногами — на двадцатом тике: ванильная навигация
                // отказывает телу, которое ещё не коснулось земли.
                String trouble = whyCannotReach(walker, inFarm);
                if (trouble != null) {
                    context.throwGameTestException(walkFailure("На поле не войти", trouble,
                            world, farm, farmPlan, walker, inFarm));
                }
            } finally {
                cleanUpVillage(world, manager, colony, hall, ground);
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /**
     * В каждое здание мода можно войти с земли — во все и у обоих народов.
     * <p>
     * Заказчик написал про дом и поле, но беда была не в них: пол любого
     * здания стоит на цоколе, под цоколь мод подсыпает опору, и порог
     * оказывается выше земли вокруг. Чинить по одному зданию — значит
     * возвращаться к этому каждый раз, когда в моде появится новое;
     * их уже десять.
     * <p>
     * Поэтому проверяется <b>весь список схем разом</b> и на той высоте,
     * какая и выходит в игре на склоне: здание ставится на два блока выше
     * земли. У каждого входа снаружи обязана быть опора не ниже чем
     * на шаг от порога — всё остальное сделают ноги.
     * <p>
     * Ходьбу как таковую проверяет соседняя проверка, спрашивая ванильный
     * поиск пути; здесь же — <b>геометрия у порога</b>, зато у всех зданий
     * и быстро.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "walkin", tickLimit = 900)
    public void everyBuildingLetsYouInFromTheGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        List<Identifier> all = new ArrayList<>(SchematicLoader.ids());
        all.sort(java.util.Comparator.comparing(Identifier::toString));

        List<String> complaints = new ArrayList<>();
        List<BlockPos> ground = new ArrayList<>();

        // Высота — вместо простора. Делянки игровых проверок стоят
        // в двенадцати блоках друг от друга, а этой площадке нужно
        // пятнадцать: соседи затирали бы её ровно тогда, когда мир общий
        // и проверки идут разом. По высоте соседей нет, и разъехаться
        // вверх дешевле, чем ужимать землю до бесполезной.
        BlockPos hall = context.getAbsolutePos(new BlockPos(10, 139, 3));
        // На два блока выше земли: так здание и встаёт после подсыпки опоры.
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 141, 0));

        try {
            for (int x = -9; x <= 20; x++) {
                for (int z = -9; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 138, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            for (Identifier id : all) {
                Schematic schematic = SchematicLoader.get(id).orElseThrow();
                Identifier type = BuildJob.buildingTypeOf(id).orElse(null);
                if (type == null) {
                    continue;
                }

                Identifier culture = new Identifier(type.getNamespace(),
                        type.getPath().split("/")[0]);
                Settlement colony = colonyWithBuilder(world, manager, hall, culture);
                Building site = new Building(UUID.randomUUID(), type,
                        BuildJob.levelOf(id).orElse(1), anchor, BlockRotation.NONE,
                        BuildProgress.PLANNED, List.of());
                colony.addBuilding(site);

                try {
                    stockFor(world, colony, schematic);
                    BuildJob.Outcome outcome =
                            BuildJob.advance(world, manager, colony.id(), site.id(), 40_000);
                    if (outcome != BuildJob.Outcome.FINISHED) {
                        // Недостроенное здание молча прошло бы проверку: порог
                        // висит в воздухе, под ним ровная площадка, спуск
                        // безупречен — и вход при этом не существует.
                        complaints.add(id + ": не достроилось (" + outcome
                                + "), вход проверять не на чем");
                        continue;
                    }

                    List<BlockPos> doors = Access.entrances(site, schematic);
                    if (doors.isEmpty()) {
                        // Здание без входа — отдельный разговор: у поленницы
                        // и у второго уровня дверь наследуется от первого.
                        continue;
                    }
                    for (BlockPos door : doors) {
                        String trouble = descentTrouble(world, site, schematic, door);
                        if (trouble != null) {
                            complaints.add(id + ": " + trouble);
                        }
                    }
                } finally {
                    demolish(world, site, schematic);
                    manager.remove(colony.id());
                }
            }

            if (!complaints.isEmpty()) {
                context.throwGameTestException("Здания, в которые не войти с земли:\n  "
                        + String.join("\n  ", complaints));
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
     * Колония начинается с крыши над головой и поля под боком.
     * <p>
     * Заказчик: «добавь для начала колонии гарантированный дом и ферму,
     * чтоб уже были построены». До этого первый час игры выглядел так:
     * ратуша, один строитель и пустырь. Спать негде, есть нечего, а чтобы
     * появился первый дом, надо разметить его, добыть материалы, завезти
     * и дождаться стройки — и всё это <b>до</b> того, как в моде случится
     * хоть что-то.
     * <p>
     * Проверяется не запись в данных, а <b>блоки в мире</b>: здание,
     * записанное готовым и не поставленное, — ровно та беда, от которой
     * игрок и жаловался, только теперь молча.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "founding", tickLimit = 600)
    public void colonyStartsWithARoofAndAField(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement colony = null;

        try {
            for (int x = -18; x <= 18; x++) {
                for (int z = -18; z <= 18; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            FoundingOutcome outcome = ColonyFounder.foundAt(world, UUID.randomUUID(), NORMAN, hall);
            if (!(outcome instanceof FoundingOutcome.Founded founded)) {
                context.throwGameTestException("Колония не основана: "
                        + ((FoundingOutcome.Refused) outcome).translationKey());
                return;
            }
            colony = founded.settlement();

            Building home = null;
            Building field = null;
            for (Building site : colony.buildings()) {
                if (BuildingTypes.isHome(site.type())) {
                    home = site;
                }
                if (BuildingTypes.employs(site.type(), FARMER)) {
                    field = site;
                }
            }

            if (home == null || field == null) {
                context.throwGameTestException("Колонии не дали надела: "
                        + colony.buildings().stream().map(site -> site.type() + " "
                                + site.progress()).toList());
                return;
            }

            for (Building site : List.of(home, field)) {
                if (site.progress() != BuildProgress.DONE) {
                    context.throwGameTestException(site.type() + " числится "
                            + site.progress() + ", а обещано готовым");
                }
                Schematic plan = SchematicLoader.get(BuildJob.schematicId(site)).orElseThrow();
                int raised = raisedBlocks(world, site, plan);
                if (raised < plan.blocks().size() / 2) {
                    context.throwGameTestException(site.type() + " записано готовым, а в мире "
                            + raised + " блоков из " + plan.blocks().size()
                            + ": здание есть только на бумаге");
                }
            }
        } finally {
            cleanUpVillage(world, manager, colony, hall, meadow);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * За прилавком стоит купец, а не старейшина.
     * <p>
     * Заказчик: «ларёк для купца, чтоб у него покупать и продавать вещи,
     * а не у старейшины — разделим обязанности». До этого деревня была
     * одним человеком с четырьмя руками: старейшина давал квесты, принимал
     * подарки, мирился и торговал.
     * <p>
     * Проверяется <b>обе стороны разделения</b>: у купца товар есть,
     * у старейшины его нет. Одной половины мало — прилавок, открытый
     * у обоих, выглядел бы как работающее разделение ровно до того мига,
     * когда игрок подойдёт к старейшине.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "quests", tickLimit = 600)
    public void theCounterIsKeptByTheMerchantNotTheElder(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала: место занято="
                        + manager.isSettled(centre) + ", помеха=" + whoBlocks(manager, centre));
                return;
            }

            Citizen merchant = village.citizens().stream()
                    .filter(citizen -> citizen.profession()
                            .filter(Villages.MERCHANT::equals).isPresent())
                    .findFirst().orElse(null);
            if (merchant == null) {
                context.throwGameTestException("В деревне нет купца: "
                        + village.citizens().stream().map(citizen -> citizen.profession()
                                .map(Identifier::toString).orElse("без дела")).toList());
                return;
            }

            Building stall = village.buildings().stream()
                    .filter(site -> BuildingTypes.employs(site.type(), Villages.MERCHANT))
                    .findFirst().orElse(null);
            if (stall == null || stall.progress() != BuildProgress.DONE) {
                context.throwGameTestException("Ларёк не стоит: "
                        + village.buildings().stream().map(site -> site.type() + " "
                                + site.progress()).toList());
                return;
            }
            // И у купца есть где стоять: ларёк без рабочего места — сарай.
            if (Workplaces.of(village, merchant).isEmpty()) {
                context.throwGameTestException("Купцу не досталось ларька");
            }

            UUID player = UUID.randomUUID();
            Warehouse wares = Warehouse.of(world, village);
            SimpleInventory pockets = new SimpleInventory(9);

            QuestView atCounter = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.MERCHANT, wares, Schedule.dayOf(context.getWorld().getTimeOfDay())).orElse(null);
            QuestView atElder = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.ELDER, wares, Schedule.dayOf(context.getWorld().getTimeOfDay())).orElse(null);
            if (atCounter == null || atElder == null) {
                context.throwGameTestException("Разговор не собрался: купец=" + atCounter
                        + ", старейшина=" + atElder);
                return;
            }

            if (!atCounter.trades()) {
                context.throwGameTestException("У купца пустой прилавок");
            }
            if (!atCounter.counter()) {
                context.throwGameTestException("Разговор с купцом не считается прилавком: "
                        + "игрок увидит лишние вкладки вместо товара");
            }
            if (atElder.trades()) {
                context.throwGameTestException("Старейшина всё ещё торгует: "
                        + atElder.stalls().size() + " сделок на прилавке");
            }
            // А квесты, наоборот, остались у него.
            if (Quests.offered(village, player, Villages.ELDER).isEmpty()) {
                context.throwGameTestException("Старейшине нечего предложить игроку");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Без купца прилавок держит старейшина.
     * <p>
     * Разделение обязанностей не должно оборачиваться тупиком: купца может
     * унести набег, а нанимают нового не в тот же день. Деревня, молча
     * переставшая торговать, выглядит сломанной — это уже проходили
     * с пустой полкой при первой встрече.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "quests", tickLimit = 600)
    public void withoutAMerchantTheElderKeepsTheCounter(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала");
                return;
            }

            // Купца не стало: набег, мор, дорога — неважно.
            for (Citizen citizen : List.copyOf(village.citizens())) {
                if (citizen.profession().filter(Villages.MERCHANT::equals).isPresent()) {
                    citizen.entityUuid().map(world::getEntity).ifPresent(Entity::discard);
                    village.removeCitizen(citizen.id());
                }
            }

            if (!Villages.counterKeeper(village).equals(Villages.ELDER)) {
                context.throwGameTestException("Без купца прилавок остался за "
                        + Villages.counterKeeper(village));
            }

            QuestView atElder = QuestNet.viewOf(manager, village, UUID.randomUUID(),
                    new SimpleInventory(9), Villages.ELDER, Warehouse.of(world, village), Schedule.dayOf(context.getWorld().getTimeOfDay()))
                    .orElse(null);
            if (atElder == null || !atElder.trades()) {
                context.throwGameTestException("Без купца торговать стало не с кем: "
                        + "деревня молча перестала быть деревней");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Купец приходит за свой прилавок и там остаётся.
     * <p>
     * Ремесло без выработки: купец производит <b>место встречи</b>. Ларёк
     * без купца — декорация, купец без ларька — прохожий, которого игрок
     * ловит щелчками по всей деревне. Поэтому проверяется ровно то, ради
     * чего работа заведена: где он стоит, когда работает.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade", tickLimit = 600)
    public void merchantStandsBehindHisCounter(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic stallPlan = schematic(context, STALL_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building stall = plan(colony, anchor, STALL_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, stallPlan);
            if (BuildJob.advance(world, manager, colony.id(), stall.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ларёк не встал");
                return;
            }

            BlockPos counter = Workplaces.stations(stall).stream().findFirst().orElse(null);
            if (counter == null) {
                context.throwGameTestException("В ларьке нет рабочего места: торговать негде");
                return;
            }

            Citizen merchant = hireWithBody(world, colony, Villages.MERCHANT,
                    anchor.add(0, 1, 0).north(6));
            Workplaces.assign(world, colony);
            if (Workplaces.of(colony, merchant).isEmpty()) {
                context.throwGameTestException("Купцу не досталось ларька, хотя он один");
            }

            runWork(world, manager, colony, merchant, 8, Schedule.MORNING_WORK);

            CitizenEntity body = bodyOf(world, colony, merchant);
            double away = body.getBlockPos().getSquaredDistance(counter);
            if (away > WorkContext.ARRIVAL_REACH * WorkContext.ARRIVAL_REACH) {
                context.throwGameTestException("Купец не за прилавком: он на "
                        + body.getBlockPos().toShortString() + ", прилавок на "
                        + counter.toShortString());
            }
        } finally {
            demolish(world, stall, stallPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Дойдя до прилавка, купец за ним и остаётся.
     * <p>
     * Прежде он, придя, отпускал цель — чтобы не топтаться на месте, — и его
     * тут же забирала прогулка. Следующее решение звало его обратно, и так
     * весь день: купец ходил кругами около ларька, а в сохранении заказчика
     * и вовсе стоял в тридцати блоках от него. Место за прилавком — это цель,
     * а не пункт маршрута: её держат, пока идёт работа.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade", tickLimit = 200)
    public void aMerchantAtHisCounterKeepsHisPlace(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic stallPlan = schematic(context, STALL_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building stall = plan(colony, anchor, STALL_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, stallPlan);
            if (BuildJob.advance(world, manager, colony.id(), stall.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ларёк не встал");
                return;
            }
            BlockPos counter = Workplaces.stations(stall).stream().findFirst().orElse(null);
            if (counter == null) {
                context.throwGameTestException("В ларьке нет рабочего места");
                return;
            }
            Citizen merchant = hireWithBody(world, colony, Villages.MERCHANT, counter);
            Workplaces.assign(world, colony);
            CitizenEntity body = bodyOf(world, colony, merchant);

            WorkTicker.decide(world, manager, colony, merchant, Schedule.MORNING_WORK);
            if (body.workTarget() == null) {
                context.throwGameTestException("Купец у прилавка отпустил цель — "
                        + "его заберёт прогулка");
            }
        } finally {
            demolish(world, stall, stallPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Житель на своём месте смотрит на дело или на гостя, а не себе под ноги.
     * <p>
     * Цель навигации держит и шаг, и взгляд, и смотрела она туда же, куда
     * вела, — на клетку, где житель стоит. Пока купец, дойдя, отпускал
     * цель, этого не было видно; стоило держать его за прилавком — и он
     * уставился бы в пол. Так же всю работу смотрели в землю фермер
     * у грядки и лесоруб у ствола: цель им ставилась рядом с делом.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void aCitizenAtHisPostDoesNotStareAtHisFeet(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos spot = context.getAbsolutePos(new BlockPos(3, 2, 3));
        List<BlockPos> floor = new ArrayList<>();
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos at = spot.add(dx, -1, dz);
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }
            Citizen stander = hireWithBody(world, colony, Villages.MERCHANT, spot);
            CitizenEntity body = bodyOf(world, colony, stander);
            body.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
            body.setWorkTarget(spot);

            com.villagepax.entity.CitizenWorkGoal goal = new com.villagepax.entity.CitizenWorkGoal(body);
            goal.start();
            goal.tick();
            if (body.getLookControl().isLookingAtSpecificPosition()
                    && body.getLookControl().getLookY() < body.getEyeY() - 1.0) {
                context.throwGameTestException("Житель на месте смотрит себе под ноги: взгляд на "
                        + body.getLookControl().getLookY() + ", глаза на " + body.getEyeY());
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Ратуша растёт до четвёртого уровня, и колония становится столицей.
     * <p>
     * Ступень «столица» была в лестнице с первой недели и недостижима:
     * схемы ратуши четвёртого уровня не существовало, и на третьем
     * «Улучшить» отвечало «выше некуда». Обещание держится схемой.
     * <p>
     * И растёт она <b>вширь</b>, а не вверх, и это не украшение: третий
     * ярус пришлось укоротить на ряд стен и ярус кровли, потому что
     * до верха билдер не дотягивался. Четвёртый растёт гульбищем вокруг —
     * на восток и юг, чтобы якорь здания остался на месте.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth", tickLimit = 900)
    public void townHallGrowsWideIntoACapital(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic fourth = schematic(context, new Identifier("villagepax",
                "norman/town_hall_lvl4"));

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos at = context.getAbsolutePos(new BlockPos(2, 1, 2));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building seat = null;

        try {
            seat = plan(colony, at, TOWN_HALL_TYPE, BlockRotation.NONE);
            seat.setLevel(4);
            seat.restartBuilding();
            stockFor(world, colony, fourth);
            if (BuildJob.advance(world, manager, colony.id(), seat.id(), 40_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ратуша четвёртого уровня не встала: шаг "
                        + seat.nextStep() + " из " + fourth.plan().steps().size());
                return;
            }

            Levels.refresh(colony);
            if (colony.level() != SettlementLevel.CAPITAL) {
                context.throwGameTestException("Ратуша четвёртого уровня стоит, а колония "
                        + "всё ещё " + colony.level().id());
            }

            // И ступень открывает дело, а не только число: рынок был заперт,
            // а теперь его можно строить.
            com.villagepax.core.building.BuildingType market =
                    BuildingTypes.get(MARKET_TYPE).orElse(null);
            if (market == null) {
                context.throwGameTestException("Рынка нет в данных");
                return;
            }
            if (market.openTo(SettlementLevel.TOWN)) {
                context.throwGameTestException("Рынок открыт городу: тогда столица "
                        + "не открывает ничего");
            }
            if (!market.openTo(colony.level())) {
                context.throwGameTestException("Столица стоит, а рынок всё ещё заперт");
            }
        } finally {
            if (seat != null) {
                demolish(world, seat, fourth);
            }
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * К рынку обоз приходит каждый день, а к колонии без рынка — раз в три.
     * <p>
     * Это и есть награда за столицу, и она из тех, которые видно, не
     * открывая пульта: у ворот стоит чужой обоз, и стоит он там каждое
     * утро. Ступень, дающая только предел населения, наградой
     * не ощущается — это уже проходили с первыми тремя.
     * <p>
     * Считается по дням числом, а не по игровым суткам: «раз в три дня»
     * иначе не проверить — тест не может прождать трое суток, а шесть
     * вызовов в одном тике для мода один и тот же день.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "caravan", tickLimit = 900)
    public void theMarketBringsACaravanEveryDay(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic marketPlan = schematic(context, MARKET_SCHEMATIC);

        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(20, 2, 2));
        BlockPos marketAt = context.getAbsolutePos(new BlockPos(4, 2, 5));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = null;
        Settlement village = null;
        Building market = null;

        try {
            for (int x = -2; x <= 26; x++) {
                for (int z = -4; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Торговая", villageAt);
            world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
            manager.add(village);

            colony = colonyWithBuilder(world, manager, colonyAt);

            // Деревне есть чем торговать — иначе обоз не выйдет вовсе,
            // и проверка мерила бы пустоту.
            Warehouse store = Warehouse.of(world, village);
            store.add(new ItemStack(Items.BREAD, 40));
            Coins.earn(store.coins(), 64);

            int without = countVisits(world, manager, village, colony);

            market = plan(colony, marketAt, MARKET_TYPE, BlockRotation.NONE);
            stockFor(world, colony, marketPlan);
            if (BuildJob.advance(world, manager, colony.id(), market.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Рынок не встал");
                return;
            }
            Warehouse.of(world, village).add(new ItemStack(Items.BREAD, 40));
            Coins.earn(Warehouse.of(world, village).coins(), 64);

            int with = countVisits(world, manager, village, colony);

            if (with <= without) {
                context.throwGameTestException("Рынок не позвал обозы чаще: без рынка "
                        + without + " прихода за шесть дней, с рынком " + with);
            }
            if (with < 6) {
                context.throwGameTestException("К рынку обоз приходит не каждый день: "
                        + with + " прихода за шесть дней");
            }
        } finally {
            if (market != null) {
                demolish(world, market, marketPlan);
            }
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, colonyAt, floor);
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
            world.setBlockState(colonyAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Союз заключают друзья и только с городом — и он держится дружбой.
     * <p>
     * Лестница ступеней обещала союзы с города, а доверие до сих пор
     * меняло только цены на прилавке. Проверяется вся сделка: чего не
     * хватает на каждом шагу, что дар уходит деревне и что союз
     * <b>перестаёт действовать</b>, если игрок растерял дружбу.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "peace", tickLimit = 600)
    public void anAllianceNeedsFriendshipATownAndGold(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(14, 2, 2));
        List<BlockPos> floor = new ArrayList<>();

        Settlement village = null;
        Settlement colony = null;

        try {
            for (int x = -2; x <= 18; x++) {
                for (int z = -2; z <= 8; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
            world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
            manager.add(village);

            colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
            manager.add(colony);

            SimpleInventory pockets = new SimpleInventory(9);

            // Чужак с золотом: денег мало, дружбы нет.
            Coins.earn(pockets, Alliance.PRICE);
            if (Alliance.judge(village, colony, player, pockets)
                    != Alliance.Verdict.NOT_FRIENDS) {
                context.throwGameTestException("Союз предлагают чужаку: "
                        + Alliance.judge(village, colony, player, pockets));
            }

            // Друг, но хутор.
            village.addReputation(player, Standing.FRIEND.from());
            if (Alliance.judge(village, colony, player, pockets) != Alliance.Verdict.NO_TOWN) {
                context.throwGameTestException("Союз предлагают хутору: "
                        + Alliance.judge(village, colony, player, pockets));
            }

            // Друг и город, но без золота.
            colony.setLevel(SettlementLevel.TOWN);
            SimpleInventory empty = new SimpleInventory(9);
            if (Alliance.judge(village, colony, player, empty) != Alliance.Verdict.NO_COIN) {
                context.throwGameTestException("Союз заключают даром: "
                        + Alliance.judge(village, colony, player, empty));
            }

            // И наконец всё сошлось.
            Alliance.Outcome outcome = Alliance.forge(world, manager, village, colony, player,
                    pockets, 7L, left -> { });
            if (!outcome.forged()) {
                context.throwGameTestException("Союз не заключён: " + outcome.verdict());
                return;
            }
            if (Coins.total(pockets) != 0) {
                context.throwGameTestException("Дар не ушёл: у игрока осталось "
                        + Coins.total(pockets));
            }
            if (Coins.total(Warehouse.of(world, village).coins()) != Alliance.PRICE) {
                context.throwGameTestException("Дар не дошёл до деревни: в кошеле "
                        + Coins.total(Warehouse.of(world, village).coins()));
            }
            if (!manager.byId(village.id()).orElseThrow().isAllyOf(player)) {
                context.throwGameTestException("Союз заключён, а деревня об этом не знает");
            }

            // И союз держится дружбой, а не записью.
            Settlement allied = manager.byId(village.id()).orElseThrow();
            allied.addReputation(player, -Standing.FRIEND.from());
            if (allied.isAllyOf(player)) {
                context.throwGameTestException("Союз пережил утраченную дружбу: доверие "
                        + allied.reputationOf(player));
            }
        } finally {
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, colonyAt, floor);
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Союзники приходят на набег и бьют налётчиков.
     * <p>
     * Это и есть всё содержание союза: не строка в пульте, а мечи у ворот.
     * Проверяется то, ради чего он заключается, — что подмога пришла,
     * что её не считают налётчиками свои же проверки, и что обе стороны
     * взяли друг друга на прицел.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "raid", tickLimit = 600)
    public void alliesComeWhenRaidersDo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        // Своя полка по высоте: делянки проверок стоят в двенадцати блоках,
        // а этой нужна площадка шире — соседи затирали бы её.
        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 61, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(10, 61, 6));
        BlockPos friendAt = context.getAbsolutePos(new BlockPos(2, 61, 14));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(colony);
        Settlement friend = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Верный", friendAt);
        friend.addReputation(player, Standing.FRIEND.from());
        friend.makeAlly(player, 1L);
        manager.add(friend);

        WarParty party = new WarParty(UUID.randomUUID(), UUID.randomUUID(), MAYA,
                musters, 2, 20L, 21L);

        try {
            for (int x = 2; x <= 16; x++) {
                for (int z = 0; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 60, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            manager.update(colony.id(), state -> state.besiege(party, 19L));
            Raids.watch(world, manager, 20L);

            List<CitizenEntity> raiders = Raids.bodiesOf(world, party);
            List<CitizenEntity> helpers = Allies.defendersOf(world, party);

            if (raiders.size() != 2) {
                context.throwGameTestException("Налётчиков " + raiders.size() + " вместо двух: "
                        + "союзников сочли своими");
                return;
            }
            if (helpers.size() != 2) {
                context.throwGameTestException("Союзников пришло " + helpers.size()
                        + " вместо двух");
                return;
            }
            for (CitizenEntity helper : helpers) {
                if (helper.isRaider()) {
                    context.throwGameTestException("Союзник числится налётчиком: "
                            + "его смерть засчитают деревне-обидчице");
                }
            }

            // И обе стороны видят друг в друге врага.
            //
            // Спрашивается НЕ ОДИН РАЗ, а до самого срока: ванильная цель
            // просыпается раз в десяток тиков, случайно, и требует прямой
            // видимости. Проверка, спросившая однажды, мигает — и это
            // не «иногда не работает», а «иногда не успели посмотреть».
            boolean[] met = {false};
            for (int tick = 40; tick <= 200; tick += 40) {
                boolean last = tick > 160;
                context.runAtTick(tick, () -> {
                    if (met[0]) {
                        return;
                    }
                    // Меркой служит КРОВЬ НАЛЁТЧИКА, а не то, на кого он
                    // смотрит прямо сейчас. Прицел — мгновение: бой
                    // кончается за пару секунд, и проверка, спросившая
                    // «целится ли», у победившей стороны получает «нет»
                    // ровно потому, что дело сделано. А бить налётчика
                    // в этой проверке больше некому: жителей у колонии нет,
                    // падать неоткуда.
                    List<CitizenEntity> raidersNow = Raids.bodiesOf(world, party);
                    boolean bled = raidersNow.size() < 2
                            || raidersNow.stream().anyMatch(one -> one.getHealth() < one.getMaxHealth());
                    if (bled) {
                        met[0] = true;
                        tidyUpFight(world, manager, party, friend, friendAt, colony, centre, floor);
                        context.complete();
                        return;
                    }
                    if (!last) {
                        return;
                    }
                    try {
                        context.throwGameTestException("Союзники пришли и не подрались: "
                                + "за десять секунд налётчики не потеряли ни капли крови. "
                                + "Их " + Raids.bodiesOf(world, party).size()
                                + ", союзников " + Allies.defendersOf(world, party).size());
                    } finally {
                        tidyUpFight(world, manager, party, friend, friendAt, colony, centre, floor);
                    }
                });
            }
        } catch (RuntimeException | Error trouble) {
            tidyUpFight(world, manager, party, friend, friendAt, colony, centre, floor);
            throw trouble;
        }
    }

    /**
     * Пульт открывается только хозяину, и это чинит молчащее меню.
     * <p>
     * Жалоба заказчика: «не могу заказать постройку и поставить постройку,
     * не грузит призрак». Причина оказалась не в призраке. Ратуша есть
     * и у деревни народа, и щелчок по ней открывал <b>полный пульт
     * колонии</b> — со списком зданий и кнопками «Заказать». Ни одна
     * из них не работала: сервер отбрасывает намерения по чужому
     * поселению, потому что распоряжаться можно только своим. Молча.
     * <p>
     * Кнопка, которая ничего не делает и ничего не говорит, — худшее,
     * что бывает в меню: игрок не понимает, сломан мод или он сам.
     * Теперь чужая ратуша пульта не открывает вовсе и говорит, что
     * здесь можно на самом деле.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "founding")
    public void aVillageTownHallIsNotYourConsole(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 20, 2));
        BlockPos mineAt = context.getAbsolutePos(new BlockPos(14, 20, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        Settlement mine = Settlement.found(NORMAN, Owner.of(player), "Моя", mineAt);
        Settlement theirs = Settlement.found(NORMAN, Owner.of(stranger), "Чужая", mineAt);

        manager.add(village);
        manager.add(mine);

        try {
            if (TownHallConsole.yours(village, player)) {
                context.throwGameTestException("Пульт деревни народа открывается игроку: "
                        + "в нём все кнопки молчат");
            }
            if (TownHallConsole.yours(theirs, player)) {
                context.throwGameTestException("Пульт чужой колонии открывается игроку");
            }
            if (!TownHallConsole.yours(mine, player)) {
                context.throwGameTestException("Своя колония не пускает хозяина в пульт");
            }

            // И это ровно тот же признак, по которому сервер решает, чьё
            // намерение исполнять: два разных ответа на один вопрос
            // и давали молчащее меню.
            if (Founding.colonyOf(manager, player).map(Settlement::id)
                    .filter(mine.id()::equals).isEmpty()) {
                context.throwGameTestException("Колония игрока не находится по владельцу");
            }
            if (Founding.colonyOf(manager, stranger).isPresent()) {
                context.throwGameTestException("У чужака нашлась колония в этом мире");
            }
        } finally {
            manager.remove(village.id());
            manager.remove(mine.id());
        }

        context.complete();
    }

    /**
     * У каждого заказа есть призрак, и он не пустой.
     * <p>
     * Призрак — единственное, чем игрок выбирает место: нет призрака —
     * нет и постройки, и жаловаться он будет ровно теми словами, какими
     * и пожаловался: «не грузит призрак». Ломается это молча — схема
     * загрузилась, список заказов полон, а показывать нечего, — и потому
     * сверяется списком.
     * <p>
     * Заодно мерится потолок: призрак в восемь тысяч блоков клиент
     * обрежет на середине, и здание покажется недостроенным ещё
     * до стройки.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "orders")
    public void everyOrderHasAGhostToShow(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            List<String> mute = new ArrayList<>();
            for (Identifier schematicId : TownHallView.of(world, colony).offers()) {
                Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
                if (schematic == null) {
                    mute.add(schematicId + ": схемы нет вовсе");
                    continue;
                }
                GhostPlan ghost = GhostPlan.of(schematicId, schematic);
                if (ghost.blocks().isEmpty()) {
                    mute.add(schematicId + ": призрак пуст");
                }
                if (ghost.blocks().size() >= GhostPlan.MAX_BLOCKS) {
                    mute.add(schematicId + ": призрак упёрся в потолок ("
                            + ghost.blocks().size() + ")");
                }
                if (!ghost.size().equals(schematic.size())) {
                    mute.add(schematicId + ": размер призрака " + ghost.size()
                            + " вместо " + schematic.size());
                }
            }

            if (!mute.isEmpty()) {
                context.throwGameTestException("Заказы без призрака: "
                        + String.join("; ", mute));
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Разбитый отряд деревня помнит, а ушедший — нет.
     * <p>
     * С этого дня считается право требовать дань: она берётся не с того,
     * кто слабее вообще, а с того, чьи люди <b>лежат под твоими воротами</b>.
     * Разница видна на второй половине проверки: отряд, ушедший целым,
     * дня разгрома не оставляет — его и не было.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "raid")
    public void aBeatenWarBandIsRemembered(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 71, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(9, 71, 6));
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 71, 14));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        Settlement village = Settlement.found(MAYA, Owner.AUTONOMOUS, "Коба", villageAt);
        manager.add(colony);
        manager.add(village);

        WarParty band = new WarParty(UUID.randomUUID(), village.id(), MAYA, musters, 1, 20L, 21L);

        try {
            for (int x = 2; x <= 14; x++) {
                for (int z = 2; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 70, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            // Отряд пришёл и лёг в бою до последнего бойца. Осада снимается
            // вместе с ним, и итог подводится в тот же миг — в день боя.
            manager.update(colony.id(), state -> state.besiege(band, 19L));
            Raids.watch(world, manager, 20L);
            for (CitizenEntity fighter : Raids.bodiesOf(world, band)) {
                Raids.fell(world, fighter, 20L);
                fighter.discard();
            }

            long beaten = manager.byId(village.id()).orElseThrow().beatenOn();
            if (beaten != 20L) {
                context.throwGameTestException("День разгрома не записан: " + beaten
                        + " вместо 20. Дань требовать будет не с чего");
            }

            // А второй отряд уходит целым — и разгрома не случилось.
            WarParty whole = new WarParty(UUID.randomUUID(), village.id(), MAYA, musters,
                    1, 30L, 31L);
            manager.update(village.id(), state -> state.beaten(Settlement.UNSEEN_DAY));
            manager.update(colony.id(), state -> state.besiege(whole, 29L));
            Raids.watch(world, manager, 30L);
            Raids.watch(world, manager, 32L);

            if (manager.byId(village.id()).orElseThrow().beatenOn() != Settlement.UNSEEN_DAY) {
                context.throwGameTestException("Ушедший целым отряд засчитан разгромом: "
                        + "дань можно было бы требовать после любого набега");
            }

            // Отряд, которого не нашли — игрока рядом не было, чанк выгружен, —
            // битым не считается: право на дань даёт бой, а не отлучка.
            WarParty unseen = new WarParty(UUID.randomUUID(), village.id(), MAYA, musters,
                    1, 40L, 41L);
            manager.update(village.id(), state -> state.beaten(Settlement.UNSEEN_DAY));
            manager.update(colony.id(), state -> state.besiege(unseen, 39L));
            Raids.watch(world, manager, 40L);
            Raids.bodiesOf(world, unseen).forEach(CitizenEntity::discard);
            Raids.watch(world, manager, 42L);
            if (manager.byId(village.id()).orElseThrow().beatenOn() != Settlement.UNSEEN_DAY) {
                context.throwGameTestException("Отряд без боя засчитан разгромом");
            }
        } finally {
            Raids.bodiesOf(world, band).forEach(CitizenEntity::discard);
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, centre, floor);
        }

        context.complete();
    }

    /**
     * Дань берут с разбитых, а не с друзей — и не с хутора.
     * <p>
     * Лестница ступеней обещала городу «право требовать дань со слабых
     * соседей», и слово «слабых» тут не украшение: требовать можно только
     * у той деревни, чей отряд <b>только что</b> лёг под твоими воротами.
     * Иначе дань стала бы налогом на соседство — подрос и обложил всех,
     * ничем не рискуя.
     * <p>
     * Проверяется каждый отказ по очереди, потому что каждый из них —
     * отдельное правило, и выпади любое, дань перестанет что-то значить.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "peace")
    public void tributeIsTakenFromTheBeatenNotFromFriends(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 30, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(16, 30, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
        manager.add(village);
        manager.add(colony);

        try {
            // Хутор не требует ничего, даже у разбитых.
            manager.update(village.id(), state -> state.beaten(10L));
            if (Tribute.judge(manager.byId(village.id()).orElseThrow(), colony, player, 12L)
                    != Tribute.Verdict.NO_TOWN) {
                context.throwGameTestException("Хутор требует дань: "
                        + Tribute.judge(village, colony, player, 12L));
            }

            colony.setLevel(SettlementLevel.TOWN);

            // Неразбитая деревня не платит.
            manager.update(village.id(), state -> state.beaten(Settlement.UNSEEN_DAY));
            if (Tribute.judge(manager.byId(village.id()).orElseThrow(), colony, player, 12L)
                    != Tribute.Verdict.NOT_BEATEN) {
                context.throwGameTestException("Дань берут с деревни, которая не воевала");
            }

            // И давно разбитая тоже: страх не вечен.
            manager.update(village.id(), state -> state.beaten(10L));
            if (Tribute.judge(manager.byId(village.id()).orElseThrow(), colony, player,
                    10L + Tribute.MEMORY + 1) != Tribute.Verdict.NOT_BEATEN) {
                context.throwGameTestException("Разгром помнят дольше срока памяти");
            }

            // Друг дани не платит.
            manager.update(village.id(), state ->
                    state.addReputation(player, Standing.FRIEND.from()));
            if (Tribute.judge(manager.byId(village.id()).orElseThrow(), colony, player, 12L)
                    != Tribute.Verdict.TOO_FRIENDLY) {
                context.throwGameTestException("С друга берут дань: дружба и дань смешались");
            }

            // А с обиженного — берут, и требование разрывает союз.
            manager.update(village.id(), state -> {
                state.addReputation(player, -Standing.FRIEND.from());
                state.makeAlly(player, 11L);
            });
            Settlement beaten = manager.byId(village.id()).orElseThrow();
            Tribute.Outcome outcome = Tribute.demand(manager, beaten, colony, player, 12L);
            if (!outcome.taken()) {
                context.throwGameTestException("Дань не взята: " + outcome.verdict());
                return;
            }

            Settlement paying = manager.byId(village.id()).orElseThrow();
            if (!paying.owesTributeTo(player, 12L)) {
                context.throwGameTestException("Дань назначена, а деревня о ней не знает");
            }
            if (paying.tributeDaysLeft(12L) != Tribute.DAYS) {
                context.throwGameTestException("Срок дани " + paying.tributeDaysLeft(12L)
                        + " вместо " + Tribute.DAYS);
            }
            if (paying.isAllyOf(player)) {
                context.throwGameTestException("Союз пережил требование дани: "
                        + "деревня и вступается за игрока, и откупается от него");
            }
            if (Tribute.judge(paying, colony, player, 12L) != Tribute.Verdict.ALREADY) {
                context.throwGameTestException("Дань требуют дважды");
            }
        } finally {
            manager.remove(village.id());
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Дань переезжает монетой и копит обиду.
     * <p>
     * Дань — не число в сохранении, а <b>серебро из чужого сундука</b>:
     * обобрать можно только того, у кого есть что взять, и увидеть это
     * можно, открыв его склад. Разорённая деревня не платит, и это
     * не сбой, а ответ.
     * <p>
     * И каждый платёж роняет доверие. Иначе дань была бы бесплатным
     * доходом, а она — решение: монета сегодня против отряда у ворот
     * послезавтра.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "peace")
    public void tributeMovesCoinAndBreedsResentment(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 35, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(16, 35, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
        manager.add(village);

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
        world.setBlockState(colonyAt, ModBlocks.TOWN_HALL.getDefaultState());
        manager.add(colony);

        try {
            Coins.earn(Warehouse.of(world, village).coins(), Tribute.RATE * 3);
            manager.update(village.id(), state -> state.startTribute(player, 100L));
            int trustBefore = manager.byId(village.id()).orElseThrow().reputationOf(player);
            int mineBefore = Coins.total(Warehouse.of(world, colony).coins());

            for (long day = 1; day <= 3; day++) {
                if (Tribute.pay(world, manager, village, day) != Tribute.RATE) {
                    context.throwGameTestException("День " + day + ": дань не заплачена");
                    return;
                }
            }

            int mineAfter = Coins.total(Warehouse.of(world, colony).coins());
            if (mineAfter != mineBefore + Tribute.RATE * 3) {
                context.throwGameTestException("На склад колонии пришло "
                        + (mineAfter - mineBefore) + " вместо " + (Tribute.RATE * 3));
            }
            if (Coins.total(Warehouse.of(world, village).coins()) != 0) {
                context.throwGameTestException("У деревни осталась монета: "
                        + Coins.total(Warehouse.of(world, village).coins())
                        + " — платили не из её сундука");
            }

            Settlement paying = manager.byId(village.id()).orElseThrow();
            if (paying.reputationOf(player) != trustBefore - Tribute.RESENTMENT * 3) {
                context.throwGameTestException("Обида не копится: доверие "
                        + paying.reputationOf(player) + " вместо "
                        + (trustBefore - Tribute.RESENTMENT * 3));
            }

            // Разорённая деревня не платит, но и дани не лишается.
            if (Tribute.pay(world, manager, village, 4L) != 0) {
                context.throwGameTestException("Разорённая деревня всё равно заплатила");
            }
            if (!manager.byId(village.id()).orElseThrow().owesTributeTo(player, 4L)) {
                context.throwGameTestException("Дань кончилась от одного пустого дня");
            }

            // А срок выходит — и запись убирается сама.
            Tribute.pay(world, manager, village, 200L);
            if (manager.byId(village.id()).orElseThrow().tributeTo().isPresent()) {
                context.throwGameTestException("Срок вышел, а дань в записи осталась");
            }
        } finally {
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, colonyAt, List.of());
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
            world.setBlockState(colonyAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Карточка дани говорит сама за себя — и появляется не у всех.
     * <p>
     * У мирного соседа её нет вовсе: «сперва разбей их отряд» в разговоре
     * с деревней, которая тебе ничего не сделала, — это не цель, а подсказка
     * грабить. Зато у разбитой она есть сразу, и у платящей — тоже:
     * игрок должен видеть, сколько ему ещё несут.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "quests")
    public void theLevyCardSpeaksForItself(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 45, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(16, 45, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
        colony.setLevel(SettlementLevel.TOWN);
        manager.add(village);
        manager.add(colony);

        SimpleInventory pockets = new SimpleInventory(9);
        Warehouse wares = Warehouse.of(world, village);

        try {
            // Мирный сосед: дани нет, а поход предлагают — деревню,
            // которую ещё не били, обобрать нельзя, но пойти на неё можно,
            // и узнать об этом игрок должен здесь же.
            QuestView quiet = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.ELDER, wares, Optional.empty(), ItemStack.EMPTY, 12L).orElseThrow();
            QuestView.Levy peaceful = quiet.levy().orElse(null);
            if (peaceful == null) {
                context.throwGameTestException("Городу не сказали даже про поход");
                return;
            }
            if (peaceful.ready() || peaceful.paying()) {
                context.throwGameTestException("Дань предлагают у мирной деревни: "
                        + "это подсказка грабить, а не цель");
            }
            if (!peaceful.march().worthShowing()) {
                context.throwGameTestException("Поход на небитую деревню не предложен");
            }

            // А другу карточку не показывают вовсе: ни дани, ни похода.
            manager.update(village.id(), state ->
                    state.addReputation(player, Standing.FRIEND.from()));
            QuestView friendly = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.ELDER, wares, Optional.empty(), ItemStack.EMPTY, 12L).orElseThrow();
            if (friendly.levy().isPresent()) {
                context.throwGameTestException("Другу предлагают войну");
            }
            manager.update(village.id(), state ->
                    state.addReputation(player, -Standing.FRIEND.from()));

            // Разбитая: карточка есть и говорит «можно».
            manager.update(village.id(), state -> state.beaten(10L));
            QuestView beaten = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.ELDER, wares, Optional.empty(), ItemStack.EMPTY, 12L).orElseThrow();
            QuestView.Levy levy = beaten.levy().orElse(null);
            if (levy == null || !levy.ready() || levy.paying()) {
                context.throwGameTestException("У разбитой деревни карточка дани не готова: "
                        + levy);
            }

            // Платящая: карточка считает дни.
            manager.update(village.id(), state -> state.startTribute(player, 20L));
            QuestView paying = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.ELDER, wares, Optional.empty(), ItemStack.EMPTY, 12L).orElseThrow();
            QuestView.Levy going = paying.levy().orElse(null);
            if (going == null || !going.paying() || going.days() != 8) {
                context.throwGameTestException("Карточка не считает дни дани: " + going);
            }
        } finally {
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, colonyAt, List.of());
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Башни убавляют отряд, но не отменяют набег.
     * <p>
     * Это всё, что делает укрепление, и мерится оно тем, чем игрок его
     * и почувствует: <b>к воротам пришло меньше людей</b>. Не «плюс десять
     * к обороне», которых не видно, а двое вместо четверых.
     * <p>
     * И не до нуля. Набег, который не приходит, — это выключенная механика,
     * а не победа: деревня со счётом к игроку пошлёт хотя бы одного.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid")
    public void towersThinTheWarBandButNeverStopIt(TestContext context) {
        int angry = Raids.fightersFor(-100);
        if (angry < 3) {
            context.throwGameTestException("Проверка рассчитана на полный отряд, а он "
                    + angry + ": числа набега изменились, поправь проверку");
        }

        if (Raids.fightersFor(-100, 1) != angry - 1) {
            context.throwGameTestException("Одна башня убавила " + (angry
                    - Raids.fightersFor(-100, 1)) + " мечей вместо одного");
        }
        if (Raids.fightersFor(-100, 2) != angry - 2) {
            context.throwGameTestException("Две башни убавили не двоих");
        }
        if (Raids.fightersFor(-100, 99) != 1) {
            context.throwGameTestException("Башни отменили набег совсем: пришло "
                    + Raids.fightersFor(-100, 99) + " бойцов. Выключенная механика —"
                    + " не оборона");
        }
        if (Raids.fightersFor(0, 5) != 0) {
            context.throwGameTestException("Башни зовут набег там, где его не было");
        }

        context.complete();
    }

    /**
     * Недостроенная башня мечей не убавляет, а достроенная — считается.
     * <p>
     * Обещать оборону, которой ещё нет, — худший вид обмана: игрок
     * рассчитывает на стены и встречает полный отряд.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid", tickLimit = 600)
    public void onlyAFinishedTowerCounts(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, TOWER_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 50, 1));
        BlockPos at = context.getAbsolutePos(new BlockPos(4, 50, 4));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building tower = null;

        try {
            tower = plan(colony, at, TOWER_TYPE, BlockRotation.NONE);
            if (Raids.towersOf(colony) != 0) {
                context.throwGameTestException("Размеченная башня уже считается обороной");
            }

            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), tower.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Башня не встала: до верха не дотянуться?");
                return;
            }

            if (Raids.towersOf(colony) != 1) {
                context.throwGameTestException("Готовая башня не сочтена: "
                        + Raids.towersOf(colony));
            }
            if (Workplaces.stations(tower).isEmpty()) {
                context.throwGameTestException("На башне нет поста: страже некуда встать");
            }
        } finally {
            if (tower != null) {
                demolish(world, tower, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Страж приходит на башню и стоит там.
     * <p>
     * «Работа должна быть видна» — правило дизайн-документа, и до башни
     * страж его нарушал: мирный обход был ходьбой к дальнему зданию,
     * то есть кругами по чужим огородам. Башню для того и строят, чтобы
     * с неё смотреть, и пустая башня рядом с бродящим по улице стражем
     * была бы насмешкой над обоими.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard", tickLimit = 600)
    public void theGuardStandsOnTheTower(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, TOWER_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 55, 1));
        BlockPos at = context.getAbsolutePos(new BlockPos(4, 55, 4));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building tower = null;

        try {
            tower = plan(colony, at, TOWER_TYPE, BlockRotation.NONE);
            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), tower.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Башня не встала");
                return;
            }

            BlockPos post = Workplaces.stations(tower).get(0);
            Citizen watchman = hireWithBody(world, colony, Villages.GUARD,
                    hall.add(0, 1, 0));
            runWork(world, manager, colony, watchman, 8, Schedule.MORNING_WORK);

            CitizenEntity body = bodyOf(world, colony, watchman);
            double away = body.getBlockPos().getSquaredDistance(post);
            if (away > WorkContext.ARRIVAL_REACH * WorkContext.ARRIVAL_REACH) {
                context.throwGameTestException("Страж не на башне: он на "
                        + body.getBlockPos().toShortString() + ", пост на "
                        + post.toShortString());
            }
        } finally {
            if (tower != null) {
                demolish(world, tower, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Верёвку можно натянуть в любую сторону, и она всё так же держит вещи.
     * <p>
     * Заказчик: «сделай, чтоб верёвка могла смотреть в разные стороны».
     * До этого она шла только с запада на восток, и во дворе, вытянутом
     * поперёк, висела через проход, а не вдоль стены.
     * <p>
     * Проверяется не только поворот, но и то, что он <b>переживает</b>
     * поворот здания: схемы ставятся всеми четырьмя сторонами, и верёвка,
     * теряющая направление при повороте дома, повисла бы поперёк комнаты.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "decor")
    public void theRopeCanBeStrungAnyWay(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos at = context.getAbsolutePos(new BlockPos(2, 22, 2));

        try {
            for (Direction facing : Direction.Type.HORIZONTAL) {
                BlockState strung = ModBlocks.LAUNDRY.getDefaultState()
                        .with(LaundryBlock.FACING, facing);
                world.setBlockState(at, strung);

                if (world.getBlockState(at).get(LaundryBlock.FACING) != facing) {
                    context.throwGameTestException("Верёвка не встала на " + facing);
                    return;
                }
                if (!(world.getBlockEntity(at) instanceof RopeBlockEntity rope)) {
                    context.throwGameTestException("У повёрнутой верёвки нет блок-сущности");
                    return;
                }

                // И держит: поворот не должен отнимать у неё смысл.
                ItemStack hide = new ItemStack(Items.LEATHER, 1);
                if (!rope.hang(hide)) {
                    context.throwGameTestException("Повёрнутая на " + facing
                            + " верёвка ничего не держит");
                }
                if (rope.hung().stream().allMatch(ItemStack::isEmpty)) {
                    context.throwGameTestException("Вещь не повисла на " + facing);
                }

                // Поворот здания поворачивает и верёвку.
                BlockState turned = strung.rotate(BlockRotation.CLOCKWISE_90);
                if (turned.get(LaundryBlock.FACING) != facing.rotateYClockwise()) {
                    context.throwGameTestException("Верёвка не поворачивается вместе с домом: "
                            + facing + " превратилось в " + turned.get(LaundryBlock.FACING));
                }
            }
        } finally {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

}
