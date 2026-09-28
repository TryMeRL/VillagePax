package com.villagepax.sim.games;

import com.villagepax.core.Named;

/**
 * По какому поводу житель говорит за игрой.
 * <p>
 * Имя случая — часть ключа словаря: {@code villagepax.games.say.<случай>…}.
 * Порядок не значим; новый случай — новая строка здесь и пул фраз в словаре.
 */
public enum Say implements Named {
    ACCEPT("accept"),
    BUSY("busy"),
    ASLEEP("asleep"),
    FESTIVAL("festival"),
    PLAYING("playing"),
    BROKE("broke"),
    SULK("sulk"),
    PIOUS("pious"),
    FULL("full"),
    ROLL_HIGH("roll_high"),
    ROLL_LOW("roll_low"),
    BUST("bust"),
    OCHKO("ochko"),
    WIN("win"),
    LOSE("lose"),
    PUSH("push"),
    STRAIN("strain"),
    REMATCH("rematch"),
    BOAST("boast"),
    AMBIENT_THROW("ambient_throw"),
    AMBIENT_REPLY("ambient_reply"),
    HIDE_INVITE("hide_invite"),
    HIDE_NOWHERE("hide_nowhere"),
    HIDE_FOUND("hide_found"),
    HIDE_LOST("hide_lost"),
    HIDE_THANKS("hide_thanks"),
    THANKS("thanks");

    private final String id;

    Say(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}
