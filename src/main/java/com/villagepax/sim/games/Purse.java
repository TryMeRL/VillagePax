package com.villagepax.sim.games;

import com.villagepax.sim.life.Nature;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Кошелёк жителя на вечер — из ничего, заново каждый день.
 * <p>
 * Жалование в моде не лежит у жителя в кармане: его платит казна и тут же
 * тратит рынок. Играть же на что-то надо, и кошелёк — это деньги «на вечер»:
 * у купца толще — с ним одним и можно сыграть на серебряк, — честолюбивый
 * ставит смелее, трус мельче. Ремесло чужого датапака получает обычный
 * кошелёк: о его достатке мод ничего не знает.
 */
public final class Purse {

    /** Какие бывают ставки, в медяках: девять медяков — серебряк. */
    public static final List<Integer> STAKES = List.of(1, 2, 5, 9);

    private static final int MERCHANT = 18;
    private static final int ELDER = 8;
    private static final int COMMON = 4;

    private Purse() {
    }

    /** Сколько медяков у жителя на вечер: по ремеслу, умноженное нравом, не меньше одного. */
    public static int of(Optional<Identifier> profession, Nature nature) {
        int base = profession.map(Identifier::getPath).map(path -> switch (path) {
            case "merchant" -> MERCHANT;
            case "elder" -> ELDER;
            default -> COMMON;
        }).orElse(COMMON);
        double scale = switch (nature) {
            case AMBITIOUS -> 1.5;
            case COWARD -> 0.5;
            default -> 1.0;
        };
        return Math.max(1, (int) Math.floor(base * scale));
    }

    /**
     * Ставки, которые можно выбрать.
     * <p>
     * Не больше половины остатка — «очко» платится вдвойне, и платить его
     * обязано быть из чего; и не больше монет игрока — ставку, которой
     * у него нет, окно выбрать не даёт.
     *
     * @param left        сколько осталось в кошельке жителя сегодня
     * @param playerCoins сколько монет у игрока, в медяках
     */
    public static List<Integer> allowed(int left, int playerCoins) {
        return STAKES.stream()
                .filter(stake -> 2 * stake <= left && stake <= playerCoins)
                .toList();
    }
}
