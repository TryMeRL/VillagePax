package com.villagepax.sim.faith;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Лестница благосклонности: пороги, порядок и то, что открывает каждая ступень.
 * <p>
 * Проверяется без игры, потому что это чистая арифметика — и потому что
 * ошибиться в ней легче всего молча. Ступень решает права: с неё просят
 * благословение, с неё чудо, с неё вручают артефакт. Сдвинь порог на
 * единицу — и половина пантеона откроется на день раньше или не откроется
 * никогда, а увидеть это в игре можно только на пятидесятый день.
 * <p>
 * Числа здесь написаны <b>руками</b>, а не взяты у {@code Tier.from()}.
 * Взятые из кода, они меняются вместе с кодом, и проверка согласилась бы
 * с любым значением. Сколько стоит ступень — решение по игре, и пинать
 * его должна проверка, а не наоборот.
 */
class FaithTierTest {

    @Test
    void theLadderStandsWhereItWasDesigned() {
        assertEquals(0, Faith.Tier.UNKNOWN.from());
        assertEquals(40, Faith.Tier.NOTICED.from());
        assertEquals(120, Faith.Tier.HEARD.from());
        assertEquals(250, Faith.Tier.KEPT.from());
        assertEquals(450, Faith.Tier.CHOSEN.from());
    }

    /**
     * Порог — <b>нижняя</b> граница, как у доверия деревни.
     * <p>
     * Ровно на пороге ступень уже достигнута: «сорок очков» и «сорок одно»
     * не должны означать разного. На единицу ниже — ещё нет.
     */
    @Test
    void theThresholdItselfCounts() {
        assertEquals(Faith.Tier.UNKNOWN, Faith.tierOf(39));
        assertEquals(Faith.Tier.NOTICED, Faith.tierOf(40));
        assertEquals(Faith.Tier.NOTICED, Faith.tierOf(119));
        assertEquals(Faith.Tier.HEARD, Faith.tierOf(120));
        assertEquals(Faith.Tier.KEPT, Faith.tierOf(250));
        assertEquals(Faith.Tier.CHOSEN, Faith.tierOf(450));
        assertEquals(Faith.Tier.CHOSEN, Faith.tierOf(10_000));
    }

    /**
     * Отрицательных очков быть не должно, но если они откуда-то возьмутся —
     * это «не знает», а не падение.
     */
    @Test
    void nothingBelowTheBottom() {
        assertEquals(Faith.Tier.UNKNOWN, Faith.tierOf(0));
        assertEquals(Faith.Tier.UNKNOWN, Faith.tierOf(-5));
    }

    /**
     * Сравнение ступеней опирается на порядок объявления, и порядок этот
     * несущий: {@code reached} — единственный способ спросить «достаточно ли
     * высоко», и он есть в каждом правиле веры.
     */
    @Test
    void higherTiersReachLowerOnes() {
        assertTrue(Faith.Tier.CHOSEN.reached(Faith.Tier.KEPT));
        assertTrue(Faith.Tier.KEPT.reached(Faith.Tier.HEARD));
        assertTrue(Faith.Tier.HEARD.reached(Faith.Tier.HEARD));
        assertFalse(Faith.Tier.NOTICED.reached(Faith.Tier.HEARD));
        assertFalse(Faith.Tier.UNKNOWN.reached(Faith.Tier.NOTICED));
    }

    /**
     * У высшей ступени следующей нет, и пульт на это опирается: полосу
     * «сколько до следующей» он рисует только когда расти есть куда.
     */
    @Test
    void theTopHasNoNextRung() {
        assertEquals(Optional.of(Faith.Tier.NOTICED), Faith.Tier.UNKNOWN.next());
        assertEquals(Optional.of(Faith.Tier.CHOSEN), Faith.Tier.KEPT.next());
        assertEquals(Optional.empty(), Faith.Tier.CHOSEN.next());
    }

    /**
     * Лестница обязана идти вверх без провалов.
     * <p>
     * Проверка от опечатки в самом списке: переставленные местами пороги
     * сделали бы {@code tierOf} бессмысленным, а все остальные проверки
     * этого файла по-прежнему проходили бы, если бы сверяли только пары.
     */
    @Test
    void theLadderOnlyGoesUp() {
        Faith.Tier[] rungs = Faith.Tier.values();
        for (int at = 1; at < rungs.length; at++) {
            assertTrue(rungs[at].from() > rungs[at - 1].from(),
                    "ступень " + rungs[at].id() + " не выше предыдущей");
        }
    }

    /**
     * Цена благословения и чуда должна быть посильна с той ступени,
     * с которой их разрешают просить.
     * <p>
     * Иначе право открывается, а воспользоваться им нельзя: кнопка
     * есть, ответ всегда «не хватает». Это ровно та беда, за которую
     * мод уже расплачивался ступенью колонии без схемы ратуши.
     */
    @Test
    void whatIsAllowedIsAlsoAffordable() {
        assertTrue(Faith.BLESSING_COST <= Faith.Tier.NOTICED.from(),
                "благословение дороже, чем ступень, с которой его разрешают");
        assertTrue(Faith.MIRACLE_COST <= Faith.Tier.HEARD.from(),
                "чудо дороже, чем ступень, с которой его разрешают");
    }
}
