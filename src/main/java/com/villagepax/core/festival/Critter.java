package com.villagepax.core.festival;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;

/**
 * Кого ловят в загоне — закрытый список ванильных зверей.
 * <p>
 * Список короткий не от скупости. Зверёк ловли — наш наследник ванильного
 * животного с одной целью «удирать по загону», и ванильные цели ему надо
 * снять. У козы вместо целей мозг ({@code Brain}), и снять там нечего:
 * она прыгала бы через борт загона по своей ванильной воле. Поэтому
 * северяне ловят зайцев, а не козлят.
 */
public enum Critter implements Named {

    PIG("pig"),

    CHICKEN("chicken"),

    FOX("fox"),

    RABBIT("rabbit"),

    SHEEP("sheep");

    public static final Codec<Critter> CODEC = EnumCodecs.of(values(), "зверёк ловли");

    private final String id;

    Critter(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
