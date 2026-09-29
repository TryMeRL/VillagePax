package com.villagepax.client.screen;

import com.villagepax.item.ModItems;
import com.villagepax.screen.GameView;
import com.villagepax.screen.GamesNet;
import com.villagepax.screen.PanelMetrics;
import com.villagepax.sim.games.ArmWrestle;
import com.villagepax.sim.games.Bouts;
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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Окно игры за столом: кости «очко» и армрестлинг в такт.
 * <p>
 * Ход считает сервер; экран только просит и рисует. Отметку армрестлинга
 * он ведёт сам, от тика начала по часам своего мира, — снимок приходит раз
 * в два тика, а отметка должна бежать плавно, каждый кадр.
 * <p>
 * Игру не ставит на паузу: соперник бросает при всех с паузой в полсекунды,
 * и в одиночной игре сервер, вставший вместе с окном, не бросил бы ни разу.
 * Закрыть окно до итога — встать из-за стола, то есть сдать партию: иначе
 * закрыть его, увидев перебор соперника на подходе, было бы бесплатным
 * отказом от проигрыша.
 */
public class GameScreen extends BaseOwoScreen<FlowLayout> {

    private static final int PANEL_WIDTH = PanelMetrics.GAME_WIDTH;
    private static final int PANEL_HEIGHT = PanelMetrics.GAME_HEIGHT;
    private static final int PADDING = PanelMetrics.PADDING;
    private static final int GAP = PanelMetrics.GAP;
    private static final int HEADER_HEIGHT = PanelMetrics.HEADER;
    private static final int STATUS_HEIGHT = PanelMetrics.STATUS;
    private static final int BODY_HEIGHT =
            PanelMetrics.bodyHeight(PANEL_HEIGHT, HEADER_HEIGHT, STATUS_HEIGHT);

    /** Ширина текста в карточке: панель без отступов и ползунка. */
    private static final int TEXT_WIDTH = PANEL_WIDTH - 2 * PADDING - 26;

    private static final int NAME_WIDTH = 70;
    private static final int STAKE_WIDTH = 36;
    private static final int BUTTON_WIDTH = 64;

    /** Поле армрестлинга: полоса перевеса сверху, шкала с отметкой под ней. */
    private static final int ARENA_WIDTH = TEXT_WIDTH;
    private static final int ARENA_HEIGHT = 40;
    private static final int TRACK_TOP = 10;
    private static final int TRACK_HEIGHT = 8;
    private static final int SCALE_TOP = 26;
    private static final int SCALE_HEIGHT = 10;

    private GameView view;
    private FlowLayout head;
    private FlowLayout status;
    private FlowLayout body;
    private KeptScroll<FlowLayout> scroll;
    private FlowLayout arena;

    /** Закрыто сервером — сдавать нечего: партии уже нет. */
    private boolean closedByServer;

    public GameScreen(GameView view) {
        super(Text.translatable("villagepax.games.screen.title"));
        this.view = view;
    }

    /** Открыть окно — или обновить открытое, не сбрасывая прокрутку. */
    public static void open(MinecraftClient client, GameView view) {
        if (client.currentScreen instanceof GameScreen already) {
            already.refresh(view);
        } else {
            client.setScreen(new GameScreen(view));
        }
    }

    /** Сервер снял или сдал партию: окно закрывается, причину он уже сказал строкой над рукой. */
    public static void closeFromServer(MinecraftClient client) {
        if (client.currentScreen instanceof GameScreen screen) {
            screen.closedByServer = true;
            screen.close();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
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

    private void refresh(GameView fresh) {
        if (fresh.equals(view)) {
            return;
        }
        boolean sameShape = view.bout().map(GameView.BoutLine::kind)
                .equals(fresh.bout().map(GameView.BoutLine::kind))
                && view.bout().map(GameView.BoutLine::phase)
                .equals(fresh.bout().map(GameView.BoutLine::phase));
        this.view = fresh;
        if (body == null) {
            return;
        }
        // Армрестлинг шлёт перевес раз в два тика: вёрстку тогда не трогаем —
        // полосу рисует кадр, — а то окно мигало бы десять раз в секунду.
        if (sameShape && fresh.bout().filter(bout -> bout.kind().equals(Bouts.Kind.ARM.id())).isPresent()) {
            fillStatus();
            return;
        }
        fill();
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
        LabelComponent name = Components.label(Text.literal(view.rivalName()));
        name.color(Look.LIGHT);
        name.shadow(true);
        row.child(name);
        view.rivalTitle().ifPresent(title -> row.child(Look.pill(Text.translatable(title), Look.LIGHT)));
        row.child(Look.pill(Text.translatable("villagepax.nature." + view.nature()), Look.LIGHT));
        row.child(Look.pill(Text.translatable("villagepax.games.screen.score", view.won(), view.lost()),
                Look.LIGHT));
        head.child(row);
    }

    /** Строка под заголовком: на что играют — или что сейчас в партии. */
    private void fillStatus() {
        status.clearChildren();
        Text line;
        Color colour = Look.MUTED;
        GameView.BoutLine bout = view.bout().orElse(null);
        if (bout == null) {
            line = view.coins()
                    ? Text.translatable("villagepax.games.screen.coins", view.purse())
                    : Text.translatable("villagepax.games.screen.for_fun");
        } else if (bout.outcome().isPresent()) {
            line = outcomeLine(bout);
            colour = bout.paid() > 0 || bout.outcome().get().equals("win") ? Look.GOOD
                    : bout.paid() < 0 || bout.outcome().get().equals("lose") ? Look.BAD : Look.MUTED;
        } else if (bout.kind().equals(Bouts.Kind.ARM.id())) {
            line = Text.translatable("villagepax.games.screen.arm_rule");
        } else if (bout.phase().equals("rival")) {
            line = Text.translatable("villagepax.games.screen.rival_turn", view.rivalName());
        } else {
            line = Text.translatable("villagepax.games.screen.your_turn");
            colour = Look.GOOD;
        }
        LabelComponent label = Components.label(line);
        label.color(colour);
        label.shadow(false);
        status.child(label);
    }

    private Text outcomeLine(GameView.BoutLine bout) {
        Text verdict = Text.translatable("villagepax.games.screen." + bout.outcome().orElse("push"));
        if (bout.paid() == 0) {
            return verdict;
        }
        return Text.translatable("villagepax.games.screen.paid", verdict,
                Text.translatable(bout.paid() > 0 ? "villagepax.games.screen.plus" : "villagepax.games.screen.minus",
                        Math.abs(bout.paid())));
    }

    private void fillBody() {
        body.clearChildren();
        arena = null;
        GameView.BoutLine bout = view.bout().orElse(null);
        if (bout == null) {
            fillChoice();
        } else if (bout.kind().equals(Bouts.Kind.ARM.id())) {
            fillArm(bout);
        } else {
            fillDice(bout);
        }
    }

    /** Без партии: две игры и ставки к каждой; ставки, которой нет, окно не рисует. */
    private void fillChoice() {
        FlowLayout card = Look.card("villagepax.games.screen.choose", new ItemStack(ModItems.COIN));
        for (Bouts.Kind kind : Bouts.Kind.values()) {
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.gap(4);
            LabelComponent name = Components.label(Text.translatable("villagepax.games.screen." + kind.id()));
            name.color(Look.INK);
            name.shadow(false);
            row.child(name.horizontalSizing(Sizing.fixed(NAME_WIDTH)));
            if (view.stakes().isEmpty()) {
                row.child(Look.hint(Text.translatable("villagepax.games.screen.no_stake"),
                        TEXT_WIDTH - NAME_WIDTH - 4));
            }
            for (int stake : view.stakes()) {
                Text label = view.coins()
                        ? Text.translatable("villagepax.games.screen.stake", stake)
                        : Text.translatable("villagepax.games.screen.play");
                row.child(Look.action(label, view.coins() ? STAKE_WIDTH : BUTTON_WIDTH,
                        pressed -> start(kind, stake)));
            }
            card.child(row);
            card.child(Look.hint(Text.translatable("villagepax.games.screen." + kind.id() + "_rule"),
                    TEXT_WIDTH));
        }
        body.child(card);
    }

    /** Кости: свои и соперника, суммы, «Бросить» и «Хватит» — только в свой ход. */
    private void fillDice(GameView.BoutLine bout) {
        FlowLayout card = Look.card("villagepax.games.screen.dice", new ItemStack(Items.BONE));
        card.child(diceRow(Text.translatable("villagepax.games.screen.you"), bout.mine()));
        card.child(diceRow(Text.literal(view.rivalName()), bout.theirs()));
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.gap(4);
        if (bout.outcome().isPresent()) {
            actions.child(Look.action(Text.translatable("villagepax.games.screen.again"), BUTTON_WIDTH,
                    pressed -> start(Bouts.Kind.DICE, bout.stake())));
            actions.child(Look.action(Text.translatable("villagepax.games.screen.leave"), BUTTON_WIDTH,
                    pressed -> close()));
        } else if (bout.phase().equals("player")) {
            actions.child(Look.action(Text.translatable("villagepax.games.screen.roll"), BUTTON_WIDTH,
                    pressed -> send(GamesNet.ROLL)));
            if (!bout.mine().isEmpty()) {
                actions.child(Look.action(Text.translatable("villagepax.games.screen.stand"), BUTTON_WIDTH,
                        pressed -> send(GamesNet.STAND)));
            }
        }
        card.child(actions);
        body.child(card);
    }

    private FlowLayout diceRow(Text who, List<Integer> faces) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(3);
        LabelComponent name = Components.label(who);
        name.color(Look.INK);
        name.shadow(false);
        row.child(name.horizontalSizing(Sizing.fixed(NAME_WIDTH)));
        int total = 0;
        for (int face : faces) {
            row.child(Look.pill(Text.literal(String.valueOf(face)), Look.INK));
            total += face;
        }
        LabelComponent sum = Components.label(Text.translatable("villagepax.games.screen.sum", total));
        sum.color(total > 21 ? Look.BAD : total == 21 ? Look.GOOD : Look.MUTED);
        sum.shadow(false);
        row.child(sum);
        return row;
    }

    /** Армрестлинг: поле рисует кадр, здесь — только место под него и кнопки после итога. */
    private void fillArm(GameView.BoutLine bout) {
        FlowLayout card = Look.card("villagepax.games.screen.arm", new ItemStack(Items.IRON_INGOT));
        arena = Containers.verticalFlow(Sizing.fixed(ARENA_WIDTH), Sizing.fixed(ARENA_HEIGHT));
        card.child(arena);
        // Как жать — сказано строкой над полем, пока партия идёт; после итога
        // на её месте итог, а здесь — «ещё» и «встать».
        if (bout.outcome().isPresent()) {
            FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            actions.gap(4);
            actions.child(Look.action(Text.translatable("villagepax.games.screen.again"), BUTTON_WIDTH,
                    pressed -> start(Bouts.Kind.ARM, bout.stake())));
            actions.child(Look.action(Text.translatable("villagepax.games.screen.leave"), BUTTON_WIDTH,
                    pressed -> close()));
            card.child(actions);
        }
        body.child(card);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        GameView.BoutLine bout = view.bout().orElse(null);
        if (arena == null || bout == null || !bout.kind().equals(Bouts.Kind.ARM.id())) {
            return;
        }
        int x = arena.x();
        int y = arena.y();
        int w = arena.width();

        // Полоса перевеса: слева рука игрока ложится, справа — соперника.
        context.drawText(textRenderer, Text.translatable("villagepax.games.screen.you"), x, y,
                Look.INK.argb(), false);
        Text rival = Text.literal(view.rivalName());
        context.drawText(textRenderer, rival, x + w - textRenderer.getWidth(rival), y, Look.INK.argb(), false);
        int top = y + TRACK_TOP;
        context.fill(x, top, x + w, top + TRACK_HEIGHT, 0x60000000);
        int middle = x + w / 2;
        int knob = middle + (int) Math.round(bout.balance() / 100.0 * (w / 2.0));
        int lean = bout.balance() >= 0 ? Look.GOOD.argb() : Look.BAD.argb();
        context.fill(Math.min(middle, knob), top + 1, Math.max(middle, knob), top + TRACK_HEIGHT - 1, lean);
        context.fill(middle, top - 1, middle + 1, top + TRACK_HEIGHT + 1, 0xFF3A2A18);

        // Шкала такта: зелёное посередине, отметка бежит туда и обратно.
        int scale = y + SCALE_TOP;
        context.fill(x, scale, x + w, scale + SCALE_HEIGHT, 0x40000000);
        int zone = (int) Math.round(bout.zone() * w);
        context.fill(middle - zone / 2, scale, middle + zone / 2, scale + SCALE_HEIGHT, 0xC02F6B2A);
        if (bout.outcome().isEmpty() && client != null && client.world != null) {
            double t = client.world.getTime() - bout.markerStart() + delta;
            int marker = x + (int) Math.round(ArmWrestle.markerAt(t) * (w - 2));
            context.fill(marker, scale - 2, marker + 2, scale + SCALE_HEIGHT + 2, 0xFFF3E6C8);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE && pressing()) {
            send(GamesNet.PRESS);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pressing() && arena != null && arena.isInBoundingBox(mouseX, mouseY)) {
            send(GamesNet.PRESS);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Идёт ли армрестлинг — жать есть куда. */
    private boolean pressing() {
        return view.bout().filter(bout -> bout.kind().equals(Bouts.Kind.ARM.id())
                && bout.outcome().isEmpty()).isPresent();
    }

    /** Встать до итога — сдать партию; после итога — просто уйти. */
    @Override
    public void removed() {
        if (!closedByServer && view.bout().filter(bout -> bout.outcome().isEmpty()).isPresent()) {
            send(GamesNet.LEAVE);
        }
        super.removed();
    }

    /** «Сыграть» и «Ещё партию»: право и ставку считает сервер и присылает свежий снимок. */
    private void start(Bouts.Kind kind, int stake) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.rival());
        buf.writeString(kind.id());
        buf.writeVarInt(stake);
        ClientPlayNetworking.send(GamesNet.START, buf);
    }

    private static void send(Identifier channel) {
        ClientPlayNetworking.send(channel, PacketByteBufs.empty());
    }
}
