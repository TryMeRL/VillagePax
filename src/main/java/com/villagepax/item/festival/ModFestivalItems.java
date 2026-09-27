package com.villagepax.item.festival;

import com.villagepax.VillagePax;
import com.villagepax.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Предметы праздника: ленты, лук состязания, мячики жонглёра и шапки народов.
 * <p>
 * Регистрируются в общую вкладку мода той же очередью, что снаряжение
 * народов, — отдельного места в творческом меню праздник не просит.
 */
public final class ModFestivalItems {

    /** Праздничная лента — награда за место; в лавке затейника это деньги. */
    public static final Item FESTIVAL_RIBBON = ModItems.add("festival_ribbon",
            new Item(new Item.Settings()));

    public static final Item FESTIVAL_BOW = ModItems.add("festival_bow",
            new FestivalBowItem(new Item.Settings().maxCount(1)));

    /** Мячики в руке — вывеска ремесла затейника, как монета у купца. */
    public static final Item JUGGLING_BALLS = ModItems.add("juggling_balls",
            new Item(new Item.Settings().maxCount(1)));

    private static final Map<Identifier, FestivalHatItem> HATS = new LinkedHashMap<>();

    public static final Item NORMAN_WREATH = hat("norman_wreath", "norman");
    public static final Item MAYA_FEATHER_CROWN = hat("maya_feather_crown", "maya");
    public static final Item KITSUNE_MASK = hat("kitsune_mask", "yamato");
    public static final Item NORD_STRAW_CROWN = hat("nord_straw_crown", "nord");
    public static final Item PONY_PARTY_HAT = hat("pony_party_hat", "pony");
    public static final Item DWARF_CANDLE_CAP = hat("dwarf_candle_cap", "dwarf");
    public static final Item ELF_FIREFLY_WREATH = hat("elf_firefly_wreath", "elf");

    private ModFestivalItems() {
    }

    private static Item hat(String name, String people) {
        Identifier culture = new Identifier(VillagePax.MOD_ID, people);
        FestivalHatItem hat = new FestivalHatItem(culture,
                new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON));
        HATS.put(culture, hat);
        return ModItems.add(name, hat);
    }

    /** Праздничная шапка народа, если у него она есть. */
    public static Optional<FestivalHatItem> hatOf(Identifier culture) {
        return Optional.ofNullable(HATS.get(culture));
    }

    /** Обращение к классу, чтобы сработала статическая инициализация. */
    public static void register() {
        VillagePax.LOGGER.debug("Праздничных шапок: {}", HATS.size());
    }
}
