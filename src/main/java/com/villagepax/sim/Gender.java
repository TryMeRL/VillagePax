package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;

/** Нужен для выбора имени из списков культуры и для семей. */
public enum Gender implements Named {
    MALE("male"),
    FEMALE("female");

    public static final Codec<Gender> CODEC = EnumCodecs.of(values(), "пол");

    private final String id;

    Gender(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
