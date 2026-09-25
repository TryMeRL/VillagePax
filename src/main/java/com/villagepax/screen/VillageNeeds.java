package com.villagepax.screen;

import com.villagepax.core.ModTags;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.work.Housing;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Что деревне народа нужно сейчас — и как ей это отдать.
 * <p>
 * Заказчик: «ещё бы узнавать, что нужно поселению на данный момент, а то
 * этими недоквестами ничего не получится». Просьбы старейшины — это
 * цепочка, написанная заранее, и она ничего не знает о том, что деревня
 * сегодня строит и чем кормится. А деревня знает: у неё стоит стройка,
 * которой не хватает камня, склад, в котором кончается хлеб, и дома,
 * в которых нет свободных кроватей.
 * <p>
 * Щелчок по ратуше деревни это и рассказывает. А щелчок с нужной вещью
 * в руке её отдаёт: вещь ложится на склад деревни, и деревня помнит,
 * кто помог, — доверием, тем же, что растёт от просьб. Отдать можно
 * только то, чего не хватает, и не больше: иначе ратуша стала бы
 * скупкой, где доверие покупают булыжником.
 */
public final class VillageNeeds {

    /** Меньше стольких дней еды — деревня примет и хлеб. */
    public static final int HUNGRY_DAYS = 3;

    /** Столько принесённого стоит одного очка доверия. */
    public static final int ITEMS_PER_TRUST = 8;

    /** Больше этого доверия за один раз не дают: помощь — не торговля. */
    public static final int TRUST_CAP = 10;

    private VillageNeeds() {
    }

    /** Чего не хватает стройке, которая идёт сейчас, если она идёт. */
    public static Map<Item, Integer> missing(ServerWorld world, Settlement village) {
        return underConstruction(village)
                .map(site -> BuildOrders.stillNeeded(world, village, site))
                .orElse(Map.of());
    }

    private static Optional<Building> underConstruction(Settlement village) {
        return village.buildings().stream().filter(BuildJob::isUnderConstruction).findFirst();
    }

    /** Рассказать, что деревне нужно. */
    public static void tell(ServerWorld world, Settlement village, PlayerEntity player) {
        Culture culture = CultureManager.get(village.culture());
        Text people = culture == null ? Text.literal(village.culture().toString())
                : Text.translatable(culture.displayName());
        player.sendMessage(Text.translatable("villagepax.needs.head",
                Text.literal(village.name()), people).formatted(Formatting.GOLD), false);

        Optional<Building> site = underConstruction(village);
        if (site.isEmpty()) {
            player.sendMessage(Text.translatable("villagepax.needs.nothing_building"), false);
        } else {
            Text name = Text.translatable(BuildingTypes.displayName(site.get().type()));
            Map<Item, Integer> missing = BuildOrders.stillNeeded(world, village, site.get());
            player.sendMessage(missing.isEmpty()
                    ? Text.translatable("villagepax.needs.building_ready", name)
                    : Text.translatable("villagepax.needs.building", name,
                    BuildOrders.shoppingLine(missing, 6)), false);
        }

        Warehouse warehouse = Warehouse.of(world, village);
        int days = TownHallView.daysOfFood(warehouse.tally(), village.population());
        player.sendMessage(days < HUNGRY_DAYS
                ? Text.translatable("villagepax.needs.food_low").formatted(Formatting.RED)
                : Text.translatable("villagepax.needs.food", days), false);
        player.sendMessage(Text.translatable("villagepax.needs.beds",
                Housing.freeSpots(world, village)), false);
        player.sendMessage(Text.translatable("villagepax.needs.hint")
                .formatted(Formatting.GRAY), false);
    }

    /**
     * Отдать деревне то, что в руке, если ей это нужно.
     *
     * @return отдано ли хоть что-нибудь
     */
    public static boolean donate(ServerWorld world, SettlementManager manager, Settlement village,
                                 PlayerEntity player, ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        Item item = held.getItem();
        Map<Item, Integer> wanted = new LinkedHashMap<>(missing(world, village));
        Warehouse warehouse = Warehouse.of(world, village);
        if (held.isIn(ModTags.CITIZEN_FOOD)
                && TownHallView.daysOfFood(warehouse.tally(), village.population()) < HUNGRY_DAYS) {
            wanted.merge(item, held.getMaxCount(), Math::max);
        }
        int need = wanted.getOrDefault(item, 0);
        if (need <= 0) {
            player.sendMessage(Text.translatable("villagepax.needs.not_needed"), true);
            return false;
        }

        int offered = Math.min(need, held.getCount());
        ItemStack left = warehouse.add(new ItemStack(item, offered));
        int taken = offered - left.getCount();
        if (taken <= 0) {
            player.sendMessage(Text.translatable("villagepax.needs.no_room"), true);
            return false;
        }
        if (!player.getAbilities().creativeMode) {
            held.decrement(taken);
        }
        int trust = Math.min(TRUST_CAP, Math.max(1, taken / ITEMS_PER_TRUST));
        manager.update(village.id(), state -> state.addReputation(player.getUuid(), trust));
        player.sendMessage(Text.translatable("villagepax.needs.thanks",
                Text.literal(village.name()), taken, item.getName(), trust), false);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES,
                SoundCategory.NEUTRAL, 1.0f, 1.0f);
        return true;
    }
}
