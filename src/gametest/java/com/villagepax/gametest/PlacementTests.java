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
}
