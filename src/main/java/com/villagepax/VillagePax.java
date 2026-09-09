package com.villagepax;

import com.villagepax.block.ModBlocks;
import com.villagepax.block.entity.ModBlockEntities;
import com.villagepax.core.config.Configs;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.entity.ModEntities;
import com.villagepax.command.BuildCommand;
import com.villagepax.item.ModItems;
import com.villagepax.screen.ColonyNet;
import com.villagepax.screen.TownHallNet;
import com.villagepax.screen.TownHallScreens;
import com.villagepax.sim.Villages;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.work.WorkTicker;
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
        // Первым делом: остальное уже смотрит на настройки.
        Configs.load();

        ModBlocks.init();
        ModBlockEntities.init();
        ModItems.registerBlockItems();
        ModItems.init();
        ModEntities.init();
        CitizenSpawner.register();
        SchematicLoader.register();
        WorkTicker.register();
        Villages.register();
        BuildCommand.register();
        TownHallScreens.init();
        TownHallNet.registerServer();
        ColonyNet.register();

        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new CultureManager());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new ProfessionManager());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new SchematicLoader());
        ResourceManagerHelper.get(ResourceType.SERVER_DATA)
                .registerReloadListener(new QuestManager());

        LOGGER.info("Village Pax: инициализация, настройки в {}", Configs.path());
    }
}
