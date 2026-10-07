package com.villagepax.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * Стрельба лучника стражи.
 * <p>
 * Ваниль умеет стрелять только у скелета, и цель его прибита к скелету.
 * Здесь та же арифметика выстрела, но своё поведение: лучник держит
 * дистанцию — подходит, пока не видит врага или тот дальше
 * {@link #RANGE}, и отступает, если враг подошёл ближе {@link #CLOSE}.
 * Стрелы не подбираются: иначе стража стала бы фермой стрел.
 */
public class CitizenBowGoal extends Goal {

    /** Дальше этого не стреляет: подходит. */
    static final double RANGE = 15.0;

    /** Ближе этого отступает: лучник в рукопашной — мёртвый лучник. */
    static final double CLOSE = 5.0;

    /** Пауза между выстрелами, в тиках. */
    static final int INTERVAL = 30;

    private final CitizenEntity archer;
    private int wait;

    public CitizenBowGoal(CitizenEntity archer) {
        this.archer = archer;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity target = archer.getTarget();
        return archer.isArcher() && target != null && target.isAlive();
    }

    @Override
    public boolean shouldContinue() {
        return canStart();
    }

    @Override
    public void start() {
        wait = INTERVAL / 2;
        archer.setAttacking(true);
    }

    @Override
    public void stop() {
        archer.setAttacking(false);
        archer.getNavigation().stop();
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = archer.getTarget();
        if (target == null) {
            return;
        }
        archer.getLookControl().lookAt(target, 30.0f, 30.0f);
        double away = archer.squaredDistanceTo(target);
        boolean sees = archer.getVisibilityCache().canSee(target);

        if (!sees || away > RANGE * RANGE) {
            archer.getNavigation().startMovingTo(target, 1.0);
        } else if (away < CLOSE * CLOSE) {
            Vec3d back = NoPenaltyTargeting.findFrom(archer, 8, 4, target.getPos());
            if (back != null) {
                archer.getNavigation().startMovingTo(back.x, back.y, back.z, 1.1);
            }
        } else {
            archer.getNavigation().stop();
        }

        if (wait > 0) {
            wait--;
            return;
        }
        if (sees && away <= RANGE * RANGE) {
            shoot(target);
            wait = INTERVAL;
        }
    }

    /** Выстрел — арифметикой скелета: чуть выше цели, с поправкой на дальность. */
    private void shoot(LivingEntity target) {
        PersistentProjectileEntity arrow = ProjectileUtil.createArrowProjectile(archer,
                new ItemStack(Items.ARROW), 1.0f);
        double dx = target.getX() - archer.getX();
        double dy = target.getBodyY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - archer.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.setVelocity(dx, dy + flat * 0.2, dz, 1.6f, 4.0f);
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        archer.swingHand(Hand.MAIN_HAND);
        archer.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0f,
                1.0f / (archer.getRandom().nextFloat() * 0.4f + 0.8f));
        archer.getWorld().spawnEntity(arrow);
    }
}
