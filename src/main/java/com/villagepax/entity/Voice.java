package com.villagepax.entity;

import java.util.Set;

/**
 * Высота голоса жителя — по тому, кто говорит.
 * <p>
 * Голос у всех один — деревенское «хм», и это решено давно: оно уже язык,
 * которому игрока учить не надо. Но до сих пор он был и одной высоты:
 * кузнец, его жена и их пятилетняя дочь звучали одним человеком. Высота —
 * то, по чему ухо узнаёт говорящего не глядя, и стоит она одного
 * множителя: ребёнок выше, женщина чуть выше мужчины, гном ниже всех,
 * эльф чуть звонче.
 * <p>
 * Слова — те же, что у примет на модели ({@link Looks#words}): народ,
 * пол и ремесло из имени облика. Своего описания голоса у датапака нет
 * и пока не нужно; незнакомый народ звучит по-человечески.
 */
public final class Voice {

    /** Во сколько раз ребёнок выше взрослого того же народа. */
    static final float CHILD = 1.35f;

    private Voice() {
    }

    /**
     * Множитель высоты к ванильному разбросу.
     *
     * @param words слова облика: народ, пол, ремесло
     * @param child ребёнок ли
     */
    public static float pitch(Set<String> words, boolean child) {
        float pitch = 1.0f;
        if (words.contains("female")) {
            pitch *= 1.12f;
        } else if (words.contains("male")) {
            pitch *= 0.94f;
        }
        if (words.contains("dwarf")) {
            pitch *= 0.84f;
        } else if (words.contains("elf")) {
            pitch *= 1.06f;
        }
        return child ? pitch * CHILD : pitch;
    }
}
