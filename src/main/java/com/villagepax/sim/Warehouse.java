package com.villagepax.sim;

import com.villagepax.block.entity.TownHallBlockEntity;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Склад колонии: <b>вид</b> поверх реальных контейнеров, а не счётчик.
 * <p>
 * Своего состояния у склада нет. Предметы хранят сами контейнеры — ратуша
 * и сундуки зданий, — и это единственный источник правды. Гибрид «счётчик
 * плюс сундуки» отвергнут сразу: две записи об одном и том же расходятся
 * при первом рассинхроне, и игрок видит на складе то, чего в сундуках нет.
 * <p>
 * Ратуша входит в склад всегда: иначе получается курица и яйцо — чтобы
 * построить склад, нужен склад.
 */
public final class Warehouse {

    private final List<Inventory> containers;

    private Warehouse(List<Inventory> containers) {
        this.containers = containers;
    }

    /**
     * Собрать склад поселения из мира.
     * <p>
     * Вид собирается заново на каждое обращение и не кэшируется: сундук можно
     * сломать, а здание разрушить, и устаревший список контейнеров привёл бы
     * к работе с выгруженными блок-энтити.
     */
    public static Warehouse of(ServerWorld world, Settlement settlement) {
        List<Inventory> found = new ArrayList<>();

        // Ратуша стоит в центре поселения — там её поставил чертёж.
        if (world.getBlockEntity(settlement.center()) instanceof TownHallBlockEntity hall) {
            found.add(hall);
        }

        for (Building building : settlement.buildings()) {
            if (!building.isOperational()) {
                continue;
            }
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            for (BlockPos spot : BuildJob.pointsOfInterest(building, schematic, MarkerKind.STORAGE)) {
                if (world.getBlockEntity(spot) instanceof Inventory chest && !found.contains(chest)) {
                    found.add(chest);
                }
            }
        }

        return new Warehouse(found);
    }

    /** Пустой склад для мест, где мир недоступен. */
    public static Warehouse empty() {
        return new Warehouse(List.of());
    }

    public int containerCount() {
        return containers.size();
    }

    public int count(Item item) {
        int total = 0;
        for (Inventory container : containers) {
            total += container.count(item);
        }
        return total;
    }

    public boolean has(Item item, int amount) {
        return amount <= 0 || count(item) >= amount;
    }

    /**
     * Выдача «всё или ничего»: сначала считаем, потом забираем.
     * <p>
     * Частичная выдача была бы хуже отказа — билдер получил бы половину
     * нужного, записать это некуда, и материалы просто исчезли бы.
     */
    public boolean take(Item item, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (count(item) < amount) {
            return false;
        }

        int left = amount;
        for (Inventory container : containers) {
            for (int slot = 0; slot < container.size() && left > 0; slot++) {
                ItemStack stack = container.getStack(slot);
                if (!stack.isOf(item)) {
                    continue;
                }
                left -= container.removeStack(slot, Math.min(left, stack.getCount())).getCount();
            }
            if (left == 0) {
                break;
            }
        }
        return left == 0;
    }

    /**
     * Положить на склад. Возвращает то, что не влезло.
     * <p>
     * Контейнеры конечны, и остаток нельзя терять молча: потерянные брёвна
     * хуже, чем уборка на площадке. {@link #addOrScatter} рассыпает остаток.
     */
    public ItemStack add(ItemStack stack) {
        ItemStack left = stack.copy();

        // Сначала доливаем начатые стопки, потом занимаем пустые слоты:
        // иначе склад забивается огрызками по одному предмету в слоте.
        for (Inventory container : containers) {
            left = topUpExisting(container, left);
        }
        for (Inventory container : containers) {
            left = fillEmptySlots(container, left);
        }
        return left;
    }

    /** Что не влезло — на землю: молча терять добычу нельзя. */
    public void addOrScatter(ServerWorld world, BlockPos where, ItemStack stack) {
        ItemStack left = add(stack);
        if (!left.isEmpty()) {
            ItemScatterer.spawn(world, where.getX(), where.getY(), where.getZ(), left);
        }
    }

    private static ItemStack topUpExisting(Inventory container, ItemStack stack) {
        for (int slot = 0; slot < container.size() && !stack.isEmpty(); slot++) {
            ItemStack existing = container.getStack(slot);
            if (existing.isEmpty() || !ItemStack.canCombine(existing, stack)) {
                continue;
            }
            int room = Math.min(existing.getMaxCount(), container.getMaxCountPerStack()) - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, stack.getCount());
            existing.increment(moved);
            stack.decrement(moved);
            container.markDirty();
        }
        return stack;
    }

    private static ItemStack fillEmptySlots(Inventory container, ItemStack stack) {
        for (int slot = 0; slot < container.size() && !stack.isEmpty(); slot++) {
            if (!container.getStack(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(stack.getCount(),
                    Math.min(stack.getMaxCount(), container.getMaxCountPerStack()));
            container.setStack(slot, stack.split(moved));
            container.markDirty();
        }
        return stack;
    }

    public boolean isEmpty() {
        for (Inventory container : containers) {
            if (!container.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int totalItems() {
        int total = 0;
        for (Inventory container : containers) {
            for (int slot = 0; slot < container.size(); slot++) {
                total += container.getStack(slot).getCount();
            }
        }
        return total;
    }
}
