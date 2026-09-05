package com.villagepax.entity;

import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {

    /**
     * {@code disableSaving} здесь несёт смысл, а не оптимизацию: тело жителя
     * не должно попадать в сохранение чанка, иначе источников правды станет
     * два — запись в поселении и осиротевшая сущность рядом с ней.
     */
    public static final EntityType<CitizenEntity> CITIZEN = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(VillagePax.MOD_ID, "citizen"),
            EntityType.Builder.<CitizenEntity>create(CitizenEntity::new, SpawnGroup.MISC)
                    .setDimensions(0.6f, 1.95f)
                    .maxTrackingRange(10)
                    .disableSaving()
                    .disableSummon()
                    .build("citizen"));

    private ModEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(CITIZEN, CitizenEntity.createAttributes());
    }
}
