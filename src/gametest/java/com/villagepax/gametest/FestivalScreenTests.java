package com.villagepax.gametest;

import com.villagepax.core.festival.ContestKind;
import com.villagepax.screen.FestivalNet;
import com.villagepax.screen.FestivalView;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.sim.festival.Matches;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Экран затейника говорит, когда праздник и почему сейчас нельзя.
 * <p>
 * Проверяется снимок, а не вёрстка: у игровых проверок нет клиента, а всё,
 * что игрок увидит, — в снимке.
 */
public class FestivalScreenTests extends GameTestSupport {

    /**
     * В будни — «не сегодня» и сколько дней, в праздник — открыто; взятый
     * приз помечен; пока идёт чужое состязание — «занято»; из двадцати
     * блоков начать нельзя — «подойдите».
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "festival_screen")
    public void theEntertainerTellsWhenAndWhy(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            Citizen host = FestivalDay.host(ground.village(), ground.place()).orElseThrow();
            PlayerEntity player = playerAt(context, ground.place().counter());
            List<String> wrong = new ArrayList<>();

            FestivalView weekday = FestivalNet.viewOf(manager, ground.village(), host, player, FAIR_DAY + 1,
                    FAIR_MORNING).orElse(null);
            if (weekday == null) {
                context.throwGameTestException("У затейника нет экрана");
                return;
            }
            if (!weekday.closed().equals(Optional.of("villagepax.festival.reason.not_today"))
                    || weekday.daysUntil() != 7) {
                wrong.add("будни: закрыто " + weekday.closed() + ", дней " + weekday.daysUntil()
                        + " — ждали not_today и 7");
            }

            FestivalView festive = FestivalNet.viewOf(manager, ground.village(), host, player, FAIR_DAY,
                    FAIR_MORNING).orElseThrow();
            if (festive.closed().isPresent() || festive.daysUntil() != 0
                    || festive.contests().stream().anyMatch(line -> line.refusal().isPresent())) {
                wrong.add("праздник: закрыто " + festive.closed() + ", дней " + festive.daysUntil()
                        + ", состязания " + festive.contests());
            }

            manager.markAwarded(ground.village().id(), FAIR_DAY, player.getUuid(), 0);
            if (!FestivalNet.viewOf(manager, ground.village(), host, player, FAIR_DAY, FAIR_MORNING)
                    .orElseThrow().contests().get(0).awarded()) {
                wrong.add("взятый приз не помечен");
            }

            PlayerEntity other = playerAt(context, ground.place().counter());
            int archery = contestIndex(ground.village(), ContestKind.ARCHERY);
            if (Matches.start(world, other, ground.village(), archery, FAIR_DAY, FAIR_MORNING)
                    != Matches.Verdict.YES) {
                wrong.add("второй игрок не начал стрельбу");
            }
            FestivalView busy = FestivalNet.viewOf(manager, ground.village(), host, player, FAIR_DAY,
                    FAIR_MORNING).orElseThrow();
            if (busy.contests().stream().anyMatch(line ->
                    !line.refusal().equals(Optional.of("villagepax.contest.reason.busy")))
                    || busy.running().isEmpty()) {
                wrong.add("пока идёт чужая стрельба: " + busy.contests() + ", идёт " + busy.running());
            }
            Matches.at(ground.village().id()).ifPresent(match ->
                    match.cancel(world, "villagepax.contest.cancel.over"));

            PlayerEntity far = playerAt(context, ground.place().counter().add(20, 0, 0));
            if (Matches.start(world, far, ground.village(), 0, FAIR_DAY, FAIR_MORNING)
                    != Matches.Verdict.TOO_FAR) {
                wrong.add("из двадцати блоков началось состязание");
            }
            FestivalView distant = FestivalNet.viewOf(manager, ground.village(), host, far, FAIR_DAY,
                    FAIR_MORNING).orElseThrow();
            if (!distant.contests().get(0).refusal().equals(Optional.of("villagepax.contest.reason.too_far"))) {
                wrong.add("издали: " + distant.contests().get(0).refusal() + " — ждали too_far");
            }

            if (!wrong.isEmpty()) {
                context.throwGameTestException("Экран затейника:\n  " + String.join("\n  ", wrong));
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }
}
