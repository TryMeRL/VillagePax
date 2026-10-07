package com.villagepax.gametest;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Chatter;
import com.villagepax.sim.work.Schedule;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.Random;

/**
 * Живые реплики в мире: житель говорит правду о себе рядом с игроком,
 * не тараторит и молчит во сне.
 */
public class ChatterTests extends GameTestSupport {

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "chatter")
    public void aHungryCitizenSaysSo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        try {
            Citizen citizen = evenNewborn("Ren", "", NORMAN, Gender.MALE);
            citizen.setLived(Ages.grownAt() + 5);
            citizen.setSaturation(2);
            citizen.setPosition(Vec3d.ofBottomCenter(hall.add(2, 0, 2)));
            colony.addCitizen(citizen);
            CitizenEntity body = CitizenSpawner.spawnBody(world, colony, citizen);
            PlayerEntity player = context.createMockSurvivalPlayer();
            long day = Schedule.dayOf(world.getTimeOfDay());

            Optional<Chatter.Topic> said = Chatter.speak(world, colony, citizen, body, player, day,
                    new Random(1));
            if (said.filter(Chatter.Topic.HUNGRY::equals).isEmpty()) {
                context.throwGameTestException("Голодный житель сказал не о голоде: " + said);
            }
            if (spoken(body).filter(key -> key.startsWith("villagepax.say.hungry.")).isEmpty()) {
                context.throwGameTestException("Над головой не голод: " + spoken(body));
            }
            if (Chatter.speak(world, colony, citizen, body, player, day, new Random(2)).isPresent()) {
                context.throwGameTestException("Житель тараторит: вторая фраза сразу за первой");
            }

            Citizen sleeper = evenNewborn("Sora", "", NORMAN, Gender.FEMALE);
            sleeper.setLived(Ages.grownAt() + 5);
            sleeper.setPosition(Vec3d.ofBottomCenter(hall.add(-2, 0, 2)));
            colony.addCitizen(sleeper);
            CitizenEntity dozing = CitizenSpawner.spawnBody(world, colony, sleeper);
            dozing.setDozing(true);
            if (Chatter.speak(world, colony, sleeper, dozing, player, day, new Random(3)).isPresent()) {
                context.throwGameTestException("Спящий житель заговорил");
            }

            Chatter.Situation seen = Chatter.situation(world, colony, citizen, player, day);
            if (seen.owner() || !seen.hungry()) {
                context.throwGameTestException("Положение собрано неверно: " + seen);
            }
            context.complete();
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
    }
}
