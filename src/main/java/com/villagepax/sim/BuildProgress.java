package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;

public enum BuildProgress implements Named {

    /** Место выбрано, заявка на материалы подана, билдер ещё не пришёл. */
    PLANNED("planned"),

    /** Билдер выкладывает схему блок за блоком. */
    BUILDING("building"),

    /** Здание достроено и работает. */
    DONE("done"),

    /**
     * Здание пострадало — от войны или от крипера. Чинится тем же билдером
     * по той же схеме. Здание никогда не исчезает: проигранная война должна
     * стоить дани и унижения, а не двухсот часов работы.
     */
    DAMAGED("damaged");

    public static final Codec<BuildProgress> CODEC = EnumCodecs.of(values(), "состояние стройки");

    private final String id;

    BuildProgress(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
