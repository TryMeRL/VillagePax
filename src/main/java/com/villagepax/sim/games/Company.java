package com.villagepax.sim.games;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.Standing;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Вечерняя компания: до четырёх взрослых у места игры.
 * <p>
 * С ними игрок и играет — за столом, а не где придётся: компанию видно
 * с площади, и тот, кто пришёл вечером в деревню, находит её там же, где
 * вчера. Состав на вечер — по нраву: честолюбивые садятся первыми, трусы
 * последними; внутри нрава — по стойкой жеребьёвке дня, чтобы компания
 * менялась от вечера к вечеру, но не каждые пять секунд. Набожный за кости
 * не садится.
 * <p>
 * Когда в деревне есть кому смотреть, компания играет сама с собой: один
 * бросает — взмах рукой и стук костей, — другой отвечает словом.
 */
public final class Company {

    /** Больше четырёх за одним столом не стоят. */
    public static final int MAX = 4;

    /** Одному за столом не компания: он идёт на обычный сбор. */
    public static final int MIN = 2;

    /** При госте засиживаются — до этого часа ночи. */
    public static final long LATE = 14_000;

    /** Гость — игрок в стольких блоках от места игры. */
    static final double GUEST_NEAR = 8;

    /** Сама с собой — раз в столько тиков: десять секунд. */
    public static final int AMBIENT_EVERY = 200;

    /** Смотреть есть кому — игрок в стольких блоках. */
    static final double AUDIENCE = 24;

    /** Через сколько тиков после броска отвечают. */
    static final int REPLY_AFTER = 20;

    /**
     * Кольцо в двух шагах от места: сперва четыре стороны — лицом к лицу,
     * — потом углы.
     */
    private static final int[][] RING = {{0, -2}, {0, 2}, {2, 0}, {-2, 0},
            {2, -2}, {-2, 2}, {2, 2}, {-2, -2}};

    /** Кто кому ответит и когда: поселение → ответ. Только сервер, не сохраняется. */
    private static final Map<UUID, Reply> REPLIES = new LinkedHashMap<>();

    private record Reply(RegistryKey<World> world, UUID village, UUID citizen, long at) {
    }

    private Company() {
    }

    /**
     * Собирается ли компания в это время.
     * <p>
     * На досуге — да; во сне — только до {@link #LATE} и только при госте
     * у места игры: засиживаются, когда есть с кем. В праздник — никогда:
     * все на ярмарке.
     */
    public static boolean gathers(ServerWorld world, SettlementManager manager, Settlement settlement,
                                  long day, Schedule part, long timeOfDay,
                                  List<? extends PlayerEntity> players) {
        if (FestivalDay.isOn(settlement, day)) {
            return false;
        }
        if (part == Schedule.LEISURE) {
            return true;
        }
        if (part != Schedule.SLEEP || Math.floorMod(timeOfDay, Schedule.DAY_LENGTH) >= LATE) {
            return false;
        }
        Vec3d spot = Vec3d.ofCenter(GameSpot.of(world, manager, settlement));
        return players.stream().anyMatch(player -> !player.isSpectator()
                && player.squaredDistanceTo(spot) <= GUEST_NEAR * GUEST_NEAR);
    }

    /** Компания на этот вечер, по порядку мест; не собирается — пусто. */
    public static List<Citizen> of(ServerWorld world, Settlement settlement, long day, Schedule part,
                                   long timeOfDay, List<? extends PlayerEntity> players) {
        SettlementManager manager = SettlementManager.get(world);
        if (!gathers(world, manager, settlement, day, part, timeOfDay, players)) {
            return List.of();
        }
        List<Citizen> company = settlement.citizens().stream()
                // Старики — тоже взрослые: «старый Рено» за столом — часть вечера.
                .filter(citizen -> !Ages.isChild(citizen))
                .filter(citizen -> Natures.of(citizen) != Nature.PIOUS)
                .filter(citizen -> awake(world, citizen))
                .sorted(Comparator.comparingInt((Citizen citizen) -> seat(Natures.of(citizen)))
                        .thenComparingInt(citizen -> Long.hashCode(
                                citizen.id().getMostSignificantBits() ^ day)))
                .limit(MAX)
                .toList();
        return company.size() < MIN ? List.of() : company;
    }

    /** Кто садится первым: честолюбивый рвётся к столу, трус подходит последним. */
    private static int seat(Nature nature) {
        return switch (nature) {
            case AMBITIOUS -> 0;
            case EVEN -> 1;
            case LAZY -> 2;
            case COWARD -> 3;
            case PIOUS -> 4;
        };
    }

    /** С телом и не в постели: спящего из-за стола не поднимают. */
    private static boolean awake(ServerWorld world, Citizen citizen) {
        return body(world, citizen).filter(body -> !body.isSleeping() && !body.isDozing()).isPresent();
    }

    static Optional<CitizenEntity> body(ServerWorld world, Citizen citizen) {
        return citizen.entityUuid().map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .map(CitizenEntity.class::cast);
    }

    /**
     * Где стоять члену компании: своя клетка кольца, а занятая стеной —
     * следующая свободная. Не нашлось — само место: житель подойдёт к нему
     * вплотную и встанет, где сможет.
     */
    public static BlockPos standAt(ServerWorld world, BlockPos spot, int index) {
        int free = 0;
        for (int[] offset : RING) {
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos at = spot.add(offset[0], dy, offset[1]);
                if (Standing.canStandAt(world, at)) {
                    if (free == index) {
                        return at;
                    }
                    free++;
                    break;
                }
            }
        }
        return spot;
    }

    /**
     * Компания играет сама с собой: один бросает, другой через секунду отвечает.
     * <p>
     * Только когда есть кому смотреть — игрок в {@link #AUDIENCE} блоках:
     * пустой деревне это ни к чему. Играющий с игроком не отвлекается.
     */
    public static void tick(ServerWorld world, SettlementManager manager, Settlement settlement,
                            long day, long timeOfDay, List<? extends PlayerEntity> players) {
        Schedule part = Schedule.at(timeOfDay);
        if (part != Schedule.LEISURE && part != Schedule.SLEEP) {
            return;
        }
        BlockPos spot = GameSpot.of(world, manager, settlement);
        Vec3d centre = Vec3d.ofCenter(spot);
        boolean watched = players.stream().anyMatch(player -> !player.isSpectator()
                && player.squaredDistanceTo(centre) <= AUDIENCE * AUDIENCE);
        if (!watched) {
            return;
        }
        List<Citizen> free = of(world, settlement, day, part, timeOfDay, players).stream()
                .filter(citizen -> Bouts.rivalOf(citizen.id()).isEmpty())
                .toList();
        if (free.size() < 2) {
            return;
        }
        int first = world.getRandom().nextInt(free.size());
        int second = (first + 1 + world.getRandom().nextInt(free.size() - 1)) % free.size();
        Citizen thrower = free.get(first);
        body(world, thrower).ifPresent(body -> {
            body.throwDice();
            Lines.say(body, thrower, Say.AMBIENT_THROW);
        });
        world.playSound(null, spot, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.NEUTRAL, 0.6f, 1.3f);
        REPLIES.put(settlement.id(), new Reply(world.getRegistryKey(), settlement.id(),
                free.get(second).id(), world.getTime() + REPLY_AFTER));
    }

    /** Ответы, которым пришёл срок: «Везёт же!» через секунду после броска. */
    public static void replies(ServerWorld world, SettlementManager manager) {
        long now = world.getTime();
        for (Iterator<Reply> it = REPLIES.values().iterator(); it.hasNext(); ) {
            Reply reply = it.next();
            if (!reply.world().equals(world.getRegistryKey()) || reply.at() > now) {
                continue;
            }
            it.remove();
            manager.byId(reply.village()).flatMap(village -> village.citizen(reply.citizen()))
                    .ifPresent(citizen -> body(world, citizen)
                            .ifPresent(body -> Lines.say(body, citizen, Say.AMBIENT_REPLY)));
        }
    }
}
