package com.villagepax.sim;

import com.villagepax.sim.build.BuildStep;
import com.villagepax.VillagePax;
import com.villagepax.screen.BuildOrders;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Galleries;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Footing;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Где поселению поставить здание и как поднять его разом.
 * <p>
 * Жило это в {@link Villages}, потому что размечала себе место только
 * деревня народа. Теперь тем же пользуется и колония игрока: при основании
 * ей достаются готовыми дом и поле, и ставиться они обязаны <b>по тем же
 * правилам</b>, по каким стоят деревенские. Иначе у мода завелись бы два
 * представления о пригодном месте, и расходиться они начали бы с первой
 * же правки одного из них.
 * <p>
 * Пригодность спрашивается у {@link BuildOrders#check} — тех самых правил,
 * по которым размечает игрок. Ни деревня, ни колония не должны уметь того,
 * чего не умеет он.
 */
public final class Raising {

    /** Кольца поиска места для нового здания и шаг между ними, в блоках. */
    private static final int PLACE_RINGS = 5;
    private static final int PLACE_STEP = 7;

    /**
     * Сколько колец обходят под первый надел — те, что <b>у самой ратуши</b>.
     * <p>
     * Это двор, а не выселки. Общий поиск обходит пять колец и в глухом
     * месте уводит дом за тридцать шагов и за холм: деревне, которая
     * строится годами, так и надо, а первый дом колонии должен стоять
     * там, где игрок его увидит, обернувшись. Не нашлось места в двух
     * кольцах — надела не будет, и это честнее дома за околицей.
     */
    public static final int CLOSE_RINGS = 2;

    /** Сколько шагов в сторону стоит один блок подъёма: круче не ходят. */
    private static final int CLIMB_PER_STEP = 3;

    /** Холмик у самой ратуши деревню не портит. */
    private static final int MIN_CLIMB = 3;

    private Raising() {
    }

    /**
     * Поставить здание такого типа готовым — со стенами, крышей и утварью.
     * <p>
     * Материалы не спрашиваются: у деревни это здание стояло <b>до прихода
     * игрока</b>, а колонии оно даётся как первый надел. Просить за то
     * и за другое было бы не у кого — колония в свой первый миг пуста.
     *
     * @return поставленное здание, или пусто, если места не нашлось
     */
    public static Optional<Building> raise(ServerWorld world, SettlementManager manager,
                                           Settlement settlement, Identifier type) {
        return raise(world, manager, settlement, type, PLACE_RINGS);
    }

    /** То же, но с ограничением, как далеко от ратуши искать место. */
    public static Optional<Building> raise(ServerWorld world, SettlementManager manager,
                                           Settlement settlement, Identifier type, int rings) {
        Identifier schematicId = new Identifier(type.getNamespace(), type.getPath() + "_lvl1");
        Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }

        Building site = placeNear(world, manager, settlement, schematicId, rings).orElse(null);
        if (site == null) {
            return Optional.empty();
        }

        // Материал кладётся В САМУ СТРОЙКУ, а не на склад, и это не мелочь.
        // Склад колонии в первый день — один сундук ратуши на двадцать семь
        // ячеек; материалы на дом и поле разом в него не влезают, а лишнее
        // {@code addOrScatter} высыпает под ноги. Билдер тогда просит
        // то, что валяется рядом на земле, и подарок застревает
        // недостроенным — ровно это и поймала проверка «путь игрока».
        //
        // Запас стройки билдер спрашивает первым, до всякого склада,
        // и объёма у него нет.
        Materials.required(schematic).forEach((item, count) ->
                site.stock().add(Registries.ITEM.getId(item), count));

        BuildJob.Outcome outcome =
                BuildJob.advance(world, manager, settlement.id(), site.id(), Integer.MAX_VALUE);
        if (outcome != BuildJob.Outcome.FINISHED) {
            // Подарок, который не встал, не оставляет после себя
            // стройплощадку. Недостроенное здание — это не «почти готово»,
            // а вечная работа для билдера: он берётся за неё первой, до
            // всего, что заказал игрок, и колония застывает на месте.
            // Поймано проверкой «путь игрока»: подаренное поле село
            // на край площадки, не достроилось — и билдер двести тиков
            // ходил вокруг него, пока размеченный игроком дом стоял
            // нетронутым.
            takeBack(world, manager, settlement, site, schematic);
            VillagePax.LOGGER.info("Надел {} не встал ({}) — место возвращено",
                    type, outcome);
            return Optional.empty();
        }
        return settlement.building(site.id());
    }

    /**
     * Убрать несостоявшийся подарок: и блоки, и запись.
     * <p>
     * Блоки — только те клетки, которые план успел пройти: всё остальное
     * билдер не трогал, и трогать это здесь значило бы выесть землю
     * вокруг на ровном месте.
     */
    private static void takeBack(ServerWorld world, SettlementManager manager,
                                 Settlement settlement, Building site, Schematic schematic) {
        List<BuildStep> steps = schematic.plan().steps();
        int done = Math.min(site.nextStep(), steps.size());
        for (int step = 0; step < done; step++) {
            if (steps.get(step).placesBlock()) {
                world.setBlockState(BuildJob.worldPos(site, schematic.size(), steps.get(step).pos()),
                        Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            }
        }
        manager.update(settlement.id(), state -> state.removeBuilding(site.id()));
    }

    /**
     * Место для здания: кольцами от ратуши, первое подходящее.
     * <p>
     * Пригодность спрашивается у тех же правил, по которым размечает игрок
     * ({@link BuildOrders#check}) — границы, наложение следов, имя схемы.
     * Поселение не должно уметь того, чего не умеет игрок, иначе его
     * застройка начнёт выглядеть невозможной.
     */
    public static Optional<Building> placeNear(ServerWorld world, SettlementManager manager,
                                               Settlement settlement, Identifier schematicId) {
        return placeNear(world, manager, settlement, schematicId, PLACE_RINGS);
    }

    /** То же, но не дальше названного числа колец от ратуши. */
    public static Optional<Building> placeNear(ServerWorld world, SettlementManager manager,
                                               Settlement settlement, Identifier schematicId,
                                               int rings) {
        Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }

        // Лучшее из первых годных мест, а не первое попавшееся: ближе
        // к середине и дверью к ней. Дальше искать незачем — дальнее место
        // хуже ближнего уже тем, что дальнее.
        Candidate best = null;
        int found = 0;
        for (BlockPos column : columns(settlement.center(), rings * PLACE_STEP)) {
            BlockPos anchor = spot(world, settlement, column);
            if (anchor == null || !isWalkableFrom(settlement.center(), anchor)) {
                continue;
            }
            for (BlockRotation rotation : BlockRotation.values()) {
                if (!fits(world, settlement, anchor, schematic, rotation)
                        || !(BuildOrders.check(settlement, schematicId, anchor, rotation)
                        instanceof BuildOrders.Result.Placed)) {
                    continue;
                }
                double score = score(settlement.center(), schematic, anchor, rotation);
                if (best == null || score < best.score()) {
                    best = new Candidate(anchor, rotation, score);
                }
                found++;
            }
            if (found >= ENOUGH) {
                break;
            }
        }
        if (best != null && BuildOrders.place(manager, settlement, schematicId, best.anchor(),
                best.rotation()) instanceof BuildOrders.Result.Placed placed) {
            // В горе к размеченному залу сперва ведут штольню: иначе
            // билдеру не к чему подойти.
            Galleries.cutAll(world, settlement);
            return Optional.of(placed.site());
        }
        return Optional.empty();
    }

    /** Место и поворот, которые уже годятся, и во что они обходятся. */
    private record Candidate(BlockPos anchor, BlockRotation rotation, double score) {
    }

    /**
     * Сколько годных мест посмотреть, прежде чем выбрать лучшее: дюжины
     * хватает, чтобы среди ближних нашлось место дверью к площади.
     */
    private static final int ENOUGH = 12;

    /**
     * Шаг сетки мест. Прежде он был в ширину дома, и с обязательным
     * зазором годилась лишь каждая вторая клетка: дома стояли редко,
     * через пустырь, и деревня расползалась по склону. Сетка в клетку
     * ставит дом в зазоре от соседа — улицей, а не хутором, — и находит
     * у эльфов опору помоста там, где крупный шаг её перешагивал.
     */
    private static final int FINE_STEP = 1;

    /** Ближе этого к ратуше не строят: там площадь. */
    private static final int MIN_RING = 4;

    /** Во что обходится дверь, отвёрнутая от площади: как шесть шагов лишней дороги. */
    private static final double DOOR_AWAY = 6;

    /**
     * Цена места: расстояние от середины поселения и то, куда смотрит дверь.
     * <p>
     * Дверь — главное, по чему деревня читается деревней, а не складом
     * коробок. Прежде дом вставал в первом годном повороте, то есть почти
     * всегда в одном и том же, и половина домов смотрела дверью в лес.
     * Теперь дом разворачивается к площади, если место позволяет, и улицы
     * сходятся к ратуше сами.
     */
    static double score(BlockPos centre, Schematic schematic, BlockPos anchor,
                        BlockRotation rotation) {
        Vec3i size = schematic.size();
        BlockPos middle = BuildSite.toWorld(anchor, size, rotation,
                new BlockPos(size.getX() / 2, 0, size.getZ() / 2));
        double away = Math.sqrt(middle.getSquaredDistance(centre.getX(), middle.getY(),
                centre.getZ()));
        List<Schematic.Entrance> doors = schematic.entrances();
        if (doors.isEmpty()) {
            return away;
        }
        Schematic.Entrance door = doors.get(0);
        BlockPos inside = BuildSite.toWorld(anchor, size, rotation, door.pos());
        BlockPos outside = BuildSite.toWorld(anchor, size, rotation,
                door.pos().offset(door.wayOut()));
        double fx = outside.getX() - inside.getX();
        double fz = outside.getZ() - inside.getZ();
        double tx = centre.getX() - inside.getX();
        double tz = centre.getZ() - inside.getZ();
        double length = Math.sqrt(tx * tx + tz * tz);
        double facing = length < 1e-6 ? 1 : (fx * tx + fz * tz) / length;
        return away + (1 - facing) * DOOR_AWAY;
    }

    /** Колонны вокруг середины — квадратными кольцами наружу, по мелкой сетке. */
    private static List<BlockPos> columns(BlockPos centre, int reach) {
        List<BlockPos> columns = new ArrayList<>();
        for (int ring = MIN_RING; ring <= reach; ring += FINE_STEP) {
            for (int dx = -ring; dx <= ring; dx += FINE_STEP) {
                for (int dz = -ring; dz <= ring; dz += FINE_STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                        columns.add(centre.add(dx, 0, dz));
                    }
                }
            }
        }
        return columns;
    }

    /**
     * Куда в этой колонне встанет угол здания.
     * <p>
     * На поверхности это земля, в горе — <b>отметка пола чертога</b>,
     * одна на всё поселение. Рельефа гномы не спрашивают вовсе: пол
     * у них не следует за склоном, а вырубается по уровню, и в этом
     * вся разница между деревней и чертогом.
     */
    private static BlockPos spot(ServerWorld world, Settlement settlement, BlockPos column) {
        return Footing.of(settlement).spot(world, settlement, column);
    }

    /**
     * Годится ли место под здание такого размера.
     * <p>
     * На лугу спрашивается уклон, в горе — толща: «ровно ли» под землёй
     * вопрос без смысла, пол и так один, а вот «не вскрыт ли свод сверху»
     * — тот самый вопрос, который отличает чертог от ямы.
     */
    private static boolean fits(ServerWorld world, Settlement settlement, BlockPos anchor,
                                Schematic schematic, BlockRotation rotation) {
        // Повёрнутый след: у дома восемь на шесть поворот меняет то, что
        // лежит под ним, и ровность надо спрашивать про тот след, что встанет.
        return Footing.of(settlement).fits(world, settlement, anchor,
                BuildSite.rotatedSize(schematic.size(), rotation));
    }

    /**
     * Можно ли дойти от середины поселения до этого места пешком.
     * <p>
     * Написано по настоящей деревне из игры заказчика: дом и ферма встали
     * <b>на восемнадцать блоков выше</b> ратуши, в десяти шагах от неё.
     * Ровности следа это не нарушало — полка на скале ровная, — и место
     * проходило проверку. А билдер до него не добирался, стройка вставала
     * на середине, и игрок сказал прямо: «строится высоко и не пройти».
     * <p>
     * Правило простое и человеческое: <b>подъём не круче одного блока
     * на три шага</b>. Столько поднимается лестница, столько одолевает
     * житель, и ровно на столько ляжет улица. Ближний край поселения при
     * этом всё равно вправе быть на три блока выше: холмик у дома —
     * не скала.
     */
    public static boolean isWalkableFrom(BlockPos centre, BlockPos anchor) {
        int away = (int) Math.sqrt(centre.getSquaredDistance(anchor.getX(), centre.getY(),
                anchor.getZ()));
        int climb = Math.abs(anchor.getY() - centre.getY());
        return climb <= Math.max(MIN_CLIMB, away / CLIMB_PER_STEP);
    }

}
