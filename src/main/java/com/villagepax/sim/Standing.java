package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;

/**
 * Как деревня относится к игроку.
 * <p>
 * Порогами, а не числом в лицо: «друг» игрок понимает сразу, а «сорок семь
 * очков доверия» требует объяснения, которого в игре негде дать. Само число
 * при этом остаётся — по нему считаются пороги квестов, и его видно в пульте.
 */
public enum Standing implements Named {

    /** Чужак: с ним говорят, но дела ему не доверяют. */
    STRANGER("stranger", 0, 150, 50),

    /** Знакомый: этого уже видели и помнят. */
    KNOWN("known", 20, 125, 75),

    /** Друг: ему отдадут и чертёж ратуши, и товар по своей цене. */
    FRIEND("friend", 45, 100, 100),

    /** Почётный житель: выше не бывает, и торгуют с ним лучше, чем со своими. */
    HONOURED("honoured", 80, 75, 125);

    public static final Codec<Standing> CODEC = EnumCodecs.of(values(), "отношение");

    private final String id;
    private final int from;
    private final int buyPercent;
    private final int sellPercent;

    Standing(String id, int from, int buyPercent, int sellPercent) {
        this.id = id;
        this.from = from;
        this.buyPercent = buyPercent;
        this.sellPercent = sellPercent;
    }

    @Override
    public String id() {
        return id;
    }

    public int from() {
        return from;
    }

    /**
     * Сколько процентов цены платит игрок, покупая.
     * <p>
     * Наценка живёт здесь, а не в торге, потому что это свойство
     * <b>отношения</b>, а не сделки: у ступеней доверия и без того есть
     * названия и пороги, и разносить их по двум местам значило бы однажды
     * добавить ступень без цены.
     */
    public int buyPercent() {
        return buyPercent;
    }

    /** Сколько процентов цены получает игрок, продавая. */
    public int sellPercent() {
        return sellPercent;
    }

    /** Ключ локализации: этими словами отношение называют игроку. */
    public String displayKey() {
        return "villagepax.standing." + id;
    }

    /**
     * Отношение по числу доверия. Отрицательное доверие остаётся чужаком:
     * вражда — это отдельная механика, а не «минус знакомство».
     */
    public static Standing of(int reputation) {
        Standing reached = STRANGER;
        for (Standing standing : values()) {
            if (reputation >= standing.from) {
                reached = standing;
            }
        }
        return reached;
    }
}
