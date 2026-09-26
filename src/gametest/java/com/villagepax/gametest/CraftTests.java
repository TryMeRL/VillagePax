package com.villagepax.gametest;

import com.villagepax.sim.BuildProgress;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Gender;
import net.minecraft.util.math.Vec3d;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.core.ModTags;
import com.villagepax.sim.work.Needs;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.work.HaulJob;
import com.villagepax.sim.work.JobState;
import com.villagepax.core.profession.Profession;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.GatherJob;
import com.villagepax.sim.work.Jobs;
import com.villagepax.sim.work.Workplaces;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkTicker;
import net.minecraft.block.BlockState;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ремёсла: профессии данными, лесоруб, фермер, кто чем занят и что у кого в руках.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class CraftTests extends GameTestSupport {

    // --- задача 1.9а: профессии как данные и лесоруб ---

    /** Профессии приходят из датапака, а логика работы — из кода. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "lumberjack")
    public void professionsComeFromTheDatapack(TestContext context) {
        Profession lumberjack = ProfessionManager.get(GatherJob.LUMBERJACK).orElse(null);
        if (lumberjack == null) {
            context.throwGameTestException("Профессия лесоруба не загружена. Загружены: "
                    + ProfessionManager.ids());
            return;
        }
        if (!lumberjack.job().equals(GatherJob.LOGIC)) {
            context.throwGameTestException("Лесоруб выбрал не ту логику: " + lumberjack.job());
        }
        if (!lumberjack.needsWorkplace()) {
            context.throwGameTestException("Лесорубу нужна мастерская: там его роща");
        }

        // Логика находится по профессии — через данные, а не по таблице в коде.
        if (Jobs.forProfession(Optional.of(GatherJob.LUMBERJACK)).isEmpty()) {
            context.throwGameTestException("По профессии лесоруба не нашлось логики");
        }
        // А билдер и курьер мастерской не требуют: их зданий ещё нет,
        // и жёсткое требование остановило бы уже идущую работу.
        if (ProfessionManager.get(BuildJob.BUILDER).orElseThrow().needsWorkplace()
                || ProfessionManager.get(HaulJob.COURIER).orElseThrow().needsWorkplace()) {
            context.throwGameTestException("Билдеру или курьеру навязали мастерскую");
        }

        // Опечатка в датапаке не должна ронять сервер: житель просто без дела.
        if (Jobs.forProfession(Optional.of(new Identifier("villagepax", "no_such_profession"))).isPresent()) {
            context.throwGameTestException("Неизвестная профессия получила логику");
        }

        // Порядок найма задан данными и устойчив.
        List<Identifier> order = ProfessionManager.byHiringPriority();
        if (!order.get(0).equals(BuildJob.BUILDER)) {
            context.throwGameTestException("Первым нанимают не строителя, а " + order.get(0));
        }
        if (!order.equals(ProfessionManager.byHiringPriority())) {
            context.throwGameTestException("Порядок найма меняется от вызова к вызову");
        }

        context.complete();
    }

    /**
     * Домик лесоруба приносит огороженную рощу и рабочее место.
     * <p>
     * Роща — не новое состояние, а часть схемы: грядки для деревьев есть
     * позиции, где в схеме стоят саженцы.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "lumberjack")
    public void lumberjackHutGivesAGroveAndAWorkplace(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic hutPlan = schematic(context, LUMBERJACK_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building hut = plan(colony, anchor, LUMBERJACK_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, hutPlan);
            if (BuildJob.advance(world, manager, colony.id(), hut.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Домик лесоруба не достроился");
            }

            List<BlockPos> grove = GatherJob.groveTiles(hut);
            if (grove.size() != 4) {
                context.throwGameTestException("Грядок в роще " + grove.size() + ", в схеме четыре");
            }
            for (BlockPos tile : grove) {
                if (!world.getBlockState(tile).isIn(BlockTags.SAPLINGS)) {
                    context.throwGameTestException("На грядке не саженец: "
                            + world.getBlockState(tile).getBlock());
                }
                if (!world.getBlockState(tile.down()).isIn(BlockTags.DIRT)) {
                    context.throwGameTestException("Под саженцем не земля");
                }
            }

            if (Workplaces.stations(hut).isEmpty()) {
                context.throwGameTestException("У домика нет рабочего места");
            }

            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, hall.up());
            Workplaces.assign(world, colony);

            if (Workplaces.of(colony, woodsman).isEmpty()) {
                context.throwGameTestException("Лесорубу не досталась мастерская");
            }
            if (!Workplaces.of(colony, woodsman).orElseThrow().id().equals(hut.id())) {
                context.throwGameTestException("Лесоруб приписан к чужому зданию");
            }
        } finally {
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Приёмка задачи 1.9а: лесоруб валит дерево, сдаёт брёвна на склад
     * и сажает саженец обратно.
     * <p>
     * Посадка — решение заказчика: в роще лес не кончается, а окрестности
     * не превращаются в пустырь. Дикий лес он, наоборот, просто счищает.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "lumberjack")
    public void lumberjackFellsGroveTreeAndReplantsIt(TestContext context) {
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

            // Одна грядка «выросла»: ствол и немного кроны.
            BlockPos tile = GatherJob.groveTiles(hut).get(0);
            for (int dy = 0; dy < 4; dy++) {
                world.setBlockState(tile.up(dy), Blocks.OAK_LOG.getDefaultState());
            }
            world.setBlockState(tile.up(4),
                    Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));

            // Саженцы на складе: колония живёт своим кругооборотом.
            Warehouse.of(world, colony).add(new ItemStack(Items.OAK_SAPLING, 4));
            int logsBefore = Warehouse.of(world, colony).count(Items.OAK_LOG);

            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, tile.up(6));
            Workplaces.assign(world, colony);

            runWork(world, manager, colony, woodsman, 40, Schedule.MORNING_WORK);

            if (!world.getBlockState(tile.up(1)).isAir()) {
                context.throwGameTestException("Ствол не свален: на высоте один стоит "
                        + world.getBlockState(tile.up(1)).getBlock());
            }
            if (Warehouse.of(world, colony).count(Items.OAK_LOG) < logsBefore + 4) {
                context.throwGameTestException("Брёвна не легли на склад: было " + logsBefore
                        + ", стало " + Warehouse.of(world, colony).count(Items.OAK_LOG));
            }
            if (!world.getBlockState(tile).isIn(BlockTags.SAPLINGS)) {
                context.throwGameTestException("На месте срубленного дерева не посажен саженец: "
                        + world.getBlockState(tile).getBlock());
            }
        } finally {
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Лесоруб счищает дикий лес, но не разбирает зданий.
     * <p>
     * Это не мелочь: фахверк норманнских домов сложен из тёмного дуба, то есть
     * из брёвен. Без проверки «внутри здания» лесоруб унёс бы на склад стены
     * той самой мастерской, в которой работает.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "lumberjack")
    public void lumberjackClearsWildForestButSparesBuildings(TestContext context) {
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

            // Угловой столб мастерской — тоже бревно.
            BlockPos post = anchor.add(0, 1, 0);
            if (!world.getBlockState(post).isIn(BlockTags.LOGS)) {
                context.throwGameTestException("Ожидался бревенчатый столб мастерской, стоит "
                        + world.getBlockState(post).getBlock());
            }

            // Дикое дерево за пределами следа здания, но в границах колонии.
            BlockPos wild = anchor.add(13, 1, 2);
            for (int dy = 0; dy < 3; dy++) {
                world.setBlockState(wild.up(dy), Blocks.OAK_LOG.getDefaultState());
            }

            // Роща занята саженцами, так что лесоруб пойдёт в дикий лес.
            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, wild.up(4));
            Workplaces.assign(world, colony);

            runWork(world, manager, colony, woodsman, 30, Schedule.MORNING_WORK);

            if (!world.getBlockState(wild).isAir()) {
                context.throwGameTestException("Дикое дерево не счищено: стоит "
                        + world.getBlockState(wild).getBlock());
            }
            if (!world.getBlockState(post).isIn(BlockTags.LOGS)) {
                context.throwGameTestException("Лесоруб разобрал столб собственной мастерской");
            }
            for (BlockPos tile : GatherJob.groveTiles(hut)) {
                if (!world.getBlockState(tile).isIn(BlockTags.SAPLINGS)) {
                    context.throwGameTestException("Лесоруб выдрал саженцы из своей рощи");
                }
            }
        } finally {
            for (int dy = 0; dy < 3; dy++) {
                world.setBlockState(anchor.add(13, 1 + dy, 2), Blocks.AIR.getDefaultState());
            }
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- задача 1.9б: фермер ---

    /**
     * Построенная ферма даёт засеянное поле, воду и рабочее место — а фермер
     * к ней приписывается, хотя имена профессии и здания не совпадают.
     * <p>
     * «Фермер» работает на «ферме», и это ровно тот случай, ради которого
     * профессия называет своё рабочее место в данных, а не выводит его
     * из собственного имени.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void builtFarmGivesASownFieldAndAWorkplace(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            if (BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ферма не достроилась");
            }

            // Двадцать три, а не двадцать четыре: одну клетку занял тюк
            // пугала, которое с этой правки стоит на поле с первого
            // уровня. Число здесь написано числом намеренно — считать
            // грядки по той же схеме, которую строит проверка, значит
            // спрашивать ответ у проверяемого.
            List<BlockPos> plots = FarmJob.plots(farm);
            if (plots.size() != 23) {
                context.throwGameTestException("Грядок на ферме " + plots.size()
                        + ", в схеме двадцать три");
            }
            for (BlockPos plot : plots) {
                if (!world.getBlockState(plot).isIn(BlockTags.CROPS)) {
                    context.throwGameTestException("Грядка не засеяна: "
                            + world.getBlockState(plot).getBlock());
                }
                if (!world.getBlockState(plot.down()).isOf(Blocks.FARMLAND)) {
                    context.throwGameTestException("Под посевом не грядка");
                }
            }

            // То, что растёт на поле, житель должен уметь съесть: иначе фермер
            // кормит склад, а не колонию, и голод из задачи 1.8 остаётся на игроке.
            Item food = FarmJob.seedOf(world, farmPlan).orElse(Items.AIR);
            if (!food.getDefaultStack().isIn(ModTags.CITIZEN_FOOD)) {
                context.throwGameTestException("Урожай фермы жителям не еда: " + food);
            }

            // Источник воды в середине поля. Он и есть причина, по которой
            // вода попала в тег декора: иначе она растекается сквозь
            // недостроенную ограду с первого же слоя.
            BlockPos well = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(3, 1, 3));
            if (!world.getBlockState(well).isOf(Blocks.WATER)) {
                context.throwGameTestException("В середине поля нет воды, стоит "
                        + world.getBlockState(well).getBlock());
            }

            if (Workplaces.stations(farm).isEmpty()) {
                context.throwGameTestException("У фермы нет рабочего места");
            }

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, hall.up());
            Workplaces.assign(world, colony);

            Building assigned = Workplaces.of(colony, farmer).orElse(null);
            if (assigned == null) {
                context.throwGameTestException("Фермеру не досталось рабочее место: имя здания "
                        + "задано в данных профессии, а не выведено из её имени");
            }
            if (!assigned.id().equals(farm.id())) {
                context.throwGameTestException("Фермер приписан к чужому зданию");
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
     * Приёмка задачи 1.9б: фермер жнёт поспевшее, сдаёт урожай на склад
     * и засевает грядку заново — за одну морковь из того же склада.
     * <p>
     * Проверяется в два приёма, потому что урожай и посевное — один и тот же
     * предмет: сначала один шаг стратегии на жатву, потом остальные на посев.
     * Иначе тест зависел бы от случайного числа морковок в добыче.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void farmerHarvestsRipeCropAndSowsItAgain(TestContext context) {
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

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos plot = FarmJob.plots(farm).get(0);
            world.setBlockState(plot, ((CropBlock) Blocks.CARROTS).withAge(CropBlock.MAX_AGE));

            // Склад пуст по этой культуре: всё, что появится, пришло с грядки.
            Warehouse before = Warehouse.of(world, colony);
            before.take(crop, before.count(crop));

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, plot.up(2));
            Workplaces.assign(world, colony);

            // Один шаг — жатва.
            runWork(world, manager, colony, farmer, 1, Schedule.MORNING_WORK);

            int harvested = Warehouse.of(world, colony).count(crop);
            if (harvested < 1) {
                context.throwGameTestException("Урожай не попал на склад");
            }
            if (!world.getBlockState(plot).isAir()) {
                context.throwGameTestException("Поспевшая грядка не сжата: стоит "
                        + world.getBlockState(plot).getBlock());
            }

            // Остальные шаги — посев за одну морковь со склада.
            runWork(world, manager, colony, farmer, 4, Schedule.MORNING_WORK);

            BlockState sown = world.getBlockState(plot);
            if (!sown.isIn(BlockTags.CROPS)) {
                context.throwGameTestException("Сжатая грядка не засеяна заново: "
                        + sown.getBlock());
            }
            if (sown.getBlock() instanceof CropBlock ripe && ripe.isMature(sown)) {
                context.throwGameTestException("На грядке снова поспевший колос — "
                        + "значит его не сжали, а посчитали");
            }
            int left = Warehouse.of(world, colony).count(crop);
            if (left != harvested - 1) {
                context.throwGameTestException("На посев ушло не одно семя: было " + harvested
                        + ", осталось " + left);
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
     * Без запаса на складе грядка остаётся пустой.
     * <p>
     * Это и есть замкнутый круг: посевное берётся оттуда, куда сам же фермер
     * сдал урожай. Иначе поле заполнялось бы из ничего, и колония кормилась
     * бы воздухом.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void farmerSowsOnlyWhatTheStorageHas(TestContext context) {
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

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos bare = FarmJob.plots(farm).get(0);
            world.setBlockState(bare, Blocks.AIR.getDefaultState());

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, bare.up(2));
            Workplaces.assign(world, colony);

            // Посевного на складе нет — грядка остаётся пустой.
            Warehouse empty = Warehouse.of(world, colony);
            empty.take(crop, empty.count(crop));
            runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

            if (!world.getBlockState(bare).isAir()) {
                context.throwGameTestException("Грядка засеяна без запаса на складе: "
                        + world.getBlockState(bare).getBlock());
            }

            // Завезли — и поле снова полное, ровно на одну морковь дешевле.
            Warehouse.of(world, colony).add(new ItemStack(crop, 4));
            runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

            if (!world.getBlockState(bare).isIn(BlockTags.CROPS)) {
                context.throwGameTestException("С запасом на складе грядка так и не засеяна");
            }
            int left = Warehouse.of(world, colony).count(crop);
            if (left != 3) {
                context.throwGameTestException("Со склада ушло не одно семя, а " + (4 - left));
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
     * Приёмка задачи 1.9б целиком: колония кормит себя сама.
     * <p>
     * До этой профессии еду в ратушу носил игрок, и голод из задачи 1.8
     * упирался в него. Здесь на складе нет ни крошки — а через один шаг
     * работы фермера голодный житель ест то, что снято с грядки.
     * <p>
     * Ровно поэтому норманнское поле растит морковь: пшеницу житель съесть
     * не может, и поле пшеницы кормило бы только склад.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void colonyFeedsItselfFromItsOwnFarm(TestContext context) {
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

            if (Warehouse.of(world, colony).hasAny(ModTags.CITIZEN_FOOD)) {
                context.throwGameTestException("На складе есть еда до работы фермера — "
                        + "тогда тест ничего не доказывает");
            }

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos plot = FarmJob.plots(farm).get(0);
            world.setBlockState(plot, ((CropBlock) Blocks.CARROTS).withAge(CropBlock.MAX_AGE));

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, plot.up(2));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, farmer, 1, Schedule.MORNING_WORK);

            int grown = Warehouse.of(world, colony).count(crop);
            if (grown < 1) {
                context.throwGameTestException("После работы фермера склад пуст");
            }

            Citizen eater = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            eater.setSaturation(0);

            // Первое решение отправляет к еде, второе — кормит.
            runWork(world, manager, colony, eater, 3, Schedule.MEAL);

            if (eater.saturation() < Needs.nourishment(crop)) {
                context.throwGameTestException("Житель не поел урожаем: сытость "
                        + eater.saturation());
            }
            if (Warehouse.of(world, colony).count(crop) >= grown) {
                context.throwGameTestException("Житель поел, а со склада ничего не ушло");
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
     * Вытоптанная грядка вскапывается заново, а не выпадает из поля навсегда.
     * <p>
     * Любой прыгнувший на грядку — житель, корова, сам игрок — сбивает её
     * до земли. Без починки ферма год за годом превращается в пустырь,
     * и игрок видит здание, которое перестало работать без причины.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void trampledPlotIsTilledAndSownAgain(TestContext context) {
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

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos plot = FarmJob.plots(farm).get(0);

            // Кто-то прыгнул на грядку: земля сбита, посев слетел.
            world.setBlockState(plot, Blocks.AIR.getDefaultState());
            world.setBlockState(plot.down(), Blocks.DIRT.getDefaultState());

            Warehouse.of(world, colony).add(new ItemStack(crop, 4));

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, plot.up(2));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

            if (!world.getBlockState(plot.down()).isOf(Blocks.FARMLAND)) {
                context.throwGameTestException("Грядку не вскопали заново: под посевом "
                        + world.getBlockState(plot.down()).getBlock());
            }
            if (!world.getBlockState(plot).isIn(BlockTags.CROPS)) {
                context.throwGameTestException("Вскопанную грядку не засеяли: "
                        + world.getBlockState(plot).getBlock());
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
     * Чужое на грядке фермер не трогает.
     * <p>
     * Игрок вправе поставить на своём поле что угодно; выкапывать землю
     * из-под его сундука — не работа фермера, а порча имущества.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void farmerLeavesPlayerBlocksOnTheFieldAlone(TestContext context) {
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

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos plot = FarmJob.plots(farm).get(0);

            world.setBlockState(plot, Blocks.STONE.getDefaultState());
            world.setBlockState(plot.down(), Blocks.DIRT.getDefaultState());

            Warehouse.of(world, colony).add(new ItemStack(crop, 4));

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, plot.up(2));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

            if (!world.getBlockState(plot).isOf(Blocks.STONE)) {
                context.throwGameTestException("Фермер убрал чужой блок с грядки");
            }
            if (!world.getBlockState(plot.down()).isOf(Blocks.DIRT)) {
                context.throwGameTestException("Фермер вскопал землю под чужим блоком");
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- кто чем занят и когда отступается ---

    /**
     * Курьер и билдер делят одну стройку: подвоз материалов не запрещает
     * стройку.
     * <p>
     * Занятость считается по делу, а не по зданию, и это не тонкость.
     * Курьер, несущий материалы, держит в состоянии работы ту же стройку,
     * что и билдер, — общий счёт означал бы, что стоит курьеру взяться
     * за подвоз, и здание перестаёт строиться вообще. Ровно это и было
     * после починки толкотни.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "claims")
    public void courierAndBuilderShareOneSite(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        // Далеко по высоте: те же чанки заведомо загружены, а склад «не рядом».
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 24, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);
            if (BuildJob.storageIsNearby(Warehouse.of(world, colony), site)) {
                context.throwGameTestException("Склад оказался рядом — курьеру нечего делать");
            }

            Citizen porter = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());

            runWork(world, manager, colony, porter, 1, Schedule.MORNING_WORK);
            if (porter.jobState().building().filter(site.id()::equals).isEmpty()) {
                context.throwGameTestException("Курьер не взялся за заявку — проверять нечего");
            }

            runWork(world, manager, colony, mason, 1, Schedule.MORNING_WORK);
            if (mason.jobState().building().filter(site.id()::equals).isEmpty()) {
                context.throwGameTestException("Билдер не взялся за стройку, к которой едет "
                        + "курьер: подвоз материалов запретил стройку");
            }

            // А вот второй билдер по-прежнему за неё не берётся.
            Citizen second = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, second, 1, Schedule.MORNING_WORK);
            if (!second.jobState().isIdle()) {
                context.throwGameTestException("Двое билдеров на одной стройке снова толкаются");
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
     * Житель, дважды упёршийся в одну и ту же цель, переносится к ней.
     * <p>
     * Отказ от недостижимого — первая ступень, и она верна: пусть возьмётся
     * за другое. Но у билдера дело одно — стройка, — и отказ кончался тем,
     * что через полминуты он снова упирался в ту же стену, и так до конца
     * мира; в сохранении заказчика ровно так висела стройка ларька майя.
     * MineColonies в таком случае переносит жителя, «набравшегося решимости»,
     * на место рядом с целью, — это и есть последняя ступень, после отказа,
     * а не вместо него.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "stuck", tickLimit = 900)
    public void aWorkerStuckTwiceIsBroughtToHisWork(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos cell = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos work = context.getAbsolutePos(new BlockPos(8, 2, 2));
        List<BlockPos> stone = new ArrayList<>();
        Settlement colony = colonyWithBuilder(world, manager, hall);

        // Пол под целью и каменная клетка вокруг жителя: отсюда не выйти.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (BlockPos at : List.of(work.add(dx, -1, dz), cell.add(dx, -1, dz),
                        cell.add(dx, 2, dz))) {
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    stone.add(at);
                }
                if (dx != 0 || dz != 0) {
                    for (int up = 0; up <= 1; up++) {
                        BlockPos wall = cell.add(dx, up, dz);
                        world.setBlockState(wall, Blocks.STONE.getDefaultState());
                        stone.add(wall);
                    }
                }
            }
        }
        // Без ремесла: стратегия мода не метит им в цели сама и не мешает проверке.
        Citizen walledIn = evenNewborn("Узник", "", NORMAN, Gender.MALE);
        walledIn.setLived(com.villagepax.sim.life.Ages.grownAt());
        walledIn.setPosition(Vec3d.ofBottomCenter(cell));
        colony.addCitizen(walledIn);
        CitizenEntity body = CitizenSpawner.spawnBody(world, colony, walledIn);

        for (int decision = 0; decision < 8; decision++) {
            body.noteReachAttempt(work);
        }
        if (!body.isUnreachable(work)) {
            context.throwGameTestException("Первый отказ не записан: житель не отступился");
        }
        if (body.getBlockPos().getSquaredDistance(cell) > 1) {
            context.throwGameTestException("Житель перенесён с первого же отказа — "
                    + "это не последняя ступень, а первая");
        }

        // Память об отказе живёт полминуты; потом он пробует снова — и снова упирается.
        context.runAtTick(700, () -> {
            try {
                for (int decision = 0; decision < 8; decision++) {
                    body.noteReachAttempt(work);
                }
                if (body.getBlockPos().getSquaredDistance(work) > 4) {
                    context.throwGameTestException("Дважды упёршись в одну цель, житель так "
                            + "и сидит в клетке на " + body.getBlockPos().toShortString());
                }
            } finally {
                discardBodies(world, colony);
                manager.remove(colony.id());
                for (BlockPos at : stone) {
                    world.setBlockState(at, Blocks.AIR.getDefaultState());
                }
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /**
     * Житель отступается от точки, до которой не может дойти, и берётся
     * за следующее дело.
     * <p>
     * Работа выбирается как первая подходящая. Без отступления недостижимая
     * грядка держала фермера навсегда: он выбирал её решение за решением,
     * а всё остальное поле стояло. Игрок видит это как зависшего работника.
     * <p>
     * Проверяется <b>без телепорта</b>: обычный прогон работы подносит
     * жителя к цели, и недостижимости в нём не бывает по определению.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "claims")
    public void workerGivesUpOnAnUnreachableTarget(TestContext context) {
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

            // Две грядки поспели: отступившись от первой, фермер должен
            // взяться за вторую.
            List<BlockPos> plots = FarmJob.plots(farm);
            BlockState mature = ((CropBlock) Blocks.CARROTS).withAge(CropBlock.MAX_AGE);
            world.setBlockState(plots.get(0), mature);
            world.setBlockState(plots.get(5), mature);

            // Фермер далеко и с места не двигается: тест не телепортирует его.
            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, hall.up(40));
            Workplaces.assign(world, colony);
            CitizenEntity body = (CitizenEntity) world.getEntity(farmer.entityUuid().orElseThrow());

            WorkTicker.decide(world, manager, colony, farmer, Schedule.MORNING_WORK);
            BlockPos first = body.workTarget();
            if (first == null) {
                context.throwGameTestException("Фермер не выбрал грядку");
            }

            // Смотрим на память о недостижимом, а не на саму цель: цель
            // теперь — место, ОТКУДА берутся за грядку, а не сама грядка,
            // и у двух соседних грядок это место может быть одним и тем же.
            for (int decision = 0; decision < 12; decision++) {
                WorkTicker.decide(world, manager, colony, farmer, Schedule.MORNING_WORK);
            }

            boolean gaveUp = body.isUnreachable(plots.get(0))
                    || body.isUnreachable(plots.get(5));
            if (!gaveUp) {
                context.throwGameTestException("Фермер двенадцать решений метит в одну "
                        + "недостижимую грядку и не отступается: цель "
                        + body.workTarget());
            }

            BlockPos later = body.workTarget();
            if (later == null) {
                context.throwGameTestException("Отступившись, фермер не взялся ни за что");
                return;
            }
            boolean besidePlot = plots.stream()
                    .anyMatch(plot -> plot.getSquaredDistance(later) <= 9);
            if (!besidePlot) {
                context.throwGameTestException("Отступившись, фермер пошёл не к грядке: "
                        + later.toShortString());
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- работа в руках ---

    /**
     * Строитель держит тот блок, который ставит.
     * <p>
     * Просьба заказчика, и не косметическая: без предмета в руке понять,
     * что происходит, можно только по растущей стене. Проверяется на сервере,
     * потому что снаряжение — серверное состояние: клиент его лишь рисует.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hands")
    public void builderHoldsTheBlockHeIsPlacing(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(mason.entityUuid().orElseThrow());

            if (!body.getMainHandStack().isEmpty()) {
                context.throwGameTestException("Житель родился с предметом в руке");
            }

            runWork(world, manager, colony, mason, 40, Schedule.MORNING_WORK);

            // В руке должен быть ровно тот блок, который стоит следующим
            // в плане, — а не просто «что-нибудь».
            Item expected = expectedBlockInHand(housePlan, site);
            if (expected == null) {
                context.throwGameTestException("План кончился раньше, чем тест успел проверить руку");
            }
            if (!body.getMainHandStack().isOf(expected)) {
                context.throwGameTestException("В руке " + body.getMainHandStack()
                        + ", а ставит он " + expected);
            }

            // Отпустил работу — руки пусты.
            mason.setJobState(JobState.IDLE);
            mason.setProfession(null);
            runWork(world, manager, colony, mason, 1, Schedule.MORNING_WORK);
            if (!body.getMainHandStack().isEmpty()) {
                context.throwGameTestException("Житель без дела остался с инструментом: "
                        + body.getMainHandStack());
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
     * Лесоруб держит топор, курьер — свой груз.
     * <p>
     * У курьера это самый честный показ работы в моде: игрок видит не
     * «житель идёт», а «житель несёт двадцать брёвен вон туда».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hands")
    public void workersCarryTheirToolsAndLoads(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic hutPlan = schematic(context, LUMBERJACK_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building hut = plan(colony, anchor, LUMBERJACK_TYPE, BlockRotation.NONE);
        // Далеко по высоте, а не по горизонтали: те же чанки заведомо загружены.
        Building far = plan(colony, anchor.add(0, 22, 0), HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, hutPlan);
            BuildJob.advance(world, manager, colony.id(), hut.id(), 10_000);

            // Лесоруб: на грядке выросло дерево, значит есть работа.
            BlockPos tile = GatherJob.groveTiles(hut).get(0);
            for (int dy = 0; dy < 3; dy++) {
                world.setBlockState(tile.up(dy), Blocks.OAK_LOG.getDefaultState());
            }
            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, tile.up(5));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, woodsman, 1, Schedule.MORNING_WORK);

            CitizenEntity axeman = (CitizenEntity) world.getEntity(woodsman.entityUuid().orElseThrow());
            if (!axeman.getMainHandStack().isOf(Items.IRON_AXE)) {
                context.throwGameTestException("Лесоруб без топора: "
                        + axeman.getMainHandStack());
            }

            // Курьер: далёкая стройка и материалы на складе — он их понесёт.
            // Завозится ровно то, что нужно дому: иначе курьеру нечего нести
            // и тест проверял бы не руки, а собственную догадку.
            stockFor(world, colony, schematic(context, HOUSE_SCHEMATIC));
            Citizen porter = hireWithBody(world, colony, HaulJob.COURIER, hall.up());

            CitizenEntity carrier = (CitizenEntity) world.getEntity(porter.entityUuid().orElseThrow());

            // Груз живёт недолго: взял, донёс, сдал. Поэтому руки проверяются
            // в тот шаг, когда груз действительно в них, а не после.
            boolean seenCarrying = false;
            for (int round = 0; round < 24 && !seenCarrying; round++) {
                runWork(world, manager, colony, porter, 1, Schedule.MORNING_WORK);

                Identifier load = porter.jobState().firstLoad()
                        .map(JobState.Load::item)
                        .orElse(null);
                if (load == null) {
                    continue;
                }
                seenCarrying = true;

                if (!carrier.getMainHandStack().isOf(Registries.ITEM.get(load))) {
                    context.throwGameTestException("Курьер несёт " + load
                            + ", а в руках у него " + carrier.getMainHandStack());
                }
            }
            if (!seenCarrying) {
                context.throwGameTestException("Курьер так и не взял груз — тест проверяет не то");
            }
        } finally {
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Инструмент не выпадает из погибшего жителя.
     * <p>
     * Иначе топор лесоруба — бесконечный источник железа: житель погиб,
     * топор упал, наняли нового — и снова топор. Проверяется смертью,
     * а не полем «шанс выпадения»: ваниль его наружу не отдаёт, а важно
     * тут поведение.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hands")
    public void toolsDoNotDropFromTheBody(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen worker = hireWithBody(world, colony, GatherJob.LUMBERJACK, hall.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());
            body.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));

            body.kill();

            Box around = new Box(hall).expand(6.0);
            for (ItemEntity dropped : world.getEntitiesByClass(ItemEntity.class, around, entity -> true)) {
                if (dropped.getStack().isOf(Items.IRON_AXE)) {
                    context.throwGameTestException("Из погибшего жителя выпал топор");
                }
                dropped.discard();
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- по следам жалобы: остальные ремёсла тоже никто не гонял ---

    /**
     * Фермера посылают только туда, где можно стоять.
     * <p>
     * Билдера я после жалобы игрока проверил на всех схемах, а фермера,
     * лесоруба и курьера — никто. У них та же беда: цель ставит стратегия,
     * и если она назовёт точку в воздухе или в огне, житель послушно туда
     * пойдёт. Проверяется тот же инвариант, что у билдера.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trades")
    public void farmerIsSentOnlyWhereHeCanStand(TestContext context) {
        checkTradeReach(context, new Identifier("villagepax", "norman/farm_lvl1"),
                FARM_TYPE, new Identifier("villagepax", "farmer"));
    }

    /** Лесоруба — тоже: у него роща, и в ствол вставать нельзя. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trades")
    public void lumberjackIsSentOnlyWhereHeCanStand(TestContext context) {
        checkTradeReach(context, new Identifier("villagepax", "norman/lumberjack_lvl1"),
                new Identifier("villagepax", "norman/lumberjack"),
                new Identifier("villagepax", "lumberjack"));
    }

    /**
     * Курьера — тоже: он ходит между складом и стройкой, и обе точки
     * выбирает стратегия.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "trades")
    public void courierIsSentOnlyWhereHeCanStand(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, new Identifier("villagepax", "norman/house_lvl1"));

        BlockPos hall = context.getAbsolutePos(new BlockPos(20, 9, 2));
        List<BlockPos> ground = new ArrayList<>();

        try {
            for (int x = -3; x <= 24; x++) {
                for (int z = -3; z <= 10; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall);
            // Стройка нарочно далеко от склада: ближе двенадцати блоков
            // билдер носит сам, и курьеру нечего было бы делать.
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
            Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 1, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(site);

            try {
                stockFor(world, colony, house);
                Citizen courier = hireWithBody(world, colony,
                        new Identifier("villagepax", "courier"),
                        context.getAbsolutePos(new BlockPos(18, 9, 2)));

                List<String> complaints = watchWhereHeIsSent(world, manager, colony, courier,
                        200, "курьер");
                if (!complaints.isEmpty()) {
                    context.throwGameTestException(String.join("\n  ", complaints));
                }
            } finally {
                demolish(world, site, house);
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

}
