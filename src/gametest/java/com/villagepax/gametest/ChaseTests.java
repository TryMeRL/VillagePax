package com.villagepax.gametest;

import com.villagepax.core.festival.ContestKind;
import com.villagepax.core.festival.Critter;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.festival.FestivalCritters;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.festival.Chase;
import com.villagepax.sim.festival.Match;
import com.villagepax.sim.festival.Matches;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Ловля: зверьки носятся по загону, их ловят щелчком и вплотную.
 * <p>
 * Ярмарка — на своём лугу, как у поиска: загон стоит на земле.
 */
public class ChaseTests extends GameTestSupport {

    private static Chase startChase(TestContext context, ServerWorld world, FairGround ground,
                                    PlayerEntity player) {
        return (Chase) startContest(context, world, ground, player, ContestKind.CHASE);
    }

    /**
     * Поросёнок удирает от ловца, но из загона не выходит.
     * <p>
     * Загон — пять на пять клеток на ровном лугу, без борта: держит его
     * в загоне поведение, а не забор. Ловец — житель, замерший у края:
     * за сто тиков поросёнок хоть раз отбегает от него на два блока дальше,
     * чем стоял, и все сто тиков стоит в клетках загона.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "chase", tickLimit = 200)
    public void aCritterRunsFromAChaserButStaysInThePen(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        List<BlockPos> meadow = new ArrayList<>();
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                meadow.add(at);
            }
        }
        List<BlockPos> pen = new ArrayList<>();
        for (int x = 12; x <= 16; x++) {
            for (int z = 12; z <= 16; z++) {
                pen.add(context.getAbsolutePos(new BlockPos(x, 2, z)));
            }
        }
        BlockPos hall = context.getAbsolutePos(new BlockPos(30, 2, 30));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen doll = hireWithBody(world, colony, new Identifier("villagepax", "farmer"),
                context.getAbsolutePos(new BlockPos(14, 2, 10)));
        CitizenEntity chaser = bodyOf(world, colony, doll);
        chaser.setAiDisabled(true);
        MobEntity piglet = FestivalCritters.spawn(world, Critter.PIG, pen.get(12), pen, null);
        if (piglet == null) {
            context.throwGameTestException("Поросёнок не вышел в загон");
            return;
        }
        double start = piglet.distanceTo(chaser);
        double[] farthest = {start};
        List<String> strayed = new ArrayList<>();
        for (int tick = 1; tick <= 100; tick++) {
            int at = tick;
            context.runAtTick(tick, () -> {
                farthest[0] = Math.max(farthest[0], piglet.distanceTo(chaser));
                boolean inPen = pen.stream().anyMatch(cell ->
                        Math.abs(piglet.getX() - cell.getX() - 0.5) <= 0.7
                                && Math.abs(piglet.getZ() - cell.getZ() - 0.5) <= 0.7);
                if (!inPen && strayed.size() < 3) {
                    strayed.add("тик " + at + ": " + piglet.getPos());
                }
            });
        }
        context.runAtTick(101, () -> {
            try {
                if (!strayed.isEmpty()) {
                    context.throwGameTestException("Поросёнок вышел из загона: " + strayed);
                }
                if (farthest[0] - start < 2) {
                    context.throwGameTestException("Поросёнок не удирает: стоял в " + start
                            + ", дальше всего " + farthest[0]);
                }
            } finally {
                piglet.discard();
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
                meadow.forEach(at -> world.setBlockState(at, Blocks.AIR.getDefaultState()));
            }
            context.complete();
        });
    }

    /** Щелчок участника ловит зверька: очко, и зверёк исчезает в облачке. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "chase")
    public void aClickCatchesACritter(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            PlayerEntity player = playerAt(context, ground.place().counter());
            Chase chase = startChase(context, world, ground, player);
            pastTheCountdown(world);
            MobEntity critter = chase.critters(world).get(0);
            Matches.grab(world, critter, player);
            if (chase.scoreOf(player.getUuid()) != 1) {
                context.throwGameTestException("Пойманный зверёк не дал очка: " + chase.scoreOf(player.getUuid()));
            }
            if (!critter.isRemoved()) {
                context.throwGameTestException("Пойманный зверёк остался в загоне");
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /** Житель-соперник ловит, подобравшись вплотную, — очко ему, а не игроку. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "chase")
    public void aRivalCatchesByComingClose(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            Citizen rival = hireWithBody(world, ground.village(), new Identifier("villagepax", "farmer"),
                    ground.place().counter());
            PlayerEntity player = playerAt(context, ground.place().counter());
            Chase chase = startChase(context, world, ground, player);
            if (!chase.isRival(rival.id())) {
                context.throwGameTestException("Взрослый у ярмарки не стал соперником ловли");
            }
            pastTheCountdown(world);
            MobEntity critter = chase.critters(world).get(0);
            CitizenEntity body = bodyOf(world, ground.village(), rival);
            body.refreshPositionAndAngles(critter.getX() + 0.6, critter.getY(), critter.getZ(), 0, 0);
            for (int tick = 0; tick < Match.STEER_EVERY; tick++) {
                Matches.tick(world);
            }
            if (chase.scoreOf(rival.id()) != 1 || chase.scoreOf(player.getUuid()) != 0) {
                context.throwGameTestException("Вплотную к зверьку: соперник " + chase.scoreOf(rival.id())
                        + ", игрок " + chase.scoreOf(player.getUuid()));
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /** Загон засыпан землёй — бегать негде, и ловля недоступна. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "chase")
    public void aFilledPenMakesTheChaseUnavailable(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            ground.place().pen().forEach(cell -> world.setBlockState(cell, Blocks.DIRT.getDefaultState()));
            PlayerEntity player = playerAt(context, ground.place().counter());
            Matches.Verdict verdict = Matches.start(world, player, ground.village(),
                    contestIndex(ground.village(), ContestKind.CHASE), FAIR_DAY, FAIR_MORNING);
            if (verdict != Matches.Verdict.NO_ROOM) {
                context.throwGameTestException("Засыпанный загон, а ловля: " + verdict);
            }
        } finally {
            ground.place().pen().forEach(cell -> world.setBlockState(cell, Blocks.AIR.getDefaultState()));
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /** Зверьки ловли не сохраняются: выгрузка чанка не оставит поросёнка без ловли. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "chase")
    public void critterBodiesAreNotSaved(TestContext context) {
        for (Critter critter : Critter.values()) {
            EntityType<?> type = FestivalCritters.typeOf(critter);
            if (type.isSaveable() || type.isSummonable()) {
                context.throwGameTestException(critter + ": тип зверька сохраняется или призывается");
            }
        }
        context.complete();
    }
}
