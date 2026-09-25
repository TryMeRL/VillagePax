package com.villagepax.gametest;

import com.villagepax.block.entity.TownHallBlockEntity;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import net.minecraft.util.math.Vec3d;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.Box;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.sim.work.Hauling;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.work.HaulJob;
import com.villagepax.sim.work.JobState;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import java.util.List;
import java.util.UUID;

/**
 * Склад поверх настоящих сундуков, курьер и носильщик со слотами.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class HaulingTests extends GameTestSupport {

    // --- задача 1.7а: склад поверх реальных контейнеров ---

    /**
     * Ратуша — стартовое хранилище колонии.
     * <p>
     * Без него получается курица и яйцо: чтобы построить склад, нужен склад.
     * Поэтому ратуша входит в склад всегда, а игрок кладёт материалы рукой.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void townHallIsTheColonyStartingStorage(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Warehouse warehouse = Warehouse.of(world, colony);
            if (warehouse.containerCount() != 1) {
                context.throwGameTestException("Хранилищ у новой колонии " + warehouse.containerCount()
                        + ", ожидалась одна ратуша");
            }
            if (!warehouse.isEmpty() || warehouse.totalItems() != 0) {
                context.throwGameTestException("Новая ратуша не пуста");
            }

            // Положили и забрали: склад — вид поверх настоящего контейнера.
            warehouse.add(new ItemStack(Items.OAK_LOG, 40));
            if (Warehouse.of(world, colony).count(Items.OAK_LOG) != 40) {
                context.throwGameTestException("Склад не увидел то, что в него положили");
            }
            if (!(world.getBlockEntity(hall) instanceof TownHallBlockEntity chest) || chest.isEmpty()) {
                context.throwGameTestException("Предметы легли не в ратушу");
            }

            if (Warehouse.of(world, colony).take(Items.OAK_LOG, 41)) {
                context.throwGameTestException("Склад выдал больше, чем в нём есть");
            }
            if (Warehouse.of(world, colony).count(Items.OAK_LOG) != 40) {
                context.throwGameTestException("Отказ в выдаче не должен трогать склад");
            }
            if (!Warehouse.of(world, colony).take(Items.OAK_LOG, 40)
                    || Warehouse.of(world, colony).count(Items.OAK_LOG) != 0) {
                context.throwGameTestException("Выдача целиком не сработала");
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Выдача идёт «всё или ничего» даже когда нужное размазано по контейнерам. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void warehouseTakesAcrossSeveralContainers(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не достроилось, второго хранилища не появится");
            }

            // Достроенное здание принесло колонии сундук на складском маркере.
            Warehouse warehouse = Warehouse.of(world, colony);
            if (warehouse.containerCount() < 2) {
                context.throwGameTestException("Сундук здания не вошёл в склад: хранилищ "
                        + warehouse.containerCount());
            }
            List<BlockPos> storage = BuildJob.pointsOfInterest(site, schematic, MarkerKind.STORAGE);
            if (storage.isEmpty()) {
                context.throwGameTestException("У здания нет складской точки");
            }
            for (BlockPos spot : storage) {
                if (!world.getBlockState(spot).isOf(Blocks.CHEST)) {
                    context.throwGameTestException("На складской точке не сундук: "
                            + world.getBlockState(spot).getBlock());
                }
            }

            // Раскладываем по десятку в каждое хранилище и просим всё сразу.
            warehouse.add(new ItemStack(Items.COBBLESTONE, 10));
            if (world.getBlockEntity(storage.get(0)) instanceof Inventory chest) {
                chest.setStack(0, new ItemStack(Items.COBBLESTONE, 10));
            }

            Warehouse merged = Warehouse.of(world, colony);
            if (merged.count(Items.COBBLESTONE) != 20) {
                context.throwGameTestException("Склад не сложил содержимое контейнеров: "
                        + merged.count(Items.COBBLESTONE));
            }
            if (!merged.take(Items.COBBLESTONE, 15)) {
                context.throwGameTestException("Выдача через два контейнера не прошла");
            }
            if (Warehouse.of(world, colony).count(Items.COBBLESTONE) != 5) {
                context.throwGameTestException("После выдачи осталось не то количество");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Излишки падают на землю, а не исчезают.
     * <p>
     * Контейнеры конечны, и потерять брёвна с расчистки молча хуже, чем
     * оставить игроку уборку: пропажу он заметит только по нехватке
     * материалов через полчаса.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullWarehouseScattersInsteadOfLosing(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            if (!(world.getBlockEntity(hall) instanceof TownHallBlockEntity storage)) {
                context.throwGameTestException("У ратуши нет хранилища");
                return;
            }
            for (int slot = 0; slot < storage.size(); slot++) {
                storage.setStack(slot, new ItemStack(Items.STONE, 64));
            }

            Warehouse warehouse = Warehouse.of(world, colony);
            ItemStack leftover = warehouse.add(new ItemStack(Items.OAK_LOG, 7));
            if (leftover.getCount() != 7) {
                context.throwGameTestException("Полный склад что-то принял: осталось "
                        + leftover.getCount() + " из 7");
            }

            warehouse.addOrScatter(world, hall.up(), new ItemStack(Items.OAK_LOG, 7));
            Box around = new Box(hall).expand(4.0);
            int dropped = world.getEntitiesByClass(ItemEntity.class, around,
                    entity -> entity.getStack().isOf(Items.OAK_LOG)).size();
            if (dropped == 0) {
                context.throwGameTestException("Излишки исчезли вместо того, чтобы упасть на землю");
            }

            world.getEntitiesByClass(ItemEntity.class, around, entity -> true).forEach(Entity::discard);
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Склад без контейнеров ничего не выдаёт и не принимает — но и не падает. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void colonyWithoutContainersHasNoStorage(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos nowhere = context.getAbsolutePos(new BlockPos(3, 5, 3));

        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Без ратуши", nowhere);
        manager.add(colony);

        try {
            Warehouse warehouse = Warehouse.of(world, colony);

            if (warehouse.containerCount() != 0 || !warehouse.isEmpty()) {
                context.throwGameTestException("Склад без контейнеров не пуст");
            }
            if (warehouse.take(Items.OAK_LOG, 1)) {
                context.throwGameTestException("Пустой склад что-то выдал");
            }
            if (warehouse.add(new ItemStack(Items.OAK_LOG, 3)).getCount() != 3) {
                context.throwGameTestException("Складу без контейнеров удалось что-то принять");
            }
            if (!warehouse.has(Items.OAK_LOG, 0)) {
                context.throwGameTestException("Нулевого количества хватает всегда");
            }
        } finally {
            manager.remove(colony.id());
        }

        context.complete();
    }

    // --- задача 1.7б: курьер ---

    /**
     * Приёмка задачи 1.7: курьер носит материалы со склада на стройку.
     * <p>
     * Здесь же проверяется и решение заказчика «рядом сам, далеко — курьер»:
     * та же площадка без курьера встаёт на первом же блоке, хотя склад полон.
     * <p>
     * Ходьба заменена телепортом: тест проверяет решения стратегии, а не поиск
     * пути. Что жители действительно ходят, доказывает отдельный тест на
     * настоящих тиках мира.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "courier")
    public void courierCarriesMaterialsWhenStorageIsFar(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        // Далеко по высоте, а не по горизонтали: те же чанки заведомо загружены.
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 20, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

            if (BuildJob.storageIsNearby(Warehouse.of(world, colony), site)) {
                context.throwGameTestException("Площадка оказалась рядом со складом — "
                        + "курьера проверять нечем");
            }

            // Без курьера стройка встаёт, хотя склад полон: до него не дотянуться.
            BuildJob.Outcome alone = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (alone != BuildJob.Outcome.WAITING_FOR_MATERIALS) {
                context.throwGameTestException("Без курьера при далёком складе ожидалось ожидание, "
                        + "получено: " + alone);
            }
            int stalledAt = site.nextStep();

            Citizen courier = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            runWork(world, manager, colony, courier, 12, Schedule.MORNING_WORK);

            if (site.stock().total() == 0) {
                context.throwGameTestException("Курьер ничего не принёс на площадку");
            }
            if (courier.jobState().isCarrying()) {
                context.throwGameTestException("Курьер остался с грузом в руках: "
                        + courier.jobState().phase().id());
            }

            // Теперь билдеру есть из чего строить — из запаса площадки.
            BuildJob.Outcome withCourier = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (site.nextStep() <= stalledAt) {
                context.throwGameTestException("Стройка не двинулась после подвоза: шаг "
                        + site.nextStep() + ", было " + stalledAt + ", исход " + withCourier);
            }
        } finally {
            demolish(world, site, schematic);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Груз, который нести уже некуда, возвращается на склад.
     * <p>
     * Самое коварное место всей задачи: задание отменилось, а тридцать брёвен
     * остались в руках. Обнулить состояние целиком — значит удалить их из мира,
     * и игрок никогда не поймёт, куда они девались.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "courier")
    public void strandedLoadGoesBackToStorage(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen courier = hireWithBody(world, colony, HaulJob.COURIER, hall.up());

            // Нёс на стройку, которой больше нет.
            courier.setJobState(JobState.startAt(UUID.randomUUID(), JobState.Phase.TO_SITE)
                    .carrying(Registries.ITEM.getId(Items.OAK_LOG), 7)
                    .withPhase(JobState.Phase.IDLE));
            if (!courier.jobState().hasStrandedLoad()) {
                context.throwGameTestException("Состояние не считается брошенным грузом");
            }

            runWork(world, manager, colony, courier, 4, Schedule.MORNING_WORK);

            if (Warehouse.of(world, colony).count(Items.OAK_LOG) != 7) {
                context.throwGameTestException("Брошенный груз не вернулся на склад: брёвен "
                        + Warehouse.of(world, colony).count(Items.OAK_LOG));
            }
            if (courier.jobState().isCarrying()) {
                context.throwGameTestException("Груз остался в руках после сдачи");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Билдер работает только в пределах вытянутой руки — но пустые шаги
     * расчистки проскакивает не сходя с места.
     * <p>
     * Второе важнее первого: проверка досягаемости стоит после отсечения
     * пустых шагов, иначе билдер шёл бы к каждой из ста одиннадцати пустых
     * позиций расчистки, и вся выгода от «шагать к участку» пропала бы.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "courier")
    public void builderReachesOnlyAsFarAsHisArm(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

            Vec3d faraway = Vec3d.ofCenter(anchor).add(0, 40, 0);
            BuildJob.Outcome tooFar = BuildJob.advance(world, manager, colony.id(), site.id(), 10, faraway);

            if (tooFar != BuildJob.Outcome.OUT_OF_REACH) {
                context.throwGameTestException("Издалека ожидался переход, получено: " + tooFar);
            }
            if (site.nextStep() == 0) {
                context.throwGameTestException("Пустые шаги расчистки должны проскакивать "
                        + "не сходя с места, иначе билдер обойдёт всю площадку пешком");
            }

            BuildStep next = schematic.plan().steps().get(site.nextStep());
            if (!next.placesBlock()) {
                context.throwGameTestException("Билдер встал не на установке блока");
            }

            // Подошли — и работа пошла.
            Vec3d atWork = Vec3d.ofCenter(BuildJob.worldPos(site, schematic.size(), next.pos()));
            int before = site.nextStep();
            BuildJob.Outcome close = BuildJob.advance(world, manager, colony.id(), site.id(), 3, atWork);

            if (site.nextStep() <= before) {
                context.throwGameTestException("Вплотную к блоку работа не пошла: " + close);
            }
        } finally {
            demolish(world, site, schematic);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Остатки со стройплощадки возвращаются на склад, когда здание сдано.
     * <p>
     * Запас площадки — счётчик: физически предметов нигде нет. Не вернуть
     * их — значит удалить из экономики колонии молча. Курьер приносит по
     * полстопки, зданию нужно четыре блока, и двадцать восемь перестают
     * существовать; игрок заметит это однажды, когда материалы кончатся
     * без причины. Сюда же попадает добыча с расчистки, сложенная у стройки,
     * когда склад был далеко.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "courier")
    public void leftoverSiteStockReturnsToStorageWhenDone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

            // Заведомо ненужный схеме предмет: если он вернётся, значит
            // возвращается вообще всё, а не только угаданные виды.
            Identifier surplus = Registries.ITEM.getId(Items.DIAMOND);
            site.stock().add(surplus, 5);

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не достроилось");
            }

            if (!site.stock().isEmpty()) {
                context.throwGameTestException("Запас площадки не опустел после сдачи: "
                        + site.stock().total() + " штук осталось висеть в счётчике");
            }
            if (Warehouse.of(world, colony).count(Items.DIAMOND) != 5) {
                context.throwGameTestException("Остатки не вернулись на склад: алмазов "
                        + Warehouse.of(world, colony).count(Items.DIAMOND) + " из 5");
            }
        } finally {
            demolish(world, site, schematic);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- носильщик со слотами ---

    /**
     * Один житель поднимает стройку без курьера.
     * <p>
     * С этого начался разговор: курьеру нужен дом, дому нужны материалы
     * у стройки, а материалы к далёкой стройке без курьера не попадают.
     * Замкнутый круг. Теперь первый житель делает всё сам — ходит на склад,
     * набирает слоты, возвращается и строит.
     * <p>
     * Склад намеренно дальше, чем вытянутая рука билдера: иначе он брал бы
     * из сундука не сходя с места, и проверять было бы нечего.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slots")
    public void loneBuilderFetchesMaterialsHimself(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(FAR_SITE);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            if (BuildJob.storageIsNearby(Warehouse.of(world, colony), site)) {
                context.throwGameTestException("Посылка теста не выполнена: склад оказался "
                        + "под боком, и носить ничего не надо");
            }
            if (!Hauling.nobodyElseWillCarry(colony)) {
                context.throwGameTestException("Посылка теста не выполнена: в колонии есть "
                        + "кому носить");
            }

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 600, Schedule.MORNING_WORK);

            if (!site.isOperational()) {
                context.throwGameTestException("Дом не достроился без курьера: шаг "
                        + site.nextStep() + " из " + housePlan.plan().steps().size()
                        + ", на площадке " + site.stock().contents()
                        + ", на складе " + Warehouse.of(world, colony).tally().contents()
                        + ", не хватает " + Materials.shortfall(housePlan, site, 64)
                        + ", фаза " + mason.jobState().phase().id());
            }

            // Материалы обязаны сойтись, и это не придирка к бухгалтерии.
            // Носильщик со слотами легко начинает печатать предметы: стоит
            // после разгрузки поработать с устаревшим состоянием, и груз
            // возвращается в руки, уже лежа на площадке. Так на площадке
            // и оказалось семнадцать сотен булыжника вместо двадцати пяти.
            // Здесь склад завезли ровно под схему, сносить в пустоте нечего,
            // значит после сдачи дома не должно остаться ничего.
            int leftOver = Warehouse.of(world, colony).totalItems();
            if (leftOver != 0) {
                context.throwGameTestException("После стройки на складе осталось " + leftOver
                        + " предметов, а завозили ровно под схему: значит, где-то "
                        + "размножились");
            }
            if (mason.jobState().isCarrying()) {
                context.throwGameTestException("Билдер остался с грузом в руках: "
                        + mason.jobState().carried());
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Появился курьер — билдер снова только строит.
     * <p>
     * Иначе решение заказчика «стройка под боком у склада идёт сама,
     * вынесенная за околицу требует людей» перестало бы что-либо значить:
     * география снова стала бы декорацией. Носить билдер берётся только
     * когда носить больше <b>некому</b>.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slots")
    public void builderLeavesHaulingToTheCourier(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(FAR_SITE);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            // Курьер в колонии есть — пусть даже он ещё не в загруженном чанке.
            Citizen porter = evenNewborn("Носильщик", "", NORMAN, Gender.MALE);
            porter.setProfession(HaulJob.COURIER);
            colony.addCitizen(porter);

            if (Hauling.nobodyElseWillCarry(colony)) {
                context.throwGameTestException("Курьера в колонии не заметили");
            }

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            // Меньше терпения: пока есть надежда на курьера, билдер ждёт
            // на месте. Что он вмешается, когда курьер не справится,
            // проверяет соседний тест.
            runWork(world, manager, colony, mason, Hauling.PATIENCE / 2, Schedule.MORNING_WORK);

            if (mason.jobState().isCarrying()) {
                context.throwGameTestException("Билдер сразу понёс материалы сам, хотя есть "
                        + "курьер и терпение не вышло: " + mason.jobState().carried());
            }
            if (mason.jobState().phase() == JobState.Phase.TO_STORAGE) {
                context.throwGameTestException("Билдер сразу пошёл на склад, хотя есть курьер");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Курьер не справляется — билдер вмешивается.
     * <p>
     * Решение заказчика: помогать, <b>если курьер не справляется</b>, а не
     * только когда его нет вовсе. Курьер может спать, застрять, не дойти
     * или не успевать — стройка стоять из-за этого не должна.
     * <p>
     * Здесь курьер есть, но телом в мире не появлялся: работать он не может
     * никак. Это самый чистый способ изобразить «не справляется», не
     * подпирая тест сном и заблудившимся путём.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slots")
    public void builderStepsInWhenTheCourierCannotCope(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(FAR_SITE);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            Citizen idle = evenNewborn("Лежебока", "", NORMAN, Gender.MALE);
            idle.setProfession(HaulJob.COURIER);
            colony.addCitizen(idle);

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 600, Schedule.MORNING_WORK);

            if (!site.isOperational()) {
                context.throwGameTestException("Стройка встала при неработающем курьере: шаг "
                        + site.nextStep() + " из " + housePlan.plan().steps().size()
                        + ", тел рядом " + world.getEntitiesByClass(CitizenEntity.class,
                                new Box(hall).expand(48), alive -> true).size());
            }
            int leftOver = Warehouse.of(world, colony).totalItems();
            if (leftOver != 0) {
                context.throwGameTestException("После стройки осталось " + leftOver
                        + " предметов, а завозили ровно под схему");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * За одну ходку носильщик берёт несколько видов груза.
     * <p>
     * Раньше слот был один, и дом из шестнадцати видов блоков требовал
     * шестнадцати походов через полдеревни — работа ради работы, которую
     * игрок и видел.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slots")
    public void oneTripCarriesSeveralKinds(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(FAR_SITE);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            Citizen porter = hireWithBody(world, colony, HaulJob.COURIER, hall.up());

            int mostSlots = 0;
            for (int round = 0; round < 40; round++) {
                runWork(world, manager, colony, porter, 1, Schedule.MORNING_WORK);
                mostSlots = Math.max(mostSlots, porter.jobState().usedSlots());
            }

            if (mostSlots < 2) {
                context.throwGameTestException("Курьер за ходку взял видов груза: " + mostSlots
                        + ". Слотов у него " + Hauling.slots() + ", а дому нужно много разного");
            }
            if (mostSlots > Hauling.slots()) {
                context.throwGameTestException("Курьер унёс " + mostSlots
                        + " видов груза, а слотов у него " + Hauling.slots());
            }

            // И принесённое доходит до площадки, а не остаётся в руках.
            if (site.stock().isEmpty()) {
                context.throwGameTestException("На площадку ничего не донесли");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

}
