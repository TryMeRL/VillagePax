package com.villagepax.sim.work;

import com.villagepax.core.profession.Profession;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Natures;
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
        NO_SUCH_PROFESSION,

        /** Ремесло есть, но колония до него ещё не доросла. */
        LOCKED,

        /**
         * Это ребёнок.
         * <p>
         * Отдельный отказ, а не молчание: кнопка ремесла стоит у каждого
         * жителя в списке, и ребёнок ничем от взрослого в нём не отличался
         * бы. Нажал — и не понял, почему ничего не случилось.
         */
        TOO_YOUNG,

        /**
         * Характер не тот: трусу меча не дают.
         * <p>
         * Отказ, а не тихая замена на другое ремесло. Игрок выбирает
         * жителя глазами — по имени в списке, — и подмена выбора без
         * слов читается как поломка кнопки. К тому же это единственный
         * случай, когда характер жителя мешает приказу игрока, и узнать
         * о нём лучше здесь, чем по пустому месту на стене.
         */
        WRONG_NATURE
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
        Profession craft = profession.map(id -> ProfessionManager.get(id).orElse(null))
                .orElse(null);
        if (profession.isPresent() && craft == null) {
            return Result.NO_SUCH_PROFESSION;
        }
        if (craft != null && colony.citizen(citizenId)
                .filter(Ages::isChild).isPresent()) {
            return Result.TOO_YOUNG;
        }
        if (craft != null && !craft.openTo(colony.level())) {
            // Ремесло откроется со ступенью. Отказ здесь, а не в экране:
            // пульт — только способ попросить, а правило одно на все
            // способы, включая будущие команды и чужие моды.
            //
            // Раньше характера нарочно: запертое ремесло не даётся
            // <b>никому</b>, а «этот не возьмёт меча» отправило бы игрока
            // пробовать жителя за жителем и получать тот же отказ.
            return Result.LOCKED;
        }
        if (profession.isPresent() && colony.citizen(citizenId)
                .filter(who -> Natures.refuses(who, profession.get())).isPresent()) {
            return Result.WRONG_NATURE;
        }

        manager.update(colony.id(), settlement -> {
            settlement.citizen(citizenId)
                    .ifPresent(citizen -> citizen.setProfession(profession.orElse(null)));
            Workplaces.assign(world, settlement);
        });
        return Result.DONE;
    }
}
