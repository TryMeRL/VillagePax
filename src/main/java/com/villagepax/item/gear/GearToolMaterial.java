package com.villagepax.item.gear;

import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

/** Материал оружия народа: прочность, сила, уровень добычи и чем чинить. */
public record GearToolMaterial(int durability, float miningSpeed, float attackDamage,
                               int miningLevel, int enchantability, Item repair)
        implements ToolMaterial {

    @Override
    public int getDurability() {
        return durability;
    }

    @Override
    public float getMiningSpeedMultiplier() {
        return miningSpeed;
    }

    @Override
    public float getAttackDamage() {
        return attackDamage;
    }

    @Override
    public int getMiningLevel() {
        return miningLevel;
    }

    @Override
    public int getEnchantability() {
        return enchantability;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.ofItems(repair);
    }
}
