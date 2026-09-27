package com.villagepax.entity.festival;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Лисёнок ловли: ванильный зверёк с целями загона. См. {@link FestivalCritters}. */
public class FestivalFoxEntity extends FoxEntity implements PenRunner {

    private final Pen pen = new Pen();

    public FestivalFoxEntity(EntityType<? extends FoxEntity> type, World world) {
        super(type, world);
    }

    @Override
    public Pen pen() {
        return pen;
    }

    /**
     * Лисёнок бежит медленнее, чем просит план (1.2): шаг лисы и так
     * быстрее, и с 1.2 его не догнал бы и бегущий игрок.
     */
    @Override
    public double dash() {
        return 1.15;
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
