package com.villagepax.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Телосложение народа считается из одного числа — и считается так,
 * как коротышек рисуют испокон веку.
 * <p>
 * Правило сюда вынесено ровно затем, чтобы его можно было проверить
 * без игры: в отрисовке оно невидимо, а ошибка в знаке превратила бы
 * гнома в жердь, и заметить это можно было бы только глазами и только
 * в мире.
 */
class StatureTest {

    /** Человеческий рост не считается вовсе: это то же самое тело. */
    @Test
    void plainFolkStayPlain() {
        assertSame(Stature.PLAIN, Stature.of(1.0f));
    }

    /**
     * Кто ниже — тот шире и головастее. Числа здесь не выведены из кода,
     * а названы руками: проверка не спрашивает ответа у проверяемого.
     */
    @Test
    void theShortAreStockyAndBigHeaded() {
        Stature dwarf = Stature.of(0.72f);

        assertEquals(0.72f, dwarf.height(), 0.0001f);
        // Недобор роста 0.28: в ширину уходит 0.8 от него, в голову 0.5.
        assertEquals(1.224f, dwarf.girth(), 0.0001f);
        assertEquals(1.14f, dwarf.head(), 0.0001f);
    }

    /** А кто выше — тоньше и мельче лицом. */
    @Test
    void theTallAreSlender() {
        Stature elf = Stature.of(1.06f);

        assertTrue(elf.girth() < 1.0f, "высокий народ должен быть тоньше: " + elf.girth());
        assertTrue(elf.head() < 1.0f, "и мельче лицом: " + elf.head());
    }

    /**
     * Негодное число даёт человека, а не исчезнувшее тело.
     * <p>
     * Ноль приходит из отслеживаемого поля в тот кадр, пока клиент ещё
     * не получил значение с сервера. Один такой кадр с нулевым ростом —
     * это житель, схлопнутый в точку.
     */
    @Test
    void nonsenseMakesAPerson() {
        assertSame(Stature.PLAIN, Stature.of(0.0f));
        assertSame(Stature.PLAIN, Stature.of(-3.0f));
        assertSame(Stature.PLAIN, Stature.of(Float.NaN));
    }
}
