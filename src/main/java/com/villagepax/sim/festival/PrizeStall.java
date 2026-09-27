package com.villagepax.sim.festival;

import com.villagepax.core.Named;
import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.sim.Settlement;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;

/**
 * Лавка затейника: праздничный товар за ленты.
 * <p>
 * Открыта только в праздник — весь день, и после заката тоже: ленты
 * выиграны днём, а тратить их приходят вечером, к фейерверку. Товар у народа
 * свой (данные праздника); ракеты выдаются в цветах народа и с полётом два —
 * такими, какими бьёт фейерверк ярмарки, а не голыми ванильными.
 * <p>
 * В своей колонии лавка тоже торгует: это праздничный товар, а не торг
 * с самим собой, и шапку своего народа колония заслужила ярмаркой.
 */
public final class PrizeStall {

    /** Взял ли игрок товар — или почему нет. */
    public enum Verdict implements Named {
        YES("yes"), CLOSED("closed"), NO_SUCH("no_such"), POOR("poor");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        public String reasonKey() {
            return "villagepax.stall.reason." + id;
        }
    }

    /** Полёт ракет из лавки: как у фейерверка ярмарки, повыше ванильной. */
    private static final int ROCKET_FLIGHT = 2;

    private PrizeStall() {
    }

    /** Взять товар за ленты: ленты — из сумки, товар — в сумку, лишнее — под ноги. */
    public static Verdict buy(ServerWorld world, PlayerEntity player, Settlement settlement, int index, long day) {
        if (!FestivalDay.isOn(settlement, day)) {
            return Verdict.CLOSED;
        }
        Festival festival = Festivals.of(settlement.culture()).orElse(null);
        if (festival == null || index < 0 || index >= festival.prizes().size()) {
            return Verdict.NO_SUCH;
        }
        Festival.Prize prize = festival.prizes().get(index);
        Item item = Registries.ITEM.getOrEmpty(prize.item()).orElse(Items.AIR);
        if (item == Items.AIR || prize.count() < 1) {
            return Verdict.NO_SUCH;
        }
        PlayerInventory inventory = player.getInventory();
        if (inventory.count(ModFestivalItems.FESTIVAL_RIBBON) < prize.price()) {
            return Verdict.POOR;
        }
        Inventories.remove(inventory, stack -> stack.isOf(ModFestivalItems.FESTIVAL_RIBBON), prize.price(), false);
        give(inventory, goods(festival, item), prize.count());
        return Verdict.YES;
    }

    /** Что выдать: ракета — в цветах народа, остальное — как есть. */
    private static ItemStack goods(Festival festival, Item item) {
        return item == Items.FIREWORK_ROCKET ? Fireworks.rocket(festival, ROCKET_FLIGHT) : new ItemStack(item);
    }

    /** Выдать столько штук, стопками по размеру стопки. */
    private static void give(PlayerInventory inventory, ItemStack one, int count) {
        int left = count;
        while (left > 0) {
            ItemStack stack = one.copy();
            int take = Math.min(left, stack.getMaxCount());
            stack.setCount(take);
            inventory.offerOrDrop(stack);
            left -= take;
        }
    }
}
