package com.villagepax.sim.games;

import com.villagepax.core.Profiled;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * Часы игр: ответы компании, игра самой с собой.
 * <p>
 * Настоящие время и игроки подставляются здесь и только здесь: правила
 * принимают их доводами, потому что мир игровых проверок общий и время
 * в нём не подвинешь, а подставной игрок проверок в списке игроков мира
 * не числится.
 */
public final class GamesTicker {

    private GamesTicker() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(Profiled.tick("games", GamesTicker::tick));
    }

    static void tick(ServerWorld world) {
        SettlementManager manager = SettlementManager.get(world);
        Company.replies(world, manager);
        if (world.getTime() % Company.AMBIENT_EVERY != 0) {
            return;
        }
        List<ServerPlayerEntity> players = world.getPlayers();
        if (players.isEmpty()) {
            return;
        }
        long timeOfDay = world.getTimeOfDay();
        long day = Schedule.dayOf(timeOfDay);
        for (Settlement settlement : manager.all()) {
            Company.tick(world, manager, settlement, day, timeOfDay, players);
        }
    }
}
