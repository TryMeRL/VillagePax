package com.villagepax.core.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Настройки мода.
 * <p>
 * Кодеком и обычным json-файлом, а не библиотекой конфигов. Причина та же,
 * по которой сеть пульта своя: <b>движок не должен зависеть от чужой
 * библиотеки</b>. Кодек в моде уже есть под каждое описание данных, файл
 * читается двадцатью строками, а экран настроек — это раскладка поверх
 * таблицы {@link #SETTINGS}: он появился, и ничего переписывать не пришлось.
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
 * @param structureDistanceChunks насколько деревня народа держится от ванильных
 *                              построек; 0 — не держится вовсе
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
        boolean mortality,
        int structureDistanceChunks
) {

    public static final Config DEFAULT = new Config(
            true, 0, 1.0, 4, 6, 64, 16, 16, 10, true, true, 4, true, 8, 120, true, 6);

    /**
     * Что можно записать в поле: выключатель, целое или дробное в границах.
     * <p>
     * Границы — числами, а не только кодеком: {@code Codec.intRange} своих
     * границ наружу не отдаёт, а экрану настроек они нужны, чтобы сказать
     * «от одного до двухсот» до того, как игрок нажмёт «сохранить».
     */
    public sealed interface Bounds permits Flag, Whole, Fraction {
        Codec<?> codec();
    }

    /** Да или нет. */
    public record Flag() implements Bounds {
        @Override
        public Codec<Boolean> codec() {
            return Codec.BOOL;
        }
    }

    /** Целое от и до, включая оба края. */
    public record Whole(int min, int max) implements Bounds {
        @Override
        public Codec<Integer> codec() {
            return Codec.intRange(min, max);
        }
    }

    /** Дробное от и до, включая оба края. */
    public record Fraction(double min, double max) implements Bounds {
        @Override
        public Codec<Double> codec() {
            return Codec.doubleRange(min, max);
        }
    }

    // Допустимые значения объявлены по одному разу и здесь: из них собирается
    // и кодек, и таблица SETTINGS, по которой игроку сообщают о непринятом
    // и по которой рисуется экран. Двух источников правды у диапазона быть
    // не должно — иначе однажды кодек примет то, о чём проверка промолчит.
    private static final Flag FLAG = new Flag();
    private static final Whole SPACING = new Whole(0, 512);
    private static final Fraction SCALE = new Fraction(0.1, 20.0);
    private static final Whole DAYS = new Whole(1, 1_000);
    private static final Whole AMOUNT = new Whole(0, 6_400);
    private static final Whole TEMPO = new Whole(1, 200);
    private static final Whole SLOTS = new Whole(1, 27);
    private static final Whole DISTANCE = new Whole(0, 32);

    /**
     * Настройка: имя поля в файле, раздел экрана и допустимые значения.
     * <p>
     * Раздел — слово для ключа словаря ({@code villagepax.config.group.world}),
     * а не заголовок: экран подписывает его на языке игрока.
     */
    public record Setting(String key, String group, Bounds bounds) {
    }

    /**
     * Все настройки в том порядке, в каком их показывает экран.
     * <p>
     * Порядок — по разделам, а не по файлу: в экране игрок ищет «что
     * про жителей», а не «что шло пятым полем». Полноту таблицы — что
     * в ней ровно те поля, что пишет кодек, — сторожит модульная проверка.
     */
    public static final List<Setting> SETTINGS = List.of(
            new Setting("autonomous_villages", "world", FLAG),
            new Setting("village_spacing_chunks", "world", SPACING),
            new Setting("population_scale", "world", SCALE),
            new Setting("structure_distance_chunks", "world", DISTANCE),
            new Setting("ticks_per_decision", "people", TEMPO),
            new Setting("carry_slots", "people", SLOTS),
            new Setting("hunger_warn_days", "people", DAYS),
            new Setting("hunger_leave_days", "people", DAYS),
            new Setting("child_days", "people", DAYS),
            new Setting("life_days", "people", DAYS),
            new Setting("mortality", "people", FLAG),
            new Setting("village_trade_per_day", "economy", AMOUNT),
            new Setting("village_income_per_day", "economy", AMOUNT),
            new Setting("road_reserve", "economy", AMOUNT),
            new Setting("citizen_labels", "interface", FLAG),
            new Setting("building_labels", "interface", FLAG),
            new Setting("greet_newcomers", "interface", FLAG));

    /**
     * Поле файла и допустимые для него значения.
     * <p>
     * Нужно потому, что {@code orElse} в DFU <b>глотает ошибку вложенного
     * кодека</b>: написал недопустимое — получил значение по умолчанию
     * и никакого объяснения. По этой таблице загрузчик проверяет
     * написанное отдельно и называет непринятое в логе.
     */
    public static final Map<String, Codec<?>> RANGES = SETTINGS.stream()
            .collect(Collectors.toUnmodifiableMap(Setting::key,
                    setting -> setting.bounds().codec()));

    /**
     * Где встают деревни: шаг сетки и отступ от ванильных построек.
     * <p>
     * Парой только потому, что кодек записи в DFU берёт не больше
     * шестнадцати полей, а настроек стало семнадцать. В файле пары не видно:
     * {@link MapCodec} кладёт её поля рядом с остальными, как и прежде.
     */
    private record Placement(int spacing, int distance) {
    }

    private static final MapCodec<Placement> PLACEMENT = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    SPACING.codec().fieldOf("village_spacing_chunks")
                            .orElse(DEFAULT.villageSpacingChunks).forGetter(Placement::spacing),
                    // Шесть чанков от ванильной деревни, аванпоста или храма:
                    // ратуша не врастает в чужой дом, а улица не идёт сквозь
                    // колокольню. Ноль — для тех, кому соседство милее.
                    DISTANCE.codec().fieldOf("structure_distance_chunks")
                            .orElse(DEFAULT.structureDistanceChunks).forGetter(Placement::distance)
            ).apply(instance, Placement::new));

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
            FLAG.codec().fieldOf("autonomous_villages").orElse(DEFAULT.autonomousVillages)
                    .forGetter(Config::autonomousVillages),
            PLACEMENT.forGetter(config -> new Placement(config.villageSpacingChunks(),
                    config.structureDistanceChunks())),
            SCALE.codec().fieldOf("population_scale").orElse(DEFAULT.populationScale)
                    .forGetter(Config::populationScale),
            DAYS.codec().fieldOf("hunger_warn_days").orElse(DEFAULT.hungerWarnDays)
                    .forGetter(Config::hungerWarnDays),
            DAYS.codec().fieldOf("hunger_leave_days").orElse(DEFAULT.hungerLeaveDays)
                    .forGetter(Config::hungerLeaveDays),
            AMOUNT.codec().fieldOf("village_trade_per_day").orElse(DEFAULT.villageTradePerDay)
                    .forGetter(Config::villageTradePerDay),
            AMOUNT.codec().fieldOf("village_income_per_day").orElse(DEFAULT.villageIncomePerDay)
                    .forGetter(Config::villageIncomePerDay),
            AMOUNT.codec().fieldOf("road_reserve").orElse(DEFAULT.roadReserve)
                    .forGetter(Config::roadReserve),
            TEMPO.codec().fieldOf("ticks_per_decision").orElse(DEFAULT.ticksPerDecision)
                    .forGetter(Config::ticksPerDecision),
            FLAG.codec().fieldOf("citizen_labels").orElse(DEFAULT.citizenLabels)
                    .forGetter(Config::citizenLabels),
            FLAG.codec().fieldOf("building_labels").orElse(DEFAULT.buildingLabels)
                    .forGetter(Config::buildingLabels),
            SLOTS.codec().fieldOf("carry_slots").orElse(DEFAULT.carrySlots)
                    .forGetter(Config::carrySlots),
            FLAG.codec().fieldOf("greet_newcomers").orElse(DEFAULT.greetNewcomers)
                    .forGetter(Config::greetNewcomers),
            // Восемь дней детства: примерно два с половиной часа игры
            // на глазах у игрока. Меньше — и ребёнка не успеешь заметить;
            // больше — и колония стоит, кормя того, кто не работает.
            DAYS.codec().fieldOf("child_days").orElse(DEFAULT.childDays)
                    .forGetter(Config::childDays),
            // Сто двадцать дней жизни. Старость начинается с восьмидесяти
            // (последняя треть), и это тот срок, за который житель успевает
            // вырастить двоих и запомниться игроку по имени.
            DAYS.codec().fieldOf("life_days").orElse(DEFAULT.lifeDays)
                    .forGetter(Config::lifeDays),
            // Выключатель смертности стоял в дизайн-документе с первого дня
            // и до сих пор отсутствовал — потому что выключать было нечего.
            // Теперь есть: кому смерть от старости мешает, тот её снимает,
            // и колония живёт вечно, только не растёт сама.
            FLAG.codec().fieldOf("mortality").orElse(DEFAULT.mortality)
                    .forGetter(Config::mortality)
    ).apply(instance, (autonomous, placement, scale, warn, leave, trade, income, reserve,
                       tempo, citizenLabels, buildingLabels, slots, greet, child, life,
                       mortality) -> new Config(autonomous, placement.spacing(), scale, warn,
            leave, trade, income, reserve, tempo, citizenLabels, buildingLabels, slots, greet,
            child, life, mortality, placement.distance())));

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
