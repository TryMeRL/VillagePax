package com.villagepax.sim.work;

import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Смена дела жителю по воле игрока.
 * <p>
 * Решение заказчика: профессия назначается сама — пришедший житель получает
 * самую нужную, — но игрок вправе переназначить. Колония работает без
 * присмотра и при этом остаётся управляемой.
 * <p>
 * Живёт здесь, а не в сетевом слое, ровно по той же причине, по какой
 * проверки заказа живут в {@code BuildOrders}: это серверная операция,
 * а пакет — только способ её попросить. Заодно её можно проверить игровым
 * тестом, у которого игрока нет вовсе.
 */
public final class Assignments {

    public enum Result {
        DONE,
        NO_SUCH_CITIZEN,
        NO_SUCH_PROFESSION
    }

    private Assignments() {
    }

    /**
     * Дать жителю профессию или снять её (пустое значение).
     * <p>
     * Мастерские раздаются тут же: иначе новый лесоруб ждал бы рассвета,
     * и игрок решил бы, что кнопка не работает. Тот же урок, что с домом,
     * достроенным в полдень.
     */
    public static Result set(ServerWorld world, SettlementManager manager, Settlement colony,
                             UUID citizenId, Optional<Identifier> profession) {
        if (colony.citizen(citizenId).isEmpty()) {
            return Result.NO_SUCH_CITIZEN;
        }
        if (profession.isPresent() && ProfessionManager.get(profession.get()).isEmpty()) {
            return Result.NO_SUCH_PROFESSION;
        }

        manager.update(colony.id(), settlement -> {
            settlement.citizen(citizenId)
                    .ifPresent(citizen -> citizen.setProfession(profession.orElse(null)));
            Workplaces.assign(world, settlement);
        });
        return Result.DONE;
    }
}
