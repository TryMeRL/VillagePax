package com.villagepax.sim.festival;

import com.villagepax.core.Profiled;
import com.villagepax.core.Safely;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ход праздника в мире: раз в секунду обходит поселения.
 * <p>
 * На смене дня у поселения — зов. Помнит, кому уже звали сегодня, только
 * в памяти сервера: после перезапуска посреди праздничного дня зов прозвучит
 * ещё раз, и это к лучшему — кто зашёл в мир, узнает, что сегодня праздник.
 */
public final class FestivalTicker {

    /** Раз в секунду: праздник меряется часами, а не тиками. */
    private static final int EVERY = 20;

    private static final Map<UUID, Long> HERALDED = new HashMap<>();

    private FestivalTicker() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(Profiled.tick("festival", FestivalTicker::tick));
    }

    private static void tick(ServerWorld world) {
        if (world.getTime() % EVERY != 0) {
            return;
        }
        SettlementManager manager = SettlementManager.get(world);
        long day = Schedule.dayOf(world.getTimeOfDay());
        for (Settlement settlement : List.copyOf(manager.all())) {
            Safely.run(settlement.name(), "Праздник", () -> {
                Long heralded = HERALDED.get(settlement.id());
                if (heralded == null || heralded != day) {
                    HERALDED.put(settlement.id(), day);
                    Heralds.dawn(world, settlement, day);
                }
            });
        }
    }
}
