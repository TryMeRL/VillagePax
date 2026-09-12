package com.villagepax.sim.war;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Кривая наказания: сколько бойцов пошлёт деревня.
 * <p>
 * Единственное в набеге, что можно посчитать неправильно молча, — и потому
 * единственное, что проверяется без запущенной игры. Всё остальное в набеге
 * видно глазами: пришли, дрались, ушли.
 */
class RaidCurveTest {

    /** Пока терпят — никого не посылают. */
    @Test
    void patienceMeansNobodyComes() {
        assertEquals(0, Raids.fightersFor(0));
        assertEquals(0, Raids.fightersFor(50), "друг набегов не ждёт");
        assertEquals(0, Raids.fightersFor(Raids.PATIENCE_ENDS + 1),
                "на очко выше предела ещё терпят");
    }

    /** Ровно на пределе выходит один. */
    @Test
    void firstFighterAtTheEdge() {
        assertEquals(1, Raids.fightersFor(Raids.PATIENCE_ENDS));
        assertEquals(1, Raids.fightersFor(Raids.PATIENCE_ENDS - 19));
    }

    /** Дальше — по бойцу за каждые двадцать очков обиды. */
    @Test
    void deeperGrudgeSendsMore() {
        assertEquals(2, Raids.fightersFor(-60));
        assertEquals(3, Raids.fightersFor(-80));
        assertEquals(4, Raids.fightersFor(-100));
    }

    /**
     * И никогда больше предела. Без потолка разбойник со счётом в тысячу
     * получил бы отряд, который не пережить, — то есть наказание, из
     * которого нет выхода.
     */
    @Test
    void thereIsACeiling() {
        assertEquals(Raids.MOST_FIGHTERS, Raids.fightersFor(-1000));
    }
}
