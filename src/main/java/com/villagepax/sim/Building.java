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
            Uuids.STRING_CODEC.listOf().optionalFieldOf("workers", List.of()).forGetter(Building::workers),
            Codec.INT.optionalFieldOf("next_step", 0).forGetter(Building::nextStep),
            ItemTally.CODEC.optionalFieldOf("stock", new ItemTally()).forGetter(Building::stock),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Building::priority)
    ).apply(instance, Building::new));

    private final UUID id;
    private final Identifier type;
    private int level;

    /**
     * Угол следа здания. Неизменяем: улучшение здание не переносит.
     * <p>
     * Схема следующего уровня содержит предыдущий на тех же местах
     * и растёт только вверх, на восток и на юг — это правило проверяет
     * генератор схем при сборке. Поэтому улучшению переносить нечего,
     * а игрок не теряет выбранное им место.
     */
    private final BlockPos anchor;
    private final BlockRotation rotation;
    private BuildProgress progress;
    private final List<UUID> workers;

    /**
     * Сколько шагов плана стройки уже выполнено.
     * <p>
     * Лежит здесь, а не в билдере, намеренно: билдер может выгрузиться вместе
     * с чанком, погибнуть или сменить работу, и стройка от этого не должна
     * начинаться заново. Это то же решение, что и «житель — данные, тело —
     * временная сущность».
     */
    private int nextStep;

    /**
     * Материалы, сложенные на стройплощадке.
     * <p>
     * Это <b>не</b> дубль контейнера: физически они нигде не лежат, их принёс
     * курьер и сложил у стройки. Поэтому расхождения, из-за которого склад
     * колонии стал видом поверх сундуков, здесь нет и быть не может.
     */
    private final ItemTally stock;

    /**
     * Насколько эта стройка важнее прочих.
     * <p>
     * Заказал три дома — решаешь, какой первым. Без этого билдер брался
     * за первую попавшуюся стройку, то есть за ту, что размечена раньше,
     * и переставить порядок было нечем: приходилось отменять заказы
     * и размечать заново.
     */
    private int priority;

    public Building(UUID id, Identifier type, int level, BlockPos anchor, BlockRotation rotation,
                    BuildProgress progress, List<UUID> workers) {
        this(id, type, level, anchor, rotation, progress, workers, 0);
    }

    public Building(UUID id, Identifier type, int level, BlockPos anchor, BlockRotation rotation,
                    BuildProgress progress, List<UUID> workers, int nextStep) {
        this(id, type, level, anchor, rotation, progress, workers, nextStep, new ItemTally());
    }

    public Building(UUID id, Identifier type, int level, BlockPos anchor, BlockRotation rotation,
                    BuildProgress progress, List<UUID> workers, int nextStep, ItemTally stock) {
        this(id, type, level, anchor, rotation, progress, workers, nextStep, stock, 0);
    }

    public Building(UUID id, Identifier type, int level, BlockPos anchor, BlockRotation rotation,
                    BuildProgress progress, List<UUID> workers, int nextStep, ItemTally stock,
                    int priority) {
        this.priority = priority;
        this.id = id;
        this.type = type;
        this.level = level;
        this.anchor = anchor;
        this.rotation = rotation;
        this.progress = progress;
        this.workers = new ArrayList<>(workers);
        this.nextStep = Math.max(0, nextStep);
        this.stock = stock;
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

    public ItemTally stock() {
        return stock;
    }

    public int priority() {
        return priority;
    }

    /** Подвинуть в очереди. Отрицательное значит «потом». */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int nextStep() {
        return nextStep;
    }

    public void setNextStep(int nextStep) {
        this.nextStep = Math.max(0, nextStep);
    }

    public void advanceStep() {
        nextStep++;
    }

    /** Стройка заново: после апгрейда и после повреждения план проходится с начала. */
    public void restartBuilding() {
        nextStep = 0;
        progress = BuildProgress.BUILDING;
    }
}
