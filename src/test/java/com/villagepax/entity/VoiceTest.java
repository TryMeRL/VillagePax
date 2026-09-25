package com.villagepax.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Голоса различимы на слух: кто выше, кто ниже — и никто не за краем.
 */
class VoiceTest {

    private static float of(String... words) {
        return Voice.pitch(Set.of(words), false);
    }

    @Test
    void aWomanSpeaksHigherThanAMan() {
        assertTrue(of("norman", "female", "farmer") > of("norman", "male", "farmer"));
    }

    @Test
    void aChildSpeaksHigherThanItsParents() {
        Set<String> mother = Set.of("maya", "female", "weaver");
        assertTrue(Voice.pitch(mother, true) > Voice.pitch(mother, false));
    }

    @Test
    void aDwarfIsTheLowestVoiceInTheWorld() {
        float dwarf = of("dwarf", "male", "builder");
        for (String people : Set.of("norman", "maya", "elf", "pony")) {
            assertTrue(dwarf < of(people, "male", "builder"), people);
        }
    }

    /** Незнакомый народ из чужого датапака звучит по-человечески. */
    @Test
    void anUnknownPeopleSoundsHuman() {
        assertEquals(of("norman", "male"), of("strangers", "male"));
        assertEquals(1.0f, of());
    }

    /**
     * Ни один голос не уходит за пределы, в которых звук ещё звучит:
     * ваниль режет высоту до 0.5–2, и разброс ±0.1 ложится поверх.
     */
    @Test
    void everyVoiceStaysAudible() {
        for (String people : Set.of("norman", "maya", "dwarf", "elf", "pony")) {
            for (String gender : Set.of("male", "female")) {
                for (boolean child : new boolean[] {false, true}) {
                    float pitch = Voice.pitch(Set.of(people, gender), child);
                    assertTrue(pitch * 0.9f >= 0.5f && pitch * 1.1f <= 2.0f,
                            people + " " + gender + " " + child + ": " + pitch);
                }
            }
        }
    }
}
