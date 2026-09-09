package com.villagepax.core.building;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Умолчания типа здания решают, что случится с недописанным датапаком.
 * <p>
 * И решают в <b>безопасную</b> сторону: молчание не наделяет здание
 * правами. Здание без роли — просто здание: строится и чинится, но
 * колонии уровня не даёт и мастерской никому не служит. Обратное
 * умолчание однажды сделало бы ратушей чей-нибудь сарай.
 */
class BuildingTypeCodecTest {

    private static BuildingType parse(String json) {
        DataResult<BuildingType> decoded = BuildingType.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        return decoded.result().orElseThrow(() -> new AssertionError("не прочиталось: "
                + decoded.error().map(Object::toString).orElse("?")));
    }

    @Test
    void silenceGrantsNothing() {
        BuildingType plain = parse("{\"display_name\": \"a.b\", \"role\": \"plain\"}");

        assertEquals(BuildingType.Role.PLAIN, plain.role(), "просто здание");
        assertFalse(plain.isTownHall(), "молчание не делает ратушей");
        assertFalse(plain.starting(), "молчание не делает начальным зданием");
        assertEquals(Optional.empty(), plain.profession());
        assertFalse(plain.employs(new Identifier("villagepax", "farmer")));
    }

    @Test
    void declaredRolesAreRead() {
        BuildingType hall = parse("{\"display_name\": \"a.b\", \"role\": \"town_hall\"}");
        assertTrue(hall.isTownHall());

        BuildingType farm = parse("{\"display_name\": \"a.b\", \"role\": \"workplace\","
                + " \"profession\": \"villagepax:farmer\", \"starting\": true}");
        assertTrue(farm.starting());
        assertTrue(farm.employs(new Identifier("villagepax", "farmer")));
        assertFalse(farm.employs(new Identifier("villagepax", "builder")),
                "мастерская служит названной профессии, а не любой");
    }

    /** Мастерская без профессии никому не служит: это описка датапака. */
    @Test
    void workplaceWithoutProfessionEmploysNobody() {
        BuildingType nobody = parse("{\"display_name\": \"a.b\", \"role\": \"workplace\"}");

        assertEquals(BuildingType.Role.WORKPLACE, nobody.role());
        assertFalse(nobody.employs(new Identifier("villagepax", "farmer")));
    }

    /**
     * Неизвестная роль — ошибка, а не молчаливая подстановка.
     * <p>
     * Ради этого роль и сделана обязательным полем: у необязательного
     * с умолчанием DFU глотает ошибку вложенного кодека, и описка
     * {@code "castel"} молча стала бы «просто зданием». Автор датапака
     * искал бы, почему его ратуша не ратуша.
     */
    @Test
    void unknownRoleIsRefused() {
        DataResult<BuildingType> decoded = BuildingType.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"display_name\": \"a.b\", \"role\": \"castle\"}"));

        assertTrue(decoded.error().isPresent(), "выдуманная роль обязана быть отвергнута");
    }

    /** И пропущенная роль — тоже ошибка: она решает права здания. */
    @Test
    void missingRoleIsRefused() {
        DataResult<BuildingType> decoded = BuildingType.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"display_name\": \"a.b\"}"));

        assertTrue(decoded.error().isPresent(), "тип без роли обязан быть отвергнут");
    }
}
