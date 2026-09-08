package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Счётчик предметов: сколько чего есть, без привязки к контейнеру.
 * <p>
 * Предметы хранятся идентификаторами, а не объектами {@code Item}, поэтому
 * счётчик остаётся проверяемым без запущенной игры и переживает удаление
 * мода, добавлявшего предмет: запись просто станет неизвестной, а не уронит
 * загрузку.
 * <p>
 * Склад колонии счётчиком быть <b>не может</b> — он вид поверх реальных
 * сундуков, иначе две записи об одном и том же расходятся. А вот запас
 * стройплощадки — может и должен: физически эти материалы нигде не лежат,
 * они «сложены у стройки», и второго источника правды тут нет.
 */
public class ItemTally {

    public static final Codec<ItemTally> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("items", Map.of())
                    .forGetter(ItemTally::contents)
    ).apply(instance, ItemTally::new));

    private final Map<Identifier, Integer> items;

    public ItemTally() {
        this(Map.of());
    }

    public ItemTally(Map<Identifier, Integer> items) {
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
            throw new IllegalArgumentException("нельзя добавить " + amount + " штук " + item);
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
     * записать это некуда, и материалы просто исчезли бы.
     *
     * @return {@code false}, если в запасе меньше требуемого; запас не тронут
     */
    public boolean take(Identifier item, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("нельзя выдать " + amount + " штук " + item);
        }
        if (amount == 0) {
            return true;
        }
        int available = count(item);
        if (available < amount) {
            return false;
        }
        if (available == amount) {
            // Пустые записи не храним: иначе счётчик раздувается в NBT списком
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

    /**
     * Равенство по содержимому, а не по ссылке.
     * <p>
     * Нужно снимку колонии для экрана ратуши: тот сравнивается целиком,
     * чтобы не гнать по сети то, что не менялось. Со сравнением по ссылке
     * «изменилось» было бы всегда, и вся экономия исчезла бы молча —
     * именно на этом и попался первый вариант.
     * <p>
     * Пустые записи в счётчике не хранятся, поэтому равенство карт —
     * это в точности равенство запасов.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ItemTally tally && items.equals(tally.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }

    @Override
    public String toString() {
        return "ItemTally" + items;
    }
}
