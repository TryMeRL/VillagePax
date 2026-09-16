package com.villagepax.core.profession;

import com.villagepax.sim.SettlementLevel;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Лестница ремёсел: с какой ступени какое дело доступно.
 * <p>
 * Проверяется без игры, потому что это правило сравнения, и ошибиться
 * в нём можно молча в обе стороны: запереть пахаря в только что
 * основанной колонии или отдать пивовара хутору даром.
 */
class ProfessionLadderTest {

    private static Profession at(SettlementLevel level) {
        return new Profession("villagepax.profession.brewer",
                new Identifier("villagepax", "craft"), true, 40, Optional.of("brewery"), level);
    }

    /** Своя ступень открывает ремесло. */
    @Test
    void ownRankOpensIt() {
        assertTrue(at(SettlementLevel.VILLAGE).openTo(SettlementLevel.VILLAGE));
    }

    /** Ступень выше — тем более. Выросшая колония ничего не теряет. */
    @Test
    void higherRankKeepsIt() {
        assertTrue(at(SettlementLevel.VILLAGE).openTo(SettlementLevel.TOWN));
        assertTrue(at(SettlementLevel.VILLAGE).openTo(SettlementLevel.CAPITAL));
    }

    /** А ниже — заперто. Это и есть цель, ради которой растут. */
    @Test
    void lowerRankIsLockedOut() {
        assertFalse(at(SettlementLevel.VILLAGE).openTo(SettlementLevel.HAMLET));
    }

    /**
     * Молчание датапака открывает ремесло сразу.
     * <p>
     * Умолчание выбрано безопасным нарочно: описка в поле не должна
     * запирать пахаря, без которого колония не живёт вовсе.
     */
    @Test
    void silenceMeansOpen() {
        assertTrue(at(SettlementLevel.HAMLET).openTo(SettlementLevel.HAMLET));
    }
}
