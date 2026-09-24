package com.villagepax.sim.build;

import com.villagepax.VillagePax;
import com.villagepax.sim.Building;
import com.villagepax.sim.Ground;
import com.villagepax.sim.Settlement;
import net.minecraft.block.Block;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;

/**
 * Всход: лестница с эльфийского настила на землю.
 * <p>
 * Зеркало гномьих ворот, и ровно та же необходимость. Поселение эльфов
 * висит на девять блоков над лесом; без лестницы игрок видел бы деревню
 * с земли и не мог бы в неё попасть, а житель не мог бы из неё выйти.
 * <b>Деревня, в которую нельзя войти, — не деревня</b>, и это мод говорит
 * уже третий раз: про крыльцо, про скамью за порогом и про запечатанный
 * чертог.
 *
 * <h2>Лестница, а не лифт и не верёвка</h2>
 * Из настила вниз можно было бы спустить лестницу-перекладины вдоль ствола:
 * дёшево и всегда получается. Но по перекладинам ванильный поиск пути
 * ходит плохо, а стража и курьер ходят именно им; деревня, из которой
 * НПС физически не спускается, работала бы только на вид.
 * <p>
 * Поэтому всход — <b>маршевая лестница</b>: блок вниз и блок в сторону,
 * ступенями. По ней ходят все — игрок, житель и ванильный поиск, — и она
 * видна снизу, что для деревни на дереве важнее, чем кажется: иначе её
 * замечают, только задрав голову.
 *
 * <h2>Даром и из того же дерева</h2>
 * Ни доски со склада, ни платы: всход настелен <b>до прихода игрока</b>,
 * теми же предками, что подняли ратушу. Материал берётся из-под самого
 * порога — тот же довод, по которому крыльцо всегда из того же, из чего
 * сложен вход.
 */
public final class Ascent {

    /**
     * Дальше этого лестницу не тянут.
     * <p>
     * Двадцать четыре ступени: настил висит на девяти, и даже на склоне
     * лестница укладывается втрое короче. Всё, что длиннее, — это уже
     * не спуск с дерева, а мост до соседнего холма.
     */
    public static final int MAX_STEPS = 24;

    /** Ширина марша: по лестнице расходятся встречные. */
    public static final int WIDTH = 2;

    /** Сколько блоков над ступенью остаётся свободными. */
    private static final int HEADROOM = 3;

    private Ascent() {
    }

    /**
     * Спустить лестницу от ратуши на землю.
     *
     * @return сколько ступеней уложено, или пусто, если спускаться неоткуда
     */
    public static Optional<Integer> build(ServerWorld world, Settlement grove, Building hall) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(hall)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }

        BlockPos door = Access.entrances(hall, schematic).stream().findFirst().orElse(null);
        if (door == null) {
            return Optional.empty();
        }

        Direction down = Access.awayFrom(hall, schematic, door);
        BlockPos start = door.offset(down);
        BlockState tread = world.getBlockState(door.down());
        int top = door.getY() - 1;

        // Сперва примерить, потом класть.
        //
        // Первая version клала ступени по ходу дела и, не дойдя до земли
        // за двадцать четыре шага, бросала лестницу висеть в пустоте —
        // а рядом строила ствол. Над озером это выглядело так: доски
        // уходят в воду и обрываются, а у двери отдельно торчат
        // перекладины. Недостроенное хуже непостроенного.
        int steps = march(world, start, down, top);
        if (steps < 0) {
            VillagePax.LOGGER.info("Всход поселения {} не достал земли за {} шагов — "
                    + "спускаем ствол", grove.name(), MAX_STEPS);
            return Optional.of(drop(world, start, down, top, tread));
        }

        int level = top;
        for (int step = 1; step <= steps; step++) {
            lay(world, new BlockPos(start.offset(down, step - 1).getX(), level,
                    start.offset(down, step - 1).getZ()), down, tread);
            level--;
        }
        return Optional.of(steps);
    }

    /**
     * Сколько ступеней до земли — или {@code -1}, если её там нет.
     * <p>
     * Ничего не ставит: это примерка. Отделена от укладки нарочно, потому
     * что «дошли» и «не дошли» — два совсем разных здания, и решать, какое
     * строить, надо до первого блока.
     */
    private static int march(ServerWorld world, BlockPos start, Direction down, int top) {
        int level = top;
        for (int step = 1; step <= MAX_STEPS; step++) {
            BlockPos ahead = start.offset(down, step - 1);
            Integer soil = Ground.levelAt(world, ahead.getX(), ahead.getZ()).orElse(null);
            if (soil != null && soil >= level) {
                // Дошли до земли: ступень легла бы уже в грунт.
                return step;
            }
            level--;
        }
        return -1;
    }

    /**
     * Последнее средство: перекладины вниз от самого порога.
     * <p>
     * Марш не дошёл до земли за двадцать четыре ступени — значит под
     * деревней обрыв или вода. Оставлять из-за этого висящую деревню
     * нельзя: <b>у отказа должен быть выход, а не тупик</b>. То же
     * решение, что у гномьего ствола, и та же честная цена — по
     * перекладинам житель ходит хуже, чем по маршу, зато ходит.
     */
    private static int drop(ServerWorld world, BlockPos start, Direction down, int from,
                            BlockState tread) {
        Integer soil = Ground.levelAt(world, start.getX(), start.getZ()).orElse(null);
        int bottom = soil == null ? from - Canopy.LIFT : soil;
        Direction across = down.rotateYClockwise();

        // Ствол, а не голые перекладины.
        //
        // Первая версия ставила лестницу прямо в воздух под помостом —
        // и вся она осыпалась предметами в тот же тик: держаться ей было
        // не на чем. У гномов тот же ствол работает, потому что идёт
        // в сплошном камне; здесь стену надо принести с собой.
        for (int level = from; level > bottom; level--) {
            BlockPos post = new BlockPos(start.getX(), level, start.getZ());
            if (canLay(world, post)) {
                world.setBlockState(post, tread, Block.NOTIFY_ALL);
            }
            BlockPos rung = post.offset(across);
            if (canLay(world, rung)) {
                world.setBlockState(rung, Blocks.LADDER.getDefaultState()
                        .with(HorizontalFacingBlock.FACING, across), Block.NOTIFY_ALL);
            }
        }

        // Под перекладинами должно быть на что встать: без опоры житель,
        // слезший с последней, продолжит падать.
        BlockPos foot = new BlockPos(start.getX(), bottom, start.getZ()).offset(across);
        if (!isSound(world, foot)) {
            world.setBlockState(foot, tread, Block.NOTIFY_ALL);
        }
        return from - bottom;
    }

    /**
     * Уложить одну ступень: настил под ногу и просвет над ней.
     * <p>
     * Ступень шириной в два блока, и оба — по ходу вбок от оси: марш,
     * у которого ширина считалась бы от середины, при чётной ширине
     * съезжал бы на полблока и не сходился бы с дверью.
     */
    private static void lay(ServerWorld world, BlockPos at, Direction down, BlockState tread) {
        Direction across = down.rotateYClockwise();

        for (int side = 0; side < WIDTH; side++) {
            BlockPos column = at.offset(across, side);
            if (canLay(world, column)) {
                world.setBlockState(column, tread, Block.NOTIFY_ALL);
            }
            for (int up = 1; up <= HEADROOM; up++) {
                BlockPos cell = column.up(up);
                if (isGrowth(world.getBlockState(cell))) {
                    world.setBlockState(cell, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    /**
     * Лес ли это на пути — то, что билдер вправе снять.
     * <p>
     * Листва, ствол и трава: ветка над лестницей это помеха, и та же
     * рука, что валит дерево под стройку, снимает и её. А камень,
     * стена и сундук — <b>не</b> лес: всход спускается через лес,
     * а не прорубается сквозь чужое. Тот же список, по которому
     * площадка отличает пустоту от чужого добра.
     */
    private static boolean isGrowth(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        return state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)
                || state.isReplaceable();
    }

    /**
     * Можно ли положить здесь свой блок.
     * <p>
     * Чужое добро не трогаем: сундук, печь и всякий блок с содержимым —
     * не пустое место, и настелить по нему значило бы съесть его молча.
     * Тот же белый список, по которому площадка отличает пустоту
     * от чужого, и заведён он там ровно после того, как закопал сундуки.
     * <p>
     * И уже готовый пол не перекладываем: если под ногой и так твёрдо,
     * ступень не нужна — нужна проходимость, а она есть.
     */
    private static boolean canLay(ServerWorld world, BlockPos at) {
        BlockState state = world.getBlockState(at);
        if (state.hasBlockEntity()) {
            return false;
        }
        return !state.isSolidBlock(world, at) || isGrowth(state);
    }

    private static boolean isSound(ServerWorld world, BlockPos at) {
        BlockState state = world.getBlockState(at);
        return state.getFluidState().isEmpty() && state.isSolidBlock(world, at);
    }
}
