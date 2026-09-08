package com.villagepax.client.screen;

import com.villagepax.client.hologram.Placement;
import com.villagepax.screen.Mood;
import com.villagepax.screen.TownHallNet;
import com.villagepax.screen.TownHallScreenHandler;
import com.villagepax.screen.TownHallView;
import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.Components;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
 */
public class TownHallScreen extends BaseOwoHandledScreen<FlowLayout, TownHallScreenHandler> {

    /** Вкладки. Порядок — порядок в заголовке. */
    private enum Tab {
        OVERVIEW("overview"),
        BUILDINGS("buildings"),
        CITIZENS("citizens"),
        STOCK("stock");

        private final String id;

        Tab(String id) {
            this.id = id;
        }

        Text title() {
            return Text.translatable("villagepax.screen.tab." + id);
        }
    }

    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 210;

    private Tab tab = Tab.OVERVIEW;
    private FlowLayout body;
    private FlowLayout tabs;

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

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.fixed(PANEL_HEIGHT));
        panel.surface(Surface.DARK_PANEL);
        panel.padding(Insets.of(8));
        panel.gap(6);

        panel.child(Components.label(Text.translatable("villagepax.screen.town_hall",
                view().name())).shadow(true));

        tabs = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        tabs.gap(4);
        panel.child(tabs);

        body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        body.gap(3);

        FlowLayout scroll = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        scroll.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), body));
        panel.child(scroll);

        root.child(panel);

        fillTabs();
        fillBody();
    }

    private TownHallView view() {
        return getScreenHandler().view();
    }

    /** Новый снимок с сервера: заголовки вкладок и тело заново. */
    public void refresh(TownHallView fresh) {
        getScreenHandler().acceptView(fresh);
        if (body != null) {
            fillTabs();
            fillBody();
        }
    }

    private void fillTabs() {
        tabs.clearChildren();
        for (Tab candidate : Tab.values()) {
            Text label = candidate == tab
                    ? candidate.title().copy().formatted(Formatting.YELLOW)
                    : candidate.title();
            tabs.child(Components.button(label, button -> {
                tab = candidate;
                fillTabs();
                fillBody();
            }).horizontalSizing(Sizing.fixed(74)));
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
        }
    }

    // --- вкладки ---

    private void overview(TownHallView view) {
        line("villagepax.screen.overview.culture",
                Text.translatable("villagepax.culture." + view.culture().getPath()));
        line("villagepax.screen.overview.level", Text.literal(view.level()));
        line("villagepax.screen.overview.population",
                number(view.population()), number(view.maxCitizens()));
        line("villagepax.screen.overview.beds", number(view.beds()), number(view.freeBeds()));
        line("villagepax.screen.overview.food", number(view.meals()));

        if (view.daysOfFood() > 0) {
            line("villagepax.screen.overview.days", number(view.daysOfFood()));
        } else {
            body.child(Components.label(Text.translatable("villagepax.screen.overview.days_none"))
                    .color(Color.RED));
        }
        line("villagepax.screen.overview.containers", number(view.containers()));

        TownHallView.Construction construction = view.construction().orElse(null);
        if (construction == null) {
            body.child(Components.label(Text.translatable("villagepax.screen.overview.idle")));
            return;
        }

        line("villagepax.screen.overview.construction", building(construction.type()),
                number(construction.step()), number(construction.steps()));

        if (construction.missing().isEmpty()) {
            body.child(Components.label(Text.translatable("villagepax.screen.overview.missing_none")));
            return;
        }
        body.child(Components.label(Text.translatable("villagepax.screen.overview.missing"))
                .color(Color.RED));
        for (Map.Entry<Identifier, Integer> lack : construction.missing().contents().entrySet()) {
            body.child(itemLine(lack.getKey(), lack.getValue()));
        }
    }

    private void buildings(TownHallView view) {
        if (view.buildings().isEmpty()) {
            body.child(Components.label(Text.translatable("villagepax.screen.buildings.none")));
        }
        for (TownHallView.BuildingLine line : view.buildings()) {
            body.child(Components.label(Text.translatable("villagepax.screen.buildings.line",
                    building(line.type()), number(line.level()),
                    Text.literal(line.progress().id()))));
        }

        body.child(Components.label(Text.translatable("villagepax.screen.buildings.offers"))
                .shadow(true).margins(Insets.top(6)));
        body.child(Components.label(Text.translatable("villagepax.screen.buildings.order_hint"))
                .color(Color.ofRgb(0xA0A0A0)));

        for (Identifier schematic : view.offers()) {
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.gap(4);
            row.child(Components.button(Text.translatable("villagepax.screen.buildings.order"),
                    button -> order(schematic)).horizontalSizing(Sizing.fixed(70)));
            row.child(Components.label(building(typeOf(schematic))));
            body.child(row);
        }
    }

    private void citizens(TownHallView view) {
        if (view.citizens().isEmpty()) {
            body.child(Components.label(Text.translatable("villagepax.screen.citizens.none")));
        }

        for (TownHallView.CitizenLine citizen : view.citizens()) {
            FlowLayout row = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            row.surface(Surface.PANEL_INSET);
            row.padding(Insets.of(3));
            row.gap(2);

            row.child(Components.label(Text.literal(citizen.name()).formatted(Formatting.WHITE))
                    .shadow(true));

            List<Text> marks = new ArrayList<>();
            marks.add(Text.translatable(citizen.mood().translationKey()));
            if (!citizen.housed()) {
                marks.add(Text.translatable("villagepax.screen.citizens.homeless"));
            }
            if (citizen.workplace().isEmpty()) {
                marks.add(Text.translatable("villagepax.screen.citizens.no_workplace"));
            }
            if (citizen.leavingSoon()) {
                marks.add(Text.translatable("villagepax.screen.citizens.leaving"));
            }
            row.child(Components.label(join(marks)).color(colorOf(citizen)));

            FlowLayout controls = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            controls.verticalAlignment(VerticalAlignment.CENTER);
            controls.gap(4);
            controls.child(Components.button(professionName(view, citizen.profession()),
                    button -> assign(citizen.id(), nextProfession(view, citizen.profession())))
                    .horizontalSizing(Sizing.fixed(100)));
            controls.child(Components.label(Text.translatable("villagepax.screen.citizens.change"))
                    .color(Color.ofRgb(0xA0A0A0)));
            row.child(controls);

            body.child(row);
        }
    }

    private void stock(TownHallView view) {
        body.child(Components.label(Text.translatable("villagepax.screen.stock.hint"))
                .color(Color.ofRgb(0xA0A0A0)));

        if (view.stock().isEmpty()) {
            body.child(Components.label(Text.translatable("villagepax.screen.stock.empty")));
            return;
        }
        for (Map.Entry<Identifier, Integer> entry : view.stock().contents().entrySet()) {
            body.child(itemLine(entry.getKey(), entry.getValue()));
        }
    }

    // --- намерения ---

    /**
     * Выбор здания включает режим установки, а экран закрывается: место
     * игрок выбирает в мире голограммой, а не в меню.
     */
    private void order(Identifier schematic) {
        Placement.begin(schematic);
        close();
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
        if (current.isEmpty()) {
            return Optional.of(known.get(0).id());
        }

        for (int index = 0; index < known.size(); index++) {
            if (known.get(index).id().equals(current.get())) {
                // За последней профессией — «без дела», и круг замыкается.
                return index + 1 < known.size()
                        ? Optional.of(known.get(index + 1).id())
                        : Optional.empty();
            }
        }
        return Optional.of(known.get(0).id());
    }

    // --- мелочи вёрстки ---

    private void line(String key, Text... args) {
        body.child(Components.label(Text.translatable(key, (Object[]) args)));
    }

    private Component itemLine(Identifier item, int count) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);

        Item known = Registries.ITEM.get(item);
        row.child(Components.item(new ItemStack(known)));
        row.child(Components.label(known.getName().copy().append(" × " + count)));
        return row;
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
            return Color.RED;
        }
        return citizen.mood() == Mood.CONTENT ? Color.ofRgb(0x90C090) : Color.ofRgb(0xD0C070);
    }

    private static Text number(int value) {
        return Text.literal(String.valueOf(value));
    }

    private static Text join(List<Text> parts) {
        Text joined = Text.empty();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                joined = joined.copy().append(", ");
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
