package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.villagepax.core.config.Configs;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.villagepax.core.trade.Caravan;
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
                    .optionalFieldOf("gift_days", Map.of()).forGetter(Settlement::giftDays)
    ).apply(instance, Settlement::new));

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
