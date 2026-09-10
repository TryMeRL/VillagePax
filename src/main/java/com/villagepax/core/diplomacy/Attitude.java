package com.villagepax.core.diplomacy;

import com.villagepax.core.Named;

/**
 * Как один народ смотрит на другой.
 * <p>
 * Порогами, а не числом, — тем же приёмом, что и {@link com.villagepax.sim.Standing}
 * для доверия деревни к игроку: «настороженно» игрок понимает сразу, а
 * «минус десять» требует объяснения, которого в игре негде дать. Само число
 * при этом остаётся: по нему считается, как отзовётся у чужих поступок,
 * сделанный для своих.
 * <p>
 * Число задаёт <b>датапак</b>, в {@code diplomacy_defaults} культуры, и
 * задаёт его односторонне: у народа может быть свой счёт к соседу, о котором
 * сосед и не помнит. Симметрию мод не навязывает — это было бы неправдой
 * про историю.
 * <p>
 * <b>Чего в лестнице нет.</b> Ни войны, ни союза. Их нельзя получить ни из
 * данных, ни из игры: объявить войну пока нечем и незачем, а ступень, до
 * которой не доходит ни один путь, — это слово без содержания. Обе приедут
 * вместе с военной системой, и приедут со своими проверками.
 */
public enum Attitude implements Named {

    /** Вражда: этих здесь не ждут. */
    HOSTILE("hostile", -100),

    /** Настороженно: чужие боги, чужой камень. Дела ведут, но с оглядкой. */
    WARY("wary", -25),

    /** Ровно: друг другу никто, и счётов нет. */
    NEUTRAL("neutral", -8),

    /** Дружба: соседу рады и за него вступятся. */
    FRIENDLY("friendly", 25);

    private final String id;
    private final int from;

    Attitude(String id, int from) {
        this.id = id;
        this.from = from;
    }

    @Override
    public String id() {
        return id;
    }

    /** С какого числа начинается ступень. */
    public int from() {
        return from;
    }

    /** Ключ локализации: этими словами отношение называют игроку. */
    public String displayKey() {
        return "villagepax.attitude." + id;
    }

    /** Ступень по числу. Ниже самой низкой не бывает: всё дно — вражда. */
    public static Attitude of(int value) {
        Attitude reached = HOSTILE;
        for (Attitude candidate : values()) {
            if (value >= candidate.from) {
                reached = candidate;
            }
        }
        return reached;
    }
}
