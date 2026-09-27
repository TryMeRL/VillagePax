package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.sim.Building;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Затейник: стоит у своего прилавка на ярмарке и жонглирует.
 * <p>
 * Ремесло без выработки, как купец, и по той же причине: он производит
 * не вещь, а место встречи. В будни к нему подходят узнать, когда праздник,
 * в праздник — играть. Мячики в руке — вывеска ремесла, как монета у купца:
 * по ним игрок узнаёт затейника с другого конца деревни.
 * <p>
 * Нет ярмарки — бродит, как всякий без дела. Праздника без затейника
 * не бывает, и это правило живёт не здесь, а в правиле дня праздника.
 */
public class EntertainJob implements Job {

    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "entertain");

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        context.hold(new ItemStack(ModFestivalItems.JUGGLING_BALLS));
        // У затейника ни груза, ни срока, как у купца: он всегда «в простое»,
        // и так же возвращает на склад груз, если раньше был курьером.
        if (context.state().phase() != JobState.Phase.IDLE) {
            context.goIdle();
        }
        Building fair = Workplaces.of(context.settlement(), context.citizen()).orElse(null);
        if (fair == null) {
            return Optional.empty();
        }
        List<BlockPos> counter = Workplaces.stations(fair);
        return counter.isEmpty() ? Optional.empty() : Optional.of(counter.get(0));
    }
}
