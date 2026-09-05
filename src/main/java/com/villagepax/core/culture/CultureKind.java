package com.villagepax.core.culture;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;

/**
 * Род культуры. На геймплей влияет мало, но задаёт ожидания по арту
 * и позволяет фильтровать народы в настройках мира.
 */
public enum CultureKind implements Named {
    HISTORICAL("historical"),
    FANTASY("fantasy"),
    BIOME("biome");

    public static final Codec<CultureKind> CODEC = EnumCodecs.of(values(), "род культуры");

    private final String id;

    CultureKind(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
