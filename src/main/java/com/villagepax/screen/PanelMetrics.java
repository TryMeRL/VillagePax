package com.villagepax.screen;

/**
 * Размеры панелей обоих экранов — числами, которые можно проверить.
 * <p>
 * Живут не в клиентском коде, а здесь, и это осознанно. Жалоба игрока
 * была «меню не листается», а причина — чистая арифметика: в owo
 * {@code Sizing.fill(100)} означает процент <b>всего</b> места контейнера,
 * а не остатка после соседей. Прокрутка, стоявшая в панели рядом
 * с заголовком и вкладками, получала высоту всей панели, считала, что
 * содержимое влезло, и листать было нечего, а лишнее рисовалось за краем.
 * <p>
 * Вёрстку глазами проверить тестом нельзя — у игровых тестов нет клиента.
 * А вот <b>сходится ли панель по высоте</b> — можно, и это ровно то, что
 * было сломано. Поэтому числа лежат в общем коде, а клиентские экраны
 * их только читают.
 */
public final class PanelMetrics {

    /** Отступ панели с каждой стороны и промежуток между её частями. */
    public static final int PADDING = 8;
    public static final int GAP = 6;

    /** Заголовок: строка имени с пилюлями и черта под ними. */
    public static final int HEADER = 32;

    /** Ряд кнопок-вкладок. */
    public static final int TABS = 16;

    /** Пульт колонии: четыре вкладки, самый плотный экран мода. */
    public static final int TOWN_HALL_WIDTH = 340;
    public static final int TOWN_HALL_HEIGHT = 236;

    /** Разговор со старейшиной: две вкладки, длинные фразы. */
    public static final int ELDER_WIDTH = 330;
    public static final int ELDER_HEIGHT = 224;

    /** Ниже этого прокрутка бессмысленна: в ней не поместится и строки. */
    public static final int LEAST_BODY = 24;

    /** Ширина кнопки-вкладки в разговоре со старейшиной. */
    public static final int ELDER_TAB = 90;

    private PanelMetrics() {
    }

    /**
     * Влезает ли ряд вкладок в панель по ширине.
     * <p>
     * Тем же тестом и по той же причине, что и высота тела: вёрстку глазами
     * проверить нельзя, а «третья вкладка уехала за край» — это арифметика.
     * Считается по внутренней ширине: отступы панели вкладкам не принадлежат.
     *
     * @param panelWidth ширина панели целиком
     * @param tabs       сколько вкладок в ряду
     * @param tabWidth   ширина одной
     * @param gap        промежуток между ними
     */
    public static boolean tabsFit(int panelWidth, int tabs, int tabWidth, int gap) {
        int taken = tabs * tabWidth + Math.max(0, tabs - 1) * gap;
        return taken <= panelWidth - 2 * PADDING;
    }

    /**
     * Высота тела экрана: всё, что осталось от панели.
     * <p>
     * Считается вычитанием, а не {@code fill}: см. описание класса.
     * Промежуток берётся по числу частей над телом — ровно столько
     * промежутков и будет, потому что тело идёт последним.
     *
     * @param panelHeight высота панели целиком
     * @param above       высоты частей над телом
     */
    public static int bodyHeight(int panelHeight, int... above) {
        int taken = 2 * PADDING + GAP * above.length;
        for (int part : above) {
            taken += part;
        }
        return Math.max(LEAST_BODY, panelHeight - taken);
    }

    /**
     * Сходится ли панель ровно: сумма частей, отступов и промежутков
     * равна её высоте.
     * <p>
     * Нужно тесту. Не сойдётся — значит либо тело вылезло за край панели,
     * либо под ним осталась пустая полоса, и в обоих случаях прокрутка
     * считает переполнение неверно.
     */
    public static boolean addsUp(int panelHeight, int... above) {
        int body = bodyHeight(panelHeight, above);
        int total = 2 * PADDING + GAP * above.length + body;
        for (int part : above) {
            total += part;
        }
        return total == panelHeight;
    }
}
