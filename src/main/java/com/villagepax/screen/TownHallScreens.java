package com.villagepax.screen;

import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * Реестр экранов мода.
 * <p>
 * Тип «расширенный», потому что вместе с открытием экрана уезжает снимок
 * колонии: ванильный обработчик умеет синхронизировать только слоты
 * и числа-свойства, а здесь ни того, ни другого нет.
 */
public final class TownHallScreens {

    public static final Identifier TOWN_HALL_ID = new Identifier(VillagePax.MOD_ID, "town_hall");

    public static final ScreenHandlerType<TownHallScreenHandler> TOWN_HALL =
            Registry.register(Registries.SCREEN_HANDLER, TOWN_HALL_ID,
                    new ExtendedScreenHandlerType<>(TownHallScreenHandler::new));

    private TownHallScreens() {
    }

    /** Ранняя инициализация класса: реестры заполняются до первого мира. */
    public static void init() {
    }
}
