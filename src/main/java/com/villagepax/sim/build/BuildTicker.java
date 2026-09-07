package com.villagepax.sim.build;

import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

/**
 * Темп стройки: раз в {@link BuildJob#TICKS_PER_STEP} на каждую стройплощадку.
 */
public final class BuildTicker {

    private BuildTicker() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(BuildTicker::tick);
    }

    public static void tick(ServerWorld world) {
        SettlementManager manager = SettlementManager.get(world);
        if (manager.count() == 0) {
            return;
        }

        long time = world.getTime();

        // Обход без копирования списков: шаг стройки меняет поля здания, но
        // не состав списков. Если это когда-нибудь изменится, обход упадёт
        // громко, а не начнёт молча пропускать здания.
        for (Settlement settlement : manager.all()) {
            for (Building building : settlement.buildings()) {
                if (!BuildJob.isUnderConstruction(building) || !isItsTurn(time, building)) {
                    continue;
                }
                BuildJob.advance(world, manager, settlement.id(), building.id(), 1);
            }
        }
    }

    /**
     * Смещение по хешу идентификатора: без него все стройки мира ставили бы
     * блок в один и тот же тик, и всплеск нагрузки складывался бы.
     */
    private static boolean isItsTurn(long time, Building building) {
        int offset = Math.floorMod(building.id().hashCode(), BuildJob.TICKS_PER_STEP);
        return Math.floorMod(time + offset, BuildJob.TICKS_PER_STEP) == 0;
    }
}
