package com.villagepax.sim.build;

import com.villagepax.sim.Ground;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Высоты земли, спрошенные у мира однажды на весь поиск места.
 * <p>
 * Поиск перебирает тысячи мест в четырёх поворотах, и соседние следы делят
 * почти все клетки: без памяти одна и та же колонна спрашивалась бы у мира
 * десятки раз, а каждый вопрос — проход по колонне сверху вниз. Память
 * живёт ровно один поиск: мир меняется, и вчерашняя высота — не сегодняшняя.
 */
public final class Heights {

    /** Колонну ещё не спрашивали. Отлично от {@link FloorChoice#NO_GROUND}. */
    private static final int UNKNOWN = Integer.MIN_VALUE + 1;

    private final ServerWorld world;
    private final Long2IntOpenHashMap known = new Long2IntOpenHashMap();

    public Heights(ServerWorld world) {
        this.world = world;
        known.defaultReturnValue(UNKNOWN);
    }

    /**
     * Высота хода в колонне — первая пустая клетка над землёй, как у якоря,
     * или {@link FloorChoice#NO_GROUND}, если строить здесь не на чем.
     */
    public int at(int x, int z) {
        long key = BlockPos.asLong(x, 0, z);
        int height = known.get(key);
        if (height == UNKNOWN) {
            height = Ground.levelAt(world, x, z).orElse(FloorChoice.NO_GROUND);
            known.put(key, height);
        }
        return height;
    }
}
