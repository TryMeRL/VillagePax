package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;

/**
 * Как житель себя чувствует — одним словом.
 * <p>
 * Игрок читает «голоден», а не «сытость 7»: числа внутренней механики не его
 * забота, а решение, которое он должен принять, — завезти еду или разобраться
 * с недовольством. Само слово подбирает клиент по ключу локализации, поэтому
 * сюда едет состояние, а не готовая строка.
 * <p>
 * Порядок в перечислении — порядок срочности: чем раньше, тем нужнее внимание.
 */
public enum Mood implements Named {

    /** Сытость на нуле: следующий суточный подсчёт начнёт отсчёт до уходa. */
    STARVING("starving"),

    /** Ниже порога голода: пойдёт есть в обед, если на складе есть еда. */
    HUNGRY("hungry"),

    /** Ест, но чем-то недоволен — например, ночует под открытым небом. */
    UNHAPPY("unhappy"),

    /** Всё в порядке. */
    CONTENT("content");

    public static final Codec<Mood> CODEC = EnumCodecs.of(values(), "настроение жителя");

    private final String id;

    Mood(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    /** Ключ локализации: слово подбирает клиент. */
    public String translationKey() {
        return "villagepax.mood." + id;
    }
}
