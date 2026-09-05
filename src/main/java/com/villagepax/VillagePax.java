package com.villagepax;

import com.villagepax.block.ModBlocks;
import com.villagepax.block.entity.ModBlockEntities;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.entity.ModEntities;
import com.villagepax.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagePax implements ModInitializer {

    public static final String MOD_ID = "villagepax";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.init();
        ModBlockEntities.init();
        ModItems.registerBlockItems();
        ModItems.init();
        ModEntities.init();
        CitizenSpawner.register();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new CultureManager());

        LOGGER.info("Village Pax: инициализация");
    }
}
