package com.villagepax.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * То, что пьют, а не едят.
 * <p>
 * Разница видна и слышна: кружку подносят к губам, а не грызут, и звук
 * у неё свой. Ради одного жеста заводить класс стоит: эль колонии —
 * это награда, и она обязана ощущаться наградой, а не съеденной морковкой.
 */
public class DrinkItem extends Item {

    /** Что остаётся в руке, когда выпито: бутылка у стаута, у кружки — ничего. */
    private final Item leftover;

    public DrinkItem(Settings settings) {
        this(settings, null);
    }

    public DrinkItem(Settings settings, Item leftover) {
        super(settings);
        this.leftover = leftover;
    }

    /**
     * Выпил — пустая тара остаётся. Стаут варят в стеклянной бутылке,
     * и если она пропадает вместе с пивом, каждая кружка стоит бутылку:
     * так ванильный мёд не поступает, и игрок это замечает сразу.
     */
    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack left = super.finishUsing(stack, world, user);
        if (leftover == null || world.isClient()) {
            return left;
        }
        ItemStack empty = new ItemStack(leftover);
        if (left.isEmpty()) {
            return empty;
        }
        if (user instanceof PlayerEntity player && !player.getAbilities().creativeMode
                && !player.getInventory().insertStack(empty)) {
            player.dropItem(empty, false);
        }
        return left;
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
