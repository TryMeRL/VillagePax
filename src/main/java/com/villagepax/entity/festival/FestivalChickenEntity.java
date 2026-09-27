package com.villagepax.entity.festival;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Цыплёнок ловли: ванильный зверёк с целями загона. См. {@link FestivalCritters}. */
public class FestivalChickenEntity extends ChickenEntity implements PenRunner {

    private final Pen pen = new Pen();

    public FestivalChickenEntity(EntityType<? extends ChickenEntity> type, World world) {
        super(type, world);
    }

    @Override
    public Pen pen() {
        return pen;
    }

    @Override
    public double dash() {
        return 1.4;
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        FestivalCritters.retrain(this, goalSelector, targetSelector);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return FestivalCritters.shielded(source) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean canBeLeashedBy(PlayerEntity player) {
        return false;
    }

    @Override
    public boolean isBreedingItem(ItemStack stack) {
        return false;
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        return FestivalCritters.caughtBy(this, player);
    }
}
