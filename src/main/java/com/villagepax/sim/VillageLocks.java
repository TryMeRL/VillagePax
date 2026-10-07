package com.villagepax.sim;

import com.villagepax.block.entity.TownHallBlockEntity;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.diplomacy.Citizenship;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Сундуки деревни народа — не для прохожего.
 * <p>
 * Деревни народов нарочно не защищены от игрока: их берут набегом
 * и походом, и стены у них можно ломать. Но вместе со стенами открытыми
 * стояли и сундуки — склад деревни с её хлебом, монетой, элем и сукном.
 * Игрок, которому дружба нужна ради товара по своей цене, просто выносил
 * товар из амбара, и доверие теряло смысл: зачем зарабатывать то, что
 * лежит под рукой.
 * <p>
 * Теперь сундук, бочка и любое хранилище <b>в здании деревни</b> не
 * открываются и не ломаются чужим. Свой — гражданин в отведённом ему доме:
 * «кровать и сундук в нём — ваши». Сундук, который игрок поставил сам
 * на свободной земле деревни, остаётся его. Воронку в здание деревни
 * не поставить: иначе замок обходился бы снизу.
 * <p>
 * В творческом режиме замков нет: там строят, а не играют.
 */
public final class VillageLocks {

    private VillageLocks() {
    }

    /** Чья деревня держит этот блок на замке от этого игрока, если держит. */
    public static Optional<Settlement> lockedFor(ServerWorld world, PlayerEntity player, BlockPos pos) {
        if (player.isCreative() || player.isSpectator()) {
            return Optional.empty();
        }
        BlockEntity entity = world.getBlockEntity(pos);
        if (!(entity instanceof Inventory) && !(entity instanceof TownHallBlockEntity)) {
            return Optional.empty();
        }
        return inVillageBuilding(world, player, pos);
    }

    /** Лежит ли клетка в здании деревни народа, которое не дом этого игрока. */
    public static Optional<Settlement> inVillageBuilding(ServerWorld world, PlayerEntity player, BlockPos pos) {
        if (player.isCreative() || player.isSpectator()) {
            return Optional.empty();
        }
        Settlement village = SettlementManager.get(world).at(pos)
                .filter(settlement -> settlement.owner().isAutonomous()).orElse(null);
        if (village == null) {
            return Optional.empty();
        }
        if (village.center().equals(pos)) {
            return Optional.of(village);
        }
        Optional<Building> home = Citizenship.houseOf(village, player.getUuid());
        for (Building building : village.buildings()) {
            if (contains(building, pos)) {
                boolean mine = home.filter(house -> house.id().equals(building.id())).isPresent();
                return mine ? Optional.empty() : Optional.of(village);
            }
        }
        return Optional.empty();
    }

    /** Попадает ли клетка в коробку здания по его схеме. */
    static boolean contains(Building building, BlockPos pos) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
        if (schematic == null) {
            return false;
        }
        BlockPos near = BuildJob.worldPos(building, schematic.size(), BlockPos.ORIGIN);
        BlockPos far = BuildJob.worldPos(building, schematic.size(), new BlockPos(
                schematic.size().getX() - 1, schematic.size().getY() - 1, schematic.size().getZ() - 1));
        return BlockBox.create(near, far).contains(pos);
    }

    /** Сказать, почему нельзя. */
    public static void tell(PlayerEntity player, Settlement village) {
        player.sendMessage(Text.translatable("villagepax.lock.village", village.name()), true);
    }
}
