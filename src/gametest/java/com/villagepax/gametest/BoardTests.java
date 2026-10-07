package com.villagepax.gametest;

import com.villagepax.block.ModBlocks;
import com.villagepax.screen.BoardNet;
import com.villagepax.screen.BoardView;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Streetscape;
import com.villagepax.sim.Villages;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.Standing;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Доска заданий: стоит у ратуши каждой деревни, на ней листок
 * на каждое ремесло с просьбой, старейшина первым.
 */
public class BoardTests extends GameTestSupport {

    /** Деревня, вставшая на лугу, вешает доску у ратуши — лицом к площади. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "board")
    public void aVillageHangsItsNoticeBoard(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;
        try {
            for (int x = -24; x <= 24; x++) {
                for (int z = -24; z <= 24; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }
            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала");
                return;
            }
            BlockPos board = manager.decorOf(village.id()).stream()
                    .filter(at -> world.getBlockState(at).isOf(ModBlocks.NOTICE_BOARD))
                    .findFirst().orElse(null);
            if (board == null) {
                context.throwGameTestException("У ратуши новой деревни нет доски заданий");
                return;
            }
            Direction face = world.getBlockState(board).get(com.villagepax.block.FurnitureBlock.FACING);
            if (!Standing.canStandAt(world, board.offset(face))) {
                context.throwGameTestException("Перед доской негде встать: листки читать неоткуда");
            }
            Settlement owner = BoardNet.villageAt(manager, board).orElse(null);
            if (owner == null || !owner.id().equals(village.id())) {
                context.throwGameTestException("Доска не знает, чья она");
            }
            if (Streetscape.noticeBoard(world, manager, village).isPresent()) {
                context.throwGameTestException("Вторая доска встала у той же ратуши");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }
        context.complete();
    }

    /** На доске листок старейшины первым и листки ремёсел с поручениями. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "board")
    public void theBoardHangsASheetPerTrade(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Meadow meadow = meadow(context, world, manager);
        Settlement village = meadow.village();
        BlockPos board = meadow.hall().add(-4, 0, 0);
        try {
            Citizen elder = evenNewborn("Rollo", "", NORMAN, Gender.MALE);
            elder.setLived(Ages.grownAt());
            elder.setProfession(Villages.ELDER);
            village.addCitizen(elder);
            world.setBlockState(board, ModBlocks.NOTICE_BOARD.getDefaultState());

            UUID player = UUID.randomUUID();
            BoardView view = BoardNet.viewOf(manager, village, board, player, new SimpleInventory(9),
                    Schedule.dayOf(world.getTimeOfDay()));
            if (view.sheets().size() < 2) {
                context.throwGameTestException("На доске " + view.sheets().size()
                        + " листков, а ждали старейшину и строителя");
                return;
            }
            if (!view.sheets().get(0).giver().equals(Villages.ELDER)) {
                context.throwGameTestException("Первым висит не старейшина, а "
                        + view.sheets().get(0).giver());
            }
            for (BoardView.Sheet sheet : view.sheets()) {
                if (sheet.author().isBlank() || sheet.offer().objectives().isEmpty()) {
                    context.throwGameTestException("Пустой листок от " + sheet.giver());
                }
                if (sheet.offer().ready()) {
                    context.throwGameTestException("С пустыми руками листок " + sheet.giver()
                            + " говорит «можно отдать»");
                }
            }

            // Колония — не деревня: просить с её доски некому.
            village.setOwner(Owner.of(player));
            if (BoardNet.villageAt(manager, board).isPresent()) {
                context.throwGameTestException("Доска колонии выдаёт деревенские просьбы");
            }
        } finally {
            world.setBlockState(board, Blocks.AIR.getDefaultState());
            clearMeadow(world, manager, meadow);
        }
        context.complete();
    }
}
