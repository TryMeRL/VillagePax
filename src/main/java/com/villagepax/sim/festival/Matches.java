package com.villagepax.sim.festival;

import com.villagepax.VillagePax;
import com.villagepax.core.Named;
import com.villagepax.core.Profiled;
import com.villagepax.core.festival.ContestKind;
import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.work.WorkContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Идущие состязания: реестр, запуск, тик, уборка.
 * <p>
 * На ярмарке идёт одно состязание за раз, поэтому реестр — по поселению.
 * Состязания живут в памяти сервера: минута игры не стоит записи на диск,
 * а поставленное ими в мир (вещицы) записано в память праздника поселения,
 * и после падения сервера {@link #recover} убирает остатки.
 */
public final class Matches {

    /** Можно ли начать состязание — или почему нельзя. */
    public enum Verdict implements Named {
        YES("yes"),
        NOT_TODAY("not_today"),
        CLOSED("closed"),
        BUSY("busy"),
        NO_FAIR("no_fair"),
        NO_HOST("no_host"),
        BESIEGED("besieged"),
        NO_ROOM("no_room"),
        TOO_FAR("too_far");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        public String reasonKey() {
            return "villagepax.contest.reason." + id;
        }
    }

    /** Как далеко от сердца ищут соперников: кто дальше, праздника не видит. */
    private static final double RIVALS_FROM = 32;

    private static final Map<UUID, Match> RUNNING = new LinkedHashMap<>();

    private Matches() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(Profiled.tick("contests", Matches::tick));
        ServerLifecycleEvents.SERVER_STOPPING.register(Matches::stopAll);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.getWorlds()
                .forEach(Matches::recover));
    }

    /**
     * Можно ли начать: праздник идёт, светло, ярмарка свободна, игрок рядом.
     * <p>
     * Отказ называет первую причину по порядку: игроку, которому сказали
     * «занято» в будний день, пришлось бы гадать дважды.
     */
    public static Verdict check(PlayerEntity player, Settlement settlement, int index, long day,
                                long timeOfDay) {
        switch (FestivalDay.today(settlement, day)) {
            case NO_FESTIVAL, NOT_TODAY -> {
                return Verdict.NOT_TODAY;
            }
            case NO_FAIR -> {
                return Verdict.NO_FAIR;
            }
            case NO_HOST -> {
                return Verdict.NO_HOST;
            }
            case BESIEGED -> {
                return Verdict.BESIEGED;
            }
            case ON -> {
            }
        }
        if (!FestivalDay.contestsOpen(settlement.owner().isAutonomous(), timeOfDay)) {
            return Verdict.CLOSED;
        }
        if (at(settlement.id()).isPresent()) {
            return Verdict.BUSY;
        }
        Festival festival = Festivals.of(settlement.culture()).orElse(null);
        if (festival == null || index < 0 || index >= festival.contests().size()) {
            return Verdict.NO_ROOM;
        }
        Fair fair = Fairs.of(settlement).orElse(null);
        if (fair == null) {
            return Verdict.NO_FAIR;
        }
        if (player.squaredDistanceTo(Vec3d.ofCenter(fair.heart())) > Match.CATCHMENT * Match.CATCHMENT) {
            return Verdict.TOO_FAR;
        }
        return Verdict.YES;
    }

    /**
     * Начать состязание: приготовить место, созвать игроков у ярмарки,
     * выбрать соперников.
     */
    public static Verdict start(ServerWorld world, PlayerEntity player, Settlement settlement,
                                int index, long day, long timeOfDay) {
        Verdict verdict = check(player, settlement, index, day, timeOfDay);
        if (verdict != Verdict.YES) {
            return verdict;
        }
        Festival festival = Festivals.of(settlement.culture()).orElseThrow();
        Fair fair = Fairs.of(settlement).orElseThrow();
        Match match = create(world, settlement, fair, festival, index, day);
        if (match == null || !match.prepare(world, world.getRandom())) {
            if (match != null) {
                match.clear(world);
            }
            return Verdict.NO_ROOM;
        }
        RUNNING.put(settlement.id(), match);
        Vec3d heart = Vec3d.ofCenter(fair.heart());
        for (ServerPlayerEntity near : world.getPlayers()) {
            if (!near.isSpectator()
                    && near.squaredDistanceTo(heart) <= Match.CATCHMENT * Match.CATCHMENT) {
                match.join(world, near);
            }
        }
        match.join(world, player);
        for (Citizen rival : rivalsFor(world, settlement, fair, match.contest().kind(), day)) {
            match.addRival(rival);
        }
        return Verdict.YES;
    }

    /** Состязание нужного вида. Ловля и стрельба приходят с задачами 19–20. */
    private static Match create(ServerWorld world, Settlement settlement, Fair fair,
                                Festival festival, int index, long day) {
        UUID id = UUID.randomUUID();
        return switch (festival.contests().get(index).kind()) {
            case HUNT -> new Hunt(world, id, settlement, fair, festival, index, day);
            case CHASE, ARCHERY -> null;
        };
    }

    /**
     * Щелчок по вещице поиска.
     *
     * @return истина — вещица чьего-то поиска: засчитана или ждёт; ложь — остаток,
     *         поиска нет, и вещицу можно просто убрать
     */
    public static boolean collect(ServerWorld world, BlockPos token, PlayerEntity player) {
        for (Match match : RUNNING.values()) {
            if (!match.isOver() && match instanceof Hunt hunt && hunt.hides(token)) {
                hunt.collect(world, token, player);
                return true;
            }
        }
        return false;
    }

    /**
     * Соперники: жители с телами у ярмарки, кроме тех, кто работает и в праздник.
     * <p>
     * В поиск сперва идут дети — детям искать пасхальные яйца положено, —
     * и только потом взрослые; ловят и стреляют только взрослые. Порядок
     * устойчивый — от опознавателя и дня: в один праздник соперники одни
     * и те же, в другой — другие.
     */
    static List<Citizen> rivalsFor(ServerWorld world, Settlement settlement, Fair fair,
                                   ContestKind kind, long day) {
        Vec3d heart = Vec3d.ofCenter(fair.heart());
        List<Citizen> near = settlement.citizens().stream()
                .filter(citizen -> !Revels.works(citizen))
                .filter(citizen -> {
                    CitizenEntity body = Match.bodyOf(world, citizen);
                    return body != null && body.squaredDistanceTo(heart) <= RIVALS_FROM * RIVALS_FROM;
                })
                .sorted(Comparator.comparingInt((Citizen citizen) -> Objects.hash(citizen.id(), day))
                        .thenComparing(Citizen::id))
                .toList();
        List<Citizen> pool = new ArrayList<>();
        if (kind == ContestKind.HUNT) {
            near.stream().filter(Ages::isChild).forEach(pool::add);
        }
        near.stream().filter(Ages::isAdult).forEach(pool::add);
        return pool.subList(0, Math.min(Match.RIVALS, pool.size()));
    }

    /** Идущее на ярмарке поселения состязание. */
    public static Optional<Match> at(UUID settlement) {
        return Optional.ofNullable(RUNNING.get(settlement)).filter(match -> !match.isOver());
    }

    /**
     * Ведёт ли состязание этого жителя: соперника решает игра, а не распорядок.
     *
     * @return истина — решать за жителя больше нечего
     */
    public static boolean steers(WorkContext context) {
        Match match = at(context.settlement().id()).orElse(null);
        if (match == null || !match.isRival(context.citizen().id())) {
            return false;
        }
        match.steerRival(context.world(), context.body(), context.citizen());
        return true;
    }

    /** Ход всех состязаний этого мира; кончившиеся уходят из реестра. */
    public static void tick(ServerWorld world) {
        if (RUNNING.isEmpty()) {
            return;
        }
        for (Match match : List.copyOf(RUNNING.values())) {
            if (!match.world().equals(world.getRegistryKey())) {
                continue;
            }
            try {
                match.tick(world);
            } catch (RuntimeException broken) {
                // Сломанное состязание снимается, а не падает каждый тик:
                // минута игры не стоит лога на весь вечер.
                VillagePax.LOGGER.error("Состязание «{}» упало и снято", match.contest().name(), broken);
                try {
                    match.cancel(world, "villagepax.contest.cancel.broken");
                } catch (RuntimeException ignored) {
                    // Уборка тоже упала — остатки уберёт память праздника.
                }
            }
            if (match.isOver()) {
                RUNNING.remove(match.settlement(), match);
            }
        }
    }

    /** Сервер встаёт: снять всё и убрать за собой, пока миры ещё живы. */
    public static void stopAll(MinecraftServer server) {
        for (Match match : List.copyOf(RUNNING.values())) {
            ServerWorld world = server.getWorld(match.world());
            if (world != null) {
                match.cancel(world, "villagepax.contest.cancel.stopping");
            }
        }
        RUNNING.clear();
    }

    /**
     * После запуска: убрать оставшееся от состязаний прошлого запуска.
     * <p>
     * Состязаний после запуска нет, а стоящее в мире — вещицы, которые
     * сервер не успел убрать, упав. Стол праздника остаётся: пироги
     * живут весь день, и уберёт их наутро сам стол. Что в незагруженных
     * чанках — уберётся наутро вместе со столом.
     */
    public static void recover(ServerWorld world) {
        SettlementManager manager = SettlementManager.get(world);
        for (Settlement settlement : List.copyOf(manager.all())) {
            if (at(settlement.id()).isPresent()) {
                continue;
            }
            SettlementManager.Festive memory = manager.festiveOf(settlement.id()).orElse(null);
            if (memory == null) {
                continue;
            }
            for (SettlementManager.Placed placed : memory.placed()) {
                if (!placed.block().equals(Feast.PIE)) {
                    Feast.takeBack(world, manager, settlement.id(), placed);
                }
            }
        }
    }
}
