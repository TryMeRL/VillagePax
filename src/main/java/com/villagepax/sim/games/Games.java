package com.villagepax.sim.games;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

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
}
