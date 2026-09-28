package com.villagepax.gametest;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.build.BuildJob;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Игры с жителями: слова над головой, вечерняя компания, партия за столом.
 * <p>
 * Заказчик: «улучшай восприятие» — чтобы жители за игрой ощущались живыми
 * людьми, а не автоматами. Живой соперник говорит: соглашается, досадует,
 * хвалится, — и говорит так, что видно, кто именно.
 */
public class GamesTests extends GameTestSupport {

    /**
     * Фраза встаёт над головой и через три секунды гаснет сама.
     * Вторая фраза раньше двух секунд не встаёт: иначе компания тараторила бы.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "games_speech", tickLimit = 100)
    public void aLineShowsOverTheHeadAndFades(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen talker = hireWithBody(world, colony, BuildJob.BUILDER,
                context.getAbsolutePos(new BlockPos(4, 1, 4)));
        CitizenEntity body = (CitizenEntity) world.getEntity(talker.entityUuid().orElseThrow());
        if (!body.say(Text.literal("Шесть!"))) {
            context.throwGameTestException("Первая фраза не встала");
        }
        if (body.say(Text.literal("Ещё!"))) {
            context.throwGameTestException("Вторая фраза встала раньше двух секунд");
        }
        if (!body.speech().map(Text::getString).equals(Optional.of("Шесть!"))) {
            context.throwGameTestException("Над головой не та фраза: " + body.speech());
        }
        context.runAtTick(70, () -> {
            try {
                if (body.speech().isPresent()) {
                    context.throwGameTestException("Фраза не погасла через три секунды: "
                            + body.speech());
                }
            } finally {
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }
}
