package com.villagepax.gametest;

import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import com.villagepax.core.ModTags;
import com.villagepax.sim.build.Roads;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.work.HaulJob;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.GatherJob;
import com.villagepax.sim.work.Schedule;
import net.minecraft.block.BlockState;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Roads;
import java.util.ArrayList;
import java.util.List;

/**
 * Дороги под ногами, калитки в ограде и улицы колонии.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class RoadTests extends GameTestSupport {

    // --- дорога под ногами ---

    /**
     * Житель предпочитает идти по дороге, а не напрямик через газон.
     * <p>
     * Проверяется сравнением: одна и та же местность с мостовой и без неё.
     * Без сравнения тест ничего не значил бы — путь и так мог бы случайно
     * лежать на нужном ряду.
     * <p>
     * Что считать дорогой, решает тег {@code villagepax:preferred_path}:
     * этот тест заодно проверяет, что тег вообще доехал до датапака.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "road")
    public void citizenPrefersThePavedRoute(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos corner = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        List<BlockPos> ground = new ArrayList<>();

        try {
            // Луг три блока в ширину и тринадцать в длину.
            for (int x = 0; x < 13; x++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos at = corner.add(x, 0, z);
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    ground.add(at);
                }
            }

            BlockPos from = corner.add(0, 1, 0);
            BlockPos to = corner.add(12, 1, 0);

            Citizen walker = hireWithBody(world, colony, HaulJob.COURIER, from);
            CitizenEntity body = (CitizenEntity) world.getEntity(walker.entityUuid().orElseThrow());
            body.setOnGround(true);

            int overGrass = pavedSteps(world, body, to, corner);

            // Замостим средний ряд — и путь обязан на него перейти.
            for (int x = 0; x < 13; x++) {
                world.setBlockState(corner.add(x, 0, 1), Blocks.DIRT_PATH.getDefaultState());
            }
            int overRoad = pavedSteps(world, body, to, corner);

            if (overRoad <= overGrass) {
                context.throwGameTestException("Дорога ничего не изменила: шагов по среднему ряду "
                        + overRoad + " с мостовой и " + overGrass + " без неё");
            }
            if (overRoad < 6) {
                context.throwGameTestException("По мостовой прошли всего " + overRoad
                        + " шагов из тринадцати — надбавка за бездорожье не работает");
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Житель не ходит по верху забора.
     * <p>
     * Жалоба игрока: строитель шёл по верхам заборов, скатывался с них
     * и ходил кругами, повиснув насмерть. Ваниль такой путь разрешает —
     * у забора коробка столкновений в полтора блока, и моб как бы на нём
     * стоит, — а стоит он там плохо.
     * <p>
     * Проверяется настоящим поиском пути через огороженное поле: путь
     * внутрь есть (калитка открыта), и ни один его узел не стоит
     * на заборе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gates")
    public void citizenNeverWalksAlongFenceTops(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        // Житель ставится с той стороны, где через забор — КОРОЧЕ: калитка
        // на западе, а он приходит с востока. Без запрета ваниль полезет
        // прямо через ограду, потому что обход к калитке вдвое дальше.
        // С площадки у калитки этот тест не кусался бы вовсе: туда путь
        // и так идёт по земле.
        BlockPos landing = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(7, 1, 3));

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            world.setBlockState(landing, Blocks.DIRT.getDefaultState());
            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, landing.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(farmer.entityUuid().orElseThrow());
            body.setOnGround(true);

            BlockPos plot = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(5, 2, 3));
            Path through = body.getNavigation().findPathTo(plot, 0);
            if (through == null) {
                context.throwGameTestException("Пути на поле нет вовсе — калитка сломалась");
                return;
            }

            for (int index = 0; index < through.getLength(); index++) {
                BlockPos step = through.getNode(index).getBlockPos();
                BlockPos under = step.down();
                if (world.getBlockState(under).isIn(BlockTags.FENCES)
                        || world.getBlockState(under).isIn(BlockTags.FENCE_GATES)) {
                    context.throwGameTestException("Путь идёт по верху забора: узел "
                            + step.toShortString() + " стоит на "
                            + world.getBlockState(under).getBlock());
                }
            }
        } finally {
            demolish(world, farm, farmPlan);
            world.setBlockState(landing, Blocks.AIR.getDefaultState());
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * С забора житель сходит сам.
     * <p>
     * Жалоба игрока: строитель шёл по верхам заборов, скатывался с них
     * и ходил кругами, повиснув насмерть. Поиск пути через забор его
     * не ведёт — ваниль считает забор непроходимым, — но оказаться на нём
     * он может: коробка столкновений у забора полтора блока, и в толкотне
     * у калитки жители выталкивают друг друга наверх.
     * <p>
     * Стоя там, житель ломает себе путь: ноги на полуторной высоте, узла
     * пути в этой точке нет, и он съезжает к цели снова и снова. Поэтому
     * его снимают на землю.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gates")
    public void citizenStepsOffAFenceByItself(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos ground = context.getAbsolutePos(new BlockPos(6, 8, 6));
        BlockPos fence = ground.add(2, 0, 0);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        List<BlockPos> laid = new ArrayList<>();

        try {
            // Площадка и забор на ней.
            for (int dx = -2; dx <= 3; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos at = ground.add(dx, 0, dz);
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    laid.add(at);
                }
            }
            world.setBlockState(fence.up(), Blocks.OAK_FENCE.getDefaultState());
            laid.add(fence.up());

            // Житель ставится ровно на забор — так его и выталкивает толкотня.
            Citizen worker = hireWithBody(world, colony, BuildJob.BUILDER, fence.up(2));
            CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());
            body.refreshPositionAndAngles(fence.getX() + 0.5, fence.getY() + 2,
                    fence.getZ() + 0.5, 0f, 0f);

            if (!world.getBlockState(body.getBlockPos().down()).isOf(Blocks.OAK_FENCE)) {
                context.throwGameTestException("Посылка теста не выполнена: житель не на заборе, "
                        + "а на " + world.getBlockState(body.getBlockPos().down()).getBlock());
            }

            if (!body.stepOutOfTrouble()) {
                context.throwGameTestException("Житель остался стоять на заборе");
            }
            if (world.getBlockState(body.getBlockPos().down()).isOf(Blocks.OAK_FENCE)) {
                context.throwGameTestException("Житель сошёл, но опять на забор");
            }
            if (!world.getBlockState(body.getBlockPos().down())
                    .isSolidBlock(world, body.getBlockPos().down())) {
                context.throwGameTestException("Житель сошёл в пустоту: под ним "
                        + world.getBlockState(body.getBlockPos().down()).getBlock());
            }

            // И на твёрдой земле его больше не трогают.
            if (body.stepOutOfTrouble()) {
                context.throwGameTestException("Жителя на земле всё равно переносят");
            }
        } finally {
            for (BlockPos at : laid) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- калитки в ограде ---

    /**
     * На огороженное поле можно войти: в ограде есть открытая калитка.
     * <p>
     * Ванильный поиск пути считает закрытую калитку непроходимой
     * ({@code PathNodeType.FENCE}), а открывать их умеют только двери
     * у деревенских жителей. С глухой оградой фермер стоял бы снаружи
     * своего поля и не делал ничего — и это было ровно так.
     * <p>
     * Проверяется настоящим поиском пути, а не наличием блока: калитку
     * можно поставить и оставить закрытой.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gates")
    public void fencedFarmCanBeEntered(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);
        BlockPos landing = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(-1, 1, 3));

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            BlockPos gate = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(0, 2, 3));
            BlockPos plot = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(1, 2, 3));

            if (!(world.getBlockState(gate).getBlock() instanceof FenceGateBlock)) {
                context.throwGameTestException("В ограде поля нет калитки, стоит "
                        + world.getBlockState(gate).getBlock());
            }

            // Площадка снаружи: поле стоит в пустоте, идти жителю неоткуда.
            world.setBlockState(landing, Blocks.DIRT.getDefaultState());

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, landing.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(farmer.entityUuid().orElseThrow());

            // Только что созданное тело ещё не коснулось земли, а поиск пути
            // из воздуха ванилью запрещён. В игре это происходит само
            // на первом же тике.
            body.setOnGround(true);

            Path through = body.getNavigation().findPathTo(plot, 0);
            if (through == null || !through.reachesTarget()) {
                context.throwGameTestException("Фермер не может войти на своё поле: "
                        + (through == null ? "пути нет вовсе" : "путь не доходит"));
            }

            // Заменим калитку забором — и пути внутрь больше нет. Проверка
            // держится на этом: иначе тест прошёл бы и с глухой оградой.
            world.setBlockState(gate, Blocks.OAK_FENCE.getDefaultState());
            Path blocked = body.getNavigation().findPathTo(plot, 0);
            if (blocked != null && blocked.reachesTarget()) {
                context.throwGameTestException("Путь нашёлся сквозь глухую ограду — "
                        + "значит проверка ничего не проверяет");
            }
        } finally {
            world.setBlockState(landing, Blocks.AIR.getDefaultState());
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** В рощу лесоруба тоже есть вход — по той же причине. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gates")
    public void fencedGroveCanBeEntered(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic hutPlan = schematic(context, LUMBERJACK_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building hut = plan(colony, anchor, LUMBERJACK_TYPE, BlockRotation.NONE);
        BlockPos landing = BuildJob.worldPos(hut, hutPlan.size(), new BlockPos(10, 0, 2));

        try {
            stockFor(world, colony, hutPlan);
            BuildJob.advance(world, manager, colony.id(), hut.id(), 10_000);

            BlockPos gate = BuildJob.worldPos(hut, hutPlan.size(), new BlockPos(9, 1, 2));
            BlockPos inside = BuildJob.worldPos(hut, hutPlan.size(), new BlockPos(8, 1, 2));

            if (!(world.getBlockState(gate).getBlock() instanceof FenceGateBlock)) {
                context.throwGameTestException("В ограде рощи нет калитки, стоит "
                        + world.getBlockState(gate).getBlock());
            }

            world.setBlockState(landing, Blocks.DIRT.getDefaultState());

            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, landing.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(woodsman.entityUuid().orElseThrow());
            body.setOnGround(true);

            Path through = body.getNavigation().findPathTo(inside, 0);
            if (through == null || !through.reachesTarget()) {
                context.throwGameTestException("Лесоруб не может войти в свою рощу: "
                        + (through == null ? "пути нет вовсе" : "путь не доходит"));
            }
        } finally {
            world.setBlockState(landing, Blocks.AIR.getDefaultState());
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- улицы колонии ---

    /**
     * Билдер сам мостит улицу от готового дома к ратуше.
     * <p>
     * Решение заказчика: деревня должна становиться деревней, а не набором
     * домов на траве. Приказа игрока на это нет — билдер берётся за улицу,
     * когда строить больше нечего.
     * <p>
     * Материала на складе нет, поэтому улица получается натоптанной тропой:
     * она ничего не стоит, ровно как удар лопатой по траве у игрока. Так
     * улицы появляются и у самой бедной колонии — иначе игрок не увидел бы
     * этой работы вовсе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "streets")
    public void builderPavesAStreetToTheTownHall(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(STREET_HALL);
        List<BlockPos> lawn = lawn(world, context);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, context.getAbsolutePos(STREET_HOUSE), HOUSE_TYPE,
                BlockRotation.NONE);

        try {
            raiseHouse(context, world, manager, colony, house, housePlan);

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(mason.entityUuid().orElseThrow());

            // Три решения: найти дело, дойти, положить первый тайл.
            runWork(world, manager, colony, mason, 3, Schedule.MORNING_WORK);
            if (!body.getMainHandStack().isOf(Items.IRON_SHOVEL)) {
                context.throwGameTestException("Билдер топчет тропу без лопаты в руке: "
                        + body.getMainHandStack());
            }

            runWork(world, manager, colony, mason, 20, Schedule.MORNING_WORK);

            List<BlockPos> street = streetOf(world, colony, house);
            if (street.size() < 4) {
                context.throwGameTestException("Маршрут улицы вышел длиной " + street.size()
                        + " — от дома до площади должно быть дальше");
            }

            for (BlockPos tile : street) {
                BlockState state = world.getBlockState(tile);
                if (!state.isOf(Blocks.DIRT_PATH)) {
                    context.throwGameTestException("Улица прервалась на "
                            + tile.toShortString() + ": там " + state.getBlock());
                }
                if (!state.isIn(ModTags.PREFERRED_PATH)) {
                    context.throwGameTestException("Замощённое не считается дорогой — "
                            + "поиск пути по такой улице жителей не поведёт");
                }
            }

            // Путь ведёт ОТ ДВЕРИ: первый тайл стоит прямо перед входом.
            BlockPos door = BuildJob.pointsOfInterest(house, housePlan, MarkerKind.DOOR).get(0);
            BlockPos first = street.get(0);
            int fromDoor = Math.max(Math.abs(first.getX() - door.getX()),
                    Math.abs(first.getZ() - door.getZ()));
            if (fromDoor != 1) {
                context.throwGameTestException("Улица начинается в " + fromDoor
                        + " блоках от двери " + door.toShortString() + ", а надо у порога");
            }

            // И маршрут не рвётся: соседние тайлы стоят рядом.
            for (int index = 1; index < street.size(); index++) {
                BlockPos was = street.get(index - 1);
                BlockPos now = street.get(index);
                if (Math.max(Math.abs(now.getX() - was.getX()),
                        Math.abs(now.getZ() - was.getZ())) > 1) {
                    context.throwGameTestException("Разрыв в улице между "
                            + was.toShortString() + " и " + now.toShortString());
                }
            }

            int paved = 0;
            for (BlockPos at : lawn) {
                if (world.getBlockState(at).isOf(Blocks.DIRT_PATH)) {
                    paved++;
                }
            }
            if (paved != street.size()) {
                context.throwGameTestException("Замощено тайлов " + paved + ", а в маршруте "
                        + street.size() + ": улица должна быть шириной в один");
            }
        } finally {
            clearStreet(world, house, housePlan, lawn);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Улица берёт только излишки материала.
     * <p>
     * Без этого правила дорожка молча съедала бы булыжник, отложенный
     * игроком на цоколь следующего дома, — и он не понял бы, куда девается
     * камень. Здесь гравия ровно на один тайл больше запаса: улица кладёт
     * одну плиту и переходит на бесплатную тропу.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "streets")
    public void streetTakesOnlySurplusMaterial(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(STREET_HALL);
        List<BlockPos> lawn = lawn(world, context);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, context.getAbsolutePos(STREET_HOUSE), HOUSE_TYPE,
                BlockRotation.NONE);

        try {
            raiseHouse(context, world, manager, colony, house, housePlan);

            ItemStack over = Warehouse.of(world, colony)
                    .add(new ItemStack(Items.GRAVEL, Roads.reserve() + 1));
            if (!over.isEmpty()) {
                context.throwGameTestException("Склад не принял гравий: " + over);
            }

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 24, Schedule.MORNING_WORK);

            List<BlockPos> street = streetOf(world, colony, house);
            BlockState first = world.getBlockState(street.get(0));
            if (!first.isOf(Blocks.GRAVEL)) {
                context.throwGameTestException("Излишек гравия не пошёл в мостовую: у порога "
                        + first.getBlock());
            }

            int left = Warehouse.of(world, colony).count(Items.GRAVEL);
            if (left != Roads.reserve()) {
                context.throwGameTestException("Запас тронут: гравия осталось " + left
                        + " вместо " + Roads.reserve());
            }

            for (int index = 1; index < street.size(); index++) {
                BlockState state = world.getBlockState(street.get(index));
                if (!state.isOf(Blocks.DIRT_PATH)) {
                    context.throwGameTestException("Материал кончился, а улица встала: на "
                            + street.get(index).toShortString() + " лежит " + state.getBlock());
                }
            }
        } finally {
            clearStreet(world, house, housePlan, lawn);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Чужого улица не трогает: ни дорожки игрока, ни грядки.
     * <p>
     * То же правило, по которому фермер не считает своими посадки игрока.
     * Мостить билдер вправе только натуральный грунт: увидел кварц или
     * пашню — обошёл и пошёл дальше, а не встал и не перекопал.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "streets")
    public void streetLeavesPlayerBlocksAndFieldsAlone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(STREET_HALL);
        List<BlockPos> lawn = lawn(world, context);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, context.getAbsolutePos(STREET_HOUSE), HOUSE_TYPE,
                BlockRotation.NONE);

        BlockPos mine;
        BlockPos field;

        try {
            raiseHouse(context, world, manager, colony, house, housePlan);

            // Прямо на будущей улице: своя дорожка игрока и его грядка.
            List<BlockPos> planned = streetOf(world, colony, house);
            if (planned.size() < 6) {
                context.throwGameTestException("Маршрут короче шести тайлов, некуда ставить чужое");
                return;
            }
            mine = planned.get(2);
            field = planned.get(4);
            world.setBlockState(mine, Blocks.QUARTZ_BLOCK.getDefaultState());
            world.setBlockState(field, Blocks.FARMLAND.getDefaultState());

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 24, Schedule.MORNING_WORK);

            if (!world.getBlockState(mine).isOf(Blocks.QUARTZ_BLOCK)) {
                context.throwGameTestException("Улица перекопала дорожку игрока: теперь там "
                        + world.getBlockState(mine).getBlock());
            }
            if (!world.getBlockState(field).isOf(Blocks.FARMLAND)) {
                context.throwGameTestException("Улица прошла по грядке: теперь там "
                        + world.getBlockState(field).getBlock());
            }

            // А вокруг чужого улица всё-таки легла: обошла, а не встала.
            for (BlockPos tile : streetOf(world, colony, house)) {
                if (tile.equals(mine) || tile.equals(field)) {
                    continue;
                }
                BlockState state = world.getBlockState(tile);
                if (!state.isOf(Blocks.DIRT_PATH)) {
                    context.throwGameTestException("Улица встала перед чужим блоком: на "
                            + tile.toShortString() + " лежит " + state.getBlock());
                }
            }
        } finally {
            clearStreet(world, house, housePlan, lawn);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

}
