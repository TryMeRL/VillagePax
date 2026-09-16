package com.villagepax.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.UseAction;

/**
 * То, что пьют, а не едят.
 * <p>
 * Разница видна и слышна: кружку подносят к губам, а не грызут, и звук
 * у неё свой. Ради одного жеста заводить класс стоит: эль колонии —
 * это награда, и она обязана ощущаться наградой, а не съеденной морковкой.
 */
public class DrinkItem extends Item {

    public DrinkItem(Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public SoundEvent getDrinkSound() {
        return SoundEvents.ENTITY_GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatSound() {
        return SoundEvents.ENTITY_GENERIC_DRINK;
    }
}
