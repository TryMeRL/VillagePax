package com.villagepax.client.screen;

import com.villagepax.item.ModItems;
import com.villagepax.screen.PanelMetrics;
import com.villagepax.screen.QuestNet;
import com.villagepax.screen.QuestView;
import com.villagepax.sim.trade.Coins;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
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
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
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
 * Оформление общее с пультом колонии ({@link Look}): два экрана мода
 * не должны выглядеть как два мода. Высота тела считается числом — по той
 * же причине, по которой пульт не листался: {@code Sizing.fill} в owo
 * означает процент всего места контейнера, а не остатка.
 */
public class QuestScreen extends BaseOwoScreen<FlowLayout> {

    /** Вкладки. Порядок — порядок в заголовке. */
    private enum Tab {
        TALK("talk"),
        TRADE("trade"),
        PEOPLE("people");

        private final String id;

        Tab(String id) {
            this.id = id;
        }

        Text title() {
            return Text.translatable("villagepax.quest.tab." + id);
        }
    }

    private static final int PANEL_WIDTH = PanelMetrics.ELDER_WIDTH;
    private static final int PANEL_HEIGHT = PanelMetrics.ELDER_HEIGHT;
    private static final int PADDING = PanelMetrics.PADDING;
    private static final int GAP = PanelMetrics.GAP;

    private static final int HEADER_HEIGHT = PanelMetrics.HEADER;
    private static final int TABS_HEIGHT = PanelMetrics.TABS;

    private static final int BODY_HEIGHT =
            PanelMetrics.bodyHeight(PANEL_HEIGHT, HEADER_HEIGHT, TABS_HEIGHT);

    /** Ширина названия товара в строке прилавка. */
    private static final int NAME_WIDTH = 130;

    /** Ширина кнопки-вкладки: проверена {@link PanelMetrics#tabsFit}. */
    private static final int TAB_WIDTH = PanelMetrics.ELDER_TAB;

    /** Ширина названия народа в строке соседа. */
    private static final int PEOPLE_WIDTH = 120;

    /** Ширина текста внутри карточки: панель без отступов и ползунка. */
    private static final int TEXT_WIDTH = PANEL_WIDTH - 2 * PADDING - 26;

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

        FlowLayout panel = Look.panel(PANEL_WIDTH, PANEL_HEIGHT, PADDING, GAP);

        head = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(HEADER_HEIGHT));
        head.gap(3);
        panel.child(head);

        tabs = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(TABS_HEIGHT));
        tabs.gap(3);
        panel.child(tabs);

        body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.gap(4);

        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(
                Sizing.fill(100), Sizing.fixed(BODY_HEIGHT), body);
        scroll.scrollbarThiccness(4);
        scroll.padding(Insets.right(6));
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

        // Имя деревни — на доске, как вывеска над входом.
        FlowLayout row = Look.board(HEADER_HEIGHT - 8);

        LabelComponent name = Components.label(Text.literal(view.villageName()));
        name.color(Look.LIGHT);
        name.shadow(true);
        row.child(name);

        row.child(Look.pill(standingLine(), Look.LIGHT));

        if (view.trades()) {
            // Кошель деревни в заголовке, а не во вкладке торга: по нему
            // видно, есть ли смысл нести товар на продажу, ещё до того,
            // как игрок туда заглянул.
            row.child(Look.pill(new ItemStack(ModItems.COIN), Coins.spell(view.purse()),
                    view.purse() > 0 ? Look.LIGHT : Look.MUTED));
        }

        head.child(row);
    }

    private void fillTabs() {
        tabs.clearChildren();
        for (Tab candidate : Tab.values()) {
            if (candidate == Tab.TRADE && !view.trades()) {
                continue;
            }
            tabs.child(Look.tab(candidate.title(), candidate == tab, TAB_WIDTH, pressed -> {
                tab = candidate;
                fill();
            }));
        }
    }

    private void fillBody() {
        body.clearChildren();
        switch (tab) {
            case TALK -> fillTalk();
            case TRADE -> fillStalls();
            case PEOPLE -> fillPeople();
        }
    }

    /**
     * Вкладка народа: чей это народ, как он смотрит на игрока, как — на
     * соседей, и что будет, если подарить то, что в руке.
     * <p>
     * Одной вкладкой, а не тремя карточками в разговоре: это <b>другой
     * разговор</b>. С деревней говорят о деле — что принести, что купить;
     * о народе спрашивают отдельно, и ответ на такой вопрос нужен целиком,
     * а не строкой между квестом и прилавком.
     */
    private void fillPeople() {
        QuestView.People people = view.people();

        FlowLayout who = Look.card("villagepax.people.screen.people",
                new ItemStack(Items.BELL));
        who.child(Look.stat(Text.translatable(people.name()),
                Text.translatable("villagepax.people.screen.trust",
                        Text.translatable(people.standing()),
                        Text.literal(String.valueOf(people.trust()))),
                PEOPLE_WIDTH));
        LabelComponent about = Components.label(
                Text.translatable("villagepax.people.screen.about"));
        about.color(Look.MUTED);
        about.lineHeight(10);
        who.child(about.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
        body.child(who);

        if (!people.neighbours().isEmpty()) {
            FlowLayout others = Look.card("villagepax.people.screen.neighbours",
                    new ItemStack(Items.MAP));
            for (QuestView.Neighbour neighbour : people.neighbours()) {
                others.child(Look.stat(Text.translatable(neighbour.name()),
                        Text.translatable(neighbour.attitude()), PEOPLE_WIDTH));
            }
            body.child(others);
        }

        fillWar();
        fillGift();
    }

    /**
     * Карточка войны: сколько мечей придёт и сколько стоит, чтобы не пришли.
     * <p>
     * Стоит <b>перед подарком</b>, и это не про порядок карточек. Игрок,
     * к которому ходят отряды, пришёл говорить не о подарках: если война
     * есть, она и есть разговор.
     */
    private void fillWar() {
        QuestView.Truce truce = view.truce().orElse(null);
        if (truce == null) {
            return;
        }

        FlowLayout card = Look.card("villagepax.people.screen.war",
                new ItemStack(Items.IRON_SWORD));

        if (truce.resting()) {
            card.child(Look.stat(Text.translatable("villagepax.people.screen.truce"),
                    Text.translatable("villagepax.people.screen.truce_days",
                            Text.literal(String.valueOf(truce.daysLeft()))), PEOPLE_WIDTH));
            LabelComponent why = Components.label(
                    Text.translatable("villagepax.people.screen.truce_about"));
            why.color(Look.MUTED);
            why.lineHeight(10);
            card.child(why.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
            body.child(card);
            return;
        }

        LabelComponent threat = Components.label(
                Text.translatable("villagepax.people.screen.war_threat",
                        Text.literal(String.valueOf(truce.fighters()))));
        threat.color(Look.BAD);
        threat.lineHeight(10);
        card.child(threat.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));

        FlowLayout row = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(6);
        row.child(Look.pill(new ItemStack(ModItems.COIN), Coins.spell(truce.price()),
                truce.canPay() ? Look.INK : Look.MUTED));

        ButtonComponent buy = Look.action(
                Text.translatable("villagepax.people.screen.buy_peace"), 92, button -> peace());
        buy.active(truce.canPay());
        row.child(buy);
        if (!truce.canPay()) {
            row.tooltip(Text.translatable("villagepax.peace.reason.no_coin"));
        }
        card.child(row);
        body.child(card);
    }

    /**
     * Карточка подарка.
     * <p>
     * Показывает <b>что именно возьмут</b>, а не что в руке: если в стопке
     * больше, чем стоит суточной благодарности, число будет меньше стопки.
     * Иначе игрок отдал бы шестьдесят четыре железа за те же восемь очков
     * и справедливо счёл бы это надувательством.
     */
    private void fillGift() {
        FlowLayout card = Look.card("villagepax.people.screen.gift",
                new ItemStack(Items.SUNFLOWER));
        QuestView.Gift gift = view.gift().orElse(null);

        if (gift == null) {
            LabelComponent empty = Components.label(
                    Text.translatable("villagepax.gift.reason.empty_handed"));
            empty.color(Look.MUTED);
            empty.lineHeight(10);
            card.child(empty.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
            body.child(card);
            return;
        }

        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);
        row.verticalAlignment(VerticalAlignment.CENTER);

        ItemComponent picture = Components.item(new ItemStack(gift.item(), gift.count()));
        picture.showOverlay(true);
        picture.setTooltipFromStack(true);
        row.child(picture);

        LabelComponent name = Components.label(Text.translatable("villagepax.trade.screen.goods",
                gift.item().getName(), Text.literal(String.valueOf(gift.count()))));
        name.color(gift.ready() ? Look.INK : Look.MUTED);
        row.child(name.horizontalSizing(Sizing.fixed(NAME_WIDTH)));

        row.child(Look.pill(Text.translatable("villagepax.people.screen.worth",
                Text.literal("+" + gift.trust())), gift.ready() ? Look.GOOD : Look.MUTED));

        ButtonComponent give = Look.action(
                Text.translatable("villagepax.people.screen.give"), 58, button -> gift());
        give.active(gift.ready());
        row.child(give);

        gift.verdict().reasonKey().ifPresent(key -> row.tooltip(Text.translatable(key)));
        card.child(row);
        body.child(card);
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

    private void fillTalk() {
        QuestView.Offer offer = view.quest().orElse(null);
        if (offer == null) {
            FlowLayout card = Look.card(null);
            card.child(Look.nothing(Text.translatable("villagepax.quest.screen.nothing"),
                    TEXT_WIDTH));
            body.child(card);
            return;
        }

        FlowLayout words = Look.card(null);
        LabelComponent said = Components.label(Text.translatable(offer.dialogue()));
        said.color(Look.INK);
        said.lineHeight(10);
        words.child(said.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
        body.child(words);

        FlowLayout asks = Look.card("villagepax.quest.screen.asks",
                new ItemStack(Items.CHEST));
        for (QuestView.Need need : offer.objectives()) {
            asks.child(needRow(need));
        }
        body.child(asks);

        if (!offer.rewards().isEmpty()) {
            FlowLayout gives = Look.card("villagepax.quest.screen.gives",
                    new ItemStack(ModItems.COIN));
            for (QuestView.Prize prize : offer.rewards()) {
                gives.child(prizeRow(prize));
            }
            body.child(gives);
        }

        // Кнопка выключена, пока принесено не всё: отказ лучше показать
        // до нажатия, а не после.
        ButtonComponent hand = Look.action(
                Text.translatable("villagepax.quest.screen.hand_in"), 130, button -> handIn());
        hand.active(offer.ready());
        body.child(hand);
    }

    /**
     * Прилавок: сперва что продают, потом что скупают.
     * <p>
     * Именно в таком порядке. Игрок приходит в деревню за товаром чаще,
     * чем с товаром, и первое, что он хочет увидеть, — что тут вообще есть.
     */
    private void fillStalls() {
        LabelComponent about = Components.label(
                Text.translatable("villagepax.trade.screen.prices"));
        about.color(Look.MUTED);
        about.lineHeight(10);
        body.child(about.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));

        fillSide(true, "villagepax.trade.screen.sells");
        fillSide(false, "villagepax.trade.screen.buys");
    }

    private void fillSide(boolean villageSells, String headingKey) {
        boolean any = view.stalls().stream().anyMatch(s -> s.villageSells() == villageSells);
        if (!any) {
            return;
        }

        FlowLayout card = Look.card(headingKey);
        for (QuestView.Stall stall : view.stalls()) {
            if (stall.villageSells() == villageSells) {
                card.child(stallRow(stall));
            }
        }
        body.child(card);
    }

    /**
     * Строка прилавка: товар, цена, кнопка.
     * <p>
     * Причина отказа висит подсказкой на <b>строке</b>, а не на кнопке:
     * серая кнопка сама ничего не объясняет, а выключенная — тем более
     * не всегда отвечает на наведение.
     */
    private Component stallRow(QuestView.Stall stall) {
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
        name.color(ready ? Look.INK : Look.MUTED);
        row.child(name.horizontalSizing(Sizing.fixed(NAME_WIDTH)));

        row.child(Look.pill(new ItemStack(ModItems.COIN), Coins.spell(stall.price()),
                ready ? Look.INK : Look.MUTED));

        ButtonComponent deal = Look.action(Text.translatable(stall.villageSells()
                ? "villagepax.trade.screen.take" : "villagepax.trade.screen.give"),
                58, button -> trade(stall));
        deal.active(ready);
        row.child(deal);

        stall.ready().reasonKey().ifPresent(key -> row.tooltip(Text.translatable(key)));
        return row;
    }

    /**
     * Требование строкой: предмет, сколько есть, сколько надо. Хватает —
     * зелёным, не хватает — красным: это видно быстрее, чем читается.
     */
    /**
     * Строка награды: значок, человеческое имя и число.
     * <p>
     * Раньше здесь стояла строка, собранная сервером, и в ней был
     * опознаватель предмета: игрок читал {@code villagepax:coin x9}.
     * Теперь вещь доезжает вещью, и имя ей даёт клиент — на своём языке.
     */
    private Component prizeRow(QuestView.Prize prize) {
        FlowLayout row = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);

        if (prize.isTrust()) {
            LabelComponent trust = Components.label(
                    Text.translatable("villagepax.quest.screen.reward_trust",
                            Text.literal("+" + prize.amount())));
            trust.color(Look.GOOD);
            trust.shadow(false);
            row.child(trust);
            return row;
        }

        ItemStack goods = new ItemStack(prize.goods().orElseThrow(), prize.amount());
        ItemComponent picture = Components.item(goods);
        picture.showOverlay(true);
        picture.setTooltipFromStack(true);
        row.child(picture);

        LabelComponent name = Components.label(Text.translatable("villagepax.trade.screen.goods",
                goods.getName(), Text.literal(String.valueOf(prize.amount()))));
        name.color(Look.GOOD);
        name.shadow(false);
        row.child(name);
        return row;
    }

    private Component needRow(QuestView.Need need) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);
        row.verticalAlignment(VerticalAlignment.CENTER);

        ItemComponent picture = Components.item(new ItemStack(need.item()));
        picture.setTooltipFromStack(true);
        row.child(picture);

        LabelComponent line = Components.label(Text.translatable("villagepax.quest.screen.need",
                need.item().getName(), Text.literal(String.valueOf(need.have())),
                Text.literal(String.valueOf(need.need()))));
        line.color(need.enough() ? Look.GOOD : Look.BAD);
        row.child(line);
        return row;
    }

    /**
     * «Дарю то, что в руке».
     * <p>
     * Ни предмета, ни числа не уезжает: руку сервер видит сам. Клиент
     * сообщает только <b>намерение</b> — и это единственное, что он вообще
     * знает наверняка.
     */
    private void gift() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        buf.writeIdentifier(view.giver());
        ClientPlayNetworking.send(QuestNet.GIFT, buf);
    }

    /** «Плачу за мир»: сумму называет сервер, клиент — только намерение. */
    private void peace() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        buf.writeIdentifier(view.giver());
        ClientPlayNetworking.send(QuestNet.PEACE, buf);
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
        // Опознаватель обоза, если торг идёт с ним: у обоза свой товар,
        // и сервер должен знать, из чьей телеги брать.
        buf.writeOptional(view.caravan(), PacketByteBuf::writeUuid);
        ClientPlayNetworking.send(QuestNet.TRADE, buf);
    }
}
