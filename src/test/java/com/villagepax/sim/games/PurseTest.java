package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Кошелёк и сила — решение по игре, и числа здесь написаны руками. */
class PurseTest {

    private static Optional<Identifier> craft(String path) {
        return Optional.of(new Identifier("villagepax", path));
    }

    @Test
    void aMerchantCarriesTheMost() {
        assertEquals(18, Purse.of(craft("merchant"), Nature.EVEN));
        assertEquals(8, Purse.of(craft("elder"), Nature.EVEN));
        assertEquals(4, Purse.of(craft("farmer"), Nature.EVEN));
        assertEquals(4, Purse.of(Optional.empty(), Nature.EVEN));
    }

    @Test
    void natureScalesThePurse() {
        assertEquals(27, Purse.of(craft("merchant"), Nature.AMBITIOUS));
        assertEquals(6, Purse.of(craft("farmer"), Nature.AMBITIOUS));
        assertEquals(2, Purse.of(craft("farmer"), Nature.COWARD));
        assertEquals(4, Purse.of(craft("elder"), Nature.COWARD));
    }

    /** Ставка — не больше половины остатка: «очко» платится вдвойне, и платить есть из чего. */
    @Test
    void stakesFitHalfThePurse() {
        assertEquals(List.of(1, 2), Purse.allowed(4, 100));
        assertEquals(List.of(1, 2, 5, 9), Purse.allowed(18, 100));
        assertEquals(List.of(), Purse.allowed(1, 100));
    }

    /** И ставки, которой у игрока нет, окно не даёт выбрать. */
    @Test
    void stakesFitThePlayersCoins() {
        assertEquals(List.of(1, 2, 5), Purse.allowed(18, 5));
        assertEquals(List.of(), Purse.allowed(18, 0));
    }

    @Test
    void strengthComesFromTheCraftAndTheYears() {
        assertEquals(0.8, Strength.of(craft("builder"), false), 1e-9);
        assertEquals(0.8, Strength.of(craft("guard"), false), 1e-9);
        assertEquals(0.6, Strength.of(craft("courier"), false), 1e-9);
        assertEquals(0.4, Strength.of(craft("merchant"), false), 1e-9);
        assertEquals(0.4, Strength.of(Optional.empty(), false), 1e-9);
        assertEquals(0.6, Strength.of(craft("lumberjack"), true), 1e-9);
    }
}
