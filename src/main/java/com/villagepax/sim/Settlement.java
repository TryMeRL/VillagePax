package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.villagepax.core.config.Configs;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.trade.Caravan;
import com.villagepax.core.war.WarParty;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Поселение — общая структура данных для колонии игрока и для деревни народа.
 * <p>
 * Различает их только {@link Owner}: у колонии решения принимает игрок через
 * интерфейс ратуши, у деревни — контроллер, раз в игровой день. Всё остальное
 * (здания, жители, стройка, экономика, квесты, дипломатия) написано один раз
 * и работает для обоих. Это несущее архитектурное решение всего мода.
 */
public class Settlement {

    /** «Суточные нужды ещё ни разу не считались». */
    public static final long UNSEEN_DAY = Long.MIN_VALUE;

    public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("id").forGetter(Settlement::id),
            Identifier.CODEC.fieldOf("culture").forGetter(Settlement::culture),
            Owner.CODEC.optionalFieldOf("owner", Owner.AUTONOMOUS).forGetter(Settlement::owner),
            Codec.STRING.fieldOf("name").forGetter(Settlement::name),
            BlockPos.CODEC.fieldOf("center").forGetter(Settlement::center),
            SettlementLevel.CODEC.optionalFieldOf("level", SettlementLevel.HAMLET).forGetter(Settlement::level),
            SettlementStats.CODEC.optionalFieldOf("stats", SettlementStats.INITIAL).forGetter(Settlement::stats),
            Building.CODEC.listOf().optionalFieldOf("buildings", List.of()).forGetter(Settlement::buildings),
            Citizen.CODEC.listOf().optionalFieldOf("citizens", List.of()).forGetter(Settlement::citizens),
            Codec.LONG.optionalFieldOf("last_day", UNSEEN_DAY).forGetter(Settlement::lastDay),
            Codec.unboundedMap(Uuids.STRING_CODEC, Codec.INT)
                    .optionalFieldOf("reputation", Map.of()).forGetter(Settlement::reputation),
            Codec.unboundedMap(Uuids.STRING_CODEC, Identifier.CODEC.listOf())
                    .optionalFieldOf("quests_done", Map.of()).forGetter(Settlement::questsDone),
            Caravan.CODEC.listOf().optionalFieldOf("visitors", List.of())
                    .forGetter(Settlement::visitors),
            Codec.unboundedMap(Uuids.STRING_CODEC, Codec.LONG)
                    .optionalFieldOf("gift_days", Map.of()).forGetter(Settlement::giftDays),
            War.CODEC.optionalFieldOf("war", War.NONE).forGetter(Settlement::war),
            Faith.CODEC.optionalFieldOf("faith", Faith.NONE).forGetter(Settlement::faith)
    ).apply(instance, Settlement::new));

    /**
     * Вера, какой её помнит поселение: кому молятся, когда клали в последний
     * раз, до какого дня хранит бог и что уже вручено.
     * <p>
     * Запись, а не четыре поля, по той же причине, что и война: у кодека
     * Mojang ровно шестнадцать полей в группе, пятнадцать заняты, и вере
     * досталось последнее. Запись вдобавок честно называет то, что и так
     * было четырьмя половинами одного, — отношения поселения с небом.
     * <p>
     * Благосклонность лежит <b>в поселении</b>, а не на игроке, и это то же
     * решение, что с доверием деревни: молится не человек вообще, а вот эта
     * колония вот этому богу. Перенеси её на игрока — и вторая колония
     * начинала бы с чужой святостью.
     *
     * @param favour       бог → набранная благосклонность
     * @param lastOffering бог → день, когда ему последний раз клали
     * @param blessedUntil домен → день, до которого держится благословение
     * @param artifacts    что уже вручено: второй раз не дают
     */
    public record Faith(Map<Identifier, Integer> favour, Map<Identifier, Long> lastOffering,
                        Map<String, Long> blessedUntil, List<Identifier> artifacts) {

        /** Небо молчит: никому не молились, ничего не просили. */
        public static final Faith NONE = new Faith(Map.of(), Map.of(), Map.of(), List.of());

        public Faith {
            favour = Map.copyOf(favour);
            lastOffering = Map.copyOf(lastOffering);
            blessedUntil = Map.copyOf(blessedUntil);
            artifacts = List.copyOf(artifacts);
        }

        public static final Codec<Faith> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                        .optionalFieldOf("favour", Map.of()).forGetter(Faith::favour),
                Codec.unboundedMap(Identifier.CODEC, Codec.LONG)
                        .optionalFieldOf("last_offering", Map.of()).forGetter(Faith::lastOffering),
                // Домен — строка, а не опознаватель: он не из реестра,
                // а из закрытого списка кода, и опознаватель тут врал бы
                // о том, что его можно добавить датапаком.
                Codec.unboundedMap(Codec.STRING, Codec.LONG)
                        .optionalFieldOf("blessed_until", Map.of()).forGetter(Faith::blessedUntil),
                Identifier.CODEC.listOf()
                        .optionalFieldOf("artifacts", List.of()).forGetter(Faith::artifacts)
        ).apply(instance, Faith::new));
    }

    /**
     * Война, какой её помнит поселение: кто у ворот, когда приходили
     * и до какого дня не придут.
     * <p>
     * Сгруппировано по той же причине, что и быт в пульте: у кодека Mojang
     * ровно шестнадцать полей в группе, и семнадцатое — перемирие — в неё
     * не поместилось. Запись вдобавок честно называет то, что и так было
     * тремя половинами одного: военное положение поселения.
     * <p>
     * Снаружи ничего не изменилось: поселение по-прежнему отвечает на
     * {@code siege()} и {@code lastRaid()}, просто переспрашивает их у войны.
     *
     * @param siege      отряд, стоящий у ворот прямо сейчас
     * @param lastRaid   день последнего набега <b>на это</b> поселение
     * @param truceUntil до какого дня это поселение никого не посылает
     * @param allies     с кем заключён союз и с какого дня
     * @param beatenOn     день, когда отряд <b>этого</b> поселения перебили
     * @param tributeTo    кому платят дань
     * @param tributeUntil до какого дня платят
     */
    public record War(Optional<WarParty> siege, long lastRaid, long truceUntil,
                      Map<UUID, Long> allies, long beatenOn,
                      Optional<UUID> tributeTo, long tributeUntil,
                      Optional<UUID> marchingOn) {

        /** Мир: никто не стоит у ворот, никто никуда не идёт. */
        public static final War NONE = new War(Optional.empty(), UNSEEN_DAY, UNSEEN_DAY,
                Map.of(), UNSEEN_DAY, Optional.empty(), UNSEEN_DAY, Optional.empty());

        public War {
            allies = Map.copyOf(allies);
        }

        public War(Optional<WarParty> siege, long lastRaid, long truceUntil) {
            this(siege, lastRaid, truceUntil, Map.of(), UNSEEN_DAY);
        }

        public War(Optional<WarParty> siege, long lastRaid, long truceUntil,
                   Map<UUID, Long> allies) {
            this(siege, lastRaid, truceUntil, allies, UNSEEN_DAY);
        }

        public War(Optional<WarParty> siege, long lastRaid, long truceUntil,
                   Map<UUID, Long> allies, long beatenOn) {
            this(siege, lastRaid, truceUntil, allies, beatenOn, Optional.empty(), UNSEEN_DAY);
        }

        public War(Optional<WarParty> siege, long lastRaid, long truceUntil,
                   Map<UUID, Long> allies, long beatenOn,
                   Optional<UUID> tributeTo, long tributeUntil) {
            this(siege, lastRaid, truceUntil, allies, beatenOn, tributeTo, tributeUntil,
                    Optional.empty());
        }

        /** То же военное положение, но с другой данью. */
        public War paying(Optional<UUID> to, long until) {
            return new War(siege, lastRaid, truceUntil, allies, beatenOn, to, until, marchingOn);
        }

        /** То же, но с другим отрядом у ворот. */
        public War besieged(Optional<WarParty> party) {
            return new War(party, lastRaid, truceUntil, allies, beatenOn, tributeTo, tributeUntil,
                    marchingOn);
        }

        /** То же, но набег пришёл в этот день. */
        public War raidedOn(long day) {
            return new War(siege, day, truceUntil, allies, beatenOn, tributeTo, tributeUntil,
                    marchingOn);
        }

        /** То же, но с перемирием до этого дня. */
        public War restingUntil(long day) {
            return new War(siege, lastRaid, day, allies, beatenOn, tributeTo, tributeUntil,
                    marchingOn);
        }

        /** То же, но с другими союзниками. */
        public War allied(Map<UUID, Long> with) {
            return new War(siege, lastRaid, truceUntil, with, beatenOn, tributeTo, tributeUntil,
                    marchingOn);
        }

        /** То же, но отряд этого поселения перебит в этот день. */
        public War beatenOnDay(long day) {
            return new War(siege, lastRaid, truceUntil, allies, day, tributeTo, tributeUntil,
                    marchingOn);
        }

        /** То же, но отряд колонии вышел в поход — или вернулся. */
        public War marching(Optional<UUID> target) {
            return new War(siege, lastRaid, truceUntil, allies, beatenOn, tributeTo,
                    tributeUntil, target);
        }

        public static final Codec<War> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                WarParty.CODEC.optionalFieldOf("siege").forGetter(War::siege),
                Codec.LONG.optionalFieldOf("last_raid", UNSEEN_DAY).forGetter(War::lastRaid),
                Codec.LONG.optionalFieldOf("truce_until", UNSEEN_DAY).forGetter(War::truceUntil),
                // Союз лежит в военном положении, и это не натяжка: союз —
                // это ровно «за кого вступятся», то есть то же самое, о чём
                // и остальные три поля. Заодно в главной группе кодека
                // остаётся свободное место: у Mojang их ровно шестнадцать,
                // и одно уже съела война.
                Codec.unboundedMap(Uuids.STRING_CODEC, Codec.LONG)
                        .optionalFieldOf("allies", Map.of()).forGetter(War::allies),
                // День разгрома лежит здесь же, и это не случайность: дань
                // берут со страха, а страх у деревни ровно один — тот отряд,
                // который ушёл и не вернулся.
                Codec.LONG.optionalFieldOf("beaten_on", UNSEEN_DAY).forGetter(War::beatenOn),
                // Дань лежит рядом с союзом, и это не теснота кодека: союз
                // и дань — одно и то же отношение, вывернутое наизнанку.
                // За одного вступаются, от другого откупаются.
                Uuids.STRING_CODEC.optionalFieldOf("tribute_to").forGetter(War::tributeTo),
                Codec.LONG.optionalFieldOf("tribute_until", UNSEEN_DAY)
                        .forGetter(War::tributeUntil),
                // На кого идёт отряд этой колонии. Сам отряд лежит
                // у осаждаемого — «кто у моих ворот» спрашивают там, —
                // а здесь только имя цели, и заведено оно ради одного
                // вопроса: «в походе ли моя стража». Спрашивают его
                // каждое решение каждого жителя, и обходить ради него
                // все поселения мира было бы дорого.
                Uuids.STRING_CODEC.optionalFieldOf("marching_on").forGetter(War::marchingOn)
        ).apply(instance, War::new));
    }

    private final UUID id;
    private final Identifier culture;
    private Owner owner;
    private String name;
    private final BlockPos center;
    private SettlementLevel level;
    private SettlementStats stats;
    private final List<Building> buildings;
    private final List<Citizen> citizens;

    /**
     * Последний игровой день, за который посчитаны суточные нужды.
     * <p>
     * {@link #UNSEEN_DAY} значит «ещё не видели»: только что основанная
     * колония не должна проголодаться в тот же тик.
     */
    private long lastDay;

    /**
     * Доверие деревни к каждому игроку и выполненные им квесты.
     * <p>
     * Лежит в <b>поселении</b>, а не на игроке, и это не случайно: отношение
     * — это отношение <i>деревни</i>, и на сервере их у одного игрока столько
     * же, сколько деревень. Хранение на игроке потребовало бы либо компонента
     * с чужой библиотекой, либо второй карты «игрок → деревня», которая
     * умеет разойтись с первой.
     */
    private final Map<UUID, Integer> reputation;
    private final Map<UUID, List<Identifier>> questsDone;

    /**
     * Обозы, стоящие у этого поселения прямо сейчас.
     * <p>
     * Гости, а не свои: чужой торговец приходит на день, торгует и уходит.
     * Хранятся <b>у принимающего</b>, а не у пославшего, потому что
     * спрашивают о них здесь: игрок подошёл к своей колонии — есть ли
     * кто в гостях.
     */
    private final List<Caravan> visitors;

    /**
     * В какой день этот игрок последний раз что-то дарил.
     * <p>
     * День, а не счётчик: подарок принимают раз в сутки, и хранить
     * «сколько уже подарено» значило бы обнулять счётчик на смене дня —
     * то есть помнить день всё равно, только двумя полями вместо одного.
     */
    private final Map<UUID, Long> giftDays;

    /**
     * Отряд, стоящий у этого поселения прямо сейчас.
     * <p>
     * Один, а не список: беда приходит по одной. Пока отряд не ушёл,
     * второго не пошлют — и это не упрощение, а решение по игре. Две
     * осады сразу превратили бы наказание за разбой в неиграбельную
     * лавину, из которой нет выхода.
     * <p>
     * День последнего набега хранится здесь же, а не у пославшей деревни:
     * остыть должна <b>осаждаемая</b> сторона. Иначе три обиженные деревни
     * присылали бы отряды в три дня подряд, каждая по своему счёту.
     * <p>
     * А перемирие — наоборот, у <b>посылающей</b>: это её решение не идти,
     * а не чужая защита.
     */
    private War war = War.NONE;

    /**
     * Чем поселение обязано небу и что небо ему должно.
     * <p>
     * Лежит здесь, а не в отдельном хранилище, по тому же правилу, что
     * и всё остальное состояние: поселение — единственная запись, которая
     * переживает перезаход в мир, и вера обязана переживать его вместе
     * с ним. Отдельная карта «поселение → вера» умеет разойтись с этой.
     */
    private Faith faith = Faith.NONE;

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens) {
        this(id, culture, owner, name, center, level, stats, buildings, citizens, UNSEEN_DAY);
    }

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens, long lastDay) {
        this(id, culture, owner, name, center, level, stats, buildings, citizens, lastDay,
                Map.of(), Map.of(), List.of(), Map.of());
    }

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens, long lastDay,
                      Map<UUID, Integer> reputation, Map<UUID, List<Identifier>> questsDone,
                      List<Caravan> visitors) {
        this(id, culture, owner, name, center, level, stats, buildings, citizens, lastDay,
                reputation, questsDone, visitors, Map.of());
    }

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens, long lastDay,
                      Map<UUID, Integer> reputation, Map<UUID, List<Identifier>> questsDone,
                      List<Caravan> visitors, Map<UUID, Long> giftDays) {
        this(id, culture, owner, name, center, level, stats, buildings, citizens, lastDay,
                reputation, questsDone, visitors, giftDays, War.NONE);
    }

    /** Совместимость: осада и день набега по отдельности, без перемирия. */
    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens, long lastDay,
                      Map<UUID, Integer> reputation, Map<UUID, List<Identifier>> questsDone,
                      List<Caravan> visitors, Map<UUID, Long> giftDays,
                      Optional<WarParty> siege, long lastRaid) {
        this(id, culture, owner, name, center, level, stats, buildings, citizens, lastDay,
                reputation, questsDone, visitors, giftDays, new War(siege, lastRaid, UNSEEN_DAY));
    }

    /** Поселение без веры: небо о нём ещё не знает. */
    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens, long lastDay,
                      Map<UUID, Integer> reputation, Map<UUID, List<Identifier>> questsDone,
                      List<Caravan> visitors, Map<UUID, Long> giftDays, War war) {
        this(id, culture, owner, name, center, level, stats, buildings, citizens, lastDay,
                reputation, questsDone, visitors, giftDays, war, Faith.NONE);
    }

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens, long lastDay,
                      Map<UUID, Integer> reputation, Map<UUID, List<Identifier>> questsDone,
                      List<Caravan> visitors, Map<UUID, Long> giftDays, War war, Faith faith) {
        this.id = id;
        this.culture = culture;
        this.owner = owner;
        this.name = name;
        this.center = center;
        this.level = level;
        this.stats = stats;
        this.buildings = new ArrayList<>(buildings);
        this.citizens = new ArrayList<>(citizens);
        this.lastDay = lastDay;
        this.reputation = new LinkedHashMap<>(reputation);
        this.questsDone = new LinkedHashMap<>();
        questsDone.forEach((player, quests) -> this.questsDone.put(player, new ArrayList<>(quests)));
        this.visitors = new ArrayList<>(visitors);
        this.giftDays = new LinkedHashMap<>(giftDays);
        this.war = war;
        this.faith = faith;
    }

    // --- вера ---

    public Faith faith() {
        return faith;
    }

    /** Сколько благосклонности набрано у этого бога. */
    public int favourOf(Identifier god) {
        return faith.favour().getOrDefault(god, 0);
    }

    /**
     * Прибавить (или убавить) благосклонность.
     * <p>
     * Ниже нуля не падает, и это решение по игре: бог мода не гневается.
     * Заказчик сказал прямо — «жертва одному не злит другого»; отрицательная
     * благосклонность была бы той же обидой, только с другой стороны,
     * и превращала бы пантеон в счёт долгов.
     */
    public void addFavour(Identifier god, int amount) {
        Map<Identifier, Integer> next = new LinkedHashMap<>(faith.favour());
        next.merge(god, amount, Integer::sum);
        next.put(god, Math.max(0, next.get(god)));
        this.faith = new Faith(next, faith.lastOffering(), faith.blessedUntil(), faith.artifacts());
    }

    /** В какой день этому богу последний раз клали на алтарь. */
    public long offeredOn(Identifier god) {
        return faith.lastOffering().getOrDefault(god, UNSEEN_DAY);
    }

    /**
     * Принимал ли этот бог жертву сегодня.
     * <p>
     * День, а не счётчик: жертва принимается раз в сутки, и хранить
     * «сколько уже положили» значило бы обнулять счётчик на смене дня —
     * то есть помнить день всё равно, только двумя полями вместо одного.
     * Тот же выбор, что у подарков деревне.
     */
    public boolean offeredToday(Identifier god, long today) {
        return offeredOn(god) == today;
    }

    public void noteOffering(Identifier god, long today) {
        Map<Identifier, Long> next = new LinkedHashMap<>(faith.lastOffering());
        next.put(god, today);
        this.faith = new Faith(faith.favour(), next, faith.blessedUntil(), faith.artifacts());
    }

    /** До какого дня держится благословение этого домена. */
    public long blessedUntil(String domain) {
        return faith.blessedUntil().getOrDefault(domain, UNSEEN_DAY);
    }

    /** Держится ли благословение этого домена сегодня. */
    public boolean isBlessed(String domain, long today) {
        return today < blessedUntil(domain);
    }

    /** Сколько дней благословения осталось: ноль, если его нет. */
    public int blessingDaysLeft(String domain, long today) {
        return isBlessed(domain, today)
                ? (int) Math.min(Integer.MAX_VALUE, blessedUntil(domain) - today) : 0;
    }

    /**
     * Благословить до названного дня.
     * <p>
     * От <b>сегодня</b> или от конца уже идущего благословения, смотря что
     * дальше: второй призыв должен добавлять дней, а не отсчитывать их
     * заново. То же правило, что у перемирия, и по той же причине —
     * иначе игрок, призвавший дважды, терял бы оплаченное.
     */
    public void bless(String domain, long today, int days) {
        Map<String, Long> next = new LinkedHashMap<>(faith.blessedUntil());
        next.put(domain, Math.max(today, blessedUntil(domain)) + days);
        this.faith = new Faith(faith.favour(), faith.lastOffering(), next, faith.artifacts());
    }

    /** Вручён ли уже этот артефакт. */
    public boolean hasArtifact(Identifier artifact) {
        return faith.artifacts().contains(artifact);
    }

    /** Запомнить, что артефакт вручён: второго не дают никогда. */
    public void noteArtifact(Identifier artifact) {
        if (hasArtifact(artifact)) {
            return;
        }
        List<Identifier> next = new ArrayList<>(faith.artifacts());
        next.add(artifact);
        this.faith = new Faith(faith.favour(), faith.lastOffering(), faith.blessedUntil(), next);
    }

    // --- осада ---

    public War war() {
        return war;
    }

    public Optional<WarParty> siege() {
        return war.siege();
    }

    public long lastRaid() {
        return war.lastRaid();
    }

    /** До какого дня поселение никого не посылает. */
    public long truceUntil() {
        return war.truceUntil();
    }

    /**
     * Держит ли деревня перемирие в этот день.
     * <p>
     * Перемирие — это <b>время</b>, а не прощение: доверие оно не меняет
     * ни на очко. Куплено оно кровью своих или чужой монетой — в числе
     * не различить, и различать незачем: деревня просто никуда не идёт.
     */
    public boolean atTruce(long today) {
        return war.truceUntil() != UNSEEN_DAY && today < war.truceUntil();
    }

    /**
     * День, когда отряд этого поселения перебили под чужими воротами.
     * <p>
     * {@link #UNSEEN_DAY} значит «не били никогда». По этому дню считается
     * право требовать дань: деревня платит не сильному вообще, а тому,
     * кто <b>только что</b> положил её людей. Страх не вечен.
     */
    public long beatenOn() {
        return war.beatenOn();
    }

    /**
     * Платит ли это поселение дань названному игроку прямо сейчас.
     * <p>
     * Срок проверяется здесь, а не при платеже: дань, которая кончилась,
     * должна и в разговоре выглядеть кончившейся.
     */
    public boolean owesTributeTo(UUID player, long today) {
        return war.tributeTo().filter(player::equals).isPresent() && today < war.tributeUntil();
    }

    /** Сколько дней дани осталось: ноль, если её нет. */
    public int tributeDaysLeft(long today) {
        return war.tributeTo().isPresent() && today < war.tributeUntil()
                ? (int) Math.min(Integer.MAX_VALUE, war.tributeUntil() - today) : 0;
    }

    /** Кому платят дань, если платят. */
    public Optional<UUID> tributeTo() {
        return war.tributeTo();
    }

    /** Обложить данью до названного дня. */
    public void startTribute(UUID player, long until) {
        this.war = war.paying(Optional.of(player), until);
    }

    /** Дань кончилась: платить больше некому и нечем. */
    public void stopTribute() {
        this.war = war.paying(Optional.empty(), UNSEEN_DAY);
    }

    /**
     * Разорвать союз.
     * <p>
     * Нужен требованию дани: вступаться за человека и откупаться от него
     * разом нельзя, и выбирает тут игрок — тем, что пришёл требовать.
     */
    public void breakAlly(UUID player) {
        if (!war.allies().containsKey(player)) {
            return;
        }
        Map<UUID, Long> left = new java.util.LinkedHashMap<>(war.allies());
        left.remove(player);
        this.war = war.allied(left);
    }

    /**
     * На кого вышел отряд этой колонии, если вышел.
     * <p>
     * Только имя цели: сам отряд лежит у осаждаемого, как и всё в этом
     * моде — «кто у моих ворот» спрашивают там. Здесь оно заведено ради
     * единственного вопроса «в походе ли моя стража», и спрашивают его
     * на каждом решении каждого стража.
     */
    public Optional<UUID> marchingOn() {
        return war.marchingOn();
    }

    /** Отряд вышел. */
    public void marchOn(UUID village) {
        this.war = war.marching(Optional.of(village));
    }

    /** Отряд вернулся — целым, поредевшим или не вернулся вовсе. */
    public void cameHome() {
        this.war = war.marching(Optional.empty());
    }

    /** Отряд перебит: запомнить день. */
    public void beaten(long today) {
        this.war = war.beatenOnDay(today);
    }

    /**
     * В союзе ли поселение с этим игроком — и держится ли союз.
     * <p>
     * Союз держится <b>дружбой</b>, а не записью: упало доверие ниже
     * дружбы — и союзники за игрока больше не вступаются, хотя запись
     * цела. Так союз нельзя купить один раз и забыть, и так же его
     * не надо расторгать отдельной кнопкой: он расторгается делами.
     */
    public boolean isAllyOf(UUID player) {
        return war.allies().containsKey(player)
                && reputationOf(player) >= Standing.FRIEND.from();
    }

    /** С какого дня заключён союз, если он есть. */
    public Optional<Long> allySince(UUID player) {
        return Optional.ofNullable(war.allies().get(player));
    }

    /** Заключить союз с игроком. */
    public void makeAlly(UUID player, long today) {
        Map<UUID, Long> allies = new java.util.LinkedHashMap<>(war.allies());
        allies.put(player, today);
        this.war = war.allied(allies);
    }

    /** Сколько дней тишины ещё осталось: ноль, если перемирия нет. */
    public int truceDaysLeft(long today) {
        return atTruce(today) ? (int) Math.min(Integer.MAX_VALUE, war.truceUntil() - today) : 0;
    }

    /**
     * Не воевать ещё столько дней.
     * <p>
     * От <b>сегодня</b> или от конца уже идущего перемирия, смотря что
     * дальше: второй павший боец должен добавлять тишины, а не заново
     * отсчитывать её от полудня.
     */
    public void restFor(long today, int days) {
        // UNSEEN_DAY — это «давно прошло», и max с ним возвращает сегодня:
        // отдельной проверки «перемирия ещё не было» не нужно.
        this.war = war.restingUntil(Math.max(today, war.truceUntil()) + days);
    }

    /** Отряд встал у ворот. День запоминается сразу: остывать начинают с прихода. */
    public void besiege(WarParty party, long today) {
        this.war = war.besieged(Optional.of(party)).raidedOn(today);
    }

    /** Отряд поредел или ушёл: пустой отряд снимается с поселения. */
    public void updateSiege(WarParty party) {
        Optional<WarParty> left = party.fighters() <= 0 ? Optional.empty() : Optional.of(party);
        this.war = war.besieged(left);
    }

    public void liftSiege() {
        this.war = war.besieged(Optional.empty());
    }

    // --- гости ---

    public List<Caravan> visitors() {
        return java.util.Collections.unmodifiableList(visitors);
    }

    public void welcome(Caravan caravan) {
        visitors.add(caravan);
    }

    public java.util.Optional<Caravan> visitor(UUID caravanId) {
        return visitors.stream().filter(guest -> guest.id().equals(caravanId)).findFirst();
    }

    /** Обоз уехал или разорился: запись уходит вместе с ним. */
    public boolean seeOff(UUID caravanId) {
        return visitors.removeIf(guest -> guest.id().equals(caravanId));
    }

    /**
     * Заменить запись обоза: у него убыл товар или монета.
     * <p>
     * Заменой, а не правкой на месте: {@link Caravan} — запись, и это
     * то же решение, что у здания и жителя. Менять неизменяемое нельзя,
     * а пересобрать дешевле, чем однажды разойтись с сохранением.
     */
    public void restock(Caravan fresh) {
        for (int index = 0; index < visitors.size(); index++) {
            if (visitors.get(index).id().equals(fresh.id())) {
                visitors.set(index, fresh);
                return;
            }
        }
    }

    public static Settlement found(Identifier culture, Owner owner, String name, BlockPos center) {
        return new Settlement(UUID.randomUUID(), culture, owner, name, center,
                SettlementLevel.HAMLET, SettlementStats.INITIAL, List.of(), List.of());
    }

    public UUID id() {
        return id;
    }

    public Identifier culture() {
        return culture;
    }

    public Owner owner() {
        return owner;
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    public String name() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    public BlockPos center() {
        return center;
    }

    public SettlementLevel level() {
        return level;
    }

    public void setLevel(SettlementLevel level) {
        this.level = level;
    }

    public SettlementStats stats() {
        return stats;
    }

    /** Какую долю заработанного колония забирает себе. */
    public int taxRate() {
        return stats.taxRate();
    }

    /** Поменять ставку. Ограничение живёт в самой записи, а не здесь. */
    public void setTaxRate(int rate) {
        this.stats = stats.withTaxRate(rate);
    }

    public void setStats(SettlementStats stats) {
        this.stats = stats;
    }

    public List<Building> buildings() {
        return Collections.unmodifiableList(buildings);
    }

    /**
     * Здания в порядке очереди: сперва важные, потом по времени заказа.
     * <p>
     * Порядок заказа сохраняется при равной важности намеренно — иначе
     * список прыгал бы от решения к решению, а с ним и билдер: он берётся
     * за первую подходящую стройку, и «первая» обязана быть устойчивой.
     */
    public List<Building> byPriority() {
        List<Building> queue = new ArrayList<>(buildings);
        queue.sort(Comparator.comparingInt(Building::priority).reversed());
        return queue;
    }

    public List<Citizen> citizens() {
        return Collections.unmodifiableList(citizens);
    }

    public void addBuilding(Building building) {
        buildings.add(building);
    }

    public void addCitizen(Citizen citizen) {
        citizens.add(citizen);
    }

    public boolean removeCitizen(UUID citizenId) {
        return citizens.removeIf(citizen -> citizen.id().equals(citizenId));
    }

    /**
     * Забыть здание.
     * <p>
     * Единственный способ убрать стройку из поселения, и он нужен ровно
     * одному месту: подарку, который не встал. Недостроенное здание
     * в списке — это не «почти готово», а <b>вечная работа для билдера</b>:
     * он берётся за неё первой, до всего, что заказал игрок.
     */
    public boolean removeBuilding(UUID buildingId) {
        return buildings.removeIf(site -> site.id().equals(buildingId));
    }

    public Optional<Building> building(UUID buildingId) {
        return buildings.stream().filter(building -> building.id().equals(buildingId)).findFirst();
    }

    public Optional<Citizen> citizen(UUID citizenId) {
        return citizens.stream().filter(citizen -> citizen.id().equals(citizenId)).findFirst();
    }

    public long lastDay() {
        return lastDay;
    }

    public void setLastDay(long lastDay) {
        this.lastDay = lastDay;
    }

    public boolean hasSeenADay() {
        return lastDay != UNSEEN_DAY;
    }

    // --- доверие и квесты ---

    public Map<UUID, Integer> reputation() {
        return Collections.unmodifiableMap(reputation);
    }

    public Map<UUID, List<Identifier>> questsDone() {
        return Collections.unmodifiableMap(questsDone);
    }

    public int reputationOf(UUID player) {
        return reputation.getOrDefault(player, 0);
    }

    public Standing standingOf(UUID player) {
        return Standing.of(reputationOf(player));
    }

    public void addReputation(UUID player, int amount) {
        reputation.merge(player, amount, Integer::sum);
    }

    /**
     * Есть ли у деревни мнение об этом игроке.
     * <p>
     * Отличается от «доверие равно нулю»: у только что познакомившегося
     * игрока ноль, и у незнакомого ноль, а это разные вещи. Первый
     * считается в среднем счёте народа, второй — нет: подмешивать мнение
     * деревни, которой игрок в глаза не видел, значило бы наказывать его
     * за существование деревень, которых он не встречал.
     */
    public boolean knows(UUID player) {
        return reputation.containsKey(player);
    }

    // --- подарки ---

    public Map<UUID, Long> giftDays() {
        return Collections.unmodifiableMap(giftDays);
    }

    /**
     * В какой день этот игрок дарил здесь последний раз.
     * {@link #UNSEEN_DAY} — не дарил никогда.
     */
    public long giftedOn(UUID player) {
        return giftDays.getOrDefault(player, UNSEEN_DAY);
    }

    public void noteGift(UUID player, long day) {
        giftDays.put(player, day);
    }

    /** Что этот игрок здесь уже сделал. Пустой список — не значит «никогда». */
    public List<Identifier> questsDone(UUID player) {
        return Collections.unmodifiableList(questsDone.getOrDefault(player, List.of()));
    }

    public void noteQuestDone(UUID player, Identifier quest) {
        questsDone.computeIfAbsent(player, ignored -> new ArrayList<>()).add(quest);
    }

    public int population() {
        return citizens.size();
    }

    /**
     * Предел жителей с учётом настройки.
     * <p>
     * Множителем, а не числом на уровень: уровней будет больше, и таблица
     * из четырёх чисел в настройках устарела бы с первым же новым уровнем.
     * Хотя бы один житель помещается всегда — колония из нуля человек
     * не колония, а поломка.
     */
    public int maxCitizens() {
        return Math.max(1, (int) Math.round(level.maxCitizens()
                * Configs.get().populationScale()));
    }

    public boolean hasRoomForCitizen() {
        return population() < maxCitizens();
    }

    public ChunkPos centerChunk() {
        return new ChunkPos(center);
    }

    /**
     * Границы поселения заданы радиусом в чанках от ратуши, а не набором чанков:
     * для среза 0.1 этого достаточно, а хранить и синхронизировать нечего.
     */
    public boolean claims(ChunkPos chunk) {
        ChunkPos origin = centerChunk();
        int radius = level.claimRadiusChunks();
        return Math.abs(chunk.x - origin.x) <= radius && Math.abs(chunk.z - origin.z) <= radius;
    }

    public boolean claims(BlockPos pos) {
        return claims(new ChunkPos(pos));
    }

    /** Пересечение границ — повод для спора за территорию, а позже и для войны. */
    public boolean overlaps(Settlement other) {
        ChunkPos a = centerChunk();
        ChunkPos b = other.centerChunk();
        int reach = level.claimRadiusChunks() + other.level.claimRadiusChunks();
        return Math.abs(a.x - b.x) <= reach && Math.abs(a.z - b.z) <= reach;
    }
}
