package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.ItemTally;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Снимок колонии ездит по сети тем же кодеком, каким пишется на диск,
 * поэтому потерянное поле — это молча пустая вкладка у игрока, а не отказ.
 * Круговой прогон ловит именно это.
 */
class TownHallViewCodecTest {

    private static final TownHallView.Growth GROWTH =
            new TownHallView.Growth("villagepax.level.hamlet", Optional.empty(), 1, 2,
                    java.util.List.of(), true);

    /**
     * Вера с содержимым, а не пустая.
     * <p>
     * Пустая вера прошла бы круг и с потерянным полем: {@code NONE}
     * совпадает с умолчанием кодека. Проверять надо ровно то, что
     * умолчанию не равно, — иначе круговой прогон подтверждает сам себя.
     */
    private static final TownHallView.FaithView FAITH = new TownHallView.FaithView(
            java.util.List.of(new TownHallView.GodLine(
                    new Identifier("villagepax", "norman_sower"),
                    "villagepax.god.norman.sower", "harvest", 137,
                    "villagepax.faith.tier.heard", 250, 2, false)),
            true);

    private static TownHallView sample() {
        return new TownHallView(
                "Бовуар",
                new Identifier("villagepax", "norman"),
                "hamlet",
                3, 8,
                new TownHallView.Household(4, 1, 12, 2, 2),
                Optional.of(new TownHallView.Construction(
                        new Identifier("villagepax", "norman/farm"), 1, 40, 196,
                        new ItemTally(Map.of(new Identifier("minecraft", "oak_fence"), 6)))),
                List.of(new TownHallView.BuildingLine(UUID.randomUUID(),
                        new Identifier("villagepax", "norman/town_hall"), 1,
                        BuildProgress.DONE, new BlockPos(10, 64, -20), true, 3)),
                List.of(new TownHallView.CitizenLine(UUID.randomUUID(), "Rollo le Macon",
                        Optional.of(new Identifier("villagepax", "builder")), true,
                        Optional.of(new Identifier("villagepax", "norman/town_hall")),
                        Mood.CONTENT, false, "villagepax.age.elder", 91, "Аделиза (2)",
                        "ambitious")),
                new ItemTally(Map.of(new Identifier("minecraft", "bread"), 4)),
                List.of(new Identifier("villagepax", "norman/house_lvl1")),
                List.of(new TownHallView.ProfessionLine(new Identifier("villagepax", "builder"),
                        "villagepax.profession.builder", false, "villagepax.level.hamlet")),
                Optional.of("villagepax.advice.no_farm"),
                GROWTH,
                FAITH,
                new TownHallView.Yoke("Бовуар", 7));
    }

    @Test
    void roundTripsThroughJson() {
        TownHallView view = sample();

        DataResult<com.google.gson.JsonElement> encoded =
                TownHallView.CODEC.encodeStart(JsonOps.INSTANCE, view);
        DataResult<TownHallView> decoded = TownHallView.CODEC.parse(JsonOps.INSTANCE,
                encoded.result().orElseThrow(() -> new AssertionError("не закодировалось: "
                        + encoded.error().map(Object::toString).orElse("?"))));

        assertEquals(view, decoded.result().orElseThrow(() ->
                new AssertionError("не раскодировалось: "
                        + decoded.error().map(Object::toString).orElse("?"))));
    }

    /**
     * Сеть пишет снимок как NBT-словарь, и приведение типа в отправителе
     * держится ровно на этом. Запись кодируется словарём — но проверить
     * это надо, а не предполагать.
     */
    @Test
    void encodesToAnNbtCompound() {
        DataResult<NbtElement> encoded = TownHallView.CODEC.encodeStart(NbtOps.INSTANCE, sample());
        NbtElement element = encoded.result().orElseThrow();

        assertInstanceOf(NbtCompound.class, element);
    }

    /**
     * Вера переживает круг и различает снимки.
     * <p>
     * Отдельной проверкой, а не доверием к общему кругу: вера — последнее,
     * шестнадцатое поле кодека поселения и четырнадцатое здесь, и добавлена
     * она {@code optionalFieldOf} с умолчанием. Такое поле теряется
     * <b>молча</b>: снимок всё равно раскодируется, просто вкладка у игрока
     * окажется пустой. Значит, проверять его надо значением, которое
     * умолчанию не равно.
     */
    @Test
    void faithSurvivesTheRoundTripAndTellsViewsApart() {
        TownHallView view = sample();

        NbtElement encoded = TownHallView.CODEC.encodeStart(NbtOps.INSTANCE, view)
                .result().orElseThrow();
        TownHallView back = TownHallView.CODEC.parse(NbtOps.INSTANCE, encoded)
                .result().orElseThrow();

        assertEquals(FAITH, back.faith(), "вера потерялась в кодеке");
        assertEquals(137, back.faith().gods().get(0).favour(), "благосклонность не та");
        assertTrue(back.faith().temple(), "храм потерялся");

        TownHallView godless = new TownHallView(view.name(), view.culture(), view.level(),
                view.population(), view.maxCitizens(), view.household(), view.construction(),
                view.buildings(), view.citizens(), view.stock(), view.offers(),
                view.professions(), view.advice(), view.growth(),
                TownHallView.FaithView.NONE, TownHallView.Yoke.NONE);

        assertTrue(!view.equals(godless),
                "снимки с разной верой обязаны различаться, иначе вкладка замрёт");
    }

    @Test
    void roundTripsThroughNbt() {
        TownHallView view = sample();

        NbtElement encoded = TownHallView.CODEC.encodeStart(NbtOps.INSTANCE, view).result().orElseThrow();
        TownHallView back = TownHallView.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(view, back);
    }

    /**
     * Колония без стройки и без жителей — обычное состояние первой минуты
     * игры, и снимок обязан его выражать.
     */
    @Test
    void emptyColonyRoundTrips() {
        TownHallView empty = new TownHallView("", new Identifier("villagepax", "norman"), "hamlet",
                0, 4, new TownHallView.Household(0, 0, 0, 0, 0), Optional.empty(),
                List.of(), List.of(), new ItemTally(), List.of(), List.of(), Optional.empty(), GROWTH,
                TownHallView.FaithView.NONE, TownHallView.Yoke.NONE);

        NbtElement encoded = TownHallView.CODEC.encodeStart(NbtOps.INSTANCE, empty).result().orElseThrow();
        assertEquals(empty, TownHallView.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow());
    }

    /**
     * Снимок сравнивают, чтобы не гнать сеть на пустом месте, — значит
     * равенство обязано быть по значению, а не по ссылке.
     */
    @Test
    void equalViewsAreEqual() {
        assertEquals(sample().name(), sample().name());

        TownHallView one = new TownHallView("Бовуар", new Identifier("villagepax", "norman"),
                "hamlet", 1, 4, new TownHallView.Household(1, 0, 2, 1, 1), Optional.empty(),
                List.of(), List.of(),
                new ItemTally(Map.of(new Identifier("minecraft", "bread"), 2)), List.of(),
                List.of(), Optional.empty(), GROWTH, TownHallView.FaithView.NONE, TownHallView.Yoke.NONE);
        TownHallView same = new TownHallView("Бовуар", new Identifier("villagepax", "norman"),
                "hamlet", 1, 4, new TownHallView.Household(1, 0, 2, 1, 1), Optional.empty(),
                List.of(), List.of(),
                new ItemTally(Map.of(new Identifier("minecraft", "bread"), 2)), List.of(),
                List.of(), Optional.empty(), GROWTH, TownHallView.FaithView.NONE, TownHallView.Yoke.NONE);

        assertEquals(one, same, "снимки с одинаковым содержимым обязаны быть равны");

        TownHallView other = new TownHallView("Бовуар", new Identifier("villagepax", "norman"),
                "hamlet", 1, 4, new TownHallView.Household(1, 0, 2, 1, 1), Optional.empty(),
                List.of(), List.of(),
                new ItemTally(Map.of(new Identifier("minecraft", "bread"), 3)), List.of(),
                List.of(), Optional.empty(), GROWTH, TownHallView.FaithView.NONE, TownHallView.Yoke.NONE);

        assertTrue(!one.equals(other), "разный склад — разные снимки, иначе экран замрёт");
    }
}
