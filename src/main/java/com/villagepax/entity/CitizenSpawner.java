package com.villagepax.entity;

import com.villagepax.VillagePax;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.List;
import java.util.UUID;

/**
 * Связывает данные жителей с их телами по загрузке и выгрузке чанков.
 * <p>
 * Загрузился чанк — у живущих в нём жителей появляются тела; выгрузился —
 * тела возвращают состояние в данные и исчезают. Поселение за горизонтом
 * не стоит серверу ничего, а вернувшийся игрок застаёт жителей там же,
 * где оставил.
 */
public final class CitizenSpawner {

    private CitizenSpawner() {
    }

    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> onChunkLoad(world, chunk.getPos()));
        ServerChunkEvents.CHUNK_UNLOAD.register(CitizenSpawner::onChunkUnload);
    }

    public static int onChunkLoad(ServerWorld world, ChunkPos chunk) {
        SettlementManager manager = SettlementManager.get(world);
        int spawned = 0;

        for (Settlement settlement : manager.all()) {
            if (!settlement.claims(chunk)) {
                continue;
            }
            for (Citizen citizen : settlement.citizens()) {
                if (!livesIn(settlement, citizen, chunk)) {
                    continue;
                }
                if (hasLiveBody(world, citizen)) {
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

    public static int unloadChunk(ServerWorld world, ChunkPos chunk) {
        List<CitizenEntity> bodies = world.getEntitiesByType(
                ModEntities.CITIZEN,
                new net.minecraft.util.math.Box(
                        chunk.getStartX(), world.getBottomY(), chunk.getStartZ(),
                        chunk.getEndX() + 1, world.getTopY(), chunk.getEndZ() + 1),
                entity -> true);

        for (CitizenEntity body : bodies) {
            body.writeBackTo(world);
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

        if (!world.spawnEntity(body)) {
            VillagePax.LOGGER.error("Мир отказался принять тело жителя {}", citizen.fullName());
            return null;
        }

        citizen.setEntityUuid(body.getUuid());
        return body;
    }

    /** Житель без записанной позиции считается стоящим у ратуши. */
    private static Vec3d spawnPosition(Settlement settlement, Citizen citizen) {
        return citizen.position().orElseGet(() -> Vec3d.ofBottomCenter(settlement.center().up()));
    }

    private static boolean livesIn(Settlement settlement, Citizen citizen, ChunkPos chunk) {
        Vec3d position = spawnPosition(settlement, citizen);
        return new ChunkPos(BlockPos.ofFloored(position)).equals(chunk);
    }

    private static boolean hasLiveBody(ServerWorld world, Citizen citizen) {
        UUID entityUuid = citizen.entityUuid().orElse(null);
        return entityUuid != null && world.getEntity(entityUuid) != null;
    }
}
