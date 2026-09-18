package com.villagepax.sim;

import com.villagepax.core.culture.Traits;
import com.villagepax.screen.BuildOrders;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.item.ItemStack;
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

        Warehouse warehouse = Warehouse.of(world, settlement);
        Materials.required(schematic).forEach((item, count) -> {
            int rest = count;
            while (rest > 0) {
                int chunk = Math.min(rest, item.getMaxCount());
                warehouse.addOrScatter(world, settlement.center(), new ItemStack(item, chunk));
                rest -= chunk;
            }
        });

        BuildJob.advance(world, manager, settlement.id(), site.id(), Integer.MAX_VALUE);
        return settlement.building(site.id());
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

        for (BlockPos anchor : spots(world, settlement, schematic, rings)) {
            for (BlockRotation rotation : BlockRotation.values()) {
                if (!(BuildOrders.check(settlement, schematicId, anchor, rotation)
                        instanceof BuildOrders.Result.Placed)) {
                    continue;
                }
                if (BuildOrders.place(manager, settlement, schematicId, anchor, rotation)
                        instanceof BuildOrders.Result.Placed placed) {
                    return Optional.of(placed.site());
                }
            }
        }
        return Optional.empty();
    }

    /** Возможные углы застройки: кольца вокруг ратуши по ровной земле. */
    private static List<BlockPos> spots(ServerWorld world, Settlement settlement,
                                        Schematic schematic, int rings) {
        List<BlockPos> spots = new ArrayList<>();
        BlockPos centre = settlement.center();

        for (int ring = 1; ring <= rings; ring++) {
            int reach = ring * PLACE_STEP;
            for (int dx = -reach; dx <= reach; dx += PLACE_STEP) {
                for (int dz = -reach; dz <= reach; dz += PLACE_STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != reach) {
                        continue;
                    }
                    BlockPos anchor = surface(world, centre.add(dx, 0, dz));
                    if (anchor != null && isWalkableFrom(centre, anchor)
                            && isFlatEnough(world, settlement, anchor, schematic)) {
                        spots.add(anchor);
                    }
                }
            }
        }
        return spots;
    }

    /**
     * Земля под колонной — та, на которой можно строить.
     * <p>
     * Не карта высот мира: она считает поверхностью верхушку листвы, и
     * деревня в лесу размечала бы дома по кронам деревьев.
     */
    private static BlockPos surface(ServerWorld world, BlockPos column) {
        return Ground.buildableAt(world, column.getX(), column.getZ()).orElse(null);
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

    /**
     * Ровность следа. Без этой проверки поселение охотно ставит дом на склон,
     * и половина его висит в воздухе, а другая утоплена в холм.
     * <p>
     * Насколько неровно — дело народа: у майя есть черта террасного
     * земледелия, и они берутся за склоны, на которые норманны не пойдут.
     */
    private static boolean isFlatEnough(ServerWorld world, Settlement settlement, BlockPos anchor,
                                        Schematic schematic) {
        Vec3i size = schematic.size();
        int allowed = Traits.maxSlope(settlement.culture());

        for (int dx = 0; dx < size.getX(); dx += Math.max(1, size.getX() - 1)) {
            for (int dz = 0; dz < size.getZ(); dz += Math.max(1, size.getZ() - 1)) {
                BlockPos corner = surface(world, anchor.add(dx, 0, dz));
                if (corner == null || Math.abs(corner.getY() - anchor.getY()) > allowed) {
                    return false;
                }
            }
        }
        return true;
    }
}
