package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.villagepax.core.Safely;
import com.villagepax.VillagePax;
import com.villagepax.core.config.Configs;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Рассылка карты колонии: подписи над зданиями и граница владений.
 * <p>
 * Раз в {@link #EVERY} тиков всем, кто рядом. Не по событию изменения —
 * и это осознанно: колония меняется редко, а способов забыть уведомление
 * много (достроили, снесли, улучшили, игрок вошёл в мир, игрок пришёл
 * пешком). Снимок раз в две секунды не требует помнить ни об одном из этих
 * случаев и всегда верен. Стоит он мало: десяток зданий — это несколько
 * сотен байт.
 * <p>
 * Устаревание клиент решает сам: снимок, которому больше нескольких секунд,
 * он забывает. Поэтому отдельного «сотри карту» не нужно — ни на уход
 * игрока, ни на роспуск колонии.
 */
public final class ColonyNet {

    public static final Identifier MAP = new Identifier(VillagePax.MOD_ID, "colony_map");

    /** Две секунды между снимками: подписи не бегают, торопиться некуда. */
    public static final int EVERY = 40;

    /**
     * Запас поверх владений, в чанках.
     * <p>
     * Граница должна появляться <b>до</b> того, как игрок в неё войдёт:
     * она и нужна затем, чтобы понять, где чужая земля, ещё подходя.
     */
    private static final int MARGIN_CHUNKS = 2;

    private ColonyNet() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(ColonyNet::tick);
    }

    private static void tick(ServerWorld world) {
        if (world.getTime() % EVERY != 0) {
            return;
        }

        SettlementManager manager = SettlementManager.get(world);
        if (manager.count() == 0 || world.getPlayers().isEmpty()) {
            return;
        }

        for (Settlement colony : List.copyOf(manager.all())) {
            Safely.run(colony.name(), "Карта колонии", () -> sendMap(world, colony));
        }
    }

    /** Разослать карту колонии тем, кто рядом. */
    private static void sendMap(ServerWorld world, Settlement colony) {
        NbtCompound snapshot = null;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!isNear(player, colony)) {
                continue;
            }
            if (snapshot == null) {
                // Снимок собирается один раз на колонию, и только если
                // рядом кто-то есть: пустой мир не должен стоить ничего.
                snapshot = encode(mapOf(colony));
            }

            // Буфер — свой на каждого: отправка его освобождает,
            // и второй игрок получил бы пустоту.
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeNbt(snapshot);
            ServerPlayNetworking.send(player, MAP, buf);
        }
    }

    private static boolean isNear(ServerPlayerEntity player, Settlement colony) {
        int reach = (colony.level().claimRadiusChunks() + MARGIN_CHUNKS) * 16;
        BlockPos centre = colony.center();

        double dx = player.getX() - centre.getX();
        double dz = player.getZ() - centre.getZ();
        return dx * dx + dz * dz <= (double) reach * reach;
    }

    /** Что показать про колонию: имя, границы и подписи её зданий. */
    public static ColonyMap mapOf(Settlement colony) {
        List<ColonyMap.Sign> signs = new ArrayList<>();

        // Подписи выключаются настройкой на сервере, а не у каждого клиента:
        // решает тот, кто держит мир, и тогда их не приходится присылать
        // впустую.
        for (Building building : Configs.get().buildingLabels()
                ? colony.buildings() : List.<Building>of()) {
            signAt(building).ifPresent(at -> signs.add(new ColonyMap.Sign(
                    building.type(), building.level(), at, building.isOperational())));
        }

        return new ColonyMap(colony.id(), colony.name(), colony.center(),
                colony.level().claimRadiusChunks(), signs);
    }

    /**
     * Где висит подпись: над серединой здания, на высоту схемы.
     * <p>
     * Считает сервер, а не клиент: размер здания знает схема, а схем
     * у клиента нет — они лежат в датапаке и в сеть не едут.
     */
    private static Optional<BlockPos> signAt(Building building) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }

        Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
        return Optional.of(building.anchor().add(size.getX() / 2, size.getY(), size.getZ() / 2));
    }

    private static NbtCompound encode(ColonyMap map) {
        DataResult<?> encoded = ColonyMap.CODEC.encodeStart(NbtOps.INSTANCE, map);
        return (NbtCompound) encoded.result().orElseThrow(() -> new IllegalStateException(
                "карта колонии не кодируется: "
                        + encoded.error().map(Object::toString).orElse("причина неизвестна")));
    }

    /**
     * Карта с сервера. Битая карта — не причина ронять клиент: подписей
     * не будет, а в логе останется причина.
     */
    public static Optional<ColonyMap> read(PacketByteBuf buf) {
        NbtCompound nbt = buf.readNbt();
        if (nbt == null) {
            return Optional.empty();
        }

        DataResult<ColonyMap> decoded = ColonyMap.CODEC.parse(NbtOps.INSTANCE, nbt);
        decoded.error().ifPresent(error ->
                VillagePax.LOGGER.warn("Карта колонии не читается: {}", error.message()));
        return decoded.result();
    }
}
