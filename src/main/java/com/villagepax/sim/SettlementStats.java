package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Показатели поселения.
 * <p>
 * Счастье — главный регулятор сложности всего мода: оно падает от нехватки еды,
 * плохого жилья, опасности, дальней дороги до работы и высоких налогов, роняет
 * производительность, а потом жители начинают уходить.
 * <p>
 * Престиж — валюта уровней, и начисляется он из обеих половин мода сразу:
 * за здания и счастье, но также за квесты чужих народов, артефакты и союзы.
 * Именно поэтому колонию нельзя вырастить в город, ни с кем не общаясь.
 * <p>
 * <b>Ставка налога стоит здесь же</b>, хотя её и не измеряют, а задают.
 * Причина не в тесноте кодека (хотя у поселения все шестнадцать полей
 * заняты): ставка бьёт ровно по соседнему числу — по счастью, — и держать
 * их порознь значило бы прятать эту связь. Она единственная из всех,
 * которую ставит игрок.
 */
public record SettlementStats(int food, int happiness, int safety, int prestige,
                              int taxRate) {

    public static final int MAX_HAPPINESS = 100;

    /** Выше этого ставку не поднять: сто — это «работают даром». */
    public static final int MAX_TAX = 100;

    public static final SettlementStats INITIAL = new SettlementStats(0, 70, 50, 0, 0);

    public static final Codec<SettlementStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("food", 0).forGetter(SettlementStats::food),
            Codec.INT.optionalFieldOf("happiness", 70).forGetter(SettlementStats::happiness),
            Codec.INT.optionalFieldOf("safety", 50).forGetter(SettlementStats::safety),
            Codec.INT.optionalFieldOf("prestige", 0).forGetter(SettlementStats::prestige),
            // Ноль по умолчанию, и это решение, а не умолчание от лени:
            // колония, в которую игрок не заглядывал, не должна собирать
            // с жителей ничего. Налог обязан быть его выбором.
            Codec.INT.optionalFieldOf("tax_rate", 0).forGetter(SettlementStats::taxRate)
    ).apply(instance, SettlementStats::new));

    public SettlementStats(int food, int happiness, int safety, int prestige) {
        this(food, happiness, safety, prestige, 0);
    }

    public SettlementStats {
        happiness = clamp(happiness);
        safety = clamp(safety);
        prestige = Math.max(0, prestige);
        food = Math.max(0, food);
        taxRate = Math.max(0, Math.min(MAX_TAX, taxRate));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_HAPPINESS, value));
    }

    public SettlementStats withHappiness(int value) {
        return new SettlementStats(food, value, safety, prestige, taxRate);
    }

    public SettlementStats withPrestige(int value) {
        return new SettlementStats(food, happiness, safety, value, taxRate);
    }

    public SettlementStats withTaxRate(int value) {
        return new SettlementStats(food, happiness, safety, prestige, value);
    }

    public SettlementStats addPrestige(int delta) {
        return withPrestige(prestige + delta);
    }
}
