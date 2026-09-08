package com.villagepax.core.profession;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Профессия — данные, и её кодек проверяется так же, как кодек культуры:
 * это вторая точка входа контента в мод.
 * <p>
 * Отдельно проверяется поле {@code workplace}: без него фермер искал бы
 * здание «фермер», которого нет ни в одной культуре.
 */
class ProfessionTest {

    private static final Identifier FARMER = new Identifier("villagepax", "farmer");
    private static final Identifier LUMBERJACK = new Identifier("villagepax", "lumberjack");

    private static DataResult<Profession> parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        return Profession.CODEC.parse(JsonOps.INSTANCE, element);
    }

    @Test
    void parsesFullProfession() {
        DataResult<Profession> result = parse("""
                {
                  "display_name": "villagepax.profession.farmer",
                  "job": "villagepax:farm",
                  "needs_workplace": true,
                  "hiring_priority": 70,
                  "workplace": "farm"
                }
                """);

        Profession farmer = result.result().orElseThrow(() ->
                new AssertionError("не разобралось: "
                        + result.error().map(Object::toString).orElse("?")));

        assertEquals("villagepax.profession.farmer", farmer.displayName());
        assertEquals(new Identifier("villagepax", "farm"), farmer.job());
        assertTrue(farmer.needsWorkplace());
        assertEquals(70, farmer.hiringPriority());
        assertEquals("farm", farmer.workplaceOf(FARMER));
    }

    @Test
    void appliesDefaultsWhenFieldsOmitted() {
        DataResult<Profession> result = parse("""
                { "display_name": "x", "job": "villagepax:haul" }
                """);

        Profession courier = result.result().orElseThrow();

        assertFalse(courier.needsWorkplace(), "мастерская нужна не всем");
        assertEquals(0, courier.hiringPriority(), "по умолчанию не нужнее прочих");
        assertTrue(courier.workplace().isEmpty());
    }

    /**
     * Совпадающие имена в датапаке писать не нужно: лесоруб работает
     * в «lumberjack», и это выводится само.
     */
    @Test
    void workplaceFallsBackToTheProfessionName() {
        Profession woodsman = parse("""
                { "display_name": "x", "job": "villagepax:gather", "needs_workplace": true }
                """).result().orElseThrow();

        assertEquals("lumberjack", woodsman.workplaceOf(LUMBERJACK));
    }

    /**
     * А несовпадающие — обязательно: фермер работает на ферме, и без этого
     * поля он остался бы без рабочего места навсегда.
     */
    @Test
    void declaredWorkplaceWinsOverTheProfessionName() {
        Profession farmer = parse("""
                {
                  "display_name": "x",
                  "job": "villagepax:farm",
                  "needs_workplace": true,
                  "workplace": "farm"
                }
                """).result().orElseThrow();

        assertEquals("farm", farmer.workplaceOf(FARMER));
        assertFalse(farmer.workplaceOf(FARMER).equals(FARMER.getPath()),
                "иначе поле ничего не меняло бы");
    }

    @Test
    void rejectsMissingJob() {
        assertTrue(parse("""
                { "display_name": "x" }
                """).error().isPresent(), "профессия без логики работы бессмысленна");
    }

    @Test
    void rejectsMalformedJobId() {
        assertTrue(parse("""
                { "display_name": "x", "job": "ЛОГИКА" }
                """).error().isPresent(), "идентификатор с заглавными буквами недопустим");
    }
}
