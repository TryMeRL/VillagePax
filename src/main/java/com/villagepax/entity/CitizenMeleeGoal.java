package com.villagepax.entity;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

/**
 * Ближний бой жителя: удар идёт с тем темпом, какой у оружия в руке.
 * <p>
 * Ванильная цель бьёт раз в секунду чем угодно — мечом, молотом, кулаком.
 * Для моба с мечом это честно, но у народов теперь своё оружие, и у него
 * своя скорость: гномий молот бьёт на десять, а лунный клинок на пять.
 * Раз в секунду молот стал бы вдвое сильнее меча, и налёт гномов срезал
 * бы стражу за миг. Поэтому пауза меж ударами считается от скорости
 * оружия так, что железный меч бьёт как прежде — раз в секунду, — а
 * урон в секунду у всего оружия выходит один: тяжёлое бьёт реже, лёгкое
 * чаще. Разница остаётся в самом ударе — в морозе, яде, оглушении.
 * <p>
 * Без оружия, у которого есть скорость, темп прежний: кулак пахаря
 * не должен стать быстрее от того, что у мечей появилась арифметика.
 */
public class CitizenMeleeGoal extends MeleeAttackGoal {

    /** Пауза железного меча — и та, что была у всех до оружия народов. */
    static final int BASE_INTERVAL = 20;

    /** Скорость удара железного меча: с ней пауза и равна базовой. */
    private static final double SWORD_SPEED = 1.6;

    /** Быстрее этого не бьёт никто: иначе клинок стал бы пулемётом. */
    private static final int FASTEST = 10;

    private int wait;

    public CitizenMeleeGoal(CitizenEntity citizen) {
        super(citizen, 1.0, false);
    }

    @Override
    public void start() {
        super.start();
        wait = 0;
    }

    @Override
    public void tick() {
        if (wait > 0) {
            wait--;
        }
        super.tick();
    }

    @Override
    protected void attack(LivingEntity target, double squaredDistance) {
        if (squaredDistance <= getSquaredMaxAttackDistance(target) && wait <= 0) {
            wait = interval(mob.getMainHandStack());
            mob.swingHand(Hand.MAIN_HAND);
            mob.tryAttack(target);
        }
    }

    /** Пауза меж ударами этим оружием, в тиках. */
    public static int interval(ItemStack weapon) {
        double bonus = 0;
        boolean known = false;
        for (EntityAttributeModifier modifier : weapon.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(EntityAttributes.GENERIC_ATTACK_SPEED)) {
            if (modifier.getOperation() == EntityAttributeModifier.Operation.ADDITION) {
                bonus += modifier.getValue();
                known = true;
            }
        }
        if (!known) {
            return BASE_INTERVAL;
        }
        // Скорость удара у игрока — четыре плюс поправка оружия.
        double speed = Math.max(0.25, 4.0 + bonus);
        return Math.max(FASTEST, (int) Math.round(BASE_INTERVAL * SWORD_SPEED / speed));
    }
}
