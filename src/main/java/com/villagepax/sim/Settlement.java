package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
            Warehouse.CODEC.optionalFieldOf("warehouse", new Warehouse()).forGetter(Settlement::warehouse)
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
    private final Warehouse warehouse;

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens) {
        this(id, culture, owner, name, center, level, stats, buildings, citizens, new Warehouse());
    }

    public Settlement(UUID id, Identifier culture, Owner owner, String name, BlockPos center,
                      SettlementLevel level, SettlementStats stats,
                      List<Building> buildings, List<Citizen> citizens, Warehouse warehouse) {
        this.id = id;
        this.culture = culture;
        this.owner = owner;
        this.name = name;
        this.center = center;
        this.level = level;
        this.stats = stats;
        this.buildings = new ArrayList<>(buildings);
        this.citizens = new ArrayList<>(citizens);
        this.warehouse = warehouse;
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

    public Warehouse warehouse() {
        return warehouse;
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
