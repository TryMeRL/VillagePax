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
import net.minecraft.util.math.Direction;
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
 * Мостят из <b>излишков</b>: пока на складе не больше {@link #reserve()} штук
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
     * <b>Один блок</b>, а не два, и это не придирка к виду. Через ступень
     * в два блока житель не перелезет вовсе: прыжок берёт один. Вниз
     * такая лестница ещё вела, наверх — уже нет, и дом на полке оставался
     * недоступным ровно наполовину. Игрок сказал об этом прямо: «не пройти
     * ни к зданиям, ни к фермам».
     * <p>
     * Всё, что круче, теперь досыпается ступенями — см. {@link #step}.
     * <p>
     * Дорожка следует рельефу шаг за шагом от предыдущего тайла, а не по
     * высоте поверхности: иначе она забралась бы на скалу или на крышу,
     * и житель полез бы за ней.
     */
    private static final int CLIMB = 1;

    /**
     * Насколько глубоко улица досыпает ступень.
     * <p>
     * Два блока — это уступ, три и больше — обрыв. Дорожка, ныряющая
     * в пропасть насыпью, перестала бы быть дорожкой и стала бы мостом,
     * а мост — отдельная работа и отдельное решение.
     */
    private static final int FILL_DEPTH = 2;

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
            if (needsWork(world, colony, ground, paving) && reachable.test(ground)) {
                return Optional.of(ground);
            }
        }
        return Optional.empty();
    }

    /**
     * Осталась ли работа на этой клетке улицы.
     * <p>
     * На поверхности вопрос один — тот ли под ногами блок. В горе их два:
     * <b>пол и свод</b>. Галерея, у которой пол выложен, а над ним всё ещё
     * камень, — это не улица, а замурованная плита; спрашивать только
     * о поле значило бы объявлять готовым ход, по которому не пройти.
     */
    private static boolean needsWork(ServerWorld world, Settlement colony, BlockPos ground,
                                     Block paving) {
        Footing footing = Footing.of(colony);
        if (footing == Footing.CANOPY && paving == Blocks.DIRT_PATH) {
            // Моста из натоптанной тропы не бывает. У чертога бедная улица
            // это голый вырубленный камень — по нему ходят; у крон под
            // ногами воздух, и пока на складе нет настила, моста нет.
            // Честнее не строить, чем строить из ничего.
            return false;
        }
        if (footing.keepsOneLevel()) {
            return !isClearAbove(world, ground) || (paving != Blocks.DIRT_PATH
                    && !world.getBlockState(ground).isOf(paving));
        }
        BlockState state = world.getBlockState(ground);
        return needsPaving(state, paving) && canPave(state, paving);
    }

    /** Свободно ли над этой клеткой в рост: свод галереи, просвет моста. */
    private static boolean isClearAbove(ServerWorld world, BlockPos ground) {
        for (int up = 1; up <= Hold.HEADROOM; up++) {
            if (!isFree(world, ground.up(up))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Освободить проход над клеткой: свод в горе, просвет в кронах.
     * <p>
     * Снятое уходит на склад — тем же правилом, каким расчистка под стройку
     * приносит брёвна. У гномов из этого выходит следствие, которого нет
     * ни у кого другого: <b>прокладка улиц их обогащает</b>. Гора платит
     * за то, что в ней роют. У эльфов то же движение отдаёт листву,
     * и это уже не богатство, а просто уборка — но правило одно,
     * и разных правил для разных народов здесь нет.
     */
    private static void clearAbove(ServerWorld world, Warehouse warehouse, BlockPos ground) {
        for (int up = 1; up <= Hold.HEADROOM; up++) {
            BlockPos cell = ground.up(up);
            if (isFree(world, cell)) {
                continue;
            }
            salvage(world, warehouse, cell);
            world.setBlockState(cell, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
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
    public static boolean pave(ServerWorld world, Settlement colony, Warehouse warehouse,
                               BlockPos ground, Block paving) {
        Footing footing = Footing.of(colony);
        if (footing.keepsOneLevel()) {
            // Проход первым: галерея без свода — замурованная плита,
            // мост без просвета — настил под ветками. И только потом пол,
            // если колонии есть чем его выложить; нет — гномы ходят
            // по вырубленному камню, и это не бедность, а порода.
            clearAbove(world, warehouse, ground);
            if (paving == Blocks.DIRT_PATH) {
                return footing == Footing.HOLD;
            }
        }
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

        // Ступень на уступе кладётся в пустоту, и под ней тоже пусто:
        // досыпаем опору, иначе по такой улице не пройти — под ней дыра.
        //
        // Но только там, где улица лежит на земле. Под галереей чертога
        // и так сплошной камень, а под мостом в кронах пустота <b>по
        // замыслу</b>: досыпка вывесила бы под каждой доской моста
        // по два блока земли, и лес под деревней зарос бы сталактитами.
        if (footing.levelsTheGround()) {
            fillUnder(world, warehouse, ground, paving);
        }
        return true;
    }

    /**
     * Досыпать опору под только что положенной клеткой улицы.
     * <p>
     * Молча и не глубже {@link #FILL_DEPTH}: клетку назначал {@link #step},
     * и он уже убедился, что твёрдое дно близко. Кончился материал —
     * останавливаемся: незаконченная насыпь лучше, чем улица, съевшая
     * весь склад.
     */
    private static void fillUnder(ServerWorld world, Warehouse warehouse, BlockPos ground,
                                  Block paving) {
        Item material = paving.asItem();
        for (int depth = 1; depth <= FILL_DEPTH; depth++) {
            BlockPos under = ground.down(depth);
            if (world.getBlockState(under).isSolidBlock(world, under)) {
                return;
            }
            // Запас неприкосновенен и здесь: ступень — та же улица,
            // а улица ждёт излишков.
            if (paving != Blocks.DIRT_PATH
                    && (material == Items.AIR || warehouse.count(material) <= reserve()
                    || !warehouse.take(material, 1))) {
                return;
            }
            BlockState laid = paving == Blocks.DIRT_PATH
                    ? Blocks.DIRT.getDefaultState() : paving.getDefaultState();
            world.setBlockState(under, laid, Block.NOTIFY_ALL);
        }
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
        // Пустая клетка — это назначенная ступень: её не перекладывают,
        // а досыпают. Для тропы такого не бывает: воздух не вытопчешь.
        if (state.isAir() || state.isReplaceable()) {
            return paving != Blocks.DIRT_PATH;
        }
        return state.isIn(ModTags.PAVABLE) || state.isOf(Blocks.DIRT_PATH);
    }

    /** Тропу топчут только по настоящей земле: из песка тропинки не выйдет. */
    private static boolean canPave(BlockState ground, Block paving) {
        if (ground.isAir() || ground.isReplaceable()) {
            return paving != Blocks.DIRT_PATH;
        }
        return paving != Blocks.DIRT_PATH || ground.isIn(BlockTags.DIRT);
    }

    /**
     * Колонны улиц, которые уже лежат в мире: замощённые или вытоптанные.
     * <p>
     * Нужно разметке: мостовая для неё была травой, и новый дом ложился
     * поперёк улицы соседа, а та обрывалась у его стены. Считаются только
     * уже положенные тайлы — будущая улица сама обойдёт новый след, а
     * настоящую ломать нельзя. Улица по-прежнему не хранится: её тайлы
     * спрашиваются у мира, как и везде в этом классе.
     *
     * @return колонны в виде {@code BlockPos.asLong(x, 0, z)}
     */
    public static java.util.Set<Long> pavedColumns(ServerWorld world, Settlement colony) {
        java.util.Set<Long> paved = new java.util.HashSet<>();
        if (Footing.of(colony).keepsOneLevel()) {
            // Галерею и мост разметка и так не накроет: их тайлы лежат
            // на полу поселения, а новый зал вырубается рядом с ними.
            return paved;
        }
        java.util.Set<Block> street = new java.util.HashSet<>();
        street.add(Blocks.DIRT_PATH);
        Culture culture = CultureManager.get(colony.culture());
        if (culture != null) {
            culture.road().forEach(id -> street.add(Registries.BLOCK.get(id)));
        }
        for (Building building : colony.buildings()) {
            for (BlockPos tile : route(world, colony, building)) {
                if (street.contains(world.getBlockState(tile).getBlock())) {
                    paved.add(BlockPos.asLong(tile.getX(), 0, tile.getZ()));
                }
            }
        }
        return paved;
    }

    /**
     * Маршрут улицы: от порога здания к центру колонии, по земле.
     * <p>
     * Открыт наружу для приёмки: «улица идёт от двери и не рвётся» — это
     * обещание мода, и проверять его надо прямо, а не угадывая координаты
     * в тесте.
     * <p>
     * Звездой от ратуши, а не сетью между домами: у хутора из пяти зданий
     * это и есть площадь с расходящимися улицами, а сеть потребовала бы
     * хранить графы и объяснять игроку, почему дорожка пошла вот так.
     */
    public static List<BlockPos> route(ServerWorld world, Settlement colony, Building building) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
        BlockPos door = door(colony, building).orElse(null);
        if (schematic == null || door == null) {
            return List.of();
        }

        if (Footing.of(colony).keepsOneLevel()) {
            // В горе и в кронах прямая улице не годится: по диагонали меж
            // углов не пройти ни штольней, ни мостом, а середина поселения —
            // стена ратуши, а не её дверь. Путь ищется в обход (см. Galleries).
            List<BlockPos> gallery = Galleries.path(colony, building);
            if (!gallery.isEmpty()) {
                return gallery;
            }
        }

        List<Footprint> footprints = footprints(colony);
        List<BlockPos> tiles = new ArrayList<>();
        int height = door.getY() - 1;

        // Улица начинается с порога — с тайла наружу от двери, — а не
        // от самой двери. Дверь стоит в стене, её колонна лежит внутри
        // следа и мостить её нельзя; улица от этого начиналась там, где
        // линия впервые выходила из-под здания, то есть сбоку от входа.
        // Игрок и сказал: пусть пути ведут от двери.
        BlockPos doorstep = doorstep(building, schematic, door);
        if (!world.isChunkLoaded(doorstep.getX() >> 4, doorstep.getZ() >> 4)) {
            // Здание за краем видимого: его улицу спросят, когда туда придут.
            // Считать её сейчас значило бы грузить чанки ради ответа,
            // который никому не нужен.
            return List.of();
        }

        if (!Footing.of(colony).keepsOneLevel()) {
            // Путь ищется, а прямая осталась запасной — на случай, когда
            // обхода нет вовсе. Она хуже, но не хуже, чем было.
            int threshold = height;
            List<BlockPos> found = remembered(world, colony, building,
                    () -> search(world, colony, doorstep, threshold, footprints));
            if (!found.isEmpty()) {
                return found;
            }
        }

        for (BlockPos column : line(doorstep, colony.center())) {
            // Внутри зданий не мостят. Без этого улица прошла бы прямо
            // по грядкам фермы и по земле рощи лесоруба: там под ногами
            // тот же грунт, что и на лугу.
            if (inside(footprints, column)) {
                continue;
            }

            if (Footing.of(colony).keepsOneLevel()) {
                // Ни в горе, ни в кронах улица не ищет землю и не спускается
                // уступами: пол там один на всё поселение. Камень на пути —
                // это не стена, а ещё не прорубленная галерея; пустота
                // на пути — не пропасть, а ещё не настланный мост.
                tiles.add(new BlockPos(column.getX(), height, column.getZ()));
                continue;
            }

            BlockPos ground = ground(world, column.getX(), column.getZ(), height);
            if (ground == null) {
                // Земли на ходовой высоте нет — значит уступ. Не бросаем
                // улицу, а назначаем ступень на блок ниже: её билдер
                // досыплет. Так дорожка спускается с холма лестницей,
                // а не обрывается на полпути, оставляя дом недоступным.
                ground = step(world, column.getX(), column.getZ(), height);
            }
            if (ground == null) {
                // Стена, вода, пропасть — улица здесь прерывается и идёт дальше.
                continue;
            }
            height = ground.getY();
            tiles.add(ground);
        }
        return tiles;
    }

    /**
     * На сколько клеток улица может уйти вбок от прямой, обходя дома.
     * <p>
     * Двенадцать — это ширина дома с зазорами по обе стороны: обойти
     * соседа хватает, а петли через полдеревни поиск не заложит.
     */
    private static final int DETOUR = 12;

    /** Сколько тайлов поиск осматривает, прежде чем сдаться и вернуть прямую. */
    private static final int SEARCH_LIMIT = 4000;

    /**
     * Цена шага по уже положенной улице — меньше шага по траве.
     * <p>
     * От этого улицы <b>сходятся</b>: вторая дорожка к ратуше вливается
     * в первую, как только та рядом, и деревня получает улицу, а не
     * пучок параллельных тропинок. И маршрут не пляшет от решения
     * к решению: замощённое дешевле, поэтому путь держится за него.
     */
    private static final double PAVED_STEP = 0.6;

    /** Шаг вверх или вниз: блок перепада стоит как блок пути. */
    private static final double CLIMB_COST = 1.0;

    /** Ступень, которую придётся досыпать: земляная работа, а не просто шаг. */
    private static final double FILL_COST = 1.5;

    /** Поворот: без него путь выходит ломаной лесенкой, а улица — прямыми пролётами. */
    private static final double TURN_COST = 0.4;

    /** Сколько тиков маршрут помнится: улица не меняется от решения к решению. */
    private static final int REMEMBER_TICKS = 200;

    private static final Direction[] WAYS = {Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST};

    /** Шаг поиска: тайл, откуда в него пришли, во что обошлось и куда смотрели. */
    private record Node(BlockPos tile, Node parent, double cost, Direction heading) {
    }

    /** Очередь поиска: порядок постановки разводит ничьи одинаково каждый раз. */
    private record Open(Node node, double estimate, long order) {
    }

    /** Запомненный маршрут: до какого тика и при скольких зданиях он верен. */
    private record Remembered(long until, int buildings, List<BlockPos> tiles) {
    }

    /**
     * Недавние маршруты. Их спрашивают билдер на каждом решении, разметка
     * и откос, и все они хотят один и тот же ответ: улица не хранится,
     * но и считать её заново каждые полсекунды незачем.
     */
    private static final java.util.Map<java.util.UUID, Remembered> ROUTES =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<java.util.UUID, Remembered> eldest) {
                    return size() > 512;
                }
            };

    /**
     * Маршрут из памяти, если он свежий, иначе — найденный заново.
     * <p>
     * Новое здание сбрасывает память сразу: оно могло встать поперёк
     * старой улицы, и та обязана пойти в обход.
     */
    private static List<BlockPos> remembered(ServerWorld world, Settlement colony, Building building,
                                             java.util.function.Supplier<List<BlockPos>> search) {
        long now = world.getTime();
        int buildings = colony.buildings().size();
        Remembered known = ROUTES.get(building.id());
        if (known != null && known.until() >= now && known.until() - REMEMBER_TICKS <= now
                && known.buildings() == buildings) {
            return known.tiles();
        }
        List<BlockPos> tiles = List.copyOf(search.get());
        ROUTES.put(building.id(), new Remembered(now + REMEMBER_TICKS, buildings, tiles));
        return tiles;
    }

    /**
     * Путь улицы по земле: от порога к ратуше, в обход домов и убранства.
     * <p>
     * Прежде улица была прямой: клетки под чужим следом пропускались,
     * и дорожка обрывалась у одной стены дома, чтобы продолжиться у другой.
     * Житель шёл по ней до стены и искал обход сам. Теперь путь ищется
     * поиском A* по тем же правилам шага, что были у прямой: тайл земли
     * на блок выше или ниже, или ступень на блок ниже с досыпкой. Путь
     * идёт сторонами клеток — по диагонали меж двух углов не проходит
     * ни житель, ни тележка.
     *
     * @return тайлы от порога до площади, или пусто, если обхода не нашлось
     */
    private static List<BlockPos> search(ServerWorld world, Settlement colony, BlockPos from,
                                         int height, List<Footprint> footprints) {
        BlockPos centre = colony.center();
        if (Math.max(Math.abs(from.getX() - centre.getX()),
                Math.abs(from.getZ() - centre.getZ())) > MAX_LENGTH) {
            return List.of();
        }
        BlockPos first = tileAt(world, from.getX(), from.getZ(), height);
        if (first == null || inside(footprints, first)) {
            return List.of();
        }

        Footprint goal = plaza(colony);
        java.util.Set<Long> decor = decorColumns(world, colony);
        int minX = Math.min(from.getX(), centre.getX()) - DETOUR;
        int maxX = Math.max(from.getX(), centre.getX()) + DETOUR;
        int minZ = Math.min(from.getZ(), centre.getZ()) - DETOUR;
        int maxZ = Math.max(from.getZ(), centre.getZ()) + DETOUR;

        java.util.PriorityQueue<Open> open = new java.util.PriorityQueue<>(
                Comparator.comparingDouble(Open::estimate).thenComparingLong(Open::order));
        java.util.Map<BlockPos, Double> best = new java.util.HashMap<>();
        long order = 0;
        open.add(new Open(new Node(first, null, 0, null), remaining(first, goal), order++));
        best.put(first, 0.0);

        int looked = 0;
        while (!open.isEmpty() && looked++ < SEARCH_LIMIT) {
            Node node = open.poll().node();
            if (node.cost() > best.getOrDefault(node.tile(), Double.MAX_VALUE) + 1e-9) {
                continue;
            }
            if (goal.contains(node.tile())) {
                List<BlockPos> tiles = new ArrayList<>();
                for (Node step = node; step != null; step = step.parent()) {
                    tiles.add(step.tile());
                }
                java.util.Collections.reverse(tiles);
                return tiles;
            }
            for (Direction way : WAYS) {
                int x = node.tile().getX() + way.getOffsetX();
                int z = node.tile().getZ() + way.getOffsetZ();
                if (x < minX || x > maxX || z < minZ || z > maxZ
                        || decor.contains(BlockPos.asLong(x, 0, z))
                        // Спросить блок в выгруженном чанке значит заставить
                        // мир загрузить его здесь и сейчас, посреди тика. Прямая
                        // шла от двери к ратуше и почти не выходила за видимое,
                        // а поиск заглядывает на двенадцать клеток вбок.
                        || !world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos next = tileAt(world, x, z, node.tile().getY());
                if (next == null || inside(footprints, next)) {
                    continue;
                }
                double cost = node.cost() + stepCost(world, node, next, way);
                if (cost < best.getOrDefault(next, Double.MAX_VALUE) - 1e-9) {
                    best.put(next, cost);
                    open.add(new Open(new Node(next, node, cost, way),
                            cost + remaining(next, goal), order++));
                }
            }
        }
        return List.of();
    }

    /** Тайл улицы в колонне: земля на ходовой высоте или ступень ниже. */
    private static BlockPos tileAt(ServerWorld world, int x, int z, int height) {
        BlockPos ground = ground(world, x, z, height);
        return ground != null ? ground : step(world, x, z, height);
    }

    /** Во что обходится шаг на соседний тайл. */
    private static double stepCost(ServerWorld world, Node from, BlockPos next, Direction way) {
        BlockState state = world.getBlockState(next);
        double cost = state.isIn(ModTags.PREFERRED_PATH) ? PAVED_STEP : 1.0;
        if (next.getY() != from.tile().getY()) {
            cost += CLIMB_COST;
        }
        if (isFree(world, next)) {
            cost += FILL_COST;
        }
        if (from.heading() != null && from.heading() != way) {
            cost += TURN_COST;
        }
        return cost;
    }

    /** Сколько ещё идти до площади — не больше, чем на самом деле. */
    private static double remaining(BlockPos tile, Footprint goal) {
        int dx = Math.max(0, Math.max(goal.minX() - tile.getX(), tile.getX() - goal.maxX()));
        int dz = Math.max(0, Math.max(goal.minZ() - tile.getZ(), tile.getZ() - goal.maxZ()));
        return PAVED_STEP * (dx + dz);
    }

    /**
     * Куда улица ведёт: к ратуше вплотную — под самые стены её следа,
     * а если зала нет, к блоку ратуши в середине.
     * <p>
     * Прямая прежде упиралась ровно туда же: клетки под следом ратуши
     * пропускались, и улица кончалась у её края.
     */
    private static Footprint plaza(Settlement colony) {
        for (Building hall : colony.buildings()) {
            if (!com.villagepax.core.building.BuildingTypes.isTownHall(hall.type())) {
                continue;
            }
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(hall)).orElse(null);
            if (schematic == null) {
                continue;
            }
            Vec3i size = BuildSite.rotatedSize(schematic.size(), hall.rotation());
            BlockPos a = hall.anchor();
            return new Footprint(a.getX() - 1, a.getZ() - 1,
                    a.getX() + size.getX(), a.getZ() + size.getZ());
        }
        BlockPos centre = colony.center();
        return new Footprint(centre.getX() - 1, centre.getZ() - 1,
                centre.getX() + 1, centre.getZ() + 1);
    }

    /** Колонны убранства, через которые улица не идёт: сруб колодца, столб, коновязь. */
    private static java.util.Set<Long> decorColumns(ServerWorld world, Settlement colony) {
        java.util.Set<Long> columns = new java.util.HashSet<>();
        for (BlockPos at : com.villagepax.sim.SettlementManager.get(world).decorOf(colony.id())) {
            if (!world.isChunkLoaded(at.getX() >> 4, at.getZ() >> 4)) {
                // В выгруженный чанк улица и так не пойдёт — поиск его обходит.
                continue;
            }
            BlockState state = world.getBlockState(at);
            if (!state.isReplaceable() && !state.isIn(BlockTags.FLOWERS)) {
                columns.add(BlockPos.asLong(at.getX(), 0, at.getZ()));
            }
        }
        return columns;
    }

    /**
     * Ступень на уступе: клетка, которую надо досыпать, чтобы улица
     * не оборвалась.
     * <p>
     * Написано по жалобе из игры: «не пройти ни к зданиям, ни к фермам».
     * Улица шла по существующей земле и на любом уступе выше двух блоков
     * просто прерывалась — дом на полке оставался без подхода, а житель
     * искал обход, которого нет.
     * <p>
     * Ступень берётся <b>на блок ниже</b> хода улицы: лестница вниз по
     * одному блоку — то, что проходит и житель, и игрок. Под ступенью
     * должна быть опора не глубже {@link #FILL_DEPTH}: досыпать пропасть
     * улица не станет, мост — это не дорожка, а отдельная работа.
     * И над ступенью должно быть пусто: иначе улица полезет в стену.
     */
    private static BlockPos step(ServerWorld world, int x, int z, int height) {
        BlockPos at = new BlockPos(x, height - 1, z);
        if (!isFree(world, at) || !isFree(world, at.up())) {
            return null;
        }

        for (int depth = 1; depth <= FILL_DEPTH; depth++) {
            BlockPos under = at.down(depth);
            if (world.getBlockState(under).isSolidBlock(world, under)) {
                return at;
            }
            if (!isFree(world, under)) {
                return null;
            }
        }
        return null;
    }

    /** Свободно ли: воздух, трава или снег — всё, что улице не помеха. */
    private static boolean isFree(ServerWorld world, BlockPos at) {
        BlockState state = world.getBlockState(at);
        return state.getFluidState().isEmpty() && (state.isAir() || state.isReplaceable());
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
     * Порог: тайл прямо перед дверью, снаружи стены.
     * <p>
     * Считается по <b>той стене, в которой дверь стоит</b>, а не шагами
     * в сторону площади. Разница видна сразу: шагами к площади порог
     * съезжает по диагонали и улица начинается сбоку от входа, а игрок
     * просил, чтобы путь вёл <b>от двери</b>.
     * <p>
     * Дверь всегда лежит на краю следа — она в стене. Какой это край,
     * тот и говорит, куда наружу.
     */
    private static BlockPos doorstep(Building building, Schematic schematic, BlockPos door) {
        Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
        BlockPos anchor = building.anchor();

        int minX = anchor.getX();
        int minZ = anchor.getZ();
        int maxX = minX + size.getX() - 1;
        int maxZ = minZ + size.getZ() - 1;

        if (door.getZ() == minZ) {
            return door.north();
        }
        if (door.getZ() == maxZ) {
            return door.south();
        }
        if (door.getX() == minX) {
            return door.west();
        }
        if (door.getX() == maxX) {
            return door.east();
        }
        // Дверь не в стене — такое бывает у калитки в середине ограды.
        // Тогда улица начнётся там, где линия сама выйдет из-под следа.
        return door;
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
