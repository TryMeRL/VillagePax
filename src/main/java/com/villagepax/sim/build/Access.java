package com.villagepax.sim.build;

import com.villagepax.sim.Building;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Крыльцо: ступени от входа к земле.
 * <p>
 * Жалоба заказчика, повторённая трижды: «который раз не взобраться
 * на ферму, с неё не смогут забрать посев». Причина не в схеме поля,
 * а в устройстве всех зданий разом. Здание стоит на своём цоколе, под
 * цоколь мод подсыпает опору, и пол оказывается на блок-два выше земли
 * вокруг. Шаг в один блок игрок и житель делают сами; <b>два не делает
 * никто</b>, и вход превращается в порог, который не переступить.
 * Поле от этого стоит нетронутым: фермер не входит, урожай не убирают.
 * <p>
 * Чинится это не правкой схем — их шесть, и завтра будет двадцать, —
 * а <b>общим правилом</b>: у каждого входа после стройки выкладывается
 * лесенка вниз, по блоку на шаг, пока не встретится земля. По такой
 * лестнице проходят и человек, и ванильный поиск пути, а выглядит она
 * крыльцом, потому что крыльцо и есть.
 * <p>
 * Материал берётся <b>из-под самого входа</b>: у норманнского дома это
 * булыжник цоколя, у поля — земля насыпи. Ничего не заказывается и ни
 * с чем не спорит: крыльцо всегда из того же, из чего порог.
 * <p>
 * И со склада за него не берут — единственное в моде, что строится
 * даром. Опора под домом ждёт излишков и это правильно: не хватило —
 * дом просто стоит на сваях. А вход, в который не войти, — это не
 * экономия, а поломка, и откладывать её до следующего завоза нельзя.
 */
public final class Access {

    /** Как далеко от входа тянуть ступени. Дальше — уже улица. */
    private static final int REACH = 4;

    /** Глубже этого лесенку не роют: там уже обрыв, а не порог. */
    private static final int DROP = 4;

    private Access() {
    }

    /**
     * Выложить ступени у всех входов здания.
     *
     * @return сколько блоков положено — по этому числу проверка и судит
     */
    public static int porch(ServerWorld world, Building building, Schematic schematic) {
        int laid = 0;
        BlockPos centre = BuildJob.worldPos(building, schematic.size(),
                new BlockPos(schematic.size().getX() / 2, 0, schematic.size().getZ() / 2));
        for (BlockPos door : entrances(building, schematic)) {
            laid += stepsFrom(world, centre, door);
        }
        return laid;
    }

    /**
     * Где у здания вход.
     * <p>
     * И метка двери, и калитка ограды: у поля двери нет вовсе, а войти
     * в него надо — с этого вся починка и началась.
     */
    public static List<BlockPos> entrances(Building building, Schematic schematic) {
        List<BlockPos> doors = new ArrayList<>(
                BuildJob.pointsOfInterest(building, schematic, MarkerKind.DOOR));

        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            BlockState block = schematic.blockAt(step.paletteIndex());
            if (block.isIn(BlockTags.FENCE_GATES) || block.isIn(BlockTags.DOORS)) {
                BlockPos where = BuildJob.worldPos(building, schematic.size(), step.pos());
                if (!doors.contains(where)) {
                    doors.add(where);
                }
            }
        }
        return thresholds(doors);
    }

    /**
     * Из всех блоков входа — только порог.
     * <p>
     * Дверь высотой в два блока (и метка её, и сама дверь) даёт в столбце
     * две точки, а вход у неё один: <b>нижняя</b>. Крыльцо, померенное
     * от верхней половины, выкладывало лишнюю ступень на высоте головы —
     * ровно это и нашли проверки улиц, увидев булыжник там, где ждали
     * мостовую.
     */
    private static List<BlockPos> thresholds(List<BlockPos> doors) {
        Map<Long, BlockPos> lowest = new LinkedHashMap<>();
        for (BlockPos door : doors) {
            long column = ((long) door.getX() << 32) ^ (door.getZ() & 0xFFFFFFFFL);
            BlockPos known = lowest.get(column);
            if (known == null || door.getY() < known.getY()) {
                lowest.put(column, door);
            }
        }
        return List.copyOf(lowest.values());
    }

    /**
     * Куда крыльцо <b>могло бы</b> лечь — одной геометрией, без мира.
     * <p>
     * Нужно уборке: ступени ложатся снаружи следа здания, а снос идёт
     * по следу, и в общем мире игровых тестов крыльцо оставалось навсегда.
     * Считать по миру нельзя — он уже изменён; считать по чертежу можно,
     * потому что место ступени зависит только от порога и середины дома.
     */
    public static List<BlockPos> stepSpots(Building building, Schematic schematic) {
        BlockPos centre = BuildJob.worldPos(building, schematic.size(),
                new BlockPos(schematic.size().getX() / 2, 0, schematic.size().getZ() / 2));
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos door : entrances(building, schematic)) {
            Direction out = outward(centre, door);
            for (int step = 1; step <= REACH; step++) {
                spots.add(door.offset(out, step).withY(door.getY() - step));
            }
        }
        return spots;
    }

    /**
     * Лесенка от одного входа наружу.
     * <p>
     * Наружу — это прочь от середины здания: по той стороне, с которой
     * вход и смотрит. Иначе ступени легли бы внутрь дома.
     */
    private static int stepsFrom(ServerWorld world, BlockPos centre, BlockPos door) {
        Direction out = outward(centre, door);
        BlockState material = world.getBlockState(door.down());
        if (!standable(world, door.down())) {
            // Порог висит в воздухе: чинить надо не крыльцом.
            return 0;
        }

        BlockState tread = stepBlock(material, out);
        int laid = 0;
        for (int step = 1; step <= REACH; step++) {
            // Каждый шаг наружу — на блок ниже: и человек, и поиск пути
            // проходят перепад в один блок в обе стороны.
            BlockPos column = door.offset(out, step);
            int top = door.getY() - step;

            // Перепад в один блок — не помеха, а обычный шаг, и засыпать
            // его нельзя. Крыльцо, ровняющее и то, что и так проходимо,
            // растаскивает булыжник по всей колонии и лезет на улицу —
            // ровно это и показали проверки дорог.
            int ground = topSolid(world, column, door.getY());
            if (ground >= top - 1) {
                return laid;
            }
            if (!needsStep(world, column, top)) {
                return laid;
            }

            world.setBlockState(column.withY(top), tread, Block.NOTIFY_ALL);
            laid++;
            // Над ступенью должно быть, где пройти: два блока воздуха.
            for (int head = 1; head <= 2; head++) {
                BlockPos above = column.withY(top + head);
                if (!world.getBlockState(above).isAir()
                        && !world.getBlockState(above).isSolidBlock(world, above)) {
                    world.removeBlock(above, false);
                }
            }
        }
        return laid;
    }

    /**
     * Из чего сделать ступень.
     * <p>
     * Заказчик увидел готовое крыльцо и сказал: «просто блок добавился».
     * Он прав: кусок земли под калиткой чинит проход, но выглядит
     * оплошностью, а не крыльцом. Поэтому у материалов, у которых
     * в ванили есть ступенчатая пара, крыльцо кладётся <b>ступенями</b>
     * и разворачивается к порогу — по ним и видно, что это вход.
     * <p>
     * Таблицей, а не угадыванием по имени: {@code stone_bricks} даёт
     * {@code stone_brick_stairs}, а {@code dark_oak_planks} —
     * {@code dark_oak_stairs}, и правило «приписать _stairs» промахнулось
     * бы на обоих. Чего в таблице нет, то кладётся целым блоком: земля
     * ступеней не имеет, и ком земли под калиткой честнее пустоты.
     */
    private static BlockState stepBlock(BlockState material, Direction out) {
        Block tread = TREAD.get(material.getBlock());
        if (tread == null) {
            return material;
        }
        if (!(tread instanceof StairsBlock)) {
            // У земли ступеней не бывает, а тропа — бывает: вытоптанная
            // дорожка к калитке читается входом, а ком земли — оплошностью.
            return tread.getDefaultState();
        }
        return tread.getDefaultState()
                // Лицом к порогу: по ступени поднимаются ко входу, а не от него.
                .with(StairsBlock.FACING, out.getOpposite())
                .with(StairsBlock.HALF, BlockHalf.BOTTOM)
                .with(StairsBlock.SHAPE, StairShape.STRAIGHT);
    }

    /**
     * Чем мостить ступень для каждого основания.
     * <p>
     * Камню и доскам — ступени, земле — тропа. Заказчик увидел готовое
     * крыльцо у поля и сказал «просто блок добавился»: он был прав,
     * ком земли под калиткой чинит проход и портит вид.
     */
    private static final Map<Block, Block> TREAD = Map.ofEntries(
            Map.entry(Blocks.COBBLESTONE, Blocks.COBBLESTONE_STAIRS),
            Map.entry(Blocks.STONE, Blocks.STONE_STAIRS),
            Map.entry(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS),
            Map.entry(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS),
            Map.entry(Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_STAIRS),
            Map.entry(Blocks.OAK_PLANKS, Blocks.OAK_STAIRS),
            Map.entry(Blocks.SANDSTONE, Blocks.SANDSTONE_STAIRS),
            Map.entry(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_STAIRS),
            Map.entry(Blocks.DIRT, Blocks.DIRT_PATH),
            Map.entry(Blocks.COARSE_DIRT, Blocks.DIRT_PATH),
            Map.entry(Blocks.ROOTED_DIRT, Blocks.DIRT_PATH),
            Map.entry(Blocks.GRASS_BLOCK, Blocks.DIRT_PATH),
            Map.entry(Blocks.FARMLAND, Blocks.DIRT_PATH));

    /**
     * Можно ли стоять на этом блоке.
     * <p>
     * По столкновениям, а не по «полный ли это куб». Ступень из тропы,
     * плиты или ступеней — не полный куб, и мерка «полный куб» объявила
     * бы собственное крыльцо пустотой: мод положил бы вторую ступень
     * поверх первой, а проверка решила бы, что войти нельзя.
     */
    private static boolean standable(ServerWorld world, BlockPos at) {
        return !world.getBlockState(at).getCollisionShape(world, at).isEmpty();
    }

    /** Верхний твёрдый блок в столбце: от порога и вниз. */
    private static int topSolid(ServerWorld world, BlockPos column, int from) {
        for (int y = from; y > world.getBottomY(); y--) {
            BlockPos at = column.withY(y);
            if (standable(world, at)) {
                return y;
            }
        }
        return world.getBottomY();
    }

    /**
     * Нужна ли ступень в этой точке.
     * <p>
     * Нужна, если под ней пусто до самого обрыва: класть блок в воздух
     * посреди ущелья — не крыльцо, а мостик в никуда.
     */
    private static boolean needsStep(ServerWorld world, BlockPos column, int top) {
        for (int depth = 0; depth <= DROP; depth++) {
            BlockPos under = column.withY(top - depth);
            if (under.getY() <= world.getBottomY()) {
                return false;
            }
            if (standable(world, under)) {
                return depth > 0;
            }
        }
        return false;
    }

    /**
     * В какую сторону смотрит вход.
     * <p>
     * По тому, в какую сторону он смещён от середины здания. Это грубо
     * и этого довольно: вход стоит в стене, а стена — с краю.
     */
    private static Direction outward(BlockPos centre, BlockPos door) {
        int dx = door.getX() - centre.getX();
        int dz = door.getZ() - centre.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
