package com.villagepax.client.dev;

import com.villagepax.VillagePax;
import com.villagepax.client.screen.QuestScreen;
import com.villagepax.client.screen.TownHallScreen;
import com.villagepax.screen.Mood;
import com.villagepax.screen.QuestView;
import com.villagepax.screen.TownHallNet;
import com.villagepax.screen.TownHallScreenHandler;
import com.villagepax.screen.TownHallView;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.ItemTally;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Снимки экранов мода — инструмент разработки, а не игры.
 * <p>
 * Окна мода верстаются кодом, и увидеть их можно только в игре: щёлкнуть
 * по ратуше, по старейшине, по доске. Проверять так каждую правку долго,
 * а без проверки вёрстка расползается. Запуск клиента со свойством
 * {@code -Dvillagepax.shots=<папка>} открывает на титульном экране каждое
 * окно с образцовыми данными, снимает его в {@code screenshots/<папка>}
 * и закрывает игру. Без свойства класс ничего не делает.
 */
public final class ScreenShots {

    record Shot(String name, Supplier<Screen> screen) {
    }

    private static final int SETTLE_TICKS = 25;

    private static List<Shot> shots;
    private static int at = -1;
    private static int waited;

    private ScreenShots() {
    }

    public static void install() {
        String folder = System.getProperty("villagepax.shots");
        if (folder == null || folder.isBlank()) {
            return;
        }
        VillagePax.LOGGER.info("Снимки экранов: в screenshots/{}", folder);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, folder));
    }

    private static void tick(MinecraftClient client, String folder) {
        if (shots == null) {
            if (!(client.currentScreen instanceof TitleScreen) || client.getOverlay() != null) {
                return;
            }
            shots = samples();
            try {
                java.nio.file.Files.createDirectories(client.runDirectory.toPath()
                        .resolve("screenshots").resolve(folder));
            } catch (java.io.IOException e) {
                VillagePax.LOGGER.warn("Папка снимков не создаётся", e);
            }
            at = 0;
            waited = 0;
            client.setScreen(shots.get(0).screen().get());
            return;
        }
        if (at >= shots.size()) {
            return;
        }
        if (++waited < SETTLE_TICKS) {
            return;
        }
        String name = folder + "/" + shots.get(at).name() + ".png";
        ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(),
                message -> VillagePax.LOGGER.info("Снимок: {}", message.getString()));
        at++;
        waited = 0;
        if (at < shots.size()) {
            client.setScreen(shots.get(at).screen().get());
        } else {
            client.scheduleStop();
        }
    }

    // ===================== образцы =====================

    private static List<Shot> samples() {
        List<Shot> list = new ArrayList<>();
        list.add(new Shot("town_hall_overview", () -> townHall(0)));
        list.add(new Shot("town_hall_buildings", () -> townHall(1)));
        list.add(new Shot("town_hall_citizens", () -> townHall(2)));
        list.add(new Shot("town_hall_stock", () -> townHall(3)));
        list.add(new Shot("town_hall_faith", () -> townHall(4)));
        list.add(new Shot("elder", () -> new QuestScreen(elder())));
        list.addAll(Samples.extra());
        return list;
    }

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    private static Identifier id(String path) {
        return new Identifier("villagepax", path);
    }

    static Screen townHall(int tab) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(UUID.randomUUID());
        buf.writeBlockPos(BlockPos.ORIGIN);
        TownHallNet.writeView(buf, colony());
        TownHallScreenHandler handler = new TownHallScreenHandler(0, new PlayerInventory(null), buf);
        TownHallScreen screen = new TownHallScreen(handler, new PlayerInventory(null), Text.literal("Кан"));
        screen.showTab(tab);
        return new Preview(screen);
    }

    /**
     * Окно с ячейками на титульном экране: его тик спрашивает игрока,
     * которого там нет. Обёртка только раскладывает и рисует.
     */
    static final class Preview extends Screen {
        private final Screen inner;

        Preview(Screen inner) {
            super(inner.getTitle());
            this.inner = inner;
        }

        @Override
        protected void init() {
            inner.init(client, width, height);
        }

        @Override
        public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
            inner.render(context, mouseX, mouseY, delta);
        }
    }

    static TownHallView colony() {
        Map<Identifier, Integer> stock = new LinkedHashMap<>();
        stock.put(new Identifier("minecraft", "oak_log"), 112);
        stock.put(new Identifier("minecraft", "cobblestone"), 340);
        stock.put(new Identifier("minecraft", "bread"), 46);
        stock.put(new Identifier("minecraft", "wheat"), 23);
        stock.put(new Identifier("minecraft", "carrot"), 64);
        stock.put(new Identifier("minecraft", "oak_planks"), 75);
        stock.put(new Identifier("minecraft", "glass_pane"), 12);
        stock.put(new Identifier("villagepax", "coin"), 30);
        stock.put(new Identifier("minecraft", "iron_ingot"), 7);
        Map<Identifier, Integer> missing = new LinkedHashMap<>();
        missing.put(new Identifier("minecraft", "stone_bricks"), 24);
        missing.put(new Identifier("minecraft", "oak_planks"), 10);

        List<TownHallView.BuildingLine> buildings = List.of(
                new TownHallView.BuildingLine(UUID.randomUUID(), id("norman/town_hall"), 2,
                        BuildProgress.DONE, BlockPos.ORIGIN, true, 0),
                new TownHallView.BuildingLine(UUID.randomUUID(), id("norman/house"), 1,
                        BuildProgress.DONE, new BlockPos(10, 64, 4), true, 0),
                new TownHallView.BuildingLine(UUID.randomUUID(), id("norman/house"), 1,
                        BuildProgress.DONE, new BlockPos(-12, 64, 6), true, 0),
                new TownHallView.BuildingLine(UUID.randomUUID(), id("norman/farm"), 1,
                        BuildProgress.DONE, new BlockPos(20, 64, -14), false, 0),
                new TownHallView.BuildingLine(UUID.randomUUID(), id("norman/lumberjack"), 1,
                        BuildProgress.DONE, new BlockPos(-20, 64, -10), false, 0),
                new TownHallView.BuildingLine(UUID.randomUUID(), id("norman/chapel"), 1,
                        BuildProgress.BUILDING, new BlockPos(4, 64, 22), false, 1),
                new TownHallView.BuildingLine(UUID.randomUUID(), id("norman/watchtower"), 1,
                        BuildProgress.PLANNED, new BlockPos(30, 64, 30), false, 2));

        String[][] people = {
                {"Гийом Лефевр", "builder", "villagepax.age.adult", "content"},
                {"Мари Лефевр", "farmer", "villagepax.age.adult", "content"},
                {"Жан Моро", "lumberjack", "villagepax.age.adult", "hungry"},
                {"Анна Моро", "", "villagepax.age.child", "content"},
                {"Пьер Дюбуа", "guard", "villagepax.age.elder", "unhappy"},
                {"Луиза Бернар", "brewer", "villagepax.age.adult", "content"},
        };
        List<TownHallView.CitizenLine> citizens = new ArrayList<>();
        for (String[] person : people) {
            Optional<Identifier> craft = person[1].isEmpty() ? Optional.empty() : Optional.of(id(person[1]));
            citizens.add(new TownHallView.CitizenLine(UUID.randomUUID(), person[0], craft, true,
                    Optional.empty(), Mood.valueOf(person[3].toUpperCase()), false, person[2], 14,
                    "", "villagepax.nature.diligent", "", ""));
        }

        List<TownHallView.ProfessionLine> crafts = List.of(
                new TownHallView.ProfessionLine(id("farmer"), "villagepax.profession.farmer", false, ""),
                new TownHallView.ProfessionLine(id("lumberjack"), "villagepax.profession.lumberjack", false, ""),
                new TownHallView.ProfessionLine(id("guard"), "villagepax.profession.guard", false, ""),
                new TownHallView.ProfessionLine(id("weaver"), "villagepax.profession.weaver", true,
                        "villagepax.level.town"));

        TownHallView.FaithView faith = new TownHallView.FaithView(List.of(
                new TownHallView.GodLine(id("norman_sower"), "villagepax.god.norman.sower", "harvest", 64,
                        "noticed", 120, 2, false),
                new TownHallView.GodLine(id("norman_mason"), "villagepax.god.norman.mason", "stone", 12,
                        "unknown", 40, 0, false),
                new TownHallView.GodLine(id("norman_watchman"), "villagepax.god.norman.watchman", "watch",
                        130, "heard", 250, 0, false)), true);

        return new TownHallView("Кан", NORMAN, "village", 6, 14,
                new TownHallView.Household(8, 2, 46, 7, 3, 30, 12, 10, false),
                Optional.of(new TownHallView.Construction(id("norman/chapel"), 1, 140, 380,
                        new ItemTally(missing))),
                buildings, citizens, new ItemTally(stock),
                List.of(id("norman/house_lvl1"), id("norman/farm_lvl1"), id("norman/brewery_lvl1"),
                        id("norman/watchtower_lvl1")),
                crafts, Optional.of("villagepax.advice.no_beds"),
                new TownHallView.Growth("villagepax.level.village", Optional.of("villagepax.level.town"), 2, 3,
                        List.of("villagepax.building.norman.townhouse", "villagepax.building.norman.weavery"),
                        true),
                faith, new TownHallView.Extras(TownHallView.Yoke.NONE,
                List.of(json(Text.translatable("villagepax.arrival.market_tomorrow")),
                        json(Text.translatable("villagepax.happening.name_day.news", "Мари")),
                        json(Text.translatable("villagepax.arrival.festival_in", 3))),
                List.of(json(Text.translatable("villagepax.chronicle.day", 31,
                                Text.translatable("villagepax.chronicle.wedding", "Жан", "Луиза"))),
                        json(Text.translatable("villagepax.chronicle.day", 28,
                                Text.translatable("villagepax.chronicle.level",
                                        Text.translatable("villagepax.level.village")))),
                        json(Text.translatable("villagepax.chronicle.day", 20,
                                Text.translatable("villagepax.chronicle.raid_repelled", "Ушмаль"))))));
    }

    static String json(Text text) {
        return Text.Serializer.toJson(text);
    }

    static QuestView elder() {
        QuestView.Offer offer = new QuestView.Offer("villagepax.quest.founding_3",
                List.of(new QuestView.Need("villagepax.quest.screen.deliver", Optional.of(Items.BREAD),
                        Optional.empty(), 8, 5)),
                List.of(new QuestView.Prize(QuestView.Prize.TRUST, Optional.empty(), 20),
                        new QuestView.Prize(QuestView.Prize.GOODS,
                                Optional.of(com.villagepax.item.ModItems.TOWN_HALL_BLUEPRINT), 1)),
                false);
        List<QuestView.Stall> stalls = List.of(
                new QuestView.Stall(Items.BREAD, 4, 3, true, QuestView.Ready.YES),
                new QuestView.Stall(Items.IRON_INGOT, 2, 9, true, QuestView.Ready.NO_TRUST),
                new QuestView.Stall(Items.WHEAT, 16, 2, false, QuestView.Ready.YES),
                new QuestView.Stall(Items.OAK_LOG, 16, 3, false, QuestView.Ready.PLAYER_CANT));
        return new QuestView(UUID.randomUUID(), "Бовуар", id("elder"), "villagepax.standing.known", 27,
                Optional.of(45), Optional.of(offer), stalls, 40, Optional.empty(),
                new QuestView.People("villagepax.culture.norman", "villagepax.standing.known", 22, List.of()),
                Optional.empty(), Optional.empty(), true, Optional.empty(), Optional.empty());
    }
}
