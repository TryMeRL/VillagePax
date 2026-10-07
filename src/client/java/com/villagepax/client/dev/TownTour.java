package com.villagepax.client.dev;

import com.villagepax.VillagePax;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

import java.util.List;

/**
 * Осмотр города глазами — инструмент разработки, а не игры.
 * <p>
 * Снимок схемой показывает разметку сверху, но не то, как город выглядит
 * с дороги: рельеф, лес, свет, высоту стены. Со свойством
 * {@code -Dvillagepax.tour=<народ>,<ступень>} клиент сам создаёт мир,
 * поднимает в нём город командой {@code /villagepax raise}, облетает его
 * наблюдателем и снимает с нескольких точек в {@code screenshots/tour},
 * после чего закрывается. Без свойства класс ничего не делает.
 */
public final class TownTour {

    /** Точка съёмки: смещение от ратуши, поворот и наклон взгляда. */
    private record View(String name, int dx, int dy, int dz, float yaw, float pitch) {
    }

    private static final List<View> VIEWS = List.of(
            new View("aerial", 0, 45, -75, 0f, 30f),
            new View("wall", 0, 3, -62, 0f, 0f),
            new View("east", 75, 35, 0, 90f, 25f),
            new View("above", 0, 110, 0, 0f, 90f));

    private enum Step { TITLE, JOINING, MOVING, RAISING, VIEWING, DONE }

    /** Сколько мест пробовать, прежде чем сдаться. */
    private static final int ATTEMPTS = 8;

    /** На сколько блоков отходить от неудачного места. */
    private static final int STRIDE = 400;

    private static Step step = Step.TITLE;
    private static int waited;
    private static int view;
    private static BlockPos centre;
    private static BlockPos origin;
    private static int attempt;

    private TownTour() {
    }

    public static void install() {
        String spec = System.getProperty("villagepax.tour");
        if (spec == null || spec.isBlank()) {
            return;
        }
        String[] parts = spec.split(",");
        String culture = parts[0];
        String level = parts.length > 1 ? parts[1] : "town";
        VillagePax.LOGGER.info("Осмотр города: {} до ступени {}", culture, level);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, culture, level));
    }

    private static void tick(MinecraftClient client, String culture, String level) {
        switch (step) {
            case TITLE -> {
                if (client.currentScreen instanceof TitleScreen && client.getOverlay() == null) {
                    createWorld(client);
                    step = Step.JOINING;
                    waited = 0;
                }
            }
            case JOINING -> {
                if (client.world == null || client.player == null) {
                    return;
                }
                if (++waited < 100) {
                    return;
                }
                command(client, "gamerule doDaylightCycle false");
                command(client, "gamerule doWeatherCycle false");
                command(client, "gamerule doMobSpawning false");
                command(client, "time set 5000");
                command(client, "weather clear");
                origin = client.player.getBlockPos();
                attempt = 0;
                raiseHere(client, culture, level);
            }
            case MOVING -> {
                // Чанки на новом месте грузятся не сразу: подождать, потом строить.
                if (++waited < 120) {
                    return;
                }
                raiseHere(client, culture, level);
            }
            case RAISING -> {
                if (++waited == 60) {
                    BlockPos raised = raisedNear(client, culture);
                    if (raised == null) {
                        attempt++;
                        if (attempt >= ATTEMPTS) {
                            VillagePax.LOGGER.warn("Город не встал ни на одном из {} мест", ATTEMPTS);
                            step = Step.DONE;
                            client.scheduleStop();
                            return;
                        }
                        command(client, String.format(java.util.Locale.ROOT, "spreadplayers %d %d 0 1 false @s",
                                origin.getX() + attempt * STRIDE, origin.getZ() + (attempt % 2) * STRIDE / 2));
                        step = Step.MOVING;
                        waited = 0;
                        return;
                    }
                    centre = raised;
                    VillagePax.LOGGER.info("Город встал у {}", centre.toShortString());
                }
                if (waited < 260) {
                    return;
                }
                command(client, "gamemode spectator");
                client.options.hudHidden = true;
                view = 0;
                goTo(client, VIEWS.get(0));
                step = Step.VIEWING;
                waited = 0;
            }
            case VIEWING -> {
                // Чанки под llvmpipe дорисовываются долго: ждём, потом снимаем.
                if (++waited < 160) {
                    return;
                }
                ScreenshotRecorder.saveScreenshot(client.runDirectory,
                        "tour/" + culture + "_" + level + "_" + VIEWS.get(view).name() + ".png",
                        client.getFramebuffer(),
                        message -> VillagePax.LOGGER.info("Снимок: {}", message.getString()));
                view++;
                waited = 0;
                if (view < VIEWS.size()) {
                    goTo(client, VIEWS.get(view));
                } else {
                    step = Step.DONE;
                    client.scheduleStop();
                }
            }
            case DONE -> {
            }
        }
    }

    /** Поднять город там, где стоит игрок. */
    private static void raiseHere(MinecraftClient client, String culture, String level) {
        centre = client.player.getBlockPos();
        command(client, "villagepax raise villagepax:" + culture + " 40 " + level);
        step = Step.RAISING;
        waited = 0;
    }

    /** Встал ли город этого народа рядом — его ратуша, спрошенная у сервера. */
    private static BlockPos raisedNear(MinecraftClient client, String culture) {
        net.minecraft.server.integrated.IntegratedServer server = client.getServer();
        if (server == null) {
            return null;
        }
        BlockPos near = centre;
        return server.submit(() -> com.villagepax.sim.SettlementManager.get(server.getOverworld()).all().stream()
                .filter(settlement -> settlement.culture().getPath().equals(culture))
                .filter(settlement -> settlement.center().getSquaredDistance(near) < 80 * 80)
                .map(com.villagepax.sim.Settlement::center)
                .findFirst().orElse(null)).join();
    }

    private static void createWorld(MinecraftClient client) {
        try {
            java.nio.file.Files.createDirectories(client.runDirectory.toPath().resolve("screenshots/tour"));
        } catch (java.io.IOException ignored) {
            // Снимки скажут сами, если папки нет.
        }
        String name = "tour-" + System.currentTimeMillis();
        long seed = Long.getLong("villagepax.tour.seed", 20261007L);
        LevelInfo info = new LevelInfo(name, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), new DataConfiguration(
                new DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                FeatureFlags.DEFAULT_ENABLED_FEATURES));
        client.createIntegratedServerLoader().createAndStart(name, info,
                new GeneratorOptions(seed, true, false),
                registries -> registries.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.DEFAULT)
                        .value().createDimensionsRegistryHolder());
    }

    private static void goTo(MinecraftClient client, View at) {
        command(client, String.format(java.util.Locale.ROOT, "tp @s %d %d %d %.1f %.1f",
                centre.getX() + at.dx(), centre.getY() + at.dy(), centre.getZ() + at.dz(),
                at.yaw(), at.pitch()));
    }

    private static void command(MinecraftClient client, String line) {
        if (client.player != null) {
            client.player.networkHandler.sendChatCommand(line);
        }
    }
}
