package com.villagepax.gametest;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.life.Ages;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Одно тело на жителя.
 * <p>
 * Клоны были настоящей бедой: тело, зашедшее в секцию на краю прогрузки,
 * отрывалось от записи, при следующей загрузке чанка у жителя появлялось
 * второе, а первое бродило без решений с тем же именем над головой.
 * Умерший или ушедший оставлял такое же тело-призрак; страж, павший
 * в походе, воскресал дома.
 */
public class BodyTests extends GameTestSupport {

    /** Тело ушло из ведения мира, но живо: запись о нём не забывает. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bodies")
    public void aBodyOutOfTrackingKeepsItsRecord(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        try {
            Citizen citizen = grown(colony, hall);
            CitizenEntity body = CitizenSpawner.spawnBody(world, colony, citizen);

            CitizenSpawner.bodyUnloaded(world, body);
            if (!citizen.entityUuid().filter(body.getUuid()::equals).isPresent()) {
                context.throwGameTestException("Тело ушло из ведения, а запись о нём забыла: "
                        + "при следующей загрузке чанка был бы клон");
            }

            body.discard();
            if (citizen.entityUuid().isPresent()) {
                context.throwGameTestException("Убранное тело осталось в записи");
            }
            if (citizen.position().isEmpty()) {
                context.throwGameTestException("Убранное тело не оставило в записи, где стояло");
            }
            context.complete();
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
    }

    /** Второе тело одного жителя уходит само, первое и запись — целы. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bodies")
    public void aSecondBodyOfOneCitizenLeaves(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen citizen = grown(colony, hall);
        CitizenEntity stray = CitizenSpawner.spawnBody(world, colony, citizen);
        CitizenEntity body = CitizenSpawner.spawnBody(world, colony, citizen);

        context.runAtTick(45, () -> {
            try {
                if (!stray.isRemoved()) {
                    context.throwGameTestException("Лишнее тело жителя так и бродит");
                }
                if (body.isRemoved()) {
                    context.throwGameTestException("Ушло законное тело, а не лишнее");
                }
                if (!citizen.entityUuid().filter(body.getUuid()::equals).isPresent()) {
                    context.throwGameTestException("Лишнее тело, уходя, затёрло связь живого");
                }
                if (colony.citizen(citizen.id()).isEmpty()) {
                    context.throwGameTestException("Уход лишнего тела стоил жителю жизни");
                }
                context.complete();
            } finally {
                discardBodies(world, colony);
                stray.discard();
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
        });
    }

    /** Лишнее тело убили прежде, чем оно ушло: житель жив. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bodies")
    public void killingAStrayBodyBuriesNobody(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        try {
            Citizen citizen = grown(colony, hall);
            CitizenEntity stray = CitizenSpawner.spawnBody(world, colony, citizen);
            CitizenEntity body = CitizenSpawner.spawnBody(world, colony, citizen);

            stray.remove(net.minecraft.entity.Entity.RemovalReason.KILLED);
            if (colony.citizen(citizen.id()).isEmpty()) {
                context.throwGameTestException("Убит призрак, а похоронен живой житель");
            }
            if (!citizen.entityUuid().filter(body.getUuid()::equals).isPresent()) {
                context.throwGameTestException("Смерть призрака оторвала живого от записи");
            }
            context.complete();
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
    }

    /** Жителя не стало — его тело не бродит призраком. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bodies")
    public void aBodyWithoutARecordLeaves(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen citizen = grown(colony, hall);
        CitizenEntity ghost = CitizenSpawner.spawnBody(world, colony, citizen);
        colony.removeCitizen(citizen.id());

        context.runAtTick(45, () -> {
            try {
                if (!ghost.isRemoved()) {
                    context.throwGameTestException("Ушедший житель оставил тело-призрак");
                }
                context.complete();
            } finally {
                ghost.discard();
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
        });
    }

    /** Запись, потерявшая тело, подхватывается тем телом, что живёт. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bodies")
    public void anOrphanBodyIsTakenBackByItsRecord(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen citizen = grown(colony, hall);
        CitizenEntity body = CitizenSpawner.spawnBody(world, colony, citizen);
        citizen.setEntityUuid(null);

        context.runAtTick(45, () -> {
            try {
                if (body.isRemoved()) {
                    context.throwGameTestException("Сирота ушла вместо того, чтобы вернуться в запись");
                }
                if (!citizen.entityUuid().filter(body.getUuid()::equals).isPresent()) {
                    context.throwGameTestException("Запись так и не нашла живое тело своего жителя");
                }
                context.complete();
            } finally {
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
        });
    }

    /** Страж колонии, павший в походе, домой не возвращается. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bodies")
    public void aGuardKilledOnCampaignStaysDead(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        try {
            Citizen soldier = grown(colony, hall);
            CitizenEntity body = CitizenSpawner.spawnBody(world, colony, soldier);
            body.linkRaid(UUID.randomUUID(), UUID.randomUUID());

            body.remove(net.minecraft.entity.Entity.RemovalReason.KILLED);
            if (colony.citizen(soldier.id()).isPresent()) {
                context.throwGameTestException("Павший в походе остался в колонии — "
                        + "дома он встал бы снова");
            }
            context.complete();
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
    }

    /** Раненый житель лечится сам: прежде рана копилась годами и убивала. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bodies", tickLimit = 300)
    public void aWoundedCitizenHeals(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen citizen = grown(colony, hall);
        CitizenEntity body = CitizenSpawner.spawnBody(world, colony, citizen);
        body.setHealth(8.0f);

        context.runAtTick(230, () -> {
            try {
                if (body.getHealth() <= 8.0f) {
                    context.throwGameTestException("Раненый житель не лечится: " + body.getHealth());
                }
                if (body.getSafeFallDistance() > 3) {
                    context.throwGameTestException("Житель готов прыгать с высоты "
                            + body.getSafeFallDistance());
                }
                context.complete();
            } finally {
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
        });
    }

    private static Citizen grown(Settlement colony, BlockPos hall) {
        Citizen citizen = evenNewborn("Тёзка", "", NORMAN, Gender.MALE);
        citizen.setLived(Ages.grownAt());
        citizen.setPosition(Vec3d.ofBottomCenter(hall.add(2, 0, 2)));
        colony.addCitizen(citizen);
        return citizen;
    }
}
