package com.villagepax.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

/**
 * Настройки мода.
 * <p>
 * Кодеком и обычным json-файлом, а не библиотекой конфигов. Причина та же,
 * по которой сеть пульта своя: <b>движок не должен зависеть от чужой
 * библиотеки</b>. Кодек в моде уже есть под каждое описание данных, файл
 * читается двадцатью строками, а экран настроек — это раскладка, и её можно
 * добавить позже, ничего не переписывая.
 * <p>
 * Каждое поле необязательно и имеет значение по умолчанию: файл, в котором
 * игрок оставил одну строку, обязан работать. И наоборот — незнакомое поле
 * не роняет загрузку, потому что кодек читает только известные.
 * <p>
 * <b>Чего здесь нет.</b> План задачи называл смертность и ставку налога.
 * Ни того, ни другого в моде пока нет: жители бессмертны по решению
 * заказчика, а налогов не существует. Настройка, которая ничего не
 * выключает, хуже отсутствующей — она обещает то, чего не будет.
 *
 * @param autonomousVillages    появляются ли деревни народов в мире
 * @param villageSpacingChunks  шаг сетки деревень; 0 — как сказано в культуре
 * @param populationScale       множитель предела жителей поселения
 * @param hungerWarnDays        через сколько дней голода житель жалуется
 * @param hungerLeaveDays       через сколько уходит навсегда
 * @param villageTradePerDay    сколько материала деревня получает привозом за день
 * @param roadReserve           сколько материала не тратится на улицы
 * @param ticksPerDecision      как часто житель решает, что делать
 * @param citizenLabels         показывать ли имя и ремесло над жителем
 * @param buildingLabels        показывать ли подписи над зданиями
 */
public record Config(
        boolean autonomousVillages,
        int villageSpacingChunks,
        double populationScale,
        int hungerWarnDays,
        int hungerLeaveDays,
        int villageTradePerDay,
        int roadReserve,
        int ticksPerDecision,
        boolean citizenLabels,
        boolean buildingLabels
) {

    public static final Config DEFAULT = new Config(
            true, 0, 1.0, 4, 6, 64, 16, 10, true, true);

    // Допустимые значения объявлены по одному разу и здесь: из них собирается
    // и кодек, и таблица RANGES, по которой игроку сообщают о непринятом.
    // Двух источников правды у диапазона быть не должно — иначе однажды
    // кодек примет то, о чём проверка промолчит.
    private static final Codec<Integer> SPACING = Codec.intRange(0, 512);
    private static final Codec<Double> SCALE = Codec.doubleRange(0.1, 20.0);
    private static final Codec<Integer> DAYS = Codec.intRange(1, 1_000);
    private static final Codec<Integer> AMOUNT = Codec.intRange(0, 6_400);
    private static final Codec<Integer> TEMPO = Codec.intRange(1, 200);

    /**
     * Поле файла и допустимые для него значения.
     * <p>
     * Нужно потому, что {@code optionalFieldOf} в DFU <b>глотает ошибку
     * вложенного кодека</b>: написал недопустимое — получил значение по
     * умолчанию и никакого объяснения. По этой таблице загрузчик проверяет
     * написанное отдельно и называет непринятое в логе.
     */
    public static final Map<String, Codec<?>> RANGES = Map.of(
            "autonomous_villages", Codec.BOOL,
            "village_spacing_chunks", SPACING,
            "population_scale", SCALE,
            "hunger_warn_days", DAYS,
            "hunger_leave_days", DAYS,
            "village_trade_per_day", AMOUNT,
            "road_reserve", AMOUNT,
            "ticks_per_decision", TEMPO,
            "citizen_labels", Codec.BOOL,
            "building_labels", Codec.BOOL);

    public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("autonomous_villages", DEFAULT.autonomousVillages)
                    .forGetter(Config::autonomousVillages),
            SPACING.optionalFieldOf("village_spacing_chunks", DEFAULT.villageSpacingChunks)
                    .forGetter(Config::villageSpacingChunks),
            SCALE.optionalFieldOf("population_scale", DEFAULT.populationScale)
                    .forGetter(Config::populationScale),
            DAYS.optionalFieldOf("hunger_warn_days", DEFAULT.hungerWarnDays)
                    .forGetter(Config::hungerWarnDays),
            DAYS.optionalFieldOf("hunger_leave_days", DEFAULT.hungerLeaveDays)
                    .forGetter(Config::hungerLeaveDays),
            AMOUNT.optionalFieldOf("village_trade_per_day", DEFAULT.villageTradePerDay)
                    .forGetter(Config::villageTradePerDay),
            AMOUNT.optionalFieldOf("road_reserve", DEFAULT.roadReserve)
                    .forGetter(Config::roadReserve),
            TEMPO.optionalFieldOf("ticks_per_decision", DEFAULT.ticksPerDecision)
                    .forGetter(Config::ticksPerDecision),
            Codec.BOOL.optionalFieldOf("citizen_labels", DEFAULT.citizenLabels)
                    .forGetter(Config::citizenLabels),
            Codec.BOOL.optionalFieldOf("building_labels", DEFAULT.buildingLabels)
                    .forGetter(Config::buildingLabels)
    ).apply(instance, Config::new));

    /**
     * Приведение к осмысленному виду.
     * <p>
     * Порядок «пожаловался, потом ушёл» обязан сохраняться: с обратными
     * сроками житель уходил бы, ни разу не предупредив, и игрок не понял бы,
     * за что. Диапазоны полей проверяет кодек, а вот их <b>согласованность</b>
     * — нет, и проверить её больше негде.
     */
    public Config {
        if (hungerLeaveDays <= hungerWarnDays) {
            hungerLeaveDays = hungerWarnDays + 1;
        }
    }
}
