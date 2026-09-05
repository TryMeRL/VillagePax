package com.villagepax.client;

import com.villagepax.block.ModBlocks;
import com.villagepax.item.ModItems;
import com.villagepax.item.TownHallBlueprintItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

public class VillagePaxClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        registerTooltips();
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
