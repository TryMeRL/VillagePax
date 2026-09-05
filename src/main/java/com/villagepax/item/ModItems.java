package com.villagepax.item;

import com.villagepax.VillagePax;
import com.villagepax.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ModItems {

    /** Порядок показа в творческой вкладке. */
    private static final List<Item> TAB_ORDER = new ArrayList<>();

    /**
     * Чертёж ратуши. Основной путь получения — награда за стартовую цепочку квестов
     * у чужой деревни. Крафт существует как подстраховка: на неудачном сиде рядом
     * может не оказаться ни одного поселения, и без чертежа мод было бы не начать.
     */
    public static final Item TOWN_HALL_BLUEPRINT = register("town_hall_blueprint",
            new TownHallBlueprintItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

    public static final ItemGroup GROUP = Registry.register(
            Registries.ITEM_GROUP,
            new Identifier(VillagePax.MOD_ID, "general"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModBlocks.TOWN_HALL))
                    .displayName(Text.translatable("itemGroup.villagepax.general"))
                    .entries((context, entries) -> TAB_ORDER.forEach(entries::add))
                    .build());

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        Item registered = Registry.register(Registries.ITEM, new Identifier(VillagePax.MOD_ID, name), item);
        TAB_ORDER.add(registered);
        return registered;
    }

    /**
     * Предметы-блоки создаются здесь, а не в {@link ModBlocks}, чтобы блок ничего
     * не знал о своём предмете: блоки нужны на сервере всегда, а предмет — только
     * для инвентаря и творческой вкладки.
     */
    public static void registerBlockItems() {
        for (Map.Entry<Identifier, Block> entry : ModBlocks.registered().entrySet()) {
            register(entry.getKey().getPath(), new BlockItem(entry.getValue(), new Item.Settings()));
        }
    }

    public static void init() {
        VillagePax.LOGGER.debug("Предметов во вкладке: {}", TAB_ORDER.size());
    }
}
