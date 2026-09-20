package com.villagepax.client.screen;

import com.villagepax.client.hologram.Placement;
import com.villagepax.screen.Mood;
import com.villagepax.block.ModBlocks;
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
 * ({@link Look#bodyHeight}), и прокрутка знает своё место.
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
    }

    // Размеры лежат в общем коде: их проверяет модульный тест, потому
    // что «меню не листается» было ошибкой арифметики, а не рисования.
    private static final int PANEL_WIDTH = PanelMetrics.TOWN_HALL_WIDTH;
    private static final int PANEL_HEIGHT = PanelMetrics.TOWN_HALL_HEIGHT;
    private static final int PADDING = PanelMetrics.PADDING;
    private static final int GAP = PanelMetrics.GAP;

    private static final int HEADER_HEIGHT = PanelMetrics.HEADER;
    private static final int TABS_HEIGHT = PanelMetrics.TABS;

    private static final int BODY_HEIGHT =
            PanelMetrics.bodyHeight(PANEL_HEIGHT, HEADER_HEIGHT, TABS_HEIGHT);

    /** Ширина подписи в строках «подпись — значение». */
    /** Ширина текста внутри карточки: панель без отступов и ползунка. */
    private static final int TEXT_WIDTH = PanelMetrics.TOWN_HALL_WIDTH - 2 * PanelMetrics.PADDING - 26;

    private static final int CAPTION = 150;

    private Tab tab = Tab.OVERVIEW;
    private FlowLayout head;
    private FlowLayout body;
    private FlowLayout tabs;
    private KeptScroll<FlowLayout> scroll;

    public TownHallScreen(TownHallScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
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

        // Высота — числом, ширина — с запасом под ползунок, чтобы он
        // не наезжал на строки.
        scroll = new KeptScroll<>(Sizing.fill(100), Sizing.fixed(BODY_HEIGHT), body);
        scroll.scrollbarThiccness(4);
        scroll.padding(Insets.right(6));
        panel.child(scroll);

        root.child(panel);

        fillHead();
        fillTabs();
        fillBody();
    }

    private TownHallView view() {
        return getScreenHandler().view();
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
        if (body != null) {
            double kept = scroll == null ? 0 : scroll.where();
            fillHead();
            fillTabs();
            fillBody();
            if (scroll != null) {
                scroll.restore(kept);
            }
        }
    }

    /**
     * Заголовок: имя колонии, уровень и население — одной строкой.
     * <p>
     * Одной строкой намеренно: это то, что игрок хочет знать, не читая.
     * Уровень и население раньше лежали в списке наравне с числом
     * сундуков, и найти их глазами было не быстрее, чем прочитать всё.
     * Теперь они пилюлями: число на подложке видно, не разбирая строку.
     */
    private void fillHead() {
        head.clearChildren();
        TownHallView view = view();

        // Имя колонии — на доске: окно начинается вывеской, а не строкой.
        FlowLayout row = Look.board(HEADER_HEIGHT - 8);

        LabelComponent name = Components.label(Text.literal(view.name()));
        name.color(Look.LIGHT);
        name.shadow(true);
        row.child(name);

        row.child(Look.pill(Text.translatable("villagepax.settlement.level." + view.level()),
                Look.LIGHT));
        row.child(Look.pill(new ItemStack(Items.RED_BED),
                Text.literal(view.population() + "/" + view.maxCitizens()),
                view.freeBeds() > 0 ? Look.LIGHT : Look.BAD));
        row.child(Look.pill(new ItemStack(Items.BREAD),
                Text.literal(String.valueOf(view.meals())),
                view.meals() > 0 ? Look.LIGHT : Look.BAD));

        head.child(row);
    }

    private void fillTabs() {
        tabs.clearChildren();
        for (Tab candidate : Tab.values()) {
            tabs.child(Look.tab(candidate.title(), candidate == tab, 80, pressed -> {
                tab = candidate;
                fillTabs();
                fillBody();
                // Новая вкладка начинается сверху, а не с места прошлой.
                if (scroll != null) {
                    scroll.restore(0);
                }
            }));
        }
    }

    private void fillBody() {
        body.clearChildren();
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
                Text.translatable(growth.level()), CAPTION));

        if (growth.next().isEmpty()) {
            LabelComponent top = Components.label(
                    Text.translatable("villagepax.screen.growth.top"));
            top.color(Look.MUTED);
            top.lineHeight(10);
            card.child(top.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
            body.child(card);
            return;
        }

        card.child(Look.stat(Text.translatable("villagepax.screen.growth.next"),
                Text.translatable(growth.next().get()), CAPTION));

        if (!growth.reachable()) {
            // Обещать ступень, до которой нет ратуши, — хуже, чем молчать.
            LabelComponent later = Components.label(
                    Text.translatable("villagepax.screen.growth.not_yet"));
            later.color(Look.MUTED);
            later.lineHeight(10);
            card.child(later.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
            body.child(card);
            return;
        }

        LabelComponent how = Components.label(Text.translatable("villagepax.screen.growth.how",
                Text.literal(String.valueOf(growth.needsHall()))));
        how.color(Look.INK);
        how.lineHeight(10);
        card.child(how.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));

        if (growth.opens().isEmpty()) {
            LabelComponent nothing = Components.label(
                    Text.translatable("villagepax.screen.growth.nothing"));
            nothing.color(Look.MUTED);
            nothing.lineHeight(10);
            card.child(nothing.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
        } else {
            for (String key : growth.opens()) {
                LabelComponent line = Components.label(
                        Text.literal("• ").append(Text.translatable(key)));
                line.color(Look.GOOD);
                line.lineHeight(10);
                card.child(line.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
            }
        }
        body.child(card);
    }

    /**
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
            hint.child(line.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
            body.child(hint);
        });

        fillGrowth(view);

        FlowLayout colony = Look.card("villagepax.screen.overview.section_colony",
                new ItemStack(ModBlocks.TOWN_HALL));
        colony.child(Look.stat(new ItemStack(Items.WHEAT),
                Text.translatable("villagepax.screen.overview.culture_name"),
                Text.translatable("villagepax.culture." + view.culture().getPath()),
                CAPTION, Look.INK));
        colony.child(Look.stat(new ItemStack(Items.RED_BED),
                Text.translatable("villagepax.screen.overview.beds_name"),
                Text.translatable("villagepax.screen.overview.beds_value",
                        number(view.beds()), number(view.freeBeds())),
                CAPTION, view.freeBeds() > 0 ? Look.INK : Look.BAD));
        colony.child(Look.stat(new ItemStack(Items.BREAD),
                Text.translatable("villagepax.screen.overview.food_name"),
                number(view.meals()), CAPTION,
                view.meals() > 0 ? Look.INK : Look.BAD));

        if (view.daysOfFood() > 0) {
            colony.child(Look.stat(new ItemStack(Items.CLOCK),
                    Text.translatable("villagepax.screen.overview.days_name"),
                    number(view.daysOfFood()), CAPTION,
                    view.daysOfFood() > 1 ? Look.INK : Look.BAD));
        } else {
            LabelComponent hungry = Components.label(
                    Text.translatable("villagepax.screen.overview.days_none"));
            hungry.color(Look.BAD);
            colony.child(hungry);
        }
        colony.child(Look.stat(new ItemStack(Items.CHEST),
                Text.translatable("villagepax.screen.overview.containers_name"),
                number(view.containers()), CAPTION,
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
            colony.child(deserted.horizontalSizing(Sizing.fixed(TEXT_WIDTH)));
        }
        body.child(colony);

        TownHallView.Construction construction = view.construction().orElse(null);
        if (construction == null) {
            FlowLayout idle = Look.card("villagepax.screen.overview.section_build",
                    new ItemStack(Items.IRON_PICKAXE));
            idle.child(Look.nothing(Text.translatable("villagepax.screen.overview.idle"),
                    TEXT_WIDTH));
            idle.child(Look.hint(Text.translatable("villagepax.screen.overview.idle_hint"),
                    TEXT_WIDTH));
            body.child(idle);
            return;
        }

        FlowLayout site = Look.card("villagepax.screen.overview.section_build",
                new ItemStack(Items.IRON_PICKAXE));
        LabelComponent what = Components.label(building(construction.type()));
        what.color(Look.INK);
        site.child(what);
        site.child(Look.stat(Text.translatable("villagepax.screen.overview.step_name"),
                Text.translatable("villagepax.screen.overview.step_value",
                        number(construction.step()), number(construction.steps())), CAPTION));
        site.child(Look.bar(construction.step(), construction.steps(),
                PANEL_WIDTH - 2 * PADDING - 24));

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
        body.child(site);
    }

    private void buildings(TownHallView view) {
        if (view.buildings().isEmpty()) {
            body.child(muted("villagepax.screen.buildings.none"));
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
            what.shadow(true);
            title.child(what.horizontalSizing(Sizing.fixed(190)));
            title.child(Look.pill(Text.translatable("villagepax.progress." + line.progress().id()),
                    line.progress() == BuildProgress.DONE ? Look.GOOD : Look.GOLD));
            card.child(title);

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
            body.child(card);
        }

        FlowLayout offers = Look.card("villagepax.screen.buildings.offers");
        offers.child(muted("villagepax.screen.buildings.order_hint"));
        for (Identifier schematic : view.offers()) {
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.gap(4);

            LabelComponent what = Components.label(building(typeOf(schematic)));
            what.color(Look.INK);
            row.child(what.horizontalSizing(Sizing.fixed(190)));
            row.child(Look.action(Text.translatable("villagepax.screen.buildings.order"), 76,
                    button -> order(schematic)));
            offers.child(row);
        }
        body.child(offers);
    }

    /**
     * Чем этот житель работает — значком.
     * <p>
     * Предмет выбирается кодом, а не данными, и это осознанно: профессий
     * в датапаке может быть сколько угодно, но значок — это <b>вид</b>,
     * и у незнакомого ремесла он всё равно будет общим. Общий — хлебная
     * корка: человек, который просто живёт.
     */
    private static ItemStack toolOf(Optional<Identifier> profession) {
        String craft = profession.map(Identifier::getPath).orElse("");
        return new ItemStack(switch (craft) {
            case "builder" -> Items.IRON_PICKAXE;
            case "courier" -> Items.CHEST;
            case "farmer" -> Items.WHEAT;
            case "lumberjack" -> Items.IRON_AXE;
            case "guard" -> Items.IRON_SWORD;
            case "elder" -> Items.BELL;
            default -> Items.BREAD;
        });
    }

    private void citizens(TownHallView view) {
        if (view.citizens().isEmpty()) {
            body.child(muted("villagepax.screen.citizens.none"));
        }

        for (TownHallView.CitizenLine citizen : view.citizens()) {
            FlowLayout card = Look.card(null);

            FlowLayout title = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            title.verticalAlignment(VerticalAlignment.CENTER);
            title.gap(4);

            // Значок ремесла: топор у лесоруба, меч у стражи, кайло
            // у строителя. Ремесло видно раньше, чем прочитано имя, —
            // а в колонии на четырнадцать человек именно ремесло и ищут.
            ItemComponent craft = Components.item(toolOf(citizen.profession()));
            craft.sizing(Sizing.fixed(12));
            craft.tooltip(professionName(view, citizen.profession()));
            title.child(craft);

            LabelComponent name = Components.label(Text.literal(citizen.name()));
            name.color(Look.INK);
            name.shadow(false);
            title.child(name.horizontalSizing(Sizing.fixed(136)));
            title.child(Look.pill(Text.translatable(citizen.mood().translationKey()),
                    colorOf(citizen)));
            card.child(title);
            card.child(Look.rule());

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

            body.child(card);
        }
    }

    private void stock(TownHallView view) {
        FlowLayout card = Look.card(null);
        card.child(muted("villagepax.screen.stock.hint"));

        if (view.stock().isEmpty()) {
            card.child(muted("villagepax.screen.stock.empty"));
            body.child(card);
            return;
        }
        for (Map.Entry<Identifier, Integer> entry : view.stock().contents().entrySet()) {
            card.child(Look.itemRow(stackOf(entry.getKey()), entry.getValue()));
        }
        body.child(card);
    }

    /**
     * Вкладка веры: кто слушает, сколько набрано и что можно попросить.
     * <p>
     * Полоса до следующей ступени — главное, что здесь есть. Число
     * «сто двадцать» само по себе не говорит ничего; полоса, которая
     * ползёт от жертвы к жертве, говорит всё, и именно она превращает
     * медленное накопление в цель, а не в ожидание.
     * <p>
     * Кнопки <b>не запираются</b> никогда, даже когда очков заведомо мало.
     * Серая кнопка объясняет ровно столько же, сколько молчащая, то есть
     * ничего; нажатая отвечает словами — «бог ещё не заметил», «не хватает
     * благосклонности», «просить нечего». Это то самое правило, которым
     * мод расплатился за чужую ратушу с мёртвыми кнопками.
     */
    private void faith(TownHallView view) {
        TownHallView.FaithView faith = view.faith();

        if (faith.gods().isEmpty()) {
            body.child(muted("villagepax.screen.faith.no_pantheon"));
            return;
        }

        if (!faith.temple()) {
            FlowLayout hint = Look.card("villagepax.screen.faith.section_temple");
            hint.child(Look.hint(Text.translatable("villagepax.screen.faith.needs_temple"),
                    TEXT_WIDTH));
            body.child(hint);
        }

        for (TownHallView.GodLine god : faith.gods()) {
            FlowLayout card = Look.card(null);

            FlowLayout title = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            title.verticalAlignment(VerticalAlignment.CENTER);
            title.gap(4);

            LabelComponent name = Components.label(Text.translatable(god.displayName()));
            name.color(Look.INK);
            name.shadow(true);
            title.child(name.horizontalSizing(Sizing.fixed(150)));
            title.child(Look.pill(Text.translatable(god.tier()),
                    god.alwaysOn() ? Look.GOOD : Look.GOLD));
            card.child(title);

            card.child(Look.stat(Text.translatable("villagepax.screen.faith.domain"),
                    Text.translatable("villagepax.faith.domain." + god.domain()), CAPTION));

            // Полоса до следующей ступени. У высшей полосы нет: расти
            // больше некуда, и рисовать пустой жёлоб значило бы обещать
            // ступень, которой не существует.
            if (god.nextAt() > 0) {
                card.child(Look.stat(Text.translatable("villagepax.screen.faith.favour"),
                        Text.translatable("villagepax.screen.faith.favour_value",
                                number(god.favour()), number(god.nextAt())), CAPTION));
                card.child(Look.bar(Math.min(god.favour(), god.nextAt()), god.nextAt(),
                        TEXT_WIDTH));
            } else {
                card.child(Look.stat(Text.translatable("villagepax.screen.faith.favour"),
                        Text.translatable("villagepax.screen.faith.favour_top",
                                number(god.favour())), CAPTION));
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
                    blessing, CAPTION));

            FlowLayout controls = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            controls.verticalAlignment(VerticalAlignment.CENTER);
            controls.gap(3);
            controls.child(Look.action(Text.translatable("villagepax.screen.faith.bless"), 96,
                    button -> bless(god.domain())));
            controls.child(Look.action(Text.translatable("villagepax.screen.faith.miracle"), 96,
                    button -> miracle(god.domain())));
            card.child(controls);

            body.child(card);
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
        return citizen.mood() == Mood.CONTENT ? Look.GOOD : Color.ofRgb(0xD0C070);
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
