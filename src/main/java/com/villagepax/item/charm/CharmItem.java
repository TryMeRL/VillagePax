package com.villagepax.item.charm;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Оберег: сила — пока в левой руке, дело — по нажатию, если оно у него есть. */
public class CharmItem extends Item {

    /** Сколько тиков держать оберег возвращения, пока он не перенесёт домой. */
    public static final int HOMEWARD_TICKS = 60;

    private final Charm charm;

    public CharmItem(Charm charm, Settings settings) {
        super(settings);
        this.charm = charm;
    }

    public Charm charm() {
        return charm;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (player.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        switch (charm) {
            case HOMEWARD_CHARM -> {
                player.setCurrentHand(hand);
                return TypedActionResult.consume(stack);
            }
            case THUNDER_RUNE, WIND_CHARM -> {
                if (!world.isClient && Charms.act(player, charm)) {
                    player.getItemCooldownManager().set(this, charm.cooldown());
                }
                return TypedActionResult.success(stack, world.isClient);
            }
            default -> {
                return TypedActionResult.pass(stack);
            }
        }
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return charm == Charm.HOMEWARD_CHARM ? HOMEWARD_TICKS : 0;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return charm == Charm.HOMEWARD_CHARM ? UseAction.BOW : UseAction.NONE;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remaining) {
        if (world.isClient && charm == Charm.HOMEWARD_CHARM) {
            for (int i = 0; i < 2; i++) {
                world.addParticle(net.minecraft.particle.ParticleTypes.PORTAL,
                        user.getParticleX(0.8), user.getRandomBodyY(), user.getParticleZ(0.8),
                        0, 0.1, 0);
            }
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (charm == Charm.HOMEWARD_CHARM && user instanceof ServerPlayerEntity player
                && Charms.goHome(player)) {
            player.getItemCooldownManager().set(this, charm.cooldown());
        }
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        tooltip.add(Text.translatable(charm.descriptionKey()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(charm.cooldown() > 0 && (charm == Charm.THUNDER_RUNE
                        || charm == Charm.WIND_CHARM || charm == Charm.HOMEWARD_CHARM)
                        ? "villagepax.charm.use" : "villagepax.charm.wear")
                .formatted(Formatting.DARK_GRAY));
    }
}
