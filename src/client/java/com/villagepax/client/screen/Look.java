package com.villagepax.client.screen;

import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Общий язык обоих экранов мода: цвета, карточки, пилюли, строки.
 * <p>
 * Заведён затем, что экранов стало два — пульт колонии и разговор
 * со старейшиной, — и до сих пор каждый из них верстался своими руками.
 * Расходились они мелочами: где-то отступ три, где-то четыре, серый цвет
 * подписи в одном месте {@code 0xA0A0A0}, в другом {@code 0xB0B0B0}.
 * По отдельности это незаметно, вместе — выглядит как два мода.
 * <p>
 * Здесь же записано и <b>несущее правило вёрстки owo</b>, на котором я
 * споткнулся: {@code Sizing.fill(100)} — это процент <b>всего</b> места
 * контейнера, а не остатка после соседей. Прокрутка, которой дали
 * {@code fill(100)} рядом с заголовком и вкладками, получала высоту всей
 * панели, считала, что содержимое влезло, и <b>не листалась</b>. Поэтому
 * телу экрана высота считается числом, и счёт лежит в общем коде —
 * {@link com.villagepax.screen.PanelMetrics}, где его проверяет тест.
 */
public final class Look {

    // --- цвета ---

    /** Обычный текст. Не белый: чистый белый на тёмной панели режет глаз. */
    public static final Color INK = Color.ofRgb(0xE8E4DA);

    /** Подпись, пояснение, единицы измерения. */
    public static final Color MUTED = Color.ofRgb(0x9A958C);

    /** Золото: имя поселения, заголовки разделов, выбранная вкладка. */
    public static final Color GOLD = Color.ofRgb(0xE0B030);

    /** Хорошо и плохо. Одна пара на весь мод. */
    public static final Color GOOD = Color.ofRgb(0x7BD07B);
    public static final Color BAD = Color.ofRgb(0xE07A6A);

    /** Карточка: тёмная подложка и еле заметная рамка. */
    private static final int CARD_FILL = 0x30000000;
    private static final int CARD_EDGE = 0x24FFFFFF;

    /** Пилюля: то же, но плотнее — на ней лежит число. */
    private static final int PILL_FILL = 0x50000000;
    private static final int PILL_EDGE = 0x2CFFFFFF;

    /** Кнопка вкладки: обычная, наведённая, выбранная. */
    private static final int TAB_FILL = 0x40000000;
    private static final int TAB_HOVER = 0x60FFFFFF;
    private static final int TAB_CHOSEN = 0x60E0B030;

    private Look() {
    }

    /** Панель экрана: тёмная, с отступом и промежутками. */
    public static FlowLayout panel(int width, int height, int padding, int gap) {
        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(width), Sizing.fixed(height));
        // По отдельности, а не цепочкой: surface возвращает общий тип
        // родителя, и gap на нём уже не найти.
        panel.surface(Surface.DARK_PANEL);
        panel.padding(Insets.of(padding));
        panel.gap(gap);
        return panel;
    }

    /**
     * Карточка раздела: подложка, рамка, заголовок.
     * <p>
     * Карточками, а не сплошным списком строк. Пульт показывает разом
     * население, еду, стройку и нехватку материалов, и одним списком это
     * читается кашей: глаз не знает, где кончилось одно и началось другое.
     */
    public static FlowLayout card(String headingKey) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.flat(CARD_FILL).and(Surface.outline(CARD_EDGE)));
        card.padding(Insets.of(5));
        card.gap(3);
        if (headingKey != null) {
            card.child(heading(headingKey));
        }
        return card;
    }

    /** Заголовок раздела. */
    public static Component heading(String key) {
        LabelComponent label = Components.label(Text.translatable(key));
        label.color(GOLD);
        label.shadow(true);
        return label;
    }

    /**
     * Строка «подпись — значение».
     * <p>
     * Подпись серая и слева, значение белое и на своём месте: так столбец
     * значений выравнивается сам, и число находится глазом, а не чтением.
     */
    public static Component stat(Text name, Text value, int nameWidth) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);

        LabelComponent caption = Components.label(name);
        caption.color(MUTED);
        row.child(caption.horizontalSizing(Sizing.fixed(nameWidth)));

        LabelComponent number = Components.label(value);
        number.color(INK);
        row.child(number);
        return row;
    }

    /** Пилюля с числом: уровень, население, монета. */
    public static Component pill(Text text, Color colour) {
        FlowLayout pill = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        pill.surface(Surface.flat(PILL_FILL).and(Surface.outline(PILL_EDGE)));
        pill.padding(Insets.both(4, 2));
        pill.verticalAlignment(VerticalAlignment.CENTER);
        pill.gap(3);

        LabelComponent label = Components.label(text);
        label.color(colour);
        pill.child(label);
        return pill;
    }

    /** Пилюля со значком предмета: население кроватью, монета медяком. */
    public static Component pill(ItemStack icon, Text text, Color colour) {
        FlowLayout pill = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        pill.surface(Surface.flat(PILL_FILL).and(Surface.outline(PILL_EDGE)));
        pill.padding(Insets.both(4, 1));
        pill.verticalAlignment(VerticalAlignment.CENTER);
        pill.gap(3);

        ItemComponent picture = Components.item(icon);
        picture.setTooltipFromStack(true);
        pill.child(picture);

        LabelComponent label = Components.label(text);
        label.color(colour);
        pill.child(label);
        return pill;
    }

    /** Кнопка вкладки. Выбранная — золотая и выключенная: нажимать нечего. */
    public static ButtonComponent tab(Text title, boolean chosen, int width,
                                      java.util.function.Consumer<ButtonComponent> press) {
        ButtonComponent button = Components.button(
                chosen ? title.copy().formatted(Formatting.BLACK) : title, press);
        button.renderer(ButtonComponent.Renderer.flat(TAB_FILL, TAB_HOVER, TAB_CHOSEN));
        button.textShadow(!chosen);
        button.active(!chosen);
        button.horizontalSizing(Sizing.fixed(width));
        button.verticalSizing(Sizing.fixed(16));
        return button;
    }

    /** Обычная кнопка действия — тем же плоским письмом, что и вкладки. */
    public static ButtonComponent action(Text title, int width,
                                         java.util.function.Consumer<ButtonComponent> press) {
        ButtonComponent button = Components.button(title, press);
        button.renderer(ButtonComponent.Renderer.flat(TAB_FILL, TAB_HOVER, 0x30000000));
        button.horizontalSizing(Sizing.fixed(width));
        button.verticalSizing(Sizing.fixed(14));
        return button;
    }

    /** Черта: делит панель на части, чтобы она не читалась одной кашей. */
    public static Component rule() {
        BoxComponent line = new BoxComponent(Sizing.fill(100), Sizing.fixed(1));
        line.fill(true);
        line.color(Color.ofArgb(0x30FFFFFF));
        return line;
    }

    /**
     * Полоса доли: сколько сделано из всего.
     * <p>
     * Числами «шаг сорок из ста девяноста шести» доля не читается: чтобы
     * понять, много ли осталось, приходится делить в голове. Полоса
     * отвечает на это взглядом.
     */
    public static Component bar(int done, int total, int width) {
        int filled = total <= 0 ? 0 : Math.max(1, Math.min(width, width * done / total));

        FlowLayout track = Containers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(5));
        track.surface(Surface.flat(0x60000000).and(Surface.outline(0x20FFFFFF)));

        BoxComponent grown = new BoxComponent(Sizing.fixed(filled), Sizing.fixed(5));
        grown.fill(true);
        grown.color(GOOD);
        track.child(grown);
        return track;
    }

    /** Строка предмета: значок, имя, количество. */
    public static Component itemRow(ItemStack stack, int count) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);

        ItemComponent picture = Components.item(stack);
        picture.setTooltipFromStack(true);
        row.child(picture);

        LabelComponent name = Components.label(stack.getName());
        name.color(INK);
        row.child(name.horizontalSizing(Sizing.fixed(170)));

        LabelComponent many = Components.label(Text.literal("× " + count));
        many.color(MUTED);
        row.child(many);
        return row;
    }
}
