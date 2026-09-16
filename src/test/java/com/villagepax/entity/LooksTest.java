package com.villagepax.entity;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Из чего складывается облик жителя.
 * <p>
 * Проверяется без игры, потому что это правило именования, а такое
 * ломается молча: путь соберётся, картинки не найдётся, и весь народ
 * молча оденется норманнами.
 */
class LooksTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");
    private static final Identifier MAYA = new Identifier("villagepax", "maya");
    private static final Identifier FARMER = new Identifier("villagepax", "farmer");

    @Test
    void craftIsPartOfTheLook() {
        assertEquals(new Identifier("villagepax", "textures/entity/citizen/norman/male_farmer.png"),
                Looks.of(NORMAN, Gender.MALE, Optional.of(FARMER)));
    }

    /** Без ремесла человек всё равно одет — в будничное своего народа. */
    @Test
    void plainFolkStillHaveALook() {
        assertEquals(new Identifier("villagepax", "textures/entity/citizen/maya/female.png"),
                Looks.of(MAYA, Gender.FEMALE, Optional.empty()));
    }

    /**
     * Чужой народ кладёт своих людей к себе.
     * <p>
     * Пространство имён берётся у культуры: датапак, объявивший гномов,
     * не обязан просить нас положить его текстуры в наш мод.
     */
    @Test
    void foreignPeopleKeepTheirNamespace() {
        Identifier dwarves = new Identifier("mypack", "dwarf");
        assertEquals(new Identifier("mypack", "textures/entity/citizen/dwarf/male.png"),
                Looks.of(dwarves, Gender.MALE, Optional.empty()));
    }

    /** Запись жителя знает о себе всё, что нужно для облика. */
    @Test
    void theRecordIsEnough() {
        Citizen citizen = Citizen.newborn("Rollo", "le Macon", NORMAN, Gender.MALE);
        citizen.setProfession(FARMER);
        assertEquals(Looks.of(NORMAN, Gender.MALE, Optional.of(FARMER)), Looks.of(citizen));
    }
}
