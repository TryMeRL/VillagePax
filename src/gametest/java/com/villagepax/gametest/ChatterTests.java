package com.villagepax.gametest;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Chatter;
import com.villagepax.sim.life.Gossip;
import com.villagepax.sim.work.Schedule;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
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

    /** Вечером двое рядом судачат о голодном третьем, второй отвечает. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "chatter")
    public void neighboursGossipInTheEvening(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        try {
            // Строитель колонии тела не имеет — сплетничают только трое ниже.
            Citizen one = evenNewborn("Ren", "", NORMAN, Gender.MALE);
            Citizen two = evenNewborn("Sora", "", NORMAN, Gender.FEMALE);
            Citizen hungry = evenNewborn("Kaito", "", NORMAN, Gender.MALE);
            for (Citizen citizen : List.of(one, two, hungry)) {
                citizen.setLived(Ages.grownAt() + 5);
                colony.addCitizen(citizen);
            }
            hungry.setSaturation(2);
            one.setPosition(Vec3d.ofBottomCenter(hall.add(2, 0, 2)));
            two.setPosition(Vec3d.ofBottomCenter(hall.add(3, 0, 2)));
            hungry.setPosition(Vec3d.ofBottomCenter(hall.add(-3, 0, -3)));
            CitizenEntity oneBody = CitizenSpawner.spawnBody(world, colony, one);
            CitizenEntity twoBody = CitizenSpawner.spawnBody(world, colony, two);
            CitizenSpawner.spawnBody(world, colony, hungry);
            PlayerEntity player = context.createMockSurvivalPlayer();
            player.setPosition(Vec3d.ofCenter(hall.add(0, 1, 4)));
            long day = Schedule.dayOf(world.getTimeOfDay());

            Optional<Gossip.Item> said = Gossip.tick(world, colony, day, 11_500, List.of(player),
                    new Random(5));
            if (said.isEmpty()) {
                context.throwGameTestException("Вечером двое рядом не сказали ни слова");
                return;
            }
            boolean oneSpoke = spoken(oneBody).filter(key -> key.startsWith("villagepax.gossip.")).isPresent();
            boolean twoSpoke = spoken(twoBody).filter(key -> key.startsWith("villagepax.gossip.")).isPresent();
            if (oneSpoke == twoSpoke) {
                context.throwGameTestException("Сплетню сказал не один из двоих: " + spoken(oneBody)
                        + " / " + spoken(twoBody));
            }
            if (said.get().about().equals(oneSpoke ? "Ren" : "Sora")) {
                context.throwGameTestException("Житель сплетничает о себе: " + said.get());
            }
            Gossip.replies(world, manager, world.getTime() + Gossip.REPLY_AFTER);
            CitizenEntity hearer = oneSpoke ? twoBody : oneBody;
            if (spoken(hearer).filter(key -> key.startsWith("villagepax.gossip.reply.")).isEmpty()) {
                context.throwGameTestException("На сплетню не ответили: " + spoken(hearer));
            }
            if (Gossip.tick(world, colony, day, 3_000, List.of(player), new Random(6)).isPresent()) {
                context.throwGameTestException("Днём на работе сплетничают");
            }
            context.complete();
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
    }
}
