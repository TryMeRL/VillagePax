package com.villagepax.sim.games;

import com.villagepax.core.Profiled;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * Часы игр: идущие партии и прятки, ответы компании, игра самой с собой,
 * оклики прохожих и приглашения детей.
 * <p>
 * Настоящие время и игроки подставляются здесь и только здесь: правила
 * принимают их доводами, потому что мир игровых проверок общий и время
 * в нём не подвинешь, а подставной игрок проверок в списке игроков мира
 * не числится.
 */
public final class GamesTicker {

    /** Прохожих оглядывают раз в секунду: оклик — не реакция на шаг. */
    private static final int PASSERSBY_EVERY = 20;

    /** В какой день память игр чистилась последний раз, по мирам. Только сервер. */
    private static final java.util.Map<net.minecraft.registry.RegistryKey<net.minecraft.world.World>, Long>
            PRUNED = new java.util.HashMap<>();

    private GamesTicker() {
    }

    /**
     * Раз в день забыть лишнее: счёт ушедших и умерших, вчерашние траты,
     * бодрость старше вчерашней. Мир живёт годами, и чужие счета копились бы.
     */
    private static void forgetOld(ServerWorld world, SettlementManager manager) {
        long day = Schedule.dayOf(world.getTimeOfDay());
        Long last = PRUNED.put(world.getRegistryKey(), day);
        if (last != null && last == day) {
            return;
        }
        java.util.Set<java.util.UUID> living = new java.util.HashSet<>();
        for (Settlement settlement : manager.all()) {
            settlement.citizens().forEach(citizen -> living.add(citizen.id()));
        }
        GamesLedger.get(world).prune(living, day);
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(Profiled.tick("games", GamesTicker::tick));
        ServerLifecycleEvents.SERVER_STOPPING.register(Bouts::stopAll);
        ServerLifecycleEvents.SERVER_STOPPING.register(HideAndSeek::stopAll);
    }

    static void tick(ServerWorld world) {
        SettlementManager manager = SettlementManager.get(world);
        Bouts.tick(world);
        HideAndSeek.tick(world);
        Company.replies(world, manager);
        forgetOld(world, manager);
        List<ServerPlayerEntity> players = world.getPlayers();
        if (players.isEmpty()) {
            return;
        }
        long timeOfDay = world.getTimeOfDay();
        long day = Schedule.dayOf(timeOfDay);
        if (world.getTime() % PASSERSBY_EVERY == 0) {
            Passersby.tick(world, manager, day, timeOfDay, players);
            for (Settlement settlement : manager.all()) {
                HideAndSeek.invite(world, settlement, day, timeOfDay, players);
            }
        }
        if (world.getTime() % Company.AMBIENT_EVERY == 0) {
            for (Settlement settlement : manager.all()) {
                Company.tick(world, manager, settlement, day, timeOfDay, players);
            }
        }
    }
}
