package com.villagepax.sim;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Работа с любым {@link Inventory} — сумкой игрока в том числе.
 * <p>
 * Нужно затем, что торг обязан быть <b>«всё или ничего»</b>, а для этого
 * надо уметь спросить «влезет ли», не двигая ни одного предмета. У
 * {@code PlayerInventory} такой вопрос есть только вместе с ответом:
 * {@code insertStack} уже кладёт. Сделка, где монеты списаны, а товар
 * не влез, хуже отказа: игрок остался бы без того и без другого.
 * <p>
 * Склад колонии умеет то же самое своими средствами и остаётся при них:
 * он работает со списком контейнеров, а не с одним, и переписывать его
 * ради общей строчки кода значило бы трогать то, что уже проверено.
 */
public final class Stacks {

    private Stacks() {
    }

    /**
     * Влезет ли столько этого предмета.
     * <p>
     * Считается по тому же правилу, по которому потом кладётся: сперва
     * начатые стопки, потом пустые слоты. Иначе «влезет» и «положилось»
     * разошлись бы на инвентаре с ограничением на стопку.
     */
    public static boolean room(Inventory inventory, Item item, int amount) {
        return roomFor(inventory, item, amount) >= amount;
    }

    /**
     * Сколько из желаемого влезет. Считается с потолком: у склада из
     * двадцати сундуков незачем складывать всю свободу, когда спрашивают
     * про одну стопку.
     */
    public static int roomFor(Inventory inventory, Item item, int wanted) {
        if (wanted <= 0) {
            return 0;
        }
        int perStack = Math.min(item.getMaxCount(), inventory.getMaxCountPerStack());
        int free = 0;

        for (int slot = 0; slot < inventory.size() && free < wanted; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                free += perStack;
            } else if (stack.isOf(item) && stack.getCount() < perStack) {
                free += perStack - stack.getCount();
            }
        }
        return Math.min(free, wanted);
    }

    /**
     * Изъятие «всё или ничего»: сначала считаем, потом забираем.
     * <p>
     * Частичное было бы хуже отказа — у игрока отобрали бы половину даром.
     */
    public static boolean take(Inventory inventory, Item item, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (inventory.count(item) < amount) {
            return false;
        }

        int left = amount;
        for (int slot = 0; slot < inventory.size() && left > 0; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isOf(item)) {
                continue;
            }
            left -= inventory.removeStack(slot, Math.min(left, stack.getCount())).getCount();
        }
        inventory.markDirty();
        return left == 0;
    }

    /**
     * Положить. Возвращает то, что не влезло, — но у проверенного заранее
     * {@link #room} остатка не бывает.
     */
    public static ItemStack insert(Inventory inventory, ItemStack stack) {
        ItemStack left = stack.copy();
        int perStack = Math.min(left.getMaxCount(), inventory.getMaxCountPerStack());

        for (int slot = 0; slot < inventory.size() && !left.isEmpty(); slot++) {
            ItemStack existing = inventory.getStack(slot);
            if (existing.isEmpty() || !ItemStack.canCombine(existing, left)) {
                continue;
            }
            int moved = Math.min(perStack - existing.getCount(), left.getCount());
            if (moved <= 0) {
                continue;
            }
            existing.increment(moved);
            left.decrement(moved);
            inventory.markDirty();
        }

        for (int slot = 0; slot < inventory.size() && !left.isEmpty(); slot++) {
            if (!inventory.getStack(slot).isEmpty()) {
                continue;
            }
            inventory.setStack(slot, left.split(Math.min(perStack, left.getCount())));
            inventory.markDirty();
        }
        return left;
    }
}
