package com.villagepax.sim;

import com.villagepax.core.building.BuildingTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

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
        refresh(null, settlement);
    }

    /**
     * То же, но с миром: с ним ступень становится событием.
     * <p>
     * Мир нужен только ради праздника — звука, частиц и слов в чат.
     * Без него уровень всё равно поднимется: проверки зовут этот метод
     * без мира, и ступень не должна зависеть от того, есть ли кому
     * её увидеть.
     */
    public static void refresh(ServerWorld world, Settlement settlement) {
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
            if (world != null) {
                Milestones.reached(world, settlement, reached);
            }
        }
    }

    private static boolean isTownHall(Building building) {
        return isTownHallType(building.type());
    }

    /**
     * Ратуша ли это. Спрашивается у типа здания, объявленного датапаком.
     * <p>
     * Прежде опознавали по имени — «путь кончается на town_hall», — и это
     * работало до первого датапака, который назвал бы своё здание иначе:
     * {@code norman/town_hall_ruins} мод счёл бы ратушей и позволил бы
     * поднимать по нему уровень колонии.
     * <p>
     * Остаётся здесь, а не переезжает в {@code BuildingTypes}, потому что
     * спрашивают в двух местах и оба про колонию: уровень и список
     * заказов. Ратуша у поселения одна: она и есть его середина.
     */
    public static boolean isTownHallType(Identifier type) {
        return BuildingTypes.isTownHall(type);
    }
}
