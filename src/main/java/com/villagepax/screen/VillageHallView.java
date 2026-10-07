package com.villagepax.screen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ратуша деревни народа глазами гостя: что за деревня, кто ты для неё,
 * что ей нужно и что в ней сегодня.
 * <p>
 * Прежде щелчок по ратуше деревни писал в чат пять строк, и они тонули
 * в разговоре жителей. Теперь это окно, и в нём то, ради чего к ратуше
 * подходят: доверие и до чего оно доведёт, цены по нему, рынок и праздник,
 * нужды деревни с кнопкой «отдать», жители, здания и летопись.
 * <p>
 * Строки новостей и летописи едут готовым текстом (JSON компонента):
 * у них свои ключи и аргументы, и разбирать их второй раз на клиенте
 * незачем.
 */
public record VillageHallView(
        UUID village, String name, Identifier culture, String level, Optional<String> nextLevel,
        BlockPos hall, int population, int maxCitizens,
        Trust trust, Calendar calendar, Needs needs,
        List<String> news, List<Person> people, List<House> buildings, List<String> chronicle,
        Ties ties) {

    /** Кто ты для деревни и во что это обходится на прилавке. */
    public record Trust(int reputation, String standing, Optional<String> nextStanding, int nextFrom,
                        int fromHere, int buyPercent, int sellPercent, boolean marketPrices) {
        public static final Codec<Trust> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("reputation").forGetter(Trust::reputation),
                Codec.STRING.fieldOf("standing").forGetter(Trust::standing),
                Codec.STRING.optionalFieldOf("next_standing").forGetter(Trust::nextStanding),
                Codec.INT.fieldOf("next_from").forGetter(Trust::nextFrom),
                Codec.INT.fieldOf("from_here").forGetter(Trust::fromHere),
                Codec.INT.fieldOf("buy").forGetter(Trust::buyPercent),
                Codec.INT.fieldOf("sell").forGetter(Trust::sellPercent),
                Codec.BOOL.fieldOf("market_prices").forGetter(Trust::marketPrices)
        ).apply(instance, Trust::new));
    }

    /** Рынок и праздник: сегодня или через сколько дней. */
    public record Calendar(long day, int marketIn, boolean hasMarket, Optional<String> festival,
                           int festivalIn) {
        public static final Codec<Calendar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("day").forGetter(Calendar::day),
                Codec.INT.fieldOf("market_in").forGetter(Calendar::marketIn),
                Codec.BOOL.fieldOf("has_market").forGetter(Calendar::hasMarket),
                Codec.STRING.optionalFieldOf("festival").forGetter(Calendar::festival),
                Codec.INT.fieldOf("festival_in").forGetter(Calendar::festivalIn)
        ).apply(instance, Calendar::new));
    }

    /** Чего деревне не хватает: стройке, амбару, кроватям. */
    public record Needs(Optional<Identifier> building, Map<Identifier, Integer> missing, int foodDays,
                        boolean hungry, int freeBeds, Map<Identifier, Integer> carried) {
        public static final Codec<Needs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("building").forGetter(Needs::building),
                Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("missing").forGetter(Needs::missing),
                Codec.INT.fieldOf("food_days").forGetter(Needs::foodDays),
                Codec.BOOL.fieldOf("hungry").forGetter(Needs::hungry),
                Codec.INT.fieldOf("free_beds").forGetter(Needs::freeBeds),
                Codec.unboundedMap(Identifier.CODEC, Codec.INT).fieldOf("carried").forGetter(Needs::carried)
        ).apply(instance, Needs::new));
    }

    /** Житель в списке: имя, дело, пора жизни. */
    public record Person(String name, Optional<Identifier> profession, String stage, boolean married) {
        public static final Codec<Person> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Person::name),
                Identifier.CODEC.optionalFieldOf("profession").forGetter(Person::profession),
                Codec.STRING.fieldOf("stage").forGetter(Person::stage),
                Codec.BOOL.fieldOf("married").forGetter(Person::married)
        ).apply(instance, Person::new));
    }

    /** Здание деревни: что, какого уровня и готово ли. */
    public record House(Identifier type, int level, boolean done) {
        public static final Codec<House> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("type").forGetter(House::type),
                Codec.INT.fieldOf("level").forGetter(House::level),
                Codec.BOOL.fieldOf("done").forGetter(House::done)
        ).apply(instance, House::new));
    }

    /**
     * Дела между игроком и деревней: союз, дань, перемирие, гражданство.
     *
     * @param citizenship приговор просьбе о доме: «yes», «already», «not_a_friend»…
     */
    public record Ties(boolean ally, int tributeDays, int truceDays, boolean besieged,
                       String citizenship) {
        public static final Codec<Ties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("ally").forGetter(Ties::ally),
                Codec.INT.fieldOf("tribute_days").forGetter(Ties::tributeDays),
                Codec.INT.fieldOf("truce_days").forGetter(Ties::truceDays),
                Codec.BOOL.fieldOf("besieged").forGetter(Ties::besieged),
                Codec.STRING.fieldOf("citizenship").forGetter(Ties::citizenship)
        ).apply(instance, Ties::new));
    }

    public static final Codec<VillageHallView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("village").forGetter(VillageHallView::village),
            Codec.STRING.fieldOf("name").forGetter(VillageHallView::name),
            Identifier.CODEC.fieldOf("culture").forGetter(VillageHallView::culture),
            Codec.STRING.fieldOf("level").forGetter(VillageHallView::level),
            Codec.STRING.optionalFieldOf("next_level").forGetter(VillageHallView::nextLevel),
            BlockPos.CODEC.fieldOf("hall").forGetter(VillageHallView::hall),
            Codec.INT.fieldOf("population").forGetter(VillageHallView::population),
            Codec.INT.fieldOf("max_citizens").forGetter(VillageHallView::maxCitizens),
            Trust.CODEC.fieldOf("trust").forGetter(VillageHallView::trust),
            Calendar.CODEC.fieldOf("calendar").forGetter(VillageHallView::calendar),
            Needs.CODEC.fieldOf("needs").forGetter(VillageHallView::needs),
            Codec.STRING.listOf().fieldOf("news").forGetter(VillageHallView::news),
            Person.CODEC.listOf().fieldOf("people").forGetter(VillageHallView::people),
            House.CODEC.listOf().fieldOf("buildings").forGetter(VillageHallView::buildings),
            Codec.STRING.listOf().fieldOf("chronicle").forGetter(VillageHallView::chronicle),
            Ties.CODEC.fieldOf("ties").forGetter(VillageHallView::ties)
    ).apply(instance, VillageHallView::new));
}
