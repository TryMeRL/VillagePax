package com.villagepax.item.charm;

import com.villagepax.effect.ModEffects;
import com.villagepax.item.ModItems;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Регистрация оберегов и их силы — пассивные, ответные и по нажатию. */
public final class Charms {

    /** Раз в сколько тиков проверяется оберег в левой руке. */
    public static final int CHECK_EVERY = 10;

    /** Раз в сколько тиков магнит тянет вещи: чаще, чтобы тянул плавно. */
    public static final int MAGNET_EVERY = 2;

    /** Докуда тянет магнит. */
    public static final double MAGNET_REACH = 8.0;

    /** Докуда бьёт руна: до этого блока Громовержец видит цель. */
    public static final double THUNDER_REACH = 48.0;

    /** Когда срабатывает ладанка: сил осталось меньше этой доли. */
    public static final float SECOND_WIND_AT = 0.3f;

    private static final Map<Charm, Item> ITEMS = new EnumMap<>(Charm.class);

    private Charms() {
    }

    public static void register() {
        for (Charm charm : Charm.values()) {
            Rarity rarity = charm.people() == null ? Rarity.UNCOMMON : Rarity.RARE;
            ITEMS.put(charm, ModItems.add(charm.id(),
                    new CharmItem(charm, new Item.Settings().maxCount(1).rarity(rarity))));
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTicks();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                Charm worn = worn(player);
                if (worn == null) {
                    continue;
                }
                if (worn == Charm.MAGNET_CHARM && tick % MAGNET_EVERY == 0) {
                    pull(player);
                }
                if (tick % CHECK_EVERY == 0) {
                    empower(player);
                }
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof PlayerEntity player) {
                secondWind(player, amount);
            }
            return true;
        });
    }

    public static Item itemOf(Charm charm) {
        return ITEMS.get(charm);
    }

    /** Какой оберег у него в левой руке — или {@code null}. */
    public static Charm worn(LivingEntity wearer) {
        ItemStack held = wearer.getOffHandStack();
        return held.getItem() instanceof CharmItem charm ? charm.charm() : null;
    }

    /** Силы оберега, пока он в левой руке. */
    public static void empower(LivingEntity wearer) {
        Charm worn = worn(wearer);
        if (worn == null) {
            return;
        }
        World world = wearer.getWorld();
        switch (worn) {
            case JADE_JAGUAR -> {
                keep(wearer, StatusEffects.NIGHT_VISION, 0, 400, 220);
                // Ягуар видит добычу: ночью нечисть вокруг светится сквозь
                // стены. Быстрый шаг — у стёганого доспеха майя, не у оберега.
                if (world.isNight()) {
                    markPrey(wearer);
                }
            }
            case LUCKY_HORSESHOE -> {
                keep(wearer, StatusEffects.LUCK, 0, 40, 20);
                if (wearer.isSprinting()) {
                    keep(wearer, ModEffects.RAINBOW_DASH, 0, 40, 20);
                }
            }
            case ORE_GEM -> {
                // Самоцвет чует руду и бережёт от огня глубин; сноровка
                // в кирке — у гномьих лат, оберег её не повторяет.
                if (wearer.getBlockY() < 60 || !world.isSkyVisible(wearer.getBlockPos().up())) {
                    keep(wearer, ModEffects.ORE_SENSE, 0, 60, 30);
                    keep(wearer, StatusEffects.FIRE_RESISTANCE, 0, 60, 30);
                }
            }
            case MOON_PENDANT -> {
                // Лёгкость — у листовой кольчуги эльфов. Кулон кормит лунным
                // светом: раз в двадцать секунд — кусок сытости.
                if (wearer instanceof PlayerEntity player && world.getTime() % MOONLIGHT_EVERY < CHECK_EVERY) {
                    player.getHungerManager().add(1, 0.5f);
                }
                if (world.isNight()) {
                    // Лечение тикает по своим часам: подновлять его каждые
                    // полсекунды значило бы лечить вчетверо быстрее.
                    keep(wearer, StatusEffects.REGENERATION, 0, 200, 60);
                }
            }
            case KITSUNE_CHARM -> {
                if (wearer.isSneaking()) {
                    keep(wearer, StatusEffects.INVISIBILITY, 0, 40, 20);
                }
            }
            case SEA_SHELL -> {
                if (wearer.isTouchingWater()) {
                    keep(wearer, StatusEffects.WATER_BREATHING, 0, 60, 30);
                    keep(wearer, StatusEffects.DOLPHINS_GRACE, 0, 60, 30);
                }
            }
            default -> {
            }
        }
        if (inHarmony(wearer)) {
            harmony(wearer, worn, world);
        }
    }

    /** Как часто лунный кулон подкармливает: раз в двадцать секунд. */
    static final int MOONLIGHT_EVERY = 20 * 20;

    /** Как далеко ягуар видит добычу. */
    static final double PREY_REACH = 24.0;

    /** Ночью нечисть вокруг носящего ягуара светится. */
    private static void markPrey(LivingEntity wearer) {
        Box around = wearer.getBoundingBox().expand(PREY_REACH);
        for (net.minecraft.entity.mob.HostileEntity prey : wearer.getWorld().getEntitiesByClass(
                net.minecraft.entity.mob.HostileEntity.class, around, Entity::isAlive)) {
            keep(prey, StatusEffects.GLOWING, 0, 40, 20);
        }
    }

    /**
     * Лад: полный набор народа и его оберег в левой руке.
     * <p>
     * Заказчик: «если я соберу сет, зачем амулет». Прежде незачем: набор
     * майя давал быстрый шаг, а ягуар — быстрый шаг ночью; латы гномов —
     * сноровку, а самоцвет — её же под камнем. Теперь у набора и оберега
     * разные силы, а вместе они дают третью — лад народа, сильнее обеих:
     * собравший всё своего народа получает то, чего не даёт ни одна вещь
     * поодиночке.
     */
    public static boolean inHarmony(LivingEntity wearer) {
        Charm worn = worn(wearer);
        if (worn == null || worn.people() == null) {
            return false;
        }
        com.villagepax.item.gear.Gear set = com.villagepax.item.gear.Gear.fullSetOn(wearer);
        return set != null && set.id().equals(worn.people());
    }

    /** Сила лада — по народу. */
    private static void harmony(LivingEntity wearer, Charm worn, World world) {
        switch (worn) {
            // Норманны: стена щитов в два ряда; ладанка — вдвое чаще.
            case PILGRIM_RELIQUARY -> keep(wearer, StatusEffects.RESISTANCE, 1, 40, 20);
            // Майя: ночная охота — сила и двойной шаг.
            case JADE_JAGUAR -> {
                if (world.isNight()) {
                    keep(wearer, StatusEffects.STRENGTH, 0, 40, 20);
                    keep(wearer, StatusEffects.SPEED, 1, 40, 20);
                }
            }
            // Пони: радужный галоп — всегда быстр и вдвойне удачлив.
            case LUCKY_HORSESHOE -> {
                keep(wearer, StatusEffects.SPEED, 0, 40, 20);
                keep(wearer, StatusEffects.LUCK, 1, 40, 20);
            }
            // Гномы: сердце горы — кирка вдвое быстрее под камнем.
            case ORE_GEM -> {
                if (wearer.getBlockY() < 60 || !world.isSkyVisible(wearer.getBlockPos().up())) {
                    keep(wearer, StatusEffects.HASTE, 1, 40, 20);
                }
            }
            // Эльфы: лунный лес — ночью быстры и лечатся вдвое.
            case MOON_PENDANT -> {
                if (world.isNight()) {
                    keep(wearer, StatusEffects.SPEED, 0, 40, 20);
                    keep(wearer, StatusEffects.REGENERATION, 1, 200, 60);
                }
            }
            // Северяне: буря — сила всегда; руна — вдвое чаще.
            case THUNDER_RUNE -> keep(wearer, StatusEffects.STRENGTH, 0, 40, 20);
            // Ямато: путь тени — крадучись быстр и лечится.
            case KITSUNE_CHARM -> {
                if (wearer.isSneaking()) {
                    keep(wearer, StatusEffects.SPEED, 0, 40, 20);
                    keep(wearer, StatusEffects.REGENERATION, 0, 100, 40);
                }
            }
            default -> {
            }
        }
        if (world instanceof ServerWorld server && world.getTime() % 40 < CHECK_EVERY) {
            server.spawnParticles(ParticleTypes.ENCHANT, wearer.getX(), wearer.getBodyY(0.6),
                    wearer.getZ(), 3, 0.4, 0.5, 0.4, 0.4);
        }
    }

    /** Сколько отдыхает оберег у этого носящего: в ладу — вдвое меньше. */
    public static int cooldownOf(LivingEntity wearer, Charm charm) {
        return inHarmony(wearer) && worn(wearer) == charm ? charm.cooldown() / 2 : charm.cooldown();
    }

    /**
     * Дать силу, если её нет или она на исходе. Подновлять каждый раз нельзя:
     * у ночного зрения мигание в последние десять секунд, а у лечения —
     * свои часы, и постоянное подновление их сбивает.
     */
    public static void keep(LivingEntity who, StatusEffect effect, int amplifier, int ticks, int below) {
        StatusEffectInstance now = who.getStatusEffect(effect);
        if (now == null || now.getDuration() < below || now.getAmplifier() < amplifier) {
            who.addStatusEffect(new StatusEffectInstance(effect, ticks, amplifier, true, false, true));
        }
    }

    /** Магнит: вещи и опыт вокруг летят к хозяину. Крадучись — не тянет. */
    public static int pull(PlayerEntity player) {
        if (player.isSneaking() || player.isSpectator()) {
            return 0;
        }
        Box around = player.getBoundingBox().expand(MAGNET_REACH);
        int pulled = 0;
        for (Entity loose : player.getWorld().getOtherEntities(player, around,
                entity -> entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity)) {
            Vec3d toward = player.getPos().add(0, 0.5, 0).subtract(loose.getPos());
            double distance = toward.length();
            if (distance < 0.6) {
                continue;
            }
            Vec3d pace = toward.normalize().multiply(Math.min(0.45, 0.15 + distance * 0.04));
            loose.setVelocity(pace);
            loose.velocityModified = true;
            pulled++;
        }
        return pulled;
    }

    /**
     * Ладанка паломника: удар, после которого сил остаётся меньше трети,
     * встречает золотой щит — сердца поглощения и лечение. Раз в две минуты.
     */
    public static boolean secondWind(PlayerEntity player, float amount) {
        if (worn(player) != Charm.PILGRIM_RELIQUARY) {
            return false;
        }
        Item reliquary = ITEMS.get(Charm.PILGRIM_RELIQUARY);
        if (player.getItemCooldownManager().isCoolingDown(reliquary)) {
            return false;
        }
        if (player.getHealth() - amount > player.getMaxHealth() * SECOND_WIND_AT) {
            return false;
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 20, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 5, 1));
        player.getItemCooldownManager().set(reliquary, cooldownOf(player, Charm.PILGRIM_RELIQUARY));
        if (player.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getBodyY(0.6),
                    player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE,
                    SoundCategory.PLAYERS, 1.0f, 1.2f);
        }
        player.sendMessage(Text.translatable("villagepax.charm.second_wind"), true);
        return true;
    }

    /** Дело оберега по нажатию. @return сделано ли */
    static boolean act(PlayerEntity player, Charm charm) {
        return switch (charm) {
            case THUNDER_RUNE -> thunder(player);
            case WIND_CHARM -> dash(player);
            default -> false;
        };
    }

    /** Руна Громовержца: молния в блок, на который смотришь. */
    public static boolean thunder(PlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        HitResult hit = player.raycast(THUNDER_REACH, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.translatable("villagepax.charm.thunder.no_target"), true);
            return false;
        }
        BlockPos at = ((BlockHitResult) hit).getBlockPos();
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) {
            return false;
        }
        bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(at.up()));
        if (player instanceof ServerPlayerEntity caster) {
            bolt.setChanneler(caster);
        }
        world.spawnEntity(bolt);
        return true;
    }

    /** Оберег ветра: рывок вперёд, и три секунды падать мягко. */
    public static boolean dash(PlayerEntity player) {
        Vec3d look = player.getRotationVector();
        Vec3d push = new Vec3d(look.x, 0, look.z).normalize().multiply(1.5);
        player.setVelocity(push.x, Math.max(0.42, look.y * 0.6 + 0.3), push.z);
        player.velocityModified = true;
        player.fallDistance = 0;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0,
                true, false));
        if (player.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5,
                    player.getZ(), 16, 0.3, 0.2, 0.3, 0.05);
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1,
                    SoundCategory.PLAYERS, 0.8f, 1.3f);
        }
        return true;
    }

    /**
     * Оберег возвращения: к ратуше своей колонии, а у кого колонии нет —
     * к постели или к началу мира. Только из того же мира, что и дом.
     */
    static boolean goHome(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Optional<BlockPos> home = homeOf(world, player);
        if (home.isEmpty()) {
            player.sendMessage(Text.translatable("villagepax.charm.homeward.nowhere"), true);
            return false;
        }
        BlockPos to = home.get();
        world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getBodyY(0.5),
                player.getZ(), 40, 0.4, 0.8, 0.4, 0.5);
        player.teleport(world, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, player.getYaw(),
                player.getPitch());
        world.playSound(null, to, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS,
                1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.PORTAL, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5,
                40, 0.4, 0.8, 0.4, 0.5);
        player.sendMessage(Text.translatable("villagepax.charm.homeward.arrived"), true);
        return true;
    }

    /** Куда ведёт оберег возвращения. */
    static Optional<BlockPos> homeOf(ServerWorld world, ServerPlayerEntity player) {
        if (world.getRegistryKey() == World.OVERWORLD) {
            for (Settlement settlement : SettlementManager.get(world).all()) {
                if (settlement.owner().isOwnedBy(player.getUuid())) {
                    BlockPos spot = com.villagepax.sim.Ground.spotNear(world, settlement.center(),
                            1, 4);
                    return Optional.of(spot != null ? spot : settlement.center().up());
                }
            }
        }
        BlockPos bed = player.getSpawnPointPosition();
        if (bed != null && player.getSpawnPointDimension() == world.getRegistryKey()) {
            return Optional.of(bed.up());
        }
        if (world.getRegistryKey() == World.OVERWORLD) {
            return Optional.of(world.getSpawnPos());
        }
        return Optional.empty();
    }
}
