package com.villagepax.client.dev;

import com.villagepax.client.screen.VillageHallScreen;
import com.villagepax.screen.VillageHallView;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Образцы прочих окон: дописываются по мере того, как окна переделываются. */
final class Samples {

    private Samples() {
    }

    static List<ScreenShots.Shot> extra() {
        List<ScreenShots.Shot> list = new ArrayList<>();
        for (int tab = 0; tab < 5; tab++) {
            int shown = tab;
            list.add(new ScreenShots.Shot("village_hall_" + tab,
                    () -> new VillageHallScreen(villageHall()).showTab(shown)));
        }
        list.add(new ScreenShots.Shot("festival", () -> new com.villagepax.client.screen.FestivalScreen(festival())));
        list.add(new ScreenShots.Shot("game", () -> new com.villagepax.client.screen.GameScreen(game())));
        list.add(new ScreenShots.Shot("board", () -> boardScreen()));
        list.add(new ScreenShots.Shot("settings", () -> new com.villagepax.client.screen.SettingsScreen(null)));
        return list;
    }

    static com.villagepax.screen.FestivalView festival() {
        return new com.villagepax.screen.FestivalView(UUID.randomUUID(), "Бовуар", "Колен Пуассон",
                "villagepax.citizen.title.entertainer", "villagepax.festival.norman", 0, Optional.empty(),
                List.of(new com.villagepax.screen.FestivalView.ContestLine("villagepax.contest.norman.chase",
                                "chase", false, Optional.empty()),
                        new com.villagepax.screen.FestivalView.ContestLine("villagepax.contest.norman.archery",
                                "archery", true, Optional.empty()),
                        new com.villagepax.screen.FestivalView.ContestLine("villagepax.contest.norman.hunt",
                                "hunt", false, Optional.of("villagepax.contest.reason.busy"))),
                5, true,
                List.of(new com.villagepax.screen.FestivalView.PrizeLine(id("norman_wreath"), 1, 8, false),
                        new com.villagepax.screen.FestivalView.PrizeLine(id("norman_bunting"), 4, 2, true),
                        new com.villagepax.screen.FestivalView.PrizeLine(
                                new Identifier("minecraft", "firework_rocket"), 8, 1, true)),
                Optional.empty());
    }

    static net.minecraft.client.gui.screen.Screen boardScreen() {
        com.villagepax.screen.QuestView.Offer offer = ScreenShots.elder().quest().orElseThrow();
        List<com.villagepax.screen.BoardView.Sheet> sheets = List.of(
                new com.villagepax.screen.BoardView.Sheet(id("elder"), "Бертран Кан", Optional.empty(), offer, true),
                new com.villagepax.screen.BoardView.Sheet(id("farmer"), "Эмма Мартен", Optional.empty(), offer, true),
                new com.villagepax.screen.BoardView.Sheet(id("guard"), "Робер Дюпон", Optional.empty(), offer, false));
        com.villagepax.screen.BoardView view = new com.villagepax.screen.BoardView(UUID.randomUUID(), "Бовуар",
                BlockPos.ORIGIN, "villagepax.standing.known", 27, sheets);
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        com.villagepax.client.screen.BoardScreen.open(client, view);
        return client.currentScreen;
    }

    static com.villagepax.screen.GameView game() {
        return new com.villagepax.screen.GameView(UUID.randomUUID(), UUID.randomUUID(), "Готье Мартен",
                Optional.of("villagepax.citizen.title.builder"), "ambitious", 2, 1, true, 14,
                List.of(1, 2, 4), Optional.empty());
    }

    private static Identifier id(String path) {
        return new Identifier("villagepax", path);
    }

    private static String json(Text text) {
        return Text.Serializer.toJson(text);
    }

    static VillageHallView villageHall() {
        Map<Identifier, Integer> missing = new LinkedHashMap<>();
        missing.put(new Identifier("minecraft", "stone_bricks"), 24);
        missing.put(new Identifier("minecraft", "oak_planks"), 16);
        missing.put(new Identifier("minecraft", "glass_pane"), 6);
        Map<Identifier, Integer> carried = new LinkedHashMap<>();
        carried.put(new Identifier("minecraft", "stone_bricks"), 40);
        carried.put(new Identifier("minecraft", "oak_planks"), 0);
        carried.put(new Identifier("minecraft", "glass_pane"), 3);
        List<VillageHallView.Person> people = List.of(
                new VillageHallView.Person("Бертран Кан", Optional.of(id("elder")), "villagepax.age.elder", true),
                new VillageHallView.Person("Жанна Кан", Optional.of(id("merchant")), "villagepax.age.adult", true),
                new VillageHallView.Person("Готье Мартен", Optional.of(id("builder")), "villagepax.age.adult", false),
                new VillageHallView.Person("Эмма Мартен", Optional.of(id("farmer")), "villagepax.age.adult", false),
                new VillageHallView.Person("Робер Дюпон", Optional.of(id("guard")), "villagepax.age.adult", true),
                new VillageHallView.Person("Люк Дюпон", Optional.empty(), "villagepax.age.child", false));
        List<VillageHallView.House> houses = List.of(
                new VillageHallView.House(id("norman/town_hall"), 2, true),
                new VillageHallView.House(id("norman/house"), 1, true),
                new VillageHallView.House(id("norman/house"), 2, true),
                new VillageHallView.House(id("norman/farm"), 1, true),
                new VillageHallView.House(id("norman/market_stall"), 1, true),
                new VillageHallView.House(id("norman/chapel"), 1, false));
        return new VillageHallView(UUID.randomUUID(), "Бовуар", id("norman"), "villagepax.level.village",
                Optional.of("villagepax.level.town"), BlockPos.ORIGIN, 9, 14,
                new VillageHallView.Trust(27, "villagepax.standing.known", Optional.of("villagepax.standing.friend"),
                        45, 20, 100, 100, true),
                new VillageHallView.Calendar(127, 0, true, Optional.of("villagepax.festival.norman"), 3),
                new VillageHallView.Needs(Optional.of(id("norman/chapel")), missing, 5, false, 2, carried),
                List.of(json(Text.translatable("villagepax.arrival.market_today")),
                        json(Text.translatable("villagepax.happening.quarrel.news", "Готье", "Робер")),
                        json(Text.translatable("villagepax.arrival.festival_in", 3))),
                people, houses,
                List.of(json(Text.translatable("villagepax.chronicle.day", 120,
                                Text.translatable("villagepax.chronicle.wedding", "Робер", "Мари"))),
                        json(Text.translatable("villagepax.chronicle.day", 96,
                                Text.translatable("villagepax.chronicle.champion", "Steve",
                                        Text.translatable("villagepax.contest.norman.chase"))))),
                new VillageHallView.Ties(false, 0, 0, false, "not_a_friend"));
    }
}
