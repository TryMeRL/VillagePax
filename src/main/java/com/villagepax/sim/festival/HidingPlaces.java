package com.villagepax.sim.festival;

import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Footing;
import com.villagepax.sim.build.Roads;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Где спрятать вещицы поиска.
 * <p>
 * На земле вокруг ярмарки — там, где встанет человек: под ногами твёрдое
 * и не забор, ноги в воздухе или в низкой траве, голова в воздухе.
 * Не в домах и не на улице: вещица в чужой спальне — это обыск, а не
 * праздник, а на мостовой её найдёт первый прохожий. Ярмарка — тоже здание,
 * и её след закрыт вместе с загоном и стрелищем.
 * <p>
 * У народов на земле — под открытым небом. Гномы и эльфы живут на одном
 * уровне — в зале и на помосте, — и у них вещицы лежат на полу не дальше
 * трёх блоков по высоте от пола ярмарки.
 * <p>
 * Сперва «спрятанные» места — у стены, забора, цветка, куста или в траве, —
 * потом остальные: вещица посреди голой поляны видна с ярмарки, и искать
 * было бы нечего. Друг от друга не ближе четырёх блоков: десять вещиц
 * в одной клумбе — одна находка, а не десять.
 */
public final class HidingPlaces {

    /** Как далеко от сердца прячут. */
    public static final int RADIUS = 24;

    /** Не ближе этого друг к другу. */
    public static final int APART = 4;

    /** У народов одного уровня: на сколько блоков пол может быть выше или ниже пола ярмарки. */
    private static final int LEVEL_REACH = 3;

    private HidingPlaces() {
    }

    /** До {@code count} мест, спрятанные вперёд; порядок — от случая. */
    public static List<BlockPos> find(ServerWorld world, Settlement settlement, Fair fair, int count,
                                      Random random) {
        return find(world, settlement, fair.heart(), fair.standingY(), count, random);
    }

    /**
     * То же вокруг любой точки: прятки прячут детей там же, где ярмарка —
     * вещицы, и по тем же правилам — не в домах, не на улице, не кучкой.
     *
     * @param standingY высота пола у народов одного уровня: от неё ищут пол
     */
    public static List<BlockPos> find(ServerWorld world, Settlement settlement, BlockPos heart,
                                      int standingY, int count, Random random) {
        boolean oneLevel = Footing.of(settlement).keepsOneLevel();
        Set<Long> built = footprints(settlement);
        Set<Block> street = Roads.streetBlocks(settlement);
        List<BlockPos> hidden = new ArrayList<>();
        List<BlockPos> open = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) {
                    continue;
                }
                int x = heart.getX() + dx;
                int z = heart.getZ() + dz;
                if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(x),
                        ChunkSectionPos.getSectionCoord(z))
                        || built.contains(BlockPos.asLong(x, 0, z))) {
                    continue;
                }
                BlockPos feet = oneLevel ? floorSpot(world, x, z, standingY)
                        : surfaceSpot(world, x, z);
                if (feet == null || street.contains(world.getBlockState(feet.down()).getBlock())) {
                    continue;
                }
                (hidden(world, feet) ? hidden : open).add(feet);
            }
        }
        shuffle(hidden, random);
        shuffle(open, random);
        List<BlockPos> chosen = new ArrayList<>();
        pick(hidden, chosen, count);
        pick(open, chosen, count);
        return chosen;
    }

    /**
     * Где встать в этой колонне: у народа на земле — верх под небом,
     * у народа одного уровня — пол около пола ярмарки. Следы и улицы
     * не спрашиваются: так ищут, куда пройтись, а не где спрятать.
     */
    static BlockPos spotAt(ServerWorld world, Settlement settlement, Fair fair, int x, int z) {
        if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z))) {
            return null;
        }
        return Footing.of(settlement).keepsOneLevel() ? floorSpot(world, x, z, fair.standingY())
                : surfaceSpot(world, x, z);
    }

    /** Стоячее ли место: под ногами твёрдое и не забор, ноги в воздухе или низкой траве, голова в воздухе. */
    public static boolean standable(ServerWorld world, BlockPos feet) {
        BlockPos below = feet.down();
        BlockState ground = world.getBlockState(below);
        if (!ground.isSolidBlock(world, below) || ground.isIn(BlockTags.FENCES)
                || ground.isIn(BlockTags.WALLS)) {
            return false;
        }
        BlockState at = world.getBlockState(feet);
        return (at.isAir() || isLowGrass(at)) && world.getBlockState(feet.up()).isAir();
    }

    /** Спрятанное ли место: вещица в траве, или рядом стена, забор, цветок, куст. */
    static boolean hidden(ServerWorld world, BlockPos feet) {
        if (isLowGrass(world.getBlockState(feet))) {
            return true;
        }
        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos next = feet.offset(side);
            BlockState state = world.getBlockState(next);
            if (state.isSolidBlock(world, next) || state.isIn(BlockTags.FENCES)
                    || state.isIn(BlockTags.WALLS) || state.isIn(BlockTags.FLOWERS)
                    || state.isIn(BlockTags.LEAVES) || state.isOf(Blocks.TALL_GRASS)
                    || state.isOf(Blocks.LARGE_FERN) || state.isOf(Blocks.SWEET_BERRY_BUSH)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLowGrass(BlockState state) {
        return state.isOf(Blocks.GRASS) || state.isOf(Blocks.FERN);
    }

    /** У народа на земле: верх колонны под открытым небом — листва над головой не в счёт. */
    private static BlockPos surfaceSpot(ServerWorld world, int x, int z) {
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) > top) {
            return null;
        }
        BlockPos feet = new BlockPos(x, top, z);
        return standable(world, feet) ? feet : null;
    }

    /** У народа одного уровня: пол около пола ярмарки, ближний по высоте. */
    private static BlockPos floorSpot(ServerWorld world, int x, int z, int floor) {
        for (int step = 0; step <= 2 * LEVEL_REACH; step++) {
            int dy = (step + 1) / 2 * (step % 2 == 0 ? -1 : 1);
            BlockPos feet = new BlockPos(x, floor + dy, z);
            if (standable(world, feet)) {
                return feet;
            }
        }
        return null;
    }

    /** Колонны следов всех зданий поселения, ярмарки тоже. */
    private static Set<Long> footprints(Settlement settlement) {
        Set<Long> columns = new HashSet<>();
        for (Building building : settlement.buildings()) {
            Schematic plan = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (plan == null) {
                continue;
            }
            Vec3i size = BuildSite.rotatedSize(plan.size(), building.rotation());
            for (int dx = 0; dx < size.getX(); dx++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    columns.add(BlockPos.asLong(building.anchor().getX() + dx, 0,
                            building.anchor().getZ() + dz));
                }
            }
        }
        return columns;
    }

    private static void pick(List<BlockPos> from, List<BlockPos> chosen, int count) {
        for (BlockPos spot : from) {
            if (chosen.size() >= count) {
                return;
            }
            if (chosen.stream().allMatch(other -> horizontal(other, spot) >= APART * APART)) {
                chosen.add(spot);
            }
        }
    }

    private static int horizontal(BlockPos one, BlockPos other) {
        int dx = one.getX() - other.getX();
        int dz = one.getZ() - other.getZ();
        return dx * dx + dz * dz;
    }

    private static <T> void shuffle(List<T> list, Random random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T swap = list.get(i);
            list.set(i, list.get(j));
            list.set(j, swap);
        }
    }
}
