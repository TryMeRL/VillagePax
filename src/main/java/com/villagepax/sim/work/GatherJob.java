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

    /** Полукоробка вокруг ствола, в которой снимается крона. */
    private static final int CROWN_RADIUS = 3;
    private static final int CROWN_HEIGHT = 10;

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

        if (context.position().squaredDistanceTo(Vec3d.ofCenter(base)) <= CHOP_REACH * CHOP_REACH) {
            ServerWorld world = context.world();
            BlockPos next = nextBlockToChop(world, base).orElse(null);

            if (next != null) {
                chop(world, context.warehouse(), next);
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
            BlockState state = world.getBlockState(tile);
            if (state.isIn(BlockTags.LOGS)) {
                return Optional.of(tile);
            }
            if (empty == null && state.isAir() && world.getBlockState(tile.down()).isIn(BlockTags.DIRT)) {
                empty = tile;
            }
        }
        return Optional.ofNullable(empty);
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
     * Что рубить следующим: самое низкое бревно ствола, затем крона.
     * <p>
     * Снизу вверх, а не сверху вниз: до верхушки шестиметрового дерева
     * с земли не дотянуться, а брёвна в Minecraft не падают. Крона снимается
     * после ствола — и не ради красоты: из листвы падают саженцы, а без них
     * роща не возобновится.
     */
    private static Optional<BlockPos> nextBlockToChop(ServerWorld world, BlockPos base) {
        for (int dy = 0; dy < TRUNK_HEIGHT; dy++) {
            BlockPos at = base.up(dy);
            if (world.getBlockState(at).isIn(BlockTags.LOGS)) {
                return Optional.of(at);
            }
        }

        for (int dy = 0; dy < CROWN_HEIGHT; dy++) {
            for (int dx = -CROWN_RADIUS; dx <= CROWN_RADIUS; dx++) {
                for (int dz = -CROWN_RADIUS; dz <= CROWN_RADIUS; dz++) {
                    BlockPos at = base.add(dx, dy, dz);
                    if (world.getBlockState(at).isIn(BlockTags.LEAVES)) {
                        return Optional.of(at);
                    }
                }
            }
        }
        return Optional.empty();
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
