package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.sim.Building;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Купец: стоит за прилавком и ждёт покупателя.
 * <p>
 * Ремесло без выработки — единственное такое в моде, и это не недоделка.
 * Купец производит не вещь, а <b>место встречи</b>: игрок должен знать,
 * куда идти торговать, и находить там человека. Ларёк без купца —
 * декорация, купец без ларька — прохожий, которого ищут по всей деревне
 * щелчками мыши.
 * <p>
 * Отсюда и вся работа: дойти до своего рабочего места и стоять. Стоять —
 * это тоже работа, если на тебя за этим приходят смотреть.
 * <p>
 * Нет ларька — купец бродит, как всякий житель без дела, а торгует за него
 * старейшина: правило «разговор не должен упереться в тупик» старше
 * разделения обязанностей.
 */
public class TradeJob implements Job {

    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "trade");

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        // Монета в руке — вывеска ремесла. Тот же приём, что топор
        // у лесоруба и меч у стража: игрок узнаёт, с кем говорит,
        // не подходя вплотную и не читая подписи.
        context.hold(new ItemStack(Items.EMERALD));

        // Фаза работы означает привязку к зданию с грузом и сроком;
        // у купца ни того, ни другого нет, и он всегда «в простое».
        // Заодно так возвращается на склад груз бывшего курьера.
        if (context.state().phase() != JobState.Phase.IDLE) {
            context.goIdle();
        }

        Building stall = Workplaces.of(context.settlement(), context.citizen()).orElse(null);
        if (stall == null) {
            return Optional.empty();
        }

        List<BlockPos> counter = Workplaces.stations(stall);
        if (counter.isEmpty()) {
            return Optional.empty();
        }

        // Место за прилавком — цель на весь рабочий день, а не пункт
        // маршрута. Прежде купец, дойдя, отпускал её, чтобы не топтаться,
        // и его тут же забирала прогулка: весь день он ходил кругами около
        // ларька, а в сохранении заказчика стоял в тридцати блоках от него.
        // Топтания нет и так: цель навигации у места пути не прокладывает.
        return Optional.of(counter.get(0));
    }
}
