package com.villagepax.entity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Как читается условие приметы в имени кости.
 * <p>
 * Правило одно на клиент и на проверки, и ломается оно молча: примета,
 * чьё условие прочитано не так, просто не покажется — или покажется всем.
 */
class MarksTest {

    private static final Set<String> DWARF_MAN = Set.of("dwarf", "male", "adult");
    private static final Set<String> DWARF_BOY = Set.of("dwarf", "male", "child");
    private static final Set<String> ELF_ELDER = Set.of("elf", "female", "elder", "adult");

    @Test
    void aBoneWithoutConditionIsNotAMark() {
        assertTrue(Marks.of("head").isEmpty());
    }

    @Test
    void everyWordMustMatch() {
        Marks beard = Marks.of("beard@dwarf+male+adult").orElseThrow();
        assertTrue(beard.fits(DWARF_MAN));
        assertFalse(beard.fits(DWARF_BOY), "у мальчика бороды нет");
        assertFalse(beard.fits(ELF_ELDER));
    }

    @Test
    void aNegatedWordMustNotMatch() {
        Marks skirt = Marks.of("skirt@female+!elder").orElseThrow();
        assertFalse(skirt.fits(ELF_ELDER), "у старейшины ряса, а не юбка");
        assertTrue(skirt.fits(Set.of("elf", "female", "adult")));
    }

    @Test
    void oneOfTheOptionsIsEnough() {
        Marks bags = Marks.of("saddlebags@courier|merchant").orElseThrow();
        assertTrue(bags.fits(Set.of("pony", "male", "merchant")));
        assertTrue(bags.fits(Set.of("pony", "female", "courier")));
        assertFalse(bags.fits(Set.of("pony", "male", "farmer")));
    }

    /** Пустое условие — не запрет: кость видна всем. */
    @Test
    void anEmptyConditionShowsTheBoneToEveryone() {
        assertTrue(Marks.of("hat@").orElseThrow().fits(Set.of()));
    }

    @Test
    void theWordsAreListedForTheDatapackCheck() {
        assertEquals(List.of("female", "elder"),
                Marks.of("skirt@female+!elder").orElseThrow().words());
    }
}
