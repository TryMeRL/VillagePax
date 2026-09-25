package com.villagepax.item;

import com.villagepax.entity.CitizenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Хлопушка: взрыв конфетти, и деревня празднует вместе с тобой.
 * <p>
 * Первая вещь мода, которая ничего не даёт, кроме радости, — и ровно
 * об этом просил заказчик: «добавь разнообразия, веселья». Хлопок
 * слышен, конфетти видно, а жители вокруг бросают дела и машут тебе
 * рукой, и над ними вспыхивают искорки. Мелочь, но от неё деревня
 * впервые откликается на игрока просто так, а не по делу.
 */
public class PopperItem extends Item {

    /** Докуда слышен хлопок жителям. */
    public static final double REACH = 10;

    private static final Vector3f[] CONFETTI = {
            new Vector3f(0.93f, 0.26f, 0.35f), new Vector3f(0.97f, 0.80f, 0.20f),
            new Vector3f(0.29f, 0.70f, 0.95f), new Vector3f(0.40f, 0.80f, 0.40f),
            new Vector3f(0.75f, 0.45f, 0.90f), new Vector3f(1.0f, 1.0f, 1.0f)};

    public PopperItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world instanceof ServerWorld server) {
            Vec3d at = user.getEyePos().add(user.getRotationVector().multiply(1.2));
            burst(server, at);
            cheer(server, at);
        }
        user.getItemCooldownManager().set(this, 10);
        if (!user.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    /** Конфетти всех цветов и хлопок с искрами. */
    public static void burst(ServerWorld world, Vec3d at) {
        Random random = world.getRandom();
        for (Vector3f colour : CONFETTI) {
            world.spawnParticles(new DustParticleEffect(colour, 1.0f), at.x, at.y, at.z,
                    8, 0.6, 0.5, 0.6, 0.0);
        }
        world.spawnParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.08);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                SoundCategory.PLAYERS, 1.0f, 1.2f + random.nextFloat() * 0.2f);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE,
                SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    /**
     * Жители вокруг машут и радуются.
     *
     * @return сколько жителей откликнулось
     */
    public static int cheer(ServerWorld world, Vec3d at) {
        int cheered = 0;
        for (CitizenEntity citizen : world.getEntitiesByClass(CitizenEntity.class,
                Box.of(at, REACH * 2, REACH, REACH * 2), CitizenEntity::isAlive)) {
            citizen.greet();
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, citizen.getX(),
                    citizen.getEyeY() + 0.4, citizen.getZ(), 6, 0.3, 0.2, 0.3, 0.0);
            cheered++;
        }
        return cheered;
    }
}
