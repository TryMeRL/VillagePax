package com.villagepax.gametest;

import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.festival.Fair;
import com.villagepax.sim.festival.Fairs;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkTicker;
import com.villagepax.sim.work.Workplaces;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Праздник на ярмарке: места праздника, затейник, день праздника,
 * состязания и призы.
 */
public class FestivalTests extends GameTestSupport {

    /**
     * У каждого народа есть ярмарка, и у ярмарки — все места праздника.
     * <p>
     * Места спрашиваются у схемы, а не у мира, поэтому проверка обходится
     * без единого блока: {@link Fairs} читает сердце, загон, черту, мишени
     * и столы по плану стройки. Числа — решение по игре: загон не меньше
     * пяти на пять (трём зверькам есть где удирать), ровно три мишени
     * (ближняя, средняя, дальняя), хоть одна черта и один стол.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "festival")
    public void everyPeopleHasAFairWithAllItsPlaces(TestContext context) {
        List<String> complaints = new ArrayList<>();
        for (Identifier culture : CultureManager.all().keySet()) {
            Identifier type = new Identifier(culture.getNamespace(), culture.getPath() + "/fairground");
            if (BuildingTypes.get(type).isEmpty()) {
                complaints.add(culture + ": нет ярмарки");
                continue;
            }
            Building site = Building.planned(type, BlockPos.ORIGIN, BlockRotation.NONE);
            Fair fair = Fairs.of(site).orElse(null);
            if (fair == null) {
                complaints.add(culture + ": у ярмарки нет сердца или прилавка");
                continue;
            }
            if (fair.pen().size() < 25) {
                complaints.add(culture + ": загон меньше 5x5 — клеток " + fair.pen().size());
            }
            if (fair.targets().size() != 3) {
                complaints.add(culture + ": мишеней " + fair.targets().size());
            }
            if (fair.shooting().isEmpty()) {
                complaints.add(culture + ": негде встать стрелку");
            }
            if (fair.tables().isEmpty()) {
                complaints.add(culture + ": некуда ставить пироги");
            }
            if (!fair.area().contains(fair.heart().toCenterPos())) {
                complaints.add(culture + ": сердце праздника вне ярмарки");
            }
        }
        if (!complaints.isEmpty()) {
            context.throwGameTestException("Ярмарки народов:\n  " + String.join("\n  ", complaints));
        }
        context.complete();
    }

    static final Identifier NORMAN_FAIR = new Identifier("villagepax", "norman/fairground");
    static final Identifier NORMAN_FAIR_PLAN = new Identifier("villagepax", "norman/fairground_lvl1");

    /**
     * Затейник стоит у своего прилавка на ярмарке, и в руке у него мячики.
     * <p>
     * Мячики — вывеска ремесла, как монета у купца: по ним затейника
     * узнают с другого конца деревни, не читая подписи. Ярмарка ставится
     * в воздухе — так проверка не трогает землю соседей по партии.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "festival")
    public void anEntertainerKeepsHisCounterWithBallsInHand(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 8, 4));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Schematic plan = schematic(context, NORMAN_FAIR_PLAN);
        Building fair = null;
        try {
            fair = standUp(context, world, manager, colony, NORMAN_FAIR, anchor);
            BlockPos counter = Workplaces.stations(fair).get(0);
            Citizen jester = hireWithBody(world, colony, Villages.ENTERTAINER, counter);
            Workplaces.assign(world, colony);
            if (!jester.workplace().equals(Optional.of(fair.id()))) {
                context.throwGameTestException("Затейник не получил ярмарку своей мастерской");
            }
            CitizenEntity body = bodyOf(world, colony, jester);
            WorkTicker.decide(world, manager, colony, jester, Schedule.MORNING_WORK);
            if (body.workTarget() == null
                    || body.workTarget().getSquaredDistance(counter) > 2.25) {
                context.throwGameTestException("Затейник не у прилавка: цель " + body.workTarget()
                        + ", прилавок " + counter);
            }
            if (!body.getMainHandStack().isOf(ModFestivalItems.JUGGLING_BALLS)) {
                context.throwGameTestException("В руке у затейника не мячики, а "
                        + body.getMainHandStack());
            }
        } finally {
            if (fair != null) {
                demolish(world, fair, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }
}
