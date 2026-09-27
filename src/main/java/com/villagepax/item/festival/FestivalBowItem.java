package com.villagepax.item.festival;

import com.villagepax.sim.festival.Archery;
import com.villagepax.sim.festival.Matches;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Праздничный лук: затейник даёт его стрелку на время состязания.
 * <p>
 * Свой предмет, а не ванильный лук, потому что лук состязания не должен
 * пережить состязание: его не унесёшь с ярмарки и не заложишь в сундук.
 * Он помнит, чьё он состязание, и рассыпается, как только того нет, — где бы
 * он ни лежал: в руке, в сумке или в сундуке, из которого его достали.
 * Стрелы ему не нужны: выстрелов восемь, и счёт им ведёт состязание.
 */
public class FestivalBowItem extends BowItem {

    public static final String MATCH = "Match";
    public static final String SHOTS = "Shots";

    public FestivalBowItem(Settings settings) {
        super(settings);
    }

    /** Лук состязания: чьё оно и сколько выстрелов. */
    public static ItemStack forMatch(UUID match, int shots) {
        ItemStack stack = new ItemStack(ModFestivalItems.FESTIVAL_BOW);
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putUuid(MATCH, match);
        nbt.putInt(SHOTS, shots);
        return stack;
    }

    /** Чьё состязание у этого лука. */
    public static Optional<UUID> matchOf(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return stack.isOf(ModFestivalItems.FESTIVAL_BOW) && nbt != null && nbt.containsUuid(MATCH)
                ? Optional.of(nbt.getUuid(MATCH)) : Optional.empty();
    }

    private static Optional<Archery> archeryOf(ItemStack stack) {
        return matchOf(stack).flatMap(Matches::byId)
                .filter(Archery.class::isInstance)
                .map(Archery.class::cast);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld server)) {
            // Клиент состязаний не знает: натягивает, если выстрелы есть,
            // а выстрелит ли — решит сервер.
            NbtCompound nbt = stack.getNbt();
            if (nbt != null && nbt.getInt(SHOTS) > 0) {
                user.setCurrentHand(hand);
                return TypedActionResult.consume(stack);
            }
            return TypedActionResult.fail(stack);
        }
        Archery archery = archeryOf(stack).orElse(null);
        if (archery == null || !archery.isPlayer(user.getUuid()) || archery.shotsOf(user.getUuid()) <= 0) {
            crumble(server, user, stack);
            return TypedActionResult.fail(stack);
        }
        if (!archery.isRunning()) {
            // Отсчёт: лук ждёт «Начали!», а не рассыпается.
            user.sendMessage(Text.translatable("villagepax.contest.chase.wait"), true);
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server) || !(user instanceof PlayerEntity player)) {
            return;
        }
        float pull = getPullProgress(getMaxUseTime(stack) - remainingUseTicks);
        Archery archery = archeryOf(stack).orElse(null);
        if (pull < 0.1f || archery == null || !archery.mayShoot(player)) {
            return;
        }
        PersistentProjectileEntity arrow = Archery.festivalArrow(server, player);
        arrow.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, pull * 3.0f, 1.0f);
        arrow.setCritical(pull >= 1.0f);
        world.spawnEntity(arrow);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_SHOOT,
                SoundCategory.PLAYERS, 1.0f, 1.0f / (world.getRandom().nextFloat() * 0.4f + 1.2f) + pull * 0.5f);
        archery.shot(server, player);
        stack.getOrCreateNbt().putInt(SHOTS, archery.shotsOf(player.getUuid()));
        player.incrementStat(Stats.USED.getOrCreateStat(this));
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world instanceof ServerWorld server && archeryOf(stack).isEmpty()) {
            crumble(server, entity, stack);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(SHOTS)) {
            tooltip.add(Text.translatable("villagepax.festival_bow.shots", nbt.getInt(SHOTS))
                    .formatted(Formatting.GRAY));
        }
    }

    /** Рассыпаться с облачком: состязания нет, лук вернулся к затейнику. */
    private static void crumble(ServerWorld world, Entity holder, ItemStack stack) {
        stack.setCount(0);
        world.spawnParticles(ParticleTypes.POOF, holder.getX(), holder.getY() + 1.0, holder.getZ(), 6,
                0.2, 0.2, 0.2, 0.01);
        if (holder instanceof PlayerEntity player) {
            player.sendMessage(Text.translatable("villagepax.festival_bow.gone").formatted(Formatting.GRAY),
                    true);
        }
    }
}
