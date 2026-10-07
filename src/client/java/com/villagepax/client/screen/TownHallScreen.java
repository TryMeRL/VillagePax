package com.villagepax.client.screen;

import com.villagepax.client.hologram.Placement;
import com.villagepax.screen.Mood;
import com.villagepax.block.ModBlocks;
import com.villagepax.item.ModItems;
import com.villagepax.screen.PanelMetrics;
import com.villagepax.sim.BuildProgress;
import com.villagepax.screen.TownHallNet;
import com.villagepax.screen.TownHallScreenHandler;
import com.villagepax.screen.TownHallView;
import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
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
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Пульт колонии: то, что игрок видит, щёлкнув по ратуше.
 * <p>
 * Экран ничего не считает и ничего не помнит: всё, что он показывает,
 * приходит снимком с сервера. Поэтому здесь только вёрстка и намерения —
 * ни одной строчки про кровати, еду или материалы.
 * <p>
 * Разметка собирается один раз, а при новом снимке заново наполняется
 * <b>только тело вкладки</b>. Полная пересборка сбрасывала бы прокрутку,
 * и список жителей прыгал бы под курсором каждый раз, когда кто-то
 * проголодался.
 * <p>
 * <b>Почему тело мерится числом.</b> Жалоба игрока: «меню не листается».
 * Причина была в моей вёрстке: {@code Sizing.fill(100)} в owo — это
 * процент <b>всего</b> места контейнера, а не остатка после соседей.
 * Прокрутка стояла в панели рядом с заголовком и вкладками и получала
 * высоту всей панели: содержимое «влезало», листать было нечего, а лишнее
 * рисовалось за краем панели. Теперь высота тела вычитается явно
 * ({@link PanelMetrics#bodyHeight}), и прокрутка знает своё место.
 */
public class TownHallScreen extends BaseOwoHandledScreen<FlowLayout, TownHallScreenHandler> {

    /** Вкладки. Порядок — порядок в заголовке. */
    private enum Tab {
        OVERVIEW("overview"),
        BUILDINGS("buildings"),
        CITIZENS("citizens"),
        STOCK("stock"),
        FAITH("faith");

        private final String id;

        Tab(String id) {
            this.id = id;
        }

        Text title() {
            return Text.translatable("villagepax.screen.tab." + id);
        }

        ItemStack icon() {
            return new ItemStack(switch (this) {
                case OVERVIEW -> Items.WRITABLE_BOOK;
                case BUILDINGS -> Items.BRICKS;
                case CITIZENS -> Items.VILLAGER_SPAWN_EGG;
                case STOCK -> Items.CHEST;
                case FAITH -> Items.CANDLE;
            });
        }
    }

    /** Окно во весь экран: вывеска, вкладки слева, карточки колонками. */
    private Frame frame;

    private Tab tab = Tab.OVERVIEW;

    public TownHallScreen(TownHallScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        frame = Frame.build(root, width, height, true);
        fillHead();
        fillTabs();
        fillBody();
        frame.hint(Text.translatable("villagepax.screen.footer.town_hall"));
    }

    /** Новый размер окна игры — собрать заново под него. */
    @Override
    public void resize(MinecraftClient client, int width, int height) {
        double kept = frame == null ? 0 : frame.scroll.where();
        this.uiAdapter = null;
        super.resize(client, width, height);
        if (frame != null) {
            frame.scroll.restore(kept);
        }
    }

    /** Открыть сразу на вкладке по её номеру: для снимков экрана. */
    public void showTab(int index) {
        tab = Tab.values()[Math.floorMod(index, Tab.values().length)];
    }

    private TownHallView view() {
        return getScreenHandler().view();
    }

    /** Ширина текста в карточке колонки. */
    private int text() {
        return frame.textWidth;
    }

    /** Ширина подписи слева от числа. */
    private int caption() {
        return Math.max(90, frame.textWidth / 2);
    }

    /** Карточку — в колонку; вес — число её строк. */
    private void put(Component card) {
        frame.place(card, card instanceof FlowLayout flow ? flow.children().size() : 1);
    }

    /**
     * Новый снимок с сервера: заголовок, вкладки и тело заново.
     * <p>
     * И <b>ровно на том же месте</b>. Снимок приходит дважды в секунду,
     * а в живой колонии он почти всегда другой: кто-то поработал, кто-то
     * поел, стройка сдвинулась на блок. Пересобранное тело уводило
     * прокрутку в начало, и список длиннее экрана становился нечитаемым —
     * до нижней строки было не дожить. Заказчик сказал прямо: «нельзя
     * нормально использовать меню».
     */
    public void refresh(TownHallView fresh) {
        if (fresh.equals(view())) {
            return;
        }
        getScreenHandler().acceptView(fresh);
        if (frame != null) {
            double kept = frame.scroll.where();
            fillHead();
            fillTabs();
            fillBody();
            frame.scroll.restore(kept);
        }
    }

    /**
     * Вывеска: имя колонии и ступень слева, главные числа справа.
     * <p>
     * Числа пилюлями: жители, еда, монета и стройка видны, не читая.
     */
    private void fillHead() {
        frame.headerLeft.clearChildren();
        frame.headerRight.clearChildren();
        TownHallView view = view();

        frame.title(Text.literal(view.name()));
        frame.headerLeft.child(Look.pill(Text.translatable("villagepax.level." + view.level()),
                Look.LIGHT));
        frame.headerLeft.child(Look.pill(
                Text.translatable("villagepax.culture." + view.culture().getPath()), Look.LIGHT));

        frame.headerRight.child(Look.pill(new ItemStack(Items.VILLAGER_SPAWN_EGG),
                Text.literal(view.population() + "/" + view.maxCitizens()),
                view.population() < view.maxCitizens() ? Look.LIGHT : Look.GOLD));
        frame.headerRight.child(Look.pill(new ItemStack(Items.RED_BED),
                Text.literal(String.valueOf(view.freeBeds())),
                view.freeBeds() > 0 ? Look.LIGHT : Look.BAD));
        frame.headerRight.child(Look.pill(new ItemStack(Items.BREAD),
                Text.literal(String.valueOf(view.meals())),
                view.meals() > 0 ? Look.LIGHT : Look.BAD));
        frame.headerRight.child(Look.pill(new ItemStack(ModItems.SILVER_COIN),
                Text.literal(String.valueOf(view.coins())), Look.LIGHT));
        frame.closeButton(button -> close());
    }

    private void fillTabs() {
        frame.rail.clearChildren();
        for (Tab candidate : Tab.values()) {
            frame.tab(candidate.icon(), candidate.title(), candidate == tab, () -> {
                tab = candidate;
                fillTabs();
                fillBody();
                // Новая вкладка начинается сверху, а не с места прошлой.
                frame.scroll.restore(0);
            });
        }
    }

    private void fillBody() {
        frame.clear();
        TownHallView view = view();

        switch (tab) {
            case OVERVIEW -> overview(view);
            case BUILDINGS -> buildings(view);
            case CITIZENS -> citizens(view);
            case STOCK -> stock(view);
            case FAITH -> faith(view);
        }
    }

    // --- вкладки ---

    /**
     * Карточка роста: где колония сейчас, что дальше и что это откроет.
     * <p>
     * Стоит сразу за советом, до чисел. Совет отвечает «что делать
     * сегодня», рост — «ради чего вообще всё это»; без второго колония
     * работает как машина, и это ровно то, на что жаловался заказчик.
     */
    private void fillGrowth(TownHallView view) {
        TownHallView.Growth growth = view.growth();
        FlowLayout card = Look.card("villagepax.screen.overview.section_growth",
                new ItemStack(Items.GOLDEN_APPLE));

        card.child(Look.stat(Text.translatable("villagepax.screen.growth.level"),
                Text.translatable(growth.level()), caption()));

        if (growth.next().isEmpty()) {
            LabelComponent top = Components.label(
                    Text.translatable("villagepax.screen.growth.top"));
            top.color(Look.MUTED);
            top.lineHeight(10);
            card.child(top.horizontalSizing(Sizing.fixed(text())));
            put(card);
            return;
        }

        card.child(Look.stat(Text.translatable("villagepax.screen.growth.next"),
                Text.translatable(growth.next().get()), caption()));

        if (!growth.reachable()) {
            // Обещать ступень, до которой нет ратуши, — хуже, чем молчать.
            LabelComponent later = Components.label(
                    Text.translatable("villagepax.screen.growth.not_yet"));
            later.color(Look.MUTED);
            later.lineHeight(10);
            card.child(later.horizontalSizing(Sizing.fixed(text())));
            put(card);
            return;
        }

        LabelComponent how = Components.label(Text.translatable("villagepax.screen.growth.how",
                Text.literal(String.valueOf(growth.needsHall()))));
        how.color(Look.INK);
        how.lineHeight(10);
        card.child(how.horizontalSizing(Sizing.fixed(text())));

        if (growth.opens().isEmpty()) {
            LabelComponent nothing = Components.label(
                    Text.translatable("villagepax.screen.growth.nothing"));
            nothing.color(Look.MUTED);
            nothing.lineHeight(10);
            card.child(nothing.horizontalSizing(Sizing.fixed(text())));
        } else {
            for (String key : growth.opens()) {
                LabelComponent line = Components.label(
                        Text.literal("• ").append(Text.translatable(key)));
                line.color(Look.GOOD);
                line.lineHeight(10);
                card.child(line.horizontalSizing(Sizing.fixed(text())));
            }
        }
        put(card);
    }

    /**
     * Вкладка «Обзор»: совет, ярмо, рост, затем числа колонии и стройка.
     * <p>
     * Порядок — от «что делать» к «как обстоят дела»: игрок, открывший
     * пульт на минуту, должен уйти с ответом, а не с таблицей.
     */
    private void overview(TownHallView view) {
        // Совет — самой первой строкой, до всех чисел. Числа правдивы,
        // но ни одно из них не говорит, что делать дальше, а это и есть
        // единственный вопрос новичка.
        view.advice().ifPresent(key -> {
            FlowLayout hint = Look.card("villagepax.screen.overview.section_advice",
                    new ItemStack(Items.WRITABLE_BOOK));
            LabelComponent line = Components.label(Text.translatable(key));
            line.color(Look.GOLD);
            line.shadow(false);
            line.lineHeight(9);
            hint.child(line.horizontalSizing(Sizing.fixed(text())));
            put(hint);
        });

        // Ярмо — сразу за советом и красным: это состояние, а не событие,
        // и «почему у меня каждое утро пропадает серебро» обязано иметь
        // ответ там же, где игрок смотрит всё остальное.
        if (view.yoke().paying()) {
            FlowLayout yoke = Look.card("villagepax.screen.overview.section_yoke",
                    new ItemStack(Items.IRON_SWORD));
            LabelComponent line = Components.label(
                    Text.translatable("villagepax.screen.overview.yoke_line",
                            Text.literal(view.yoke().lord()),
                            number(view.yoke().days())));
            line.color(Look.BAD);
            line.shadow(false);
            line.lineHeight(9);
            yoke.child(line.horizontalSizing(Sizing.fixed(text())));
            put(yoke);
        }

        if (!view.extras().news().isEmpty()) {
            FlowLayout news = Look.card("villagepax.screen.overview.section_news",
                    new ItemStack(Items.BELL));
            for (String json : view.extras().news()) {
                news.child(Look.hint(parse(json), text()));
            }
            put(news);
        }

        fillGrowth(view);

        FlowLayout colony = Look.card("villagepax.screen.overview.section_colony",
                new ItemStack(ModBlocks.TOWN_HALL));
        colony.child(Look.stat(new ItemStack(Items.WHEAT),
                Text.translatable("villagepax.screen.overview.culture_name"),
                Text.translatable("villagepax.culture." + view.culture().getPath()),
                caption(), Look.INK));
        colony.child(Look.stat(new ItemStack(Items.RED_BED),
                Text.translatable("villagepax.screen.overview.beds_name"),
                Text.translatable("villagepax.screen.overview.beds_value",
                        number(view.beds()), number(view.freeBeds())),
                caption(), view.freeBeds() > 0 ? Look.INK : Look.BAD));
        colony.child(Look.stat(new ItemStack(Items.BREAD),
                Text.translatable("villagepax.screen.overview.food_name"),
                number(view.meals()), caption(),
                view.meals() > 0 ? Look.INK : Look.BAD));

        if (view.daysOfFood() > 0) {
            colony.child(Look.stat(new ItemStack(Items.CLOCK),
                    Text.translatable("villagepax.screen.overview.days_name"),
                    number(view.daysOfFood()), caption(),
                    view.daysOfFood() > 1 ? Look.INK : Look.BAD));
        } else {
            LabelComponent hungry = Components.label(
                    Text.translatable("villagepax.screen.overview.days_none"));
            hungry.color(Look.BAD);
            colony.child(hungry);
        }
        colony.child(Look.stat(new ItemStack(Items.CHEST),
                Text.translatable("villagepax.screen.overview.containers_name"),
                number(view.containers()), caption(),
                view.containers() > 0 ? Look.INK : Look.BAD));


        // Пустая колония — не руина: об этом надо сказать прямо, иначе
        // игрок будет сидеть над недостроенным домом и не понимать,
        // почему никто не строит.
        if (view.population() == 0) {
            LabelComponent deserted = Components.label(
                    Text.translatable("villagepax.screen.overview.deserted"));
            deserted.color(Look.BAD);
            deserted.shadow(false);
            deserted.lineHeight(9);
            colony.child(deserted.horizontalSizing(Sizing.fixed(text())));
        }
        put(colony);

        // Казна — своей карточкой, а не строкой в быту: у неё есть кнопки,
        // а карточка с кнопками посреди чисел читается как ошибка вёрстки.
        // Появляется она вместе с деньгами: у хутора денег нет, и пустая
        // карточка «жалование 0» только сбивала бы с толку.
        if (view.wages() > 0) {
            FlowLayout purse = Look.card("villagepax.screen.overview.section_purse",
                    new ItemStack(ModItems.SILVER_COIN));
            purse.child(Look.stat(new ItemStack(ModItems.SILVER_COIN),
                    Text.translatable("villagepax.screen.overview.treasury_name"),
                    number(view.coins()), caption(),
                    view.coins() >= view.wages() ? Look.INK : Look.BAD));
            purse.child(Look.stat(new ItemStack(Items.CLOCK),
                    Text.translatable("villagepax.screen.overview.wages_name"),
                    number(view.wages()), caption(),
                    view.coins() >= view.wages() ? Look.INK : Look.BAD));

            // Рынок назван прямо, а не оставлен догадке: без него ставка
            // не возвращает ни медяка, и игрок, покрутив её впустую,
            // решит, что налог не работает.
            purse.child(Look.stat(new ItemStack(Items.EMERALD),
                    Text.translatable("villagepax.screen.overview.market_name"),
                    Text.translatable(view.hasMarket()
                            ? "villagepax.screen.overview.market_yes"
                            : "villagepax.screen.overview.market_no"),
                    caption(), view.hasMarket() ? Look.INK : Look.BAD));

            FlowLayout rate = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            rate.verticalAlignment(VerticalAlignment.CENTER);
            rate.gap(4);
            rate.child(Look.stat(Text.translatable("villagepax.screen.overview.tax_name"),
                    Text.translatable("villagepax.screen.overview.tax_value",
                            number(view.taxRate())), caption()));
            rate.child(Look.action(Text.literal("−"), 18, button -> tax(-25)));
            rate.child(Look.action(Text.literal("+"), 18, button -> tax(25)));
            purse.child(rate);
            put(purse);
        }

        FlowLayout story = Look.card("villagepax.screen.overview.section_chronicle",
                new ItemStack(Items.WRITTEN_BOOK));
        if (view.extras().chronicle().isEmpty()) {
            story.child(Look.hint(Text.translatable("villagepax.chronicle.empty"), text()));
        }
        for (String json : view.extras().chronicle()) {
            story.child(Look.hint(parse(json), text()));
        }
        story.child(muted("villagepax.screen.overview.chronicle_more"));
        put(story);

        TownHallView.Construction construction = view.construction().orElse(null);
        if (construction == null) {
            FlowLayout idle = Look.card("villagepax.screen.overview.section_build",
                    new ItemStack(Items.IRON_PICKAXE));
            idle.child(Look.nothing(Text.translatable("villagepax.screen.overview.idle"),
                    text()));
            idle.child(Look.hint(Text.translatable("villagepax.screen.overview.idle_hint"),
                    text()));
            put(idle);
            return;
        }

        FlowLayout site = Look.card("villagepax.screen.overview.section_build",
                new ItemStack(Items.IRON_PICKAXE));
        LabelComponent what = Components.label(building(construction.type()));
        what.color(Look.INK);
        site.child(what);
        site.child(Look.stat(Text.translatable("villagepax.screen.overview.step_name"),
                Text.translatable("villagepax.screen.overview.step_value",
                        number(construction.step()), number(construction.steps())), caption()));
        site.child(Look.bar(construction.step(), construction.steps(),
                text()));

        if (construction.missing().isEmpty()) {
            LabelComponent enough = Components.label(
                    Text.translatable("villagepax.screen.overview.missing_none"));
            enough.color(Look.GOOD);
            site.child(enough);
        } else {
            LabelComponent lacking = Components.label(
                    Text.translatable("villagepax.screen.overview.missing"));
            lacking.color(Look.BAD);
            site.child(lacking);
            for (Map.Entry<Identifier, Integer> lack : construction.missing().contents().entrySet()) {
                site.child(Look.itemRow(stackOf(lack.getKey()), lack.getValue()));
            }
        }
        put(site);
    }

    private void buildings(TownHallView view) {
        if (view.buildings().isEmpty()) {
            put(muted("villagepax.screen.buildings.none"));
        }

        for (TownHallView.BuildingLine line : view.buildings()) {
            FlowLayout card = Look.card(null);

            FlowLayout title = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            title.verticalAlignment(VerticalAlignment.CENTER);
            title.gap(4);

            LabelComponent what = Components.label(
                    Text.translatable("villagepax.screen.buildings.line_name",
                            building(line.type()), number(line.level())));
            what.color(Look.INK);
            what.shadow(false);
            ItemComponent icon = Components.item(Look.buildingIcon(line.type()));
            icon.sizing(Sizing.fixed(12));
            title.child(icon);
            title.child(what.horizontalSizing(Sizing.fixed(text() - 100)));
            title.child(Look.pill(Text.translatable("villagepax.progress." + line.progress().id()),
                    line.progress() == BuildProgress.DONE ? Look.GOOD : Look.GOLD));
            card.child(title);
            card.child(Look.stat(Text.translatable("villagepax.screen.buildings.where"),
                    whereIs(line.anchor()), caption()));

            FlowLayout controls = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            controls.verticalAlignment(VerticalAlignment.CENTER);
            controls.gap(3);
            if (line.canUpgrade()) {
                controls.child(Look.action(
                        Text.translatable("villagepax.screen.buildings.upgrade"), 76,
                        button -> upgrade(line.id())));
            }
            // Очередь двигается только у стройки: у готового здания
            // двигать нечего, и кнопки там были бы обманом.
            if (line.progress() != BuildProgress.DONE) {
                controls.child(Look.action(Text.literal("▲"), 18,
                        button -> reorder(line.id(), 1)));
                controls.child(Look.action(Text.literal("▼"), 18,
                        button -> reorder(line.id(), -1)));
            }
            if (!controls.children().isEmpty()) {
                card.child(controls);
            }
            put(card);
        }

        FlowLayout offers = Look.card("villagepax.screen.buildings.offers");
        offers.child(muted("villagepax.screen.buildings.order_hint"));
        for (Identifier schematic : view.offers()) {
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.gap(4);

            LabelComponent what = Components.label(building(typeOf(schematic)));
            what.color(Look.INK);
            row.child(what.horizontalSizing(Sizing.fixed(text() - 84)));
            row.child(Look.action(Text.translatable("villagepax.screen.buildings.order"), 76,
                    button -> order(schematic)));
            offers.child(row);
        }
        put(offers);
    }

    /**
     * Чем этот житель работает — значком.
     * <p>
     * Предмет выбирается кодом, а не данными, и это осознанно: профессий
     * в датапаке может быть сколько угодно, но значок — это <b>вид</b>,
     * и у незнакомого ремесла он всё равно будет общим. Общий — хлебная
     * корка: человек, который просто живёт.
     */
    /**
     * Значок ремесла — то, что житель этого ремесла держит в руках
     * или делает: игрок узнаёт его в списке так же, как на улице.
     * Без ремесла — хлеб: ест, а не работает.
     */
    /** Где стоит: «x, z» и сколько до него шагов от игрока. */
    private static Text whereIs(net.minecraft.util.math.BlockPos anchor) {
        MinecraftClient client = MinecraftClient.getInstance();
        Text place = Text.literal(anchor.getX() + ", " + anchor.getZ());
        if (client.player == null) {
            return place;
        }
        int away = (int) Math.sqrt(client.player.getBlockPos().getSquaredDistance(anchor));
        return Text.translatable("villagepax.screen.buildings.where_value", place, away);
    }

    private void citizens(TownHallView view) {
        if (view.citizens().isEmpty()) {
            put(muted("villagepax.screen.citizens.none"));
        }

        for (TownHallView.CitizenLine citizen : view.citizens()) {
            FlowLayout card = Look.card(null);

            FlowLayout title = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            title.verticalAlignment(VerticalAlignment.CENTER);
            title.gap(4);

            // Значок ремесла: топор у лесоруба, меч у стражи, кайло
            // у строителя. Ремесло видно раньше, чем прочитано имя, —
            // а в колонии на четырнадцать человек именно ремесло и ищут.
            ItemComponent craft = Components.item(Look.professionIcon(citizen.profession()));
            craft.sizing(Sizing.fixed(12));
            craft.tooltip(professionName(view, citizen.profession()));
            title.child(craft);

            LabelComponent name = Components.label(Text.literal(citizen.name()));
            name.color(Look.INK);
            name.shadow(false);
            title.child(name.horizontalSizing(Sizing.fixed(text() - 92)));
            title.child(Look.pill(Text.translatable(citizen.mood().translationKey()),
                    colorOf(citizen)));
            card.child(title);
            card.child(Look.rule());

            // Возраст строкой, а не значком: «Ребёнок, 3 дня» игрок
            // прочитает и поймёт, а маленькая иконка требует объяснения,
            // которому в интерфейсе места нет.
            //
            // Дням у тех, кто пришёл взрослым, взяться неоткуда: они
            // жили в мире до того, как мод стал считать возраст, и врать
            // им число было бы хуже, чем не называть его вовсе.
            card.child(Look.stat(Text.translatable("villagepax.screen.citizens.age"),
                    citizen.days() < 0
                            ? Text.translatable(citizen.stage())
                            : Text.translatable("villagepax.screen.citizens.age_value",
                                    Text.translatable(citizen.stage()),
                                    number(citizen.days())),
                    caption()));

            if (!citizen.kin().isBlank()) {
                card.child(Look.stat(Text.translatable("villagepax.screen.citizens.kin"),
                        Text.literal(citizen.kin()), caption()));
            }
            if (!citizen.friends().isBlank()) {
                card.child(Look.stat(Text.translatable("villagepax.screen.citizens.friends"),
                        Text.literal(citizen.friends()), caption()));
            }
            // Недруги красным: это единственная строка карточки, по которой
            // игроку есть что делать прямо сейчас — развести их по разным
            // мастерским.
            if (!citizen.foes().isBlank()) {
                Component foes = Look.stat(
                        Text.translatable("villagepax.screen.citizens.foes"),
                        Text.literal(citizen.foes()), caption());
                foes.tooltip(Text.translatable("villagepax.screen.citizens.foes_hint"));
                card.child(foes);
            }

            // Характер строкой, а подсказкой к ней — что он меняет. Одно
            // название игроку не говорит ничего: «честолюбивый» — это
            // похвала или беда? Характер, о действии которого негде
            // прочесть, принимают за украшение один раз и навсегда.
            Component nature = Look.stat(
                    Text.translatable("villagepax.screen.citizens.nature"),
                    Text.translatable("villagepax.nature." + citizen.nature()), caption());
            nature.tooltip(Text.translatable("villagepax.nature." + citizen.nature() + ".what"));
            card.child(nature);

            List<Text> troubles = new ArrayList<>();
            if (!citizen.housed()) {
                troubles.add(Text.translatable("villagepax.screen.citizens.homeless"));
            }
            if (citizen.workplace().isEmpty()) {
                troubles.add(Text.translatable("villagepax.screen.citizens.no_workplace"));
            }
            if (citizen.leavingSoon()) {
                troubles.add(Text.translatable("villagepax.screen.citizens.leaving"));
            }
            if (!troubles.isEmpty()) {
                LabelComponent line = Components.label(join(troubles));
                line.color(Look.BAD);
                card.child(line);
            }

            FlowLayout controls = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            controls.verticalAlignment(VerticalAlignment.CENTER);
            controls.gap(4);
            controls.child(Look.action(professionName(view, citizen.profession()), 120,
                    button -> assign(citizen.id(), nextProfession(view, citizen.profession()))));
            controls.child(muted("villagepax.screen.citizens.change"));
            card.child(controls);

            put(card);
        }
    }

    /** Разделы склада: по тому, на что вещь идёт. */
    private enum Shelf {
        FOOD("food", Items.BREAD),
        BUILDING("building", Items.BRICKS),
        VALUABLES("valuables", Items.GOLD_INGOT),
        OTHER("other", Items.CHEST);

        final String id;
        final net.minecraft.item.Item icon;

        Shelf(String id, net.minecraft.item.Item icon) {
            this.id = id;
            this.icon = icon;
        }

        static Shelf of(net.minecraft.item.Item item) {
            if (item.isFood()) {
                return FOOD;
            }
            String path = Registries.ITEM.getId(item).getPath();
            if (path.contains("coin") || path.endsWith("_ingot") || path.contains("emerald")
                    || path.contains("diamond") || path.contains("gold")) {
                return VALUABLES;
            }
            return item instanceof net.minecraft.item.BlockItem ? BUILDING : OTHER;
        }
    }

    /**
     * Склад — ячейками, как сундук, и по полкам: еда, стройка, ценное,
     * прочее. Глаз ищет вещь по картинке, а не по строчке; число — в углу
     * ячейки, имя и счёт — в подсказке. Сверху — чего не хватает стройке:
     * это то, за чем игрок вообще открывает склад.
     */
    private void stock(TownHallView view) {
        view.construction().filter(site -> !site.missing().isEmpty()).ifPresent(site -> {
            FlowLayout lacking = Look.card("villagepax.screen.stock.section_missing",
                    new ItemStack(Items.IRON_PICKAXE));
            lacking.child(Look.hint(Text.translatable("villagepax.screen.stock.missing_for",
                    building(site.type())), frame.bodyWidth - 14));
            lacking.child(slots(site.missing().contents(), frame.bodyWidth - 14));
            frame.wide(lacking);
        });

        if (view.stock().isEmpty()) {
            FlowLayout card = Look.card("villagepax.screen.stock.section", new ItemStack(Items.CHEST));
            card.child(muted("villagepax.screen.stock.empty"));
            card.child(Look.hint(Text.translatable("villagepax.screen.stock.hint"), frame.bodyWidth - 14));
            frame.wide(card);
            return;
        }
        Map<Shelf, Map<Identifier, Integer>> shelves = new java.util.EnumMap<>(Shelf.class);
        int total = 0;
        for (Map.Entry<Identifier, Integer> entry : view.stock().contents().entrySet()) {
            Shelf shelf = Shelf.of(Registries.ITEM.get(entry.getKey()));
            shelves.computeIfAbsent(shelf, any -> new java.util.LinkedHashMap<>())
                    .put(entry.getKey(), entry.getValue());
            total += entry.getValue();
        }
        for (Map.Entry<Shelf, Map<Identifier, Integer>> shelf : shelves.entrySet()) {
            FlowLayout card = Look.card("villagepax.screen.stock.shelf." + shelf.getKey().id,
                    new ItemStack(shelf.getKey().icon));
            card.child(slots(shelf.getValue(), text()));
            int sum = shelf.getValue().values().stream().mapToInt(Integer::intValue).sum();
            card.child(Look.stat(Text.translatable("villagepax.screen.stock.total"), number(sum),
                    caption()));
            frame.place(card, 2 + shelf.getValue().size() / Math.max(1, text() / 20));
        }
        FlowLayout summary = Look.card("villagepax.screen.stock.everything", new ItemStack(Items.CHEST));
        summary.child(Look.hint(Text.translatable("villagepax.screen.stock.everything_value", number(total),
                number(view.containers())), text()));
        summary.child(Look.hint(Text.translatable("villagepax.screen.stock.hint"), text()));
        frame.place(summary, 2);
    }

    /** Ячейки рядами по ширине. */
    private static Component slots(Map<Identifier, Integer> items, int width) {
        FlowLayout grid = Containers.verticalFlow(Sizing.content(), Sizing.content());
        grid.gap(2);
        int perRow = Math.max(1, width / 20);
        FlowLayout row = null;
        int inRow = 0;
        for (Map.Entry<Identifier, Integer> entry : items.entrySet()) {
            if (row == null || inRow == perRow) {
                row = Containers.horizontalFlow(Sizing.content(), Sizing.content());
                row.gap(2);
                grid.child(row);
                inRow = 0;
            }
            row.child(Look.slot(stackOf(entry.getKey()), entry.getValue()));
            inRow++;
        }
        return grid;
    }

    private void faith(TownHallView view) {
        TownHallView.FaithView faith = view.faith();

        if (faith.gods().isEmpty()) {
            put(muted("villagepax.screen.faith.no_pantheon"));
            return;
        }

        if (!faith.temple()) {
            FlowLayout hint = Look.card("villagepax.screen.faith.section_temple");
            hint.child(Look.hint(Text.translatable("villagepax.screen.faith.needs_temple"),
                    text()));
            put(hint);
        }

        for (TownHallView.GodLine god : faith.gods()) {
            FlowLayout card = Look.card(null);

            FlowLayout title = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            title.verticalAlignment(VerticalAlignment.CENTER);
            title.gap(4);

            LabelComponent name = Components.label(Text.translatable(god.displayName()));
            name.color(Look.INK);
            name.shadow(false);
            title.child(name.horizontalSizing(Sizing.fixed(text() - 84)));
            title.child(Look.pill(Text.translatable(god.tier()),
                    god.alwaysOn() ? Look.GOOD : Look.GOLD));
            card.child(title);

            card.child(Look.stat(Text.translatable("villagepax.screen.faith.domain"),
                    Text.translatable("villagepax.faith.domain." + god.domain()), caption()));

            // Полоса до следующей ступени. У высшей полосы нет: расти
            // больше некуда, и рисовать пустой жёлоб значило бы обещать
            // ступень, которой не существует.
            if (god.nextAt() > 0) {
                card.child(Look.stat(Text.translatable("villagepax.screen.faith.favour"),
                        Text.translatable("villagepax.screen.faith.favour_value",
                                number(god.favour()), number(god.nextAt())), caption()));
                card.child(Look.bar(Math.min(god.favour(), god.nextAt()), god.nextAt(),
                        text()));
            } else {
                card.child(Look.stat(Text.translatable("villagepax.screen.faith.favour"),
                        Text.translatable("villagepax.screen.faith.favour_top",
                                number(god.favour())), caption()));
            }

            Text blessing;
            if (god.alwaysOn()) {
                blessing = Text.translatable("villagepax.screen.faith.blessing_always");
            } else if (god.blessedDays() > 0) {
                blessing = Text.translatable("villagepax.screen.faith.blessing_days",
                        number(god.blessedDays()));
            } else {
                blessing = Text.translatable("villagepax.screen.faith.blessing_none");
            }
            card.child(Look.stat(Text.translatable("villagepax.screen.faith.blessing"),
                    blessing, caption()));

            FlowLayout controls = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            controls.verticalAlignment(VerticalAlignment.CENTER);
            controls.gap(3);
            controls.child(Look.action(Text.translatable("villagepax.screen.faith.bless"), 96,
                    button -> bless(god.domain())));
            controls.child(Look.action(Text.translatable("villagepax.screen.faith.miracle"), 96,
                    button -> miracle(god.domain())));
            card.child(controls);

            put(card);
        }
    }

    // --- намерения ---

    /** Просьба о благословении. Ответ приходит в чат — и приходит всегда. */
    private void bless(String domain) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(domain);
        ClientPlayNetworking.send(TownHallNet.BLESS, buf);
    }

    private void miracle(String domain) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(domain);
        ClientPlayNetworking.send(TownHallNet.MIRACLE, buf);
    }


    /**
     * Выбор здания включает режим установки, а экран закрывается: место
     * игрок выбирает в мире голограммой, а не в меню.
     */
    private void order(Identifier schematic) {
        Placement.begin(schematic);
        close();
    }

    /** Ставка двигается шагом: правило предела живёт на сервере. */
    private void tax(int shift) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(shift);
        ClientPlayNetworking.send(TownHallNet.TAX, buf);
    }

    /** Подвинуть стройку в очереди: вверх — раньше, вниз — позже. */
    private void reorder(UUID building, int shift) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(building);
        buf.writeInt(shift);
        ClientPlayNetworking.send(TownHallNet.PRIORITY, buf);
    }

    /**
     * Улучшение заказывается кнопкой, а не голограммой: место уже выбрано,
     * здание растёт от своего угла. Экран остаётся открытым — по нему сразу
     * видно, что стройка началась.
     */
    private void upgrade(UUID building) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(building);
        ClientPlayNetworking.send(TownHallNet.UPGRADE, buf);
    }

    private void assign(UUID citizen, Optional<Identifier> profession) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(citizen);
        buf.writeOptional(profession, PacketByteBuf::writeIdentifier);
        ClientPlayNetworking.send(TownHallNet.ASSIGN, buf);
    }

    /**
     * Следующее дело по кругу: профессии из снимка, потом «без дела».
     * <p>
     * Круг, а не выпадающий список: список у owo открывается через
     * контекстное меню с экранными координатами, а проверить вёрстку
     * игровым тестом нельзя — у него нет клиента. Кнопка по кругу
     * работает наверняка, и её всегда можно заменить, когда список
     * будет виден живьём.
     */
    private static Optional<Identifier> nextProfession(TownHallView view,
                                                       Optional<Identifier> current) {
        List<TownHallView.ProfessionLine> known = view.professions();
        if (known.isEmpty()) {
            return Optional.empty();
        }
        // Запертые ступенью пропускаются: кнопка, которая переключает
        // на ремесло и тут же получает отказ, — это сломанная кнопка.
        // Видно их всё равно — в карточке роста, как цель.
        List<TownHallView.ProfessionLine> open = known.stream()
                .filter(line -> !line.locked())
                .toList();
        if (open.isEmpty()) {
            return Optional.empty();
        }
        if (current.isEmpty()) {
            return Optional.of(open.get(0).id());
        }

        for (int index = 0; index < open.size(); index++) {
            if (open.get(index).id().equals(current.get())) {
                // За последней профессией — «без дела», и круг замыкается.
                return index + 1 < open.size()
                        ? Optional.of(open.get(index + 1).id())
                        : Optional.empty();
            }
        }
        return Optional.of(open.get(0).id());
    }

    // --- мелочи вёрстки ---

    /** Строка, приехавшая с сервера готовым текстом. */
    private static Text parse(String json) {
        Text parsed = Text.Serializer.fromJson(json);
        return parsed == null ? Text.empty() : parsed;
    }

    private static Component muted(String key) {
        LabelComponent label = Components.label(Text.translatable(key));
        label.color(Look.MUTED);
        return label;
    }

    private static ItemStack stackOf(Identifier item) {
        return new ItemStack(Registries.ITEM.get(item));
    }

    private static Text professionName(TownHallView view, Optional<Identifier> profession) {
        if (profession.isEmpty()) {
            return Text.translatable("villagepax.profession.none");
        }
        for (TownHallView.ProfessionLine known : view.professions()) {
            if (known.id().equals(profession.get())) {
                return Text.translatable(known.displayName());
            }
        }
        return Text.literal(profession.get().getPath());
    }

    private static Text building(Identifier type) {
        return Text.translatable(TownHallNet.buildingKey(type));
    }

    /** Схема {@code norman/farm_lvl1} — это здание {@code norman/farm}. */
    private static Identifier typeOf(Identifier schematic) {
        String path = schematic.getPath();
        int marker = path.lastIndexOf("_lvl");
        return marker < 0 ? schematic
                : new Identifier(schematic.getNamespace(), path.substring(0, marker));
    }

    private static Color colorOf(TownHallView.CitizenLine citizen) {
        if (citizen.leavingSoon() || citizen.mood() == Mood.STARVING) {
            return Look.BAD;
        }
        // Между «доволен» и «беда» — тёмный янтарь: светло-жёлтый на
        // пергаменте не читался.
        return citizen.mood() == Mood.CONTENT ? Look.GOOD : Look.GOLD;
    }

    private static Text number(int value) {
        return Text.literal(String.valueOf(value));
    }

    private static Text join(List<Text> parts) {
        Text joined = Text.empty();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                joined = joined.copy().append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
            }
            joined = joined.copy().append(parts.get(index));
        }
        return joined;
    }

    /** Пульт открыт у этого игрока — если открыт вообще. */
    public static Optional<TownHallScreen> open(MinecraftClient client) {
        return client.currentScreen instanceof TownHallScreen screen
                ? Optional.of(screen)
                : Optional.empty();
    }
}
