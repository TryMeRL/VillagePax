package com.villagepax.client.screen;

import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;

/**
 * Полоса прокрутки, которая помнит, где её оставили.
 * <p>
 * Жалоба заказчика: «когда данные обновляются — листается вверх и нельзя
 * нормально использовать меню». И это ровно так: снимок колонии приходит
 * с сервера <b>дважды в секунду</b>, а в живой колонии он меняется почти
 * каждый раз — кто-то поработал, кто-то поел, стройка сдвинулась на блок.
 * Экран на каждый такой снимок собирает тело заново, и прокрутка вместе
 * с телом уезжает в начало. Список зданий длиннее экрана становится
 * нечитаемым: до нижней строки просто не дожить.
 * <p>
 * Само owo смещение наружу не отдаёт — оно защищённое. Поэтому здесь
 * наследник: он умеет ровно две вещи — сказать, где стоит, и встать туда
 * же снова. {@code currentScrollPosition} ставится вместе со смещением
 * намеренно: иначе список после каждого обновления плавно «доезжал» бы
 * до места, и вместо скачка получилась бы качка.
 */
public class KeptScroll<C extends Component> extends ScrollContainer<C> {

    public KeptScroll(Sizing horizontalSizing, Sizing verticalSizing, C child) {
        super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
    }

    /** Где прокрутка стоит сейчас, в пикселях от начала. */
    public double where() {
        return scrollOffset;
    }

    /**
     * Встать туда же. Зажимать не нужно: следующая же раскладка сама
     * прижмёт смещение к новой длине содержимого — список мог и укоротиться.
     */
    public void restore(double offset) {
        this.scrollOffset = offset;
        this.currentScrollPosition = offset;
    }
}
