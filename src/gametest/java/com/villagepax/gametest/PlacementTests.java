package com.villagepax.gametest;

import com.villagepax.screen.BuildOrders;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.Ground;
import com.villagepax.sim.Raising;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import com.villagepax.sim.build.Access;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Roads;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Постановка зданий: следы, пол, откос и улицы.
 * <p>
 * Написано по сохранениям заказчика после жалобы «куча лестниц и часто
 * просто перекрывает свои же постройки». В сохранениях нашлось всё
 * сразу: второй ларёк майя стоял на крыше первого, дома пони висели над
 * склоном с лесенкой в воздухе, поле норманнов — на земляной тумбе.
 */
public class PlacementTests extends GameTestSupport {

    /**
     * Здание не встаёт на крышу соседа и не жмётся к нему ни выше, ни ниже.
     * <p>
     * Найдено в «Новом мире64»: у майя второй ларёк размечен ровно над
     * первым, на его соломенной кровле. Проверка следов спрашивала
     * пересечение и по высоте, а крыша «выше» ларька — значит, не мешает.
     * Билдер туда так и не дошёл, и деревня встала навсегда: пока стройка
     * не кончена, новую она не начинает. Тот же вопрос по высоте снимал
     * и зазор: в «Новом мире666» ферма пони встала в блоке от ратуши,
     * потому что лежала на четыре блока ниже.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "placement")
    public void noBuildingIsStackedOnAnothersRoof(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farm = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 2, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);
            Vec3i size = farm.size();

            BlockPos roof = anchor.up(size.getY());
            if (!(BuildOrders.check(colony, HOUSE_SCHEMATIC, roof, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Overlaps)) {
                context.throwGameTestException("Дом размечается на крыше фермы: "
                        + roof.toShortString());
            }

            BlockPos besideBelow = anchor.add(size.getX() + 1, -10, 0);
            if (!(BuildOrders.check(colony, HOUSE_SCHEMATIC, besideBelow, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Overlaps)) {
                context.throwGameTestException("Дом встаёт в блоке от фермы, раз он на десять "
                        + "ниже: зазор между зданиями разной высоты не действует");
            }

            BlockPos clear = anchor.add(size.getX() + BuildOrders.GAP, -10, 0);
            BuildOrders.Result far = BuildOrders.check(colony, HOUSE_SCHEMATIC, clear,
                    BlockRotation.NONE);
            if (!(far instanceof BuildOrders.Result.Placed)) {
                context.throwGameTestException("Место за зазором отвергнуто: " + far);
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Деревня не строит на своём колодце и фонарях.
     * <p>
     * Колодец встаёт в шести–одиннадцати блоках от ратуши — ровно в том
     * кольце, где разметка ищет место первому же новому дому, — а следы
     * разметка сравнивала только со следами. В первом прогоне этой
     * проверки дом лёг прямо на булыжник сруба.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "wellside", tickLimit = 200)
    public void aVillageNeverBuildsOverItsWell(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -30; x <= 30; x++) {
                for (int z = -30; z <= 30; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }
            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала на ровном лугу");
                return;
            }

            for (int more = 0; more < 5; more++) {
                Raising.placeNear(world, manager, village, HOUSE_SCHEMATIC);
            }

            for (Building building : village.buildings()) {
                Schematic plan = SchematicLoader.get(BuildJob.schematicId(building)).orElseThrow();
                Vec3i size = BuildSite.rotatedSize(plan.size(), building.rotation());
                BlockPos a = building.anchor();
                for (BlockPos at : manager.decorOf(village.id())) {
                    BlockState decor = world.getBlockState(at);
                    if (decor.isReplaceable() || decor.isIn(BlockTags.FLOWERS)) {
                        continue;
                    }
                    if (at.getX() >= a.getX() - 1 && at.getX() <= a.getX() + size.getX()
                            && at.getZ() >= a.getZ() - 1 && at.getZ() <= a.getZ() + size.getZ()) {
                        context.throwGameTestException(building.type() + " размечен на убранстве: "
                                + decor.getBlock() + " на " + at.toShortString());
                    }
                }
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Новый дом не ложится поперёк мостовой соседа.
     * <p>
     * Луг здесь — узкая полоса вдоль улицы дальнего дома, и любой след
     * на ней накрыл бы мостовую: либо разметка находит место в стороне,
     * либо честно отказывает. Прежде мостовая была для неё травой, и дом
     * перегораживал улицу — та обрывалась у его стены.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "streetside", tickLimit = 200)
    public void aNewHouseNeverCutsAPavedStreet(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 9, 16));
        List<BlockPos> strip = new ArrayList<>();
        Settlement colony = null;

        try {
            for (int x = 1; x <= 31; x++) {
                for (int z = 10; z <= 22; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    strip.add(at);
                }
            }
            colony = colonyWithBuilder(world, manager, hall);
            // Дальний дом дверью к ратуше: его улица идёт вдоль всей полосы.
            Building far = plan(colony, context.getAbsolutePos(new BlockPos(23, 9, 13)),
                    HOUSE_TYPE, BlockRotation.COUNTERCLOCKWISE_90);
            List<BlockPos> street = Roads.route(world, colony, far);
            if (street.size() < 12) {
                context.throwGameTestException("Улица дальнего дома короче полосы: "
                        + street.size() + " тайлов");
                return;
            }
            for (BlockPos tile : street) {
                world.setBlockState(tile, Blocks.GRAVEL.getDefaultState());
            }

            Building site = Raising.placeNear(world, manager, colony, HOUSE_SCHEMATIC,
                    Raising.CLOSE_RINGS).orElse(null);
            if (site != null) {
                Vec3i size = BuildSite.rotatedSize(house.size(), site.rotation());
                BlockPos a = site.anchor();
                for (BlockPos tile : street) {
                    if (tile.getX() >= a.getX() && tile.getX() < a.getX() + size.getX()
                            && tile.getZ() >= a.getZ() && tile.getZ() < a.getZ() + size.getZ()) {
                        context.throwGameTestException("Дом размечен поперёк мостовой: тайл "
                                + tile.toShortString() + " под следом " + a.toShortString());
                    }
                }
            }
        } finally {
            if (colony != null) {
                manager.remove(colony.id());
            }
            for (BlockPos at : strip) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Высота склона в проверке откоса: блок через две клетки, вверх на запад. */
    private static int slopeTop(int x) {
        return 6 + Math.floorDiv(24 - x, 2);
    }

    /**
     * Готовое здание на склоне окружено откосом, а не отвесной стенкой.
     * <p>
     * Прежде площадка равнялась строго под следом: под домом досыпано,
     * над ним срыто, а в шаге за стеной — прежний склон. Вышло то, что
     * заказчик видел в своих деревнях: поле на земляной тумбе с отвесными
     * боками, дом в яме, и крыльцо, спускающееся по воздуху. Ванильные
     * деревни и Millénaire решают это одинаково — землю вокруг здания
     * ровняют к площадке уступом, — и мод теперь тоже.
     * <p>
     * Чужое в кольце откоса стоит нарочно: сундук, забор игрока, вода
     * и ствол дерева. Откос обязан обойти их, а не срыть.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "skirt", tickLimit = 300)
    public void aBuildingOnASlopeGetsAGentleSkirt(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        List<BlockPos> ground = new ArrayList<>();
        BlockPos hall = context.getAbsolutePos(new BlockPos(20, slopeTop(20) + 1, 20));
        BlockPos chest = context.getAbsolutePos(new BlockPos(10, slopeTop(10) + 1, 15));
        BlockPos fence = context.getAbsolutePos(new BlockPos(12, slopeTop(12) + 1, 19));
        BlockPos water = context.getAbsolutePos(new BlockPos(19, slopeTop(19), 15));
        BlockPos trunk = context.getAbsolutePos(new BlockPos(11, slopeTop(11) + 1, 17));
        Settlement colony = null;
        Building house = null;

        try {
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    for (int y = slopeTop(x) - 2; y <= slopeTop(x); y++) {
                        BlockPos at = context.getAbsolutePos(new BlockPos(x, y, z));
                        world.setBlockState(at, y == slopeTop(x)
                                ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.DIRT.getDefaultState());
                        ground.add(at);
                    }
                }
            }
            colony = colonyWithBuilder(world, manager, hall);
            // Пол посередине склона: запад срывается на два блока, восток
            // досыпается на блок — ровно то, что выбрала бы разметка.
            BlockPos anchor = context.getAbsolutePos(new BlockPos(12, 11, 12));
            house = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

            world.setBlockState(chest, Blocks.CHEST.getDefaultState());
            world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState());
            world.setBlockState(water, Blocks.WATER.getDefaultState());
            for (int up = 0; up < 3; up++) {
                world.setBlockState(trunk.up(up), Blocks.OAK_LOG.getDefaultState());
            }

            stockFor(world, colony, housePlan);
            stockFor(world, colony, housePlan);
            BuildJob.Outcome built = BuildJob.advance(world, manager, colony.id(), house.id(), 20_000);
            if (built != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом на склоне не достроился: " + built);
                return;
            }

            if (!world.getBlockState(chest).isOf(Blocks.CHEST)
                    || world.getBlockState(chest.down()).isAir()
                    || !world.getBlockState(fence).isIn(BlockTags.FENCES)
                    || world.getBlockState(fence.down()).isAir()
                    || !world.getBlockState(water).isOf(Blocks.WATER)
                    || !world.getBlockState(trunk).isIn(BlockTags.LOGS)
                    || world.getBlockState(trunk.down()).isAir()) {
                context.throwGameTestException("Откос тронул чужое: сундук "
                        + world.getBlockState(chest).getBlock() + ", забор "
                        + world.getBlockState(fence).getBlock() + ", вода "
                        + world.getBlockState(water).getBlock() + ", ствол "
                        + world.getBlockState(trunk).getBlock() + " на "
                        + world.getBlockState(trunk.down()).getBlock());
            }

            Vec3i size = BuildSite.rotatedSize(housePlan.size(), BlockRotation.NONE);
            int pad = anchor.getY() - 1;
            List<BlockPos> doors = Access.entrances(house, housePlan);
            List<BlockPos> foreign = List.of(chest, fence, water, trunk);
            // Четыре стороны: край следа, клетка за стеной и ещё одна.
            int[][] sides = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] side : sides) {
                int along = side[0] == 0 ? size.getX() : size.getZ();
                for (int i = 0; i < along; i++) {
                    int edgeX = side[0] < 0 ? 0 : side[0] > 0 ? size.getX() - 1 : i;
                    int edgeZ = side[1] < 0 ? 0 : side[1] > 0 ? size.getZ() - 1 : i;
                    BlockPos edge = anchor.add(edgeX, 0, edgeZ);
                    BlockPos first = edge.add(side[0], 0, side[1]);
                    BlockPos second = first.add(side[0], 0, side[1]);
                    boolean door = doors.stream().anyMatch(d -> d.getX() == edge.getX()
                            && d.getZ() == edge.getZ());
                    boolean skip = door || foreign.stream().anyMatch(f ->
                            (f.getX() == first.getX() && f.getZ() == first.getZ())
                                    || (f.getX() == second.getX() && f.getZ() == second.getZ()));
                    if (skip) {
                        continue;
                    }
                    int one = earthTop(world, first, pad);
                    int two = earthTop(world, second, pad);
                    if (Math.abs(one - pad) > 1 || Math.abs(two - one) > 1) {
                        context.throwGameTestException("У стены " + first.toShortString()
                                + " перепад: площадка " + pad + ", за стеной " + one
                                + ", дальше " + two + " — отвесная стенка вместо откоса");
                    }
                }
            }

            for (BlockPos at : BlockPos.iterate(anchor.add(-4, -6, -4),
                    anchor.add(size.getX() + 3, 6, size.getZ() + 3))) {
                if (BuildSite.covers(anchor, housePlan.size(), BlockRotation.NONE, at)) {
                    continue;
                }
                if (world.getBlockState(at).getBlock() instanceof net.minecraft.block.StairsBlock
                        && world.getBlockState(at.down()).isAir()) {
                    context.throwGameTestException("Ступень висит над воздухом: "
                            + at.toShortString());
                }
            }

            for (BlockPos door : doors) {
                String trouble = descentTrouble(world, house, housePlan, door);
                if (trouble != null) {
                    context.throwGameTestException("Со склона в дом не войти: " + trouble);
                }
            }
        } finally {
            if (house != null) {
                demolish(world, house, housePlan);
                clearSkirt(world, house, housePlan);
            }
            for (BlockPos at : List.of(chest, fence, water)) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            for (int up = 0; up < 3; up++) {
                world.setBlockState(trunk.up(up), Blocks.AIR.getDefaultState());
            }
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            if (colony != null) {
                manager.remove(colony.id());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Верх земли в колонне около площадки: первый сверху полный блок. */
    private static int earthTop(ServerWorld world, BlockPos column, int pad) {
        for (int y = pad + 6; y >= pad - 6; y--) {
            BlockPos at = column.withY(y);
            if (world.getBlockState(at).isSolidBlock(world, at)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Дом на склоне открывается дверью к земле, а не над обрывом.
     * <p>
     * Прежде пол брался по высоте угла-якоря, и на склоне, поднимающемся
     * от ратуши, это был верхний угол: дом вставал на верхнюю отметку,
     * дверь к площади смотрела с высоты в три блока, и крыльцо спускалось
     * ступенями по воздуху. Ровно так в «Новом мире888» стояла изба пони.
     * Проверяется сама разметка — до откоса и крыльца, которые потом
     * прячут ошибку, — потому что ошибка именно в ней.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "slope", tickLimit = 200)
    public void aHouseOnASlopeOpensAtGroundLevel(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, HOUSE_SCHEMATIC);

        List<BlockPos> slope = new ArrayList<>();
        BlockPos hall = null;
        Settlement colony = null;

        try {
            // Склон поднимается на запад на блок через три клетки — полого,
            // по нему ходят. Ратуша внизу, у восточного края.
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 4 + (31 - x) / 3, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    slope.add(at);
                }
            }
            hall = context.getAbsolutePos(new BlockPos(26, 4 + (31 - 26) / 3 + 1, 16));
            colony = colonyWithBuilder(world, manager, hall);

            Building site = Raising.placeNear(world, manager, colony, HOUSE_SCHEMATIC,
                    Raising.CLOSE_RINGS).orElse(null);
            if (site == null) {
                context.throwGameTestException("На пологом склоне дому не нашлось места");
                return;
            }

            for (BlockPos door : Access.entrances(site, house)) {
                BlockPos front = door.offset(Access.awayFrom(site, house, door));
                int ground = Ground.levelAt(world, front.getX(), front.getZ()).orElse(Integer.MIN_VALUE);
                int step = door.getY() - ground;
                if (step < 0 || step > 1) {
                    context.throwGameTestException("Порог двери на " + door.toShortString()
                            + " отстоит от земли перед ней на " + step
                            + " (якорь " + site.anchor().toShortString() + ", поворот "
                            + site.rotation() + "): войти без лестницы нельзя");
                }
            }
        } finally {
            if (colony != null) {
                manager.remove(colony.id());
            }
            for (BlockPos at : slope) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            if (hall != null) {
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
        }

        context.complete();
    }

    /** Холмы проверки «деревня на холмах»: сумма трёх волн, перепад около десяти блоков. */
    private static int hill(int x, int z) {
        return 9 + (int) Math.round(2.2 * Math.sin(x * 0.25) + 1.8 * Math.cos(z * 0.21)
                + 1.2 * Math.sin((x + z) * 0.13));
    }

    /**
     * Деревня на холмах: всё сразу и на земле, где ошибки живут.
     * <p>
     * Каждое правило постановки проверено по отдельности на своём склоне,
     * а ломается обычно на стыке: откос второго дома подрезает вход
     * первого, улица третьего упирается в сруб колодца. Здесь деревня
     * встаёт на волнистой земле и дорастает ещё на четыре дома — и после
     * этого у неё обязаны быть зазоры между всеми следами, ни одной
     * ступени над воздухом, проходимый вход у каждого здания и улицы,
     * которые идут сторонами клеток и не прыгают больше чем на блок.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hills", tickLimit = 400)
    public void aVillageOnRollingHillsStaysTidy(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        List<BlockPos> land = new ArrayList<>();
        BlockPos centre = context.getAbsolutePos(new BlockPos(0, hill(0, 0) + 1, 0));
        Settlement village = null;

        try {
            for (int x = -26; x <= 26; x++) {
                for (int z = -26; z <= 26; z++) {
                    int top = hill(x, z);
                    for (int y = top - 2; y <= top; y++) {
                        BlockPos at = context.getAbsolutePos(new BlockPos(x, y, z));
                        world.setBlockState(at, y == top
                                ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.DIRT.getDefaultState());
                        land.add(at);
                    }
                }
            }
            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала на холмах: помеха "
                        + whoBlocks(manager, centre));
                return;
            }
            for (int more = 0; more < 4; more++) {
                Raising.raise(world, manager, village, HOUSE_TYPE);
            }

            List<Building> standing = village.buildings().stream()
                    .filter(b -> !com.villagepax.core.building.BuildingTypes.isTownHall(b.type()))
                    .toList();
            if (standing.size() < 4) {
                context.throwGameTestException("На холмах встало только " + standing.size()
                        + " зданий кроме ратуши");
            }

            List<Building> all = List.copyOf(village.buildings());
            for (int i = 0; i < all.size(); i++) {
                for (int j = i + 1; j < all.size(); j++) {
                    Building a = all.get(i);
                    Building b = all.get(j);
                    Vec3i sa = BuildSite.rotatedSize(SchematicLoader.get(BuildJob.schematicId(a))
                            .orElseThrow().size(), a.rotation());
                    Vec3i sb = BuildSite.rotatedSize(SchematicLoader.get(BuildJob.schematicId(b))
                            .orElseThrow().size(), b.rotation());
                    int gapX = Math.max(b.anchor().getX() - (a.anchor().getX() + sa.getX()),
                            a.anchor().getX() - (b.anchor().getX() + sb.getX()));
                    int gapZ = Math.max(b.anchor().getZ() - (a.anchor().getZ() + sa.getZ()),
                            a.anchor().getZ() - (b.anchor().getZ() + sb.getZ()));
                    if (Math.max(gapX, gapZ) < BuildOrders.GAP) {
                        context.throwGameTestException(a.type() + " и " + b.type()
                                + " ближе зазора: " + a.anchor().toShortString() + " и "
                                + b.anchor().toShortString());
                    }
                }
            }

            for (Building building : standing) {
                Schematic plan = SchematicLoader.get(BuildJob.schematicId(building)).orElseThrow();
                for (BlockPos door : Access.entrances(building, plan)) {
                    String trouble = descentTrouble(world, building, plan, door);
                    if (trouble != null) {
                        StringBuilder front = new StringBuilder();
                        net.minecraft.util.math.Direction out = Access.awayFrom(building, plan, door);
                        for (int step = 1; step <= 4; step++) {
                            BlockPos column = door.offset(out, step);
                            String owner = all.stream().filter(b -> b != building && BuildSite.covers(
                                            b.anchor(), SchematicLoader.get(BuildJob.schematicId(b))
                                                    .orElseThrow().size(), b.rotation(),
                                            column.withY(b.anchor().getY())))
                                    .map(b -> b.type().getPath()).findFirst().orElse("-");
                            front.append(" | шаг").append(step).append(" земля ")
                                    .append(earthTop(world, column, door.getY())).append(" след ")
                                    .append(owner);
                        }
                        context.throwGameTestException("В " + building.type() + " на "
                                + building.anchor().toShortString() + " не войти: " + trouble + front);
                    }
                }
                List<BlockPos> street = Roads.route(world, village, building);
                for (int i = 1; i < street.size(); i++) {
                    BlockPos last = street.get(i - 1);
                    BlockPos tile = street.get(i);
                    if (Math.abs(tile.getX() - last.getX()) + Math.abs(tile.getZ() - last.getZ()) != 1
                            || Math.abs(tile.getY() - last.getY()) > 1) {
                        context.throwGameTestException("Улица от " + building.type() + " рвётся между "
                                + last.toShortString() + " и " + tile.toShortString());
                    }
                }
            }

            for (BlockPos at : BlockPos.iterate(centre.add(-26, -12, -26), centre.add(26, 14, 26))) {
                if (!(world.getBlockState(at).getBlock() instanceof net.minecraft.block.StairsBlock)
                        || !world.getBlockState(at.down()).isAir()) {
                    continue;
                }
                boolean inside = all.stream().anyMatch(b -> BuildSite.covers(b.anchor(),
                        SchematicLoader.get(BuildJob.schematicId(b)).orElseThrow().size(),
                        b.rotation(), at));
                if (!inside) {
                    context.throwGameTestException("Ступень висит над воздухом: " + at.toShortString());
                }
            }
        } finally {
            if (village != null) {
                for (Building building : village.buildings()) {
                    SchematicLoader.get(BuildJob.schematicId(building))
                            .ifPresent(plan -> clearSkirt(world, building, plan));
                }
            }
            cleanUpVillage(world, manager, village, centre, land);
        }

        context.complete();
    }
}
