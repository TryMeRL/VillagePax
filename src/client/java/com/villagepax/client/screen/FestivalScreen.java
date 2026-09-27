package com.villagepax.client.screen;

import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.screen.FestivalNet;
import com.villagepax.screen.FestivalView;
import com.villagepax.screen.PanelMetrics;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
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
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * Экран затейника: когда праздник, во что играют и что дают за ленты.
 * <p>
 * Одним листом, без вкладок: состязаний три и товаров три, и игроку,
 * пришедшему на ярмарку, нужно видеть сразу и игру, и приз за неё.
 * Кнопка «Начать» — только у того, что можно начать сейчас; у остального
 * вместо неё причина. Экран без причины, на котором кнопка молча
 * не работает, выглядел бы поломкой.
 * <p>
 * Оформление — общее с пультом и разговором ({@link Look}), высота тела —
 * числом ({@link PanelMetrics}), по той же причине, что и у них.
 */
public class FestivalScreen extends BaseOwoScreen<FlowLayout> {

    private static final int PANEL_WIDTH = PanelMetrics.FESTIVAL_WIDTH;
    private static final int PANEL_HEIGHT = PanelMetrics.FESTIVAL_HEIGHT;
    private static final int PADDING = PanelMetrics.PADDING;
    private static final int GAP = PanelMetrics.GAP;
    private static final int HEADER_HEIGHT = PanelMetrics.HEADER;
    private static final int STATUS_HEIGHT = PanelMetrics.STATUS;
    private static final int BODY_HEIGHT =
            PanelMetrics.bodyHeight(PANEL_HEIGHT, HEADER_HEIGHT, STATUS_HEIGHT);

    /** Ширина текста в карточке: панель без отступов и ползунка. */
    private static final int TEXT_WIDTH = PANEL_WIDTH - 2 * PADDING - 26;

    /** Ширина названия состязания и товара в строке. */
    private static final int NAME_WIDTH = 150;

    private static final int BUTTON_WIDTH = 56;

    private FestivalView view;
    private FlowLayout head;
    private FlowLayout status;
    private FlowLayout body;
    private KeptScroll<FlowLayout> scroll;

    public FestivalScreen(FestivalView view) {
        this.view = view;
    }

    /** Открыть экран — или обновить открытый, не сбрасывая прокрутку. */
    public static void open(MinecraftClient client, FestivalView view) {
        if (client.currentScreen instanceof FestivalScreen already) {
            already.refresh(view);
        } else {
            client.setScreen(new FestivalScreen(view));
        }
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT);
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.verticalAlignment(VerticalAlignment.CENTER);

        FlowLayout panel = Look.panel(PANEL_WIDTH, PANEL_HEIGHT, PADDING, GAP);

        head = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(HEADER_HEIGHT));
        panel.child(head);

        status = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(STATUS_HEIGHT));
        status.verticalAlignment(VerticalAlignment.CENTER);
        panel.child(status);

        body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.gap(4);
        scroll = new KeptScroll<>(Sizing.fill(100), Sizing.fixed(BODY_HEIGHT), body);
        scroll.scrollbarThiccness(4);
        scroll.padding(Insets.right(6));
        panel.child(scroll);

        root.child(panel);
        fill();
    }

    private void refresh(FestivalView fresh) {
        if (fresh.equals(view)) {
            return;
        }
        this.view = fresh;
        if (body != null) {
            fill();
        }
    }

    private void fill() {
        double kept = scroll == null ? 0 : scroll.where();
        fillHead();
        fillStatus();
        fillBody();
        if (scroll != null) {
            scroll.restore(kept);
        }
    }

    private void fillHead() {
        head.clearChildren();
        FlowLayout row = Look.board(HEADER_HEIGHT - 8);
        LabelComponent name = Components.label(Text.translatable(view.festival()));
        name.color(Look.LIGHT);
        name.shadow(true);
        row.child(name);
        row.child(Look.pill(Text.literal(view.villageName()), Look.LIGHT));
        row.child(Look.pill(new ItemStack(ModFestivalItems.FESTIVAL_RIBBON),
                Text.literal(String.valueOf(view.ribbons())), view.ribbons() > 0 ? Look.LIGHT : Look.MUTED));
        head.child(row);
    }

    /** Строка под заголовком: сегодня ли праздник — или когда и почему нет. */
    private void fillStatus() {
        status.clearChildren();
        Text line;
        if (view.closed().isEmpty()) {
            line = Text.translatable("villagepax.festival.screen.open",
                    Text.translatable(view.hostTitle()), view.host());
        } else if (view.daysUntil() > 0) {
            line = Text.translatable("villagepax.festival.screen.in", view.daysUntil());
        } else {
            line = Text.translatable(view.closed().get());
        }
        LabelComponent label = Components.label(line);
        label.color(view.closed().isEmpty() ? Look.GOOD : Look.MUTED);
        label.shadow(false);
        status.child(label);
    }

    private void fillBody() {
        body.clearChildren();
        fillContests();
        fillStall();
    }

    private void fillContests() {
        FlowLayout card = Look.card("villagepax.festival.screen.contests", new ItemStack(Items.BOW));
        if (view.contests().isEmpty()) {
            card.child(Look.nothing(Text.translatable("villagepax.festival.screen.none"), TEXT_WIDTH));
        }
        view.running().ifPresent(running -> card.child(Look.hint(
                Text.translatable("villagepax.festival.screen.running", Text.translatable(running)),
                TEXT_WIDTH)));
        for (int index = 0; index < view.contests().size(); index++) {
            FestivalView.ContestLine contest = view.contests().get(index);
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.gap(4);
            LabelComponent name = Components.label(Text.translatable(contest.name()));
            name.color(Look.INK);
            name.shadow(false);
            row.child(name.horizontalSizing(Sizing.fixed(NAME_WIDTH)));
            if (contest.awarded()) {
                row.child(Look.pill(Text.translatable("villagepax.festival.screen.awarded"), Look.GOOD));
            }
            if (contest.refusal().isEmpty()) {
                int chosen = index;
                row.child(Look.action(Text.translatable("villagepax.festival.screen.start"), BUTTON_WIDTH,
                        pressed -> start(chosen)));
            }
            card.child(row);
            card.child(Look.hint(Text.translatable("villagepax.contest.rule." + contest.kind()), TEXT_WIDTH));
            contest.refusal().filter(reason -> view.closed().isEmpty())
                    .ifPresent(reason -> card.child(Look.hint(Text.translatable(reason), TEXT_WIDTH)));
        }
        body.child(card);
    }

    /** Лавка: товар за ленты, и только в праздник. */
    private void fillStall() {
        FlowLayout card = Look.card("villagepax.festival.screen.stall",
                new ItemStack(ModFestivalItems.FESTIVAL_RIBBON));
        boolean open = view.stallOpen();
        if (!open) {
            card.child(Look.hint(Text.translatable("villagepax.festival.screen.stall_closed"), TEXT_WIDTH));
        }
        for (int index = 0; index < view.prizes().size(); index++) {
            FestivalView.PrizeLine prize = view.prizes().get(index);
            ItemStack stack = new ItemStack(Registries.ITEM.get(prize.item()), prize.count());
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.gap(4);
            ItemComponent picture = Components.item(stack);
            picture.setTooltipFromStack(true);
            picture.showOverlay(true);
            row.child(picture);
            LabelComponent name = Components.label(stack.getName());
            name.color(Look.INK);
            name.shadow(false);
            row.child(name.horizontalSizing(Sizing.fixed(NAME_WIDTH - 20)));
            row.child(Look.pill(new ItemStack(ModFestivalItems.FESTIVAL_RIBBON),
                    Text.literal(String.valueOf(prize.price())), prize.affordable() ? Look.INK : Look.BAD));
            if (open) {
                int chosen = index;
                ButtonComponent take = Look.action(Text.translatable("villagepax.festival.screen.take"),
                        BUTTON_WIDTH, pressed -> buy(chosen));
                take.active(prize.affordable());
                row.child(take);
            }
            card.child(row);
        }
        body.child(card);
    }

    /** «Начать»: право считает сервер; экран закрывается — игра идёт на ярмарке, а не в меню. */
    private void start(int index) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        buf.writeVarInt(index);
        ClientPlayNetworking.send(FestivalNet.START, buf);
        close();
    }

    /** «Взять»: цену и ленты считает сервер и присылает свежий снимок. */
    private void buy(int index) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        buf.writeVarInt(index);
        ClientPlayNetworking.send(FestivalNet.BUY, buf);
    }
}
