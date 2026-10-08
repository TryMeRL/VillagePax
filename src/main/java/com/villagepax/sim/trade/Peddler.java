package com.villagepax.sim.trade;

import com.villagepax.VillagePax;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.trade.Caravan;
import com.villagepax.item.ModItems;
import com.villagepax.item.charm.Charm;
import com.villagepax.item.charm.Charms;
import com.villagepax.screen.QuestView;
import com.villagepax.sim.Ground;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Бродячий торговец: обоз без родной деревни, с заморским товаром.
 * <p>
 * Обозы деревень везут то, что деревня делает сама, и по её ценам: хлеб,
 * эль, сукно. Бродячий торговец везёт то, чего у соседей нет: семена
 * и саженцы дальних земель, полезные редкости — бирку, седло, жемчуг
 * Края, — и одну диковину: оберег или часть доспеха чужого народа,
 * которые иначе достаются только друзьям этого народа. Дорого, но сразу.
 * <p>
 * Заходит раз в {@link #EVERY_DAYS} дней (где есть гильдия — вдвое чаще),
 * у каждого поселения свой день, — и в колонию игрока, и в деревни
 * народов от ступени «деревня»; стоит
 * у ратуши до следующего утра. Только покупает игрок: торговец налегке,
 * монеты на закупки у него нет.
 * <p>
 * Лежит той же записью обоза ({@link Caravan}), что и обозы деревень,
 * с особым опознавателем дома {@link #HOME}: так его ставит, привязывает
 * к месту и провожает уже написанный и проверенный код обозов.
 */
public final class Peddler {

    /** «Дом» бродячего торговца — никакой: по нему его и узнают. */
    public static final UUID HOME = new UUID(0L, 0x7065646c6572L);

    /** Раз в столько дней он заходит в одно поселение. */
    public static final int EVERY_DAYS = 12;

    /** Сколько видов товара везёт, кроме диковины. */
    public static final int KINDS = 5;

    /** Товар с ценой: столько штук за столько медяков. */
    public record Ware(Item item, int count, int price) {
    }

    private Peddler() {
    }

    public static boolean isPeddler(Caravan guest) {
        return HOME.equals(guest.home());
    }

    /**
     * Зайдёт ли он сегодня в это поселение. Чистое правило.
     * <p>
     * Где стоит гильдия авантюристов, там останавливаются странники:
     * туда торговец заходит вдвое чаще. Это и есть польза гильдии
     * в колонии игрока — контрактов своя гильдия хозяину не даёт.
     */
    public static boolean dueAt(Settlement host, long day) {
        boolean welcome = !host.owner().isAutonomous()
                || host.level().ordinal() >= SettlementLevel.VILLAGE.ordinal();
        int every = hasGuild(host) ? EVERY_DAYS / 2 : EVERY_DAYS;
        return welcome && Math.floorMod(day * 5 + host.id().hashCode(), every) == 0;
    }

    /** Стоит ли в поселении достроенная гильдия авантюристов. */
    static boolean hasGuild(Settlement host) {
        return host.buildings().stream().anyMatch(building -> building.isOperational()
                && com.villagepax.core.building.BuildingTypes.employs(building.type(), Villages.GUILDMASTER));
    }

    /** Утро: не пора ли торговцу прийти. Зовётся из суточного обхода поселения. */
    public static void newDay(ServerWorld world, SettlementManager manager, Settlement host, long today) {
        if (!dueAt(host, today) || host.visitors().stream().anyMatch(Peddler::isPeddler)
                || host.siege().isPresent()) {
            return;
        }
        BlockPos stands = Ground.spotNear(world, host.center(), 3, 7);
        if (stands == null) {
            return;
        }
        Random random = new Random(host.id().getLeastSignificantBits() ^ today * 0x9E3779B97F4A7C15L);
        Identifier people = peopleOf(host, random);
        ItemTally cargo = new ItemTally(wares(random, people).stream().collect(
                java.util.stream.Collectors.toMap(ware -> Registries.ITEM.getId(ware.item()), Ware::count,
                        Integer::sum, LinkedHashMap::new)));
        Caravan caravan = new Caravan(UUID.randomUUID(), HOME, people, stands, cargo, 0, today + 1);
        manager.update(host.id(), state -> state.welcome(caravan));
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getBlockPos().getSquaredDistance(host.center()) < 96 * 96) {
                player.sendMessage(Text.translatable("villagepax.peddler.arrived", Text.literal(host.name()),
                        Text.translatable("villagepax.culture." + people.getPath())).formatted(Formatting.GOLD),
                        false);
            }
        }
        VillagePax.LOGGER.info("Бродячий торговец ({}) пришёл в {}", people, host.name());
    }

    /** Какого он народа: не того, к кому пришёл, — иначе какой он заморский. */
    static Identifier peopleOf(Settlement host, Random random) {
        List<Identifier> peoples = new ArrayList<>(CultureManager.ids());
        peoples.remove(host.culture());
        peoples.sort(java.util.Comparator.comparing(Identifier::toString));
        return peoples.isEmpty() ? host.culture() : peoples.get(random.nextInt(peoples.size()));
    }

    // --- товар ---

    /** Семена и саженцы дальних земель — дёшево. */
    private static final List<Ware> COMMON = List.of(
            new Ware(Items.CHERRY_SAPLING, 2, 6), new Ware(Items.JUNGLE_SAPLING, 2, 6),
            new Ware(Items.ACACIA_SAPLING, 2, 6), new Ware(Items.DARK_OAK_SAPLING, 4, 8),
            new Ware(Items.BAMBOO, 8, 5), new Ware(Items.COCOA_BEANS, 6, 6),
            new Ware(Items.SWEET_BERRIES, 8, 4), new Ware(Items.GLOW_BERRIES, 6, 8),
            new Ware(Items.MELON_SEEDS, 6, 5), new Ware(Items.PUMPKIN_SEEDS, 6, 5),
            new Ware(Items.CACTUS, 4, 5), new Ware(Items.SUGAR_CANE, 6, 4),
            new Ware(Items.LILY_PAD, 4, 4), new Ware(Items.SPORE_BLOSSOM, 1, 18));

    /** Полезные редкости — по серебру. */
    private static final List<Ware> UNCOMMON = List.of(
            new Ware(Items.NAME_TAG, 1, 18), new Ware(Items.SADDLE, 1, 27),
            new Ware(Items.ENDER_PEARL, 2, 27), new Ware(Items.EXPERIENCE_BOTTLE, 4, 18),
            new Ware(Items.LEAD, 2, 12), new Ware(Items.SPYGLASS, 1, 20),
            new Ware(Items.GOLDEN_CARROT, 4, 15), new Ware(Items.TURTLE_EGG, 1, 30),
            new Ware(Items.AXOLOTL_BUCKET, 1, 36), new Ware(Items.TRIDENT, 1, 120));

    /** Цена диковины — оберега или части доспеха чужого народа: два золотых. */
    public static final int RARITY_PRICE = Coins.GOLD * 2;

    /** Что он везёт сегодня: три дешёвых, две редкости и одну диковину. */
    static List<Ware> wares(Random random, Identifier people) {
        List<Ware> chosen = new ArrayList<>();
        pick(COMMON, 3, random, chosen);
        pick(UNCOMMON, KINDS - 3, random, chosen);
        rarity(people, random).ifPresent(chosen::add);
        return chosen;
    }

    private static void pick(List<Ware> pool, int many, Random random, List<Ware> into) {
        List<Ware> left = new ArrayList<>(pool);
        for (int i = 0; i < many && !left.isEmpty(); i++) {
            into.add(left.remove(random.nextInt(left.size())));
        }
    }

    /** Диковина народа торговца: его оберег или шлем, или сапоги. */
    private static Optional<Ware> rarity(Identifier people, Random random) {
        List<Item> curios = new ArrayList<>();
        Charm charm = Charm.ofPeople(people.getPath());
        if (charm != null) {
            curios.add(Charms.itemOf(charm));
        }
        for (String piece : List.of("helmet", "boots")) {
            Item item = Registries.ITEM.get(new Identifier(VillagePax.MOD_ID, people.getPath() + "_" + piece));
            if (item != Items.AIR) {
                curios.add(item);
            }
        }
        return curios.isEmpty() ? Optional.empty()
                : Optional.of(new Ware(curios.get(random.nextInt(curios.size())), 1, RARITY_PRICE));
    }

    /** Цена товара за ту стопку, что он везёт. */
    public static int priceOf(Item item) {
        for (List<Ware> pool : List.of(COMMON, UNCOMMON)) {
            for (Ware ware : pool) {
                if (ware.item() == item) {
                    return ware.price();
                }
            }
        }
        return RARITY_PRICE;
    }

    /** Сколько штук в одной продаже. */
    public static int lotOf(Item item) {
        for (List<Ware> pool : List.of(COMMON, UNCOMMON)) {
            for (Ware ware : pool) {
                if (ware.item() == item) {
                    return ware.count();
                }
            }
        }
        return 1;
    }

    // --- лавка ---

    /** Окно торговца — тем же экраном, что у обоза, со своим прилавком. */
    public static QuestView viewOf(Caravan guest, PlayerEntity player) {
        List<QuestView.Stall> stalls = new ArrayList<>();
        int coins = Coins.total(player.getInventory());
        guest.cargo().contents().forEach((id, count) -> {
            Item item = Registries.ITEM.get(id);
            int lot = Math.min(count, lotOf(item));
            int price = priceOf(item);
            stalls.add(new QuestView.Stall(item, lot, price, true,
                    coins >= price ? QuestView.Ready.YES : QuestView.Ready.PLAYER_CANT));
        });
        String people = "villagepax.culture." + guest.culture().getPath();
        return new QuestView(guest.id(), Text.translatable("villagepax.peddler.title").getString(),
                Villages.MERCHANT, "villagepax.standing.stranger", 0, Optional.empty(), Optional.empty(),
                stalls, 0, Optional.of(guest.id()),
                new QuestView.People(people, "villagepax.standing.stranger", 0, List.of()),
                Optional.empty(), Optional.empty(), true, Optional.empty(), Optional.empty());
    }

    /**
     * Купить у торговца стопку товара.
     *
     * @return обоз с убавленным товаром, если купили
     */
    public static Optional<Caravan> sell(PlayerEntity player, Caravan guest, Item item) {
        Identifier id = Registries.ITEM.getId(item);
        int have = guest.cargo().contents().getOrDefault(id, 0);
        if (have <= 0) {
            player.sendMessage(Text.translatable("villagepax.trade.gone"), true);
            return Optional.empty();
        }
        int lot = Math.min(have, lotOf(item));
        int price = priceOf(item);
        if (!Coins.has(player.getInventory(), price)) {
            player.sendMessage(Text.translatable("villagepax.trade.refused.no_coin"), true);
            return Optional.empty();
        }
        Coins.pay(player.getInventory(), price).forEach(change -> player.getInventory().offerOrDrop(change));
        player.getInventory().offerOrDrop(new ItemStack(item, lot));
        Map<Identifier, Integer> left = new LinkedHashMap<>(guest.cargo().contents());
        if (have - lot > 0) {
            left.put(id, have - lot);
        } else {
            left.remove(id);
        }
        return Optional.of(guest.withCargo(new ItemTally(left), 0));
    }
}
