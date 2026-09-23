package com.villagepax.sim.build;

import com.villagepax.sim.Building;
import com.villagepax.sim.Ground;
import com.villagepax.sim.Settlement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;

/**
 * Ворота чертога: ход от ратуши гномов к дневному свету.
 * <p>
 * Это не украшение и не удобство, а <b>условие того, что чертог вообще
 * существует для игрока</b>. Здания гномов вырубаются в сплошном камне,
 * галереи между ними — тоже; без этого хода получилась бы запечатанная
 * в горе полость, о которой игрок узнал бы, только раскопав её киркой
 * наугад. Деревня, в которую нельзя войти, — это не деревня.
 * <p>
 * Мод уже получал этот урок дважды: «вход, в который не войти, — это
 * не экономия, а поломка» — про крыльцо, и «зайти нельзя из-за скамьи»
 * — про дома. Здесь он применён <b>до</b> жалобы, а не после.
 *
 * <h2>Почему ход, а не шахта сверху</h2>
 * Колодец с лестницей был бы проще и всегда бы получался. Но гномьи
 * ворота в склоне — это то, что игрок узнаёт, не спускаясь: он видит
 * их с земли, идя мимо горы. Люк в траве на вершине не говорит ничего
 * и находится только случайно.
 *
 * <h2>Ход сперва идёт ровно, потом поднимается</h2>
 * На склоне горы поверхность падает быстрее, чем девять блоков за сорок
 * восемь, и ровный ход выходит наружу сам — коротким и прямым. Но гора
 * бывает и плоской сверху, и тогда ровный ход не вышел бы никуда: поэтому
 * он <b>начинает подниматься</b>, по блоку на два шага. Подъём в полблока
 * на шаг проходят и житель, и игрок, и ванильный поиск пути; из глубины
 * в девять блоков такой ход выбирается самое большее за восемнадцать шагов.
 * <p>
 * Ровно, пока можно, и в гору, когда иначе никак: подниматься сразу
 * значило бы выводить ворота на середину склона даже там, где рядом
 * есть готовый обрыв.
 *
 * <h2>Даром</h2>
 * Ни камня со склада, ни возврата выбитого: ворота прорублены
 * <b>до прихода игрока</b>, теми же предками, что подняли ратушу. Просить
 * за них материалы было бы не у кого — деревня в свой первый миг пуста.
 */
public final class Gate {

    /**
     * Дальше этого ход не тянут.
     * <p>
     * Сорок восемь — та же мера, что у улицы. Ворота длиннее самого
     * чертога перестали бы читаться как вход и стали бы туннелем,
     * а туннель — это уже не «гномы живут в горе», а «гномы живут
     * в конце коридора».
     */
    public static final int MAX_LENGTH = 48;

    /**
     * Ширина хода.
     * <p>
     * Три: по такому ходу расходятся встречные, и он виден снаружи как
     * ворота, а не как нора. Одноклеточный лаз читался бы норой даже
     * с лампами.
     */
    public static final int WIDTH = 3;

    /** Через сколько блоков под сводом висит лампа. */
    private static final int LAMP_EVERY = 5;

    /** Через сколько шагов ход поднимается на блок, когда выхода всё нет. */
    private static final int RISE_EVERY = 2;

    private Gate() {
    }

    /**
     * Прорубить ворота от ратуши чертога наружу.
     *
     * @return длина хода в блоках, или пусто, если наружу отсюда не выйти
     */
    public static Optional<Integer> carve(ServerWorld world, Settlement hold, Building hall) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(hall)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }

        BlockPos door = Access.entrances(hall, schematic).stream().findFirst().orElse(null);
        if (door == null) {
            return Optional.empty();
        }

        Direction out = Access.awayFrom(hall, schematic, door);
        BlockPos start = door.offset(out);
        int floor = door.getY() - 1;
        BlockState wall = world.getBlockState(door.down());

        for (int step = 1; step <= MAX_LENGTH; step++) {
            BlockPos ahead = start.offset(out, step - 1);
            BlockPos at = new BlockPos(ahead.getX(), floor, ahead.getZ());

            dig(world, at, out, wall, step % LAMP_EVERY == 0);

            Integer outside = Ground.levelAt(world, ahead.getX(), ahead.getZ()).orElse(null);
            if (outside != null && outside <= floor + 1) {
                // Вышли на склон: ход кончается там, где под ногами
                // снаружи та же высота, что и внутри. Порог не нужен —
                // его тут просто нет.
                return Optional.of(step);
            }
            // Первую половину пути ход ищет склон ровно, вторую —
            // выбирается наверх всё круче: лучше лестница, чем тупик.
            int rise = step * 2 > MAX_LENGTH ? 1 : RISE_EVERY;
            if (step % rise == 0) {
                floor++;
            }
        }
        return Optional.of(shaft(world, start, door.getY() - 1, wall));
    }

    /**
     * Последнее средство: ствол наверх у самых ворот.
     * <p>
     * Ход не вышел никуда за сорок восемь шагов — значит гора над ним
     * растёт быстрее, чем он поднимается. Такое место бывает, и оставлять
     * из-за него запечатанный чертог нельзя: <b>у отказа должен быть
     * выход, а не тупик</b>.
     * <p>
     * Ствол с лестницей некрасив, и это честная цена: игрок, вышедший
     * по нему на вершину, увидит ровно то, что есть, — гномы врылись
     * так глубоко, что прямого выхода не нашлось.
     */
    private static int shaft(ServerWorld world, BlockPos start, int floor, BlockState wall) {
        int top = Ground.levelAt(world, start.getX(), start.getZ()).orElse(floor + Hold.DEPTH);
        int height = Math.max(1, top - floor);

        for (int up = 0; up <= height; up++) {
            BlockPos cell = new BlockPos(start.getX(), floor + up, start.getZ());
            if (up > 0) {
                world.setBlockState(cell, Blocks.LADDER.getDefaultState(), Block.NOTIFY_ALL);
            } else if (!isFloorSound(world, cell)) {
                world.setBlockState(cell, wall, Block.NOTIFY_ALL);
            }
        }
        return height;
    }

    /**
     * Выбрать одну клетку хода: пол, свод и опора под ногой.
     * <p>
     * Опора кладётся из того же камня, из которого сложен порог ратуши:
     * ход, у которого под ногами дыра, — это не ход. Тот же довод,
     * по которому крыльцо берёт материал из-под самого входа.
     */
    private static void dig(ServerWorld world, BlockPos at, Direction out, BlockState floor,
                            boolean lamp) {
        Direction across = out.rotateYClockwise();
        int half = WIDTH / 2;

        for (int side = -half; side <= half; side++) {
            BlockPos column = at.offset(across, side);
            if (!isFloorSound(world, column)) {
                world.setBlockState(column, floor, Block.NOTIFY_ALL);
            }
            for (int up = 1; up <= Hold.HEADROOM; up++) {
                BlockPos cell = column.up(up);
                if (!world.getBlockState(cell).isAir()) {
                    world.setBlockState(cell, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }

        if (lamp) {
            // Лампа под сводом, а не на полу: на полу её собьёт первый же
            // прохожий, а тёмный ход в Minecraft — это не мрачно, это
            // гнездо мобов у ворот деревни.
            BlockPos hang = at.up(Hold.HEADROOM);
            world.setBlockState(hang, Blocks.LANTERN.getDefaultState()
                    .with(net.minecraft.block.LanternBlock.HANGING, true), Block.NOTIFY_ALL);
        }
    }

    /** Есть ли на что ступить: пол должен быть твёрдым и сухим. */
    private static boolean isFloorSound(ServerWorld world, BlockPos at) {
        BlockState state = world.getBlockState(at);
        return state.getFluidState().isEmpty() && state.isSolidBlock(world, at);
    }
}
