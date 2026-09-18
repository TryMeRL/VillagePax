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
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        for (BlockPos door : entrances(building, schematic)) {
            laid += stepsFrom(world, building, schematic, door);
        }
        return laid;
    }

    /**
     * В какую сторону от входа уходить.
     * <p>
     * Спрашивается <b>сам чертёж</b>: наружу — туда, где в плане на уровне
     * порога дольше всего нет блоков. Это единственная мерка, которая
     * верна для всех зданий сразу.
     * <p>
     * Прежние две ошибались. Догадка по середине следа шла в стену у хижины
     * лесоруба: к дому пристроена роща, середина уезжает в неё, и крыльцо
     * упиралось в сруб — «стена высотой 3», как сказала проверка входов.
     * А {@code facing} у калитки — это ось прохода, и она молчит о том,
     * с какой стороны улица.
     */
    public static Direction awayFrom(Building building, Schematic schematic, BlockPos door) {
        Vec3i size = schematic.size();
        BlockPos local = localOf(building, schematic, door);
        Set<BlockPos> filled = new HashSet<>();
        for (BuildStep step : schematic.plan().steps()) {
            if (step.placesBlock() && !schematic.blockAt(step.paletteIndex()).isAir()) {
                filled.add(step.pos());
            }
        }

        Direction best = Direction.NORTH;
        int longest = -1;
        for (Direction way : Direction.Type.HORIZONTAL) {
            int open = 0;
            for (int step = 1; step <= REACH; step++) {
                BlockPos at = local.offset(way, step);
                boolean outside = at.getX() < 0 || at.getZ() < 0
                        || at.getX() >= size.getX() || at.getZ() >= size.getZ();
                if (outside) {
                    // За краем следа — самая улица и есть.
                    open += REACH;
                    break;
                }
                if (filled.contains(at)) {
                    break;
                }
                open++;
            }
            if (open > longest) {
                longest = open;
                best = way;
            }
        }
        // Направление чертежа поворачивается вместе со зданием.
        return turned(best, building.rotation());
    }

    /** Место входа в собственных координатах схемы. */
    private static BlockPos localOf(Building building, Schematic schematic, BlockPos door) {
        for (BuildStep step : schematic.plan().steps()) {
            if (BuildJob.worldPos(building, schematic.size(), step.pos()).equals(door)) {
                return step.pos();
            }
        }
        return BlockPos.ORIGIN;
    }

    /** Повернуть направление чертежа так, как повёрнуто здание. */
    private static Direction turned(Direction way, BlockRotation rotation) {
        return rotation.rotate(way);
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
            Direction out = awayFrom(building, schematic, door);
            // Те же места, что и у настоящей укладки: ноги опускаются
            // на блок за шаг, значит блок ложится на два ниже предыдущих ног.
            for (int step = 1; step <= REACH; step++) {
                spots.add(door.offset(out, step).withY(door.getY() - step - 1));
            }
        }
        return spots;
    }

    /**
     * Рассказ о том, что крыльцо видит у входа. Только для проверок.
     */
    public static String story(ServerWorld world, Building building, Schematic schematic) {
        StringBuilder story = new StringBuilder();
        for (BlockPos door : entrances(building, schematic)) {
            Direction out = awayFrom(building, schematic, door);
            story.append("[вход ").append(door.toShortString())
                    .append(" наружу ").append(out)
                    .append(" опора=").append(standable(world, door.down()))
                    .append(" из=").append(world.getBlockState(door.down()).getBlock());
            int walk = door.getY();
            for (int step = 1; step <= REACH; step++) {
                BlockPos column = door.offset(out, step);
                story.append(" | шаг").append(step)
                        .append(" земля=").append(topSolid(world, column, walk + 1))
                        .append(" нужна=").append(needsStep(world, column, walk - 2));
            }
            story.append("]");
        }
        return story.toString();
    }

    /**
     * Лесенка от одного входа наружу.
     * <p>
     * Наружу — это прочь от середины здания: по той стороне, с которой
     * вход и смотрит. Иначе ступени легли бы внутрь дома.
     */
    private static int stepsFrom(ServerWorld world, Building building, Schematic schematic,
                                 BlockPos door) {
        Direction out = awayFrom(building, schematic, door);
        BlockState material = world.getBlockState(door.down());
        if (!standable(world, door.down())) {
            // Порог висит в воздухе: чинить надо не крыльцом.
            return 0;
        }

        BlockState tread = stepBlock(material, out);
        int laid = 0;

        // Считается ВЫСОТА НОГ, а не место блока, и мерка одна на весь
        // спуск: каждый следующий шаг наружу ниже предыдущего ровно
        // на один блок. Прежний счёт сравнивал землю с тем уровнем, куда
        // ступень только собиралась лечь, — и у самой двери оставался
        // перепад в два блока. Ступень при этом стояла, крыльцо выглядело
        // сделанным, а войти было нельзя: ровно это заказчик и видел
        // четыре раза подряд.
        int walk = door.getY();

        for (int step = 1; step <= REACH; step++) {
            BlockPos column = door.offset(out, step);
            int ground = topSolid(world, column, walk + 1);

            // Земля не ниже чем на шаг — ступень тут не нужна, но выход
            // на этом НЕ КОНЧАЕТСЯ: крыльцо идёт дальше по самой земле.
            //
            // Прежняя редакция здесь выходила, и это была последняя
            // и самая обидная ошибка из всей череды: у поля сразу
            // за калиткой лежала ровная клетка, крыльцо радостно
            // считало дело сделанным, а обрыв в два блока ждал
            // на третьей. Ступень не клалась вовсе, житель не входил,
            // и заказчик четвёртый раз писал одно и то же.
            //
            // Ровнять проходимое по-прежнему нельзя: блок кладётся
            // только там, где перепад больше шага.
            if (ground + 1 >= walk - 1) {
                walk = ground + 1;
                continue;
            }

            int under = walk - 2;
            if (!needsStep(world, column, under)) {
                return laid;
            }

            world.setBlockState(column.withY(under), tread, Block.NOTIFY_ALL);
            laid++;
            walk--;

            // Над ступенью должно быть, где пройти: два блока воздуха.
            for (int head = 1; head <= 2; head++) {
                BlockPos above = column.withY(under + head);
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
        return outward(centre, door, null);
    }

    /**
     * В какую сторону смотрит вход.
     * <p>
     * <b>Ось берётся у самого блока</b>, если он её знает: у двери и
     * у калитки есть {@code facing}, и это точный ответ. Догадка по
     * середине здания ошибается там, где след не квадратный: у хижины
     * лесоруба к дому пристроена роща, середина уезжает в неё, и крыльцо
     * шло прямо в ограду. Проверка входов у всех зданий это и показала —
     * «стена высотой 3» у обоих лесорубов.
     * <p>
     * Знак — по середине здания: наружу значит прочь от неё. Сама
     * {@code facing} этого не скажет, потому что через калитку ходят
     * в обе стороны.
     */
    private static Direction outward(BlockPos centre, BlockPos door, Direction axisOf) {
        int dx = door.getX() - centre.getX();
        int dz = door.getZ() - centre.getZ();

        if (axisOf != null && axisOf.getAxis().isHorizontal()) {
            return axisOf.getAxis() == Direction.Axis.X
                    ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                    : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Ось входа по самому блоку в мире: дверь и калитка её знают.
     * <p>
     * Спрашивается мир, а не схема: схема хранит состояние до поворота
     * здания, а игрок ставит дом как хочет.
     */
    private static Direction axisOf(ServerWorld world, BlockPos door) {
        BlockState state = world.getBlockState(door);
        // У калитки facing — это ось прохода, у двери — куда она открыта.
        // Обе годятся: нужна только ось, знак берётся от середины дома.
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty facing
                    && "facing".equals(property.getName())) {
                Direction value = state.get(facing);
                if (value.getAxis().isHorizontal()) {
                    return value;
                }
            }
        }
        return null;
    }
}
