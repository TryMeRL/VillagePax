package com.villagepax.gametest;

import com.villagepax.VillagePax;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.HaulJob;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.Workplaces;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Нагрузка: живая колония укладывается в бюджет тика.
 * <p>
 * Бюджет записан в дизайн-документе с первого дня: <b>одно среднее
 * поселение рядом с игроком — не больше пяти миллисекунд на тик</b>.
 * До сих пор его никто не мерил, а моды такого класса умирают именно
 * от этого — от сотни жителей, каждый из которых ищет путь каждый тик.
 * <p>
 * Мерится не отдельный метод, а <b>весь тик сервера</b> — с телами,
 * поиском пути, решениями, складом и событиями конца тика: игроку
 * тормозит сервер целиком, а не одна функция. Чтобы вычесть то, что
 * к моду не относится, сперва снимается тик пустого мира, потом тот же
 * тик с колонией, и в бюджет идёт разница. Медиана, а не среднее: один
 * тик со сборкой мусора не должен ни проваливать проверку, ни прятать
 * настоящую прибавку.
 * <p>
 * Проверка стоит одна в своей партии: соседи по партии тикали бы в том
 * же сервере и ложились бы в замер.
 */
public class LoadTests extends GameTestSupport {

    /** Бюджет из дизайн-документа, миллисекунд на тик. */
    static final double BUDGET_MS = 5.0;

    /** Столько тиков хранит сервер, и столько же длится каждый замер. */
    static final int WINDOW = 100;

    /** Тела расходятся по делам, прогревается JIT: эти тики не мерятся. */
    static final int SETTLE = 60;

    /** Сколько жителей без ремесла: они гуляют, едят и спят — и тоже стоят времени. */
    static final int IDLE = 6;

    @GameTest(templateName = WIDE_STRUCTURE, batchId = "load",
            tickLimit = 2 * WINDOW + SETTLE + 40)
    public void aLivingColonyKeepsWithinItsTickBudget(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        List<Building> raised = new ArrayList<>();
        Settlement[] colony = new Settlement[1];
        double[] idle = new double[1];
        java.util.Map<java.util.UUID, Vec3d> startedAt = new java.util.HashMap<>();

        // Первый замер: мир без колонии, те же тики того же сервера.
        context.runAtTick(WINDOW + 5, () -> {
            idle[0] = medianMs(server);

            colony[0] = colonyWithBuilder(world, manager, hall);
            Settlement settled = colony[0];
            Building house = plan(settled, context.getAbsolutePos(new BlockPos(0, 8, 0)),
                    HOUSE_TYPE, BlockRotation.NONE);
            Building second = plan(settled, context.getAbsolutePos(new BlockPos(10, 8, 0)),
                    HOUSE_TYPE, BlockRotation.NONE);
            Building farm = plan(settled, context.getAbsolutePos(new BlockPos(0, 8, 10)),
                    FARM_TYPE, BlockRotation.NONE);
            for (Building site : List.of(house, second, farm)) {
                stockFor(world, settled, site == farm ? farmPlan : housePlan);
                if (BuildJob.advance(world, manager, settled.id(), site.id(), 10_000)
                        != BuildJob.Outcome.FINISHED) {
                    context.throwGameTestException("Здание не встало: мерить нечего");
                }
                raised.add(site);
            }

            // Третий дом — на самом деле строится, пока идёт замер: стройка
            // и разноска материалов — самое дорогое, что делает колония.
            Building third = plan(settled, context.getAbsolutePos(new BlockPos(20, 8, 0)),
                    HOUSE_TYPE, BlockRotation.NONE);
            stockFor(world, settled, housePlan);
            raised.add(third);

            Warehouse.of(world, settled).add(new ItemStack(Items.BREAD, 64));
            hireWithBody(world, settled, FarmJob.FARMER, context.getAbsolutePos(new BlockPos(2, 9, 12)));
            hireWithBody(world, settled, HaulJob.COURIER, hall.up());
            hireWithBody(world, settled, HaulJob.COURIER, hall.up());
            for (int i = 0; i < IDLE; i++) {
                Citizen someone = grownWith(Nature.EVEN, "Житель" + i,
                        i % 2 == 0 ? Gender.MALE : Gender.FEMALE);
                someone.setPosition(Vec3d.ofBottomCenter(hall.up()));
                settled.addCitizen(someone);
            }
            for (Citizen citizen : settled.citizens()) {
                if (bodyOf(world, settled, citizen) == null) {
                    citizen.setPosition(Vec3d.ofBottomCenter(hall.up()));
                    CitizenSpawner.spawnBody(world, settled, citizen);
                }
            }
            Housing.assignBeds(world, settled);
            Workplaces.assign(world, settled);
            for (Citizen citizen : settled.citizens()) {
                startedAt.put(citizen.id(), bodyOf(world, settled, citizen).getPos());
            }

            // Утро рабочего дня: ночью колония спит, и мерить было бы нечего.
            long day = Schedule.dayOf(world.getTimeOfDay()) + 1;
            world.setTimeOfDay(day * 24_000L + 100);
        });

        // Второй замер: тот же мир с живой колонией.
        context.runAtTick(2 * WINDOW + SETTLE + 5, () -> {
            double loaded = medianMs(server);
            double cost = loaded - idle[0];
            int people = colony[0] == null ? 0 : colony[0].population();
            // Замер дёшев лишь тогда, когда колония в нём жила: стоящие
            // столбом тела ничего не стоят и ничего не доказывают.
            int walked = 0;
            for (Citizen citizen : colony[0].citizens()) {
                Vec3d from = startedAt.get(citizen.id());
                net.minecraft.entity.Entity body = citizen.entityUuid()
                        .map(world::getEntity).orElse(null);
                if (from != null && body != null && body.getPos().distanceTo(from) > 2) {
                    walked++;
                }
            }
            int built = raised.get(raised.size() - 1).nextStep();
            VillagePax.LOGGER.info("Нагрузка: пустой мир {} мс на тик, с колонией из {} жителей"
                            + " {} мс, прибавка {} мс при бюджете {} мс; ходили {}, третий дом"
                            + " на шаге {}",
                    round(idle[0]), people, round(loaded), round(cost), BUDGET_MS, walked, built);

            try {
                if (walked < people / 2) {
                    context.throwGameTestException("Замер без жизни: из " + people
                            + " жителей с места сошли " + walked);
                }
                if (cost > BUDGET_MS) {
                    context.throwGameTestException("Колония из " + people + " жителей стоит "
                            + round(cost) + " мс на тик при бюджете " + BUDGET_MS
                            + " (пустой мир " + round(idle[0]) + ", с колонией "
                            + round(loaded) + ")");
                }
            } finally {
                if (colony[0] != null) {
                    discardBodies(world, colony[0]);
                    for (Building site : raised) {
                        demolish(world, site, site.type().equals(FARM_TYPE) ? farmPlan : housePlan);
                    }
                    cleanUpVillage(world, manager, colony[0], hall, List.of());
                }
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /** Медиана длины тика за последние {@link #WINDOW} тиков, миллисекунд. */
    private static double medianMs(MinecraftServer server) {
        long[] lengths = server.lastTickLengths.clone();
        Arrays.sort(lengths);
        return (lengths[lengths.length / 2 - 1] + lengths[lengths.length / 2]) / 2.0 / 1_000_000.0;
    }

    private static double round(double ms) {
        return Math.round(ms * 100) / 100.0;
    }
}
