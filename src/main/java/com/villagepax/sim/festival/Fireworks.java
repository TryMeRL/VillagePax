package com.villagepax.sim.festival;

import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.work.Schedule;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Optional;

/**
 * Фейерверк праздника: с заката до сна над сердцем ярмарки рвутся ракеты
 * в цветах народа.
 * <p>
 * Ракета встаёт на два блока выше всего, что стоит над сердцем: выше
 * верха столба и выше головы того, кто забрался на него. Ракета, задевшая
 * по пути жителя или свод, рвётся тут же и ранит всех в пяти блоках,
 * а вокруг сердца пляшет хоровод. Над залом гномов ракета поэтому встаёт
 * на крышу, а под горой, где верх над сердцем выше дюжины блоков,
 * фейерверка нет вовсе: неба оттуда не видно.
 * <p>
 * Бьёт, только если в 64 блоках есть игрок: огни для пустой деревни —
 * сущности и пакеты ради никого.
 */
public final class Fireworks {

    /** Тиков между ракетами: две секунды. */
    private static final long EVERY = 40;

    /** Смотреть есть кому, если игрок ближе этого к сердцу. */
    private static final double WATCHED = 64;

    /** На сколько блоков выше верха над сердцем встаёт ракета: выше головы стоящего там. */
    private static final int CLEARANCE = 2;

    /** Верх над сердцем выше этого — уже не крыша, а гора. */
    private static final int SKY_LIMIT = 12;

    private Fireworks() {
    }

    /**
     * Пустить ракету, если пора: с заката до сна, раз в две секунды,
     * в праздник, у загруженной ярмарки и если рядом есть кому смотреть.
     */
    public static void tend(ServerWorld world, Settlement settlement, long day, long timeOfDay) {
        if (world.getTime() % EVERY != 0 || !isEvening(timeOfDay)
                || !FestivalDay.isOn(settlement, day)) {
            return;
        }
        Festival festival = Festivals.of(settlement.culture()).orElse(null);
        Fair fair = Fairs.of(settlement).orElse(null);
        if (festival == null || fair == null || !world.isChunkLoaded(fair.heart())) {
            return;
        }
        BlockPos heart = fair.heart();
        if (world.isPlayerInRange(heart.getX() + 0.5, heart.getY() + 0.5, heart.getZ() + 0.5,
                WATCHED)) {
            launch(world, fair, festival, world.getRandom());
        }
    }

    /** Вечер праздника: с заката и до сна. */
    static boolean isEvening(long timeOfDay) {
        long time = Math.floorMod(timeOfDay, Schedule.DAY_LENGTH);
        return time >= FestivalDay.DUSK && Schedule.at(time) != Schedule.SLEEP;
    }

    /**
     * Пустить ракету над сердцем праздника.
     *
     * @return ракета — или пусто, если над сердцем не видно неба
     */
    public static Optional<FireworkRocketEntity> launch(ServerWorld world, Fair fair,
                                                        Festival festival, Random random) {
        BlockPos heart = fair.heart();
        int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, heart.getX(), heart.getZ());
        if (top - fair.standingY() > SKY_LIMIT) {
            return Optional.empty();
        }
        // Вразброс внутри столба над сердцем: ракеты из одной точки
        // выглядели бы работой машины, а не праздником.
        double x = heart.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        double z = heart.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        FireworkRocketEntity rocket = new FireworkRocketEntity(world, x, top + CLEARANCE, z,
                rocket(festival, 1 + random.nextInt(2)));
        world.spawnEntity(rocket);
        return Optional.of(rocket);
    }

    /**
     * Ракета в цветах народа: одна вспышка его формы, с хвостом, и искры
     * переливаются его цветами.
     *
     * @param flight полёт, как у ванильной ракеты: 1 — невысоко, 3 — под облака
     */
    public static ItemStack rocket(Festival festival, int flight) {
        Festival.Fireworks look = festival.fireworks();
        // Кодек пустых цветов не пропускает, но праздник можно собрать
        // и в коде, а ракета без цветов роняет клиент.
        List<Integer> colours = look.colors().isEmpty() ? Festival.Fireworks.PLAIN.colors()
                : look.colors();
        NbtCompound burst = new NbtCompound();
        burst.putByte(FireworkRocketItem.TYPE_KEY, (byte) look.type().getId());
        burst.putIntArray(FireworkRocketItem.COLORS_KEY, colours);
        if (colours.size() > 1) {
            burst.putIntArray(FireworkRocketItem.FADE_COLORS_KEY, colours);
        }
        burst.putBoolean(FireworkRocketItem.TRAIL_KEY, true);
        NbtList explosions = new NbtList();
        explosions.add(burst);

        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        NbtCompound fireworks = stack.getOrCreateSubNbt(FireworkRocketItem.FIREWORKS_KEY);
        fireworks.putByte(FireworkRocketItem.FLIGHT_KEY, (byte) flight);
        fireworks.put(FireworkRocketItem.EXPLOSIONS_KEY, explosions);
        return stack;
    }
}
