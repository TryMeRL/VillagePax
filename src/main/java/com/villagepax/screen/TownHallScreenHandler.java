package com.villagepax.screen;

import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Пульт колонии: обработчик экрана ратуши.
 * <p>
 * Слотов у него нет — это не сундук, а окно в данные. Ванильный обработчик
 * взят именно за подписку: сервер держит его ровно пока экран открыт и зовёт
 * {@link #sendContentUpdates()} каждый тик. Своего канала «кто на что подписан»
 * для этого не нужно, а лишний канал пришлось бы ещё и закрывать при выходе
 * игрока — вот на этом такие подписки обычно и текут.
 * <p>
 * Снимок пересобирается раз в {@link #REFRESH_TICKS} тиков и уходит клиенту
 * <b>только при отличии</b> от отправленного: {@link TownHallView} — запись,
 * и сравнение достаётся даром.
 */
public class TownHallScreenHandler extends ScreenHandler {

    /** Полсекунды между пересборками снимка. Экран — не индикатор тиков. */
    public static final int REFRESH_TICKS = 10;

    /** Докуда можно отойти от ратуши, не закрыв экран. */
    private static final double REACH_SQUARED = 64.0;

    private final UUID settlement;
    private final BlockPos hall;

    /** Только на сервере: клиент собственного мира не видит. */
    private final ServerPlayerEntity viewer;
    private final ServerWorld world;

    private TownHallView view;
    private int cooldown;

    /** Серверная сторона: снимок уже собран открывалкой, второй раз не считаем. */
    public TownHallScreenHandler(int syncId, PlayerInventory inventory, ServerPlayerEntity viewer,
                                 ServerWorld world, Settlement settlement, BlockPos hall,
                                 TownHallView snapshot) {
        super(TownHallScreens.TOWN_HALL, syncId);
        this.settlement = settlement.id();
        this.hall = hall;
        this.viewer = viewer;
        this.world = world;
        this.view = snapshot;
        this.cooldown = REFRESH_TICKS;
    }

    /** Клиентская сторона: снимок пришёл вместе с открытием экрана. */
    public TownHallScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        super(TownHallScreens.TOWN_HALL, syncId);
        this.settlement = buf.readUuid();
        this.hall = buf.readBlockPos();
        this.viewer = null;
        this.world = null;
        this.view = TownHallNet.readView(buf);
    }

    public UUID settlement() {
        return settlement;
    }

    public BlockPos hall() {
        return hall;
    }

    /** Последний снимок. На клиенте — то, что показывает экран. */
    public TownHallView view() {
        return view;
    }

    /** Клиент получил новый снимок. */
    public void acceptView(TownHallView fresh) {
        this.view = fresh;
    }

    /**
     * Снимок пересобирается на сервере и уходит только при изменении.
     * <p>
     * Без сравнения экран отправлял бы полный снимок колонии двадцать раз
     * в секунду каждому, кто его открыл; с ним молчит, пока в колонии
     * ничего не происходит.
     */
    @Override
    public void sendContentUpdates() {
        super.sendContentUpdates();

        if (viewer == null || world == null) {
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = REFRESH_TICKS;

        Settlement colony = SettlementManager.get(world).byId(settlement).orElse(null);
        if (colony == null) {
            // Колонии больше нет: закрывать экран не наше дело — это сделает
            // canUse на следующем тике.
            return;
        }

        TownHallView fresh = TownHallView.of(world, colony);
        if (!fresh.equals(view)) {
            view = fresh;
            TownHallNet.sendView(viewer, fresh);
        }
    }

    /**
     * Экран закрывается сам, если игрок ушёл от ратуши или лишился колонии.
     * <p>
     * Проверка по расстоянию, а не по блоку: ратушу могли сломать, пока
     * экран открыт, и тогда пульт обязан закрыться, а не показывать
     * колонию, у которой больше нет центра.
     */
    @Override
    public boolean canUse(PlayerEntity player) {
        if (world == null) {
            return true;
        }
        if (SettlementManager.get(world).byId(settlement).isEmpty()) {
            return false;
        }
        return player.squaredDistanceTo(Vec3d.ofCenter(hall)) <= REACH_SQUARED;
    }

    /** Слотов нет — перекладывать нечего. */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }
}
