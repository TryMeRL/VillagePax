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
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Общий язык обоих экранов мода: цвета, карточки, пилюли, строки.
 * <p>
 * Заведён затем, что экранов стало два — пульт колонии и разговор
 * со старейшиной, — и до сих пор каждый из них верстался своими руками.
 * Расходились они мелочами: где-то отступ три, где-то четыре, серый цвет
 * подписи в одном месте {@code 0xA0A0A0}, в другом {@code 0xB0B0B0}.
 * По отдельности это незаметно, вместе — выглядит как два мода.
 * <p>
 * <b>Светлая бумага под тёмными чернилами — и своя, а не чужая.</b>
 * Первый вид был чёрным стеклом со светлым текстом, и заказчик назвал
 * его «чисто тёмным». Второй — ванильными подложками owo, и вышло хуже:
 * {@code Surface.PANEL_INSET}, которым рисовались все карточки, —
 * <b>тёмно-серый</b>, а текст по нему шёл мой тёмно-коричневый. Два
 * тёмных слоя друг на друге не читаются вовсе, и заказчик сказал ровно
 * это: «вырвиглазное меню, ничего не разобрать».
 * <p>
 * Чинить перекраской чернил в светлые значило бы вернуться к тёмному
 * меню, от которого он отказался. Поэтому подложки свои: лён у окна,
 * пергамент у карточек. Тёмные чернила по светлой бумаге — то, как
 * выглядят и ванильная книга, и интерфейс MineColonies, на который
 * заказчик просил равняться. Заголовок по-прежнему лежит на доске
 * из настоящего дерева: окно начинается с вывески.
 * <p>
 * <b>Тень у текста выключена.</b> На тёмном фоне тень отделяла букву от
 * подложки, на светлом она превращается в грязь под каждой буквой. Это
 * та мелочь, по которой самодельный интерфейс отличается от ванильного
 * с первого взгляда.
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

    /**
     * Обычный текст. Тёмно-коричневый, а не чёрный: чистый чёрный на
     * светлой панели выглядит дырой, а коричневый читается как чернила.
     */
    public static final Color INK = Color.ofRgb(0x33291B);

    /** Подпись, пояснение, единицы измерения. */
    public static final Color MUTED = Color.ofRgb(0x6B5E49);

    /** Золото: имя поселения, заголовки разделов, выбранная вкладка. */
    public static final Color GOLD = Color.ofRgb(0x8A5A12);

    /** Хорошо и плохо. Одна пара на весь мод. */
    public static final Color GOOD = Color.ofRgb(0x2F6B2A);
    public static final Color BAD = Color.ofRgb(0xA33021);

    /** Светлый текст — только на тёмном: на доске заголовка и на золоте. */
    public static final Color LIGHT = Color.ofRgb(0xF3E6C8);

    /** Доска заголовка: настоящая текстура дерева, а не крашеный прямоугольник. */
    private static final Identifier BOARD =
            new Identifier("minecraft", "textures/block/stripped_dark_oak_log.png");

    /**
     * Пилюля: гнездо под число.
     * <p>
     * По светлой бумаге гнездо делается <b>тенью</b>, а не подсветкой:
     * белёсая подложка на белёсом фоне не видна вовсе. Прежние значения
     * рисовались под тёмную панель и на бумаге пропали.
     */
    private static final int PILL_FILL = 0x1A000000;
    private static final int PILL_EDGE = 0x40000000;

    /** Кнопка вкладки: обычная, наведённая, выбранная. */
    private static final int TAB_FILL = 0x18000000;
    private static final int TAB_HOVER = 0x30000000;
    private static final int TAB_CHOSEN = 0xFFB8862A;

    /** Черта: тёмная линия и светлый подбой под ней — гравировкой. */
    private static final int RULE_DARK = 0x50000000;
    private static final int RULE_LIGHT = 0x60FFFFFF;

    private Look() {
    }

    /** Поле окна: некрашеный лён. */
    private static final Identifier LINEN_BG =
            new Identifier("villagepax", "textures/gui/linen.png");

    /** Поле карточки: бумага светлее окна, чтобы карточка выступала. */
    private static final Identifier PARCHMENT_BG =
            new Identifier("villagepax", "textures/gui/parchment.png");

    /** Рамка окна и карточки: тёмное дерево, тонкой чертой. */
    private static final int FRAME = 0xFF3A2A18;
    private static final int CARD_FRAME = 0x903A2A18;

    /** Панель экрана: льняное поле в деревянной рамке. */
    public static FlowLayout panel(int width, int height, int padding, int gap) {
        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(width), Sizing.fixed(height));
        // По отдельности, а не цепочкой: surface возвращает общий тип
        // родителя, и gap на нём уже не найти.
        panel.surface(Surface.tiled(LINEN_BG, 16, 16)
                .and(Surface.outline(FRAME)));
        panel.padding(Insets.of(padding));
        panel.gap(gap);
        return panel;
    }

    /**
     * Доска заголовка: тёмное дерево с золотой надписью.
     * <p>
     * Чтобы окно начиналось с чего-то, а не сразу с текста. Имя поселения
     * на доске читается как вывеска над входом — и это ровно то, чем оно
     * и является.
     */
    public static FlowLayout board(int height) {
        FlowLayout board = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(height));
        board.surface(Surface.tiled(BOARD, 16, 16).and(Surface.outline(0x80000000)));
        board.padding(Insets.both(6, 3));
        board.gap(5);
        board.verticalAlignment(VerticalAlignment.CENTER);
        return board;
    }

    /**
     * Карточка раздела: вдавленное гнездо с заголовком.
     * <p>
     * Карточками, а не сплошным списком строк. Пульт показывает разом
     * население, еду, стройку и нехватку материалов, и одним списком это
     * читается кашей: глаз не знает, где кончилось одно и началось другое.
     */
    public static FlowLayout card(String headingKey) {
        return card(headingKey, null);
    }

    /**
     * То же со значком у заголовка: кровать у жилья, хлеб у еды, кайло
     * у стройки. Значок находится глазом раньше, чем прочитано слово.
     */
    public static FlowLayout card(String headingKey, ItemStack icon) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.tiled(PARCHMENT_BG, 16, 16)
                .and(Surface.outline(CARD_FRAME)));
        card.padding(Insets.of(6));
        card.gap(4);

        if (headingKey != null) {
            FlowLayout title = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            title.verticalAlignment(VerticalAlignment.CENTER);
            title.gap(4);
            if (icon != null) {
                ItemComponent picture = Components.item(icon);
                picture.sizing(Sizing.fixed(10));
                title.child(picture);
            }
            title.child(heading(headingKey));
            card.child(title);
            card.child(rule());
        }
        return card;
    }

    /** Заголовок раздела. */
    public static Component heading(String key) {
        LabelComponent label = Components.label(Text.translatable(key));
        label.color(GOLD);
        label.shadow(false);
        return label;
    }

    /**
     * Строка «подпись — значение».
     * <p>
     * Подпись серая и слева, значение тёмное и на своём месте: так столбец
     * значений выравнивается сам, и число находится глазом, а не чтением.
     */
    public static Component stat(Text name, Text value, int nameWidth) {
        return stat(null, name, value, nameWidth, INK);
    }

    /** То же со значком и своим цветом значения: голод красным, достаток зелёным. */
    public static Component stat(ItemStack icon, Text name, Text value, int nameWidth,
                                 Color colour) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);

        if (icon != null) {
            ItemComponent picture = Components.item(icon);
            picture.sizing(Sizing.fixed(10));
            picture.setTooltipFromStack(true);
            row.child(picture);
        }

        LabelComponent caption = Components.label(name);
        caption.color(MUTED);
        caption.shadow(false);
        row.child(caption.horizontalSizing(Sizing.fixed(nameWidth)));

        LabelComponent number = Components.label(value);
        number.color(colour);
        number.shadow(false);
        row.child(number);
        return row;
    }

    /** Пилюля с числом: уровень, население, монета. */
    public static Component pill(Text text, Color colour) {
        FlowLayout pill = nest();
        LabelComponent label = Components.label(text);
        label.color(colour);
        label.shadow(false);
        pill.child(label);
        return pill;
    }

    /** Пилюля со значком предмета: население кроватью, монета медяком. */
    public static Component pill(ItemStack icon, Text text, Color colour) {
        FlowLayout pill = nest();
        ItemComponent picture = Components.item(icon);
        picture.sizing(Sizing.fixed(10));
        picture.setTooltipFromStack(true);
        pill.child(picture);

        LabelComponent label = Components.label(text);
        label.color(colour);
        label.shadow(false);
        pill.child(label);
        return pill;
    }

    /** Гнездо пилюли: общая подложка для обоих её видов. */
    private static FlowLayout nest() {
        FlowLayout pill = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        pill.surface(Surface.flat(PILL_FILL).and(Surface.outline(PILL_EDGE)));
        pill.padding(Insets.both(4, 2));
        pill.verticalAlignment(VerticalAlignment.CENTER);
        pill.gap(3);
        return pill;
    }

    /** Кнопка вкладки. Выбранная — золотая и выключенная: нажимать нечего. */
    public static ButtonComponent tab(Text title, boolean chosen, int width,
                                      java.util.function.Consumer<ButtonComponent> press) {
        // Цвет букв задаётся СТИЛЕМ текста, а не кнопкой, и это не
        // придирка. Выбранная вкладка выключена (нажимать её незачем),
        // а выключенной кнопке ваниль рисует надпись серой — по янтарной
        // подложке её не прочесть. Стиль текста ваниль уважает и в таком
        // состоянии: ровно на этом и держится читаемость вкладок.
        ButtonComponent button = Components.button(
                title.copy().styled(style -> style.withColor(chosen ? 0x2A1E10 : 0x4A3A22)),
                press);
        button.renderer(ButtonComponent.Renderer.flat(TAB_FILL, TAB_HOVER, TAB_CHOSEN));
        button.textShadow(false);
        button.active(!chosen);
        button.horizontalSizing(Sizing.fixed(width));
        button.verticalSizing(Sizing.fixed(16));
        return button;
    }

    /** Обычная кнопка действия — тем же плоским письмом, что и вкладки. */
    public static ButtonComponent action(Text title, int width,
                                         java.util.function.Consumer<ButtonComponent> press) {
        ButtonComponent button = Components.button(
                title.copy().styled(style -> style.withColor(0x2A1E10)), press);
        button.renderer(ButtonComponent.Renderer.flat(TAB_FILL, TAB_HOVER, 0x30000000));
        button.textShadow(false);
        button.horizontalSizing(Sizing.fixed(width));
        button.verticalSizing(Sizing.fixed(14));
        return button;
    }

    /**
     * Черта: делит панель на части, чтобы она не читалась одной кашей.
     * <p>
     * В две линии — тёмная и светлая под ней. Так черта выглядит
     * вырезанной в доске, а не нарисованной поверх неё; тем же приёмом
     * нарисованы все ванильные рамки.
     */
    public static Component rule() {
        FlowLayout engraved = Containers.verticalFlow(Sizing.fill(100), Sizing.content());

        BoxComponent dark = new BoxComponent(Sizing.fill(100), Sizing.fixed(1));
        dark.fill(true);
        dark.color(Color.ofArgb(RULE_DARK));
        engraved.child(dark);

        BoxComponent light = new BoxComponent(Sizing.fill(100), Sizing.fixed(1));
        light.fill(true);
        light.color(Color.ofArgb(RULE_LIGHT));
        engraved.child(light);
        return engraved;
    }

    /**
     * Полоса доли: сколько сделано из всего.
     * <p>
     * Числами «шаг сорок из ста девяноста шести» доля не читается: чтобы
     * понять, много ли осталось, приходится делить в голове. Полоса
     * отвечает на это взглядом, а цветом — на «скоро ли»: начатое
     * янтарное, доведённое до половины зелёное.
     */
    public static Component bar(int done, int total, int width) {
        int filled = total <= 0 ? 0 : Math.max(1, Math.min(width, width * done / total));
        boolean halfway = total > 0 && done * 2 >= total;

        FlowLayout track = Containers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(6));
        track.surface(Surface.flat(0x40000000).and(Surface.outline(0x60FFFFFF)));
        track.padding(Insets.of(1));

        BoxComponent grown = new BoxComponent(Sizing.fixed(Math.max(1, filled - 2)),
                Sizing.fixed(4));
        grown.fill(true);
        grown.color(halfway ? GOOD : GOLD);
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
        name.shadow(false);
        row.child(name.horizontalSizing(Sizing.fixed(170)));

        LabelComponent many = Components.label(Text.literal("× " + count));
        many.color(MUTED);
        many.shadow(false);
        row.child(many);
        return row;
    }

    /**
     * Пояснение мелким шрифтом под разделом.
     * <p>
     * То, что игрок прочитает один раз и больше не будет, — но этот
     * один раз решает, понял он экран или закрыл его.
     */
    public static Component hint(Text text, int width) {
        LabelComponent label = Components.label(text);
        label.color(MUTED);
        label.shadow(false);
        label.lineHeight(9);
        return label.horizontalSizing(Sizing.fixed(width));
    }

    /**
     * Пустой раздел: строка посреди карточки вместо списка.
     * <p>
     * «Ничего не строится» в середине пустого гнезда читается как ответ,
     * а прижатое к левому краю — как забытая подпись.
     */
    public static Component nothing(Text text, int width) {
        FlowLayout centred = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        centred.horizontalAlignment(HorizontalAlignment.CENTER);
        centred.padding(Insets.vertical(4));

        LabelComponent label = Components.label(text);
        label.color(MUTED);
        label.shadow(false);
        centred.child(label.horizontalSizing(Sizing.fixed(width)));
        return centred;
    }

    /**
     * Ячейка склада: вещь на тёмной подложке, число в углу. Больше
     * стопки — тоже числом: на складе ратуши «340 булыжника» — обычное дело.
     */
    public static Component slot(ItemStack stack, int count) {
        FlowLayout cell = Containers.verticalFlow(Sizing.fixed(18), Sizing.fixed(18));
        cell.surface(Surface.flat(0x30000000).and(Surface.outline(0x50000000)));
        cell.padding(Insets.of(1));
        ItemStack shown = stack.copy();
        shown.setCount(Math.max(1, count));
        ItemComponent picture = Components.item(shown);
        picture.showOverlay(true);
        picture.tooltip(java.util.List.of(stack.getName(),
                Text.literal("× " + count).styled(style -> style.withColor(0xA0A0A0))));
        cell.child(picture);
        return cell;
    }

    /** Значок здания — по его делу: поле — мотыга, башня — меч, дом — кровать. */
    public static ItemStack buildingIcon(Identifier type) {
        String kind = type.getPath().substring(type.getPath().indexOf('/') + 1);
        return new ItemStack(switch (kind) {
            case "town_hall" -> com.villagepax.block.ModBlocks.TOWN_HALL.asItem();
            case "house", "townhouse" -> net.minecraft.item.Items.RED_BED;
            case "farm" -> net.minecraft.item.Items.WHEAT;
            case "lumberjack" -> net.minecraft.item.Items.IRON_AXE;
            case "warehouse" -> net.minecraft.item.Items.CHEST;
            case "builder_hut" -> net.minecraft.item.Items.IRON_PICKAXE;
            case "watchtower", "gatehouse" -> net.minecraft.item.Items.IRON_SWORD;
            case "chapel", "shrine" -> net.minecraft.item.Items.CANDLE;
            case "brewery" -> com.villagepax.item.ModItems.ALE;
            case "weavery" -> com.villagepax.item.ModItems.CLOTH;
            case "market", "market_stall" -> net.minecraft.item.Items.EMERALD;
            case "fairground" -> net.minecraft.item.Items.FIREWORK_ROCKET;
            default -> net.minecraft.item.Items.BRICKS;
        });
    }

    public static ItemStack professionIcon(java.util.Optional<Identifier> profession) {
        String craft = profession.map(Identifier::getPath).orElse("");
        return new ItemStack(switch (craft) {
            case "builder" -> net.minecraft.item.Items.IRON_PICKAXE;
            case "courier" -> net.minecraft.item.Items.CHEST;
            case "farmer" -> net.minecraft.item.Items.IRON_HOE;
            case "lumberjack" -> net.minecraft.item.Items.IRON_AXE;
            case "guard" -> net.minecraft.item.Items.IRON_SWORD;
            case "elder" -> net.minecraft.item.Items.BELL;
            case "merchant" -> net.minecraft.item.Items.EMERALD;
            case "brewer" -> com.villagepax.item.ModItems.ALE;
            case "weaver" -> com.villagepax.item.ModItems.CLOTH;
            case "entertainer" -> com.villagepax.item.festival.ModFestivalItems.JUGGLING_BALLS;
            default -> profession.isPresent() ? net.minecraft.item.Items.CRAFTING_TABLE : net.minecraft.item.Items.BREAD;
        });
    }

}
