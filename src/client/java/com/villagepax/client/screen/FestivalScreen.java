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

    private static final int NAME_WIDTH = 150;

    private static final int BUTTON_WIDTH = 56;

    private FestivalView view;
    /** Окно во весь экран. */
    private Frame frame;

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
        frame = Frame.build(root, width, height, false);
        fill();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.uiAdapter = null;
        super.resize(client, width, height);
    }

    private int text() {
        return frame.textWidth;
    }

    private void refresh(FestivalView fresh) {
        if (fresh.equals(view)) {
            return;
        }
        this.view = fresh;
        if (frame != null) {
            fill();
        }
    }

    private void fill() {
        double kept = frame.scroll.where();
        fillHead();
        fillStatus();
        fillBody();
        frame.scroll.restore(kept);
    }

    private void fillHead() {
        frame.headerLeft.clearChildren();
        frame.headerRight.clearChildren();
        frame.title(Text.translatable(view.festival()));
        frame.headerLeft.child(Look.pill(Text.literal(view.villageName()), Look.LIGHT));
        frame.headerRight.child(Look.pill(new ItemStack(ModFestivalItems.FESTIVAL_RIBBON),
                Text.literal(String.valueOf(view.ribbons())), view.ribbons() > 0 ? Look.LIGHT : Look.MUTED));
        frame.closeButton(button -> close());
    }

    private void fillStatus() {
        frame.footer.clearChildren();
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
        frame.footer.child(label);
    }

    private void fillBody() {
        frame.clear();
        fillContests();
        fillStall();
    }

    /**
     * Состязания — каждое своей карточкой: название с картинкой, правило,
     * кнопка «Начать» или причина, почему нельзя. Одной карточкой на всё
     * они в широком окне стояли узкой полосой и обрезали кнопку.
     */
    private void fillContests() {
        if (view.contests().isEmpty()) {
            FlowLayout card = Look.card("villagepax.festival.screen.contests", new ItemStack(Items.BOW));
            card.child(Look.nothing(Text.translatable("villagepax.festival.screen.none"), text()));
            frame.place(card, 2);
        }
        view.running().ifPresent(running -> frame.wide(Look.hint(
                Text.translatable("villagepax.festival.screen.running", Text.translatable(running)),
                frame.bodyWidth)));
        for (int index = 0; index < view.contests().size(); index++) {
            FestivalView.ContestLine contest = view.contests().get(index);
            FlowLayout card = Look.card(contest.name(), new ItemStack(switch (contest.kind()) {
                case "archery" -> Items.BOW;
                case "chase" -> Items.LEAD;
                default -> Items.SPYGLASS;
            }));
            card.child(Look.hint(Text.translatable("villagepax.contest.rule." + contest.kind()), text()));
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.gap(4);
            if (contest.refusal().isEmpty()) {
                int chosen = index;
                row.child(Look.action(Text.translatable("villagepax.festival.screen.start"), BUTTON_WIDTH,
                        pressed -> start(chosen)));
            }
            if (contest.awarded()) {
                row.child(Look.pill(Text.translatable("villagepax.festival.screen.awarded"), Look.GOOD));
            }
            if (!row.children().isEmpty()) {
                card.child(row);
            }
            contest.refusal().filter(reason -> view.closed().isEmpty())
                    .ifPresent(reason -> card.child(Look.hint(Text.translatable(reason), text())));
            frame.place(card, card.children().size() + 1);
        }
    }

    /** Лавка: товар за ленты, и только в праздник. */
    private void fillStall() {
        FlowLayout card = Look.card("villagepax.festival.screen.stall",
                new ItemStack(ModFestivalItems.FESTIVAL_RIBBON));
        boolean open = view.stallOpen();
        if (!open) {
            card.child(Look.hint(Text.translatable("villagepax.festival.screen.stall_closed"), text()));
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
        frame.place(card, card.children().size());
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
