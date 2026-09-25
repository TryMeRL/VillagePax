package com.villagepax.item.gear;

import com.villagepax.effect.ModEffects;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;

/**
 * Снаряжение народа: броня, оружие, удар и сила полного набора.
 * <p>
 * Заказчик: «потом добавим броню и оружие уникальное». Уникальное здесь
 * значит не «другого цвета», а «другое в руке»: у каждого оружия свой
 * удар, у каждого набора своя сила, и выбирают их по делу, а не по виду.
 * <ul>
 *   <li><b>норманны</b> — длинный меч: удар отбрасывает, рыцарь стоит крепче;
 *       кольчуга целиком — сопротивление урону;</li>
 *   <li><b>майя</b> — макуауитль, дубина с обсидиановыми зубьями: рана гноится;
 *       стёганый доспех — быстрый шаг ягуара;</li>
 *   <li><b>пони</b> — подковный кистень: враг взлетает от удара копытом,
 *       а в хозяине играет радуга; сбруя — высокий прыжок;</li>
 *   <li><b>гномы</b> — боевой молот, он же кирка: оглушает; латы —
 *       горняцкая сноровка и зрение в темноте под камнем;</li>
 *   <li><b>эльфы</b> — лунный клинок: метит добычу светом, а ночью
 *       возвращает силы; листовая кольчуга — лёгкость падения;</li>
 *   <li><b>северяне</b> — бородовидная секира: морозит, как вьюга; мех —
 *       ярость берсерка, когда силы на исходе, и холод им не страшен.</li>
 * </ul>
 */
public enum Gear {
    NORMAN("norman", new GearArmorMaterial("norman", 17, new int[]{2, 6, 5, 2}, 12,
            SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 1.0f, 0.0f, Items.IRON_INGOT),
            new GearToolMaterial(620, 6.0f, 2.0f, 2, 14, Items.IRON_INGOT), "longsword"),
    MAYA("maya", new GearArmorMaterial("maya", 14, new int[]{2, 5, 4, 2}, 22,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.0f, 0.0f, Items.EMERALD),
            new GearToolMaterial(480, 6.0f, 2.0f, 2, 20, Items.OBSIDIAN), "macuahuitl"),
    PONY("pony", new GearArmorMaterial("pony", 13, new int[]{2, 5, 4, 2}, 24,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.0f, 0.0f, Items.GOLD_INGOT),
            new GearToolMaterial(420, 6.0f, 1.5f, 2, 22, Items.GOLD_INGOT), "horseshoe_flail"),
    DWARF("dwarf", new GearArmorMaterial("dwarf", 28, new int[]{3, 6, 5, 3}, 8,
            SoundEvents.ITEM_ARMOR_EQUIP_IRON, 2.0f, 0.1f, Items.IRON_INGOT),
            new GearToolMaterial(1100, 7.0f, 3.0f, 3, 10, Items.IRON_INGOT), "warhammer"),
    ELF("elf", new GearArmorMaterial("elf", 15, new int[]{2, 5, 4, 2}, 28,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.0f, 0.0f, Items.AMETHYST_SHARD),
            new GearToolMaterial(520, 6.0f, 2.0f, 2, 26, Items.AMETHYST_SHARD), "moon_blade"),
    NORD("nord", new GearArmorMaterial("nord", 20, new int[]{2, 6, 5, 2}, 12,
            SoundEvents.ITEM_ARMOR_EQUIP_IRON, 1.5f, 0.05f, Items.IRON_INGOT),
            new GearToolMaterial(700, 6.5f, 3.0f, 2, 14, Items.IRON_INGOT), "bearded_axe");

    /** Как часто проверяется полный набор: раз в секунду, как маяк. */
    public static final int CHECK_EVERY = 20;

    /** Сколько живёт сила набора: с запасом на пропущенную проверку. */
    private static final int BONUS_TICKS = 60;

    private final String id;
    private final GearArmorMaterial armor;
    private final GearToolMaterial tool;
    private final String weapon;

    Gear(String id, GearArmorMaterial armor, GearToolMaterial tool, String weapon) {
        this.id = id;
        this.armor = armor;
        this.tool = tool;
        this.weapon = weapon;
    }

    public String id() {
        return id;
    }

    public GearArmorMaterial armor() {
        return armor;
    }

    public GearToolMaterial tool() {
        return tool;
    }

    /** Путь предмета оружия: {@code nord_bearded_axe}. */
    public String weaponName() {
        return id + "_" + weapon;
    }

    /** Путь части брони: {@code nord_helmet}. */
    public String pieceName(ArmorItem.Type type) {
        return id + "_" + type.getName();
    }

    /** Ключ строки подсказки о силе полного набора. */
    public String setKey() {
        return "villagepax.gear." + id + ".set";
    }

    /** Ключ строки подсказки об ударе оружия. */
    public String strikeKey() {
        return "villagepax.gear." + id + ".strike";
    }

    /**
     * Удар оружием народа: что случается с тем, кого ударили, и с тем, кто бил.
     */
    public void strike(LivingEntity target, LivingEntity attacker) {
        switch (this) {
            case NORMAN -> {
                target.takeKnockback(0.6, attacker.getX() - target.getX(),
                        attacker.getZ() - target.getZ());
                give(attacker, StatusEffects.RESISTANCE, 40, 0);
            }
            case MAYA -> give(target, StatusEffects.POISON, 60, 0);
            case PONY -> {
                target.addVelocity(0, 0.55, 0);
                target.velocityModified = true;
                give(attacker, ModEffects.RAINBOW_DASH, 60, 0);
            }
            case DWARF -> give(target, StatusEffects.SLOWNESS, 30, 2);
            case ELF -> {
                give(target, StatusEffects.GLOWING, 80, 0);
                if (attacker.getWorld().isNight()) {
                    attacker.heal(2.0f);
                }
            }
            case NORD -> {
                if (target.canFreeze()) {
                    target.setFrozenTicks(Math.max(target.getFrozenTicks(),
                            target.getMinFreezeDamageTicks() + 60));
                }
                give(target, StatusEffects.SLOWNESS, 40, 0);
            }
        }
    }

    /** Надет ли на нём полный набор этого народа — и какого. */
    public static Gear fullSetOn(LivingEntity wearer) {
        Gear worn = null;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = wearer.getEquippedStack(slot);
            if (!(stack.getItem() instanceof GearArmorItem piece)) {
                return null;
            }
            if (worn != null && worn != piece.gear()) {
                return null;
            }
            worn = piece.gear();
        }
        return worn;
    }

    /**
     * Дать силу полного набора, если он надет. Зовётся раз в {@link #CHECK_EVERY}
     * тиков; сила живёт чуть дольше, чем до следующей проверки, и гаснет сама,
     * стоит снять хоть одну часть.
     */
    public static void empower(LivingEntity wearer) {
        Gear worn = fullSetOn(wearer);
        if (worn == null) {
            return;
        }
        switch (worn) {
            case NORMAN -> give(wearer, StatusEffects.RESISTANCE, BONUS_TICKS, 0);
            case MAYA -> give(wearer, StatusEffects.SPEED, BONUS_TICKS, 0);
            case PONY -> give(wearer, StatusEffects.JUMP_BOOST, BONUS_TICKS, 1);
            case DWARF -> {
                give(wearer, StatusEffects.HASTE, BONUS_TICKS, 0);
                if (!wearer.getWorld().isSkyVisible(wearer.getBlockPos().up())) {
                    // Ночное зрение мигает последние десять секунд: даём с запасом.
                    give(wearer, StatusEffects.NIGHT_VISION, 20 * 15, 0);
                }
            }
            case ELF -> give(wearer, ModEffects.LIGHTNESS, BONUS_TICKS, 0);
            case NORD -> {
                wearer.setFrozenTicks(0);
                if (wearer.getHealth() <= wearer.getMaxHealth() / 2) {
                    give(wearer, StatusEffects.STRENGTH, BONUS_TICKS, 0);
                }
            }
        }
    }

    private static void give(LivingEntity who, StatusEffect effect, int ticks, int amplifier) {
        who.addStatusEffect(new StatusEffectInstance(effect, ticks, amplifier, true, false, true));
    }

    /** Предмет, которым чинят снаряжение народа на наковальне. */
    public Item repair() {
        return armor.repair();
    }
}
