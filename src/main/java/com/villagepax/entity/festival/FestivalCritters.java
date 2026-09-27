package com.villagepax.entity.festival;

import com.villagepax.VillagePax;
import com.villagepax.core.festival.Critter;
import com.villagepax.sim.festival.Matches;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * Зверьки ловли: пять видов, как ванильные, только праздничные.
 * <p>
 * Тип не сохраняется и не призывается командой: зверёк живёт минуту
 * состязания и не должен пережить выгрузку чанка — иначе в загоне
 * оставался бы поросёнок, которого никто не ловит и который никуда
 * не девается. Детёныш — маленький зверёк в загоне читается сразу,
 * — неуязвим ко всему, кроме {@code /kill} и пустоты: ловят руками,
 * а не мечом.
 */
public final class FestivalCritters {

    public static final EntityType<FestivalPigEntity> PIG = register("festival_pig",
            EntityType.Builder.create(FestivalPigEntity::new, SpawnGroup.CREATURE).setDimensions(0.9f, 0.9f));
    public static final EntityType<FestivalChickenEntity> CHICKEN = register("festival_chicken",
            EntityType.Builder.create(FestivalChickenEntity::new, SpawnGroup.CREATURE).setDimensions(0.4f, 0.7f));
    public static final EntityType<FestivalFoxEntity> FOX = register("festival_fox",
            EntityType.Builder.create(FestivalFoxEntity::new, SpawnGroup.CREATURE).setDimensions(0.6f, 0.7f));
    public static final EntityType<FestivalRabbitEntity> RABBIT = register("festival_rabbit",
            EntityType.Builder.create(FestivalRabbitEntity::new, SpawnGroup.CREATURE).setDimensions(0.4f, 0.5f));
    public static final EntityType<FestivalSheepEntity> SHEEP = register("festival_sheep",
            EntityType.Builder.create(FestivalSheepEntity::new, SpawnGroup.CREATURE).setDimensions(0.9f, 1.3f));

    private FestivalCritters() {
    }

    private static <T extends MobEntity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
        return Registry.register(Registries.ENTITY_TYPE, new Identifier(VillagePax.MOD_ID, name),
                builder.maxTrackingRange(8).disableSaving().disableSummon().build(name));
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(PIG, PigEntity.createPigAttributes());
        FabricDefaultAttributeRegistry.register(CHICKEN, ChickenEntity.createChickenAttributes());
        FabricDefaultAttributeRegistry.register(FOX, FoxEntity.createFoxAttributes());
        FabricDefaultAttributeRegistry.register(RABBIT, RabbitEntity.createRabbitAttributes());
        FabricDefaultAttributeRegistry.register(SHEEP, SheepEntity.createSheepAttributes());
    }

    /** Тип зверька по виду из данных праздника. */
    public static EntityType<? extends MobEntity> typeOf(Critter critter) {
        return switch (critter) {
            case PIG -> PIG;
            case CHICKEN -> CHICKEN;
            case FOX -> FOX;
            case RABBIT -> RABBIT;
            case SHEEP -> SHEEP;
        };
    }

    /**
     * Выпустить зверька в загон.
     *
     * @param match чьё состязание; {@code null} — зверёк сам по себе
     * @return зверёк, или {@code null}, если мир его не принял
     */
    public static MobEntity spawn(ServerWorld world, Critter critter, BlockPos cell, List<BlockPos> pen,
                                  UUID match) {
        MobEntity mob = typeOf(critter).create(world);
        if (mob == null) {
            return null;
        }
        if (mob instanceof PassiveEntity young) {
            young.setBaby(true);
        }
        ((PenRunner) mob).pen().enter(pen, match);
        mob.refreshPositionAndAngles(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5,
                world.getRandom().nextFloat() * 360f, 0);
        if (!world.spawnEntity(mob)) {
            return null;
        }
        world.spawnParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.3, mob.getZ(), 6, 0.2, 0.2, 0.2, 0);
        return mob;
    }

    /**
     * Щелчок по зверьку: ловля засчитает его, а зверёк без ловли — остаток,
     * праздник за собой не прибрал, и он просто исчезает.
     */
    static ActionResult caughtBy(MobEntity critter, PlayerEntity player) {
        if (critter.getWorld() instanceof ServerWorld world && !Matches.grab(world, critter, player)) {
            vanish(world, critter);
        }
        return ActionResult.success(critter.getWorld().isClient);
    }

    /** Исчезнуть в облачке: пойман, или состязание кончилось. */
    public static void vanish(ServerWorld world, MobEntity critter) {
        world.spawnParticles(ParticleTypes.POOF, critter.getX(), critter.getY() + 0.3, critter.getZ(), 8,
                0.2, 0.2, 0.2, 0.02);
        world.playSound(null, critter.getBlockPos(), SoundEvents.ENTITY_CHICKEN_EGG, SoundCategory.NEUTRAL,
                0.8f, 1.4f);
        critter.discard();
    }

    /**
     * Цели зверька ловли вместо ванильных.
     * <p>
     * Ванильные цели сперва ставятся — у овцы поле цели травы читается
     * в каждом {@code mobTick}, и без него она упала бы, — а потом снимаются
     * все, и цели выбора жертвы тоже: лисёнок в загоне не охотится на цыплят.
     */
    static <T extends PathAwareEntity & PenRunner> void retrain(T critter, GoalSelector goals,
                                                              GoalSelector targets) {
        goals.clear(goal -> true);
        targets.clear(goal -> true);
        goals.add(0, new SwimGoal(critter));
        goals.add(1, new RunAroundThePenGoal(critter));
        goals.add(2, new LookAroundGoal(critter));
    }

    /** Неуязвим ко всему, кроме {@code /kill} и пустоты. */
    static boolean shielded(DamageSource source) {
        return !source.isOf(DamageTypes.GENERIC_KILL) && !source.isOf(DamageTypes.OUT_OF_WORLD);
    }
}
