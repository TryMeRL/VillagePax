package com.villagepax.sim;

/**
 * Уровень колонии равен уровню её ратуши.
 * <p>
 * Решение заказчика. Правило выбрано не за простоту: у роста появляется
 * <b>понятная цель</b> — хочешь деревню, строй ратушу второго уровня, —
 * и цель эта стоит на видном месте в середине колонии. Правило «по числу
 * зданий» играло бы само, но игрок не понимал бы, за что именно ему дали
 * уровень.
 * <p>
 * Уровень тянет за собой предел населения и радиус границ, поэтому без
 * этого правила колония навсегда оставалась хутором: шесть жителей и два
 * чанка. Ровно так и было, пока это не нашлось разбором кода.
 */
public final class Levels {

    private Levels() {
    }

    /**
     * Пересчитать уровень колонии по её ратушам.
     * <p>
     * Уровень <b>не падает</b>, если ратушу снесли: снос и без того не
     * распускает колонию, а падение уровня выгнало бы жителей сверх нового
     * предела — наказание, которого игрок не заказывал.
     */
    public static void refresh(Settlement settlement) {
        int hall = 0;
        for (Building building : settlement.buildings()) {
            if (building.isOperational() && isTownHall(building)) {
                hall = Math.max(hall, building.level());
            }
        }
        if (hall <= 0) {
            return;
        }

        SettlementLevel[] ladder = SettlementLevel.values();
        SettlementLevel reached = ladder[Math.min(hall, ladder.length) - 1];

        if (reached.ordinal() > settlement.level().ordinal()) {
            settlement.setLevel(reached);
        }
    }

    /**
     * Ратуша находится по соглашению об именовании — тем же, которым
     * профессия находит свою мастерскую. Долг тот же: когда появится
     * {@code BuildingType} из датапака, здание объявит это само.
     */
    private static boolean isTownHall(Building building) {
        String type = building.type().getPath();
        return type.equals("town_hall") || type.endsWith("/town_hall");
    }
}
