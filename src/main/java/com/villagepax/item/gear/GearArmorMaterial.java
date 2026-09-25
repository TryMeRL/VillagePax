package com.villagepax.item.gear;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;

/**
 * Материал брони народа.
 * <p>
 * Защита задаётся по частям в порядке «сапоги, поножи, нагрудник, шлем» —
 * как у ванильных материалов, чтобы числа сравнивались с ними на глаз.
 * Имя без двоеточия: ваниль строит из него путь к текстуре слоя, и
 * «villagepax:nord» в середине пути сломал бы разбор идентификатора.
 */
public record GearArmorMaterial(String id, int durabilityMultiplier, int[] protection,
                                int enchantability, SoundEvent equipSound, float toughness,
                                float knockbackResistance, Item repair) implements ArmorMaterial {

    /** Прочность части на единицу множителя — те же числа, что у ванили. */
    private static int base(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 11;
            case CHESTPLATE -> 16;
            case LEGGINGS -> 15;
            case BOOTS -> 13;
        };
    }

    @Override
    public int getDurability(ArmorItem.Type type) {
        return base(type) * durabilityMultiplier;
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> protection[0];
            case LEGGINGS -> protection[1];
            case CHESTPLATE -> protection[2];
            case HELMET -> protection[3];
        };
    }

    @Override
    public int getEnchantability() {
        return enchantability;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(repair);
    }

    @Override
    public String getName() {
        return "villagepax_" + id;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockbackResistance;
    }
}
