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
 * <b>Почему здесь нет ставки налога.</b> План задачи называл её рядом
 * со смертностью, и долго не было ни того ни другого. Теперь есть оба,
 * и настройка появилась только у смертности. Ставка лежит в самом поселении
 * и задаётся в пульте: это решение игрока о своей колонии, а не правило
 * мира, и менять его надо там, где видно последствие.
 *
 * @param autonomousVillages    появляются ли деревни народов в мире
 * @param villageSpacingChunks  шаг сетки деревень; 0 — как сказано в культуре
 * @param populationScale       множитель предела жителей поселения
 * @param hungerWarnDays        через сколько дней голода житель жалуется
 * @param hungerLeaveDays       через сколько уходит навсегда
 * @param villageTradePerDay    сколько материала деревня получает привозом за день
 * @param villageIncomePerDay   сколько монеты деревня выручает со своих полей за день
 * @param roadReserve           сколько материала не тратится на улицы
 * @param ticksPerDecision      как часто житель решает, что делать
 * @param citizenLabels         показывать ли имя и ремесло над жителем
 * @param buildingLabels        показывать ли подписи над зданиями
 * @param carrySlots            сколько видов груза житель унесёт за раз
 * @param greetNewcomers        говорить ли вошедшему без колонии, с чего начать
 */
public record Config(
        boolean autonomousVillages,
        int villageSpacingChunks,
        double populationScale,
        int hungerWarnDays,
        int hungerLeaveDays,
        int villageTradePerDay,
        int villageIncomePerDay,
        int roadReserve,
        int ticksPerDecision,
        boolean citizenLabels,
        boolean buildingLabels,
        int carrySlots,
        boolean greetNewcomers,
        int childDays,
        int lifeDays,
        boolean mortality
) {

    public static final Config DEFAULT = new Config(
            true, 0, 1.0, 4, 6, 64, 16, 16, 10, true, true, 4, true, 8, 120, true);

    // Допустимые значения объявлены по одному разу и здесь: из них собирается
    // и кодек, и таблица RANGES, по которой игроку сообщают о непринятом.
    // Двух источников правды у диапазона быть не должно — иначе однажды
    // кодек примет то, о чём проверка промолчит.
    private static final Codec<Integer> SPACING = Codec.intRange(0, 512);
    private static final Codec<Double> SCALE = Codec.doubleRange(0.1, 20.0);
    private static final Codec<Integer> DAYS = Codec.intRange(1, 1_000);
    private static final Codec<Integer> AMOUNT = Codec.intRange(0, 6_400);
    private static final Codec<Integer> TEMPO = Codec.intRange(1, 200);
    private static final Codec<Integer> SLOTS = Codec.intRange(1, 27);

    /**
     * Поле файла и допустимые для него значения.
     * <p>
     * Нужно потому, что {@code optionalFieldOf} в DFU <b>глотает ошибку
     * вложенного кодека</b>: написал недопустимое — получил значение по
     * умолчанию и никакого объяснения. По этой таблице загрузчик проверяет
     * написанное отдельно и называет непринятое в логе.
     */
    public static final Map<String, Codec<?>> RANGES = Map.ofEntries(
            Map.entry("autonomous_villages", Codec.BOOL),
            Map.entry("village_spacing_chunks", SPACING),
            Map.entry("population_scale", SCALE),
            Map.entry("hunger_warn_days", DAYS),
            Map.entry("hunger_leave_days", DAYS),
            Map.entry("village_trade_per_day", AMOUNT),
            Map.entry("village_income_per_day", AMOUNT),
            Map.entry("road_reserve", AMOUNT),
            Map.entry("ticks_per_decision", TEMPO),
            Map.entry("citizen_labels", Codec.BOOL),
            Map.entry("building_labels", Codec.BOOL),
            Map.entry("carry_slots", SLOTS),
            Map.entry("greet_newcomers", Codec.BOOL),
            Map.entry("child_days", DAYS),
            Map.entry("life_days", DAYS),
            Map.entry("mortality", Codec.BOOL));

    /**
     * Кодек настроек.
     * <p>
     * Поля — {@code fieldOf(...).orElse(...)}, а не {@code optionalFieldOf}:
     * при чтении они так же подставляют умолчание вместо пропущенного
     * или негодного, но при записи <b>пишут поле всегда</b>. С
     * {@code optionalFieldOf} кодек опускал значения, равные умолчанию,
     * и мод создавал игроку файл настроек из одних фигурных скобок — {@code {}}
     * — при обещании «готового json со всеми полями». Узнать имя хоть
     * одной настройки было негде.
     */
    public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("autonomous_villages").orElse(DEFAULT.autonomousVillages)
                    .forGetter(Config::autonomousVillages),
            SPACING.fieldOf("village_spacing_chunks").orElse(DEFAULT.villageSpacingChunks)
                    .forGetter(Config::villageSpacingChunks),
            SCALE.fieldOf("population_scale").orElse(DEFAULT.populationScale)
                    .forGetter(Config::populationScale),
            DAYS.fieldOf("hunger_warn_days").orElse(DEFAULT.hungerWarnDays)
                    .forGetter(Config::hungerWarnDays),
            DAYS.fieldOf("hunger_leave_days").orElse(DEFAULT.hungerLeaveDays)
                    .forGetter(Config::hungerLeaveDays),
            AMOUNT.fieldOf("village_trade_per_day").orElse(DEFAULT.villageTradePerDay)
                    .forGetter(Config::villageTradePerDay),
            AMOUNT.fieldOf("village_income_per_day").orElse(DEFAULT.villageIncomePerDay)
                    .forGetter(Config::villageIncomePerDay),
            AMOUNT.fieldOf("road_reserve").orElse(DEFAULT.roadReserve)
                    .forGetter(Config::roadReserve),
            TEMPO.fieldOf("ticks_per_decision").orElse(DEFAULT.ticksPerDecision)
                    .forGetter(Config::ticksPerDecision),
            Codec.BOOL.fieldOf("citizen_labels").orElse(DEFAULT.citizenLabels)
                    .forGetter(Config::citizenLabels),
            Codec.BOOL.fieldOf("building_labels").orElse(DEFAULT.buildingLabels)
                    .forGetter(Config::buildingLabels),
            SLOTS.fieldOf("carry_slots").orElse(DEFAULT.carrySlots)
                    .forGetter(Config::carrySlots),
            Codec.BOOL.fieldOf("greet_newcomers").orElse(DEFAULT.greetNewcomers)
                    .forGetter(Config::greetNewcomers),
            // Восемь дней детства: примерно два с половиной часа игры
            // на глазах у игрока. Меньше — и ребёнка не успеешь заметить;
            // больше — и колония стоит, кормя того, кто не работает.
            DAYS.fieldOf("child_days").orElse(DEFAULT.childDays)
                    .forGetter(Config::childDays),
            // Сто двадцать дней жизни. Старость начинается с восьмидесяти
            // (последняя треть), и это тот срок, за который житель успевает
            // вырастить двоих и запомниться игроку по имени.
            DAYS.fieldOf("life_days").orElse(DEFAULT.lifeDays)
                    .forGetter(Config::lifeDays),
            // Выключатель смертности стоял в дизайн-документе с первого дня
            // и до сих пор отсутствовал — потому что выключать было нечего.
            // Теперь есть: кому смерть от старости мешает, тот её снимает,
            // и колония живёт вечно, только не растёт сама.
            Codec.BOOL.fieldOf("mortality").orElse(DEFAULT.mortality)
                    .forGetter(Config::mortality)
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
