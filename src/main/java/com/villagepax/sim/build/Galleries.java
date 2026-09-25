package com.villagepax.sim.build;

import com.villagepax.core.building.BuildingTypes;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Галереи чертога и мосты в кронах: ход от двери каждого зала к порогу ратуши.
 * <p>
 * Жалоба заказчика: «у гномов их поселение просто зарыто в земле, и никуда
 * не могут выйти». Ворота ({@link Gate}) выводили наружу одну ратушу,
 * а улица к остальным залам шла так же, как на лугу, — прямой к середине
 * поселения. Под землёй это ломалось дважды. Прямая по диагонали — это
 * клетки, которые касаются друг друга только углами, и меж двух каменных
 * углов не проходит ни житель, ни игрок. А середина поселения — это
 * ратуша, и прямая упиралась в её стену, а не в дверь: на лугу стену
 * обходят по траве, в горе обходить её не по чему.
 * <p>
 * Поэтому здесь путь ищется, а не проводится: поиск в ширину по отметке
 * пола, шагами только вдоль сторон света, в обход чужих следов — от
 * порога зала до порога ратуши, откуда ворота уже ведут к небу.
 * <p>
 * У эльфов та же поломка наоборот: до дальних помостов мост ещё надо было
 * настлать, и житель дальнего дома висел над лесом. Поэтому тем же путём
 * эльфам сразу кладётся мост — деревня старше игрока.
 * <p>
 * И прорубается он сразу, как только зал размечен: гномы сперва ведут
 * штольню, потом рубят в её конце зал. Без хода билдеру не к чему
 * подойти, а жителю, которому дали там постель, — не дойти до неё.
 */
public final class Galleries {

    /** На сколько блоков поиск может обойти препятствие вбок от прямой. */
    private static final int DETOUR = 8;

    /** Через сколько клеток под сводом висит фонарь. */
    private static final int LAMP_EVERY = 6;

    private static final Direction[] WAYS = {Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST};

    private Galleries() {
    }

    /**
     * Клетки пола галереи от порога здания до порога ратуши.
     * <p>
     * Пусто — если здание и есть ратуша, если схемы или двери нет, или если
     * обойти чужие следы в пределах {@link #DETOUR} нельзя: тогда улица
     * останется прежней, прямой, — хуже, чем ход, но не хуже, чем было.
     */
    public static List<BlockPos> path(Settlement colony, Building building) {
        if (BuildingTypes.isTownHall(building.type())) {
            return List.of();
        }
        Building hall = colony.buildings().stream()
                .filter(one -> BuildingTypes.isTownHall(one.type()))
                .findFirst().orElse(null);
        BlockPos from = doorstep(building).orElse(null);
        BlockPos to = hall == null ? null : doorstep(hall).orElse(null);
        if (from == null || to == null) {
            return List.of();
        }
        int floor = from.getY() - 1;
        return search(new BlockPos(from.getX(), floor, from.getZ()),
                new BlockPos(to.getX(), floor, to.getZ()), footprints(colony));
    }

    /**
     * Прорубить галерею к зданию: пол, свод в рост и фонари под сводом.
     * <p>
     * Даром и без возврата, как и ворота: это штольня, по которой к залу
     * вообще можно подойти. Сундук и чужое добро на пути не трогаются.
     *
     * @return сколько клеток пути пройдено
     */
    public static int cut(ServerWorld world, Settlement colony, Building building) {
        Footing footing = Footing.of(colony);
        if (!footing.keepsOneLevel()) {
            return 0;
        }
        List<BlockPos> tiles = path(colony, building);
        // В горе под ногой камень, в кронах — доска моста того народа,
        // что его настилает: эльфы мостят берёзой.
        BlockState rock = footing == Footing.HOLD ? Blocks.STONE.getDefaultState()
                : deckOf(colony);
        for (int i = 0; i < tiles.size(); i++) {
            BlockPos tile = tiles.get(i);
            BlockState under = world.getBlockState(tile);
            if (!under.isSolidBlock(world, tile) && !under.hasBlockEntity()) {
                world.setBlockState(tile, rock, Block.NOTIFY_ALL);
            }
            for (int up = 1; up <= Hold.HEADROOM; up++) {
                BlockPos cell = tile.up(up);
                BlockState standing = world.getBlockState(cell);
                if (!standing.isAir() && !standing.hasBlockEntity()) {
                    world.setBlockState(cell, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
            if (i % LAMP_EVERY == LAMP_EVERY / 2
                    && world.getBlockState(tile.up(Hold.HEADROOM + 1)).isSolidBlock(world,
                    tile.up(Hold.HEADROOM + 1))) {
                world.setBlockState(tile.up(Hold.HEADROOM), Blocks.LANTERN.getDefaultState()
                        .with(LanternBlock.HANGING, true), Block.NOTIFY_ALL);
            }
        }
        return tiles.size();
    }

    /**
     * Прорубить штольни ко всем залам заново.
     * <p>
     * Не к одному новому: новый зал может встать поперёк старой штольни,
     * и тогда его стены её перекроют. Путь ищется в обход всех следов,
     * включая новый, поэтому пересчёт сам выводит старую штольню в обход.
     */
    public static void cutAll(ServerWorld world, Settlement colony) {
        if (!Footing.of(colony).keepsOneLevel()) {
            return;
        }
        for (Building building : List.copyOf(colony.buildings())) {
            cut(world, colony, building);
        }
    }

    /** Первая сплошная плита из улиц народа, иначе берёзовая доска. */
    private static BlockState deckOf(Settlement colony) {
        com.villagepax.core.culture.Culture culture =
                com.villagepax.core.culture.CultureManager.get(colony.culture());
        if (culture != null) {
            for (net.minecraft.util.Identifier id : culture.road()) {
                Block block = net.minecraft.registry.Registries.BLOCK.get(id);
                if (block.getDefaultState().isFullCube(net.minecraft.world.EmptyBlockView.INSTANCE,
                        BlockPos.ORIGIN)) {
                    return block.getDefaultState();
                }
            }
        }
        return Blocks.BIRCH_PLANKS.getDefaultState();
    }

    /** Клетка перед дверью здания, снаружи, — там, где начинается улица. */
    static Optional<BlockPos> doorstep(Building building) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }
        return Access.entrances(building, schematic).stream().findFirst()
                .map(door -> door.offset(Access.awayFrom(building, schematic, door)));
    }

    /**
     * Поиск в ширину по клеткам пола. Соседи перебираются с продолжения
     * прежнего шага: из равных по длине путей берётся тот, что меньше
     * виляет, — штольня выходит прямыми пролётами, а не лесенкой.
     */
    static List<BlockPos> search(BlockPos from, BlockPos to, List<int[]> blocked) {
        int minX = Math.min(from.getX(), to.getX()) - DETOUR;
        int maxX = Math.max(from.getX(), to.getX()) + DETOUR;
        int minZ = Math.min(from.getZ(), to.getZ()) - DETOUR;
        int maxZ = Math.max(from.getZ(), to.getZ()) + DETOUR;

        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Direction> heading = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        cameFrom.put(from, from);
        queue.add(from);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            if (at.equals(to)) {
                List<BlockPos> tiles = new ArrayList<>();
                for (BlockPos step = to; !step.equals(from); step = cameFrom.get(step)) {
                    tiles.add(step);
                }
                tiles.add(from);
                Collections.reverse(tiles);
                return tiles;
            }
            Direction last = heading.get(at);
            for (Direction way : order(last)) {
                BlockPos next = at.offset(way);
                if (next.getX() < minX || next.getX() > maxX || next.getZ() < minZ
                        || next.getZ() > maxZ || cameFrom.containsKey(next)
                        || (!next.equals(to) && inside(blocked, next))) {
                    continue;
                }
                cameFrom.put(next, at);
                heading.put(next, way);
                queue.add(next);
            }
        }
        return List.of();
    }

    private static List<Direction> order(Direction last) {
        List<Direction> ways = new ArrayList<>(List.of(WAYS));
        if (last != null) {
            ways.remove(last);
            ways.add(0, last);
        }
        return ways;
    }

    private static List<int[]> footprints(Settlement colony) {
        List<int[]> boxes = new ArrayList<>();
        for (Building building : colony.buildings()) {
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
            BlockPos anchor = building.anchor();
            boxes.add(new int[]{anchor.getX(), anchor.getZ(),
                    anchor.getX() + size.getX() - 1, anchor.getZ() + size.getZ() - 1});
        }
        return boxes;
    }

    private static boolean inside(List<int[]> boxes, BlockPos at) {
        for (int[] box : boxes) {
            if (at.getX() >= box[0] && at.getX() <= box[2]
                    && at.getZ() >= box[1] && at.getZ() <= box[3]) {
                return true;
            }
        }
        return false;
    }
}
