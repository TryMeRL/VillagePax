package com.villagepax.sim.diplomacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Цена подарка — тоже арифметика, и она решает, за сколько покупается честь.
 * <p>
 * Проверяется без игры, потому что проверяется именно правило, а не путь
 * предмета из руки в деревню: сколько дадут за такую цену и сколько при
 * этом возьмут. Второе не менее важно первого — молча забрать стопку железа
 * за те же восемь очков было бы обманом, и заметить такое можно только счётом.
 */
class GiftWorthTest {

    /** Шесть медяков — очко. Полсотни медяков — суточный предел. */
    @Test
    void trustFollowsWorth() {
        assertEquals(1, Gifts.trustFor(6));
        assertEquals(4, Gifts.trustFor(24));
        assertEquals(Gifts.MOST_PER_DAY, Gifts.trustFor(48));
    }

    /** Сколько ни принеси — за сутки не поблагодарят больше. */
    @Test
    void oneDayHasACeiling() {
        assertEquals(Gifts.MOST_PER_DAY, Gifts.trustFor(10_000),
                "иначе честь покупается за один сундук железа");
    }

    /**
     * Принятое — всегда хоть очко: подарок приняли, значит поблагодарили,
     * пусть и вежливым кивком. А ничего не стоящее — ничего и не стоит.
     */
    @Test
    void acceptedGiftIsNeverFree() {
        assertEquals(1, Gifts.trustFor(1));
        assertEquals(0, Gifts.trustFor(0));
        assertEquals(0, Gifts.trustFor(-5));
    }

    /**
     * Берут ровно столько, сколько нужно на суточную благодарность.
     * Железо у норманнов идёт четыре штуки за три медяка: на сорок восемь
     * медяков нужно шестьдесят четыре штуки — ровно стопка.
     */
    @Test
    void takeOnlyWhatIsNeeded() {
        assertEquals(64, Gifts.enoughOf(3, 4));
    }

    /** Золотая монета стоит восемьдесят один медяк: её довольно одной. */
    @Test
    void oneGoldCoinIsEnough() {
        assertEquals(1, Gifts.enoughOf(81, 1));
    }

    /**
     * Округление вверх, а не вниз: неполной сделки деревня не считает,
     * и «почти хватило» не должно превращаться в недостачу.
     */
    @Test
    void roundsUpNotDown() {
        int needed = Gifts.enoughOf(5, 1);
        assertTrue(needed * 5 >= Gifts.MOST_PER_DAY * Gifts.COPPER_PER_TRUST,
                "взятого должно хватать на полную благодарность, а взяли " + needed);
        assertEquals(10, needed);
    }
}
