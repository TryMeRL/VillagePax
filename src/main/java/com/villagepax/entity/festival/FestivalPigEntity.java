package com.villagepax.entity.festival;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Поросёнок ловли: ванильный зверёк с целями загона. См. {@link FestivalCritters}. */
public class FestivalPigEntity extends PigEntity implements PenRunner {

    private final Pen pen = new Pen();

    public FestivalPigEntity(EntityType<? extends PigEntity> type, World world) {
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

    /**
     * Молния поросёнка не трогает. Ванильная свинья от неё становится
     * зомби-пиглином — вечным, с золотым мечом, — и после грозы или руны
     * Громовержца у ярмарки бродили бы малыши-пиглины.
     */
    @Override
    public void onStruckByLightning(ServerWorld world, LightningEntity lightning) {
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        return FestivalCritters.caughtBy(this, player);
    }
}
