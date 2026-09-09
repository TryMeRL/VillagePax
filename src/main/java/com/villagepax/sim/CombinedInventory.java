package com.villagepax.sim;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

import java.util.List;

/**
 * Несколько хранилищ, показанных как одно.
 * <p>
 * Нужно казне: сундуков с монетой у поселения может быть несколько, а
 * размен монеты — дело тонкое, и писать его дважды (для одного сундука
 * и для списка) значило бы однажды разойтись в правилах сдачи. Поэтому
 * арифметика монеты умеет работать с любым {@link Inventory}, а несколько
 * сундуков просто склеиваются в один вид.
 * <p>
 * Своего состояния нет: слоты остаются слотами настоящих хранилищ, и
 * запись идёт прямо в них. Вид недолговечен намеренно — блок-энтити
 * может выгрузиться, и держать его дольше одного действия нельзя.
 */
public final class CombinedInventory implements Inventory {

    private final List<Inventory> parts;
    private final int size;

    public CombinedInventory(List<Inventory> parts) {
        this.parts = List.copyOf(parts);
        int total = 0;
        for (Inventory part : this.parts) {
            total += part.size();
        }
        this.size = total;
    }

    /** Хранилище, которому принадлежит этот слот, и номер слота в нём. */
    private Inventory ownerOf(int slot) {
        for (Inventory part : parts) {
            if (slot < part.size()) {
                return part;
            }
            slot -= part.size();
        }
        return null;
    }

    private int localSlot(int slot) {
        for (Inventory part : parts) {
            if (slot < part.size()) {
                return slot;
            }
            slot -= part.size();
        }
        return -1;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (Inventory part : parts) {
            if (!part.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        Inventory owner = ownerOf(slot);
        return owner == null ? ItemStack.EMPTY : owner.getStack(localSlot(slot));
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        Inventory owner = ownerOf(slot);
        return owner == null ? ItemStack.EMPTY : owner.removeStack(localSlot(slot), amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        Inventory owner = ownerOf(slot);
        return owner == null ? ItemStack.EMPTY : owner.removeStack(localSlot(slot));
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        Inventory owner = ownerOf(slot);
        if (owner != null) {
            owner.setStack(localSlot(slot), stack);
        }
    }

    @Override
    public int getMaxCountPerStack() {
        // Наименьший из пределов: иначе в сундук с меньшим пределом
        // положилось бы больше, чем он держит.
        int limit = Inventory.super.getMaxCountPerStack();
        for (Inventory part : parts) {
            limit = Math.min(limit, part.getMaxCountPerStack());
        }
        return limit;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        Inventory owner = ownerOf(slot);
        return owner != null && owner.isValid(localSlot(slot), stack);
    }

    @Override
    public void markDirty() {
        parts.forEach(Inventory::markDirty);
    }

    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return false;
    }

    @Override
    public void clear() {
        parts.forEach(Inventory::clear);
    }
}
