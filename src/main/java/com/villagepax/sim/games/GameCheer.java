package com.villagepax.sim.games;

import com.villagepax.sim.Citizen;
import net.minecraft.server.world.ServerWorld;

/**
 * Бодрость наутро после вечера за игрой с хозяином колонии — или после
 * пряток с его детьми.
 * <p>
 * Своя колония играет на интерес: монет нет, и игра должна чем-то быть
 * для колониста. Вечер с хозяином — тем, что наутро он бодрее. Не больше
 * раза в день, как праздник: игра — не способ накачать довольство.
 */
public final class GameCheer {

    /** На сколько бодрее наутро. */
    public static final int CHEER = 3;

    private GameCheer() {
    }

    /** Прибавка к довольству за этот день: {@link #CHEER}, если в тот день играли с хозяином. */
    public static int of(ServerWorld world, Citizen citizen, long day) {
        return GamesLedger.get(world).cheered(citizen.id(), day) ? CHEER : 0;
    }
}
