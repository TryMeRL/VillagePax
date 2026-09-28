package com.villagepax.sim.games;

import com.villagepax.block.ModBlocks;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.build.Access;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.work.CraftJob;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Место игры поселения — одно: у игорного стола, если он есть; иначе у двери
 * достроенной пивной; иначе у ратуши.
 * <p>
 * Одно, а не всякий стол и всякая пивная: вечерняя компания должна быть
 * <b>где-то</b>, и игрок, пришедший вечером в деревню, находит её там же,
 * где вчера. Стол — первым: его ставят ради игры, и он говорит «здесь
 * играют» раньше, чем кто-нибудь бросит кость.
 */
public final class GameSpot {

    /** Стол у ратуши — не ближе стольких шагов от её стены: у самых дверей тесно. */
    public static final int TABLE_FROM = 3;

    /** И не дальше: дальше это уже не площадь, а чужой двор. */
    public static final int TABLE_TO = 5;

    private GameSpot() {
    }

    /** На что смотрит компания: стол, клетка перед дверью пивной или ратуша. */
    public static BlockPos of(ServerWorld world, SettlementManager manager, Settlement settlement) {
        return table(world, manager, settlement)
                .or(() -> breweryDoor(settlement))
                .orElse(settlement.center());
    }

    /**
     * Игорный стол из убранства — если он стоит.
     * <p>
     * Спрашивается мир, а не память: стол, разбитый игроком, — не стол,
     * и компания уходит к пивной. Незагруженный чанк не спрашивается вовсе —
     * спрос загрузил бы его здесь и сейчас.
     */
    public static Optional<BlockPos> table(ServerWorld world, SettlementManager manager,
                                           Settlement settlement) {
        for (BlockPos at : manager.decorOf(settlement.id())) {
            if (world.isChunkLoaded(at) && world.getBlockState(at).isOf(ModBlocks.GAME_TABLE)) {
                return Optional.of(at);
            }
        }
        return Optional.empty();
    }

    /** Клетка перед дверью достроенной пивной: там, где из неё выходят. */
    public static Optional<BlockPos> breweryDoor(Settlement settlement) {
        // Народ из снятого датапака: пивной у него больше нет, и не надо.
        Culture culture = CultureManager.get(settlement.culture());
        if (culture == null) {
            return Optional.empty();
        }
        Optional<Identifier> type = BuildingTypes.workplaceOf(culture.buildings(), CraftJob.BREWER);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        for (Building building : settlement.buildings()) {
            if (building.progress() != BuildProgress.DONE || !building.type().equals(type.get())) {
                continue;
            }
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            List<BlockPos> doors = Access.entrances(building, schematic);
            if (!doors.isEmpty()) {
                BlockPos door = doors.get(0);
                return Optional.of(door.offset(Access.awayFrom(building, schematic, door)));
            }
        }
        return Optional.empty();
    }

    /**
     * Сколько шагов от ратуши — от её стены, а не от блока внутри.
     * <p>
     * Блок ратуши деревни стоит в зале, и «три шага от ратуши», считанные
     * от него, у большого зала пришлись бы на его же пол. Без здания ратуши
     * (колония с блоком на лугу) мерится от блока.
     */
    public static int hallDistance(Settlement settlement, BlockPos at) {
        int[] box = hallBox(settlement);
        int dx = Math.max(Math.max(box[0] - at.getX(), 0), at.getX() - box[2]);
        int dz = Math.max(Math.max(box[1] - at.getZ(), 0), at.getZ() - box[3]);
        return Math.max(dx, dz);
    }

    /** След ратуши: {минимум x, минимум z, максимум x, максимум z}. */
    public static int[] hallBox(Settlement settlement) {
        for (Building building : settlement.buildings()) {
            if (!BuildingTypes.isTownHall(building.type())) {
                continue;
            }
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
            BlockPos a = building.anchor();
            return new int[]{a.getX(), a.getZ(), a.getX() + size.getX() - 1, a.getZ() + size.getZ() - 1};
        }
        BlockPos centre = settlement.center();
        return new int[]{centre.getX(), centre.getZ(), centre.getX(), centre.getZ()};
    }

    /**
     * Метка «стол поставлен» в памяти убранства.
     * <p>
     * Раз и навсегда: разбитый игроком стол заново не встаёт, как не
     * вырастает второй колодец. Опознаватель выводится из деревни, и потому
     * метка живёт там же, где метки убранных домов, — отдельной памяти
     * для одного стола не нужно.
     */
    public static UUID tableMarker(UUID village) {
        return UUID.nameUUIDFromBytes(("villagepax:game_table/" + village)
                .getBytes(StandardCharsets.UTF_8));
    }
}
