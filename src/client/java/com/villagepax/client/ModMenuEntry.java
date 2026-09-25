package com.villagepax.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.villagepax.client.screen.SettingsScreen;

/**
 * Кнопка «Настроить» в списке модов Mod Menu.
 * <p>
 * Mod Menu мод не требует: без него загрузчик эту точку входа просто
 * не вызывает, а настройки остаются в {@code config/villagepax.json}
 * и команде перечитывания. Класс поэтому и живёт отдельно от остального
 * клиента — ни одна другая строка мода о Mod Menu не знает.
 */
public class ModMenuEntry implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SettingsScreen::new;
    }
}
