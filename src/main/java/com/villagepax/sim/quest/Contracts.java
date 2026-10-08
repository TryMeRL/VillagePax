package com.villagepax.sim.quest;

import com.villagepax.core.quest.Quest;
import com.villagepax.item.ModItems;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Standing;
import com.villagepax.sim.Villages;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Контракты гильдии авантюристов.
 * <p>
 * Поручения ремёсел — мелкая помощь по хозяйству; контракт гильдии —
 * работа для того, кто с мечом: принести доказательство охоты (гнилую
 * плоть, кости, порох), добычу разведки (жемчуг Края, аметист) или
 * трофей с края мира (огненный стержень, череп иссушителя). Доказательство
 * — вещь, а не счёт убитых: так контракт сдаётся тем же путём, что и всё
 * остальное, и не требует следить за каждым ударом игрока.
 * <p>
 * <b>Ранг</b> — по доверию деревни: чужаку дают работу новичка, знакомому
 * — следопыта, другу и выше — героя. Чем выше ранг, тем опаснее заказ
 * и тем больше платят: серебро, потом золото. Один контракт в день,
 * как и поручение, — по кругу, со своим сдвигом у каждой гильдии.
 */
public final class Contracts {

    /** Ранг авантюриста у этой гильдии. */
    public enum Rank {
        NOVICE("novice", ModItems.SILVER_COIN, 2, 2),
        PATHFINDER("pathfinder", ModItems.SILVER_COIN, 5, 3),
        HERO("hero", ModItems.GOLD_COIN, 2, 4);

        private final String id;
        private final Item coin;
        private final int pay;
        private final int trust;

        Rank(String id, Item coin, int pay, int trust) {
            this.id = id;
            this.coin = coin;
            this.pay = pay;
            this.trust = trust;
        }

        public String id() {
            return id;
        }

        /** Ранг по доверию: чужак — новичок, знакомый — следопыт, друг — герой. */
        public static Rank of(int reputation) {
            Standing standing = Standing.of(reputation);
            if (standing.ordinal() >= Standing.FRIEND.ordinal()) {
                return HERO;
            }
            return standing == Standing.KNOWN ? PATHFINDER : NOVICE;
        }
    }

    /** Что принести по контракту. */
    record Bounty(Item item, int count) {
    }

    private static final List<Bounty> NOVICE = List.of(
            new Bounty(Items.ROTTEN_FLESH, 16), new Bounty(Items.BONE, 12),
            new Bounty(Items.STRING, 12), new Bounty(Items.SPIDER_EYE, 6));

    private static final List<Bounty> PATHFINDER = List.of(
            new Bounty(Items.GUNPOWDER, 8), new Bounty(Items.ENDER_PEARL, 3),
            new Bounty(Items.SLIME_BALL, 6), new Bounty(Items.PHANTOM_MEMBRANE, 2),
            new Bounty(Items.AMETHYST_SHARD, 8));

    private static final List<Bounty> HERO = List.of(
            new Bounty(Items.BLAZE_ROD, 4), new Bounty(Items.GHAST_TEAR, 1),
            new Bounty(Items.WITHER_SKELETON_SKULL, 1), new Bounty(Items.PRISMARINE_SHARD, 12),
            new Bounty(Items.ECHO_SHARD, 2));

    private Contracts() {
    }

    /** Даёт ли это ремесло контракты. */
    public static boolean gives(Identifier giver) {
        return Villages.GUILDMASTER.equals(giver);
    }

    static List<Bounty> board(Rank rank) {
        return switch (rank) {
            case NOVICE -> NOVICE;
            case PATHFINDER -> PATHFINDER;
            case HERO -> HERO;
        };
    }

    /** Контракт гильдии на этот день для авантюриста с таким доверием. */
    public static Optional<Quest> forToday(Settlement village, long today, int reputation) {
        Rank rank = Rank.of(reputation);
        List<Bounty> bounties = board(rank);
        int index = Math.floorMod(village.id().hashCode() * 31 + (int) today, bounties.size());
        Bounty bounty = bounties.get(index);
        List<Quest.Reward> rewards = new ArrayList<>();
        rewards.add(new Quest.Reward.Give(rank.coin, rank.pay));
        rewards.add(new Quest.Reward.Trust(rank.trust));
        return Optional.of(new Quest(Villages.GUILDMASTER, Optional.of(village.culture()), 0,
                List.of(new Quest.Objective.Deliver(bounty.item(), bounty.count())),
                List.copyOf(rewards), dialogueKey(rank, index), Optional.empty()));
    }

    static String dialogueKey(Rank rank, int index) {
        return "villagepax.contract." + rank.id() + "." + index;
    }

    /** Ключи слов всех контрактов: их проверяет словарь. */
    public static List<String> dialogueKeys() {
        List<String> keys = new ArrayList<>();
        for (Rank rank : Rank.values()) {
            for (int i = 0; i < board(rank).size(); i++) {
                keys.add(dialogueKey(rank, i));
            }
        }
        return keys;
    }
}
