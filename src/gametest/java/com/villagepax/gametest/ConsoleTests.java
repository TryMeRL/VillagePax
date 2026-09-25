package com.villagepax.gametest;

import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.BuildProgress;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Levels;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.screen.ColonyNet;
import com.villagepax.sim.work.Needs;
import com.villagepax.core.culture.CultureManager;
import net.minecraft.registry.Registries;
import com.villagepax.screen.ColonyMap;
import com.villagepax.screen.ColonyNet;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.work.HaulJob;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.GatherJob;
import com.villagepax.sim.work.Housing;
import com.villagepax.screen.BuildOrders;
import com.villagepax.screen.GhostPlan;
import com.villagepax.screen.Mood;
import com.villagepax.screen.TownHallView;
import com.villagepax.sim.work.Assignments;
import com.villagepax.sim.work.Workplaces;
import com.villagepax.sim.work.Needs;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkTicker;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Пульт колонии, голограмма, подпись над жителем и карта колонии.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class ConsoleTests extends GameTestSupport {

    // --- задача 1.10: пульт колонии ---

    /**
     * Снимок показывает колонию такой, какая она есть.
     * <p>
     * Экран не считает ничего сам: свободные кровати, порции еды и запас
     * дней приходят с сервера готовыми. Поэтому проверять надо именно
     * снимок — вёрстку owo-ui игровой тест увидеть не может, у него нет
     * клиента.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "townhall")
    public void viewShowsTheColonyAsItIs(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);
            if (BuildJob.advance(world, manager, colony.id(), house.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не достроился");
            }

            Citizen starving = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            starving.setSaturation(0);
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 4));
            Housing.assignBeds(world, colony);

            TownHallView view = TownHallView.of(world, colony);

            if (!view.name().equals(colony.name()) || !view.culture().equals(NORMAN)) {
                context.throwGameTestException("Снимок не о той колонии: " + view.name());
            }
            if (view.population() != 2 || view.maxCitizens() != colony.level().maxCitizens()) {
                context.throwGameTestException("Жителей в снимке " + view.population()
                        + ", в колонии " + colony.population());
            }
            if (view.beds() != 2 || view.freeBeds() != 0) {
                context.throwGameTestException("Кроватей " + view.beds() + ", свободно "
                        + view.freeBeds() + "; в схеме дома две, и обе заняты");
            }
            // Два: ратуша и сундук в доме. Сундук появился вместе
            // с перестройкой жилья — дом без хранилища был единственным
            // зданием мода, куда нечего положить.
            if (view.containers() != 2) {
                context.throwGameTestException("Хранилищ " + view.containers()
                        + ", а стоят ратуша и дом");
            }
            if (view.meals() != 4) {
                context.throwGameTestException("Порций еды " + view.meals() + ", завезено четыре");
            }

            // Четыре хлеба по десять сытости на двоих при суточной трате
            // восемь — это ровно два дня, и это то число, по которому
            // игрок решает, ехать ли за едой.
            int expectedDays = Needs.nourishment(Items.BREAD) * 4 / (Needs.DAILY_COST * 2);
            if (view.daysOfFood() != expectedDays) {
                context.throwGameTestException("Запас дней " + view.daysOfFood()
                        + ", ожидалось " + expectedDays);
            }
            if (view.stock().count(Registries.ITEM.getId(Items.BREAD)) != 4) {
                context.throwGameTestException("Хлеб в снимке склада не найден");
            }

            if (view.buildings().size() != 1
                    || view.buildings().get(0).progress() != BuildProgress.DONE) {
                context.throwGameTestException("Зданий в снимке " + view.buildings().size()
                        + ", ожидался один готовый дом");
            }
            if (view.construction().isPresent()) {
                context.throwGameTestException("Снимок говорит о стройке, а строить нечего");
            }

            if (view.citizens().size() != 2) {
                context.throwGameTestException("Жителей в списке " + view.citizens().size());
            }
            TownHallView.CitizenLine hungry = view.citizens().stream()
                    .filter(line -> line.id().equals(starving.id()))
                    .findFirst().orElse(null);
            if (hungry == null || hungry.mood() != Mood.STARVING) {
                context.throwGameTestException("Голодающий житель показан как "
                        + (hungry == null ? "никак" : hungry.mood()));
            }
            if (!hungry.housed()) {
                context.throwGameTestException("Житель с кроватью показан бездомным");
            }

            // Предлагаются только здания своего народа, только первого
            // уровня и без ратуши: уровни растут кнопкой «Улучшить», а
            // ратуша стоит с основания. Считается ожидаемое по загруженным
            // схемам, а не числом: добавится ещё одно здание — тест не
            // придётся править.
            //
            // «Своего народа» пришлось начать считать всерьёз, когда народов
            // стало двое: до майя список схем и список своих зданий совпадали,
            // и тест этого не различал.
            List<Identifier> ourBuildings = CultureManager.get(NORMAN).buildings();
            long expectedOffers = SchematicLoader.ids().stream()
                    .filter(schematic -> BuildJob.levelOf(schematic).orElse(1) == 1)
                    .filter(schematic -> BuildJob.buildingTypeOf(schematic)
                            .filter(type -> !Levels.isTownHallType(type)
                                    && ourBuildings.contains(type))
                            .isPresent())
                    .count();
            if (view.offers().size() != expectedOffers) {
                context.throwGameTestException("Предложено " + view.offers().size()
                        + " схем, а первых уровней без ратуши " + expectedOffers);
            }

            // Профессии едут в снимке вместе с ключами названий: на клиенте,
            // подключённом к выделенному серверу, файлов датапака нет вовсе.
            if (view.professions().size() != ProfessionManager.ids().size()) {
                context.throwGameTestException("Профессий в снимке " + view.professions().size()
                        + ", загружено " + ProfessionManager.ids().size());
            }
            for (TownHallView.ProfessionLine line : view.professions()) {
                if (line.displayName().isBlank()) {
                    context.throwGameTestException("У профессии " + line.id()
                            + " в снимке нет ключа названия");
                }
            }
        } finally {
            demolish(world, house, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Снимок называет то, чего не хватает стройке, — с учётом склада.
     * <p>
     * Игрок действует по этому списку, и «не хватает» для него значит
     * «нет ни у стройки, ни в сундуках». Список, считающий только запас
     * площадки, гнал бы его за материалами, которые уже лежат в ратуше.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "townhall")
    public void viewNamesWhatTheConstructionLacks(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            TownHallView empty = TownHallView.of(world, colony);
            TownHallView.Construction lacking = empty.construction().orElse(null);

            if (lacking == null) {
                context.throwGameTestException("Размеченная стройка не попала в снимок");
            }
            if (!lacking.type().equals(FARM_TYPE) || lacking.step() != 0) {
                context.throwGameTestException("В снимке не та стройка: " + lacking.type());
            }
            if (lacking.steps() != farmPlan.plan().steps().size()) {
                context.throwGameTestException("Шагов в снимке " + lacking.steps()
                        + ", в плане " + farmPlan.plan().steps().size());
            }
            if (lacking.missing().total() <= 0) {
                context.throwGameTestException("Пустой склад, а стройке всего хватает");
            }

            stockFor(world, colony, farmPlan);
            TownHallView supplied = TownHallView.of(world, colony);
            TownHallView.Construction enough = supplied.construction().orElseThrow();

            if (enough.missing().total() != 0) {
                context.throwGameTestException("Материалы на складе, а снимок просит ещё "
                        + enough.missing().total() + " штук");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Заказ здания отвергает то, что испортило бы мир, и при отказе ничего
     * не меняет.
     * <p>
     * Проверки живут в одном месте на команду и на кнопку экрана: кнопка,
     * проверяющая меньше команды, была бы дыркой в обход неё.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "townhall")
    public void orderRefusesWhatWouldBreakTheWorld(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            BuildOrders.Result unknown = BuildOrders.place(manager, colony,
                    new Identifier("villagepax", "norman/no_such_lvl1"), anchor, BlockRotation.NONE);
            if (!(unknown instanceof BuildOrders.Result.NoSchematic)) {
                context.throwGameTestException("Незнакомая схема принята: " + unknown);
            }

            BlockPos faraway = hall.add(200, 0, 200);
            BuildOrders.Result outside = BuildOrders.place(manager, colony, HOUSE_SCHEMATIC,
                    faraway, BlockRotation.NONE);
            if (!(outside instanceof BuildOrders.Result.OutsideClaim)) {
                context.throwGameTestException("Стройка за границами колонии принята: " + outside);
            }
            if (!colony.buildings().isEmpty()) {
                context.throwGameTestException("Отказ всё-таки разметил стройку");
            }

            BuildOrders.Result placed = BuildOrders.place(manager, colony, HOUSE_SCHEMATIC,
                    anchor, BlockRotation.NONE);
            if (!(placed instanceof BuildOrders.Result.Placed done)) {
                context.throwGameTestException("Законный заказ отвергнут: " + placed);
                return;
            }
            if (!done.site().type().equals(HOUSE_TYPE) || done.site().level() != 1) {
                context.throwGameTestException("Размечено не то: " + done.site().type()
                        + " ур. " + done.site().level());
            }
            if (colony.buildings().size() != 1) {
                context.throwGameTestException("Зданий в колонии " + colony.buildings().size());
            }

            BuildOrders.Result clash = BuildOrders.place(manager, colony, HOUSE_SCHEMATIC,
                    anchor.add(1, 0, 1), BlockRotation.NONE);
            if (!(clash instanceof BuildOrders.Result.Overlaps)) {
                context.throwGameTestException("Наложение следов принято: " + clash);
            }
            if (colony.buildings().size() != 1) {
                context.throwGameTestException("Отказ по наложению всё-таки добавил здание");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Смена дела вступает в силу сразу: фермер получает ферму, не дожидаясь
     * рассвета.
     * <p>
     * Решение заказчика — профессии назначаются сами, но игрок вправе
     * переназначить. Кнопка, действующая только со следующего дня,
     * выглядела бы сломанной.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "townhall")
    public void assignmentGivesTheFarmerHisFarmAtOnce(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            Citizen worker = hireWithBody(world, colony, HaulJob.COURIER, hall.up());

            Assignments.Result done = Assignments.set(world, manager, colony, worker.id(),
                    Optional.of(FarmJob.FARMER));
            if (done != Assignments.Result.DONE) {
                context.throwGameTestException("Смена дела отвергнута: " + done);
            }
            if (!worker.profession().orElseThrow().equals(FarmJob.FARMER)) {
                context.throwGameTestException("Профессия не сменилась: "
                        + worker.profession().orElse(null));
            }
            if (Workplaces.of(colony, worker).map(Building::id).filter(farm.id()::equals).isEmpty()) {
                context.throwGameTestException("Новому фермеру не досталась ферма — "
                        + "мастерские раздаются только на смене суток");
            }

            Assignments.Result unknown = Assignments.set(world, manager, colony, worker.id(),
                    Optional.of(new Identifier("villagepax", "no_such_profession")));
            if (unknown != Assignments.Result.NO_SUCH_PROFESSION) {
                context.throwGameTestException("Несуществующая профессия принята: " + unknown);
            }
            if (!worker.profession().orElseThrow().equals(FarmJob.FARMER)) {
                context.throwGameTestException("Отказ всё-таки сменил профессию");
            }

            Assignments.Result stranger = Assignments.set(world, manager, colony,
                    UUID.randomUUID(), Optional.of(FarmJob.FARMER));
            if (stranger != Assignments.Result.NO_SUCH_CITIZEN) {
                context.throwGameTestException("Чужой житель принят: " + stranger);
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Дерево валится целиком: с ветками и кроной, без висящих остатков.
     * <p>
     * Прежний обход шёл одной колонной над подножием и снимал крону
     * коробкой в три блока. У дуба ветки отходят в сторону, у тёмного дуба
     * ствол вообще толщиной в четыре бревна, а крона шире трёх блоков —
     * и игрок видел обрубок с висящей листвой вместо сваленного дерева.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wholetree")
    public void wholeTreeComesDownWithBranchesAndCanopy(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic hutPlan = schematic(context, LUMBERJACK_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building hut = plan(colony, anchor, LUMBERJACK_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, hutPlan);
            BuildJob.advance(world, manager, colony.id(), hut.id(), 10_000);

            BlockPos tile = GatherJob.groveTiles(hut).get(0);

            // Ствол в шесть брёвен, ветка углом и крона шире прежней коробки.
            for (int dy = 0; dy < 6; dy++) {
                world.setBlockState(tile.up(dy), Blocks.OAK_LOG.getDefaultState());
            }
            BlockPos elbow = tile.add(1, 4, 1);
            BlockPos tip = elbow.add(1, 0, 0);
            world.setBlockState(elbow, Blocks.OAK_LOG.getDefaultState());
            world.setBlockState(tip, Blocks.OAK_LOG.getDefaultState());

            List<BlockPos> canopy = new ArrayList<>();
            for (int dx = -2; dx <= 3; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 5; dy <= 6; dy++) {
                        BlockPos at = tile.add(dx, dy, dz);
                        if (world.getBlockState(at).isAir()) {
                            world.setBlockState(at, Blocks.OAK_LEAVES.getDefaultState());
                            canopy.add(at);
                        }
                    }
                }
            }
            if (canopy.size() < 40) {
                context.throwGameTestException("Крона не поставилась: листьев "
                        + canopy.size());
            }

            Warehouse.of(world, colony).add(new ItemStack(Items.OAK_SAPLING, 4));
            int logsBefore = Warehouse.of(world, colony).count(Items.OAK_LOG);

            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, tile.up(8));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, woodsman, 60, Schedule.MORNING_WORK);

            // Восемь брёвен: шесть ствола и два ветки.
            if (Warehouse.of(world, colony).count(Items.OAK_LOG) < logsBefore + 8) {
                context.throwGameTestException("Ветку не срубили: брёвен на складе "
                        + (Warehouse.of(world, colony).count(Items.OAK_LOG) - logsBefore)
                        + " из восьми");
            }
            if (!world.getBlockState(elbow).isAir() || !world.getBlockState(tip).isAir()) {
                context.throwGameTestException("Ветка осталась висеть");
            }
            for (BlockPos leaf : canopy) {
                if (!world.getBlockState(leaf).isAir()) {
                    context.throwGameTestException("Крона осталась висеть: "
                            + world.getBlockState(leaf).getBlock() + " в " + leaf.toShortString());
                }
            }

            // Стены мастерской из тёмного дуба остались на месте: они тоже брёвна.
            BlockPos post = anchor.add(0, 1, 0);
            if (!world.getBlockState(post).isIn(BlockTags.LOGS)) {
                context.throwGameTestException("Связный обход дошёл до стены мастерской");
            }
        } finally {
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- задача 1.11: голограмма ---

    /**
     * Примерка места ничего не меняет в колонии.
     * <p>
     * Голограмма спрашивает сервер при каждом сдвиге на блок — то есть
     * несколько раз в секунду. Если бы проверка что-то откладывала
     * в колонию, водя призраком по земле игрок засеивал бы её стройками.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hologram")
    public void probingASpotChangesNothing(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (int step = 0; step < 20; step++) {
                BuildOrders.Result verdict = BuildOrders.check(colony, HOUSE_SCHEMATIC,
                        anchor.add(step, 0, 0), BlockRotation.NONE);
                if (!(verdict instanceof BuildOrders.Result.Placed)) {
                    context.throwGameTestException("Примерка на своей земле отвергнута: " + verdict);
                }
            }

            if (!colony.buildings().isEmpty()) {
                context.throwGameTestException("Примерка разметила " + colony.buildings().size()
                        + " стройек, а не должна ни одной");
            }

            // А заказ — меняет, и ровно одну.
            BuildOrders.place(manager, colony, HOUSE_SCHEMATIC, anchor, BlockRotation.NONE);
            if (colony.buildings().size() != 1) {
                context.throwGameTestException("Заказ не разметил стройку");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Приёмка задачи 1.11 со стороны данных: стройка встаёт ровно там
     * и ровно так, как показывал призрак.
     * <p>
     * Голограмма отправляет место и поворот, которые игрок видел; если
     * заказ поставит здание иначе, вся задача бессмысленна.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hologram")
    public void orderLandsExactlyWhereTheGhostStood(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos ghost = context.getAbsolutePos(new BlockPos(3, 8, 5));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            BuildOrders.Result result = BuildOrders.place(manager, colony, HOUSE_SCHEMATIC,
                    ghost, BlockRotation.CLOCKWISE_90);

            if (!(result instanceof BuildOrders.Result.Placed placed)) {
                context.throwGameTestException("Заказ по месту голограммы отвергнут: " + result);
                return;
            }
            if (!placed.site().anchor().equals(ghost)) {
                context.throwGameTestException("Стройка встала не там: "
                        + placed.site().anchor().toShortString() + " вместо "
                        + ghost.toShortString());
            }
            if (placed.site().rotation() != BlockRotation.CLOCKWISE_90) {
                context.throwGameTestException("Поворот потерялся: " + placed.site().rotation());
            }

            Building inColony = colony.buildings().get(0);
            if (!inColony.anchor().equals(ghost)
                    || inColony.rotation() != BlockRotation.CLOCKWISE_90) {
                context.throwGameTestException("В колонии здание лежит иначе, чем вернул заказ");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * План призрака переживает дорогу по сети.
     * <p>
     * Клиент схем не видит — они в датапаке сервера, — поэтому план едет
     * пакетом. Потерянный при этом блок означает дырку в призраке, а
     * потерянное состояние — дверь, повёрнутую не туда.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hologram")
    public void ghostPlanSurvivesTheWire(TestContext context) {
        Schematic townHall = schematic(context, TOWN_HALL_SCHEMATIC);

        List<GhostPlan.Ghost> blocks = new ArrayList<>();
        for (BuildStep step : townHall.plan().steps()) {
            if (step.placesBlock()) {
                blocks.add(new GhostPlan.Ghost(step.pos(), townHall.blockAt(step.paletteIndex())));
            }
        }

        GhostPlan sent = new GhostPlan(TOWN_HALL_SCHEMATIC, townHall.size(), blocks);
        PacketByteBuf buf = PacketByteBufs.create();
        sent.write(buf);
        GhostPlan back = GhostPlan.read(buf);

        if (!back.schematic().equals(sent.schematic()) || !back.size().equals(sent.size())) {
            context.throwGameTestException("Схема или размер потерялись: " + back.schematic()
                    + " " + back.size());
        }
        if (back.blocks().size() != sent.blocks().size()) {
            context.throwGameTestException("Блоков доехало " + back.blocks().size()
                    + " из " + sent.blocks().size());
        }
        for (int index = 0; index < sent.blocks().size(); index++) {
            GhostPlan.Ghost before = sent.blocks().get(index);
            GhostPlan.Ghost after = back.blocks().get(index);

            if (!before.pos().equals(after.pos()) || before.state() != after.state()) {
                context.throwGameTestException("Блок " + index + " доехал искажённым: "
                        + before.state() + " в " + before.pos().toShortString() + " стало "
                        + after.state() + " в " + after.pos().toShortString());
            }
        }
        if (buf.readableBytes() != 0) {
            context.throwGameTestException("В пакете осталось " + buf.readableBytes()
                    + " непрочитанных байт");
        }

        // Расчистка в призрак не входит: игрок выбирает, как встанет здание,
        // а не что будет снесено.
        if (sent.blocks().size() >= townHall.plan().steps().size()) {
            context.throwGameTestException("В призрак попали шаги расчистки");
        }

        context.complete();
    }

    // --- подпись над жителем ---

    /**
     * Над жителем видно имя и ремесло.
     * <p>
     * Просьба заказчика. Ремесло рядом с именем не украшение: игрок раздаёт
     * работу и хочет видеть, кто перед ним, не открывая пульта.
     * <p>
     * Проверяется ключ перевода, а не готовая строка: на сервере словаря
     * мода нет, и {@code getString} вернул бы сам ключ. Игрок увидит
     * подпись собранной у себя на клиенте — а собирать её не из чего,
     * если ключ не тот.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "labels")
    public void citizenWearsNameAndCraft(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(mason.entityUuid().orElseThrow());

            WorkTicker.decide(world, manager, colony, mason, Schedule.MORNING_WORK);

            if (!body.isCustomNameVisible()) {
                context.throwGameTestException("Подпись жителя не показывается");
            }
            Text label = body.getCustomName();
            if (label == null || !(label.getContent() instanceof TranslatableTextContent craft)
                    || !craft.getKey().equals("villagepax.citizen.label")) {
                context.throwGameTestException("В подписи нет ремесла: " + label);
            }

            // Ремесло отобрали — осталось одно имя, и оно настоящее.
            mason.setProfession(null);
            WorkTicker.decide(world, manager, colony, mason, Schedule.MORNING_WORK);

            Text plain = body.getCustomName();
            if (plain == null || !plain.getString().equals(mason.fullName())) {
                context.throwGameTestException("Житель без ремесла подписан не своим именем: "
                        + plain);
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- карта колонии ---

    /**
     * Подпись здания висит над его крышей, а не внутри дома.
     * <p>
     * Место подписи считает сервер, потому что размер здания знает схема,
     * а схем у клиента нет. Ошибись здесь — и надпись окажется в стене,
     * где её не видно, или в небе, где непонятно, чья она.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "labels")
    public void colonySignsHangOverTheRoofs(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            ColonyMap map = ColonyNet.mapOf(colony);

            if (!map.id().equals(colony.id()) || !map.name().equals(colony.name())) {
                context.throwGameTestException("Карта не про эту колонию: " + map.name());
            }
            if (map.radius() != colony.level().claimRadiusChunks()) {
                context.throwGameTestException("Радиус владений на карте " + map.radius()
                        + ", а у колонии " + colony.level().claimRadiusChunks());
            }
            if (map.signs().size() != 1) {
                context.throwGameTestException("Подписей " + map.signs().size()
                        + ", а здание одно");
            }

            ColonyMap.Sign sign = map.signs().get(0);
            if (sign.done()) {
                context.throwGameTestException("Размеченный дом объявлен готовым");
            }

            // Ровно на высоту схемы над якорем и по её середине.
            BlockPos expected = anchor.add(housePlan.size().getX() / 2, housePlan.size().getY(),
                    housePlan.size().getZ() / 2);
            if (!sign.at().equals(expected)) {
                context.throwGameTestException("Подпись висит на " + sign.at().toShortString()
                        + ", а крыша кончается на " + expected.toShortString());
            }
            if (sign.at().getY() <= anchor.getY()) {
                context.throwGameTestException("Подпись оказалась не выше основания дома");
            }

            // Достроили — подпись перестаёт говорить «строится».
            stockFor(world, colony, housePlan);
            BuildJob.advance(world, manager, colony.id(), house.id(), 10_000);

            if (!ColonyNet.mapOf(colony).signs().get(0).done()) {
                context.throwGameTestException("Дом готов, а подпись всё ещё про стройку");
            }
        } finally {
            demolish(world, house, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

}
