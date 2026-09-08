package com.villagepax.screen;

import com.villagepax.sim.Settlement;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Открывалка пульта колонии.
 * <p>
 * Снимок собирается <b>один раз</b> и достаётся и обработчику, и пакету
 * открытия: ванильный порядок вызовов — сначала {@code createMenu}, потом
 * запись данных открытия, — иначе колонию пришлось бы обходить дважды
 * на каждый щелчок по ратуше.
 */
public final class TownHallConsole implements ExtendedScreenHandlerFactory {

    private final ServerWorld world;
    private final Settlement colony;
    private final BlockPos hall;

    private TownHallView snapshot;

    public TownHallConsole(ServerWorld world, Settlement colony, BlockPos hall) {
        this.world = world;
        this.colony = colony;
        this.hall = hall;
    }

    private TownHallView snapshot() {
        if (snapshot == null) {
            snapshot = TownHallView.of(world, colony);
        }
        return snapshot;
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeUuid(colony.id());
        buf.writeBlockPos(hall);
        TownHallNet.writeView(buf, snapshot());
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("villagepax.screen.town_hall", colony.name());
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
        return new TownHallScreenHandler(syncId, inventory, (ServerPlayerEntity) player, world,
                colony, hall, snapshot());
    }
}
