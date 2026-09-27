package com.villagepax.core.festival;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;

/**
 * Вид праздничного состязания — закрытый список, как черты народа и домены богов.
 * <p>
 * Датапак выбирает, во что играют на его ярмарке, но не приносит своей игры:
 * состязание — это поведение (где прятать, от кого бежать, как считать очки),
 * а поведение живёт в коде. Народ без своих состязаний берёт эти три
 * и называет их по-своему.
 */
public enum ContestKind implements Named {

    /** Поиск: за минуту найти вещицы, спрятанные по деревне. */
    HUNT("hunt"),

    /** Ловля: в загоне носятся зверьки, лови руками. */
    CHASE("chase"),

    /** Стрельба: три мишени на разном расстоянии, восемь выстрелов. */
    ARCHERY("archery");

    public static final Codec<ContestKind> CODEC = EnumCodecs.of(values(), "вид состязания");

    private final String id;

    ContestKind(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
