package com.villagepax.gametest;

import com.villagepax.block.ModBlocks;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.ColonyFounder;
import com.villagepax.sim.FoundingOutcome;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import net.minecraft.util.math.Vec3d;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Levels;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.ModTags;
import com.villagepax.sim.build.Decor;
import net.minecraft.block.Block;
import com.villagepax.sim.build.BuildCategory;
import com.villagepax.sim.build.BuildPlan;
import com.villagepax.sim.build.BuildPlanner;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.PointOfInterest;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.build.SchematicParser;
import com.villagepax.sim.work.JobState;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.Housing;
import com.villagepax.screen.TownHallView;
import com.villagepax.sim.work.Schedule;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resource.Resource;
import net.minecraft.util.math.Vec3i;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.work.BuilderJob;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Стройка: схемы, билдер, где он стоит, вторые уровни, декор, заказы и то, что колония делает сама.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class BuildTests extends GameTestSupport {

    // --- задача 1.5: схемы зданий и план стройки ---

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void schematicIsLoadedFromDatapack(TestContext context) {
        Schematic schematic = loadedTownHall(context);
        Vec3i size = schematic.size();

        if (!size.equals(new Vec3i(7, 6, 7))) {
            context.throwGameTestException("Размер схемы " + size.toShortString() + ", ожидался 7, 6, 7");
        }
        if (schematic.palette().isEmpty()) {
            context.throwGameTestException("Палитра схемы пуста");
        }

        int volume = size.getX() * size.getY() * size.getZ();
        if (schematic.blocks().size() != volume) {
            context.throwGameTestException("Блоков в схеме " + schematic.blocks().size()
                    + ", а объём " + volume + " — часть схемы потерялась при разборе");
        }

        // Каждый блок схемы обязан стать либо установкой, либо расчисткой:
        // потерянный блок — это дырка в готовом здании.
        BuildPlan plan = schematic.plan();
        long clearing = plan.steps().stream().filter(step -> !step.placesBlock()).count();
        if (plan.blockCount() + clearing != plan.steps().size()) {
            context.throwGameTestException("Шаги не сходятся: установок " + plan.blockCount()
                    + ", расчисток " + clearing + ", всего " + plan.steps().size());
        }
        // Шагов теперь больше, чем клеток схемы, и это правило, а не
        // погрешность: сверх следа план расчищает ПОДХОД ко входу —
        // проход в рост человека на три шага от порога. Им билдер и валит
        // дерево, выросшее у калитки, из-за которого на ферму было не войти.
        int inside = 0;
        int outside = 0;
        for (BuildStep step : plan.steps()) {
            BlockPos at = step.pos();
            boolean within = at.getX() >= 0 && at.getY() >= 0 && at.getZ() >= 0
                    && at.getX() < size.getX() && at.getY() < size.getY() && at.getZ() < size.getZ();
            if (within) {
                inside++;
                continue;
            }
            outside++;
            if (step.placesBlock()) {
                context.throwGameTestException("За следом здания план ставит блок на "
                        + at.toShortString() + ": туда билдеру можно только с топором");
            }
        }
        if (inside != volume) {
            context.throwGameTestException("Клеток схемы в плане " + inside
                    + " при объёме " + volume + " — часть схемы потерялась");
        }
        if (outside == 0) {
            context.throwGameTestException("Подход ко входу не расчищается: "
                    + "дерево у порога снова останется стоять");
        }

        context.complete();
    }

    /**
     * Маркеры — то, чем одна схема описывает и геометрию здания, и его логику.
     * Проверяется вся сделка: каждый род найден, ни один маркер не остался
     * блоком к установке, и место каждого расчищается.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void schematicMarkersBecomePointsOfInterest(TestContext context) {
        Schematic schematic = loadedTownHall(context);
        BuildPlan plan = schematic.plan();

        for (MarkerKind kind : MarkerKind.values()) {
            if (plan.positionsOf(kind).isEmpty()) {
                context.throwGameTestException("В схеме ратуши не найден маркер: " + kind.id());
            }
        }

        // Служебный блок мода не должен оказаться в шагах установки: маркер
        // либо расчищается, либо подменяется обстановкой своего рода.
        for (BuildStep step : plan.steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            Identifier blockId = Registries.BLOCK.getId(schematic.blockFor(step).getBlock());
            if ("villagepax".equals(blockId.getNamespace()) && blockId.getPath().startsWith("marker_")) {
                context.throwGameTestException("Маркер попал в шаги установки блоков: "
                        + step.pos().toShortString());
            }
        }

        Set<BlockPos> handled = new HashSet<>();
        for (BuildStep step : plan.steps()) {
            handled.add(step.pos());
        }
        for (PointOfInterest poi : plan.pois()) {
            if (!handled.contains(poi.pos())) {
                context.throwGameTestException("Место маркера " + poi.kind().id()
                        + " не обрабатывается планом: " + poi.pos().toShortString());
            }
        }

        // Складской маркер превращается в сундук, и это обычный шаг плана.
        // Отдельной фазой «доводки» сундук ставить нельзя: ремонт сносил бы
        // его вместе с содержимым, а правило «нужный блок уже стоит» защищает.
        for (BlockPos storage : plan.positionsOf(MarkerKind.STORAGE)) {
            BuildStep step = plan.steps().stream()
                    .filter(candidate -> candidate.pos().equals(storage))
                    .findFirst()
                    .orElseThrow();
            if (!step.placesBlock() || !schematic.blockFor(step).isOf(Blocks.CHEST)) {
                context.throwGameTestException("На складском маркере не сундук: "
                        + storage.toShortString());
            }
        }

        context.complete();
    }

    /**
     * Приёмка задачи 1.5: список блоков детерминирован и одинаков между
     * запусками. Порядок сверяется с планом, который загрузчик построил
     * при старте сервера — то есть в другом прогоне и на другом потоке.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void buildPlanIsDeterministic(TestContext context) {
        Schematic loaded = loadedTownHall(context);

        Optional<Resource> resource = context.getWorld().getServer()
                .getResourceManager().getResource(TOWN_HALL_SCHEMATIC_FILE);
        if (resource.isEmpty()) {
            context.throwGameTestException("Файл схемы не найден: " + TOWN_HALL_SCHEMATIC_FILE);
        }

        Schematic first;
        Schematic second;
        try (InputStream stream = resource.orElseThrow().getInputStream()) {
            NbtCompound nbt = NbtIo.readCompressed(stream);
            first = SchematicParser.parse(TOWN_HALL_SCHEMATIC, nbt, Registries.BLOCK.getReadOnlyWrapper());
            second = SchematicParser.parse(TOWN_HALL_SCHEMATIC, nbt, Registries.BLOCK.getReadOnlyWrapper());
        } catch (Exception failure) {
            context.throwGameTestException("Схема не перечиталась: " + failure);
            return;
        }

        if (!first.plan().equals(second.plan())) {
            context.throwGameTestException("Два разбора одного файла дали разные планы стройки");
        }
        if (!first.plan().equals(loaded.plan())) {
            context.throwGameTestException("План из файла не совпал с планом загрузчика");
        }

        // План считается один раз: приёмка требует кэширования.
        if (loaded.plan() != loaded.plan()) {
            context.throwGameTestException("План пересчитывается на каждое обращение");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void buildPlanOrdersClearingThenStructureThenDecor(TestContext context) {
        BuildPlan plan = loadedTownHall(context).plan();

        // Если в схеме нет всех трёх категорий, порядок между ними не
        // подтверждён ничем и тест был бы пустым.
        EnumSet<BuildCategory> present = EnumSet.noneOf(BuildCategory.class);
        for (BuildStep step : plan.steps()) {
            present.add(step.category());
        }
        if (present.size() != BuildCategory.values().length) {
            context.throwGameTestException("В плане есть только " + present
                    + " — порядок категорий проверять нечем");
        }

        BuildCategory category = null;
        int height = 0;
        for (BuildStep step : plan.steps()) {
            if (step.category() != category) {
                if (category != null && step.category().ordinal() < category.ordinal()) {
                    context.throwGameTestException("Категории перемешаны: " + category.id()
                            + " встретилась перед " + step.category().id());
                }
                category = step.category();
                height = step.pos().getY();
                continue;
            }
            int current = step.pos().getY();
            if (category.topDown() ? current > height : current < height) {
                context.throwGameTestException("Высота идёт не туда в " + category.id()
                        + ": " + height + " -> " + current);
            }
            height = current;
        }

        context.complete();
    }

    /**
     * Тег вместо предиката по блокстейту — решение задачи 1.5, и проверять
     * его надо на том самом случае, на котором предикат бы и сломался:
     * ступени крыши не полный куб, поэтому {@code isSolid} у них ложь,
     * и крыша уехала бы вноситься после мебели.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void decorTagSeparatesFurnitureFromStructure(TestContext context) {
        if (!Blocks.LECTERN.getDefaultState().isIn(ModTags.BUILD_DECOR)) {
            context.throwGameTestException(
                    "Тег villagepax:build_decor не загружен или не содержит пюпитр");
        }
        if (Blocks.DARK_OAK_PLANKS.getDefaultState().isIn(ModTags.BUILD_DECOR)) {
            context.throwGameTestException("Планки попали в декор — стены вносились бы последними");
        }

        if (BuildPlanner.categoryOf(Blocks.LECTERN.getDefaultState()) != BuildCategory.DECOR) {
            context.throwGameTestException("Пюпитр не признан обстановкой");
        }
        if (BuildPlanner.categoryOf(Blocks.COBBLESTONE.getDefaultState()) != BuildCategory.STRUCTURE) {
            context.throwGameTestException("Булыжник не признан несущим");
        }
        if (BuildPlanner.categoryOf(Blocks.DARK_OAK_STAIRS.getDefaultState()) != BuildCategory.STRUCTURE) {
            context.throwGameTestException("Ступени крыши уехали в декор");
        }

        context.complete();
    }

    // --- задача 1.6: билдер строит ---

    /**
     * Приёмка задачи 1.6: дать билдеру схему и полный склад — здание построено
     * полностью и совпадает со схемой поблочно.
     * <p>
     * Состояния сравниваются по блоку, а не целиком: {@code postProcessState}
     * досчитывает у ступеней форму, а у стёкол соединения по окружению — как
     * и при установке блока игроком. Поворот проверяется отдельно, по тем
     * свойствам, которые он и меняет.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void builderRaisesWholeBuilding(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

            BuildJob.Outcome outcome = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (outcome != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Стройка не завершилась: " + outcome
                        + ", шаг " + site.nextStep() + " из " + schematic.plan().steps().size());
            }
            if (site.progress() != BuildProgress.DONE) {
                context.throwGameTestException("Состояние здания " + site.progress().id() + ", ожидалось done");
            }

            // Слоты декора из проверки исключены намеренно: они и есть
            // расчищенные места, в которые обстановку ставит Decor — у одного
            // дома поленница, у другого бельё. Пустыми они быть не обязаны.
            Set<BlockPos> decorSlots = new HashSet<>(
                    BuildJob.pointsOfInterest(site, schematic, MarkerKind.DECOR));

            for (BuildStep step : schematic.plan().steps()) {
                BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
                BlockState actual = world.getBlockState(where);

                if (!step.placesBlock()) {
                    if (!actual.isAir() && !decorSlots.contains(where)) {
                        context.throwGameTestException("Расчищенное место занято " + actual.getBlock()
                                + " в " + step.pos().toShortString());
                    }
                    continue;
                }

                BlockState expected = schematic.blockAt(step.paletteIndex());
                if (!actual.isOf(expected.getBlock())) {
                    context.throwGameTestException("В " + step.pos().toShortString() + " ожидался "
                            + expected.getBlock() + ", стоит " + actual.getBlock());
                }
            }

            // Материалы обязаны быть израсходованы: иначе стройка бесплатна.
            if (!Warehouse.of(world, colony).isEmpty()) {
                context.throwGameTestException("Склад не опустел, осталось штук: "
                        + Warehouse.of(world, colony).totalItems());
            }

            // Точки интереса пересчитаны в мировые координаты и лежат внутри следа.
            List<BlockPos> doors = BuildJob.pointsOfInterest(site, schematic, MarkerKind.DOOR);
            if (doors.isEmpty()) {
                context.throwGameTestException("У готового здания нет точки входа");
            }
            for (BlockPos door : doors) {
                if (!BuildSite.covers(anchor, schematic.size(), BlockRotation.NONE, door)) {
                    context.throwGameTestException("Точка входа вне следа здания: " + door.toShortString());
                }
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /** Второе требование приёмки: материалов нет — билдер ждёт, а не ломается. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void builderWaitsWithoutMaterials(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            BuildJob.Outcome outcome = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (outcome != BuildJob.Outcome.WAITING_FOR_MATERIALS) {
                context.throwGameTestException("Без материалов ожидалось ожидание, получено: " + outcome);
            }
            if (site.progress() != BuildProgress.BUILDING) {
                context.throwGameTestException("Здание должно остаться в стройке, а не в "
                        + site.progress().id());
            }

            int stuckAt = site.nextStep();
            if (stuckAt >= schematic.plan().steps().size()) {
                context.throwGameTestException("Здание достроилось без материалов");
            }
            if (!schematic.plan().steps().get(stuckAt).placesBlock()) {
                context.throwGameTestException("Билдер встал на шаге расчистки, а тот материалов не требует");
            }

            // Повторное обращение не должно ни двигать индекс, ни падать.
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 100)
                    != BuildJob.Outcome.WAITING_FOR_MATERIALS) {
                context.throwGameTestException("Второе обращение без материалов дало другой исход");
            }
            if (site.nextStep() != stuckAt) {
                context.throwGameTestException("Индекс шага сдвинулся без материалов: "
                        + stuckAt + " → " + site.nextStep());
            }

            // Подвезли материалы — стройка продолжилась с того же места.
            stockFor(world, colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("После подвоза материалов стройка не завершилась");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Прогресс живёт в данных поселения, а не в билдере: стройка обязана
     * продолжаться с того же места после перезахода в мир.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void buildProgressSurvivesReload(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);
            BuildJob.advance(world, manager, colony.id(), site.id(), 40);

            int reached = site.nextStep();
            if (reached == 0 || reached >= schematic.plan().steps().size()) {
                context.throwGameTestException("Нужна недостроенная стройка, а шаг " + reached);
            }

            NbtElement saved = Settlement.CODEC.encodeStart(NbtOps.INSTANCE, colony).result().orElseThrow();
            Settlement restored = Settlement.CODEC.parse(NbtOps.INSTANCE, saved).result().orElseThrow();
            Building restoredSite = restored.building(site.id()).orElseThrow();

            if (restoredSite.nextStep() != reached) {
                context.throwGameTestException("Шаг стройки не пережил сохранение: "
                        + reached + " → " + restoredSite.nextStep());
            }
            if (restoredSite.progress() != BuildProgress.BUILDING) {
                context.throwGameTestException("Состояние стройки не пережило сохранение");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Поворот применяется и к позициям, и к блокстейтам. Повернуть только
     * позиции — значит получить дом с лестницами, ведущими в стену.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rotatedBuildingTurnsItsBlocksToo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.CLOCKWISE_90);

        try {
            stockFor(world, colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Повёрнутое здание не достроилось");
            }

            int checkedStairs = 0;
            for (BuildStep step : schematic.plan().steps()) {
                if (!step.placesBlock()) {
                    continue;
                }
                BlockState planned = schematic.blockAt(step.paletteIndex());
                if (!planned.contains(Properties.HORIZONTAL_FACING)) {
                    continue;
                }

                BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
                Direction expected = BlockRotation.CLOCKWISE_90
                        .rotate(planned.get(Properties.HORIZONTAL_FACING));
                BlockState actual = world.getBlockState(where);

                if (!actual.contains(Properties.HORIZONTAL_FACING)) {
                    context.throwGameTestException("В " + where.toShortString() + " стоит "
                            + actual.getBlock() + " без направления");
                    return;
                }
                if (actual.get(Properties.HORIZONTAL_FACING) != expected) {
                    context.throwGameTestException("Поворот блока не применён в "
                            + step.pos().toShortString() + ": ожидалось " + expected
                            + ", стоит " + actual.get(Properties.HORIZONTAL_FACING));
                }
                checkedStairs++;
            }

            if (checkedStairs == 0) {
                context.throwGameTestException("В схеме нет блоков с направлением — поворот нечем проверить");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Расчистка сдаёт добычу на склад — решение заказчика. Поэтому выбор
     * места становится экономическим: стройка в лесу дороже по времени,
     * но выгоднее по материалам.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void clearingSalvageGoesToWarehouse(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        // Кладём брёвна ровно туда, где схема требует пустоты.
        int obstacles = 0;
        List<BlockPos> blocked = new java.util.ArrayList<>();
        for (BuildStep step : schematic.plan().steps()) {
            if (step.placesBlock() || obstacles >= 4) {
                continue;
            }
            BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
            world.setBlockState(where, Blocks.OAK_LOG.getDefaultState());
            blocked.add(where);
            obstacles++;
        }

        try {
            if (obstacles == 0) {
                context.throwGameTestException("В схеме нет шагов расчистки — проверять нечего");
            }

            BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);

            if (Warehouse.of(world, colony).count(Items.OAK_LOG) < obstacles) {
                context.throwGameTestException("Добыча с расчистки не попала на склад: брёвен "
                        + Warehouse.of(world, colony).count(Items.OAK_LOG) + " из " + obstacles);
            }
            for (BlockPos where : blocked) {
                if (!world.getBlockState(where).isAir()) {
                    context.throwGameTestException("Помеха не убрана: " + where.toShortString());
                }
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /** Без строителя стройка не идёт: это работа профессии, а не самого поселения. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void constructionNeedsABuilder(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Безлюдье", hall);
        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        manager.add(colony);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 100)
                    != BuildJob.Outcome.NO_BUILDER) {
                context.throwGameTestException("Без строителя стройка не должна идти");
            }
            if (site.nextStep() != 0) {
                context.throwGameTestException("Без строителя индекс шага сдвинулся");
            }

            // Нанимаем строителя — и та же стройка идёт.
            Citizen builder = evenNewborn("Rollo", "le Macon", NORMAN, Gender.MALE);
            builder.setProfession(BuildJob.BUILDER);
            colony.addCitizen(builder);

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10)
                    == BuildJob.Outcome.NO_BUILDER) {
                context.throwGameTestException("Строитель нанят, а стройка всё равно не идёт");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void schematicNamingRoundTrips(TestContext context) {
        Building site = new Building(UUID.randomUUID(), TOWN_HALL_TYPE, 1,
                BlockPos.ORIGIN, BlockRotation.NONE, BuildProgress.PLANNED, List.of());

        Identifier schematicId = BuildJob.schematicId(site);
        if (!schematicId.equals(TOWN_HALL_SCHEMATIC)) {
            context.throwGameTestException("Имя схемы собрано неверно: " + schematicId);
        }
        if (!BuildJob.buildingTypeOf(schematicId).equals(Optional.of(TOWN_HALL_TYPE))) {
            context.throwGameTestException("Тип здания из имени схемы не восстановился");
        }
        if (!BuildJob.levelOf(schematicId).equals(Optional.of(1))) {
            context.throwGameTestException("Уровень из имени схемы не восстановился");
        }
        if (BuildJob.buildingTypeOf(new Identifier("villagepax", "norman/house")).isPresent()) {
            context.throwGameTestException("Имя без _lvl не должно разбираться");
        }

        // Заявка на материалы обязана быть непустой и не содержать воздуха.
        Map<Item, Integer> required = Materials.required(loadedTownHall(context));
        if (required.isEmpty()) {
            context.throwGameTestException("Заявка на материалы пуста");
        }
        if (required.containsKey(Items.AIR)) {
            context.throwGameTestException("В заявку попал воздух");
        }

        context.complete();
    }

    /**
     * Повреждённое здание чинится по той же схеме — и платит только за то,
     * что действительно пропало.
     * <p>
     * Здесь сходятся два решения. Первое: у повреждённого здания {@code nextStep}
     * стоит в конце с прошлой стройки, поэтому ремонт обязан начать план заново,
     * иначе он мгновенно «завершился» бы, не поставив ни блока. Второе: уже
     * стоящий нужный блок не переставляется и не оплачивается, иначе починка
     * трёх блоков списывала бы со склада схему целиком.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void damagedBuildingIsRepairedAndPaysOnlyForWhatIsMissing(TestContext context) {
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
                context.throwGameTestException("Здание не построилось до начала проверки ремонта");
            }

            // Выбиваем три несущих блока и записываем, чем они были.
            List<BlockPos> holes = new java.util.ArrayList<>();
            Map<Item, Integer> missing = new java.util.LinkedHashMap<>();
            for (BuildStep step : schematic.plan().steps()) {
                if (holes.size() >= 3 || !step.placesBlock()) {
                    continue;
                }
                BlockState planned = schematic.blockAt(step.paletteIndex());
                Optional<Item> item = Materials.itemFor(planned);
                if (item.isEmpty()) {
                    continue;
                }

                BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
                world.setBlockState(where, Blocks.AIR.getDefaultState());
                holes.add(where);
                missing.merge(item.get(), 1, Integer::sum);
            }
            if (holes.size() != 3) {
                context.throwGameTestException("Не удалось выбить три блока для проверки ремонта");
            }

            site.setProgress(BuildProgress.DAMAGED);
            Warehouse warehouse = Warehouse.of(world, colony);
            missing.forEach((item, count) -> warehouse.add(new ItemStack(item, count)));

            BuildJob.Outcome outcome = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (outcome != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ремонт не завершился: " + outcome
                        + ", шаг " + site.nextStep());
            }

            for (BlockPos hole : holes) {
                if (world.getBlockState(hole).isAir()) {
                    context.throwGameTestException("Пробоина не заделана: " + hole.toShortString());
                }
            }
            if (!Warehouse.of(world, colony).isEmpty()) {
                context.throwGameTestException("Ремонт списал лишнее: на складе осталось "
                        + Warehouse.of(world, colony).totalItems() + ", а завозили ровно на три блока");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Тот самый путь, который проходит живой игрок, и ничего кроме него.
     * <p>
     * Чертёж основывает колонию, вместе с ней появляется строитель, площадка
     * размечается, материалы завозятся — и дальше <b>никто ничего не вызывает
     * руками</b>. Блоки ставит {@code BuildTicker}, который висит на тике мира
     * с самой инициализации мода, ровно как в игре. Остальные тесты стройки
     * дёргают двигатель напрямую и потому не доказывают, что он вообще
     * подключён к игре; этот доказывает.
     */
    @GameTest(templateName = WIDE_STRUCTURE, tickLimit = 220, batchId = "playerPath")
    public void playerPathRaisesBuildingByItself(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        // Пол: билдер теперь ходит, и ему надо по чему-то идти. Без этого
        // площадка висела бы в воздухе и путь до неё не проложился бы вовсе.
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 11; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        FoundingOutcome outcome = ColonyFounder.foundAt(world, UUID.randomUUID(), NORMAN, hall);
        if (!(outcome instanceof FoundingOutcome.Founded founded)) {
            context.throwGameTestException("Колония не основана: " + outcome);
            return;
        }

        Settlement colony = founded.settlement();
        Citizen builder = colony.citizens().stream()
                .filter(citizen -> citizen.profession().equals(Optional.of(BuildJob.BUILDER)))
                .findFirst()
                .orElse(null);
        if (builder == null) {
            context.throwGameTestException("Основание не дало строителя — стройке некому идти");
            return;
        }

        // Первый надел колонии тут же и сносится. Эта проверка — про
        // приказ игрока и про то, что тикер работ подключён к тику мира;
        // подаренные дом и поле встают сами, без билдера, и вдобавок
        // занимают ту самую площадку, которую проверка размечает следом.
        razeHolding(world, colony);

        // Площадка рядом с ратушей, но не поверх неё: склад в двух шагах,
        // поэтому курьер не нужен и билдер берёт материалы сам.
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 4));
        Building site = plan(colony, anchor, BlockRotation.NONE);
        stockFor(world, colony, schematic);

        context.runAtTick(200, () -> {
            try {
                if (site.nextStep() == 0) {
                    context.throwGameTestException("За 200 тиков билдер не сделал ни шага. "
                            + "Похоже, тикер работ не подключён к тику мира");
                }

                int placed = 0;
                for (int step = 0; step < site.nextStep(); step++) {
                    BuildStep done = schematic.plan().steps().get(step);
                    if (!done.placesBlock()) {
                        continue;
                    }
                    BlockPos where = BuildJob.worldPos(site, schematic.size(), done.pos());
                    if (!world.getBlockState(where).isOf(schematic.blockAt(done.paletteIndex()).getBlock())) {
                        context.throwGameTestException("Шаг " + step + " засчитан, а блока в мире нет: "
                                + where.toShortString());
                    }
                    placed++;
                }
                if (placed == 0) {
                    context.throwGameTestException("Билдер дошёл, но ни одного блока не поставил");
                }

                // Он обязан быть у стройки, а не бродить: иначе работа шла бы
                // сама, а житель был бы декорацией.
                CitizenEntity body = (CitizenEntity) world.getEntity(builder.entityUuid().orElseThrow());
                double distance = Math.sqrt(body.getPos().squaredDistanceTo(Vec3d.ofCenter(anchor)));
                if (distance > 16.0) {
                    context.throwGameTestException("Билдер работает, стоя в " + Math.round(distance)
                            + " блоках от площадки");
                }

                context.complete();
            } finally {
                demolish(world, site, schematic);
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
        });
    }

    // --- где стоит билдер и кто занял стройку ---

    /**
     * Билдер стоит на земле рядом со стройкой, а не лезет на неё.
     * <p>
     * Стоя на недоделанной стене, он ломает себе путь: навигация ведёт вниз,
     * решение гонит наверх, и он топчется. Именно это игрок видит как
     * «путь сбивается».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "stand")
    public void builderStandsOnTheGroundBesideTheSite(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));
        Vec3i size = housePlan.size();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);
        List<BlockPos> platform = new ArrayList<>();

        try {
            // Площадка готовится начисто: пустота на всю высоту здания, потом
            // земля, и обе поверх того, что было. Мир игровых тестов один
            // на все проверки, области раздаются по-разному от прогона
            // к прогону, а блок, случайно совпавший с планом, стройку
            // не тормозит, а ускоряет — совпавший шаг пропускается, не тратя
            // бюджета, и те же шестьдесят шагов то недостраивают дом,
            // то достраивают целиком.
            for (int dx = -4; dx < size.getX() + 4; dx++) {
                for (int dz = -4; dz < size.getZ() + 4; dz++) {
                    for (int dy = 0; dy <= size.getY() + 2; dy++) {
                        BlockPos clear = anchor.add(dx, dy, dz);
                        if (!world.getBlockState(clear).isAir()) {
                            world.setBlockState(clear, Blocks.AIR.getDefaultState());
                            platform.add(clear);
                        }
                    }

                    BlockPos ground = anchor.add(dx, -1, dz);
                    world.setBlockState(ground, Blocks.DIRT.getDefaultState());
                    platform.add(ground);
                }
            }

            stockFor(world, colony, housePlan);
            // Часть стен уже стоит: раньше билдер лез именно на них.
            BuildJob.advance(world, manager, colony.id(), site.id(), 60);

            if (!BuildJob.isUnderConstruction(site)) {
                context.throwGameTestException("Посылка теста не выполнена: дом достроился "
                        + "за шестьдесят шагов, а проверять надо недостроенный");
            }

            // Спрашивается сам выбор места, а не то, что успел решить тикер.
            // Через жителя эта проверка соревновалась бы с самим модом:
            // мод тикает и принимает решения за того же билдера, и тест
            // мигал через раз не по своей вине.
            BlockPos stand = BuilderJob.standingSpot(world, site).orElse(null);

            if (stand == null) {
                context.throwGameTestException("Билдеру негде встать у стройки на шаге "
                        + site.nextStep());
            }
            if (!world.getBlockState(stand.down()).isSolidBlock(world, stand.down())) {
                context.throwGameTestException("Под ногами билдера не твёрдый блок: "
                        + world.getBlockState(stand.down()).getBlock()
                        + " в " + stand.toShortString());
            }
            if (stand.getY() > anchor.getY() + 2) {
                context.throwGameTestException("Билдер полез наверх: стоит на "
                        + (stand.getY() - anchor.getY()) + " блока выше земли стройки");
            }

            boolean insideFootprint = stand.getX() >= anchor.getX()
                    && stand.getX() < anchor.getX() + size.getX()
                    && stand.getZ() >= anchor.getZ()
                    && stand.getZ() < anchor.getZ() + size.getZ();
            if (insideFootprint) {
                context.throwGameTestException("Билдер встал внутрь следа здания "
                        + stand.toShortString() + ", хотя снаружи есть земля");
            }
        } finally {
            demolish(world, site, housePlan);
            for (BlockPos ground : platform) {
                world.setBlockState(ground, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Одна стройка — один билдер. Второй за неё не берётся.
     * <p>
     * Иначе оба идут к одному блоку и толкаются на нём: путь у каждого
     * сбивается о соседа, никто не доходит, стройка встаёт. Игрок сообщает
     * об этом как «работники повисли».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "stand")
    public void secondBuilderLeavesAClaimedSiteAlone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            Citizen first = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            Citizen second = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());

            runWork(world, manager, colony, first, 1, Schedule.MORNING_WORK);
            if (first.jobState().building().filter(site.id()::equals).isEmpty()) {
                context.throwGameTestException("Первый билдер не взялся за стройку");
            }

            runWork(world, manager, colony, second, 1, Schedule.MORNING_WORK);
            if (!second.jobState().isIdle()) {
                context.throwGameTestException("Второй билдер взялся за занятую стройку: "
                        + second.jobState().phase().id());
            }

            CitizenEntity idle = (CitizenEntity) world.getEntity(second.entityUuid().orElseThrow());
            if (idle.workTarget() != null) {
                context.throwGameTestException("Праздный билдер всё равно идёт на стройку");
            }

            // Первый отпустил работу — стройка снова свободна.
            first.setJobState(JobState.IDLE);
            runWork(world, manager, colony, second, 1, Schedule.MORNING_WORK);
            if (second.jobState().building().filter(site.id()::equals).isEmpty()) {
                context.throwGameTestException("Освободившуюся стройку никто не взял");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- колония делает то, чего у неё нет ---

    /**
     * Ферму можно построить, не имея моркови.
     * <p>
     * Игрок сообщил: «они требуют пашни, а собрать их нельзя». Пашня
     * и правда бесплатна — у неё нет предмета, — а вот у морковной грядки
     * предмет есть: морковь. Ферма требовала со склада сорок семь морковок,
     * которых у новой колонии взяться негде. Построить ферму, чтобы
     * получить морковь, можно было только имея морковь.
     * <p>
     * Записанное решение заказчика гласит, что первый посев приходит
     * вместе с постройкой, — и теперь оно наконец выполняется.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "craft")
    public void farmCostsNoSeedToBuild(TestContext context) {
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);
        Map<Item, Integer> bill = Materials.required(farmPlan);

        if (bill.containsKey(Items.CARROT)) {
            context.throwGameTestException("Ферма всё ещё требует морковь: "
                    + bill.get(Items.CARROT) + " штук. Построить ферму, чтобы получить "
                    + "морковь, можно было бы только имея морковь");
        }
        for (Item asked : bill.keySet()) {
            if (asked == Items.AIR) {
                context.throwGameTestException("В заявке оказался воздух");
            }
        }

        // Но грядки на поле всё-таки есть: посев приходит со схемой,
        // а не отменяется вовсе.
        long crops = farmPlan.plan().steps().stream()
                .filter(BuildStep::placesBlock)
                .filter(step -> farmPlan.blockAt(step.paletteIndex()).getBlock()
                        instanceof CropBlock)
                .count();
        if (crops == 0) {
            context.throwGameTestException("На ферме не осталось ни одной грядки");
        }

        context.complete();
    }

    /**
     * Колония сама делает фахверк, доски, ступени и стёкла.
     * <p>
     * Просьба игрока. Без крафта схема дома требовала фахверк, ступени,
     * доски, кровати и стёкла, а колония не умела ничего из этого: всё
     * это игрок обязан был скрафтить руками и принести в сундук, иначе
     * стройка стояла. Деревня из шести домов превращалась в двести
     * походов к верстаку.
     * <p>
     * На склад завозится только <b>сырьё</b>: брёвна, глина, песок,
     * булыжник, стекло и шерсть. Ни одной доски, ни одной ступени, ни
     * одного фахверка. Дом обязан встать целиком.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "craft")
    public void colonyCraftsWhatTheHouseNeeds(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            Warehouse warehouse = Warehouse.of(world, colony);
            raw(world, warehouse, hall, Items.DARK_OAK_LOG, 128);
            raw(world, warehouse, hall, Items.COBBLESTONE, 64);
            raw(world, warehouse, hall, Items.CLAY_BALL, 64);
            raw(world, warehouse, hall, Items.SAND, 64);
            raw(world, warehouse, hall, Items.GLASS, 16);
            // Шерсть красная, а не белая: ванильной красной кровати нужна
            // именно красная шерсть, а перекрасить белую колония не может —
            // на это нужен краситель, а он растёт в поле, не на складе.
            raw(world, warehouse, hall, Items.RED_WOOL, 32);
            // Уголь на очаг: костёр колония сложит сама из брёвен, палок
            // и угля, но уголь ей взять негде — он в шахте.
            raw(world, warehouse, hall, Items.COAL, 16);

            // Проверка посылки: готового в завозе нет.
            Warehouse stocked = Warehouse.of(world, colony);
            for (Item ready : List.of(Items.DARK_OAK_PLANKS, Items.DARK_OAK_STAIRS,
                    Items.GLASS_PANE, Items.RED_BED, ModBlocks.TIMBER_FRAME.asItem())) {
                if (stocked.count(ready) > 0) {
                    context.throwGameTestException("Посылка теста не выполнена: на складе "
                            + "уже лежит готовое — " + ready);
                }
            }

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не встал из сырья: шаг " + site.nextStep()
                        + " из " + housePlan.plan().steps().size() + ", не хватает "
                        + Materials.shortfall(housePlan, site, 200));
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- типы зданий из датапака ---

    /**
     * Права зданию даёт объявленный тип, а не его имя.
     * <p>
     * Самый старый долг мода, и вот чем он был опасен: ратушу узнавали
     * по тому, что путь типа кончается на {@code town_hall}. Здание
     * с именем {@code norman/town_hall_ruins} мод счёл бы ратушей
     * и позволил бы поднимать по нему уровень колонии — до второго уровня
     * ратуши, которой нет.
     * <p>
     * Теперь роль объявлена данными, а молчание датапака <b>не наделяет
     * здание правами</b>: неописанное здание строится и чинится, но
     * колонии уровня не даёт и мастерской никому не служит.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "orders")
    public void onlyDeclaredTypesGetRights(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        Identifier ruins = new Identifier("villagepax", "norman/town_hall_ruins");
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            // Объявленное узнаётся.
            if (!Levels.isTownHallType(TOWN_HALL_TYPE)) {
                context.throwGameTestException("Настоящая ратуша не узнана по данным");
            }
            if (!BuildingTypes.isHome(HOUSE_TYPE)) {
                context.throwGameTestException("Дом не объявлен жильём");
            }
            if (!BuildingTypes.employs(FARM_TYPE, FarmJob.FARMER)) {
                context.throwGameTestException("Ферма не объявлена мастерской фермера");
            }

            // А похожее имя — нет.
            if (Levels.isTownHallType(ruins)) {
                context.throwGameTestException("Здание " + ruins + " сочли ратушей по имени — "
                        + "это и был тот самый долг");
            }
            if (BuildingTypes.employs(ruins, FarmJob.FARMER)) {
                context.throwGameTestException("Необъявленное здание служит мастерской");
            }

            // И уровень колонии от него не растёт.
            colony.addBuilding(new Building(UUID.randomUUID(), ruins, 2, hall.add(20, 0, 20),
                    BlockRotation.NONE, BuildProgress.DONE, List.of()));
            Levels.refresh(colony);
            if (colony.level() != SettlementLevel.HAMLET) {
                context.throwGameTestException("Колония выросла от здания, которое ратушей "
                        + "не объявлено: " + colony.level().id());
            }

            // Имя у неописанного здания всё-таки есть: пустой строки
            // в интерфейсе быть не должно.
            if (!BuildingTypes.displayName(ruins)
                    .equals("villagepax.building.norman.town_hall_ruins")) {
                context.throwGameTestException("У неописанного здания нет запасного имени: "
                        + BuildingTypes.displayName(ruins));
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- очередь заказов ---

    /**
     * Билдер берётся за то, что игрок поставил вперёд.
     * <p>
     * Заказал три дома — решаешь, какой первым. Без очереди билдер брался
     * за первую размеченную стройку, и переставить порядок было нечем:
     * приходилось отменять заказы и размечать заново.
     * <p>
     * Проверяется на двух стройках, из которых <b>вторая</b> объявлена
     * важной: если бы очередь не работала, билдер взялся бы за первую.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "orders")
    public void builderTakesTheUrgentSiteFirst(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        Building first = plan(colony, context.getAbsolutePos(new BlockPos(0, 8, 0)),
                HOUSE_TYPE, BlockRotation.NONE);
        Building urgent = plan(colony, context.getAbsolutePos(new BlockPos(0, 8, 8)),
                HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);
            stockFor(world, colony, housePlan);

            // Без приоритета очередь идёт по времени заказа.
            if (!colony.byPriority().get(0).id().equals(first.id())) {
                context.throwGameTestException("При равной важности порядок заказа не сохранён");
            }

            urgent.setPriority(5);
            if (!colony.byPriority().get(0).id().equals(urgent.id())) {
                context.throwGameTestException("Важная стройка не стала первой в очереди");
            }

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 4, Schedule.MORNING_WORK);

            UUID taken = mason.jobState().building().orElse(null);
            if (taken == null) {
                context.throwGameTestException("Билдер не взялся ни за что");
            }
            if (!taken.equals(urgent.id())) {
                context.throwGameTestException("Билдер взялся не за важную стройку: "
                        + (taken.equals(first.id()) ? "за первую по времени" : taken.toString()));
            }
        } finally {
            demolish(world, first, housePlan);
            demolish(world, urgent, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- список заказов ---

    /**
     * Каждое здание предлагается один раз, и ратуша не предлагается вовсе.
     * <p>
     * Игрок сообщил: «в ратуше двоятся здания». Причина была в списке
     * заказов: он собирался из <b>всех</b> схем культуры, а подпись
     * у уровней одна на тип — и «Дом норманнов» стоял в списке дважды,
     * за первый уровень и за второй. Заказать второй уровень к тому же
     * значило бы поставить дом, у которого не было первого: уровни растут
     * кнопкой «Улучшить».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "orders")
    public void orderListShowsEachBuildingOnce(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            List<Identifier> offers = TownHallView.of(world, colony).offers();

            if (offers.isEmpty()) {
                context.throwGameTestException("Заказать нечего вовсе");
            }

            Set<Identifier> types = new HashSet<>();
            for (Identifier schematic : offers) {
                if (BuildJob.levelOf(schematic).orElse(1) != 1) {
                    context.throwGameTestException("В заказах не первый уровень: " + schematic
                            + ". Уровни растут кнопкой «Улучшить», а не заказом с нуля");
                }

                Identifier type = BuildJob.buildingTypeOf(schematic).orElseThrow();
                if (Levels.isTownHallType(type)) {
                    context.throwGameTestException("Ратушу предлагают построить второй раз: "
                            + "она уже стоит с основания");
                }
                if (!types.add(type)) {
                    context.throwGameTestException("Тип " + type + " предложен дважды — "
                            + "именно это игрок и видел как двоящиеся здания");
                }
            }

            // И то, что предлагается, обязано быть построимо: схема есть.
            for (Identifier schematic : offers) {
                if (SchematicLoader.get(schematic).isEmpty()) {
                    context.throwGameTestException("Предложена схема, которой нет: " + schematic);
                }
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- слоты декора ---

    /**
     * Слот декора заполняется, и всегда одним и тем же.
     * <p>
     * Маркер декора задумывался с самого начала, но дел не делал: становился
     * воздухом, и все дома колонии выходили близнецами. Теперь на его место
     * встаёт блок из списка культуры.
     * <p>
     * Устойчивость проверяется ремонтом, и это главное в тесте. Случайность
     * из генератора пережила бы постройку, но не ремонт: дом менял бы облик
     * всякий раз, когда билдер подлатает стену, и игрок видел бы мод, который
     * сам себя переделывает.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "decor")
    public void decorSlotsAreFilledAndStayTheSame(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, HOUSE_LVL2);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 2, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);

        try {
            stockFor(world, colony, house);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не достроился");
            }

            // Четыре, а не два: дом перестроен на след 7x7, и мест
            // под убранство в нём стало больше — три внизу и одно
            // в горнице. Число написано от руки
            // нарочно — спроси проверка его у схемы, она согласилась бы
            // и с нулём слотов, то есть с домом без убранства вовсе.
            List<BlockPos> slots = BuildJob.pointsOfInterest(site, house, MarkerKind.DECOR);
            if (slots.size() != 4) {
                context.throwGameTestException("Слотов декора " + slots.size()
                        + ", а в схеме дома второго уровня четыре");
            }

            List<Block> chosen = new ArrayList<>();
            for (BlockPos slot : slots) {
                BlockState state = world.getBlockState(slot);
                if (state.isAir()) {
                    context.throwGameTestException("Слот декора на "
                            + slot.toShortString() + " остался пустым");
                }
                if (!decorTable().contains(state.getBlock())) {
                    context.throwGameTestException("В слот встало то, чего нет в списке "
                            + "культуры: " + state.getBlock());
                }
                chosen.add(state.getBlock());
            }

            // Ремонт: план проходится заново, и декор обязан вернуться тот же.
            site.setProgress(BuildProgress.DAMAGED);
            for (BlockPos slot : slots) {
                world.setBlockState(slot, Blocks.AIR.getDefaultState());
            }
            stockFor(world, colony, house);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ремонт не завершился");
            }

            for (int index = 0; index < slots.size(); index++) {
                Block back = world.getBlockState(slots.get(index)).getBlock();
                if (back != chosen.get(index)) {
                    context.throwGameTestException("После ремонта декор сменился: было "
                            + chosen.get(index) + ", стало " + back);
                }
            }
        } finally {
            demolish(world, site, house);
            for (BlockPos slot : BuildJob.pointsOfInterest(site, house, MarkerKind.DECOR)) {
                world.setBlockState(slot, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- билдер строит стоя на земле ---

    /**
     * Высокое здание билдер достраивает, <b>ни разу не встав в воздух</b>.
     * <p>
     * Жалоба игрока: «строитель продолжает тупить при постройке, пытается
     * дотянуться а не может». Прежние тесты стройки этого поймать не могли
     * и не могут: {@code runWork} телепортирует тело прямо в назначенную
     * точку, а телепортом достаётся и блок на крыше. В игре у жителя
     * телепорта нет.
     * <p>
     * Поэтому здесь {@link #runWorkOnFoot} переносит тело только туда, где
     * <b>может стоять человек</b>: твёрдое под ногами, пусто на месте и над
     * головой. Назначили точку в воздухе — житель остаётся там, где был,
     * ровно как в игре, где он до неё не дойдёт.
     * <p>
     * Проверяется на доме второго уровня: он девять блоков высотой, и труба
     * у него идёт до самого верха. Самой высокой схемой мода он был до
     * майя; теперь выше него храм совета второго уровня, и у него своя
     * проверка — {@link #mayaTempleIsBuiltWithoutStandingInMidair}.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "reach")
    public void tallHouseIsBuiltWithoutStandingInMidair(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic tall = schematic(context, HOUSE_LVL2);

        // Ратуша рядом со стройкой намеренно: дальше двенадцати блоков
        // билдер не берёт со склада сам, и стройка встала бы по нехватке
        // материалов, а проверяем мы досягаемость.
        BlockPos hall = context.getAbsolutePos(new BlockPos(8, 9, 8));
        List<BlockPos> ground = new ArrayList<>();

        try {
            // Ровная площадка под здание и вокруг него: билдеру надо где стоять.
            for (int x = -2; x <= 12; x++) {
                for (int z = -2; z <= 12; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall);
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
            Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 2, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(site);

            try {
                stockFor(world, colony, tall);
                Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER,
                        context.getAbsolutePos(new BlockPos(-1, 9, -1)));

                int stalled = runWorkOnFoot(world, manager, colony, mason, 900);

                if (!site.isOperational()) {
                    context.throwGameTestException("Дом второго уровня не достроился: билдер "
                            + "встал на шаге " + site.nextStep() + " из "
                            + tall.plan().steps().size() + ", и " + stalled
                            + " раз его посылали в точку, где человек стоять не может");
                }
                if (stalled > 0) {
                    context.throwGameTestException("Билдера " + stalled
                            + " раз посылали стоять в воздух — в игре он туда не дойдёт");
                }
            } finally {
                demolish(world, site, tall);
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

    // --- вторые уровни дома и фермы ---

    /**
     * Дом второго уровня: четыре кровати и открытый дымоход.
     * <p>
     * Решение заказчика — «больше и красивее». Кровати это «больше»,
     * а очаг с трубой — то самое «красивее»: дым виден с улицы, и деревня
     * перестаёт выглядеть макетом.
     * <p>
     * Дымоход проверяется <b>по всей высоте</b>, и не зря: колонна проходит
     * через потолок и три слоя крыши, пробивается кодом, и одна пропущенная
     * дырка означает дом, который дымит внутрь. Увидеть такое можно было бы
     * только в игре, стоя рядом.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "levels")
    public void upgradedHouseSleepsFourAndVentsItsHearth(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic bigger = schematic(context, HOUSE_LVL2);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = new Building(UUID.randomUUID(), HOUSE_TYPE, 2, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(house);

        try {
            stockFor(world, colony, bigger);
            if (BuildJob.advance(world, manager, colony.id(), house.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом второго уровня не достроился");
            }

            // Очаг ищется в схеме, а не помнится координатой: он уже
            // переезжал — из середины комнаты в угол, после того как
            // на нём сгорел житель.
            BlockPos hearth = hearthOf(bigger, house);
            BlockState fire = hearth == null
                    ? Blocks.AIR.getDefaultState() : world.getBlockState(hearth);
            if (!fire.isOf(Blocks.CAMPFIRE) || !fire.get(CampfireBlock.LIT)) {
                context.throwGameTestException("Очага в доме нет или он потушен: "
                        + fire.getBlock());
            }

            // Дымоход идёт над очагом, где бы тот ни стоял.
            BlockPos above = hearth == null ? null
                    : BuildJob.worldPos(house, bigger.size(), new BlockPos(0, 0, 0));
            for (int y = 3; hearth != null && y < bigger.size().getY(); y++) {
                BlockPos flue = new BlockPos(hearth.getX(),
                        above.getY() + y, hearth.getZ());
                if (!world.getBlockState(flue).isAir()) {
                    context.throwGameTestException("Дымоход закрыт на высоте " + y + ": там "
                            + world.getBlockState(flue).getBlock());
                }
            }

            // Четверо под одной крышей — вдвое против первого уровня.
            for (int extra = 0; extra < 3; extra++) {
                Citizen lodger = evenNewborn("Жилец", String.valueOf(extra), NORMAN,
                        Gender.FEMALE);
                colony.addCitizen(lodger);
            }
            Housing.assignBeds(world, colony);

            long housed = colony.citizens().stream().filter(citizen -> !citizen.isHomeless())
                    .count();
            if (housed != 4) {
                context.throwGameTestException("Под крышей устроилось " + housed
                        + " жителей, а кроватей четыре");
            }

            // Пятому места нет: кроватей ровно столько, сколько построено.
            colony.addCitizen(evenNewborn("Лишний", "", NORMAN, Gender.MALE));
            Housing.assignBeds(world, colony);
            if (colony.citizens().stream().filter(citizen -> !citizen.isHomeless()).count() != 4) {
                context.throwGameTestException("Кроватей оказалось больше, чем построено");
            }
        } finally {
            demolish(world, house, bigger);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Ферма второго уровня: поле вдвое больше и по-прежнему с калиткой.
     * <p>
     * Калитка проверяется настоящим поиском пути, а не наличием блока:
     * поле без входа — это фермер, который стоит снаружи и не делает
     * ничего, и ровно так это однажды и было.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "levels")
    public void biggerFarmIsPlantedAndStillEnterable(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic bigger = schematic(context, FARM_LVL2);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = new Building(UUID.randomUUID(), FARM_TYPE, 2, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(farm);

        BlockPos landing = BuildJob.worldPos(farm, bigger.size(), new BlockPos(-1, 1, 3));

        try {
            stockFor(world, colony, bigger);
            if (BuildJob.advance(world, manager, colony.id(), farm.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ферма второго уровня не достроилась");
            }

            int planted = 0;
            for (int x = 1; x <= 7; x++) {
                for (int z = 1; z <= 7; z++) {
                    BlockPos plot = BuildJob.worldPos(farm, bigger.size(), new BlockPos(x, 2, z));
                    if (world.getBlockState(plot).getBlock() instanceof CropBlock) {
                        planted++;
                    }
                }
            }
            if (planted != BIGGER_FIELD) {
                context.throwGameTestException("Засеяно грядок " + planted + ", а ждали "
                        + BIGGER_FIELD);
            }

            // Пугало: тюк и тыква на нём. Стоит там же, где стояло
            // на первом уровне, и это половина смысла проверки:
            // улучшение надстраивает поле, а не переставляет на нём вещи.
            BlockPos straw = BuildJob.worldPos(farm, bigger.size(), new BlockPos(1, 2, 5));
            BlockPos head = BuildJob.worldPos(farm, bigger.size(), new BlockPos(1, 3, 5));
            if (!world.getBlockState(straw).isOf(Blocks.HAY_BLOCK)
                    || !world.getBlockState(head).isOf(Blocks.CARVED_PUMPKIN)) {
                context.throwGameTestException("Пугала на поле нет: "
                        + world.getBlockState(straw).getBlock() + " и "
                        + world.getBlockState(head).getBlock());
            }

            BlockPos gate = BuildJob.worldPos(farm, bigger.size(), new BlockPos(0, 2, 3));
            if (!(world.getBlockState(gate).getBlock() instanceof FenceGateBlock)) {
                context.throwGameTestException("В ограде большого поля нет калитки, стоит "
                        + world.getBlockState(gate).getBlock());
            }

            world.setBlockState(landing, Blocks.DIRT.getDefaultState());
            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, landing.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(farmer.entityUuid().orElseThrow());
            body.setOnGround(true);

            BlockPos plot = BuildJob.worldPos(farm, bigger.size(), new BlockPos(1, 2, 3));
            Path through = body.getNavigation().findPathTo(plot, 0);
            if (through == null || !through.reachesTarget()) {
                context.throwGameTestException("Фермер не может войти на большое поле: "
                        + (through == null ? "пути нет вовсе" : "путь не доходит"));
            }
        } finally {
            demolish(world, farm, bigger);
            world.setBlockState(landing, Blocks.AIR.getDefaultState());
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

}
