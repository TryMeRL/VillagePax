package com.villagepax.client.screen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.villagepax.core.config.Config;
import com.villagepax.core.config.Configs;
import com.villagepax.screen.PanelMetrics;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки мода — в игре, а не только в файле.
 * <p>
 * Тот же {@code config/villagepax.json}: экран читает его через
 * {@link Configs#get()} и пишет через {@link Configs#apply}, а поля берёт
 * из {@link Config#SETTINGS} — таблицы, из которой собран и кодек. Своего
 * списка настроек у экрана нет, поэтому новая настройка появляется здесь
 * сама, стоит ей появиться в таблице.
 * <p>
 * <b>Правка держится в json, а не в полях записи.</b> Настройка — это
 * запись из шестнадцати полей, и менять одно из них значило бы писать
 * шестнадцать «копий с изменением». Вместо этого экран правит тот же json,
 * который лёг бы в файл, и перед сохранением читает его тем же кодеком:
 * правило «что годится» остаётся одно на файл и на экран.
 * <p>
 * <b>Что не годится, сказано у самого поля</b> — красной строкой под ним,
 * и «Готово» не нажимается, пока в поле не число или число вне границ.
 * А то, что кодек поправит сам, — например, срок ухода от голода раньше
 * срока жалобы, — экран не запрещает, а называет: «сохранится семь». Игрок
 * должен знать, что именно сохранится, до того, как сохранит.
 * <p>
 * Escape и «Отмена» закрывают экран без записи: кнопок две, и тихое
 * сохранение по Escape сделало бы «Отмену» лишней.
 */
public class SettingsScreen extends BaseOwoScreen<FlowLayout> {

    private static final int WIDTH = PanelMetrics.SETTINGS_WIDTH;
    private static final int HEIGHT = PanelMetrics.SETTINGS_HEIGHT;
    private static final int BODY_HEIGHT = PanelMetrics.bodyHeight(HEIGHT,
            PanelMetrics.HEADER, PanelMetrics.FOOTER);

    /** Поле ввода и выключатель — одной ширины, чтобы столбец стоял ровно. */
    private static final int CONTROL_WIDTH = 64;

    /** Текст внутри карточки: панель без отступов, ползунка и полей карточки. */


    /** Подпись поля — всё, что осталось в строке рядом с ним. */


    /**
     * Ширина подсказки. Без переноса подсказка шла одной строкой и уходила
     * за край окна: owo кладёт текст в неё как есть, не деля на строки.
     */
    private static final int TOOLTIP_WIDTH = 200;

    /** Набранное число: годное — светлым, как в ванильном поле, негодное — красным. */
    private static final int TYPED = 0xE0E0E0;
    private static final int TYPED_WRONG = 0xFF6F5F;

    /** Надпись «Готово»: чернилами, пока можно сохранить, и серым — пока нельзя. */
    private static final int DONE_READY = 0x2A1E10;
    private static final int DONE_BLOCKED = 0x8A7E6A;

    private final Screen parent;

    /** Что стоит в полях сейчас — в том виде, в каком это ляжет в файл. */
    private JsonObject edits;

    /** Поля, в которых набрано то, что в файл лечь не может, и почему. */
    private final Map<String, Text> mistyped = new HashMap<>();

    /** Место под жалобу у каждого поля: пустое, пока жаловаться не на что. */
    private final Map<String, FlowLayout> complaints = new HashMap<>();

    private Frame frame;
    private ButtonComponent done;

    public SettingsScreen(Screen parent) {
        super(Text.translatable("villagepax.config.title"));
        this.parent = parent;
        this.edits = encode(Configs.get());
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        frame = Frame.build(root, width, height, false);
        frame.title(getTitle());
        frame.closeButton(pressed -> close());

        frame.footer.horizontalAlignment(HorizontalAlignment.RIGHT);
        frame.footer.child(Look.action(Text.translatable("villagepax.config.reset"), 110,
                pressed -> reset()));
        frame.footer.child(Look.action(ScreenTexts.CANCEL, 84, pressed -> close()));
        done = Look.action(ScreenTexts.DONE, 84, pressed -> save());
        frame.footer.child(done);
        fillBody();
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.uiAdapter = null;
        super.resize(client, width, height);
    }

    /** Ширина текста в карточке колонки. */
    private int text() {
        return frame.textWidth;
    }

    /** Ширина имени настройки: строка без поля ввода. */
    private int nameWidth() {
        return text() - CONTROL_WIDTH - 8;
    }

    private void fillBody() {
        frame.clear();
        complaints.clear();

        // На чужом сервере свой файл ничего не меняет: настройки мира
        // держит тот, кто держит мир. Сказать это стоит до первого поля,
        // иначе игрок сохранит и будет ждать перемен, которых не будет.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && !client.isIntegratedServerRunning()) {
            FlowLayout note = Look.card(null);
            note.child(Look.hint(Text.translatable("villagepax.config.remote"), frame.bodyWidth - 14));
            frame.wide(note);
        }

        // Группы — карточками по колонкам; вес карточки — число настроек.
        Map<String, FlowLayout> cards = new java.util.LinkedHashMap<>();
        for (Config.Setting setting : Config.SETTINGS) {
            cards.computeIfAbsent(setting.group(),
                    group -> Look.card("villagepax.config.group." + group)).child(row(setting));
        }
        cards.values().forEach(card -> frame.place(card, card.children().size() * 2));
        validate();
    }

    /** Строка настройки: имя слева, поле справа, место для жалобы под ними. */
    private Component row(Config.Setting setting) {
        FlowLayout holder = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        holder.gap(2);

        FlowLayout line = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        line.verticalAlignment(VerticalAlignment.CENTER);
        line.gap(8);

        List<TooltipComponent> explained = wrapped(explain(setting));
        LabelComponent name = Components.label(
                Text.translatable("villagepax.config." + setting.key()));
        name.color(Look.INK);
        name.shadow(false);
        name.tooltip(explained);
        line.child(name.horizontalSizing(Sizing.fixed(nameWidth())));

        Component control = setting.bounds() instanceof Config.Flag
                ? toggle(setting) : field(setting);
        control.tooltip(explained);
        line.child(control);
        holder.child(line);

        FlowLayout complaint = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        complaints.put(setting.key(), complaint);
        holder.child(complaint);
        return holder;
    }

    /** Выключатель: одна кнопка, надпись — то, что сейчас стоит. */
    private Component toggle(Config.Setting setting) {
        String key = setting.key();
        ButtonComponent button = Look.action(Text.empty(), CONTROL_WIDTH, pressed -> {
            boolean now = !edits.get(key).getAsBoolean();
            edits.addProperty(key, now);
            pressed.setMessage(flag(now));
            validate();
        });
        button.setMessage(flag(edits.get(key).getAsBoolean()));
        return button;
    }

    /** Поле числа: проверяется на каждое нажатие, а не при сохранении. */
    private Component field(Config.Setting setting) {
        TextBoxComponent box = Components.textBox(Sizing.fixed(CONTROL_WIDTH),
                shown(edits.get(setting.key())));
        box.verticalSizing(Sizing.fixed(14));
        box.setMaxLength(10);
        box.onChanged().subscribe(typed -> {
            accept(setting, typed);
            box.setEditableColor(mistyped.containsKey(setting.key()) ? TYPED_WRONG : TYPED);
        });
        return box;
    }

    /**
     * Принять набранное в поле — или запомнить, почему нельзя.
     * <p>
     * Запятая считается точкой: «1,5» пишет каждый, у кого в раскладке
     * она стоит на месте точки, и отказ «это не число» был бы придиркой.
     */
    private void accept(Config.Setting setting, String typed) {
        String key = setting.key();
        String clean = typed.trim().replace(',', '.');
        Text problem = null;
        try {
            if (setting.bounds() instanceof Config.Whole whole) {
                int value = Integer.parseInt(clean);
                if (value < whole.min() || value > whole.max()) {
                    problem = outOfRange(whole.min(), whole.max());
                } else {
                    edits.addProperty(key, value);
                }
            } else if (setting.bounds() instanceof Config.Fraction fraction) {
                double value = Double.parseDouble(clean);
                // Сравнение «внутри», а не «снаружи»: NaN не больше
                // и не меньше ничего и иначе прошёл бы проверку.
                if (value >= fraction.min() && value <= fraction.max()) {
                    edits.addProperty(key, value);
                } else {
                    problem = outOfRange(fraction.min(), fraction.max());
                }
            }
        } catch (NumberFormatException notANumber) {
            problem = Text.translatable("villagepax.config.not_a_number");
        }

        if (problem == null) {
            mistyped.remove(key);
        } else {
            mistyped.put(key, problem);
        }
        validate();
    }

    /**
     * Жалобы под полями и доступность «Готово».
     * <p>
     * Поправку кодека экран узнаёт, прочитав правку и записав её обратно:
     * где записанное разошлось с набранным, там кодек поправил. Правил
     * согласования экран не знает и знать не должен — они живут в записи
     * настроек, и второй их копии здесь не будет.
     */
    private void validate() {
        Map<String, Text> said = new HashMap<>(mistyped);
        Config parsed = Config.CODEC.parse(JsonOps.INSTANCE, edits).result().orElse(null);
        if (parsed != null) {
            JsonObject settled = encode(parsed);
            for (Config.Setting setting : Config.SETTINGS) {
                String key = setting.key();
                if (!said.containsKey(key) && !same(settled.get(key), edits.get(key))) {
                    said.put(key, Text.translatable("villagepax.config.adjusted",
                            shown(settled.get(key))));
                }
            }
        }

        complaints.forEach((key, holder) -> {
            holder.clearChildren();
            Text problem = said.get(key);
            if (problem != null) {
                LabelComponent line = Components.label(problem);
                line.color(Look.BAD);
                line.shadow(false);
                line.lineHeight(9);
                holder.child(line.horizontalSizing(Sizing.fixed(text() - 12)));
            }
        });
        if (done != null) {
            // Выключенной кнопке ваниль сама надпись не гасит: цвет у неё
            // задан стилем текста. Значит, гасить надо здесь, иначе
            // «Готово», которое не нажимается, ничем не отличить от того,
            // что нажимается.
            boolean ready = mistyped.isEmpty() && parsed != null;
            done.active(ready);
            done.setMessage(ScreenTexts.DONE.copy().styled(style ->
                    style.withColor(ready ? DONE_READY : DONE_BLOCKED)));
        }
    }

    private void save() {
        Config.CODEC.parse(JsonOps.INSTANCE, edits).result().ifPresent(config -> {
            Configs.apply(config);
            close();
        });
    }

    /** Всё как при установке: поля заново, прежние ошибки забыты. */
    private void reset() {
        edits = encode(Config.DEFAULT);
        mistyped.clear();
        fillBody();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    /**
     * Подсказка к полю: что оно значит, в каких границах и что стоит
     * по умолчанию. Границы — из той же таблицы, по которой проверяется
     * ввод: подсказка не может разойтись с отказом.
     */
    private static Text explain(Config.Setting setting) {
        JsonElement standard = encode(Config.DEFAULT).get(setting.key());
        Text limits;
        if (setting.bounds() instanceof Config.Whole whole) {
            limits = Text.translatable("villagepax.config.range",
                    whole.min(), whole.max(), shown(standard));
        } else if (setting.bounds() instanceof Config.Fraction fraction) {
            limits = Text.translatable("villagepax.config.range",
                    plain(fraction.min()), plain(fraction.max()), shown(standard));
        } else {
            limits = Text.translatable("villagepax.config.default", flag(standard.getAsBoolean()));
        }
        return Text.translatable("villagepax.config." + setting.key() + ".tooltip")
                .append("\n")
                .append(limits.copy().formatted(Formatting.GRAY));
    }

    /** Подсказка строками по ширине: переносы из словаря сохраняются. */
    private static List<TooltipComponent> wrapped(Text text) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        return font.wrapLines(text, TOOLTIP_WIDTH).stream()
                .map(TooltipComponent::of)
                .toList();
    }

    private static Text outOfRange(Number min, Number max) {
        return Text.translatable("villagepax.config.out_of_range",
                plain(min.doubleValue()), plain(max.doubleValue()));
    }

    private static Text flag(boolean on) {
        return Text.translatable(on ? "villagepax.config.on" : "villagepax.config.off")
                .styled(style -> style.withColor(on ? Look.GOOD.rgb() : Look.BAD.rgb()));
    }

    /** Значение поля так, как его стоит показать: «1», а не «1.0». */
    private static String shown(JsonElement value) {
        if (value == null) {
            return "";
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return plain(value.getAsDouble());
        }
        return value.getAsString();
    }

    private static String plain(double number) {
        return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }

    private static boolean same(JsonElement one, JsonElement other) {
        if (one == null || other == null) {
            return one == other;
        }
        if (one.isJsonPrimitive() && other.isJsonPrimitive()
                && one.getAsJsonPrimitive().isNumber() && other.getAsJsonPrimitive().isNumber()) {
            return one.getAsDouble() == other.getAsDouble();
        }
        return one.equals(other);
    }

    private static JsonObject encode(Config config) {
        return Config.CODEC.encodeStart(JsonOps.INSTANCE, config).result()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .orElseThrow(() -> new IllegalStateException("настройки не кодируются"));
    }
}
