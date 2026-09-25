package com.villagepax.effect;

import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Эффекты народов: то, что игрок получает от угощения каждого из них.
 * <p>
 * Заказчик просил «разнообразия, веселья, эффекты уникальные». Ванильных
 * эффектов у мода было три — спешка от эля, скорость и лечение от какао, —
 * и ни один из них не похож на народ, который его дал. Здесь три своих,
 * и каждый — характер народа, а не цифра в характеристиках:
 * <ul>
 *   <li><b>Радуга</b> пони — бежишь быстрее и оставляешь за собой радугу;</li>
 *   <li><b>Рудное чутьё</b> гномов — камень подсказывает, где руда;</li>
 *   <li><b>Лёгкость</b> эльфов — падаешь, как лист, и не разбиваешься.</li>
 * </ul>
 */
public final class ModEffects {

    /** Радужный след: по цвету на тик, шесть цветов по кругу. */
    private static final Vector3f[] RAINBOW = {
            new Vector3f(0.91f, 0.25f, 0.23f), new Vector3f(0.96f, 0.63f, 0.23f),
            new Vector3f(0.97f, 0.88f, 0.29f), new Vector3f(0.35f, 0.78f, 0.35f),
            new Vector3f(0.23f, 0.60f, 0.91f), new Vector3f(0.54f, 0.29f, 0.78f)};

    public static final StatusEffect RAINBOW_DASH = register("rainbow", new StatusEffect(
            StatusEffectCategory.BENEFICIAL, 0xF070B0) {
        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return true;
        }

        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            if (entity.getWorld() instanceof ServerWorld world) {
                // Частица на каждом тике и своего цвета: бегущий оставляет
                // полосы, стоящий — переливается облачком.
                Vector3f colour = RAINBOW[(int) (world.getTime() % RAINBOW.length)];
                world.spawnParticles(new DustParticleEffect(colour, 1.3f),
                        entity.getX(), entity.getY() + 0.3, entity.getZ(),
                        2, 0.2, 0.15, 0.2, 0.0);
            }
        }
    }.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
            "b4a6f0f2-3c7e-4d3b-9a51-6d2e8c1f7a01", 0.25,
            EntityAttributeModifier.Operation.MULTIPLY_TOTAL));

    /**
     * Как далеко слышит руду гном под хмелем: семь блоков, и ещё по два
     * на каждую ступень эффекта.
     */
    public static final int ORE_REACH = 7;

    public static final StatusEffect ORE_SENSE = register("ore_sense", new StatusEffect(
            StatusEffectCategory.BENEFICIAL, 0xC8901A) {
        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        /**
         * Раз в секунду — куда ближе всего руда и насколько.
         * <p>
         * Частицы сквозь камень не видны: игра прячет их за блоками, и
         * блеск на самой руде увидел бы только тот, кто её уже откопал.
         * Поэтому чутьё показывает <b>направление</b>: искры бегут от груди
         * игрока в сторону жилы, а над панелью написано, что это и далеко
         * ли. Это и есть лоза лозоходца, только гномья.
         */
        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            if (!(entity instanceof ServerPlayerEntity player)
                    || !(entity.getWorld() instanceof ServerWorld world)) {
                return;
            }
            BlockPos from = player.getBlockPos();
            nearestOre(world, from, ORE_REACH + 2 * amplifier).ifPresentOrElse(ore -> {
                Vec3d eyes = player.getEyePos().subtract(0, 0.4, 0);
                Vec3d toward = Vec3d.ofCenter(ore).subtract(eyes).normalize();
                for (int step = 1; step <= 4; step++) {
                    Vec3d at = eyes.add(toward.multiply(0.4 * step));
                    world.spawnParticles(player, ParticleTypes.WAX_ON, true,
                            at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
                }
                int away = (int) Math.round(Math.sqrt(ore.getSquaredDistance(from)));
                player.sendMessage(Text.translatable("villagepax.effect.ore_sense.near",
                        world.getBlockState(ore).getBlock().getName(), away), true);
            }, () -> player.sendMessage(
                    Text.translatable("villagepax.effect.ore_sense.none"), true));
        }
    });

    public static final StatusEffect LIGHTNESS = register("lightness", new StatusEffect(
            StatusEffectCategory.BENEFICIAL, 0xB8E8A8) {
        @Override
        public boolean canApplyUpdateEffect(int duration, int amplifier) {
            return true;
        }

        /**
         * Падать — как лист: высоты не набирается вовсе, и земля не бьёт.
         * Вокруг кружат лепестки — видно, что это эльфийское, а не удача.
         */
        @Override
        public void applyUpdateEffect(LivingEntity entity, int amplifier) {
            entity.fallDistance = 0;
            if (entity.getWorld() instanceof ServerWorld world && world.getTime() % 8 == 0) {
                world.spawnParticles(ParticleTypes.CHERRY_LEAVES,
                        entity.getX(), entity.getY() + 1.2, entity.getZ(),
                        1, 0.4, 0.4, 0.4, 0.0);
            }
        }
    });

    private ModEffects() {
    }

    /**
     * Ближайшая руда вокруг — по тегу {@code c:ores}, общему для всех модов:
     * медь из чужого мода гном почует так же, как ванильное железо.
     */
    public static Optional<BlockPos> nearestOre(World world, BlockPos from, int reach) {
        BlockPos best = null;
        double bestAway = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.iterate(from.add(-reach, -reach, -reach),
                from.add(reach, reach, reach))) {
            BlockState state = world.getBlockState(at);
            if (state.isIn(ConventionalBlockTags.ORES)) {
                double away = at.getSquaredDistance(from);
                if (away < bestAway) {
                    bestAway = away;
                    best = at.toImmutable();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static StatusEffect register(String name, StatusEffect effect) {
        return Registry.register(Registries.STATUS_EFFECT,
                new Identifier(VillagePax.MOD_ID, name), effect);
    }

    /** Обращение к классу, чтобы сработала статическая инициализация. */
    public static void init() {
        VillagePax.LOGGER.debug("Эффекты народов: {}, {}, {}",
                RAINBOW_DASH, ORE_SENSE, LIGHTNESS);
    }
}
