package com.villagepax.core.festival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.StrictCodecs;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Праздник народа — данные, как боги и торг.
 * <p>
 * Виды состязаний, зверьки и вещицы — закрытые перечисления в коде: датапак
 * выбирает, как пройдёт праздник на его ярмарке, но не приносит своего
 * состязания, потому что состязание — это поведение, а поведение живёт в коде.
 * <p>
 * Поля, решающие смысл, обязательные, а необязательные читаются строго
 * ({@link StrictCodecs}): у обычного необязательного поля DFU глотает ошибку
 * вложенного кодека, и описка {@code "critter": "goat"} молча давала бы
 * ловлю без зверька.
 *
 * @param culture   чей праздник
 * @param name      ключ названия: «Ярмарка полной луны»
 * @param moonPhase в какую фазу луны: 0 — полнолуние, 4 — новолуние
 * @param fireworks цвета и форма вечернего фейерверка
 * @param contests  состязания, в порядке показа у затейника
 * @param prizes    что продаёт лавка затейника за ленты
 */
public record Festival(Identifier culture, String name, int moonPhase, Fireworks fireworks,
                       List<Contest> contests, List<Prize> prizes) {

    /**
     * Одно состязание праздника.
     *
     * @param kind    во что играют
     * @param name    ключ названия: «Ловля поросят»
     * @param token   облик вещиц поиска
     * @param count   сколько вещиц, зверьков или выстрелов; ноль — по умолчанию вида
     * @param critter кого ловят
     */
    public record Contest(ContestKind kind, String name, Optional<TokenKind> token, int count,
                          Optional<Critter> critter) {

        public static final Codec<Contest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ContestKind.CODEC.fieldOf("kind").forGetter(Contest::kind),
                Codec.STRING.fieldOf("name").forGetter(Contest::name),
                StrictCodecs.optional("token", TokenKind.CODEC).forGetter(Contest::token),
                StrictCodecs.optional("count", Codec.INT, 0).forGetter(Contest::count),
                StrictCodecs.optional("critter", Critter.CODEC).forGetter(Contest::critter)
        ).apply(instance, Contest::new));

        /**
         * Сколько вещиц прячется, зверьков выпускается или выстрелов даётся.
         * <p>
         * Умолчания — решение по игре: десять вещиц за минуту находятся,
         * но не все; трёх зверьков хватает на трёх ловцов; восемь выстрелов —
         * столько, чтобы промах не решал всё.
         */
        public int pieces() {
            if (count > 0) {
                return count;
            }
            return switch (kind) {
                case HUNT -> 10;
                case CHASE -> 3;
                case ARCHERY -> 8;
            };
        }
    }

    /**
     * Товар лавки затейника.
     *
     * @param item  что даётся
     * @param count сколько штук за раз
     * @param price сколько лент стоит
     */
    public record Prize(Identifier item, int count, int price) {

        public static final Codec<Prize> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Prize::item),
                StrictCodecs.optional("count", Codec.INT, 1).forGetter(Prize::count),
                Codec.INT.fieldOf("price").forGetter(Prize::price)
        ).apply(instance, Prize::new));
    }

    /**
     * Вечерний фейерверк народа.
     * <p>
     * Форма — одна из ванильных, цветов — хоть один. Иное — ошибка чтения,
     * а не умолчание: незнакомая форма молча стала бы шаром, а ракета без
     * цветов роняет клиент — искре нечем гореть.
     *
     * @param colors цвета вспышек, как их пишет ванильная ракета
     * @param shape  форма: {@code small_ball}, {@code large_ball}, {@code star},
     *               {@code creeper}, {@code burst}
     */
    public record Fireworks(List<Integer> colors, String shape) {

        /** Народ, не назвавший своих цветов, всё равно встречает ночь огнями — белыми. */
        public static final Fireworks PLAIN = new Fireworks(List.of(0xFFFFFF), "large_ball");

        private static final String SHAPES = Arrays.stream(FireworkRocketItem.Type.values())
                .map(FireworkRocketItem.Type::getName).collect(Collectors.joining(", "));

        private static final Codec<String> SHAPE = Codec.STRING.comapFlatMap(
                name -> kindOf(name).isPresent() ? DataResult.success(name)
                        : DataResult.error(() -> "Неизвестная форма фейерверка: " + name
                        + ". Допустимые: " + SHAPES),
                name -> name);

        private static final Codec<List<Integer>> COLORS = Codec.INT.listOf().comapFlatMap(
                colors -> colors.isEmpty() ? DataResult.error(() -> "у фейерверка нет цветов")
                        : DataResult.success(colors),
                colors -> colors);

        public static final Codec<Fireworks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                COLORS.fieldOf("colors").forGetter(Fireworks::colors),
                StrictCodecs.optional("shape", SHAPE, "large_ball").forGetter(Fireworks::shape)
        ).apply(instance, Fireworks::new));

        /** Форма как у ванильной ракеты; незнакомая у собранного в коде — шар. */
        public FireworkRocketItem.Type type() {
            return kindOf(shape).orElse(FireworkRocketItem.Type.LARGE_BALL);
        }

        private static Optional<FireworkRocketItem.Type> kindOf(String name) {
            return Arrays.stream(FireworkRocketItem.Type.values())
                    .filter(type -> type.getName().equals(name)).findFirst();
        }
    }

    public static final Codec<Festival> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("culture").forGetter(Festival::culture),
            Codec.STRING.fieldOf("name").forGetter(Festival::name),
            Codec.INT.fieldOf("moon_phase").forGetter(Festival::moonPhase),
            StrictCodecs.optional("fireworks", Fireworks.CODEC, Fireworks.PLAIN).forGetter(Festival::fireworks),
            Contest.CODEC.listOf().fieldOf("contests").forGetter(Festival::contests),
            StrictCodecs.optional("prizes", Prize.CODEC.listOf(), List.<Prize>of()).forGetter(Festival::prizes)
    ).apply(instance, Festival::new));
}
