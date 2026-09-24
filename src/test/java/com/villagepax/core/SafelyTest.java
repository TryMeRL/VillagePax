package com.villagepax.core;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Оградка поселения: упавшее дело не роняет мир и не выключается навсегда.
 * <p>
 * Второе важнее, чем кажется. Оградка, которая после первой поломки
 * перестала бы звать дело вовсе, спрятала бы деревню навсегда из-за
 * ошибки одного дня — а деревня назавтра могла бы уже и не падать.
 */
class SafelyTest {

    @Test
    void aFailureDoesNotEscape() {
        assertDoesNotThrow(() -> Safely.run("деревня", "проба",
                () -> {
                    throw new IllegalStateException("сломано нарочно");
                }));
    }

    @Test
    void theWorkIsStillDoneAfterAFailure() {
        AtomicInteger tries = new AtomicInteger();
        for (int day = 0; day < 3; day++) {
            Safely.run("деревня", "повтор", () -> {
                if (tries.incrementAndGet() == 1) {
                    throw new IllegalStateException("только в первый раз");
                }
            });
        }
        assertEquals(3, tries.get(), "оградка перестала звать дело после первой поломки");
    }

    /** Ошибки, а не исключения, оградка не глотает: нехватку памяти лечить не ей. */
    @Test
    void errorsAreNotSwallowed() {
        org.junit.jupiter.api.Assertions.assertThrows(StackOverflowError.class,
                () -> Safely.run("деревня", "ошибка", () -> {
                    throw new StackOverflowError();
                }));
    }
}
