package com.villagepax.sim.festival;

import com.villagepax.VillagePax;
import com.villagepax.block.ModBlocks;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

/**
 * Праздничный стол: пироги на рассвете праздника, уборка наутро.
 * <p>
 * Стол накрывается раз в праздник и только на свободное место над столом
 * ярмарки. Съеденный пирог в тот же день не появляется снова: иначе стол
 * праздника был бы бесконечной кухней, и за пирогами ходили бы, а не на праздник.
 * <p>
 * Уборка трогает только то, что там <b>всё ещё наше</b>: на месте съеденного
 * пирога игрок вправе поставить свой цветок. Не загружен чанк — запись
 * остаётся до того дня, когда ярмарку увидят снова: убирать вслепую
 * значило бы грузить чужие чанки посреди тика.
 */
public final class Feast {

    public static final Identifier PIE = new Identifier(VillagePax.MOD_ID, "feast_pie");

    private Feast() {
    }

    /** Накрыть стол, если сегодня праздник, и убрать прошлый. */
    public static void tend(ServerWorld world, SettlementManager manager, Settlement settlement,
                            long day) {
        Optional<SettlementManager.Festive> memory = manager.festiveOf(settlement.id());
        boolean laidToday = memory.map(festive -> festive.day() == day).orElse(false);
        if (memory.isPresent() && !laidToday) {
            clearUp(world, manager, settlement, memory.get());
        }
        if (laidToday || !FestivalDay.isOn(settlement, day)) {
            return;
        }
        Fair fair = Fairs.of(settlement).orElse(null);
        if (fair == null || !world.isChunkLoaded(fair.heart())) {
            return;
        }
        manager.startFestive(settlement.id(), day);
        for (BlockPos table : fair.tables()) {
            BlockPos spot = table.up();
            if (world.getBlockState(table).isOf(ModBlocks.TABLE) && world.getBlockState(spot).isAir()) {
                world.setBlockState(spot, ModBlocks.FEAST_PIE.getDefaultState(), Block.NOTIFY_ALL);
                manager.recordFestive(settlement.id(), day, spot, PIE);
            }
        }
    }

    /**
     * Убрать поставленное праздником: только то, что стоит нашим, и только
     * в загруженных чанках. Забывается всё, до чего дошли руки.
     */
    public static void clearUp(ServerWorld world, SettlementManager manager, Settlement settlement,
                               SettlementManager.Festive memory) {
        for (SettlementManager.Placed placed : memory.placed()) {
            takeBack(world, manager, settlement.id(), placed);
        }
        if (manager.festiveOf(settlement.id()).map(festive -> festive.placed().isEmpty())
                .orElse(false)) {
            manager.clearFestive(settlement.id());
        }
    }

    /**
     * Убрать один блок праздника, если там всё ещё он, и забыть запись.
     * Чанк не загружен — ничего: запись дождётся, когда до неё дойдут.
     *
     * @return убран ли блок
     */
    public static boolean takeBack(ServerWorld world, SettlementManager manager, UUID village,
                                   SettlementManager.Placed placed) {
        BlockPos at = placed.pos();
        if (!world.isChunkLoaded(at)) {
            return false;
        }
        BlockState there = world.getBlockState(at);
        boolean ours = Registries.BLOCK.getId(there.getBlock()).equals(placed.block());
        if (ours) {
            world.removeBlock(at, false);
        }
        manager.forgetFestive(village, at);
        return ours;
    }
}
