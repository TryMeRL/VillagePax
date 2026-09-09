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
    STRANGER("stranger", 0),

    /** Знакомый: этого уже видели и помнят. */
    KNOWN("known", 20),

    /** Друг: ему отдадут и чертёж ратуши. */
    FRIEND("friend", 45),

    /** Почётный житель: выше не бывает. */
    HONOURED("honoured", 80);

    public static final Codec<Standing> CODEC = EnumCodecs.of(values(), "отношение");

    private final String id;
    private final int from;

    Standing(String id, int from) {
        this.id = id;
        this.from = from;
    }

    @Override
    public String id() {
        return id;
    }

    public int from() {
        return from;
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
