package com.villagepax.client.screen;

import com.villagepax.screen.PanelMetrics;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Окно мода во весь экран: вывеска сверху, вкладки слева, карточки
 * колонками, подсказка внизу.
 * <p>
 * Прежде каждое окно было листком в треть экрана посередине: пульт ратуши
 * на триста сорок точек, и всё, что не влезало, уходило под прокрутку
 * шириной в ладонь. Заказчик: «сделай на весь экран, больше деталей
 * и полезностей… как принято». Принято так, как в больших модах: окно
 * занимает экран, разделы — столбиком слева, а содержимое раскладывается
 * в две-три колонки, сколько влезет, — чтобы видеть сразу и стройку,
 * и склад, и жителей, а не листать.
 * <p>
 * Размер считается от экрана при сборке. Сборка повторяется при смене
 * размера окна игры (см. {@link #rebuildOnResize}): owo сам лишь двигает
 * уже собранное, и окно, собранное под одно разрешение, в другом
 * вылезало бы за край.
 */
public final class Frame {

    public final int width;
    public final int height;
    public final int bodyWidth;
    public final int bodyHeight;
    public final int columns;
    public final int columnWidth;
    /** Ширина текста внутри карточки колонки: без полей карточки. */
    public final int textWidth;

    public final FlowLayout panel;
    public final FlowLayout header;
    /** Левая половина вывески — название и главное; правая — числа и «закрыть». */
    public final FlowLayout headerLeft;
    public final FlowLayout headerRight;
    public final FlowLayout rail;
    public final FlowLayout footer;
    public final FlowLayout body;
    public final KeptScroll<FlowLayout> scroll;

    private final List<FlowLayout> stacks = new ArrayList<>();
    private final int[] weights;

    private static final int HEADER = 24;
    private static final int FOOTER = 14;
    private static final int SCROLLBAR = 8;

    private Frame(FlowLayout root, int screenWidth, int screenHeight, boolean withRail) {
        width = PanelMetrics.fit(screenWidth, PanelMetrics.MOST_WIDTH, PanelMetrics.LEAST_WIDTH);
        height = PanelMetrics.fit(screenHeight, PanelMetrics.MOST_HEIGHT, PanelMetrics.LEAST_HEIGHT);
        int pad = PanelMetrics.PADDING;
        int gap = PanelMetrics.GAP;
        int railWidth = withRail ? PanelMetrics.RAIL : 0;
        bodyWidth = width - 2 * pad - (withRail ? railWidth + gap : 0) - SCROLLBAR;
        bodyHeight = height - 2 * pad - HEADER - FOOTER - 2 * gap;
        columns = PanelMetrics.columns(bodyWidth);
        columnWidth = PanelMetrics.columnWidth(bodyWidth, columns);
        textWidth = columnWidth - 14;
        weights = new int[columns];

        root.surface(Surface.VANILLA_TRANSLUCENT);
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.verticalAlignment(VerticalAlignment.CENTER);

        panel = Look.panel(width, height, pad, gap);

        header = Look.board(HEADER);
        headerLeft = Containers.horizontalFlow(Sizing.fill(52), Sizing.fill(100));
        headerLeft.gap(5);
        headerLeft.verticalAlignment(VerticalAlignment.CENTER);
        headerRight = Containers.horizontalFlow(Sizing.fill(46), Sizing.fill(100));
        headerRight.gap(4);
        headerRight.verticalAlignment(VerticalAlignment.CENTER);
        headerRight.horizontalAlignment(HorizontalAlignment.RIGHT);
        header.child(headerLeft);
        header.child(headerRight);
        panel.child(header);

        FlowLayout middle = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(bodyHeight));
        middle.gap(gap);
        rail = Containers.verticalFlow(Sizing.fixed(railWidth), Sizing.fixed(bodyHeight));
        rail.gap(2);
        if (withRail) {
            rail.surface(Surface.flat(0x14000000).and(Surface.outline(0x30000000)));
            rail.padding(Insets.of(3));
            middle.child(rail);
        }

        body = Containers.verticalFlow(Sizing.fixed(bodyWidth), Sizing.content());
        body.gap(gap);
        scroll = new KeptScroll<>(Sizing.fixed(bodyWidth + SCROLLBAR), Sizing.fixed(bodyHeight), body);
        scroll.scrollbarThiccness(4);
        scroll.padding(Insets.right(SCROLLBAR - 2));
        middle.child(scroll);
        panel.child(middle);

        footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(FOOTER));
        footer.gap(8);
        footer.verticalAlignment(VerticalAlignment.CENTER);
        panel.child(footer);

        root.child(panel);
    }

    /** Собрать окно во весь экран. {@code withRail} — с вкладками слева. */
    public static Frame build(FlowLayout root, int screenWidth, int screenHeight, boolean withRail) {
        return new Frame(root, screenWidth, screenHeight, withRail);
    }

    // --- тело: колонки карточек ---

    /** Начать тело заново: пусто, колонки свежие. */
    public void clear() {
        body.clearChildren();
        stacks.clear();
        java.util.Arrays.fill(weights, 0);
    }

    /**
     * Карточка — в самую короткую колонку. Вес — примерная высота в строках:
     * собранная карточка своей высоты до раскладки не знает, а колонки
     * должны выйти ровными.
     */
    public void place(Component card, int weight) {
        if (stacks.isEmpty()) {
            FlowLayout row = Containers.horizontalFlow(Sizing.fixed(bodyWidth), Sizing.content());
            row.gap(PanelMetrics.GAP);
            for (int i = 0; i < columns; i++) {
                FlowLayout stack = Containers.verticalFlow(Sizing.fixed(columnWidth), Sizing.content());
                stack.gap(PanelMetrics.GAP);
                stacks.add(stack);
                row.child(stack);
            }
            body.child(row);
        }
        int shortest = 0;
        for (int i = 1; i < columns; i++) {
            if (weights[i] < weights[shortest]) {
                shortest = i;
            }
        }
        stacks.get(shortest).child(card);
        weights[shortest] += Math.max(1, weight);
    }

    /** Во всю ширину тела, над колонками или под ними. */
    public void wide(Component component) {
        body.child(component);
    }

    // --- рамка ---

    /** Вкладка в колонке слева: значок и подпись, выбранная — на золоте. */
    public void tab(ItemStack icon, Text title, boolean chosen, Runnable press) {
        FlowLayout tab = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        tab.verticalAlignment(VerticalAlignment.CENTER);
        tab.padding(Insets.horizontal(4));
        tab.gap(4);
        int fill = chosen ? 0xFFB8862A : 0x18000000;
        tab.surface(Surface.flat(fill));
        if (!chosen) {
            tab.mouseEnter().subscribe(() -> tab.surface(Surface.flat(0x30000000)));
            tab.mouseLeave().subscribe(() -> tab.surface(Surface.flat(fill)));
            tab.mouseDown().subscribe((x, y, button) -> {
                MinecraftClient.getInstance().getSoundManager().play(
                        net.minecraft.client.sound.PositionedSoundInstance.master(
                                net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                press.run();
                return true;
            });
        }
        ItemComponent picture = Components.item(icon);
        picture.sizing(Sizing.fixed(12));
        tab.child(picture);
        LabelComponent label = Components.label(title);
        label.color(Look.INK);
        label.shadow(false);
        tab.child(label);
        rail.child(tab);
    }

    /** Подсказка внизу окна: что здесь можно сделать. */
    public void hint(Text text) {
        LabelComponent label = Components.label(text);
        label.color(Look.MUTED);
        label.shadow(false);
        footer.child(label);
    }

    /** Заголовок окна на вывеске: название светлым, с тенью, как резьба. */
    public void title(Text text) {
        LabelComponent name = Components.label(text);
        name.color(Look.LIGHT);
        name.shadow(true);
        headerLeft.child(name);
    }

    /** Кнопка «закрыть» в правом углу вывески. */
    public void closeButton(Consumer<io.wispforest.owo.ui.component.ButtonComponent> press) {
        io.wispforest.owo.ui.component.ButtonComponent close = Look.action(Text.literal("✕"), 14, press);
        close.setMessage(Text.literal("✕").styled(style -> style.withColor(0xF3E6C8)));
        close.tooltip(Text.translatable("villagepax.screen.close"));
        headerRight.child(close);
    }
}
