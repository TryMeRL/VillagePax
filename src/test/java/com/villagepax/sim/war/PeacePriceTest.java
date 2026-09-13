package com.villagepax.sim.war;

import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Цена мира и счёт дней тишины.
 * <p>
 * Проверяется без игры по той же причине, что и кривая набега: это
 * арифметика, которую можно испортить молча. Всё остальное в откупе видно
 * глазами — подошёл, заплатил, отряд ушёл.
 */
class PeacePriceTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    private static Settlement village() {
        return Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", BlockPos.ORIGIN);
    }

    /** Мира не продают тому, с кем не воюют: платить не за что. */
    @Test
    void peaceIsFreeWhenNobodyIsComing() {
        assertEquals(0, Peace.price(0));
        assertEquals(0, Peace.price(Raids.PATIENCE_ENDS + 1));
    }

    /** Цена — это плата за тех, кто иначе придёт: по бойцу за цену бойца. */
    @Test
    void priceIsTheCostOfTheBand() {
        assertEquals(Peace.COIN_PER_FIGHTER, Peace.price(Raids.PATIENCE_ENDS));
        assertEquals(2 * Peace.COIN_PER_FIGHTER, Peace.price(-60));
        assertEquals(Raids.MOST_FIGHTERS * Peace.COIN_PER_FIGHTER, Peace.price(-1000));
    }

    /** Перемирие кончается ровно в тот день, до которого куплено. */
    @Test
    void truceRunsOutOnItsDay() {
        Settlement village = village();
        assertFalse(village.atTruce(5L), "перемирия не было — деревня свободна");

        village.restFor(5L, 10);
        assertTrue(village.atTruce(5L));
        assertEquals(10, village.truceDaysLeft(5L));
        assertTrue(village.atTruce(14L), "последний день тишины ещё тихий");
        assertFalse(village.atTruce(15L), "а назавтра снова можно воевать");
    }

    /**
     * Второй павший добавляет дней, а не отсчитывает их заново.
     * <p>
     * Иначе отряд из четверых стоил бы деревне ровно столько же, сколько
     * один убитый, и оборона снова не значила бы ничего.
     */
    @Test
    void everyLossAddsToTheQuiet() {
        Settlement village = village();
        village.restFor(5L, Peace.MOURNING_DAYS);
        village.restFor(5L, Peace.MOURNING_DAYS);
        assertEquals(2 * Peace.MOURNING_DAYS, village.truceDaysLeft(5L));
    }
}
