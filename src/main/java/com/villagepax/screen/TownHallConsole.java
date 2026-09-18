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

    /**
     * Твой ли это пульт.
     * <p>
     * Ратуша есть и у деревни народа, и щелчок по ней открывал <b>полный
     * пульт колонии</b>: вкладка стройки со списком зданий, кнопки «Заказать»,
     * «Улучшить», раздача ремёсел. И ни одна из них не работала — каждое
     * намерение сервер молча отбрасывал, потому что пульт был чужой.
     * <p>
     * Заказчик так и написал: «не могу заказать постройку и поставить
     * постройку, не грузит призрак». Кнопка, которая ничего не делает
     * и ничего не говорит, — худшее, что может быть в меню: игрок
     * не понимает, сломан мод или он сам.
     * <p>
     * Поэтому чужая ратуша пульта не открывает вовсе. С деревней говорят
     * через людей: квесты у старейшины, товар у купца.
     */
    public static boolean yours(Settlement settlement, java.util.UUID player) {
        return settlement.owner().isOwnedBy(player);
    }

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
