package com.villagepax.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Здание в поселении. Изменяемый объект, а не запись: уровень, состояние
 * стройки и состав работников меняются в течение игры.
 * <p>
 * Геометрия здесь не хранится — она живёт в схеме {@code .nbt}, на которую
 * ссылается {@link #type()} и {@link #level()}. Позиция задаётся якорем
 * и поворотом, и от этого же якоря строятся все последующие уровни здания,
 * чтобы схемы при апгрейде не разъезжались.
 */
public class Building {

    public static final Codec<Building> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Uuids.STRING_CODEC.fieldOf("id").forGetter(Building::id),
            Identifier.CODEC.fieldOf("type").forGetter(Building::type),
            Codec.INT.optionalFieldOf("level", 1).forGetter(Building::level),
            BlockPos.CODEC.fieldOf("anchor").forGetter(Building::anchor),
            BlockRotation.CODEC.optionalFieldOf("rotation", BlockRotation.NONE).forGetter(Building::rotation),
            BuildProgress.CODEC.optionalFieldOf("progress", BuildProgress.PLANNED).forGetter(Building::progress),
            Uuids.STRING_CODEC.listOf().optionalFieldOf("workers", List.of()).forGetter(Building::workers)
    ).apply(instance, Building::new));

    private final UUID id;
    private final Identifier type;
    private int level;
    private final BlockPos anchor;
    private final BlockRotation rotation;
    private BuildProgress progress;
    private final List<UUID> workers;

    public Building(UUID id, Identifier type, int level, BlockPos anchor, BlockRotation rotation,
                    BuildProgress progress, List<UUID> workers) {
        this.id = id;
        this.type = type;
        this.level = level;
        this.anchor = anchor;
        this.rotation = rotation;
        this.progress = progress;
        this.workers = new ArrayList<>(workers);
    }

    public static Building planned(Identifier type, BlockPos anchor, BlockRotation rotation) {
        return new Building(UUID.randomUUID(), type, 1, anchor, rotation, BuildProgress.PLANNED, List.of());
    }

    public UUID id() {
        return id;
    }

    public Identifier type() {
        return type;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public BlockRotation rotation() {
        return rotation;
    }

    public BuildProgress progress() {
        return progress;
    }

    public void setProgress(BuildProgress progress) {
        this.progress = progress;
    }

    public List<UUID> workers() {
        return Collections.unmodifiableList(workers);
    }

    public boolean assign(UUID citizen) {
        return !workers.contains(citizen) && workers.add(citizen);
    }

    public boolean unassign(UUID citizen) {
        return workers.remove(citizen);
    }

    public boolean isOperational() {
        return progress == BuildProgress.DONE;
    }
}
