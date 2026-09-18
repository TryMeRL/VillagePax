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

    /**
     * Цена улицы при выборе стороны: заведомо дороже любой пустоты внутри.
     * Пустота под крышей стоит не больше {@link #REACH}, и никакая горница
     * не перевесит выхода наружу.
     */
    private static final int STREET = 100;

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
     * <p>
     * <b>Улица бьёт любую пустоту под крышей</b>, и это не мелочь счёта.
     * Мерка «где дольше пусто» у двери в южной стене давала поровну:
     * на юг след кончается сразу, на север — четыре пустые клетки горницы.
     * Ничья доставалась тому, кого первым назовёт перебор сторон, то есть
     * северу, и крыльцо ложилось внутрь дома. Сейчас за краем следа дают
     * заведомо больше, и чем ближе край, тем больше: наружу — это туда,
     * где дом кончается раньше всего.
     */
    public static Direction awayFrom(Building building, Schematic schematic, BlockPos door) {
        Vec3i size = schematic.size();
        BlockPos local = BuildSite.toLocal(building.anchor(), size, building.rotation(), door);

        // Спрашиваются только те клетки, от которых что-то зависит: их
        // шестнадцать. Первая редакция складывала в множество ВСЕ блоки
        // схемы и потом заглядывала в него шестнадцать раз, а крыльцо
        // зовётся на каждом законченном ярусе стройки — ратуша заказывала
        // тысячи позиций по десятку раз за дом. «Куча проверок, но не
        // нагружая систему» — значит один проход и горсть клеток.
        Set<BlockPos> asked = new HashSet<>();
        for (Direction way : Direction.Type.HORIZONTAL) {
            for (int step = 1; step <= REACH; step++) {
                asked.add(local.offset(way, step));
            }
        }
        Set<BlockPos> filled = new HashSet<>();
        for (BuildStep step : schematic.plan().steps()) {
            if (step.placesBlock() && asked.contains(step.pos())
                    && !schematic.blockAt(step.paletteIndex()).isAir()) {
                filled.add(step.pos());
            }
        }

        Direction best = Direction.NORTH;
        int bestScore = Integer.MIN_VALUE;
        for (Direction way : Direction.Type.HORIZONTAL) {
            int score = 0;
            for (int step = 1; step <= REACH; step++) {
                BlockPos at = local.offset(way, step);
                boolean outside = at.getX() < 0 || at.getZ() < 0
                        || at.getX() >= size.getX() || at.getZ() >= size.getZ();
                if (outside) {
                    // Улица найдена, и чем ближе она, тем вернее сторона.
                    score = STREET - step;
                    break;
                }
                if (filled.contains(at)) {
                    break;
                }
                // Пусто, но всё ещё под крышей: это горница, а не улица.
                score = step;
            }
            if (score > bestScore) {
                bestScore = score;
                best = way;
            }
        }
        // Направление чертежа поворачивается вместе со зданием.
        return building.rotation().rotate(best);
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
     * потому что столбцы крыльца зависят только от порога и стороны.
     * <p>
     * А вот <b>высоту предсказать нельзя</b>, и попытка стоила отдельной
     * головной боли. Укладка идёт по земле: одна ровная клетка перед
     * обрывом — и вся лесенка съезжает на блок выше, чем сулит счёт
     * «шаг вниз за шаг наружу». Уборка тогда стирала пустое место,
     * а чужой булыжник оставался ждать следующую проверку. Поэтому
     * возвращается вся полоса, где ступень могла оказаться, а разбирается
     * по {@link #isTread} — по примете, а не по адресу.
     */
    public static List<BlockPos> stepSpots(Building building, Schematic schematic) {
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos door : entrances(building, schematic)) {
            Direction out = awayFrom(building, schematic, door);
            for (int step = 1; step <= REACH; step++) {
                BlockPos column = door.offset(out, step);
                for (int down = 1; down <= DROP + 1; down++) {
                    spots.add(column.withY(door.getY() - down));
                }
            }
        }
        return spots;
    }

    /**
     * Похоже ли это на ступень крыльца.
     * <p>
     * Уборке в проверках: крыльцо кладётся снаружи следа, снос идёт по следу,
     * и убирать приходится по приметам. Приметы точные — ступени и тропа:
     * ни то, ни другое само не заводится там, где мод их не клал, а землю
     * и камень трогать нельзя. Первая уборка их трогала и выедала площадку
     * под соседними проверками.
     * <p>
     * Приметы держатся на {@link #TREAD}: всё, из чего в моде сложен порог,
     * отдаёт ступени или тропу. Появится в схемах основание, которого там
     * нет, — крыльцо положит его целым блоком, и уборка такой блок не
     * узнает; тогда сюда добавится и он.
     */
    public static boolean isTread(BlockState state) {
        return state.getBlock() instanceof StairsBlock || state.isOf(Blocks.DIRT_PATH);
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
            // Высота ног ведётся так же, как в самой укладке: рассказ,
            // считающий иначе, уводит от беды вместо того, чтобы к ней
            // привести, — а зовут его как раз тогда, когда ничего не понятно.
            int walk = door.getY();
            for (int step = 1; step <= REACH; step++) {
                BlockPos column = door.offset(out, step);
                int ground = topSolid(world, column, walk + 1, walk - DROP - 2);
                story.append(" | шаг").append(step)
                        .append(" земля=").append(ground)
                        .append(" ноги=").append(walk)
                        .append(" нужна=").append(needsStep(world, column, walk - 2));
                if (ground + 1 >= walk - 1) {
                    walk = ground + 1;
                } else {
                    walk--;
                }
            }
            story.append("]");
        }
        return story.toString();
    }

    /**
     * Лесенка от одного входа наружу.
     * <p>
     * Куда именно «наружу» — отвечает {@link #awayFrom} по чертежу.
     * Ошибись он — и ступени лягут в горнице, а порог так и останется
     * непереступимым.
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
            int ground = topSolid(world, column, walk + 1, walk - DROP - 2);

            // Стена: на два блока вверх не поднимается никто, и ломать
            // чужое крыльцо не станет. Прежний счёт молча вставал на неё
            // ногами — земля-то «не ниже чем на шаг» — и вёл остаток
            // спуска от высоты, до которой не добраться.
            if (ground + 1 > walk + 1) {
                return laid;
            }

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

    /**
     * Верхний твёрдый блок в столбце: от {@code from} и не глубже {@code floor}.
     * <p>
     * Дно у поиска не от скупости. Крыльцо всё равно не строит мостов
     * глубже {@link #DROP}, а столбец над пропастью — обычное дело на
     * склоне: без дна каждый такой столбец прочитывался до самого края
     * мира, по три с лишним сотни блоков, и всё ради ответа, который
     * тут же выбрасывается.
     */
    private static int topSolid(ServerWorld world, BlockPos column, int from, int floor) {
        int bottom = Math.max(floor, world.getBottomY() + 1);
        for (int y = from; y >= bottom; y--) {
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

}
