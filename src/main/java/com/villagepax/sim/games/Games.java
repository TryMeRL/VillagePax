package com.villagepax.sim.games;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Игры с жителями — одна точка для стратегии жителя.
 * <p>
 * Как праздник, игра подменяет цель, но не стирает работу: наутро стройка
 * там, где её оставили. Решает здесь, по порядку: идёт ли у жителя партия,
 * член ли он вечерней компании — и если да, где ему стоять.
 */
public final class Games {

    private Games() {
    }

    /**
     * Забрать решение жителя, если он сейчас за игрой.
     *
     * @return истина, если житель у стола и решать за него больше нечего
     */
    public static boolean takesOver(WorkContext context, Schedule part, long day) {
        // Соперника идущей партии ведёт партия — в любой час: вечер,
        // начатый до отбоя, может кончиться после.
        if (Bouts.steers(context)) {
            return true;
        }
        ServerWorld world = context.world();
        List<Citizen> company = Company.of(world, context.settlement(), day, part,
                world.getTimeOfDay(), world.getPlayers());
        int place = company.indexOf(context.citizen());
        if (place < 0) {
            return false;
        }
        BlockPos spot = GameSpot.of(world, context.manager(), context.settlement());
        CitizenEntity body = context.body();
        context.holdNothing();
        body.setWorkTarget(Company.standAt(world, spot, place));
        body.setWorkFocus(spot);
        return true;
    }

    /** Что вышло из щелчка по жителю. */
    public enum Answer {
        /** Игре здесь нечего сказать: щелчок идёт дальше — к делу жителя или предмету в руке. */
        PASS,
        /** Открыть окно игры с ним. */
        WINDOW,
        /** Житель ответил отказом вслух. */
        REFUSED,
        /** Ребёнок ответил про прятки. */
        HIDING
    }

    /**
     * Щелчок по жителю — что на него ответят игры.
     * <p>
     * По порядку: своя партия с ним — её окно; ребёнок — прятки; у кого своё
     * дело по щелчку (затейник, старейшина с квестами, купец за прилавком),
     * тому обычный щелчок, а игре — щелчок с Shift; можно сесть за стол —
     * окно игры. Иначе отказ вслух с причиной — но только пустой рукой или
     * с Shift: щелчок с предметом в руке, как и прежде, не съедается.
     *
     * @param business есть ли у жителя своё дело по щелчку
     */
    public static Answer answer(ServerWorld world, PlayerEntity player, Settlement settlement,
                                Citizen citizen, boolean business, long day, long timeOfDay) {
        if (Bouts.of(player.getUuid()).filter(bout -> bout.rival().equals(citizen.id())).isPresent()) {
            return Answer.WINDOW;
        }
        if (Ages.isChild(citizen)) {
            return Answer.PASS;
        }
        if (business && !player.isSneaking()) {
            return Answer.PASS;
        }
        Bouts.Verdict verdict = Bouts.check(world, player, settlement, citizen, day, timeOfDay);
        if (verdict == Bouts.Verdict.YES) {
            Company.body(world, citizen).ifPresent(CitizenEntity::greet);
            return Answer.WINDOW;
        }
        if (!player.isSneaking() && !player.getMainHandStack().isEmpty()) {
            return Answer.PASS;
        }
        refuse(world, player, citizen, verdict);
        return Answer.REFUSED;
    }

    /**
     * Отказ — вслух, если сказать может житель, иначе строкой над рукой.
     * Поверх недавней фразы: игрок спросил и ждёт ответа.
     */
    private static void refuse(ServerWorld world, PlayerEntity player, Citizen citizen,
                               Bouts.Verdict verdict) {
        Optional<Say> say = verdict.say();
        CitizenEntity body = Company.body(world, citizen).orElse(null);
        if (say.isPresent() && body != null) {
            Lines.sayNow(body, citizen, say.get(), player.getName());
        } else {
            player.sendMessage(Text.translatable(verdict.reasonKey()), true);
        }
    }
}
