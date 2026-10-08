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
 * Мастер гильдии авантюристов: стоит у конторки и ждёт охотников.
 * <p>
 * Работа та же, что у купца, — быть на месте, когда к нему придут, —
 * только в руке карта, а не изумруд: по ней мастера узнают издали.
 * Контракты он выдаёт разговором ({@link com.villagepax.sim.quest.Contracts}),
 * а не ремеслом.
 */
public class GuildJob implements Job {

    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "guild");

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        context.hold(new ItemStack(Items.MAP));
        if (context.state().phase() != JobState.Phase.IDLE) {
            context.goIdle();
        }
        Building guild = Workplaces.of(context.settlement(), context.citizen()).orElse(null);
        if (guild == null) {
            return Optional.empty();
        }
        List<BlockPos> desk = Workplaces.stations(guild);
        return desk.isEmpty() ? Optional.empty() : Optional.of(desk.get(0));
    }
}
