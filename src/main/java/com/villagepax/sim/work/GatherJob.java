package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Лесоруб: валит деревья и сдаёт добычу на склад.
 * <p>
 * Работает в двух местах по-разному — это решение заказчика:
 * <ul>
 *   <li><b>Роща у домика</b> — постоянное хозяйство. Там он валит дерево
 *       целиком, снимает крону и <b>сажает саженец обратно</b>. Лес
 *       не кончается, и окрестности не превращаются в пустырь.</li>
 *   <li><b>Дикий лес</b> в границах колонии он просто счищает, без посадки:
 *       это разовый ресурс и заодно расчистка места под стройку.</li>
 * </ul>
 * Роща — это не новое состояние, а часть схемы: грядки для деревьев есть
 * позиции, где в схеме домика стоят саженцы. То же решение, что и с кроватями.
 */
public final class GatherJob implements Job {

    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "gather");
    public static final Identifier LUMBERJACK = new Identifier(VillagePax.MOD_ID, "lumberjack");

    /** Насколько высоко тянется ствол: выше лесоруб не полезет. */
    private static final int TRUNK_HEIGHT = 16;

    /** Докуда достаёт топор от подножия ствола. */
    private static final double CHOP_REACH = 6.0;

    /** Как далеко от мастерской он ищет дикий лес. */
    public static final int WILD_RANGE = 20;

    /**
     * Пределы дерева: дальше этого связный обход не идёт.
     * <p>
     * Не украшение, а защита: без предела обход по соприкосновению уходит
     * в сомкнутый лес и валит его весь за одно решение. Тёмный дуб — самое
     * широкое ванильное дерево, его крона держится в шести блоках от ствола.
     */
    private static final int TREE_RADIUS = 8;
    private static final int TREE_HEIGHT = 32;
    private static final int TREE_BLOCKS = 512;

    /** Коробка, в которой снимается обречённая крона после последнего бревна. */
    private static final int CANOPY_RADIUS = 7;
    private static final int CANOPY_HEIGHT = 20;

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        Building hut = Workplaces.of(context.settlement(), context.citizen()).orElse(null);
        if (hut == null) {
            // Без мастерской лесорубу негде держать рощу.
            context.goIdle();
            return Optional.empty();
        }

        // Дерево ищется ровно один раз за решение: обход леса кольцами
        // всё равно дороже всего остального в этом шаге.
        List<BlockPos> grove = groveTiles(hut);
        BlockPos base = tree(context, hut, grove).orElse(null);

        if (base == null) {
            context.goIdle();
            return Optional.empty();
        }
        if (context.state().isIdle()) {
            context.setState(JobState.startAt(hut.id(), JobState.Phase.TO_SITE));
        }

        // Топор в руке всё время работы: и по дороге к дереву тоже —
        // лесоруб идёт рубить, а не гулять.
        context.hold(axe());

        if (context.position().squaredDistanceTo(Vec3d.ofCenter(base)) <= CHOP_REACH * CHOP_REACH) {
            ServerWorld world = context.world();
            BlockPos next = nextLog(world, context.settlement(), grove, base).orElse(null);

            if (next != null) {
                context.swing();
                chop(world, context.warehouse(), next);

                // Последнее бревно уносит крону с собой. Иначе над пустым
                // местом висит листва: ванильный распад идёт случайными
                // тиками и минуту, а поставленная схемой листва не распадётся
                // никогда — игрок видит недорубленное дерево.
                if (nextLog(world, context.settlement(), grove, base).isEmpty()) {
                    clearDoomedCanopy(world, context.settlement(), grove, context.warehouse(), base);
                }
            } else if (grove.contains(base)) {
                // Ствол и крона убраны. В роще на это место идёт саженец.
                replant(world, context.warehouse(), hut, base);
            }
        }

        return Optional.of(base);
    }

    /** Какое дерево валить: сперва своя роща, потом дикий лес. */
    private Optional<BlockPos> tree(WorkContext context, Building hut, List<BlockPos> grove) {
        Optional<BlockPos> inGrove = groveTree(context.world(), grove);
        return inGrove.isPresent() ? inGrove : wildTree(context, hut);
    }

    /**
     * Выросшее дерево в роще: грядка, на которой вместо саженца уже бревно.
     * Или пустая грядка, куда пора посадить.
     */
    private Optional<BlockPos> groveTree(ServerWorld world, List<BlockPos> grove) {
        BlockPos empty = null;

        for (BlockPos tile : grove) {
            BlockPos trunk = standingTrunk(world, tile);
            if (trunk != null) {
                return Optional.of(trunk);
            }

            BlockState state = world.getBlockState(tile);
            if (empty == null && state.isAir() && world.getBlockState(tile.down()).isIn(BlockTags.DIRT)) {
                empty = tile;
            }
        }
        return Optional.ofNullable(empty);
    }

    /**
     * Самое низкое бревно над грядкой, если дерево на ней ещё стоит.
     * <p>
     * Не сама грядка: срубив нижнее бревно, лесоруб оставляет над пустым
     * местом остаток ствола, и подножием следующего подхода должно стать
     * это бревно. Иначе связный обход начинается в воздухе, ему некуда идти,
     * и полдерева остаётся висеть — так и было.
     */
    private static BlockPos standingTrunk(ServerWorld world, BlockPos tile) {
        for (int dy = 0; dy <= TREE_HEIGHT; dy++) {
            BlockPos at = tile.up(dy);
            if (world.getBlockState(at).isIn(BlockTags.LOGS)) {
                return at;
            }
        }
        return null;
    }

    /** Грядки рощи — позиции саженцев из схемы домика. */
    public static List<BlockPos> groveTiles(Building hut) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(hut)).orElse(null);
        if (schematic == null) {
            return List.of();
        }

        List<BlockPos> tiles = new ArrayList<>();
        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            if (schematic.blockAt(step.paletteIndex()).isIn(BlockTags.SAPLINGS)) {
                tiles.add(BuildJob.worldPos(hut, schematic.size(), step.pos()));
            }
        }
        return tiles;
    }

    /**
     * Ближайшее дикое дерево вокруг мастерской.
     * <p>
     * Позиции внутри зданий пропускаются, и это не мелочь: без проверки
     * лесоруб разобрал бы на брёвна фахверк ратуши — она ведь тоже
     * из тёмного дуба.
     */
    private Optional<BlockPos> wildTree(WorkContext context, Building hut) {
        ServerWorld world = context.world();
        Settlement settlement = context.settlement();
        BlockPos from = hut.anchor();

        // Кольцами от мастерской, с выходом на первой же находке. Полный
        // перебор квадрата — это тысяча семьсот колонок на каждое решение
        // жителя, в главном потоке сервера; кольцами цена зависит от того,
        // как далеко ближайшее дерево, а не от того, как далеко он смотрит.
        for (int radius = 0; radius <= WILD_RANGE; radius++) {
            for (BlockPos column : ring(from, radius)) {
                if (!settlement.claims(column)) {
                    continue;
                }
                BlockPos trunk = trunkBase(world, column);
                if (trunk != null && !isInsideAnyBuilding(settlement, trunk)) {
                    return Optional.of(trunk);
                }
            }
        }
        return Optional.empty();
    }

    /** Квадратное кольцо на заданном удалении: каждая позиция ровно один раз. */
    private static List<BlockPos> ring(BlockPos centre, int radius) {
        if (radius == 0) {
            return List.of(centre);
        }

        List<BlockPos> positions = new ArrayList<>(8 * radius);
        for (int offset = -radius; offset <= radius; offset++) {
            positions.add(centre.add(offset, 0, -radius));
            positions.add(centre.add(offset, 0, radius));
        }
        for (int offset = -radius + 1; offset <= radius - 1; offset++) {
            positions.add(centre.add(-radius, 0, offset));
            positions.add(centre.add(radius, 0, offset));
        }
        return positions;
    }

    /** Подножие ствола в этой колонке, если оно там есть. */
    private static BlockPos trunkBase(ServerWorld world, BlockPos column) {
        for (int dy = -4; dy <= 4; dy++) {
            BlockPos at = column.up(dy);
            if (world.getBlockState(at).isIn(BlockTags.LOGS)
                    && !world.getBlockState(at.down()).isIn(BlockTags.LOGS)) {
                return at;
            }
        }
        return null;
    }

    private static boolean isInsideAnyBuilding(Settlement settlement, BlockPos pos) {
        for (Building building : settlement.buildings()) {
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            if (BuildSite.covers(building.anchor(), schematic.size(), building.rotation(), pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Что рубить следующим: самое низкое бревно <b>всего</b> дерева.
     * <p>
     * Снизу вверх, а не сверху вниз: до верхушки шестиметрового дерева
     * с земли не дотянуться, а брёвна в Minecraft не падают.
     * <p>
     * Дерево считается связным обходом, а не колонной над подножием. Колонна
     * оставляла ветки стоять: у дуба они отходят в сторону, у тёмного дуба
     * ствол вообще толщиной в четыре бревна. Игрок в этом случае видит
     * обрубок, а не сваленное дерево.
     */
    private static Optional<BlockPos> nextLog(ServerWorld world, Settlement settlement,
                                              List<BlockPos> grove, BlockPos base) {
        BlockPos lowest = null;

        for (BlockPos log : connectedLogs(world, settlement, grove, base)) {
            if (lowest == null || ORDER.compare(log, lowest) < 0) {
                lowest = log;
            }
        }
        return Optional.ofNullable(lowest);
    }

    /**
     * Снизу вверх, а при равной высоте — устойчиво по осям: два лесоруба
     * должны выбирать одно и то же бревно, а не топтаться по кругу.
     */
    private static final java.util.Comparator<BlockPos> ORDER =
            java.util.Comparator.comparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getZ);

    /**
     * Брёвна, связанные с подножием по соприкосновению — включая ветки
     * и вторую половину толстого ствола.
     * <p>
     * Соседство берётся по всем двадцати шести направлениям: ветки дуба
     * отходят по диагонали, и обход только по шести осям обрывался бы
     * на первой же из них.
     * <p>
     * Пределы обязательны. Без них обход уходит в сомкнутый лес: деревья
     * там соприкасаются кронами и ветками, и одно решение лесоруба сносило
     * бы гектар.
     */
    private static List<BlockPos> connectedLogs(ServerWorld world, Settlement settlement,
                                                List<BlockPos> grove, BlockPos base) {
        List<BlockPos> found = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();

        seen.add(base);
        queue.add(base);

        while (!queue.isEmpty() && found.size() < TREE_BLOCKS) {
            BlockPos at = queue.poll();
            if (isProtected(settlement, grove, at)) {
                continue;
            }

            boolean log = world.getBlockState(at).isIn(BlockTags.LOGS);

            // Подножие может быть уже срублено — с него и начинается вторая
            // ходка лесоруба. Обход, требующий бревна на старте, обрывался
            // ровно здесь: остаток ствола оставался стоять, а крона слетала
            // как «последняя». Игрок видел висящий обрубок.
            if (!log && !at.equals(base)) {
                continue;
            }
            if (log) {
                found.add(at);
            }

            for (BlockPos next : around(at)) {
                if (withinTree(base, next) && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return found;
    }

    /**
     * Что рубить нельзя: постройку.
     * <p>
     * Своя роща — исключение, и без него лесоруб не может срубить в ней
     * ничего: грядки для деревьев лежат <b>внутри следа мастерской</b>, она
     * же их и поставила. Освобождается вся колонна над грядкой — там и растёт
     * то, за чем он пришёл, — а стены и ограда остаются неприкосновенны.
     */
    private static boolean isProtected(Settlement settlement, List<BlockPos> grove, BlockPos pos) {
        return !isGroveColumn(grove, pos) && isInsideAnyBuilding(settlement, pos);
    }

    private static boolean isGroveColumn(List<BlockPos> grove, BlockPos pos) {
        for (BlockPos tile : grove) {
            if (tile.getX() == pos.getX() && tile.getZ() == pos.getZ()
                    && pos.getY() >= tile.getY()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Снять крону, оставшуюся без опоры.
     * <p>
     * Обречённой считается листва, рядом с которой не осталось ни одного
     * стоящего бревна. Ванильное свойство «расстояние до бревна» для этого
     * не годится: оно пересчитывается <b>отложенным тиком</b>, а не сразу,
     * и в тот момент, когда падает последнее бревно, там ещё стоит старое
     * значение. Проверка по свойству молча ничего не находила бы.
     * <p>
     * Ради этого же правила листва соседнего дерева остаётся на месте:
     * в роще деревья стоят через блок, кроны их соприкасаются, и без этой
     * проверки одно сваленное дерево оголяло бы три соседних.
     * <p>
     * Один проход коробкой на дерево, а не на решение: он случается ровно
     * в тот момент, когда упало последнее бревно.
     * <p>
     * Брёвна постройки опорой не считаются, и это важнее, чем кажется:
     * фахверк норманнов сложен из тёмного дуба, роща стоит вплотную к
     * мастерской, и стена в трёх блоках «держала» бы всю крону — снос
     * не срабатывал бы ровно там, где деревья и растут.
     * <p>
     * Саму листву постройки никто не защищает: листва ничего не держит,
     * и ни одна схема мода её не ставит.
     */
    private static void clearDoomedCanopy(ServerWorld world, Settlement settlement,
                                          List<BlockPos> grove, Warehouse warehouse,
                                          BlockPos base) {
        List<BlockPos> leaves = new ArrayList<>();
        java.util.Set<BlockPos> logs = new java.util.HashSet<>();

        for (int dy = -1; dy <= CANOPY_HEIGHT; dy++) {
            for (int dx = -CANOPY_RADIUS; dx <= CANOPY_RADIUS; dx++) {
                for (int dz = -CANOPY_RADIUS; dz <= CANOPY_RADIUS; dz++) {
                    BlockPos at = base.add(dx, dy, dz);
                    BlockState state = world.getBlockState(at);

                    if (state.isIn(BlockTags.LOGS)) {
                        if (!isProtected(settlement, grove, at)) {
                            logs.add(at.toImmutable());
                        }
                    } else if (state.isIn(BlockTags.LEAVES)) {
                        leaves.add(at.toImmutable());
                    }
                }
            }
        }

        // Сперва брёвна, оторванные от земли: рубка снизу вверх сама их
        // и создаёт — сняв бревно в развилке, лесоруб отрезает ветку от
        // остатка ствола, и связный обход её больше не находит. Дерево
        // соседа при этом стоит на земле и остаётся нетронутым.
        java.util.Set<BlockPos> grounded = groundedLogs(world, logs);
        for (BlockPos log : logs) {
            if (!grounded.contains(log)) {
                chop(world, warehouse, log);
            }
        }

        List<BlockPos> support = new ArrayList<>(grounded);
        for (BlockPos leaf : leaves) {
            if (!hasSupportNearby(leaf, support)) {
                chop(world, warehouse, leaf);
            }
        }
    }

    /**
     * Брёвна, которые на чём-то стоят: те, под которыми не воздух, и всё,
     * что с ними связано.
     * <p>
     * Ровно этим отличается ветка, отрезанная самим лесорубом, от целого
     * дерева соседа: у первой опоры нет, у второго — земля под стволом.
     */
    private static java.util.Set<BlockPos> groundedLogs(ServerWorld world,
                                                        java.util.Set<BlockPos> logs) {
        java.util.Set<BlockPos> grounded = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();

        for (BlockPos log : logs) {
            BlockPos below = log.down();
            if (!logs.contains(below) && !world.getBlockState(below).isAir()) {
                grounded.add(log);
                queue.add(log);
            }
        }

        while (!queue.isEmpty()) {
            for (BlockPos next : around(queue.poll())) {
                if (logs.contains(next) && grounded.add(next)) {
                    queue.add(next);
                }
            }
        }
        return grounded;
    }

    /**
     * Держится ли листва на чём-нибудь. Ванильная листва живёт не дальше
     * {@link LeavesBlock#MAX_DISTANCE} от бревна, и это тот же предел.
     */
    private static boolean hasSupportNearby(BlockPos leaf, List<BlockPos> logs) {
        for (BlockPos log : logs) {
            if (Math.abs(log.getX() - leaf.getX()) <= LeavesBlock.MAX_DISTANCE
                    && Math.abs(log.getY() - leaf.getY()) <= LeavesBlock.MAX_DISTANCE
                    && Math.abs(log.getZ() - leaf.getZ()) <= LeavesBlock.MAX_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static boolean withinTree(BlockPos base, BlockPos pos) {
        int dy = pos.getY() - base.getY();
        return dy >= -1 && dy <= TREE_HEIGHT
                && Math.abs(pos.getX() - base.getX()) <= TREE_RADIUS
                && Math.abs(pos.getZ() - base.getZ()) <= TREE_RADIUS;
    }

    /** Все двадцать шесть соседей: ветки отходят и по диагонали. */
    private static List<BlockPos> around(BlockPos at) {
        List<BlockPos> neighbours = new ArrayList<>(26);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        neighbours.add(at.add(dx, dy, dz));
                    }
                }
            }
        }
        return neighbours;
    }

    private static void chop(ServerWorld world, Warehouse warehouse, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        for (ItemStack drop : Block.getDroppedStacks(state, world, pos, null, null, axe())) {
            if (!drop.isEmpty()) {
                warehouse.addOrScatter(world, pos, drop);
            }
        }
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    /**
     * Посадить обратно. Саженец берётся со склада — того самого, куда упала
     * листва: колония живёт своим кругооборотом, а не подарками.
     */
    private static void replant(ServerWorld world, Warehouse warehouse, Building hut, BlockPos tile) {
        BlockState sapling = groveSapling(hut).orElse(null);
        if (sapling == null) {
            return;
        }

        Item seed = sapling.getBlock().asItem();
        if (seed == Items.AIR || !warehouse.take(seed, 1)) {
            return;
        }
        world.setBlockState(tile, sapling, Block.NOTIFY_ALL);
    }

    /** Какой саженец растёт в этой роще — из схемы, а не из догадки. */
    private static Optional<BlockState> groveSapling(Building hut) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(hut)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }
        for (BlockState state : schematic.palette()) {
            if (state.isIn(BlockTags.SAPLINGS)) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }

    /** Топор нужен для правильной добычи: без него листва не даёт саженцев. */
    private static ItemStack axe() {
        return new ItemStack(Items.IRON_AXE);
    }
}
