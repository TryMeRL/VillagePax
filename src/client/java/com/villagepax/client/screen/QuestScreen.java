package com.villagepax.client.screen;

import com.villagepax.screen.QuestNet;
import com.villagepax.screen.QuestView;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Разговор со старейшиной чужой деревни.
 * <p>
 * Решение заказчика: чата было мало. В чате видно только то, что житель
 * сказал сейчас, — а игроку нужно видеть <b>сколько доверия</b>, сколько
 * до следующей ступени, что именно просят и сколько из этого уже в сумке.
 * <p>
 * Экран, как и пульт ратуши, ничего не считает: всё приходит снимком
 * с сервера, потому что и квесты датапака, и репутация лежат там. Здесь
 * только вёрстка и одно намерение — «отдаю».
 */
public class QuestScreen extends BaseOwoScreen<FlowLayout> {

    private static final int PANEL_WIDTH = 300;

    private final QuestView view;

    public QuestScreen(QuestView view) {
        this.view = view;
    }

    /** Открыть разговор. Зовётся из приёмника пакета. */
    public static void open(MinecraftClient client, QuestView view) {
        client.setScreen(new QuestScreen(view));
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.CENTER);

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content());
        // По отдельности, а не цепочкой: surface возвращает общий тип
        // родителя, и gap на нём уже не найти.
        panel.gap(4);
        panel.surface(Surface.DARK_PANEL);
        panel.padding(Insets.of(10));

        panel.child(Components.label(Text.literal(view.villageName()).formatted(Formatting.GOLD)));
        panel.child(Components.label(standingLine()));

        view.quest().ifPresentOrElse(offer -> fillOffer(panel, offer),
                () -> panel.child(Components.label(
                        Text.translatable("villagepax.quest.screen.nothing")
                                .formatted(Formatting.GRAY))));

        root.child(panel);
    }

    /** Строка доверия: ступень, число и сколько до следующей. */
    private Text standingLine() {
        Text standing = Text.translatable(view.standing());
        return view.nextAt()
                .map(next -> Text.translatable("villagepax.quest.screen.standing_next",
                        standing, Text.literal(String.valueOf(view.reputation())),
                        Text.literal(String.valueOf(next - view.reputation()))))
                .orElseGet(() -> Text.translatable("villagepax.quest.screen.standing",
                        standing, Text.literal(String.valueOf(view.reputation()))));
    }

    private void fillOffer(FlowLayout panel, QuestView.Offer offer) {
        LabelComponent words = Components.label(Text.translatable(offer.dialogue()));
        words.lineHeight(10);
        panel.child(words.horizontalSizing(Sizing.fixed(PANEL_WIDTH - 20)));

        panel.child(Components.label(Text.translatable("villagepax.quest.screen.asks")
                .formatted(Formatting.YELLOW)));
        for (QuestView.Need need : offer.objectives()) {
            panel.child(needLine(need));
        }

        if (!offer.rewards().isEmpty()) {
            panel.child(Components.label(Text.translatable("villagepax.quest.screen.gives")
                    .formatted(Formatting.YELLOW)));
            for (String reward : offer.rewards()) {
                panel.child(Components.label(Text.literal("  " + reward)
                        .formatted(Formatting.GRAY)));
            }
        }

        // Кнопка выключена, пока принесено не всё: отказ лучше показать
        // до нажатия, а не после.
        panel.child(Components.button(Text.translatable("villagepax.quest.screen.hand_in"),
                        button -> handIn())
                .active(offer.ready())
                .horizontalSizing(Sizing.fixed(120)));
    }

    /**
     * Требование строкой: предмет, сколько есть, сколько надо. Хватает —
     * зелёным, не хватает — красным: это видно быстрее, чем читается.
     */
    private io.wispforest.owo.ui.core.Component needLine(QuestView.Need need) {
        FlowLayout row = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        row.gap(4);
        row.verticalAlignment(VerticalAlignment.CENTER);

        row.child(Components.item(new ItemStack(need.item())));
        row.child(Components.label(Text.translatable("villagepax.quest.screen.need",
                        itemName(Registries.ITEM.getId(need.item())),
                        Text.literal(String.valueOf(need.have())),
                        Text.literal(String.valueOf(need.need()))))
                .color(need.enough() ? Color.ofRgb(0x6ADE6A) : Color.ofRgb(0xE07A6A)));
        return row;
    }

    private static Text itemName(Identifier item) {
        return Registries.ITEM.get(item).getName();
    }

    private void handIn() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        buf.writeIdentifier(view.giver());
        ClientPlayNetworking.send(QuestNet.HAND_IN, buf);
    }
}
