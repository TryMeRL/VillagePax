package com.villagepax.client;

import com.villagepax.block.ModBlocks;
import com.villagepax.entity.ModEntities;
import com.villagepax.item.ModItems;
import com.villagepax.item.TownHallBlueprintItem;
import com.villagepax.client.screen.TownHallScreen;
import com.villagepax.screen.TownHallNet;
import com.villagepax.screen.TownHallScreens;
import com.villagepax.screen.TownHallView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class VillagePaxClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.CITIZEN, CitizenEntityRenderer::new);
        HandledScreens.register(TownHallScreens.TOWN_HALL, TownHallScreen::new);
        registerViewUpdates();
        registerTooltips();
    }

    /**
     * Новый снимок колонии: экран обновляется, если он открыт.
     * <p>
     * Читать буфер надо в сетевом потоке, а показывать — в клиентском:
     * между ними {@code client.execute}, и буфера к тому времени уже нет.
     */
    private static void registerViewUpdates() {
        ClientPlayNetworking.registerGlobalReceiver(TownHallNet.VIEW,
                (client, handler, buf, sender) -> {
                    TownHallView fresh = TownHallNet.readView(buf);
                    client.execute(() -> TownHallScreen.open(client)
                            .ifPresent(screen -> screen.refresh(fresh)));
                });
    }

    /**
     * Подсказки живут в клиентском наборе исходников, а не в общем коде.
     * Ванильный {@code Item.appendTooltip} тянет за собой клиентский тип
     * в общий код, а это прямой путь к падению выделенного сервера.
     */
    private static void registerTooltips() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (stack.isOf(ModItems.TOWN_HALL_BLUEPRINT)) {
                Identifier culture = TownHallBlueprintItem.cultureOf(stack);
                lines.add(Text.translatable("villagepax.blueprint.culture",
                                Text.translatable("villagepax.culture." + culture.getPath()))
                        .formatted(Formatting.GOLD));
                lines.add(Text.translatable("villagepax.blueprint.hint").formatted(Formatting.GRAY));
                return;
            }

            if (stack.getItem() instanceof BlockItem blockItem && isMarker(blockItem.getBlock())) {
                lines.add(Text.translatable("villagepax.tooltip.marker").formatted(Formatting.DARK_GRAY));
            }
        });
    }

    private static boolean isMarker(Block block) {
        return block == ModBlocks.MARKER_WORKSTATION
                || block == ModBlocks.MARKER_BED
                || block == ModBlocks.MARKER_STORAGE
                || block == ModBlocks.MARKER_DOOR
                || block == ModBlocks.MARKER_DECOR;
    }
}
