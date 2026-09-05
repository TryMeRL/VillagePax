package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.UUID;

/**
 * Житель как данные.
 * <p>
 * Это несущее решение по производительности: житель существует всегда, а его
 * тело — сущность в мире — появляется только при загруженном чанке и исчезает
 * при выгрузке. Всё, что должно пережить выгрузку, лежит здесь, поэтому
 * лесоруб после перезахода в мир продолжит с того же шага, а не начнёт заново.
 * Существующие моды такого класса ломаются именно на том, что держат состояние
 * в сущности, и сотни сущностей душат сервер.
 */
public class Citizen {

    /** Ниже этого порога житель работает вполсилы, а затем уходит из колонии. */
    public static final int UNHAPPY_THRESHOLD = 30;

    public static final float MAX_HEALTH = 20.0f;

    public static final Codec<Citizen> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("id").forGetter(Citizen::id),
            Codec.STRING.fieldOf("first_name").forGetter(Citizen::firstName),
            Codec.STRING.optionalFieldOf("last_name", "").forGetter(Citizen::lastName),
            Identifier.CODEC.fieldOf("culture").forGetter(Citizen::culture),
            Gender.CODEC.fieldOf("gender").forGetter(Citizen::gender),
            Codec.LONG.optionalFieldOf("age_ticks", 0L).forGetter(Citizen::ageTicks),
            Identifier.CODEC.optionalFieldOf("profession").forGetter(Citizen::profession),
            Codec.INT.optionalFieldOf("happiness", 70).forGetter(Citizen::happiness),
            Codec.INT.optionalFieldOf("saturation", 20).forGetter(Citizen::saturation),
            Uuids.STRING_CODEC.optionalFieldOf("home").forGetter(Citizen::home),
            Uuids.STRING_CODEC.optionalFieldOf("workplace").forGetter(Citizen::workplace),
            Vec3d.CODEC.optionalFieldOf("position").forGetter(Citizen::position),
            Codec.FLOAT.optionalFieldOf("health", MAX_HEALTH).forGetter(Citizen::health)
    ).apply(instance, Citizen::new));

    private final UUID id;
    private String firstName;
    private String lastName;
    private final Identifier culture;
    private final Gender gender;
    private long ageTicks;
    private Optional<Identifier> profession;
    private int happiness;
    private int saturation;
    private Optional<UUID> home;
    private Optional<UUID> workplace;
    private Optional<Vec3d> position;
    private float health;

    /**
     * Живое тело жителя, если оно сейчас есть в мире.
     * <p>
     * Намеренно не сохраняется: сущности жителей не пишутся в чанк, поэтому
     * после перезапуска сервера любой такой идентификатор был бы протухшим.
     */
    private UUID entityUuid;

    public Citizen(UUID id, String firstName, String lastName, Identifier culture, Gender gender,
                   long ageTicks, Optional<Identifier> profession, int happiness, int saturation,
                   Optional<UUID> home, Optional<UUID> workplace, Optional<Vec3d> position,
                   float health) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.culture = culture;
        this.gender = gender;
        this.ageTicks = ageTicks;
        this.profession = profession;
        this.happiness = happiness;
        this.saturation = saturation;
        this.home = home;
        this.workplace = workplace;
        this.position = position;
        this.health = health;
    }

    public static Citizen newborn(String firstName, String lastName, Identifier culture, Gender gender) {
        return new Citizen(UUID.randomUUID(), firstName, lastName, culture, gender, 0L,
                Optional.empty(), 70, 20, Optional.empty(), Optional.empty(), Optional.empty(), MAX_HEALTH);
    }

    public UUID id() {
        return id;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String fullName() {
        return lastName.isEmpty() ? firstName : firstName + " " + lastName;
    }

    public void rename(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public Identifier culture() {
        return culture;
    }

    public Gender gender() {
        return gender;
    }

    public long ageTicks() {
        return ageTicks;
    }

    public void addAge(long ticks) {
        this.ageTicks += ticks;
    }

    public Optional<Identifier> profession() {
        return profession;
    }

    public void setProfession(Identifier profession) {
        this.profession = Optional.ofNullable(profession);
    }

    public int happiness() {
        return happiness;
    }

    public void setHappiness(int value) {
        this.happiness = Math.max(0, Math.min(SettlementStats.MAX_HAPPINESS, value));
    }

    public boolean isUnhappy() {
        return happiness < UNHAPPY_THRESHOLD;
    }

    public int saturation() {
        return saturation;
    }

    public void setSaturation(int value) {
        this.saturation = Math.max(0, value);
    }

    public Optional<UUID> home() {
        return home;
    }

    public void setHome(UUID building) {
        this.home = Optional.ofNullable(building);
    }

    public Optional<UUID> workplace() {
        return workplace;
    }

    public void setWorkplace(UUID building) {
        this.workplace = Optional.ofNullable(building);
    }

    public Optional<Vec3d> position() {
        return position;
    }

    public void setPosition(Vec3d position) {
        this.position = Optional.ofNullable(position);
    }

    /**
     * Здоровье хранится в данных, а не только в теле: иначе раненый житель
     * полностью исцелялся бы каждый раз, когда игрок отходит и возвращается,
     * и осада из фазы 3 перестала бы работать как механика.
     */
    public float health() {
        return health;
    }

    public void setHealth(float health) {
        this.health = Math.max(0.0f, Math.min(MAX_HEALTH, health));
    }

    public Optional<UUID> entityUuid() {
        return Optional.ofNullable(entityUuid);
    }

    public void setEntityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public boolean isHomeless() {
        return home.isEmpty();
    }

    public boolean isUnemployed() {
        return workplace.isEmpty();
    }
}
