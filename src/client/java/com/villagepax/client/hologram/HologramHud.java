package com.villagepax.client.hologram;

import com.villagepax.screen.TownHallNet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Подсказка режима установки: что за здание, можно ли здесь и какие клавиши.
 * <p>
 * Без неё режим установки — это молчаливый призрак и никакого способа
 * узнать, чем его повернуть. Строка с приговором сервера здесь же: игрок
 * должен видеть «нельзя» до подтверждения, а не после.
 */
public final class HologramHud {

    private static final int MARGIN = 6;
    private static final int LINE = 11;

    private HologramHud() {
    }

    public static void render(DrawContext context, float tickDelta) {
        Placement placement = Placement.active();
        if (placement == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int y = MARGIN;

        Text title = Text.translatable("villagepax.hologram.placing",
                Text.translatable(TownHallNet.buildingKey(typeOf(placement)))).formatted(Formatting.GOLD);
        context.drawTextWithShadow(client.textRenderer, title, MARGIN, y, 0xFFFFFF);
        y += LINE;

        Text verdict = Text.translatable(placement.reason())
                .formatted(placement.allowed() ? Formatting.GREEN : Formatting.RED);
        context.drawTextWithShadow(client.textRenderer, verdict, MARGIN, y, 0xFFFFFF);
        y += LINE;

        Text keys = Text.translatable("villagepax.hologram.keys",
                HologramKeys.rotateKey(), HologramKeys.confirmKey(), HologramKeys.cancelKey())
                .formatted(Formatting.GRAY);
        context.drawTextWithShadow(client.textRenderer, keys, MARGIN, y, 0xFFFFFF);
    }

    /** Схема {@code norman/farm_lvl1} — это здание {@code norman/farm}. */
    private static net.minecraft.util.Identifier typeOf(Placement placement) {
        String path = placement.schematic().getPath();
        int marker = path.lastIndexOf("_lvl");

        return marker < 0
                ? placement.schematic()
                : new net.minecraft.util.Identifier(placement.schematic().getNamespace(),
                        path.substring(0, marker));
    }
}
