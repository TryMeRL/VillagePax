package com.villagepax.sim.build;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Во что обходится схема: сколько чего надо взять со склада.
 * <p>
 * Заявка считается по плану стройки, а не по палитре: расчистка и маркеры
 * материалов не требуют, и включать их в счёт значило бы просить у игрока
 * сотню несуществующих предметов.
 */
public final class Materials {

    private Materials() {
    }

    /**
     * Предмет, которым ставится этот блок, или пусто, если блок ставится
     * бесплатно.
     * <p>
     * У части блоков ({@code wall_torch}, {@code fire}) предмета нет, и
     * {@code asItem} возвращает воздух. Требовать «воздух» со склада
     * бессмысленно, а схема с настенным факелом иначе встала бы навсегда.
     */
    public static Optional<Item> itemFor(BlockState state) {
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? Optional.empty() : Optional.of(item);
    }

    /** Полная заявка на постройку схемы с нуля. */
    public static Map<Item, Integer> required(Schematic schematic) {
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            itemFor(schematic.blockAt(step.paletteIndex()))
                    .ifPresent(item -> needed.merge(item, 1, Integer::sum));
        }
        return needed;
    }
}
