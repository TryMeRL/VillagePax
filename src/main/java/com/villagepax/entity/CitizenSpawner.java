package com.villagepax.entity;

import com.villagepax.VillagePax;
import com.villagepax.core.Safely;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Связывает данные жителей с их телами.
 * <p>
 * Загрузился чанк — у живущих в нём жителей появляются тела. Обратный путь
 * идёт не отсюда: состояние возвращает в данные сама сущность в
 * {@link CitizenEntity#remove}, потому что тело может исчезнуть и без выгрузки
 * чанка — от смерти, от остановки сервера, от выгрузки секции сущностей,
 * которая с 1.17 живёт своей жизнью.
 */
public final class CitizenSpawner {

    private CitizenSpawner() {
    }

    public static void register() {
        // Под оградкой: чанк грузится посреди чего угодно, и исключение
        // при появлении одного жителя не должно ронять загрузку мира.
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> Safely.run(chunk.getPos(),
                "Появление жителей в чанке", () -> onChunkLoad(world, chunk.getPos())));

        // Подстраховка к CitizenEntity.remove: Fabric обещает, что это событие
        // приходит до удаления сущности из мира, а порядок событий чанков
        // относительно секций сущностей ничем не гарантирован.
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof CitizenEntity body) {
                Safely.run(body.getUuid(), "Возврат тела в запись", () -> body.writeBackTo(world));
            }
        });
    }

    public static int onChunkLoad(ServerWorld world, ChunkPos chunk) {
        SettlementManager manager = SettlementManager.get(world);
        int spawned = 0;

        for (Settlement settlement : manager.all()) {
            if (!settlement.claims(chunk)) {
                continue;
            }
            for (Citizen citizen : settlement.citizens()) {
                if (!livesIn(settlement, citizen, chunk) || hasLiveBody(world, citizen)) {
                    continue;
                }
                if (spawnBody(world, settlement, citizen) != null) {
                    spawned++;
                }
            }
        }
        return spawned;
    }

    public static int onChunkUnload(ServerWorld world, WorldChunk chunk) {
        return unloadChunk(world, chunk.getPos());
    }

    /**
     * Убирает тела жителей в указанном чанке. Состояние возвращают сами тела
     * в {@link CitizenEntity#remove}, поэтому здесь достаточно их отпустить.
     */
    public static int unloadChunk(ServerWorld world, ChunkPos chunk) {
        List<CitizenEntity> bodies = world.getEntitiesByType(
                ModEntities.CITIZEN,
                new Box(chunk.getStartX(), world.getBottomY(), chunk.getStartZ(),
                        chunk.getEndX() + 1, world.getTopY(), chunk.getEndZ() + 1),
                entity -> true);

        for (CitizenEntity body : bodies) {
            body.discard();
        }
        return bodies.size();
    }

    public static CitizenEntity spawnBody(ServerWorld world, Settlement settlement, Citizen citizen) {
        CitizenEntity body = ModEntities.CITIZEN.create(world);
        if (body == null) {
            VillagePax.LOGGER.error("Не удалось создать тело жителя {}", citizen.fullName());
            return null;
        }

        Vec3d where = spawnPosition(settlement, citizen);
        body.refreshPositionAndAngles(where.x, where.y, where.z, world.random.nextFloat() * 360f, 0f);
        body.link(settlement.id(), citizen.id());
        body.applyFrom(citizen);
        tether(body, settlement);

        if (!world.spawnEntity(body)) {
            VillagePax.LOGGER.error("Мир отказался принять тело жителя {}", citizen.fullName());
            return null;
        }

        citizen.setEntityUuid(body.getUuid());
        return body;
    }

    /**
     * Тело без поселения: кукла.
     * <p>
     * Нужна обозу. Житель принадлежит поселению — его кормят, ему дают
     * кровать, его тикает стратегия и тянет домой привязь. Торговец
     * пришёл на день из деревни за пятьсот блоков: всё это ему не нужно
     * и всё это его бы утащило. Поэтому у куклы нет ни записи жителя,
     * ни привязи, и стратегия её не видит вовсе — она не в списке
     * жителей ни одного поселения.
     */
    public static CitizenEntity spawnPuppet(ServerWorld world, BlockPos where) {
        CitizenEntity body = ModEntities.CITIZEN.create(world);
        if (body == null) {
            VillagePax.LOGGER.error("Не удалось создать тело торговца");
            return null;
        }

        body.refreshPositionAndAngles(where.getX() + 0.5, where.getY(), where.getZ() + 0.5,
                world.random.nextFloat() * 360f, 0f);
        if (!world.spawnEntity(body)) {
            VillagePax.LOGGER.error("Мир отказался принять тело торговца");
            return null;
        }
        return body;
    }

    /**
     * Где житель на самом деле: тело, если оно есть, иначе запись.
     * <p>
     * Место в записи пишется, когда тело <b>исчезает</b> — при выгрузке
     * чанка и смерти, — а пока тело ходит, в записи лежит место, где оно
     * появилось. У новой деревни это её центр для всех. Проверка «игрок
     * рядом с выдающим» читала запись: купец уходил к ларьку, игрок жал
     * «купить», стоя рядом с ним, — и слышал «слишком далеко», если ларёк
     * дальше восьми шагов от центра; а встав на место появления, мог
     * сдавать квест старейшине, ушедшему на другой конец деревни.
     */
    public static Optional<Vec3d> whereNow(ServerWorld world, Citizen citizen) {
        return citizen.entityUuid()
                .map(world::getEntity)
                .map(Entity::getPos)
                .or(citizen::position);
    }

    /**
     * Житель не должен уходить за границы своего поселения.
     * <p>
     * Дело не в реализме: тела появляются только в тех чанках, которые
     * поселение считает своими, и ушедший за границу житель больше никогда
     * не получил бы тела — он остался бы записью в данных, занимающей место
     * в населении, но невидимой и недостижимой.
     */
    private static void tether(CitizenEntity body, Settlement settlement) {
        body.setPositionTarget(settlement.center(), tetherRange(settlement));
    }

    /**
     * Докуда житель вправе уйти от ратуши вообще — граница поселения в блоках.
     * <p>
     * Это «не дальше», а не «где гулять»: где гулять, решает стратегия
     * по делу жителя, и её привязь короче (см. {@code WorkTicker}).
     */
    public static int tetherRange(Settlement settlement) {
        return settlement.level().claimRadiusChunks() * 16;
    }

    /**
     * Где житель появится. Записанная позиция за границами поселения не
     * используется — иначе житель, оказавшийся снаружи (после смены уровня
     * поселения или правки данных руками), потерялся бы навсегда.
     */
    private static Vec3d spawnPosition(Settlement settlement, Citizen citizen) {
        Vec3d fallback = Vec3d.ofBottomCenter(settlement.center().up());
        return citizen.position()
                .filter(pos -> settlement.claims(BlockPos.ofFloored(pos)))
                .orElse(fallback);
    }

    private static boolean livesIn(Settlement settlement, Citizen citizen, ChunkPos chunk) {
        return new ChunkPos(BlockPos.ofFloored(spawnPosition(settlement, citizen))).equals(chunk);
    }

    /** Есть ли у жителя тело в мире прямо сейчас. */
    public static boolean hasLiveBody(ServerWorld world, Citizen citizen) {
        UUID entityUuid = citizen.entityUuid().orElse(null);
        return entityUuid != null && world.getEntity(entityUuid) != null;
    }
}
