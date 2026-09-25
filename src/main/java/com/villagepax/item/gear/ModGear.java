package com.villagepax.item.gear;

import com.villagepax.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Регистрация снаряжения народов и проверка полного набора.
 * <p>
 * Числа ударов — от ванили, чтобы их было с чем сравнить: длинный меч
 * бьёт как алмазный, но медленнее; молот — сильнее всех и медленнее
 * всех; лунный клинок быстрее всех и слабее; секира — как железный
 * топор, но с морозом.
 */
public final class ModGear {

    private static final Map<Gear, List<Item>> ARMOR = new EnumMap<>(Gear.class);
    private static final Map<Gear, Item> WEAPONS = new EnumMap<>(Gear.class);

    private ModGear() {
    }

    public static void register() {
        for (Gear gear : Gear.values()) {
            List<Item> pieces = new ArrayList<>();
            for (ArmorItem.Type type : ArmorItem.Type.values()) {
                pieces.add(ModItems.add(gear.pieceName(type),
                        new GearArmorItem(gear, type, new Item.Settings().rarity(Rarity.UNCOMMON))));
            }
            ARMOR.put(gear, Collections.unmodifiableList(pieces));
            WEAPONS.put(gear, ModItems.add(gear.weaponName(), weapon(gear)));
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % Gear.CHECK_EVERY != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                Gear.empower(player);
            }
        });
    }

    private static Item weapon(Gear gear) {
        Item.Settings settings = new Item.Settings().rarity(Rarity.UNCOMMON);
        return switch (gear) {
            case NORMAN -> new GearWeapons.Sword(gear, 4, -2.6f, settings);
            case MAYA -> new GearWeapons.Sword(gear, 3, -2.4f, settings);
            case PONY -> new GearWeapons.Sword(gear, 3, -2.2f, settings);
            case ELF -> new GearWeapons.Sword(gear, 2, -1.9f, settings);
            case DWARF -> new GearWeapons.Hammer(gear, 6, -3.2f, settings);
            case NORD -> new GearWeapons.Axe(gear, 5.5f, -3.0f, settings);
            case YAMATO -> new GearWeapons.Sword(gear, 3, -2.2f, settings);
        };
    }

    /** Четыре части брони народа: шлем, нагрудник, поножи, сапоги. */
    public static List<Item> armorOf(Gear gear) {
        return ARMOR.get(gear);
    }

    public static Item weaponOf(Gear gear) {
        return WEAPONS.get(gear);
    }

    /**
     * Чем вооружить бойца народа: его собственным оружием, а народ без
     * своего снаряжения (дописанный чужим датапаком) — железным мечом.
     * Страж северян выходит с секирой, гном — с молотом, и кто пришёл,
     * видно ещё до имени над головой.
     */
    public static net.minecraft.item.ItemStack armsFor(net.minecraft.util.Identifier culture) {
        for (Gear gear : Gear.values()) {
            if (culture != null && culture.getNamespace().equals(com.villagepax.VillagePax.MOD_ID)
                    && culture.getPath().equals(gear.id())) {
                return new net.minecraft.item.ItemStack(weaponOf(gear));
            }
        }
        return new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD);
    }
}
