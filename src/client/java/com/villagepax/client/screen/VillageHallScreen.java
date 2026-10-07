package com.villagepax.client.screen;

import com.villagepax.screen.TownHallNet;
import com.villagepax.screen.VillageHallNet;
import com.villagepax.screen.VillageHallView;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Окно ратуши деревни народа — то, что прежде было пятью строками в чате.
 * <p>
 * Вкладки — по тому, зачем к ратуше подходят: «кто я здесь и что мне
 * будет» (обзор: доверие, цены, рынок, праздник, новости, дела с деревней),
 * «чем помочь» (нужды с кнопкой «отдать»), «кто здесь живёт», «что
 * построено» и «что было» (летопись с книгой).
 */
public class VillageHallScreen extends BaseOwoScreen<FlowLayout> {

    private enum Tab {
        OVERVIEW("overview", Items.WRITABLE_BOOK),
        NEEDS("needs", Items.BRICKS),
        PEOPLE("people", Items.VILLAGER_SPAWN_EGG),
        BUILDINGS("buildings", Items.OAK_DOOR),
        CHRONICLE("chronicle", Items.WRITTEN_BOOK);

        final String id;
        final net.minecraft.item.Item icon;

        Tab(String id, net.minecraft.item.Item icon) {
            this.id = id;
            this.icon = icon;
        }

        Text title() {
            return Text.translatable("villagepax.hall.tab." + id);
        }
    }

    private VillageHallView view;
    private Tab tab = Tab.OVERVIEW;
    private Frame frame;

    public VillageHallScreen(VillageHallView view) {
        super(Text.literal(view.name()));
        this.view = view;
    }

    /** Новый снимок — в открытое окно той же деревни, иначе новое окно. */
    public static void open(MinecraftClient client, VillageHallView view) {
        if (client.currentScreen instanceof VillageHallScreen open
                && open.view.village().equals(view.village())) {
            open.view = view;
            open.fill();
            return;
        }
        client.setScreen(new VillageHallScreen(view));
    }

    /** Открыть сразу на вкладке по её номеру: для снимков экрана. */
    public VillageHallScreen showTab(int index) {
        tab = Tab.values()[Math.floorMod(index, Tab.values().length)];
        return this;
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
        frame = Frame.build(root, width, height, true);
        fill();
        frame.hint(Text.translatable("villagepax.hall.footer"));
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        this.uiAdapter = null;
        super.resize(client, width, height);
    }

    private void fill() {
        if (frame == null) {
            return;
        }
        double kept = frame.scroll.where();
        fillHead();
        fillTabs();
        frame.clear();
        switch (tab) {
            case OVERVIEW -> overview();
            case NEEDS -> needs();
            case PEOPLE -> people();
            case BUILDINGS -> buildings();
            case CHRONICLE -> chronicle();
        }
        frame.scroll.restore(kept);
    }

    private void fillHead() {
        frame.headerLeft.clearChildren();
        frame.headerRight.clearChildren();
        frame.title(Text.literal(view.name()));
        frame.headerLeft.child(Look.pill(Text.translatable(view.level()), Look.LIGHT));
        frame.headerLeft.child(Look.pill(Text.translatable("villagepax.culture." + view.culture().getPath()),
                Look.LIGHT));
        frame.headerRight.child(Look.pill(new ItemStack(Items.VILLAGER_SPAWN_EGG),
                Text.literal(view.population() + "/" + view.maxCitizens()), Look.LIGHT));
        frame.headerRight.child(Look.pill(new ItemStack(Items.EMERALD),
                Text.translatable(view.trust().standing()), standingColour()));
        frame.closeButton(button -> close());
    }

    private void fillTabs() {
        frame.rail.clearChildren();
        for (Tab candidate : Tab.values()) {
            frame.tab(new ItemStack(candidate.icon), candidate.title(), candidate == tab, () -> {
                tab = candidate;
                fill();
                frame.scroll.restore(0);
            });
        }
    }

    private Color standingColour() {
        return view.trust().reputation() < 0 ? Look.BAD : Look.LIGHT;
    }

    private int text() {
        return frame.textWidth;
    }

    private int caption() {
        return Math.max(90, frame.textWidth / 2);
    }

    private void put(Component card) {
        frame.place(card, card instanceof FlowLayout flow ? flow.children().size() : 1);
    }

    // --- обзор ---

    private void overview() {
        VillageHallView.Trust trust = view.trust();
        FlowLayout you = Look.card("villagepax.hall.trust", new ItemStack(Items.EMERALD));
        you.child(Look.stat(Text.translatable("villagepax.hall.trust.standing"),
                Text.translatable("villagepax.hall.trust.standing_value",
                        Text.translatable(trust.standing()), trust.reputation()), caption()));
        trust.nextStanding().ifPresent(next -> {
            int span = Math.max(1, trust.nextFrom() - trust.fromHere());
            int done = Math.max(0, Math.min(span, trust.reputation() - trust.fromHere()));
            you.child(Look.stat(Text.translatable("villagepax.hall.trust.next"),
                    Text.translatable("villagepax.hall.trust.next_value", Text.translatable(next),
                            trust.nextFrom() - trust.reputation()), caption()));
            you.child(Look.bar(done, span, text()));
        });
        you.child(Look.stat(Text.translatable("villagepax.hall.trust.buy"),
                Text.literal(trust.buyPercent() + "%"), caption()));
        you.child(Look.stat(Text.translatable("villagepax.hall.trust.sell"),
                Text.literal(trust.sellPercent() + "%"), caption()));
        if (trust.marketPrices()) {
            you.child(Look.hint(Text.translatable("villagepax.hall.trust.market_prices"), text()));
        }
        you.child(Look.hint(Text.translatable("villagepax.hall.trust.how"), text()));
        put(you);

        VillageHallView.Calendar calendar = view.calendar();
        FlowLayout days = Look.card("villagepax.hall.calendar", new ItemStack(Items.CLOCK));
        days.child(Look.stat(Text.translatable("villagepax.hall.calendar.day"),
                Text.literal(String.valueOf(calendar.day())), caption()));
        days.child(Look.stat(Text.translatable("villagepax.hall.calendar.market"),
                !calendar.hasMarket() ? Text.translatable("villagepax.hall.calendar.no_market")
                        : calendar.marketIn() == 0 ? Text.translatable("villagepax.hall.calendar.today")
                        : Text.translatable("villagepax.hall.calendar.in_days", calendar.marketIn()),
                caption()));
        calendar.festival().ifPresent(name -> days.child(Look.stat(Text.translatable(name),
                calendar.festivalIn() == 0 ? Text.translatable("villagepax.hall.calendar.today")
                        : Text.translatable("villagepax.hall.calendar.in_days", calendar.festivalIn()),
                caption())));
        put(days);

        if (!view.news().isEmpty()) {
            FlowLayout news = Look.card("villagepax.screen.overview.section_news", new ItemStack(Items.BELL));
            for (String json : view.news()) {
                news.child(Look.hint(parse(json), text()));
            }
            put(news);
        }

        put(ties());

        FlowLayout village = Look.card("villagepax.hall.village", new ItemStack(Items.OAK_DOOR));
        village.child(Look.stat(Text.translatable("villagepax.hall.village.level"),
                Text.translatable(view.level()), caption()));
        village.child(Look.stat(Text.translatable("villagepax.hall.village.people"),
                Text.literal(view.population() + " / " + view.maxCitizens()), caption()));
        village.child(Look.bar(view.population(), view.maxCitizens(), text()));
        view.nextLevel().ifPresent(next -> village.child(Look.hint(
                Text.translatable("villagepax.hall.village.grows", Text.translatable(next)), text())));
        put(village);
    }

    /** Дела между игроком и деревней. */
    private FlowLayout ties() {
        VillageHallView.Ties ties = view.ties();
        FlowLayout card = Look.card("villagepax.hall.ties", new ItemStack(Items.IRON_SWORD));
        if (ties.besieged()) {
            card.child(line(Text.translatable("villagepax.hall.ties.besieged"), Look.BAD));
        }
        if (ties.ally()) {
            card.child(line(Text.translatable("villagepax.hall.ties.ally"), Look.GOOD));
        }
        if (ties.tributeDays() > 0) {
            card.child(line(Text.translatable("villagepax.hall.ties.tribute", ties.tributeDays()), Look.GOLD));
        }
        if (ties.truceDays() > 0) {
            card.child(line(Text.translatable("villagepax.hall.ties.truce", ties.truceDays()), Look.MUTED));
        }
        switch (ties.citizenship()) {
            case "already" -> card.child(line(Text.translatable("villagepax.hall.ties.citizen"), Look.GOOD));
            case "yes" -> {
                card.child(Look.hint(Text.translatable("villagepax.hall.ties.may_settle"), text()));
                card.child(Look.action(Text.translatable("villagepax.hall.ties.settle"), 140,
                        button -> send(VillageHallNet.SETTLE, null)));
            }
            default -> card.child(Look.hint(Text.translatable("villagepax.citizenship." + ties.citizenship()),
                    text()));
        }
        card.child(Look.hint(Text.translatable("villagepax.hall.ties.elder"), text()));
        return card;
    }

    // --- нужды ---

    private void needs() {
        VillageHallView.Needs needs = view.needs();
        FlowLayout build = Look.card("villagepax.hall.needs.build", new ItemStack(Items.IRON_PICKAXE));
        if (needs.building().isEmpty()) {
            build.child(Look.hint(Text.translatable("villagepax.needs.nothing_building"), text()));
        } else if (needs.missing().isEmpty()) {
            build.child(Look.hint(Text.translatable("villagepax.needs.building_ready",
                    Text.translatable(TownHallNet.buildingKey(needs.building().get()))), text()));
        } else {
            build.child(Look.hint(Text.translatable("villagepax.hall.needs.building",
                    Text.translatable(TownHallNet.buildingKey(needs.building().get()))), text()));
            for (Map.Entry<Identifier, Integer> lack : needs.missing().entrySet()) {
                build.child(needRow(lack.getKey(), lack.getValue(), needs.carried().getOrDefault(lack.getKey(), 0)));
            }
        }
        put(build);

        FlowLayout home = Look.card("villagepax.hall.needs.home", new ItemStack(Items.BREAD));
        home.child(Look.stat(new ItemStack(Items.BREAD), Text.translatable("villagepax.hall.needs.food"),
                needs.hungry() ? Text.translatable("villagepax.needs.food_low")
                        : Text.translatable("villagepax.hall.needs.food_days", needs.foodDays()),
                caption(), needs.hungry() ? Look.BAD : Look.INK));
        home.child(Look.stat(new ItemStack(Items.RED_BED), Text.translatable("villagepax.hall.needs.beds"),
                Text.literal(String.valueOf(needs.freeBeds())), caption(),
                needs.freeBeds() > 0 ? Look.INK : Look.BAD));
        if (needs.hungry()) {
            home.child(Look.hint(Text.translatable("villagepax.hall.needs.bring_food"), text()));
        }
        put(home);

        FlowLayout how = Look.card("villagepax.hall.needs.how", new ItemStack(Items.EMERALD));
        how.child(Look.hint(Text.translatable("villagepax.hall.needs.how_text"), text()));
        how.child(Look.action(Text.translatable("villagepax.hall.needs.from_hand"), 150,
                button -> send(VillageHallNet.DONATE, VillageHallNet.HAND)));
        put(how);
    }

    private Component needRow(Identifier item, int need, int have) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);
        ItemStack stack = new ItemStack(Registries.ITEM.get(item));
        row.child(Look.slot(stack, need));
        LabelComponent name = Components.label(Text.translatable("villagepax.hall.needs.row",
                stack.getName(), need, have));
        name.color(have > 0 ? Look.INK : Look.MUTED);
        name.shadow(false);
        row.child(name.horizontalSizing(Sizing.fixed(text() - 82)));
        ButtonComponent give = Look.action(Text.translatable("villagepax.hall.needs.give"), 56,
                button -> send(VillageHallNet.DONATE, item));
        give.active(have > 0);
        row.child(give);
        return row;
    }

    // --- жители, здания, летопись ---

    private void people() {
        if (view.people().isEmpty()) {
            put(Look.nothing(Text.translatable("villagepax.screen.citizens.none"), text()));
            return;
        }
        for (VillageHallView.Person person : view.people()) {
            FlowLayout card = Look.card(null);
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.gap(4);
            ItemComponent icon = Components.item(Look.professionIcon(person.profession()));
            icon.sizing(Sizing.fixed(12));
            row.child(icon);
            LabelComponent name = Components.label(Text.literal(person.name()
                    + (person.married() ? " ♥" : "")));
            name.color(Look.INK);
            name.shadow(false);
            row.child(name.horizontalSizing(Sizing.fixed(text() - 18)));
            card.child(row);
            card.child(Look.stat(Text.translatable(person.profession()
                            .map(id -> "villagepax.profession." + id.getPath())
                            .orElse("villagepax.profession.none")),
                    Text.translatable(person.stage()), caption()));
            frame.place(card, 2);
        }
    }

    private void buildings() {
        for (VillageHallView.House house : view.buildings()) {
            FlowLayout card = Look.card(null);
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.gap(4);
            ItemComponent icon = Components.item(Look.buildingIcon(house.type()));
            icon.sizing(Sizing.fixed(12));
            row.child(icon);
            LabelComponent name = Components.label(Text.translatable("villagepax.screen.buildings.line_name",
                    Text.translatable(TownHallNet.buildingKey(house.type())), house.level()));
            name.color(Look.INK);
            name.shadow(false);
            row.child(name.horizontalSizing(Sizing.fixed(text() - 84)));
            row.child(Look.pill(Text.translatable(house.done() ? "villagepax.progress.done"
                    : "villagepax.progress.building"), house.done() ? Look.GOOD : Look.GOLD));
            card.child(row);
            frame.place(card, 1);
        }
    }

    private void chronicle() {
        FlowLayout card = Look.card("villagepax.screen.overview.section_chronicle",
                new ItemStack(Items.WRITTEN_BOOK));
        if (view.chronicle().isEmpty()) {
            card.child(Look.hint(Text.translatable("villagepax.chronicle.empty"), frame.bodyWidth - 14));
        }
        for (String json : view.chronicle()) {
            card.child(Look.hint(parse(json), frame.bodyWidth - 14));
        }
        card.child(Look.action(Text.translatable("villagepax.hall.chronicle.book"), 150,
                button -> send(VillageHallNet.CHRONICLE, null)));
        frame.wide(card);
    }

    // --- общее ---

    private static Component line(Text text, Color colour) {
        LabelComponent label = Components.label(text);
        label.color(colour);
        label.shadow(false);
        return label;
    }

    private static Text parse(String json) {
        Text parsed = Text.Serializer.fromJson(json);
        return parsed == null ? Text.empty() : parsed;
    }

    private void send(Identifier channel, Identifier item) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(view.village());
        if (item != null) {
            buf.writeIdentifier(item);
        }
        ClientPlayNetworking.send(channel, buf);
    }
}
