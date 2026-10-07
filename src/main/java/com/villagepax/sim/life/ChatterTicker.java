package com.villagepax.sim.life;

import com.villagepax.core.Profiled;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Часы живых реплик: раз в секунду каждому игроку — фраза ближайшего
 * свободного жителя, если пора.
 * <p>
 * Не чаще раза в восемь секунд на игрока: три жителя у ратуши, заговорившие
 * разом, — это гул, а не жизнь. И только тому, кто рядом: фраза над головой
 * в двадцати блоках — шёпот в пустоту, а сервер посчитал бы её зря.
 */
public final class ChatterTicker {

    /** Как часто оглядываются: раз в секунду. */
    static final int EVERY = 20;

    /** В скольких блоках от игрока житель с ним заговорит. */
    static final double REACH = 6;

    /** Игроку — не чаще раза в столько тиков. */
    static final int PLAYER_EVERY = 160;

    private static final Map<UUID, Long> HEARD = new HashMap<>();

    private ChatterTicker() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(Profiled.tick("chatter", world -> {
            if (world.getTime() % EVERY == 0) {
                tick(world, world.getPlayers());
            }
        }));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            HEARD.clear();
            Chatter.forget();
            Gossip.forget();
            Weddings.forget();
        });
    }

    /** Один обход: игроки — доводом, подставной игрок проверок в мире не числится. */
    public static void tick(ServerWorld world, List<? extends PlayerEntity> players) {
        long now = world.getTime();
        long day = Schedule.dayOf(world.getTimeOfDay());
        SettlementManager manager = SettlementManager.get(world);
        // Сплетни на вечерних площадях и ответы на них — тем же обходом.
        Gossip.replies(world, manager, now);
        // И свадебный вечер: сердечки, «Горько!», угощение пришедшему.
        Weddings.tick(world, manager, players, day, world.getTimeOfDay(),
                new Random(world.getRandom().nextLong()));
        if (!players.isEmpty()) {
            for (Settlement settlement : List.copyOf(manager.all())) {
                Gossip.tick(world, settlement, day, world.getTimeOfDay(), players,
                        new Random(world.getRandom().nextLong()));
            }
        }
        for (PlayerEntity player : players) {
            if (player.isSpectator()) {
                continue;
            }
            Long last = HEARD.get(player.getUuid());
            if (last != null && now - last < PLAYER_EVERY) {
                continue;
            }
            List<CitizenEntity> near = world.getEntitiesByClass(CitizenEntity.class,
                    player.getBoundingBox().expand(REACH),
                    body -> body.isAlive() && body.raidId() == null && body.caravanId() == null
                            && body.squaredDistanceTo(player) <= REACH * REACH);
            near.sort(Comparator.comparingDouble(body -> body.squaredDistanceTo(player)));
            for (CitizenEntity body : near) {
                Settlement settlement = body.settlementId().flatMap(manager::byId).orElse(null);
                Citizen citizen = body.data(world).orElse(null);
                if (settlement == null || citizen == null) {
                    continue;
                }
                if (Chatter.speak(world, settlement, citizen, body, player, day,
                        new Random(world.getRandom().nextLong())).isPresent()) {
                    HEARD.put(player.getUuid(), now);
                    break;
                }
            }
        }
    }
}
