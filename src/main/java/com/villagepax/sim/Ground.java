package com.villagepax.sim;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Optional;

/**
 * Где здесь земля.
 * <p>
 * Написано по следам настоящей ошибки, из-за которой деревни <b>не
 * появлялись вовсе</b>. Поиск места считал высоту по генератору —
 * {@code WORLD_SURFACE_WG}, то есть по рельефу без деревьев, — а основание
 * деревни спрашивало у мира {@code WORLD_SURFACE}, куда входит листва.
 * В лесу это разные числа, а в джунглях они расходятся на два десятка
 * блоков: поиск звал игрока на поляну, а деревня искала опору на верхушке
 * дерева, не находила и молча отказывалась вставать. Игрок приходил
 * по указанным координатам и видел пустое место — так и сказал.
 * <p>
 * Правило теперь одно на весь мод: земля — это <b>первая сверху точка,
 * на которой можно стоять и строить</b>. Листва и брёвна пропускаются,
 * вода и лава землёй не считаются.
 */
public final class Ground {

    /**
     * Насколько глубоко под верхушку заглядывать.
     * <p>
     * Тридцати двух хватает на самое высокое джунглевое дерево; больше —
     * и поиск начал бы находить пол пещеры под козырьком, выдавая его
     * за поверхность.
     */
    private static final int DIG = 32;

    /** Насколько выше или ниже точки может лежать «место рядом». */
    private static final int NEAR_ENOUGH = 3;

    private Ground() {
    }

    /**
     * Дерево ли это. Верхушка ствола — опора твёрдая, но <b>не земля</b>:
     * первая версия поиска честно нашла на ней место под ратушу, и это
     * та же самая ошибка, только уже её. Листва не твёрдая и отсеивается
     * сама, а бревно пришлось назвать.
     */
    private static boolean isTree(ServerWorld world, BlockPos support) {
        BlockState state = world.getBlockState(support);
        return state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES);
    }

    /**
     * Место, где можно поставить здание: воздух под ногами игрока и
     * твёрдая опора под ним.
     * <p>
     * Пусто — значит в этой колонне строить нельзя: ствол дерева, вода,
     * обрыв. Это законный ответ, и звать его надо на соседних колоннах,
     * а не «примерно тут».
     */
    public static Optional<BlockPos> buildableAt(ServerWorld world, int x, int z) {
        if (!world.isChunkLoaded(new BlockPos(x, world.getSeaLevel(), z))) {
            // Спрашивать высоту в незагруженном чанке нельзя: это заставит
            // мир сгенерировать его здесь и сейчас.
            return Optional.empty();
        }

        int top = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        int floor = Math.max(world.getBottomY() + 1, top - DIG);

        for (int y = top; y >= floor; y--) {
            BlockPos spot = new BlockPos(x, y, z);
            if (ColonyFounder.isBuildable(world, spot) && !isTree(world, spot.down())) {
                return Optional.of(spot);
            }
        }
        return Optional.empty();
    }

    /**
     * Место, где может встать человек, — кольцами вокруг точки.
     * <p>
     * Одно на весь мод: этим ищут, где поставить телегу обоза и где
     * собраться отряду. Кольцами от близких к далёким, потому что
     * «рядом с ратушей» и значит рядом; и с ограничением по высоте,
     * потому что место на скале в двадцати блоках над деревней
     * формально годится, а на деле игрок туда не пойдёт.
     *
     * @param centre от чего мерить
     * @param from   с какого кольца начинать: ноль — сама точка
     * @param to     до какого искать
     * @return место или {@code null}, если рядом негде встать
     */
    public static BlockPos spotNear(ServerWorld world, BlockPos centre, int from, int to) {
        for (int radius = from; radius <= to; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos column = centre.add(dx, 0, dz);
                    BlockPos spot = buildableAt(world, column.getX(), column.getZ())
                            .orElse(null);
                    if (spot != null && Math.abs(spot.getY() - centre.getY()) <= NEAR_ENOUGH) {
                        return spot;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Высота земли в колонне — для сравнения уклона.
     * <p>
     * Отдаётся отдельно от {@link #buildableAt}, потому что углы следа
     * здания сравниваются между собой, и там неважно, можно ли строить
     * ровно в этой точке: важно, насколько она выше или ниже соседней.
     */
    public static Optional<Integer> levelAt(ServerWorld world, int x, int z) {
        return buildableAt(world, x, z).map(BlockPos::getY);
    }
}
