package com.villagepax.sim.faith;

import com.villagepax.VillagePax;
import com.villagepax.core.faith.God;
import com.villagepax.core.faith.Gods;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Артефакт: то, ради чего всё это.
 * <p>
 * Правило мода, записанное раньше: <b>цепляет то, чего не получить
 * иначе</b>. Эль и какао были первой такой вещью, сукно — второй;
 * артефакт — третья и самая дорогая. Его не крафтят, не покупают
 * и не выбивают: он входит в мир одной дорогой — рукой бога, которому
 * поселение молилось полсотни дней.
 * <p>
 * <b>Вручается один раз и навсегда.</b> Благосклонность после этого
 * можно тратить на благословения хоть до нуля — артефакт остаётся.
 * Иначе игрок оказался бы перед выбором «пользоваться подарком или
 * не потерять его», а подарок, которым страшно пользоваться, — не
 * награда, а залог.
 */
public final class Artifacts {

    /** С какой ступени бог вручает своё. */
    public static final Faith.Tier FROM = Faith.Tier.KEPT;

    private Artifacts() {
    }

    /**
     * Что этот бог должен поселению прямо сейчас.
     * <p>
     * Чистая функция, и отделена она от вручения нарочно: вручение просит
     * живого игрока, которому класть вещь в сумку, а правило — не просит
     * ничего. Проверять «дают один раз и не отбирают за трату» надо
     * именно правилом.
     *
     * @return опознаватель артефакта, если он заслужен и ещё не вручен
     */
    public static Optional<Identifier> earned(Settlement settlement, Identifier godId) {
        God god = Gods.get(godId).orElse(null);
        if (god == null || god.artifact().isEmpty()) {
            return Optional.empty();
        }
        if (!Faith.tierOf(settlement, godId).reached(FROM)) {
            return Optional.empty();
        }
        Identifier artifactId = god.artifact().get();
        return settlement.hasArtifact(artifactId) ? Optional.empty() : Optional.of(artifactId);
    }

    /**
     * Вручить, если заслужено и ещё не вручено.
     *
     * @return что вручили, если вручили
     */
    public static Optional<Item> grantIfEarned(ServerWorld world, SettlementManager manager,
                                               Settlement settlement, Identifier godId,
                                               ServerPlayerEntity player) {
        Identifier artifactId = earned(settlement, godId).orElse(null);
        if (artifactId == null) {
            return Optional.empty();
        }
        God god = Gods.get(godId).orElseThrow();

        Item artifact = Registries.ITEM.get(artifactId);
        if (artifact == Registries.ITEM.get(new Identifier("minecraft", "air"))) {
            // Датапак назвал предмет, которого в игре нет. Отметить его
            // выданным нельзя: тогда исправленный пак уже ничего не вручит,
            // а игрок останется без награды за полсотни дней.
            VillagePax.LOGGER.error("Бог {} вручает {}, но такого предмета нет", godId, artifactId);
            return Optional.empty();
        }

        manager.update(settlement.id(), state -> state.noteArtifact(artifactId));

        ItemStack stack = new ItemStack(artifact);
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }

        player.sendMessage(Text.translatable("villagepax.faith.artifact.granted",
                Text.translatable(god.displayName()), stack.getName()), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 0.7f, 1.0f);
        return Optional.of(artifact);
    }
}
