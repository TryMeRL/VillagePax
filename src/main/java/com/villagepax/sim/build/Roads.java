package com.villagepax.sim.build;

import com.villagepax.core.ModTags;
import com.villagepax.core.config.Configs;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Sounds;
import com.villagepax.sim.Warehouse;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Улицы колонии: билдер мостит дорожки от дверей к площади у ратуши.
 * <p>
 * Решение заказчика — мостит <b>сам</b>, без приказа игрока: деревня должна
 * становиться деревней, а не набором домов на траве. Ходят жители по улицам
 * тоже сами: у шага вне дороги есть надбавка к стоимости пути
 * ({@code CitizenPathNodeMaker}), и путь ложится на мостовую, как только
 * она появилась.
 * <p>
 * <b>Улица не хранится.</b> Ни поля в поселении, ни списка тайлов, ни кодека:
 * маршрут считается заново от двери к центру, а «замощено ли» спрашивается
 * у самого мира. Так улица переживает перезаход, чинится, если её раскопали,
 * и не может разойтись с тем, что игрок видит под ногами — расхождение
 * счётчика с миром здесь просто нечему устроить. Это то же решение, на
 * котором стоит склад колонии.
 * <p>
 * Мостят из <b>излишков</b>: пока на складе не больше {@link #RESERVE} штук
 * материала, улица довольствуется натоптанной тропой. Иначе дорожка молча
 * съедала бы булыжник, отложенный игроком на цоколь следующего дома.
 */
public final class Roads {

    /**
     * Дальше этого улицу не тянут.
     * <p>
     * Дом на другом конце карты соединять с ратушей незачем: дорожка вышла
     * бы длиннее самой колонии, а стоит она обходом столько же блоков.
     */
    public static final int MAX_LENGTH = 48;

    /**
     * Сколько материала остаётся неприкосновенным.
     * <p>
     * Улица — украшение, а стройка — дело: пока на складе меньше запаса,
     * булыжник и гравий копятся для стен, а житель топчет тропу.
     * <p>
     * Число живёт в настройках, а не константой рядом: это из тех величин,
     * которые игрок захочет крутить, и двух источников правды у неё быть
     * не должно.
     */
    public static int reserve() {
        return Configs.get().roadReserve();
    }

    /**
     * Насколько круто улица идёт в гору.
     * <p>
     * Дорожка следует рельефу шаг за шагом от предыдущего тайла, а не по
     * высоте поверхности: иначе она забралась бы на скалу или на крышу,
     * и житель полез бы за ней.
     */
    private static final int CLIMB = 2;

    /** Порядок поиска опоры: сначала ровно, потом вниз, потом вверх. */
    private static final int[] LEVELS = {0, -1, 1, -2, 2};

    private Roads() {
    }

    /**
     * Чем колония мостит прямо сейчас.
     * <p>
     * Порядок предпочтения задаёт культура — данными, как и всё остальное.
     * Если ничего из списка на складе нет с запасом, житель топчет тропу:
     * {@link Blocks#DIRT_PATH} не стоит материала, ровно как удар лопатой
     * по траве у игрока. Поэтому улицы появляются и у самой бедной колонии,
     * а разбогатевшая перекладывает их камнем.
     */
    public static Block paving(Settlement colony, Warehouse warehouse) {
        Culture culture = CultureManager.get(colony.culture());
        if (culture != null) {
            for (Identifier id : culture.road()) {
                Block block = Registries.BLOCK.get(id);
                Item material = block.asItem();
                if (block != Blocks.AIR && material != Items.AIR
                        && warehouse.count(material) > reserve()) {
                    return block;
                }
            }
        }
        return Blocks.DIRT_PATH;
    }

    /** Что билдер держит в руке, пока мостит. */
    public static ItemStack inHand(Block paving) {
        if (paving == Blocks.DIRT_PATH) {
            // Тропу топчут лопатой — тем же движением, что и игрок.
            return new ItemStack(Items.IRON_SHOVEL);
        }
        Item material = paving.asItem();
        return material == Items.AIR ? ItemStack.EMPTY : new ItemStack(material);
    }

    /**
     * Следующий тайл улицы этого здания, которым надо заняться, или пусто,
     * если улица готова.
     *
     * @param reachable отбор по достижимости: до тайла, к которому житель
     *                  так и не смог подойти, улица не встаёт — она идёт
     *                  дальше, а не замирает на нём навсегда
     */
    public static Optional<BlockPos> nextTile(ServerWorld world, Settlement colony, Building building,
                                              Block paving, Predicate<BlockPos> reachable) {
        for (BlockPos ground : route(world, colony, building)) {
            BlockState state = world.getBlockState(ground);
            if (needsPaving(state, paving) && canPave(state, paving) && reachable.test(ground)) {
                return Optional.of(ground);
            }
        }
        return Optional.empty();
    }

    /**
     * Замостить один тайл. Ложь значит «материал кончился между решением
     * и делом» — следующее решение выберет, чем мостить, заново.
     * <p>
     * Снятый грунт уходит на склад: колония не должна терять землю молча,
     * это то же правило, по которому расчистка под стройку приносит брёвна.
     * У натоптанной тропы возврата нет — трава под лопатой не даёт ничего
     * и в ванили.
     */
    public static boolean pave(ServerWorld world, Warehouse warehouse, BlockPos ground, Block paving) {
        if (paving != Blocks.DIRT_PATH) {
            Item material = paving.asItem();
            if (material == Items.AIR || !warehouse.take(material, 1)) {
                return false;
            }
            salvage(world, warehouse, ground);
        }

        BlockState laid = paving.getDefaultState();
        world.setBlockState(ground, laid, Block.NOTIFY_ALL);
        Sounds.placed(world, ground, laid);
        return true;
    }

    private static void salvage(ServerWorld world, Warehouse warehouse, BlockPos ground) {
        BlockState existing = world.getBlockState(ground);
        for (ItemStack drop : Block.getDroppedStacks(existing, world, ground, null, null,
                new ItemStack(Items.IRON_SHOVEL))) {
            if (!drop.isEmpty()) {
                warehouse.addOrScatter(world, ground, drop);
            }
        }
    }

    /**
     * Надо ли трогать этот блок под улицу.
     * <p>
     * Уже замощённое тем же материалом — готово. Натоптанная тропа считается
     * недоделанной, если колония доросла до камня: так разбогатевшая деревня
     * перекладывает свои дорожки, и это видно. Обратно не переложит —
     * камня в {@link ModTags#PAVABLE} нет, и мостовая для улицы окончательна.
     */
    private static boolean needsPaving(BlockState state, Block paving) {
        if (state.isOf(paving)) {
            return false;
        }
        return state.isIn(ModTags.PAVABLE) || state.isOf(Blocks.DIRT_PATH);
    }

    /** Тропу топчут только по настоящей земле: из песка тропинки не выйдет. */
    private static boolean canPave(BlockState ground, Block paving) {
        return paving != Blocks.DIRT_PATH || ground.isIn(BlockTags.DIRT);
    }

    /**
     * Маршрут улицы: от двери здания к центру колонии, по земле.
     * <p>
     * Звездой от ратуши, а не сетью между домами: у хутора из пяти зданий
     * это и есть площадь с расходящимися улицами, а сеть потребовала бы
     * хранить графы и объяснять игроку, почему дорожка пошла вот так.
     */
    private static List<BlockPos> route(ServerWorld world, Settlement colony, Building building) {
        BlockPos door = door(colony, building).orElse(null);
        if (door == null) {
            return List.of();
        }

        List<Footprint> footprints = footprints(colony);
        List<BlockPos> tiles = new ArrayList<>();
        int height = door.getY() - 1;

        for (BlockPos column : line(door, colony.center())) {
            // Внутри зданий не мостят. Без этого улица прошла бы прямо
            // по грядкам фермы и по земле рощи лесоруба: там под ногами
            // тот же грунт, что и на лугу.
            if (inside(footprints, column)) {
                continue;
            }

            BlockPos ground = ground(world, column.getX(), column.getZ(), height);
            if (ground == null) {
                // Стена, вода, обрыв — улица здесь прерывается и идёт дальше.
                continue;
            }
            height = ground.getY();
            tiles.add(ground);
        }
        return tiles;
    }

    /**
     * Опора в колонне: высота, на которой можно стоять, ближайшая к тому
     * уровню, на котором улица шла до сих пор.
     */
    private static BlockPos ground(ServerWorld world, int x, int z, int height) {
        for (int level : LEVELS) {
            if (Math.abs(level) > CLIMB) {
                continue;
            }
            BlockPos at = new BlockPos(x, height + level, z);
            if (isGround(world, at)) {
                return at;
            }
        }
        return null;
    }

    /**
     * Земля улицы — то, по чему можно идти. Над ней должно быть пусто:
     * так улица не полезет под стену дома и не станет мостить пол сарая.
     */
    private static boolean isGround(ServerWorld world, BlockPos at) {
        BlockState above = world.getBlockState(at.up());
        if (!above.getFluidState().isEmpty()) {
            // Брод не мостим: гравий под водой улицей не выглядит,
            // и житель по нему всё равно не пойдёт.
            return false;
        }
        if (!above.isAir() && !above.isReplaceable()) {
            return false;
        }

        BlockState state = world.getBlockState(at);
        return state.isIn(ModTags.PAVABLE) || state.isIn(ModTags.PREFERRED_PATH);
    }

    /**
     * Дверь, от которой пойдёт улица, — та, что ближе к ратуше. У здания
     * их может быть несколько: у фермы это калитка ограды.
     */
    private static Optional<BlockPos> door(Settlement colony, Building building) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }
        return BuildJob.pointsOfInterest(building, schematic, MarkerKind.DOOR).stream()
                .min(Comparator.comparingDouble(door -> door.getSquaredDistance(colony.center())));
    }

    /** След здания на земле — прямоугольник, по которому улица не идёт. */
    private record Footprint(int minX, int minZ, int maxX, int maxZ) {

        boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    private static List<Footprint> footprints(Settlement colony) {
        List<Footprint> footprints = new ArrayList<>();

        for (Building building : colony.buildings()) {
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
            BlockPos anchor = building.anchor();
            footprints.add(new Footprint(anchor.getX(), anchor.getZ(),
                    anchor.getX() + size.getX() - 1, anchor.getZ() + size.getZ() - 1));
        }
        return footprints;
    }

    private static boolean inside(List<Footprint> footprints, BlockPos column) {
        for (Footprint footprint : footprints) {
            if (footprint.contains(column)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Колонны от двери до центра: целочисленная линия, без разрывов по
     * диагонали. Слишком далёкая цель улицы не даёт вовсе.
     */
    private static List<BlockPos> line(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));

        if (steps == 0 || steps > MAX_LENGTH) {
            return List.of();
        }

        List<BlockPos> columns = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            columns.add(new BlockPos(
                    from.getX() + Math.round((float) dx * step / steps),
                    from.getY(),
                    from.getZ() + Math.round((float) dz * step / steps)));
        }
        return columns;
    }
}
