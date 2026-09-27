package com.villagepax.block.entity;

import com.villagepax.VillagePax;
import com.villagepax.block.ModBlocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {

    public static final BlockEntityType<TownHallBlockEntity> TOWN_HALL = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(VillagePax.MOD_ID, "town_hall"),
            BlockEntityType.Builder.create(TownHallBlockEntity::new, ModBlocks.TOWN_HALL).build(null));

    public static final BlockEntityType<RopeBlockEntity> ROPE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(VillagePax.MOD_ID, "rope"),
            BlockEntityType.Builder.create(RopeBlockEntity::new, ModBlocks.LAUNDRY).build(null));

    /** Надпись на кубке: что, где, когда и кем выиграно. */
    public static final BlockEntityType<com.villagepax.block.festival.TrophyBlockEntity> TROPHY =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    new Identifier(VillagePax.MOD_ID, "trophy"),
                    BlockEntityType.Builder.create(com.villagepax.block.festival.TrophyBlockEntity::new,
                            ModBlocks.TROPHY).build(null));

    private ModBlockEntities() {
    }

    /** Обращение к классу, чтобы сработала статическая инициализация. */
    public static void init() {
    }
}
