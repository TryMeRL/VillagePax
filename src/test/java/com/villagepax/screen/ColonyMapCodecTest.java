package com.villagepax.screen;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Карта колонии ездит по сети тем же кодеком, каким описана.
 * <p>
 * Потерянное поле здесь — это не отказ, а <b>молча пропавшая подпись</b>
 * или граница, нарисованная не там. Круговой прогон ловит именно такое:
 * ошибку, которую в игре пришлось бы искать глазами.
 */
class ColonyMapCodecTest {

    private static ColonyMap sample() {
        return new ColonyMap(UUID.randomUUID(), "Бовуар", new BlockPos(120, 68, -340), 2,
                List.of(
                        new ColonyMap.Sign(new Identifier("villagepax", "norman/town_hall"), 2,
                                new BlockPos(123, 76, -337), true),
                        new ColonyMap.Sign(new Identifier("villagepax", "norman/farm"), 1,
                                new BlockPos(140, 70, -330), false)));
    }

    @Test
    void roundTripsThroughJson() {
        ColonyMap map = sample();

        DataResult<com.google.gson.JsonElement> encoded =
                ColonyMap.CODEC.encodeStart(JsonOps.INSTANCE, map);
        DataResult<ColonyMap> decoded = ColonyMap.CODEC.parse(JsonOps.INSTANCE,
                encoded.result().orElseThrow(() -> new AssertionError("не закодировалось: "
                        + encoded.error().map(Object::toString).orElse("?"))));

        assertEquals(map, decoded.result().orElseThrow(() -> new AssertionError("не прочиталось: "
                + decoded.error().map(Object::toString).orElse("?"))));
    }

    /** По сети карта едет как NBT — тем же путём, что и снимок пульта. */
    @Test
    void roundTripsThroughNbt() {
        ColonyMap map = sample();

        DataResult<?> encoded = ColonyMap.CODEC.encodeStart(NbtOps.INSTANCE, map);
        NbtCompound nbt = assertInstanceOf(NbtCompound.class, encoded.result().orElseThrow(
                () -> new AssertionError("не закодировалось в NBT")));

        assertEquals(map, ColonyMap.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow(
                () -> new AssertionError("не прочиталось из NBT")));
    }

    /**
     * Колония без зданий — обычное дело: её только что основали. Подписей
     * нет, но карта обязана прочитаться, иначе граница не покажется как раз
     * тогда, когда игрок выбирает место.
     */
    @Test
    void survivesAColonyWithoutBuildings() {
        ColonyMap bare = new ColonyMap(UUID.randomUUID(), "Пустошь", BlockPos.ORIGIN, 1, List.of());

        DataResult<?> encoded = ColonyMap.CODEC.encodeStart(NbtOps.INSTANCE, bare);
        NbtCompound nbt = (NbtCompound) encoded.result().orElseThrow(
                () -> new AssertionError("пустая карта не закодировалась"));

        ColonyMap back = ColonyMap.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow(
                () -> new AssertionError("пустая карта не прочиталась"));

        assertTrue(back.signs().isEmpty());
        assertEquals(bare, back);
    }
}
