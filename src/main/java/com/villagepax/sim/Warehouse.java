package com.villagepax.sim;

import com.villagepax.block.entity.TownHallBlockEntity;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * <p>
 * Склад помнит не только количества, но и <b>места</b>: курьеру надо знать,
 * куда идти, а билдеру — далеко ли до материалов.
 */
public final class Warehouse {

    /** Контейнер вместе с его местом в мире. */
    public record Container(BlockPos pos, Inventory inventory) {
    }

    private final List<Container> containers;

    private Warehouse(List<Container> containers) {
        this.containers = containers;
    }

    /**
     * Собрать склад поселения из мира.
     * <p>
     * Вид недолговечен намеренно: сундук можно сломать, а здание разрушить,
     * и устаревший список контейнеров привёл бы к работе с выгруженными
     * блок-энтити. Держать его дольше одного решения жителя нельзя —
     * {@code WorkContext} ровно на столько его и запоминает.
     */
    public static Warehouse of(ServerWorld world, Settlement settlement) {
        List<Container> found = new ArrayList<>();

        // Ратуша стоит в центре поселения — там её поставил чертёж.
        if (world.getBlockEntity(settlement.center()) instanceof TownHallBlockEntity hall) {
            found.add(new Container(settlement.center(), hall));
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
                if (world.getBlockEntity(spot) instanceof Inventory chest && !holds(found, spot)) {
                    found.add(new Container(spot, chest));
                }
            }
        }

        return new Warehouse(found);
    }

    private static boolean holds(List<Container> containers, BlockPos pos) {
        for (Container container : containers) {
            if (container.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public int containerCount() {
        return containers.size();
    }

    /**
     * Ближайший контейнер, в котором есть нужное. Именно сюда идёт курьер,
     * и именно поэтому склад помнит места, а не только количества.
     */
    public Optional<Container> nearestWith(BlockPos from, Item item, int amount) {
        return nearest(from, container -> container.inventory().count(item) >= amount);
    }

    /** Ближайший контейнер вообще — куда сдать груз, который нести уже некуда. */
    public Optional<Container> nearest(BlockPos from) {
        return nearest(from, container -> true);
    }

    private Optional<Container> nearest(BlockPos from, java.util.function.Predicate<Container> suitable) {
        Container best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Container container : containers) {
            if (!suitable.test(container)) {
                continue;
            }
            double distance = container.pos().getSquaredDistance(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = container;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Есть ли хранилище в пределах указанного расстояния.
     * <p>
     * От этого зависит, нужен ли колонии курьер: стройка под боком у склада
     * идёт сама, а вынесенная за околицу требует людей. Так география
     * становится решением игрока, а не декорацией.
     */
    public boolean hasContainerWithin(BlockPos from, double blocks) {
        double limit = blocks * blocks;
        for (Container container : containers) {
            if (container.pos().getSquaredDistance(from) <= limit) {
                return true;
            }
        }
        return false;
    }

    /** Ближайший контейнер, в котором есть хоть что-то из тега: еда, топливо. */
    public Optional<Container> nearestWithTag(BlockPos from, TagKey<Item> tag) {
        return nearest(from, container -> hasTagged(container.inventory(), tag));
    }

    public boolean hasAny(TagKey<Item> tag) {
        for (Container container : containers) {
            if (hasTagged(container.inventory(), tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTagged(Inventory inventory, TagKey<Item> tag) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isIn(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Взять одну единицу из тега — например один хлеб на обед.
     * <p>
     * Возвращает именно предмет, а не стопку: съеденное надо чем-то
     * пересчитать в сытость, а стопка из одного предмета для этого лишняя.
     */
    public static Optional<Item> takeTagged(Container container, TagKey<Item> tag) {
        Inventory inventory = container.inventory();

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isIn(tag)) {
                continue;
            }
            Item item = stack.getItem();
            inventory.removeStack(slot, 1);
            inventory.markDirty();
            return Optional.of(item);
        }
        return Optional.empty();
    }

    /**
     * Что лежит на складе, поимённо.
     * <p>
     * Нужно экрану ратуши: показать содержимое всех сундуков колонии одним
     * списком. Счётчик здесь — снимок для показа, а не второй источник
     * правды: правда по-прежнему в сундуках, и снимок живёт один кадр.
     */
    public ItemTally tally() {
        Map<Identifier, Integer> counted = new LinkedHashMap<>();

        for (Container container : containers) {
            Inventory inventory = container.inventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (!stack.isEmpty()) {
                    counted.merge(Registries.ITEM.getId(stack.getItem()), stack.getCount(), Integer::sum);
                }
            }
        }
        return new ItemTally(counted);
    }

    public int count(Item item) {
        int total = 0;
        for (Container container : containers) {
            total += container.inventory().count(item);
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
        for (Container container : containers) {
            Inventory inventory = container.inventory();
            for (int slot = 0; slot < inventory.size() && left > 0; slot++) {
                ItemStack stack = inventory.getStack(slot);
                if (!stack.isOf(item)) {
                    continue;
                }
                left -= inventory.removeStack(slot, Math.min(left, stack.getCount())).getCount();
            }
            if (left == 0) {
                break;
            }
        }
        return left == 0;
    }

    /** Взять из одного конкретного контейнера: курьер стоит именно у него. */
    public static int takeFrom(Container container, Item item, int amount) {
        Inventory inventory = container.inventory();
        int taken = 0;

        for (int slot = 0; slot < inventory.size() && taken < amount; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isOf(item)) {
                continue;
            }
            taken += inventory.removeStack(slot, Math.min(amount - taken, stack.getCount())).getCount();
        }
        return taken;
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
        for (Container container : containers) {
            left = topUpExisting(container.inventory(), left);
        }
        for (Container container : containers) {
            left = fillEmptySlots(container.inventory(), left);
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
        for (Container container : containers) {
            if (!container.inventory().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int totalItems() {
        int total = 0;
        for (Container container : containers) {
            Inventory inventory = container.inventory();
            for (int slot = 0; slot < inventory.size(); slot++) {
                total += inventory.getStack(slot).getCount();
            }
        }
        return total;
    }
}
