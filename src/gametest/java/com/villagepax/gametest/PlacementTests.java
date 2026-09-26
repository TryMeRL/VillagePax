package com.villagepax.gametest;

import com.villagepax.screen.BuildOrders;
import com.villagepax.sim.Building;
import com.villagepax.sim.Ground;
import com.villagepax.sim.Raising;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.build.Access;
import com.villagepax.sim.build.Schematic;
import net.minecraft.block.Blocks;
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
