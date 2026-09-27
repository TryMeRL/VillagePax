package com.villagepax.sim.build;

import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Откос: земля вокруг площадки ровняется к ней уступом в блок на шаг.
 * <p>
 * Жалоба заказчика: «куча лестниц». В сохранениях за ней стояло одно
 * и то же: площадка равнялась строго под следом ({@link Terrace}), а в шаге
 * за стеной лежал прежний склон. Поле вставало на земляную тумбу с
 * отвесными боками, дом — в яму, и крыльцо спускалось по воздуху
 * ступенями к земле, до которой было три блока.
 * <p>
 * Ванильные деревни решают это «бородой» ({@code terrain_adaptation}):
 * под домом наплывает земля, над ним срезается холм, и край сходит на нет.
 * У Millénaire то же самое названо прямо — {@code around}: сколько блоков
 * вокруг здания ровняется перед стройкой. Здесь правило одно и простое:
 * клетка в {@code d} шагах от следа держится в полосе
 * {@code [пол − d, пол + d]} и не дальше блока от соседа на шаг ближе
 * к следу. Выше грунт срывается, ниже — досыпается тем, что лежит в этой
 * колонне. Выходит уступ, по которому
 * ходят и житель, и игрок, а отвесных стенок и висячих ступеней не остаётся.
 * <p>
 * Перед дверью ровняется проход на {@link #APPROACH} шагов: сразу за порогом
 * земля не ниже площадки — порог стоит на блок выше пола, и уступ ниже
 * опять не переступить, — а дальше по шагу за клетку, до улицы.
 *
 * <h2>Что откос не трогает</h2>
 * Чужое — никогда: колонну, в которой сверху лежит не природный грунт,
 * откос обходит целиком. Сундук, забор, тропа, мостовая, грядка, вода,
 * дерево — всё это «не земля». Ствол особенно: срой грунт под ним — и
 * дерево повиснет. Не трогаются и чужие следы, убранство и улицы деревни.
 * Обрыв тоже не склон: глубже {@link #MAX_CHANGE} на клетку откос
 * не режет и выше не сыплет.
 *
 * <h2>Даром и один раз</h2>
 * Тем же доводом, что и площадка: это не стройка, а земляные работы,
 * и колония не покупает собственный грунт. Зовётся вместе с площадкой,
 * на первом шаге плана.
 */
public final class Grading {

    /**
     * На сколько клеток вокруг следа ровняется земля.
     * <p>
     * Столько же, сколько зазор между зданиями: откос одного дома
     * доходит до следа соседа и там кончается.
     */
    public static final int MARGIN = 3;

    /** Глубже этого на клетку не срывают и выше не сыплют: дальше — обрыв. */
    public static final int MAX_CHANGE = 4;

    /**
     * Сколько блоков грунта откос трогает за одно здание.
     * <p>
     * Не баланс, а сервер: всё ставится в один тик, и стройка, от которой
     * игра замирает, читается как зависание.
     */
    private static final int BUDGET = 900;

    private Grading() {
    }

    /**
     * На сколько шагов от двери ровняется проход — дальше откоса.
     * <p>
     * В дом входят не с последней клетки откоса, а с улицы: проверка входа
     * меряет спуск на четыре шага, и холм, поднимающийся на пятом, делал
     * вход стеной. Проход ровняется до улицы, по шагу за клетку.
     */
    public static final int APPROACH = 5;

    /** Колонна не наша или земли в ней нет: откос её не тронул. */
    private static final int UNTOUCHED = Integer.MIN_VALUE;

    /**
     * Выровнять землю вокруг площадки здания.
     * <p>
     * Кольцами наружу, и каждая клетка держится <b>двух</b> мерок: полосы
     * от пола и соседа внутрь — не дальше блока от него. Одной полосы мало:
     * на волнистой земле клетка за стеной лежит на гребне, следующая
     * во впадине, обе в своих полосах, а между ними снова стенка. Это
     * поймала проверка «деревня на холмах», а ровный склон не ловил никогда.
     *
     * @return сколько блоков грунта срыто и досыпано
     */
    public static int grade(ServerWorld world, Settlement settlement, Building building,
                            Schematic schematic) {
        Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
        BlockPos anchor = building.anchor();
        if (!Terrace.restsOnGround(world, anchor, size)) {
            // Дом на весу поставлен нарочно — ровнять к нему нечего,
            // вышел бы земляной вал под сваями.
            return 0;
        }

        int pad = anchor.getY() - 1;
        Set<Long> keepOff = keepOff(world, SettlementManager.get(world), settlement, building);
        java.util.Map<Long, Integer> tops = new java.util.HashMap<>();
        int[] budget = {BUDGET};

        for (int d = 1; d <= MARGIN && budget[0] > 0; d++) {
            for (BlockPos column : ring(anchor, size, d)) {
                long key = BlockPos.asLong(column.getX(), 0, column.getZ());
                if (keepOff.contains(key) || !world.isChunkLoaded(column)) {
                    continue;
                }
                int low = pad - d;
                int high = pad + d;
                int inner = d == 1 ? pad
                        : tops.getOrDefault(inward(anchor, size, column, d), UNTOUCHED);
                if (inner != UNTOUCHED) {
                    low = Math.max(low, inner - 1);
                    high = Math.min(high, inner + 1);
                }
                int top = shape(world, column, low, high, budget);
                if (top != UNTOUCHED) {
                    tops.put(key, top);
                }
            }
        }

        // Проход от двери: земля сразу за порогом — вровень с ним или на
        // ступень ниже, дальше — по шагу за клетку, до улицы. Мерится
        // от порога, а не от пола: у дома порог на блок выше пола, а у поля
        // калитка стоит над насыпью и грядкой, на три блока выше, — и мерка
        // от пола оставляла у калитки уступ в два блока. Упёрся в чужое —
        // проход кончился, но не сломался: выровненное проходимо.
        for (BlockPos door : Access.entrances(building, schematic)) {
            Direction out = Access.awayFrom(building, schematic, door);
            int threshold = door.getY();
            int last = threshold - 1;
            for (int step = 1; step <= APPROACH && budget[0] > 0; step++) {
                BlockPos column = door.offset(out, step);
                if (keepOff.contains(BlockPos.asLong(column.getX(), 0, column.getZ()))
                        || !world.isChunkLoaded(column)) {
                    break;
                }
                int top = step == 1
                        ? shape(world, column, threshold - 2, threshold - 1, budget)
                        : shape(world, column, last - 1, last + 1, budget);
                if (top == UNTOUCHED) {
                    break;
                }
                last = top;
            }
        }
        return BUDGET - budget[0];
    }

    /** Соседняя клетка на шаг ближе к следу — та, от которой меряется уступ. */
    private static long inward(BlockPos anchor, Vec3i size, BlockPos column, int d) {
        int reach = d - 1;
        int x = Math.max(anchor.getX() - reach, Math.min(column.getX(),
                anchor.getX() + size.getX() - 1 + reach));
        int z = Math.max(anchor.getZ() - reach, Math.min(column.getZ(),
                anchor.getZ() + size.getZ() - 1 + reach));
        return BlockPos.asLong(x, 0, z);
    }

    /**
     * Природный ли это грунт — то, что откос вправе срыть и чем досыпать.
     * <p>
     * Белый список, а не чёрный: всего, чего здесь нет, откос не касается.
     * Тот же урок, что у площадки, закопавшей однажды сундуки.
     */
    public static boolean isEarth(BlockState state) {
        if (state.hasBlockEntity()) {
            return false;
        }
        return state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND)
                || state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.TERRACOTTA)
                || state.isOf(Blocks.GRAVEL) || state.isOf(Blocks.CLAY)
                || state.isOf(Blocks.SANDSTONE) || state.isOf(Blocks.RED_SANDSTONE)
                || state.isOf(Blocks.SNOW_BLOCK) || state.isOf(Blocks.CALCITE);
    }

    /**
     * Мелочь поверх земли: трава, цветы, снег, саженец. Её сносят вместе
     * с грунтом, на котором она росла, — иначе она повисла бы в воздухе.
     */
    private static boolean isLitter(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isReplaceable() || state.isIn(BlockTags.FLOWERS)
                || state.isIn(BlockTags.SAPLINGS) || state.isOf(Blocks.SNOW);
    }

    /**
     * Поставить одну колонну в полосу.
     *
     * @return верх земли в колонне после работы, или {@link #UNTOUCHED}, если
     *         колонна чужая, это обрыв или стена горы
     */
    private static int shape(ServerWorld world, BlockPos column, int low, int high, int[] budget) {
        int ceiling = high + MAX_CHANGE + 1;
        int floor = low - MAX_CHANGE - 1;

        int top = UNTOUCHED;
        BlockState surface = null;
        for (int y = ceiling; y >= floor; y--) {
            BlockState state = world.getBlockState(column.withY(y));
            if (state.isAir() || isLitter(state) || state.isIn(BlockTags.LEAVES)) {
                // Листва — не земля, но и не повод бросать колонну: под
                // кроной куста земля такая же.
                continue;
            }
            if (!isEarth(state) || y == ceiling) {
                // Чужое сверху — колонна не наша. Грунт у самого потолка
                // окна — это стена горы, а не склон у дома.
                return UNTOUCHED;
            }
            top = y;
            surface = state;
            break;
        }
        if (surface == null) {
            // Земли в окне нет: обрыв или пустота. Мостов откос не строит.
            return UNTOUCHED;
        }

        if (top > high) {
            return cut(world, column, top, high, surface, budget) ? high : UNTOUCHED;
        }
        if (top < low) {
            return fill(world, column, top, low, surface, budget) ? low : UNTOUCHED;
        }
        return top;
    }

    /** Срыть грунт от верха до полосы; новая поверхность — тем же дёрном. */
    private static boolean cut(ServerWorld world, BlockPos column, int top, int high,
                               BlockState surface, int[] budget) {
        if (top - high > budget[0] || touchesFluid(world, column, high + 1, top)) {
            // Срой берег у воды — и откос станет прудом.
            return false;
        }
        // Весь столб проверяется до первого снятого блока: срезка, упёршаяся
        // в руду под дёрном, бросала колонну с открытой рудой и ямой на месте
        // дёрна. Чужое внутри — колонна не наша целиком.
        for (int y = top; y > high; y--) {
            if (!isEarth(world.getBlockState(column.withY(y)))) {
                return false;
            }
        }
        // Мелочь над верхом падает вместе с ним.
        for (int y = top + 1; y <= top + 2; y++) {
            BlockPos at = column.withY(y);
            if (isLitter(world.getBlockState(at)) && !world.getBlockState(at).isAir()) {
                world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        for (int y = top; y > high; y--) {
            BlockPos at = column.withY(y);
            if (!isEarth(world.getBlockState(at))) {
                return false;
            }
            world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            budget[0]--;
        }
        BlockPos newTop = column.withY(high);
        if (surface.isOf(Blocks.GRASS_BLOCK) && world.getBlockState(newTop).isIn(BlockTags.DIRT)) {
            world.setBlockState(newTop, Blocks.GRASS_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        }
        return isEarth(world.getBlockState(newTop));
    }

    /** Досыпать от верха до полосы тем, что лежит в колонне; сверху — дёрн, если был дёрн. */
    private static boolean fill(ServerWorld world, BlockPos column, int top, int low,
                                BlockState surface, int[] budget) {
        if (low - top > budget[0]) {
            return false;
        }
        for (int y = top + 1; y <= low; y++) {
            BlockState state = world.getBlockState(column.withY(y));
            if (!state.isAir() && !isLitter(state) && !isWildLeaves(state)) {
                // В воду и в чужое не сыплем. Дикий куст у стены — сыплем:
                // листва не земля и не чужая вещь, а без засыпки у стены
                // оставалась яма под кустом. Посаженная рукой листва —
                // чужая, её не трогают.
                return false;
            }
        }
        BlockState earth = surface.isOf(Blocks.GRASS_BLOCK) ? Blocks.DIRT.getDefaultState() : surface;
        if (surface.isOf(Blocks.GRASS_BLOCK)) {
            world.setBlockState(column.withY(top), earth, Block.NOTIFY_ALL);
        }
        for (int y = top + 1; y <= low; y++) {
            world.setBlockState(column.withY(y), y == low ? surface : earth, Block.NOTIFY_ALL);
            budget[0]--;
        }
        return true;
    }

    /** Дикая листва: выросла сама, а не посажена рукой. */
    private static boolean isWildLeaves(BlockState state) {
        return state.isIn(BlockTags.LEAVES) && state.contains(net.minecraft.block.LeavesBlock.PERSISTENT)
                && !state.get(net.minecraft.block.LeavesBlock.PERSISTENT);
    }

    /** Есть ли вода или лава рядом со срываемым столбом. */
    private static boolean touchesFluid(ServerWorld world, BlockPos column, int from, int to) {
        for (int y = from; y <= to; y++) {
            for (Direction side : Direction.Type.HORIZONTAL) {
                if (!world.getFluidState(column.withY(y).offset(side)).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Куда откос не заходит: следы других зданий, убранство и улицы.
     * <p>
     * Улица ровняется сама, по своему правилу шага; убранство поставлено
     * на свою землю; чужой след — чужая площадка со своим полом, а проход
     * к чужой двери — чужой вход.
     */
    private static Set<Long> keepOff(ServerWorld world, SettlementManager manager,
                                     Settlement settlement, Building building) {
        Set<Long> off = new HashSet<>(Roads.pavedColumns(world, settlement));
        for (BlockPos at : manager.decorOf(settlement.id())) {
            off.add(BlockPos.asLong(at.getX(), 0, at.getZ()));
        }
        for (Building other : settlement.buildings()) {
            if (other.id().equals(building.id())) {
                continue;
            }
            Schematic plan = SchematicLoader.get(BuildJob.schematicId(other)).orElse(null);
            if (plan == null) {
                continue;
            }
            // И проход перед его дверью: откос соседа не вправе срыть
            // или засыпать чужой вход.
            off.addAll(Access.doorway(other, plan, APPROACH, 0));
            Vec3i size = BuildSite.rotatedSize(plan.size(), other.rotation());
            for (int dx = 0; dx < size.getX(); dx++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    off.add(BlockPos.asLong(other.anchor().getX() + dx, 0,
                            other.anchor().getZ() + dz));
                }
            }
        }
        return off;
    }

    /** Колонны ровно в {@code d} шагах от прямоугольника следа. */
    static List<BlockPos> ring(BlockPos anchor, Vec3i size, int d) {
        List<BlockPos> ring = new ArrayList<>();
        int minX = anchor.getX() - d;
        int maxX = anchor.getX() + size.getX() - 1 + d;
        int minZ = anchor.getZ() - d;
        int maxZ = anchor.getZ() + size.getZ() - 1 + d;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x == minX || x == maxX || z == minZ || z == maxZ) {
                    ring.add(new BlockPos(x, anchor.getY(), z));
                }
            }
        }
        return ring;
    }
}
