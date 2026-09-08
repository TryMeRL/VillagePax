package com.villagepax.sim.work;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;

import java.util.UUID;

/**
 * У каждого дела один хозяин.
 * <p>
 * Без этого правила все билдеры колонии берутся за первую же стройку,
 * идут к одному и тому же блоку и толкаются на нём: поиск пути у каждого
 * сбивается о соседа, никто не доходит, и стройка встаёт. Со стороны это
 * выглядит как зависание, и игрок сообщает именно так.
 * <p>
 * То же с курьерами: двое несут одну и ту же заявку, второй приходит
 * с грузом, который уже не нужен, и уносит его назад.
 * <p>
 * Занятость видна в самих данных — в {@code JobState} жителя лежит здание,
 * за которое он взялся. Отдельного списка «кто что занял» не нужно, и это
 * важно: такой список пришлось бы чистить при смерти, уходе и выгрузке
 * жителя, а состояние работы уже переживает всё это само.
 */
public final class Claims {

    private Claims() {
    }

    /**
     * Взялся ли за это здание кто-то другой.
     * <p>
     * Праздный житель не считается занявшим: работа отпускается вместе
     * с переходом в {@code IDLE}, и здание сразу свободно для следующего.
     */
    public static boolean takenByAnother(Settlement settlement, Citizen self, UUID building) {
        for (Citizen other : settlement.citizens()) {
            if (other.id().equals(self.id())) {
                continue;
            }
            JobState state = other.jobState();
            if (!state.isIdle() && state.building().filter(building::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
