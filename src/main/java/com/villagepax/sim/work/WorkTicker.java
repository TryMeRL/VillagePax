package com.villagepax.sim.work;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Стратегический слой ИИ: раз в {@link #TICKS_PER_DECISION} тиков каждый
 * работающий житель решает, что делать дальше.
 * <p>
 * Три вещи, на которых держится производительность, и все три взяты из того,
 * как это ломается у существующих модов:
 * <ol>
 *   <li><b>Решения редки.</b> Раз в десять тиков, а не каждый тик.</li>
 *   <li><b>Решения разнесены</b> смещением по хешу жителя: иначе все работники
 *       мира думают в один и тот же тик, и всплеск нагрузки складывается.</li>
 *   <li><b>Нет тела — нет работы.</b> Тело есть только при загруженном чанке,
 *       поэтому далёкое поселение не стоит серверу ничего.</li>
 * </ol>
 * Цель навигации на сущности ходит каждый тик, но пути не считает: точку ей
 * называет стратегия, и она же кладёт её в тело.
 */
public final class WorkTicker {

    /** Полсекунды между решениями. Дизайн-документ отводит на это 20–100 тиков. */
    public static final int TICKS_PER_DECISION = 10;

    private WorkTicker() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(WorkTicker::tick);
    }

    public static void tick(ServerWorld world) {
        SettlementManager manager = SettlementManager.get(world);
        if (manager.count() == 0) {
            return;
        }

        long time = world.getTime();
        for (Settlement settlement : manager.all()) {
            for (Citizen citizen : settlement.citizens()) {
                if (isItsTurn(time, citizen)) {
                    decide(world, manager, settlement, citizen);
                }
            }
        }
    }

    /** Один шаг стратегии одного жителя. Открыт, чтобы игровые тесты не ждали тиков. */
    public static void decide(ServerWorld world, SettlementManager manager,
                              Settlement settlement, Citizen citizen) {
        Job job = Jobs.forProfession(citizen.profession()).orElse(null);
        if (job == null) {
            return;
        }

        CitizenEntity body = liveBody(world, citizen);
        if (body == null) {
            return;
        }

        WorkContext context = new WorkContext(world, manager, settlement, citizen, body);

        // Изменения идут через менеджер, чтобы состояние пометилось грязным
        // и пережило перезаход в мир. Забытый markDirty здесь — самый
        // коварный баг: всё работает до выхода из игры.
        manager.update(settlement.id(), ignored -> job.tick(context));

        // Куда идти, решает стратегия и складывает в тело: цель навигации
        // не должна пересчитывать это каждый тик.
        Optional<BlockPos> destination = job.destination(context);
        body.setWorkTarget(destination.orElse(null));
    }

    private static CitizenEntity liveBody(ServerWorld world, Citizen citizen) {
        return citizen.entityUuid()
                .map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .map(CitizenEntity.class::cast)
                .filter(Entity::isAlive)
                .orElse(null);
    }

    private static boolean isItsTurn(long time, Citizen citizen) {
        int offset = Math.floorMod(citizen.id().hashCode(), TICKS_PER_DECISION);
        return Math.floorMod(time + offset, TICKS_PER_DECISION) == 0;
    }
}
