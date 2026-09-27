package com.villagepax.core.festival;

import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;
import net.minecraft.util.StringIdentifiable;

/**
 * Какие вещицы прячут на поиске — закрытый список обликов одного блока.
 * <p>
 * Блок один на всех, а облик — свойство: вещица живёт минуту, в руки
 * не даётся и в инвентарь не попадает, и семь отдельных блоков с семью
 * предметами были бы семью лишними строками во вкладке. Народ выбирает
 * облик своих вещиц словом в данных.
 */
public enum TokenKind implements Named, StringIdentifiable {

    /** Крашеное яйцо — норманны. */
    EGG("egg"),

    /** Нефритовая фигурка — майя. */
    JADE("jade"),

    /** Омамори, амулет в мешочке, — ямато. */
    OMAMORI("omamori"),

    /** Камешек с руной — северяне. */
    RUNE("rune"),

    /** Подкова — пони. */
    HORSESHOE("horseshoe"),

    /** Самоцвет — гномы. */
    GEM("gem"),

    /** Светлячок — эльфы. */
    FIREFLY("firefly");

    // Имя полностью: StringIdentifiable приносит свой вложенный Codec,
    // и короткое имя указывало бы на него.
    public static final com.mojang.serialization.Codec<TokenKind> CODEC =
            EnumCodecs.of(values(), "вещица поиска");

    private final String id;

    TokenKind(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    /** Имя свойства блока вещицы — то же слово, что в данных народа. */
    @Override
    public String asString() {
        return id;
    }
}
