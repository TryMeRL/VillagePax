package com.villagepax;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagePax implements ModInitializer {
    public static final String MOD_ID = "villagepax";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Village Pax: инициализация");
    }
}
