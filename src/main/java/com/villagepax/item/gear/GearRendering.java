package com.villagepax.item.gear;

import java.util.function.Function;

/**
 * Мост от брони к её отрисовщику.
 * <p>
 * Клиент ставит сюда фабрику при запуске; на сервере она остаётся пустой,
 * и броня просто не просит отрисовщика — рисовать там некому.
 */
public final class GearRendering {

    private static volatile Function<GearArmorItem, Object> factory = item -> null;

    private GearRendering() {
    }

    public static void install(Function<GearArmorItem, Object> made) {
        factory = made;
    }

    static Object provider(GearArmorItem item) {
        return factory.apply(item);
    }
}
