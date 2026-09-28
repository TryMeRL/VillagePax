package com.villagepax.sim.games;

import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * Сила руки: по ремеслу и годам.
 * <p>
 * Кто таскает брёвна и камень, тот и давит сильнее; купец и старейшина —
 * слабее. Старик — на ступень слабее своего ремесла. Ремесло чужого
 * датапака — средняя сила: о его работе мод ничего не знает.
 */
public final class Strength {

    private static final double STRONG = 0.8;
    private static final double STURDY = 0.6;
    private static final double COMMON = 0.4;

    /** Насколько слабее рука в старости. */
    private static final double YEARS = 0.2;

    private Strength() {
    }

    /** От нуля до единицы. */
    public static double of(Optional<Identifier> profession, boolean elderly) {
        double base = profession.map(Identifier::getPath).map(path -> switch (path) {
            case "builder", "guard", "lumberjack" -> STRONG;
            case "farmer", "courier", "brewer" -> STURDY;
            default -> COMMON;
        }).orElse(COMMON);
        return Math.max(0, base - (elderly ? YEARS : 0));
    }
}
