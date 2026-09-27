package com.villagepax.sim.festival;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Villages;
import com.villagepax.sim.work.Needs;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Гулянье: в праздник житель идёт на ярмарку и встаёт в хоровод.
 * <p>
 * Хоровод — восемь мест по кругу у сердца праздника, и раз в пять секунд
 * круг сдвигается на шаг: каждый переходит на соседнее место, и хоровод
 * идёт по кругу, как ему и положено. Место жителя устойчиво — оно
 * считается по порядку опознавателей, а не случайно, — иначе он метался бы
 * по кругу от решения к решению. Кому не хватило места, стоит вокруг
 * и смотрит на сердце.
 * <p>
 * Не гуляют трое: затейник (ведёт праздник), купец (ярмарка — это торг)
 * и стража (кто-то должен стеречь). Голодный в обед сперва ест.
 * Соперника идущего состязания ведёт состязание.
 */
public final class Revels {

    /** Кто работает и в праздник. */
    private static final Set<Identifier> WORKING = Set.of(Villages.ENTERTAINER, Villages.MERCHANT,
            Villages.GUARD);

    /** Мест в хороводе. */
    public static final int DANCERS = 8;

    /** Хоровод — кольцо в двух шагах от сердца, по краю мощёного круга. */
    private static final int[][] RING = {{2, 0}, {2, 2}, {0, 2}, {-2, 2}, {-2, 0}, {-2, -2},
            {0, -2}, {2, -2}};

    /** Зрители — кольцо в четырёх шагах. */
    private static final int WATCH_RADIUS = 4;
    private static final int WATCHERS = 12;

    /** Тиков на шаг хоровода: пять секунд. */
    private static final long TURN = 100;

    /** На сколько гуляющего отпускают от сердца праздника. */
    public static final int LEASH = 16;

    private Revels() {
    }

    /**
     * Забрать решение жителя на праздник, если сегодня гуляют.
     *
     * @return истина, если житель сейчас гуляет и решать за него больше нечего
     */
    public static boolean takesOver(WorkContext context, Schedule part, long day) {
        // Соперника идущего состязания ведёт игра — в любой час: минута
        // игры, начатая до заката, может кончиться после отбоя.
        if (Matches.steers(context)) {
            return true;
        }
        Settlement settlement = context.settlement();
        Citizen citizen = context.citizen();
        if (!FestivalDay.revels(settlement.owner().isAutonomous(), part) || works(citizen)) {
            return false;
        }
        if (part == Schedule.MEAL && Needs.isHungry(citizen)) {
            return false;
        }
        if (!FestivalDay.isOn(settlement, day)) {
            return false;
        }
        Fair fair = Fairs.of(settlement).orElse(null);
        if (fair == null) {
            return false;
        }
        CitizenEntity body = context.body();
        context.holdNothing();
        body.keepNear(fair.heart(), LEASH);
        int place = revellers(context.world(), settlement).indexOf(citizen);
        long turn = context.world().getTime() / TURN;
        BlockPos spot = place >= 0 && place < DANCERS
                ? ringSpot(context.world(), fair, place, turn) : null;
        boolean dancing = spot != null;
        if (spot == null) {
            spot = watchSpot(context.world(), fair, Math.max(0, place - DANCERS));
        }
        body.setDancing(dancing);
        body.setWorkTarget(spot);
        body.setWorkFocus(fair.heart());
        return true;
    }

    /** Работает ли житель и в праздник: затейник, купец, стража. */
    public static boolean works(Citizen citizen) {
        return citizen.profession().filter(WORKING::contains).isPresent();
    }

    /**
     * Кто гуляет, в устойчивом порядке: по опознавателю.
     * <p>
     * Только те, у кого есть тело: житель в выгруженном чанке прийти
     * не может, и место в хороводе, отданное ему, стояло бы пустым —
     * дыра в кругу, которую видно с другого конца ярмарки.
     */
    static List<Citizen> revellers(ServerWorld world, Settlement settlement) {
        return settlement.citizens().stream()
                .filter(citizen -> !works(citizen))
                .filter(citizen -> com.villagepax.entity.CitizenSpawner.hasLiveBody(world, citizen))
                .sorted(Comparator.comparing(Citizen::id))
                .toList();
    }

    /** Где сердце праздника гудит сегодня: песня играет там, а не у ратуши. */
    public static Optional<BlockPos> stage(Settlement settlement, long day) {
        return FestivalDay.isOn(settlement, day) ? Fairs.of(settlement).map(Fair::heart)
                : Optional.empty();
    }

    /** Место в хороводе: своё с поворотом круга, а занятое стеной — следующее. */
    private static BlockPos ringSpot(ServerWorld world, Fair fair, int place, long turn) {
        for (int step = 0; step < DANCERS; step++) {
            int[] offset = RING[(int) Math.floorMod(place + turn + step, (long) DANCERS)];
            BlockPos spot = footing(world, fair.heart().add(offset[0], 0, offset[1]), fair.standingY());
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Место зрителя: кольцо в четырёх шагах, своё или следующее свободное. */
    private static BlockPos watchSpot(ServerWorld world, Fair fair, int place) {
        for (int step = 0; step < WATCHERS; step++) {
            double angle = ((place + step) % WATCHERS) * (2.0 * Math.PI / WATCHERS);
            int dx = (int) Math.round(Math.cos(angle) * WATCH_RADIUS);
            int dz = (int) Math.round(Math.sin(angle) * WATCH_RADIUS);
            BlockPos spot = footing(world, fair.heart().add(dx, 0, dz), fair.standingY());
            if (spot != null) {
                return spot;
            }
        }
        return fair.counter();
    }

    /** Где стоять в колонне: около пола ярмарки, ноги на твёрдом, голова в пустоте. */
    private static BlockPos footing(ServerWorld world, BlockPos column, int standing) {
        for (int dy : new int[]{0, 1, -1}) {
            BlockPos at = new BlockPos(column.getX(), standing + dy, column.getZ());
            if (world.getBlockState(at.down()).isSolidBlock(world, at.down())
                    && world.getBlockState(at).getCollisionShape(world, at).isEmpty()
                    && world.getBlockState(at.up()).getCollisionShape(world, at.up()).isEmpty()) {
                return at;
            }
        }
        return null;
    }
}
