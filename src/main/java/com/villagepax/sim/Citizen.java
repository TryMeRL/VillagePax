package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

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
            Uuids.STRING_CODEC.optionalFieldOf("workplace").forGetter(Citizen::workplace)
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

    public Citizen(UUID id, String firstName, String lastName, Identifier culture, Gender gender,
                   long ageTicks, Optional<Identifier> profession, int happiness, int saturation,
                   Optional<UUID> home, Optional<UUID> workplace) {
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
    }

    public static Citizen newborn(String firstName, String lastName, Identifier culture, Gender gender) {
        return new Citizen(UUID.randomUUID(), firstName, lastName, culture, gender, 0L,
                Optional.empty(), 70, 20, Optional.empty(), Optional.empty());
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

    public boolean isHomeless() {
        return home.isEmpty();
    }

    public boolean isUnemployed() {
        return workplace.isEmpty();
    }
}
