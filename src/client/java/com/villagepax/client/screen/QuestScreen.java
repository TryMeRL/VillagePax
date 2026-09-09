package com.villagepax.client.screen;

import com.villagepax.item.ModItems;
import com.villagepax.screen.QuestNet;
import com.villagepax.sim.trade.Coins;
import com.villagepax.screen.QuestView;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
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
import org.jetbrains.annotations.NotNull;

/**
 * Разговор с чужой деревней: квесты и торг.
 * <p>
 * Решение заказчика: чата было мало. В чате видно только то, что житель
 * сказал сейчас, — а игроку нужно видеть <b>сколько доверия</b>, сколько до
 * следующей ступени, что именно просят и сколько из этого уже в сумке.
 * <p>
 * Торг сделан <b>вкладкой того же экрана</b>, а не отдельным окном, и это
 * взято у Millénaire: старейшина — одно лицо, и разговаривать с ним игрок
 * приходит в одно место. Отдельное окно торговли пришлось бы как-то
 * открывать — второй кнопкой, вторым щелчком, — и деревня стала бы
 * автоматом по продаже вместо собеседника.
 * <p>
 * Экран, как и пульт ратуши, ничего не считает: и «хватает ли доверия», и
 * «есть ли у деревни монета» решает сервер и присылает решением. Здесь
 * только вёрстка и намерения.
 */
public class QuestScreen extends BaseOwoScreen<FlowLayout> {

    /** Вкладки. Порядок — порядок в заголовке. */
    private enum Tab {
        TALK("talk"),
        TRADE("trade");

        private final String id;

        Tab(String id) {
            this.id = id;
        }

        Text title() {
            return Text.translatable("villagepax.quest.tab." + id);
        }
    }

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 208;

    /** Ширина названия товара в строке прилавка. */
    private static final int NAME_WIDTH = 138;

    private QuestView view;
    private Tab tab = Tab.TALK;

    private FlowLayout head;
    private FlowLayout tabs;
    private FlowLayout body;

    public QuestScreen(QuestView view) {
        this.view = view;
    }

    /**
     * Открыть разговор — или обновить уже открытый.
     * <p>
     * Обновить, а не создать заново: сервер присылает свежий снимок после
     * каждой сделки, и новый экран сбрасывал бы и вкладку, и прокрутку.
     * Игрок, купивший хлеб, оказывался бы снова на вкладке разговора и
     * искал прилавок заново — после каждой покупки.
     */
    public static void open(MinecraftClient client, QuestView view) {
        if (client.currentScreen instanceof QuestScreen already) {
            already.refresh(view);
        } else {
            client.setScreen(new QuestScreen(view));
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

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(PANEL_WIDTH),
                Sizing.fixed(PANEL_HEIGHT));
        // По отдельности, а не цепочкой: surface возвращает общий тип
        // родителя, и gap на нём уже не найти.
        panel.surface(Surface.DARK_PANEL);
        panel.padding(Insets.of(8));
        panel.gap(6);

        head = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        head.gap(3);
        panel.child(head);

        tabs = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        tabs.gap(4);
        panel.child(tabs);

        body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.gap(3);

        FlowLayout scroll = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        scroll.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), body));
        panel.child(scroll);

        root.child(panel);

        fill();
    }

    /** Новый снимок с сервера: заголовок, вкладки и тело заново. */
    private void refresh(QuestView fresh) {
        this.view = fresh;
        if (body != null) {
            fill();
        }
    }

    private void fill() {
        if (tab == Tab.TRADE && !view.trades()) {
            // Народ перестал торговать, пока экран был открыт: пустая
            // вкладка хуже, чем возврат к разговору.
            tab = Tab.TALK;
        }
        fillHead();
        fillTabs();
        fillBody();
    }

    private void fillHead() {
        head.clearChildren();

        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.child(Components.label(Text.literal(view.villageName())
                .formatted(Formatting.GOLD, Formatting.BOLD)).shadow(true));

        if (view.trades()) {
            // Кошель деревни в заголовке, а не во вкладке торга: по нему
            // видно, есть ли смысл нести товар на продажу, ещё до того,
            // как игрок туда заглянул.
            row.child(coins(view.purse()));
        }
        head.child(row);

        head.child(Components.label(standingLine()).color(Color.ofRgb(0xB0B0B0)));
        head.child(rule());
    }

    private void fillTabs() {
        tabs.clearChildren();

        for (Tab candidate : Tab.values()) {
            if (candidate == Tab.TRADE && !view.trades()) {
                continue;
            }
            Text label = candidate == tab
                    ? candidate.title().copy().formatted(Formatting.YELLOW)
                    : candidate.title();

            ButtonComponent button = Components.button(label, pressed -> {
                tab = candidate;
                fill();
            });
            button.horizontalSizing(Sizing.fixed(84));
            // Выключенная кнопка и есть выбранная вкладка: по ней видно,
            // где ты, и нажимать её повторно незачем.
            button.active(candidate != tab);
            tabs.child(button);
        }
    }

    private void fillBody() {
        body.clearChildren();

        if (tab == Tab.TALK) {
            view.quest().ifPresentOrElse(offer -> fillOffer(offer),
                    () -> body.child(Components.label(
                            Text.translatable("villagepax.quest.screen.nothing")
                                    .formatted(Formatting.GRAY))));
        } else {
            fillStalls();
        }
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

    private void fillOffer(QuestView.Offer offer) {
        LabelComponent words = Components.label(Text.translatable(offer.dialogue()));
        words.lineHeight(10);
        body.child(words.horizontalSizing(Sizing.fixed(PANEL_WIDTH - 30)));

        body.child(heading("villagepax.quest.screen.asks"));
        for (QuestView.Need need : offer.objectives()) {
            body.child(needLine(need));
        }

        if (!offer.rewards().isEmpty()) {
            body.child(heading("villagepax.quest.screen.gives"));
            for (String reward : offer.rewards()) {
                body.child(Components.label(Text.literal("  " + reward)
                        .formatted(Formatting.GRAY)));
            }
        }

        // Кнопка выключена, пока принесено не всё: отказ лучше показать
        // до нажатия, а не после.
        ButtonComponent hand = Components.button(
                Text.translatable("villagepax.quest.screen.hand_in"), button -> handIn());
        hand.active(offer.ready());
        hand.horizontalSizing(Sizing.fixed(120));
        body.child(hand);
    }

    /**
     * Прилавок: сперва что продают, потом что скупают.
     * <p>
     * Именно в таком порядке. Игрок приходит в деревню за товаром чаще,
     * чем с товаром, и первое, что он хочет увидеть, — что тут вообще есть.
     */
    private void fillStalls() {
        // Одна строка про то, откуда цены. Без неё игрок видит числа и не
        // знает, что они изменятся: доверие в этом моде торгует вместе с ним.
        LabelComponent about = Components.label(
                Text.translatable("villagepax.trade.screen.prices").formatted(Formatting.GRAY));
        about.lineHeight(10);
        body.child(about.horizontalSizing(Sizing.fixed(PANEL_WIDTH - 30)));

        fillSide(true, "villagepax.trade.screen.sells");
        fillSide(false, "villagepax.trade.screen.buys");
    }

    private void fillSide(boolean villageSells, String headingKey) {
        boolean any = view.stalls().stream().anyMatch(s -> s.villageSells() == villageSells);
        if (!any) {
            return;
        }

        body.child(heading(headingKey));
        for (QuestView.Stall stall : view.stalls()) {
            if (stall.villageSells() == villageSells) {
                body.child(stallLine(stall));
            }
        }
    }

    /**
     * Строка прилавка: товар, цена, кнопка.
     * <p>
     * Причина отказа висит подсказкой на <b>строке</b>, а не на кнопке:
     * серая кнопка сама ничего не объясняет, а выключенная — тем более
     * не всегда отвечает на наведение.
     */
    private Component stallLine(QuestView.Stall stall) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);
        row.verticalAlignment(VerticalAlignment.CENTER);

        ItemComponent goods = Components.item(new ItemStack(stall.item(), stall.count()));
        goods.showOverlay(true);
        goods.setTooltipFromStack(true);
        row.child(goods);

        boolean ready = stall.ready() == QuestView.Ready.YES;
        LabelComponent name = Components.label(Text.translatable("villagepax.trade.screen.goods",
                stall.item().getName(), Text.literal(String.valueOf(stall.count()))));
        name.color(ready ? Color.WHITE : Color.ofRgb(0x909090));
        row.child(name.horizontalSizing(Sizing.fixed(NAME_WIDTH)));

        row.child(coins(stall.price()));

        ButtonComponent deal = Components.button(Text.translatable(stall.villageSells()
                ? "villagepax.trade.screen.take" : "villagepax.trade.screen.give"),
                button -> trade(stall));
        deal.active(ready);
        deal.horizontalSizing(Sizing.fixed(62));
        row.child(deal);

        stall.ready().reasonKey().ifPresent(key -> row.tooltip(Text.translatable(key)));
        return row;
    }

    /**
     * Монета числом: медяк и сумма.
     * <p>
     * Сумма пишется словами достоинств — «2з 4с 7м», — а не числом медяков:
     * «сто восемьдесят пять» игроку ни о чём не говорит, а «2з 4с 7м» он
     * сравнит с тем, что у него в кошеле, не считая в голове.
     */
    private static Component coins(int amount) {
        FlowLayout purse = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        purse.gap(2);
        purse.verticalAlignment(VerticalAlignment.CENTER);
        purse.child(Components.item(new ItemStack(ModItems.COIN)));
        purse.child(Components.label(Coins.spell(amount).copy()
                .formatted(Formatting.WHITE)));
        return purse;
    }

    /**
     * Требование строкой: предмет, сколько есть, сколько надо. Хватает —
     * зелёным, не хватает — красным: это видно быстрее, чем читается.
     */
    private Component needLine(QuestView.Need need) {
        FlowLayout row = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        row.gap(4);
        row.verticalAlignment(VerticalAlignment.CENTER);

        row.child(Components.item(new ItemStack(need.item())));
        row.child(Components.label(Text.translatable("villagepax.quest.screen.need",
                        need.item().getName(),
                        Text.literal(String.valueOf(need.have())),
                        Text.literal(String.valueOf(need.need()))))
                .color(need.enough() ? Color.ofRgb(0x6ADE6A) : Color.ofRgb(0xE07A6A)));
        return row;
    }

    /** Подзаголовок раздела — тот же, что в пульте ратуши. */
    private static Component heading(String key) {
        return Components.label(Text.translatable(key).formatted(Formatting.YELLOW));
    }

    /** Черта: делит экран на части, чтобы он не читался одной кашей. */
    private static Component rule() {
        BoxComponent line = new BoxComponent(Sizing.fill(100), Sizing.fixed(1));
        line.fill(true);
        line.color(Color.ofArgb(0x40FFFFFF));
        return line;
    }

    private void handIn() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        buf.writeIdentifier(view.giver());
        ClientPlayNetworking.send(QuestNet.HAND_IN, buf);
    }

    /**
     * «Торгую вот этим».
     * <p>
     * Уезжают предмет и число — но не потому, что клиенту верят: по ним
     * сервер <b>ищет</b> строку в столе торга своего датапака. Не нашлась —
     * сделки не будет. Цена не уезжает вовсе: её сервер считает сам, и
     * присланная разошлась бы с пересчитанной на первом же очке доверия.
     */
    private void trade(QuestView.Stall stall) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        buf.writeIdentifier(view.giver());
        buf.writeBoolean(stall.villageSells());
        buf.writeIdentifier(Registries.ITEM.getId(stall.item()));
        buf.writeVarInt(stall.count());
        ClientPlayNetworking.send(QuestNet.TRADE, buf);
    }
}
