package com.villagepax.sim.work;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import net.minecraft.util.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * У каждого дела один хозяин.
 * <p>
 * Без этого правила все билдеры колонии берутся за первую же стройку,
 * идут к одному и тому же блоку и толкаются на нём: поиск пути у каждого
 * сбивается о соседа, никто не доходит, и стройка встаёт. Со стороны это
 * выглядит как зависание, и игрок сообщает именно так.
 * <p>
 * Занятость видна в самих данных — в {@code JobState} жителя лежит здание,
 * за которое он взялся. Отдельного списка «кто что занял» не нужно, и это
 * важно: такой список пришлось бы чистить при смерти, уходе и выгрузке
 * жителя, а состояние работы уже переживает всё это само.
 * <p>
 * Считается <b>по делу, а не по зданию</b>. Это не тонкость: курьер, несущий
 * материалы на стройку, держит в состоянии работы ту же самую стройку, что
 * и билдер. Общий счёт занятости означал бы, что подвоз материалов
 * <b>запрещает стройку</b> — а это ровно те два дела, которые обязаны идти
 * одновременно.
 */
public final class Claims {

    private Claims() {
    }

    /**
     * Взялся ли за это здание кто-то другой <b>с тем же делом</b>.
     * <p>
     * Праздный житель не считается занявшим: работа отпускается вместе
     * с переходом в {@code IDLE}, и здание сразу свободно для следующего.
     */
    public static boolean takenByAnother(Settlement settlement, Citizen self, UUID building) {
        Identifier mine = logicOf(self).orElse(null);
        if (mine == null) {
            return false;
        }

        for (Citizen other : settlement.citizens()) {
            if (other.id().equals(self.id())) {
                continue;
            }
            if (logicOf(other).filter(mine::equals).isEmpty()) {
                // Чужое дело — не конкурент: курьер и билдер на одной стройке
                // не мешают друг другу, а нужны друг другу.
                continue;
            }

            JobState state = other.jobState();
            if (!state.isIdle() && state.building().filter(building::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Identifier> logicOf(Citizen citizen) {
        return Jobs.forProfession(citizen.profession()).map(Job::logic);
    }
}
