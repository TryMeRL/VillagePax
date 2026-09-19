package com.villagepax.block;

import com.villagepax.item.ModItems;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.Block;

/**
 * Что горит, чем топят и что кладут в компост.
 * <p>
 * Свойства, которых игрок не ищет в меню, но замечает мгновенно: солома,
 * которая не горит, — не солома, а крашеный камень; поленница, которой
 * нельзя растопить печь, — насмешка над собственным названием. Мод, где
 * вещи ведут себя не так, как выглядят, кажется сломанным, даже когда
 * всё в нём работает.
 * <p>
 * И это же — <b>дружба с чужими модами</b>. Ванильные правила горения
 * и топки читают все: печи из технических модов, огнемёты, автокомпостеры.
 * Блок, попавший в эти реестры, работает в них без единой строчки
 * совместимости.
 */
public final class Kindling {

    /**
     * Сколько тиков горит поленница.
     * <p>
     * Восемьсот — это четыре переплавки, вчетверо против бревна. Поленница
     * и есть четыре бревна, сложенные вместе: цена ей та же, что и дровам,
     * из которых она собрана, иначе печь превратилась бы в станок
     * по производству топлива из воздуха.
     */
    private static final int FIREWOOD_BURNS = 800;

    /** Сукно горит как шерсть, из которой соткано. */
    private static final int CLOTH_BURNS = 200;

    private Kindling() {
    }

    public static void init() {
        FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();

        // Солома горит как сено: вспыхивает охотно и разносит огонь далеко.
        // Кровля из неё — настоящий риск, и это честно: так и было.
        burns(fire, ModBlocks.THATCH, 60, 20);
        burns(fire, ModBlocks.THATCH_STAIRS, 60, 20);
        burns(fire, ModBlocks.THATCH_SLAB, 60, 20);

        // Фахверк — дерево по дереву: горит, но не так бойко.
        burns(fire, ModBlocks.TIMBER_FRAME, 5, 20);
        burns(fire, ModBlocks.TIMBER_FRAME_STAIRS, 5, 20);
        burns(fire, ModBlocks.TIMBER_FRAME_SLAB, 5, 20);
        burns(fire, ModBlocks.FIREWOOD, 5, 5);

        FuelRegistry.INSTANCE.add(ModBlocks.FIREWOOD, FIREWOOD_BURNS);
        FuelRegistry.INSTANCE.add(ModItems.CLOTH, CLOTH_BURNS);

        // Солома в компост: трава травой.
        CompostingChanceRegistry.INSTANCE.add(ModBlocks.THATCH, 0.65f);
    }

    private static void burns(FlammableBlockRegistry fire, Block block, int spread, int burn) {
        fire.add(block, spread, burn);
    }
}
