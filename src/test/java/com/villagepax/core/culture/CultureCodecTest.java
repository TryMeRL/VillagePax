package com.villagepax.core.culture;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Кодек культуры — единственная точка входа контента в мод, поэтому он
 * проверяется отдельно: и на успешный разбор, и на внятный отказ.
 */
class CultureCodecTest {

    private static DataResult<Culture> parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        return Culture.CODEC.parse(JsonOps.INSTANCE, element);
    }

    @Test
    void parsesFullCulture() {
        DataResult<Culture> result = parse("""
                {
                  "display_name": "villagepax.culture.norman",
                  "kind": "historical",
                  "spawn": { "biomes": "#minecraft:is_forest", "weight": 10, "min_distance_chunks": 48 },
                  "name_pools": { "male": ["Rollo"], "settlement": ["Beauvoir"] },
                  "buildings": ["villagepax:norman/town_hall"],
                  "traits": ["villagepax:stone_masonry"],
                  "diplomacy_defaults": { "villagepax:dwarf": -20 }
                }
                """);

        Culture culture = result.result().orElseThrow(() ->
                new AssertionError("не разобралось: " + result.error().map(Object::toString).orElse("?")));

        assertEquals("villagepax.culture.norman", culture.displayName());
        assertEquals(CultureKind.HISTORICAL, culture.kind());
        assertEquals(48, culture.spawn().minDistanceChunks());
        assertEquals(List.of("Rollo"), culture.namePools().male());
        assertEquals(List.of(), culture.namePools().female());
        assertEquals(-20, culture.initialAttitudeTo(new Identifier("villagepax", "dwarf")));
    }

    @Test
    void appliesDefaultsWhenFieldsOmitted() {
        DataResult<Culture> result = parse("""
                {
                  "display_name": "villagepax.culture.spare",
                  "kind": "fantasy",
                  "spawn": { "biomes": "#minecraft:is_hill" }
                }
                """);

        Culture culture = result.result().orElseThrow();

        assertEquals(10, culture.spawn().weight(), "вес по умолчанию");
        assertEquals(48, culture.spawn().minDistanceChunks(), "расстояние по умолчанию");
        assertTrue(culture.namePools().isEmpty(), "пустые списки имён допустимы");
        assertTrue(culture.buildings().isEmpty());
        assertEquals(0, culture.initialAttitudeTo(new Identifier("villagepax", "maya")),
                "незнакомый народ нейтрален, а не враждебен");
    }

    /**
     * Сласть для гостинца за прятки — необязательна: без неё — печенье,
     * а народ из датапака вправе угощать своим.
     */
    @Test
    void aTreatIsOptional() {
        Culture plain = parse("""
                {
                  "display_name": "villagepax.culture.spare",
                  "kind": "fantasy",
                  "spawn": { "biomes": "#minecraft:is_hill" }
                }
                """).result().orElseThrow();
        Culture sweet = parse("""
                {
                  "display_name": "villagepax.culture.pony",
                  "kind": "fantasy",
                  "spawn": { "biomes": "#minecraft:is_hill" },
                  "treat": "villagepax:rainbow_cupcake"
                }
                """).result().orElseThrow();
        assertEquals(java.util.Optional.empty(), plain.treat());
        assertEquals(java.util.Optional.of(new Identifier("villagepax", "rainbow_cupcake")), sweet.treat());
    }

    @Test
    void rejectsUnknownKind() {
        DataResult<Culture> result = parse("""
                {
                  "display_name": "x",
                  "kind": "spacefaring",
                  "spawn": { "biomes": "#minecraft:is_forest" }
                }
                """);

        assertTrue(result.error().isPresent(), "неизвестный род культуры должен отвергаться");
    }

    @Test
    void rejectsMissingRequiredField() {
        DataResult<Culture> result = parse("""
                { "kind": "historical", "spawn": { "biomes": "#minecraft:is_forest" } }
                """);

        assertTrue(result.error().isPresent(), "display_name обязателен");
    }
}
