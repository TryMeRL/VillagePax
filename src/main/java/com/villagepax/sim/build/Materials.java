package com.villagepax.sim.build;

import com.villagepax.sim.Building;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

import java.util.LinkedHashMap;
import java.util.List;
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
        if (state.getBlock() instanceof CropBlock) {
            // Посев приходит вместе с постройкой — это записанное решение
            // заказчика, и без этой оговорки оно не работало: у морковной
            // грядки предмет всё-таки есть (морковь — AliasedBlockItem
            // блока carrots), и ферма требовала со склада сорок семь
            // морковок, которых у новой колонии взяться негде. Построить
            // ферму, чтобы получить морковь, можно было только имея морковь.
            return Optional.empty();
        }

        Item item = state.getBlock().asItem();
        return item == Items.AIR ? Optional.empty() : Optional.of(item);
    }

    /**
     * Чего площадке не хватает на следующие {@code lookahead} шагов плана.
     * <p>
     * Окно, а не весь план, намеренно: считать нужду до конца схемы — это
     * обход трёхсот шагов на каждое решение курьера. Он принесёт то, что
     * понадобится скоро, и вернётся снова.
     * <p>
     * Порядок обхода — порядок плана, поэтому выбор устойчив: иначе курьер
     * метался бы между двумя видами блоков от решения к решению.
     */
    public static Map<Item, Integer> shortfall(Schematic schematic, Building site, int lookahead) {
        List<BuildStep> steps = schematic.plan().steps();
        Map<Item, Integer> needed = new LinkedHashMap<>();

        // Окно считается по УКЛАДКАМ, а не по шагам плана.
        //
        // Расчистка материала не просит, а идёт первой и занимает треть
        // плана; считая её, курьер смотрел сквозь неё на полтора блока
        // вперёд и приносил один вид груза вместо четырёх. Поймано конём
        // на крыше: лишний слой почти из одного воздуха — и заявка дома
        // разом обмелела до одного вида.
        int seen = 0;
        for (int index = Math.max(0, site.nextStep()); index < steps.size() && seen < lookahead;
                index++) {
            BuildStep step = steps.get(index);
            if (!step.placesBlock()) {
                continue;
            }
            seen++;
            itemFor(schematic.blockAt(step.paletteIndex()))
                    .ifPresent(item -> needed.merge(item, 1, Integer::sum));
        }

        // Вычитаем то, что курьер уже принёс, иначе он будет носить
        // одно и то же, пока площадка не утонет в брёвнах.
        Map<Item, Integer> missing = new LinkedHashMap<>();
        needed.forEach((item, count) -> {
            int have = site.stock().count(Registries.ITEM.getId(item));
            if (count > have) {
                missing.put(item, count - have);
            }
        });
        return missing;
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
