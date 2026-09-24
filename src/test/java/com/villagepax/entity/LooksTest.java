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
     * По облику видно, чей это народ.
     * <p>
     * Единственный канал, по которому клиент узнаёт народ: культуры живут
     * в датапаке сервера. По нему же выбирается тело — конь у пони,
     * человек у всех прочих, — и ошибка здесь стоила бы пони четырёх ног.
     */
    @Test
    void theLookNamesThePeople() {
        assertEquals(java.util.Optional.of("pony"), Looks.cultureOf(
                "villagepax:textures/entity/citizen/pony/female_farmer.png"));
        assertEquals(java.util.Optional.of("norman"), Looks.cultureOf(
                Looks.of(NORMAN, Gender.MALE, Optional.empty()).toString()));
        assertEquals(java.util.Optional.empty(), Looks.cultureOf("villagepax:textures/gui/icon.png"));
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

    /**
     * Слова облика — народ, пол и ремесло — для примет модели.
     * <p>
     * Ремесло с подчёркиванием остаётся одним словом: пол подчёркиваний
     * не содержит, и делить надо по первому.
     */
    @Test
    void theLookSpellsOutWhoThisIs() {
        assertEquals(java.util.Set.of("maya", "female", "farmer"),
                Looks.words("villagepax:textures/entity/citizen/maya/female_farmer.png"));
        assertEquals(java.util.Set.of("dwarf", "male"),
                Looks.words(Looks.of(new Identifier("villagepax", "dwarf"), Gender.MALE,
                        Optional.empty()).toString()));
        assertEquals(java.util.Set.of("elf", "male", "stone_mason"),
                Looks.words("mypack:textures/entity/citizen/elf/male_stone_mason.png"));
        assertEquals(java.util.Set.of(), Looks.words("villagepax:textures/gui/icon.png"));
    }
}
