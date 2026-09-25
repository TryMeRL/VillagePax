package com.villagepax.gametest;

import com.villagepax.core.trade.TradeTable;
import com.villagepax.core.trade.TradeTables;
import com.villagepax.effect.ModEffects;
import com.villagepax.item.gear.Gear;
import com.villagepax.item.gear.ModGear;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Recipe;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Снаряжение народов: у каждого оно есть, делается, продаётся и работает.
 * <p>
 * Заказчик просил «броню и оружие уникальное» — и уникальность здесь
 * проверяется делом, а не видом: у каждого оружия свой удар, у каждого
 * набора своя сила. Проверка идёт по всем народам разом, чтобы седьмой
 * народ, дописанный данными, получил или своё снаряжение, или жалобу.
 */
public class GearTests extends GameTestSupport {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gear")
    public void everyPeopleHasItsGearToCraftAndToBuy(TestContext context) {
        List<String> complaints = new ArrayList<>();
        Set<Item> craftable = new HashSet<>();
        for (Recipe<?> recipe : context.getWorld().getRecipeManager().values()) {
            craftable.add(recipe.getOutput(context.getWorld().getRegistryManager()).getItem());
        }
        for (Gear gear : Gear.values()) {
            List<Item> kit = new ArrayList<>(ModGear.armorOf(gear));
            kit.add(ModGear.weaponOf(gear));
            TradeTable table = TradeTables.of(new Identifier("villagepax", gear.id())).orElse(null);
            for (Item item : kit) {
                if (!craftable.contains(item)) {
                    complaints.add(Registries.ITEM.getId(item) + ": рецепта нет");
                }
                boolean sold = table != null && table.sells().stream()
                        .anyMatch(deal -> deal.item() == item && deal.minReputation() > 0);
                if (!sold) {
                    complaints.add(Registries.ITEM.getId(item) + ": свой народ его не продаёт");
                }
            }
        }
        if (!complaints.isEmpty()) {
            context.throwGameTestException("Снаряжение не сходится:\n  "
                    + String.join("\n  ", complaints));
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gear")
    public void aFullSetEmpowersAndAPartialOneDoesNot(TestContext context) {
        ZombieEntity wearer = context.spawnMob(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        try {
            List<Item> norman = ModGear.armorOf(Gear.NORMAN);
            for (int i = 0; i < 3; i++) {
                wearer.equipStack(slotOf(norman.get(i)), new ItemStack(norman.get(i)));
            }
            Gear.empower(wearer);
            if (wearer.hasStatusEffect(StatusEffects.RESISTANCE)) {
                context.throwGameTestException("Три части из четырёх уже дали силу набора");
            }
            wearer.equipStack(slotOf(norman.get(3)), new ItemStack(norman.get(3)));
            Gear.empower(wearer);
            if (!wearer.hasStatusEffect(StatusEffects.RESISTANCE)) {
                context.throwGameTestException("Полная кольчуга норманна не дала сопротивления");
            }
            // Чужая часть рушит набор: сила — у набора народа, а не у суммы железа.
            wearer.equipStack(EquipmentSlot.HEAD, new ItemStack(ModGear.armorOf(Gear.NORD).stream()
                    .filter(item -> slotOf(item) == EquipmentSlot.HEAD).findFirst().orElseThrow()));
            if (Gear.fullSetOn(wearer) != null) {
                context.throwGameTestException("Шлем северянина на норманне собрал набор");
            }
        } finally {
            wearer.discard();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gear")
    public void eachWeaponStrikesInItsOwnWay(TestContext context) {
        List<String> complaints = new ArrayList<>();
        for (Gear gear : Gear.values()) {
            PigEntity target = context.spawnMob(EntityType.PIG, new BlockPos(1, 2, 1));
            ZombieEntity attacker = context.spawnMob(EntityType.ZOMBIE, new BlockPos(3, 2, 1));
            try {
                ItemStack weapon = new ItemStack(ModGear.weaponOf(gear));
                weapon.getItem().postHit(weapon, target, attacker);
                String missing = struck(gear, target, attacker);
                if (missing != null) {
                    complaints.add(gear.weaponName() + ": " + missing);
                }
            } finally {
                target.discard();
                attacker.discard();
            }
        }
        if (!complaints.isEmpty()) {
            context.throwGameTestException("Оружие бьёт не так, как обещает:\n  "
                    + String.join("\n  ", complaints));
        }
        context.complete();
    }

    /**
     * Житель с оружием народа бьёт не сильнее, чем с железным мечом, — в секунду.
     * <p>
     * Моб бьёт атрибутом, а не скоростью оружия, и без поправки гномий молот
     * раз в секунду стал бы вдвое злее меча: налёт гномов срезал бы стражу
     * за миг. Проверяется урон в секунду каждого оружия против меча:
     * разница — в самом ударе, а не в числе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gear")
    public void peoplesWeaponsHitAsHardAsASwordPerSecond(TestContext context) {
        double sword = perSecond(new ItemStack(net.minecraft.item.Items.IRON_SWORD));
        if (com.villagepax.entity.CitizenMeleeGoal.interval(
                new ItemStack(net.minecraft.item.Items.IRON_SWORD)) != 20) {
            context.throwGameTestException("Железный меч сменил темп: бой стражи разбалансирован");
        }
        if (com.villagepax.entity.CitizenMeleeGoal.interval(ItemStack.EMPTY) != 20) {
            context.throwGameTestException("Кулак стал бить чаще прежнего");
        }
        List<String> complaints = new ArrayList<>();
        for (Gear gear : Gear.values()) {
            double own = perSecond(new ItemStack(ModGear.weaponOf(gear)));
            if (own > sword * 1.2 || own < sword * 0.75) {
                complaints.add(String.format("%s: %.2f в секунду против %.2f у меча",
                        gear.weaponName(), own, sword));
            }
        }
        if (!complaints.isEmpty()) {
            context.throwGameTestException("Оружие народов вне баланса:\n  "
                    + String.join("\n  ", complaints));
        }
        context.complete();
    }

    /** Каждый народ идёт в бой со своим оружием, чужой датапак — с мечом. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gear")
    public void fightersCarryTheirPeoplesWeapon(TestContext context) {
        for (Gear gear : Gear.values()) {
            ItemStack arms = ModGear.armsFor(new Identifier("villagepax", gear.id()));
            if (!arms.isOf(ModGear.weaponOf(gear))) {
                context.throwGameTestException(gear.id() + " идёт в бой не со своим: " + arms);
            }
        }
        if (!ModGear.armsFor(new Identifier("somepack", "vikings")).isOf(
                net.minecraft.item.Items.IRON_SWORD)) {
            context.throwGameTestException("Народ без снаряжения остался без меча");
        }

        // Удар жителя с секирой морозит так же, как удар игрока.
        com.villagepax.entity.CitizenEntity guard = com.villagepax.entity.CitizenSpawner
                .spawnPuppet(context.getWorld(), context.getAbsolutePos(new BlockPos(3, 2, 1)));
        PigEntity target = context.spawnMob(EntityType.PIG, new BlockPos(2, 2, 1));
        try {
            if (guard == null) {
                context.throwGameTestException("Страж не появился");
                return;
            }
            guard.equipStack(EquipmentSlot.MAINHAND, ModGear.armsFor(
                    new Identifier("villagepax", "nord")));
            guard.tryAttack(target);
            if (target.getFrozenTicks() <= target.getMinFreezeDamageTicks()) {
                context.throwGameTestException("Секира в руке стража не морозит");
            }
        } finally {
            if (guard != null) {
                guard.discard();
            }
            target.discard();
        }
        context.complete();
    }

    /** Урон в секунду в руке жителя: сила удара на частоту ударов. */
    private static double perSecond(ItemStack weapon) {
        double damage = 1.0;
        for (net.minecraft.entity.attribute.EntityAttributeModifier modifier : weapon
                .getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE)) {
            damage += modifier.getValue();
        }
        return damage * 20.0 / com.villagepax.entity.CitizenMeleeGoal.interval(weapon);
    }

    /** Что должно было случиться после удара — или null, если случилось. */
    private static String struck(Gear gear, LivingEntity target, LivingEntity attacker) {
        return switch (gear) {
            case NORMAN -> attacker.hasStatusEffect(StatusEffects.RESISTANCE)
                    ? null : "рыцарь не стал крепче";
            case MAYA -> target.hasStatusEffect(StatusEffects.POISON) ? null : "рана не гноится";
            case PONY -> target.getVelocity().y > 0.3 && attacker.hasStatusEffect(ModEffects.RAINBOW_DASH)
                    ? null : "враг не взлетел или радуги нет";
            case DWARF -> target.hasStatusEffect(StatusEffects.SLOWNESS) ? null : "враг не оглушён";
            case ELF -> target.hasStatusEffect(StatusEffects.GLOWING) ? null : "добыча не помечена";
            case NORD -> target.getFrozenTicks() > target.getMinFreezeDamageTicks()
                    ? null : "мороза нет";
            case YAMATO -> target.hasStatusEffect(StatusEffects.WEAKNESS)
                    ? null : "разрез не ослабил врага";
        };
    }

    private static EquipmentSlot slotOf(Item item) {
        return LivingEntity.getPreferredEquipmentSlot(new ItemStack(item));
    }
}
