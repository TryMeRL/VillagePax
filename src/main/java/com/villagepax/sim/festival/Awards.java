package com.villagepax.sim.festival;

import com.villagepax.block.ModBlocks;
import com.villagepax.block.entity.ModBlockEntities;
import com.villagepax.block.festival.TrophyBlockEntity;
import com.villagepax.core.festival.Festival;
import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Призы состязания: ленты за места, кубок первому, доверие чужой деревни.
 * <p>
 * Приз — раз за праздник на игрока и состязание: играть можно сколько
 * угодно, но лавка затейника не должна стать фермой лент. Место без лент
 * (четвёртое и дальше) призом не считается и не закрывает приза: игрок,
 * ставший четвёртым, может сыграть ещё и выиграть.
 * <p>
 * Доверие — только в деревне народа и только до знакомства, как у торга:
 * дружба делом, а не гулянкой. Даётся вместе с призом, то есть тоже
 * раз за праздник на каждое состязание.
 */
public final class Awards {

    public static final int TRUST_FOR_PLAYING = 2;
    public static final int TRUST_FOR_WINNING = 3;

    private Awards() {
    }

    /**
     * Раздать по местам: ленты, кубок первому, доверие чужой деревни до знакомства — раз за праздник.
     *
     * @param players кого наградить по опознавателю; {@code null} — ушёл, награждать некого
     */
    public static void grant(ServerWorld world, SettlementManager manager, Settlement settlement,
                             Festival festival, int index, long day, List<Standings.Placing> placings,
                             Function<UUID, PlayerEntity> players) {
        Festival.Contest contest = festival.contests().get(index);
        for (Standings.Placing placing : placings) {
            Contestant who = placing.who();
            int ribbons = Standings.ribbonsFor(placing.place());
            PlayerEntity player = who.player() ? players.apply(who.id()) : null;
            if (player == null || ribbons == 0) {
                continue;
            }
            if (manager.awarded(settlement.id(), day, who.id(), index)) {
                player.sendMessage(Text.translatable("villagepax.contest.prize.taken",
                        Text.translatable(contest.name())).formatted(Formatting.GRAY), false);
                continue;
            }
            boolean first = placing.place() == 1;
            give(player, new ItemStack(ModFestivalItems.FESTIVAL_RIBBON, ribbons));
            if (first) {
                give(player, trophy(contest.name(), festival.name(), settlement.name(), day, who.name()));
                com.villagepax.sim.life.Chronicle.note(world, settlement, "villagepax.chronicle.champion",
                        who.name(), "#" + contest.name());
            }
            if (settlement.owner().isAutonomous()) {
                trust(manager, settlement, who.id(),
                        TRUST_FOR_PLAYING + (first ? TRUST_FOR_WINNING : 0));
            }
            manager.markAwarded(settlement.id(), day, who.id(), index);
            player.sendMessage(first
                    ? Text.translatable("villagepax.contest.prize.first", ribbons)
                    .formatted(Formatting.GOLD)
                    : Text.translatable("villagepax.contest.prize.place", placing.place(), ribbons)
                    .formatted(Formatting.YELLOW), false);
        }
    }

    /** Кубок с надписью: ключи словаря, а не готовый текст, — читается на языке того, кто смотрит. */
    public static ItemStack trophy(String contest, String festival, String village, long day,
                                   String winner) {
        ItemStack stack = new ItemStack(ModBlocks.TROPHY);
        NbtCompound entity = new NbtCompound();
        entity.put(TrophyBlockEntity.ENGRAVING,
                TrophyBlockEntity.engraving(contest, festival, village, day, winner));
        BlockItem.setBlockEntityNbt(stack, ModBlockEntities.TROPHY, entity);
        return stack;
    }

    /** Доверие деревни — до знакомства и не выше: остаток сверх порога не даётся. */
    private static void trust(SettlementManager manager, Settlement settlement, UUID player,
                              int amount) {
        int cap = Standing.KNOWN.from();
        manager.update(settlement.id(), state -> {
            int now = state.reputationOf(player);
            if (now < cap) {
                state.addReputation(player, Math.min(amount, cap - now));
            }
        });
    }

    /** В руки, а не хватит места — к ногам. */
    private static void give(PlayerEntity player, ItemStack stack) {
        player.getInventory().offerOrDrop(stack);
    }
}
