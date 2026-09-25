package com.villagepax.item.gear;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Оружие народов. Три ванильных рода, потому что у каждого своё ремесло:
 * меч — просто меч, секира северян рубит и выбивает щит, как любой топор,
 * а гномий молот — это ещё и кирка: гном и в бою остаётся горняком.
 */
public final class GearWeapons {

    private GearWeapons() {
    }

    /** Чьё это оружие — если это оружие народа. */
    public static java.util.Optional<Gear> gearOf(ItemStack stack) {
        if (stack.getItem() instanceof Sword sword) {
            return java.util.Optional.of(sword.gear());
        }
        if (stack.getItem() instanceof Axe axe) {
            return java.util.Optional.of(axe.gear());
        }
        if (stack.getItem() instanceof Hammer hammer) {
            return java.util.Optional.of(hammer.gear());
        }
        return java.util.Optional.empty();
    }

    private static void tooltip(Gear gear, List<Text> lines) {
        lines.add(Text.translatable(gear.strikeKey()).formatted(Formatting.GRAY));
    }

    /** Меч народа: норманн, майя, пони, эльф. */
    public static class Sword extends SwordItem {
        private final Gear gear;

        public Sword(Gear gear, int damage, float speed, Settings settings) {
            super(gear.tool(), damage, speed, settings);
            this.gear = gear;
        }

        public Gear gear() {
            return gear;
        }

        @Override
        public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            gear.strike(target, attacker);
            return super.postHit(stack, target, attacker);
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> lines,
                                  TooltipContext context) {
            tooltip(gear, lines);
        }
    }

    /** Секира северян. */
    public static class Axe extends AxeItem {
        private final Gear gear;

        public Axe(Gear gear, float damage, float speed, Settings settings) {
            super(gear.tool(), damage, speed, settings);
            this.gear = gear;
        }

        public Gear gear() {
            return gear;
        }

        @Override
        public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            gear.strike(target, attacker);
            return super.postHit(stack, target, attacker);
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> lines,
                                  TooltipContext context) {
            tooltip(gear, lines);
        }
    }

    /** Гномий молот: бьёт как молот, копает как кирка. */
    public static class Hammer extends PickaxeItem {
        private final Gear gear;

        public Hammer(Gear gear, int damage, float speed, Settings settings) {
            super(gear.tool(), damage, speed, settings);
            this.gear = gear;
        }

        public Gear gear() {
            return gear;
        }

        @Override
        public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            gear.strike(target, attacker);
            return super.postHit(stack, target, attacker);
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> lines,
                                  TooltipContext context) {
            tooltip(gear, lines);
        }
    }
}
