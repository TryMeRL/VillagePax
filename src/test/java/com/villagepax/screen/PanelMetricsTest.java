package com.villagepax.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Панель обязана сходиться по высоте.
 * <p>
 * Проверка написана по жалобе «меню не листается». Причина была не
 * в прокрутке, а в арифметике: телу экрана дали {@code Sizing.fill(100)},
 * а в owo это процент <b>всего</b> места контейнера, а не остатка после
 * заголовка и вкладок. Прокрутка получала высоту всей панели, считала,
 * что содержимое влезло, и листать было нечего.
 * <p>
 * Вёрстку глазами тест не проверит — у него нет клиента. А вот это —
 * проверит, и ровно это и было сломано.
 */
class PanelMetricsTest {

    @Test
    void townHallPanelAddsUp() {
        assertTrue(PanelMetrics.addsUp(PanelMetrics.TOWN_HALL_HEIGHT,
                        PanelMetrics.HEADER, PanelMetrics.TABS),
                "пульт колонии не сходится по высоте: тело либо вылезает за край, "
                        + "либо не добирает до него");
    }

    @Test
    void elderPanelAddsUp() {
        assertTrue(PanelMetrics.addsUp(PanelMetrics.ELDER_HEIGHT,
                        PanelMetrics.HEADER, PanelMetrics.TABS),
                "разговор со старейшиной не сходится по высоте");
    }

    @Test
    void settingsPanelAddsUp() {
        assertTrue(PanelMetrics.addsUp(PanelMetrics.SETTINGS_HEIGHT,
                        PanelMetrics.HEADER, PanelMetrics.FOOTER),
                "экран настроек не сходится по высоте");
        assertTrue(PanelMetrics.bodyHeight(PanelMetrics.SETTINGS_HEIGHT,
                        PanelMetrics.HEADER, PanelMetrics.FOOTER) >= 120,
                "в экране настроек список короче пяти строк");
    }

    /**
     * Экран затейника: заголовок, строка «когда праздник» и тело с двумя
     * карточками — состязаниями и лавкой.
     */
    @Test
    void festivalPanelAddsUp() {
        assertTrue(PanelMetrics.addsUp(PanelMetrics.FESTIVAL_HEIGHT,
                        PanelMetrics.HEADER, PanelMetrics.STATUS),
                "экран затейника не сходится по высоте");
        assertTrue(PanelMetrics.bodyHeight(PanelMetrics.FESTIVAL_HEIGHT,
                        PanelMetrics.HEADER, PanelMetrics.STATUS) >= 140,
                "в экране затейника тело не вмещает и трёх состязаний");
    }

    /**
     * Окно игры: заголовок, строка «чей ход» и тело, в котором встают две
     * игры со ставками или кости обоих с кнопками. И помещается в самое
     * маленькое окно игры — 320 на 240 при крупном интерфейсе.
     */
    @Test
    void gamePanelAddsUpAndFits() {
        assertTrue(PanelMetrics.addsUp(PanelMetrics.GAME_HEIGHT,
                        PanelMetrics.HEADER, PanelMetrics.STATUS),
                "окно игры не сходится по высоте");
        assertTrue(PanelMetrics.bodyHeight(PanelMetrics.GAME_HEIGHT,
                        PanelMetrics.HEADER, PanelMetrics.STATUS) >= 120,
                "в окне игры тело не вмещает двух игр со ставками");
        assertTrue(PanelMetrics.GAME_WIDTH <= 320 && PanelMetrics.GAME_HEIGHT <= 240,
                "окно игры не помещается в 320 на 240");
    }

    /** Тело обязано быть больше строки: иначе прокрутка бессмысленна. */
    @Test
    void bodyIsWorthScrolling() {
        int townHall = PanelMetrics.bodyHeight(PanelMetrics.TOWN_HALL_HEIGHT,
                PanelMetrics.HEADER, PanelMetrics.TABS);
        int elder = PanelMetrics.bodyHeight(PanelMetrics.ELDER_HEIGHT,
                PanelMetrics.HEADER, PanelMetrics.TABS);

        assertTrue(townHall >= 120, "тело пульта всего " + townHall + " пикселей");
        assertTrue(elder >= 120, "тело разговора всего " + elder + " пикселей");
    }

    /**
     * Счёт вычитанием, а не на глаз: 236 − 16 отступов − 12 промежутков
     * − 32 заголовка − 16 вкладок.
     */
    @Test
    void heightIsSubtractedNotGuessed() {
        assertEquals(160, PanelMetrics.bodyHeight(236, 32, 16));
        assertEquals(94, PanelMetrics.bodyHeight(150, 20, 8));
    }

    /**
     * Три вкладки разговора обязаны влезать в панель по ширине.
     * <p>
     * Написано вместе с вкладкой народа. Высота панели проверялась
     * с первого дня, а ширина — нет, и третья вкладка могла уехать за
     * край незамеченной: у игровых тестов нет клиента, а глазами это
     * видно только тому, кто откроет именно этот экран.
     */
    @Test
    void threeElderTabsFitAcross() {
        assertTrue(PanelMetrics.tabsFit(PanelMetrics.ELDER_WIDTH, 3,
                        PanelMetrics.ELDER_TAB, PanelMetrics.GAP),
                "ряд из трёх вкладок не влезает в панель разговора шириной "
                        + PanelMetrics.ELDER_WIDTH);
    }

    /** И перестают влезать, когда их становится слишком много. */
    @Test
    void tooManyTabsDoNotFit() {
        assertTrue(!PanelMetrics.tabsFit(PanelMetrics.ELDER_WIDTH, 5,
                        PanelMetrics.ELDER_TAB, PanelMetrics.GAP),
                "проверка ширины обязана хоть когда-нибудь говорить «нет»");
    }

    /**
     * Панель, в которую тело не влезает, отдаёт наименьшую высоту, а не
     * отрицательную: отрицательная означала бы прокрутку наизнанку.
     */
    @Test
    void tinyPanelClampsInsteadOfGoingNegative() {
        assertEquals(PanelMetrics.LEAST_BODY, PanelMetrics.bodyHeight(40, 32, 16));
    }
}
