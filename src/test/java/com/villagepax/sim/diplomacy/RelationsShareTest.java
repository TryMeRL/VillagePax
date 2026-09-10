package com.villagepax.sim.diplomacy;

import com.villagepax.core.diplomacy.Attitude;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Эхо поступка — арифметика, и её видно.
 * <p>
 * Вся дипломатия мода держится на одной чистой функции: сколько от доброго
 * или дурного дела в одной деревне достанется третьей. Проверяется она без
 * запущенной игры, и это не роскошь — правило «своим четверть, чужим по их
 * взгляду» иначе пришлось бы проверять игровым тестом на трёх деревнях,
 * а он сказал бы «не сошлось», не сказав, где.
 */
class RelationsShareTest {

    /** Своим достаётся четверть: заметно, но знакомства не отменяет. */
    @Test
    void kinHearOfIt() {
        assertEquals(5, Relations.share(20, true, 0),
                "квест на двадцать очков должен дать соседям по народу пять");
    }

    /**
     * Чужим — по их собственному взгляду на этот народ. Настороженность
     * в минус десять означает, что услуга сопернику слегка не по нраву.
     */
    @Test
    void rivalsTakeItPersonally() {
        assertEquals(-2, Relations.share(20, false, -10));
        assertEquals(6, Relations.share(20, false, 30),
                "народ, дружащий с осчастливленным, должен и игрока зачесть");
    }

    /**
     * Знак разворачивается сам, и это несущее свойство формулы: ограбить
     * обоз народа, на который сосед косится, — в глазах соседа не
     * преступление. Отдельного правила для дурных дел не нужно.
     */
    @Test
    void badDeedsEchoBothWays() {
        assertEquals(-6, Relations.share(-25, true, 0),
                "свои ограбленной деревни обязаны обидеться");
        assertTrue(Relations.share(-25, false, -10) > 0,
                "тот, кто на этот народ косится, чужой беде не огорчится");
    }

    /**
     * Мелочь не доходит вовсе — и это правильно: о каждой купленной булке
     * соседям не рассказывают. Держится на целочисленном делении, поэтому
     * и проверяется: замена на округление сломала бы ровно это.
     */
    @Test
    void smallDeedsStayHome() {
        assertEquals(0, Relations.share(1, true, 0));
        assertEquals(0, Relations.share(3, false, -10));
    }

    /** Ступени отношения народов: словами, а не числом в лицо. */
    @Test
    void attitudeLadderNamesTheNumber() {
        assertEquals(Attitude.NEUTRAL, Attitude.of(0));
        assertEquals(Attitude.NEUTRAL, Attitude.of(-5));
        assertEquals(Attitude.WARY, Attitude.of(-10),
                "минус десять из датапака — это «настороженно», а не вражда");
        assertEquals(Attitude.HOSTILE, Attitude.of(-80));
        assertEquals(Attitude.FRIENDLY, Attitude.of(40));
    }

    /** Дно у лестницы есть: ниже вражды не бывает. */
    @Test
    void nothingBelowHostility() {
        assertEquals(Attitude.HOSTILE, Attitude.of(Integer.MIN_VALUE / 2));
    }
}
