package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Склад поселения: сколько чего есть.
 * <p>
 * Предметы хранятся идентификаторами, а не объектами {@code Item}, поэтому
 * склад остаётся проверяемым без запущенной игры и переживает удаление мода,
 * добавлявшего предмет: запись просто станет неизвестной, а не уронит загрузку.
 * <p>
 * Задача 1.7 надстроит над этим счётчиком реальные сундуки и курьера. Билдер
 * при этом ничего не заметит: он спрашивает склад, а не сундук.
 */
public class Warehouse {

    public static final Codec<Warehouse> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("items", Map.of())
                    .forGetter(Warehouse::contents)
    ).apply(instance, Warehouse::new));

    private final Map<Identifier, Integer> items;

    public Warehouse() {
        this(Map.of());
    }

    public Warehouse(Map<Identifier, Integer> items) {
        this.items = new LinkedHashMap<>();
        items.forEach((item, count) -> {
            if (count > 0) {
                this.items.put(item, count);
            }
        });
    }

    public Map<Identifier, Integer> contents() {
        return Collections.unmodifiableMap(items);
    }

    public int count(Identifier item) {
        return items.getOrDefault(item, 0);
    }

    public boolean has(Identifier item, int amount) {
        return count(item) >= amount;
    }

    public void add(Identifier item, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("на склад нельзя добавить " + amount + " штук " + item);
        }
        if (amount == 0) {
            return;
        }
        items.merge(item, amount, Integer::sum);
    }

    /**
     * Выдача «всё или ничего».
     * <p>
     * Частичная выдача была бы хуже отказа: билдер получил бы половину нужного,
     * записать это некуда, и материалы просто исчезли бы со склада.
     *
     * @return {@code false}, если на складе меньше требуемого; склад не тронут
     */
    public boolean take(Identifier item, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("со склада нельзя выдать " + amount + " штук " + item);
        }
        if (amount == 0) {
            return true;
        }
        int available = count(item);
        if (available < amount) {
            return false;
        }
        if (available == amount) {
            // Пустые записи не храним: иначе склад раздувается в NBT списком
            // всего, что через него когда-либо прошло.
            items.remove(item);
        } else {
            items.put(item, available - amount);
        }
        return true;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int total() {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }
}
