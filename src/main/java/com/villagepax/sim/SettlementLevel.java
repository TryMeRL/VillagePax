package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;

/**
 * Уровень поселения. Несущее решение по прогрессу: каждый уровень открывает
 * новую возможность в мире, а не просто увеличивает число.
 */
public enum SettlementLevel implements Named {

    /** Тебя ещё никто не замечает. */
    HAMLET("hamlet", 6, 2),

    /** Ты появляешься на дипломатической карте: приходят чужие караваны. */
    VILLAGE("village", 14, 3),

    /** Стража, стены, храм. Союзы и право требовать дань со слабых соседей. */
    TOWN("town", 28, 4),

    /** Поселения твоего народа признают сюзереном, чужие народы шлют посольства. */
    CAPITAL("capital", 64, 6);

    public static final Codec<SettlementLevel> CODEC = EnumCodecs.of(values(), "уровень поселения");

    private final String id;
    private final int maxCitizens;
    private final int claimRadiusChunks;

    SettlementLevel(String id, int maxCitizens, int claimRadiusChunks) {
        this.id = id;
        this.maxCitizens = maxCitizens;
        this.claimRadiusChunks = claimRadiusChunks;
    }

    @Override
    public String id() {
        return id;
    }

    public int maxCitizens() {
        return maxCitizens;
    }

    public int claimRadiusChunks() {
        return claimRadiusChunks;
    }

    public SettlementLevel next() {
        int index = ordinal() + 1;
        return index < values().length ? values()[index] : this;
    }

    public boolean isMax() {
        return ordinal() == values().length - 1;
    }
}
