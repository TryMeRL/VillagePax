package com.villagepax.sim.life;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;

/**
 * Смерть от старости — и только от неё.
 * <p>
 * Голод в этом моде <b>не убивает</b>: решение заказчика от 2026-09-08,
 * и оно остаётся в силе. Голодный сперва жалуется, потом работает
 * вполсилы, потом уходит навсегда — потеря больная, но заслуженная,
 * а смерть от голода была бы наказанием без предупреждения. Умирают
 * здесь только те, кто дожил.
 * <p>
 * И это <b>не потеря</b>, а условие роста. До сих пор жители были
 * бессмертны, и причина записана прямо: «смерть без семей и детей —
 * только потеря без замены». Семьи появились, значит появилась и замена:
 * старый Фульк уходит, его сын остаётся, и у сына уже свой сын.
 * <p>
 * Один за сутки, и самый старший. Не из скупости: похороны, случающиеся
 * по пять раз за день, перестают быть похоронами. А старший — потому
 * что иначе смерть выбирала бы по порядку в списке жителей, то есть
 * по случайности загрузки, и игрок видел бы, как молодой уходит
 * раньше деда.
 */
public final class Mortality {

    private Mortality() {
    }

    /** Суточный ход: не пора ли кому-то уйти. */
    public static Optional<Citizen> newDay(ServerWorld world, Settlement settlement) {
        for (Citizen citizen : Life.byAge(settlement)) {
            if (Ages.daysOf(citizen) < Ages.diesAt()) {
                // Список отсортирован от старших: если не дожил этот,
                // не дожил никто.
                return Optional.empty();
            }
            return Optional.of(die(world, settlement, citizen));
        }
        return Optional.empty();
    }

    /**
     * Похоронить.
     * <p>
     * Прибирается всё, что за жителем числилось: супруг снова свободен,
     * кровать и мастерская — тоже. Оставленная запись о супруге —
     * не мелочь: вдова с мёртвым мужем в записи не выйдет замуж больше
     * никогда, и колония тихо перестанет расти.
     */
    public static Citizen die(ServerWorld world, Settlement settlement, Citizen citizen) {
        // Горе, вдовство и память — одной дверью: см. Bonds.parted.
        // Прежде вдовство снималось здесь руками, и ровно поэтому его
        // не было в двух других способах покинуть колонию.
        Bonds.mourn(world, settlement, citizen);

        Life.discardBody(world, citizen);
        settlement.removeCitizen(citizen.id());

        Life.tell(world, settlement, "villagepax.life.died", citizen.fullName(),
                String.valueOf(Ages.daysOf(citizen)));
        Life.knell(world, settlement);
        return citizen;
    }
}
