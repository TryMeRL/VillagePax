package com.villagepax.sim.festival;

import com.villagepax.block.ModBlocks;
import com.villagepax.core.festival.Festival;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.item.festival.FestivalBowItem;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Стрельба: восемь выстрелов по трём мишеням на разном расстоянии.
 * <p>
 * Игрок стреляет праздничным луком: затейник даёт его на старте, стрелы
 * не нужны, а сам лук не переживает состязания. Жители стреляют по очереди
 * настоящими стрелами, стоя на черте, — раз в полторы секунды, с разбросом
 * по нраву, — и очки им считает та же мишень, что и игроку: честно и видно
 * глазами. Кто куда целит, решает нрав: честолюбивый бьёт по дальней,
 * ровный по средней, ленивый по ближней.
 * <p>
 * Стрела засчитывается один раз и только праздничная — помеченная; обычный
 * лук на ярмарке не стреляет в зачёт. Кончились у всех выстрелы — стрельба
 * кончается, когда долетит последняя стрела.
 */
public final class Archery extends Match {

    /** Метка праздничной стрелы: по ней стрелу считает мишень и находит уборка. */
    public static final String ARROW_TAG = "villagepax_festival";

    /** Выстрелов у каждого. */
    public static final int SHOTS = 8;

    /** Стрела соперника — раз в полторы секунды. */
    private static final long SHOT_EVERY = 30;

    /** Столько ждут после последнего выстрела: стрела должна долететь. */
    private static final long LANDING = 40;

    /** Как далеко от сердца уборка ищет свои стрелы и луки. */
    private static final double TIDY = 32;

    /** Стоит на черте, если ближе этого к её середине. */
    private static final double ON_THE_LINE = 1.5;

    private final Map<UUID, Integer> shots = new HashMap<>();
    private final Set<UUID> counted = new HashSet<>();
    private int turn;
    private long nextShot;
    private long lastShot;

    Archery(ServerWorld world, UUID id, Settlement settlement, Fair fair, Festival festival, int index,
            long day) {
        super(world, id, settlement, fair, festival, index, day);
    }

    /** Праздничная стрела этого стрелка: не подбирается и помечена. */
    public static PersistentProjectileEntity festivalArrow(ServerWorld world, LivingEntity shooter) {
        ArrowEntity arrow = new ArrowEntity(world, shooter);
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        arrow.addCommandTag(ARROW_TAG);
        return arrow;
    }

    /** Сколько выстрелов у участника. */
    public int shotsOf(UUID contestant) {
        return shots.getOrDefault(contestant, involves(contestant) ? SHOTS : 0);
    }

    /** Может ли игрок натянуть праздничный лук этого состязания прямо сейчас. */
    public boolean mayShoot(PlayerEntity player) {
        return isRunning() && isPlayer(player.getUuid()) && shotsOf(player.getUuid()) > 0;
    }

    /** Игрок выстрелил праздничным луком. */
    public void shot(ServerWorld world, PlayerEntity player) {
        shots.put(player.getUuid(), shotsOf(player.getUuid()) - 1);
        lastShot = world.getTime();
    }

    boolean aims(BlockPos target) {
        return fair.targets().contains(target);
    }

    @Override
    protected boolean prepare(ServerWorld world, Random random) {
        return !fair.shooting().isEmpty() && fair.targets().size() == 3
                && fair.targets().stream().allMatch(target -> world.getBlockState(target).isOf(ModBlocks.ARCHERY_TARGET));
    }

    @Override
    protected void joined(ServerWorld world, PlayerEntity player) {
        shots.put(player.getUuid(), SHOTS);
        ItemStack bow = FestivalBowItem.forMatch(id(), SHOTS);
        if (player.getMainHandStack().isEmpty()) {
            player.setStackInHand(Hand.MAIN_HAND, bow);
        } else {
            player.getInventory().offerOrDrop(bow);
        }
    }

    @Override
    protected void left(ServerWorld world, UUID player) {
        PlayerEntity who = playerOf(world, player);
        if (who != null) {
            takeBows(who);
        }
    }

    @Override
    protected void began(ServerWorld world) {
        nextShot = world.getTime() + SHOT_EVERY;
        lastShot = world.getTime();
    }

    @Override
    protected void steer(ServerWorld world, CitizenEntity body, Citizen rival) {
        BlockPos line = fair.shooting().get(0);
        BlockPos target = targetFor(rival);
        body.setWorkTarget(line);
        body.setWorkFocus(target);
        if (!body.getMainHandStack().isOf(Items.BOW)) {
            body.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }
        List<UUID> ready = rivals().stream().filter(one -> shotsOf(one) > 0).toList();
        if (ready.isEmpty() || world.getTime() < nextShot) {
            return;
        }
        UUID shooter = ready.get(Math.floorMod(turn, ready.size()));
        if (!shooter.equals(rival.id())) {
            return;
        }
        boolean onTheLine = body.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(line)) <= ON_THE_LINE * ON_THE_LINE;
        if (onTheLine) {
            shoot(world, body, rival, target);
        }
        // Не дошёл до черты за очередь — очередь следующему: стрельба ждёт
        // стреляющих, а не застрявших.
        turn++;
        nextShot = world.getTime() + SHOT_EVERY;
    }

    /**
     * Выстрел жителя по мишени.
     * <p>
     * Формула ванильного скелета: прицел выше цели на пятую часть расстояния,
     * скорость 1.6, разброс — по нраву.
     */
    public void shoot(ServerWorld world, CitizenEntity body, Citizen rival, BlockPos target) {
        PersistentProjectileEntity arrow = festivalArrow(world, body);
        double dx = target.getX() + 0.5 - body.getX();
        double dz = target.getZ() + 0.5 - body.getZ();
        double dy = target.getY() + 0.5 - arrow.getY();
        double distance = Math.sqrt(dx * dx + dz * dz);
        arrow.setVelocity(dx, dy + distance * 0.2, dz, 1.6f, spreadOf(rival));
        world.spawnEntity(arrow);
        body.swingHand(Hand.MAIN_HAND);
        world.playSound(null, body.getBlockPos(), SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.NEUTRAL,
                1.0f, 1.0f / (world.getRandom().nextFloat() * 0.4f + 0.8f));
        shots.put(rival.id(), shotsOf(rival.id()) - 1);
        lastShot = world.getTime();
    }

    /** Стрела попала в мишень: очки стрелку по кольцам и дальности. */
    void hit(ServerWorld world, BlockPos target, ProjectileEntity arrow, int rings) {
        if (!arrow.getCommandTags().contains(ARROW_TAG) || !counted.add(arrow.getUuid())) {
            return;
        }
        Entity owner = arrow.getOwner();
        UUID who = owner instanceof PlayerEntity player ? player.getUuid()
                : owner instanceof CitizenEntity citizen ? citizen.citizenId().orElse(null) : null;
        if (who == null) {
            return;
        }
        int points = rings * ArcheryScore.range(fair.targets(), fair.shooting().get(0), target);
        if (!score(who, points)) {
            return;
        }
        world.spawnParticles(rings == ArcheryScore.BULLSEYE ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.CRIT,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
        world.playSound(null, target, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.NEUTRAL, 1.0f,
                rings == ArcheryScore.BULLSEYE ? 2.0f : 1.2f);
        if (owner instanceof PlayerEntity player) {
            player.sendMessage(Text.translatable("villagepax.contest.archery.hit", points)
                    .formatted(Formatting.GOLD), true);
        }
    }

    @Override
    protected boolean exhausted(ServerWorld world) {
        List<UUID> everyone = new ArrayList<>(players());
        everyone.addAll(rivals());
        return everyone.stream().allMatch(one -> shotsOf(one) <= 0)
                && world.getTime() - lastShot >= LANDING;
    }

    @Override
    protected void clear(ServerWorld world) {
        Box around = new Box(fair.heart()).expand(TIDY);
        world.getEntitiesByClass(PersistentProjectileEntity.class, around,
                arrow -> arrow.getCommandTags().contains(ARROW_TAG)).forEach(Entity::discard);
        world.getEntitiesByClass(ItemEntity.class, around,
                item -> FestivalBowItem.matchOf(item.getStack()).filter(id()::equals).isPresent())
                .forEach(Entity::discard);
        for (UUID player : players()) {
            PlayerEntity who = playerOf(world, player);
            if (who != null) {
                takeBows(who);
            }
        }
        Settlement home = SettlementManager.get(world).byId(settlement()).orElse(null);
        for (UUID rival : rivals()) {
            Citizen citizen = home == null ? null : home.citizen(rival).orElse(null);
            CitizenEntity body = citizen == null ? null : bodyOf(world, citizen);
            if (body != null && body.getMainHandStack().isOf(Items.BOW)) {
                body.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
        }
    }

    /** Забрать у игрока луки этого состязания. */
    private void takeBows(PlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (FestivalBowItem.matchOf(stack).filter(id()::equals).isPresent()) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
            }
        }
    }

    /** По какой мишени бьёт житель: честолюбивый — по дальней, ровный — по средней, ленивый — по ближней. */
    private BlockPos targetFor(Citizen rival) {
        BlockPos line = fair.shooting().get(0);
        List<BlockPos> byRange = fair.targets().stream()
                .sorted(Comparator.comparingInt(target -> ArcheryScore.range(fair.targets(), line, target)))
                .toList();
        if (isGuard(rival)) {
            return byRange.get(byRange.size() - 1);
        }
        return switch (Natures.of(rival)) {
            case AMBITIOUS -> byRange.get(byRange.size() - 1);
            case LAZY -> byRange.get(0);
            default -> byRange.get(Math.min(1, byRange.size() - 1));
        };
    }

    /** Разброс выстрела: страж метче всех, ленивый хуже всех. */
    private static float spreadOf(Citizen rival) {
        if (isGuard(rival)) {
            return 1.5f;
        }
        Nature nature = Natures.of(rival);
        return switch (nature) {
            case AMBITIOUS -> 3.0f;
            case LAZY -> 7.0f;
            default -> 4.5f;
        };
    }

    private static boolean isGuard(Citizen citizen) {
        return citizen.profession().filter(Villages.GUARD::equals).isPresent();
    }
}
