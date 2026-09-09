package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
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
                    .optionalFieldOf("quests_done", Map.of()).forGetter(Settlement::questsDone)
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

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens) {
        this(id, culture, owner, name, center, level, stats, buildings, citizens, UNSEEN_DAY);
    }

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens, long lastDay) {
        this(id, culture, owner, name, center, level, stats, buildings, citizens, lastDay,
                Map.of(), Map.of());
    }

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens, long lastDay,
                      Map<UUID, Integer> reputation, Map<UUID, List<Identifier>> questsDone) {
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

    public boolean hasRoomForCitizen() {
        return population() < level.maxCitizens();
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
