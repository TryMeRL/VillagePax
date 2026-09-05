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
 */
public record SettlementStats(int food, int happiness, int safety, int prestige) {

    public static final int MAX_HAPPINESS = 100;

    public static final SettlementStats INITIAL = new SettlementStats(0, 70, 50, 0);

    public static final Codec<SettlementStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("food", 0).forGetter(SettlementStats::food),
            Codec.INT.optionalFieldOf("happiness", 70).forGetter(SettlementStats::happiness),
            Codec.INT.optionalFieldOf("safety", 50).forGetter(SettlementStats::safety),
            Codec.INT.optionalFieldOf("prestige", 0).forGetter(SettlementStats::prestige)
    ).apply(instance, SettlementStats::new));

    public SettlementStats {
        happiness = clamp(happiness);
        safety = clamp(safety);
        prestige = Math.max(0, prestige);
        food = Math.max(0, food);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_HAPPINESS, value));
    }

    public SettlementStats withHappiness(int value) {
        return new SettlementStats(food, value, safety, prestige);
    }

    public SettlementStats withPrestige(int value) {
        return new SettlementStats(food, happiness, safety, value);
    }

    public SettlementStats addPrestige(int delta) {
        return withPrestige(prestige + delta);
    }
}
