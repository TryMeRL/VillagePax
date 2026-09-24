package com.villagepax.core;

import com.villagepax.VillagePax;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Дело одного поселения, которое не роняет мир.
 * <p>
 * Всё, что мод делает каждый тик, — решения жителей, смена дня, обозы,
 * набеги, карта колонии, появление тел, — зовётся из событий сервера.
 * Исключение в любом из них без оградки роняет тик мира, а значит
 * и сервер, — вместе с миром игрока, который потом не открыть, пока
 * жив сломанный житель или битая деревня. Цена ошибки в одном поселении
 * не должна быть целым миром.
 * <p>
 * Оградка ставится <b>на одно поселение</b>, а не на весь обработчик:
 * иначе поломка в первой деревне списка каждый тик останавливала бы
 * и все остальные.
 * <p>
 * О поломке говорится один раз, с трассой: упавшее однажды упадёт и через
 * полсекунды, а лог в тысячу одинаковых трасс хуже одной. Помнится до
 * перезапуска сервера — тот, кто чинит, перезапускает его всё равно.
 */
public final class Safely {

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private Safely() {
    }

    /**
     * Сделать дело; упало — сказать однажды и жить дальше.
     *
     * @param who  чьё это дело: по нему и роду исключения поломка
     *             называется один раз
     * @param what что делали — словами, для лога
     */
    public static void run(Object who, String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException broken) {
            if (REPORTED.add(who + "/" + what + "/" + broken.getClass().getName())) {
                VillagePax.LOGGER.error("{} ({}) упало; мир продолжает жить", what, who, broken);
            }
        }
    }
}
