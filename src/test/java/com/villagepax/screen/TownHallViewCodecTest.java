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

    private static TownHallView sample() {
        return new TownHallView(
                "Бовуар",
                new Identifier("villagepax", "norman"),
                "hamlet",
                3, 8, 4, 1, 12, 2, 2,
                Optional.of(new TownHallView.Construction(
                        new Identifier("villagepax", "norman/farm"), 1, 40, 196,
                        new ItemTally(Map.of(new Identifier("minecraft", "oak_fence"), 6)))),
                List.of(new TownHallView.BuildingLine(UUID.randomUUID(),
                        new Identifier("villagepax", "norman/town_hall"), 1,
                        BuildProgress.DONE, new BlockPos(10, 64, -20))),
                List.of(new TownHallView.CitizenLine(UUID.randomUUID(), "Rollo le Macon",
                        Optional.of(new Identifier("villagepax", "builder")), true,
                        Optional.of(new Identifier("villagepax", "norman/town_hall")),
                        Mood.CONTENT, false)),
                new ItemTally(Map.of(new Identifier("minecraft", "bread"), 4)),
                List.of(new Identifier("villagepax", "norman/house_lvl1")));
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
                0, 4, 0, 0, 0, 0, 0, Optional.empty(), List.of(), List.of(), new ItemTally(),
                List.of());

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
                "hamlet", 1, 4, 1, 0, 2, 1, 1, Optional.empty(), List.of(), List.of(),
                new ItemTally(Map.of(new Identifier("minecraft", "bread"), 2)), List.of());
        TownHallView same = new TownHallView("Бовуар", new Identifier("villagepax", "norman"),
                "hamlet", 1, 4, 1, 0, 2, 1, 1, Optional.empty(), List.of(), List.of(),
                new ItemTally(Map.of(new Identifier("minecraft", "bread"), 2)), List.of());

        assertEquals(one, same, "снимки с одинаковым содержимым обязаны быть равны");

        TownHallView other = new TownHallView("Бовуар", new Identifier("villagepax", "norman"),
                "hamlet", 1, 4, 1, 0, 2, 1, 1, Optional.empty(), List.of(), List.of(),
                new ItemTally(Map.of(new Identifier("minecraft", "bread"), 3)), List.of());

        assertTrue(!one.equals(other), "разный склад — разные снимки, иначе экран замрёт");
    }
}
