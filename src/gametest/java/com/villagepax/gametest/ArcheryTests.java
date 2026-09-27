package com.villagepax.gametest;

import com.villagepax.core.festival.ContestKind;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.item.festival.FestivalBowItem;
import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.festival.Archery;
import com.villagepax.sim.festival.ArcheryScore;
import com.villagepax.sim.festival.Fair;
import com.villagepax.sim.festival.Matches;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Стрельба: очки по месту попадания и дальности, стрелы жителей считает
 * та же мишень, а праздничный лук не переживает состязания.
 */
public class ArcheryTests extends GameTestSupport {

    private static Archery startArchery(TestContext context, ServerWorld world, FairGround ground,
                                        PlayerEntity player) {
        return (Archery) startContest(context, world, ground, player, ContestKind.ARCHERY);
    }

    /** Мишени по дальности от черты: ближняя, средняя, дальняя. */
    private static List<BlockPos> byRange(Fair fair) {
        BlockPos line = fair.shooting().get(0);
        return fair.targets().stream()
                .sorted(Comparator.comparingInt(target -> ArcheryScore.range(fair.targets(), line, target)))
                .toList();
    }

    /** Середина дальней мишени — пять очков, втрое за дальность: пятнадцать. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "archery", tickLimit = 200)
    public void aBullseyeScoresFiveTimesTheRange(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        PlayerEntity player;
        Archery archery;
        try {
            player = playerAt(context, ground.place().counter());
            archery = startArchery(context, world, ground, player);
            pastTheCountdown(world);
            BlockPos far = byRange(ground.place()).get(2);
            PersistentProjectileEntity arrow = Archery.festivalArrow(world, player);
            arrow.refreshPositionAndAngles(far.getX() + 0.5, far.getY() + 0.5, far.getZ() + 1.5, 0, 0);
            arrow.setVelocity(0, 0, -1.5);
            world.spawnEntity(arrow);
        } catch (RuntimeException failed) {
            clearFairGround(context, world, manager, ground);
            throw failed;
        }
        context.runAtTick(10, () -> {
            try {
                int score = archery.scoreOf(player.getUuid());
                if (score != 15) {
                    context.throwGameTestException("Середина дальней мишени дала " + score
                            + " — ждали 5 × 3 = 15");
                }
            } finally {
                clearFairGround(context, world, manager, ground);
            }
            context.complete();
        });
    }

    /**
     * Стрелу жителя считает та же мишень, что и стрелу игрока, — и очки
     * идут ему, а не игроку. Промахнулся — ноль честно: ни одна его стрела
     * не торчит в мишени.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "archery", tickLimit = 200)
    public void aRivalsArrowCountsOnTheSameTarget(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        PlayerEntity player;
        Archery archery;
        Citizen rival;
        try {
            rival = hireWithBody(world, ground.village(), new Identifier("villagepax", "farmer"),
                    ground.place().counter());
            player = playerAt(context, ground.place().counter());
            archery = startArchery(context, world, ground, player);
            if (!archery.isRival(rival.id())) {
                context.throwGameTestException("Взрослый у ярмарки не стал соперником стрельбы");
            }
            pastTheCountdown(world);
            CitizenEntity body = bodyOf(world, ground.village(), rival);
            BlockPos line = ground.place().shooting().get(0);
            body.refreshPositionAndAngles(line.getX() + 0.5, line.getY(), line.getZ() + 0.5, 180, 0);
            archery.shoot(world, body, rival, byRange(ground.place()).get(1));
        } catch (RuntimeException failed) {
            clearFairGround(context, world, manager, ground);
            throw failed;
        }
        context.runAtTick(60, () -> {
            try {
                if (archery.scoreOf(player.getUuid()) != 0) {
                    context.throwGameTestException("Стрела соперника засчитана игроку: "
                            + archery.scoreOf(player.getUuid()));
                }
                boolean stuck = ground.place().targets().stream().anyMatch(target ->
                        !world.getEntitiesByClass(PersistentProjectileEntity.class,
                                new Box(target).expand(0.3), arrow -> arrow.getOwner() == bodyOf(world,
                                        ground.village(), rival)).isEmpty());
                if (stuck && archery.scoreOf(rival.id()) == 0) {
                    context.throwGameTestException("Стрела соперника в мишени, а очков у него нет");
                }
            } finally {
                clearFairGround(context, world, manager, ground);
            }
            context.complete();
        });
    }

    /**
     * Праздничный лук не переживает состязания: после конца его нет
     * в руках, а лук чужого, уже кончившегося состязания рассыпается сам.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "archery")
    public void theFestivalBowCrumblesAfterTheMatch(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            PlayerEntity player = playerAt(context, ground.place().counter());
            Archery archery = startArchery(context, world, ground, player);
            if (player.getInventory().count(ModFestivalItems.FESTIVAL_BOW) != 1) {
                context.throwGameTestException("Стрелку не дали праздничного лука");
            }
            archery.finish(world);
            if (player.getInventory().count(ModFestivalItems.FESTIVAL_BOW) != 0) {
                context.throwGameTestException("Праздничный лук пережил состязание");
            }
            // Вставка переносит несложимый предмет в ячейку копией, а сам
            // стек опустошает: тикать надо то, что лежит в ячейке.
            player.getInventory().insertStack(FestivalBowItem.forMatch(UUID.randomUUID(), 8));
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                ItemStack held = player.getInventory().getStack(slot);
                if (held.isOf(ModFestivalItems.FESTIVAL_BOW)) {
                    held.inventoryTick(world, player, slot, false);
                }
            }
            if (player.getInventory().count(ModFestivalItems.FESTIVAL_BOW) != 0) {
                context.throwGameTestException("Лук чужого, кончившегося состязания не рассыпался");
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /** Мишень сломана — стрелять не во что, и стрельба недоступна. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "archery")
    public void aBrokenTargetClosesTheRange(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            world.setBlockState(ground.place().targets().get(0), Blocks.AIR.getDefaultState());
            PlayerEntity player = playerAt(context, ground.place().counter());
            Matches.Verdict verdict = Matches.start(world, player, ground.village(),
                    contestIndex(ground.village(), ContestKind.ARCHERY), FAIR_DAY, FAIR_MORNING);
            if (verdict != Matches.Verdict.NO_ROOM) {
                context.throwGameTestException("Мишень сломана, а стрельба: " + verdict);
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }
}
