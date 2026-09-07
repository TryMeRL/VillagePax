package com.villagepax.sim.build;

import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.List;

/**
 * Разобранная схема здания: размер, палитра блокстейтов и содержимое.
 * <p>
 * План стройки считается <b>лениво, при первом обращении</b>, и это не
 * оптимизация. Категория блока задаётся тегом {@code villagepax:build_decor},
 * а теги привязываются к записям реестра не во время слушателей перезагрузки,
 * а позже — отдельным вызовом {@code DataPackContents.refresh}. Считай план
 * при загрузке — и весь декор оказался бы несущим, молча и без единой ошибки
 * в логе. Ленивый расчёт верен независимо от порядка загрузки.
 * <p>
 * Инвалидировать кэш не нужно: перезагрузка датапака создаёт новые объекты
 * схем, а вместе с ними и пустой кэш. Промежуточного состояния нет.
 */
public final class Schematic {

    /** Блок схемы: позиция относительно якоря и номер в палитре. */
    public record PalettedBlock(BlockPos pos, int paletteIndex) {
        public PalettedBlock {
            pos = pos.toImmutable();
        }
    }

    private final Identifier id;
    private final Vec3i size;
    private final List<BlockState> palette;
    private final List<PalettedBlock> blocks;
    private final List<PointOfInterest> markers;

    private volatile BuildPlan plan;

    public Schematic(Identifier id, Vec3i size, List<BlockState> palette, List<PalettedBlock> blocks,
                     List<PointOfInterest> markers) {
        this.id = id;
        this.size = size;
        this.palette = List.copyOf(palette);
        this.blocks = List.copyOf(blocks);
        this.markers = List.copyOf(markers);
    }

    public Identifier id() {
        return id;
    }

    public Vec3i size() {
        return size;
    }

    public List<BlockState> palette() {
        return palette;
    }

    public List<PalettedBlock> blocks() {
        return blocks;
    }

    /** Точки интереса, найденные по маркерам схемы. */
    public List<PointOfInterest> markers() {
        return markers;
    }

    /**
     * План стройки. Первое обращение считает, дальнейшие возвращают готовый.
     * <p>
     * Двойная проверка с {@code volatile} нужна потому, что схемы разбираются
     * на рабочих потоках перезагрузки, а спрашивать план будет серверный поток.
     */
    public BuildPlan plan() {
        BuildPlan current = plan;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (plan == null) {
                plan = BuildPlanner.plan(size, palette, blocks, markers);
            }
            return plan;
        }
    }

    public BlockState blockAt(int paletteIndex) {
        return palette.get(paletteIndex);
    }

    /** Блок, который ставит этот шаг. Для расчистки блока нет. */
    public BlockState blockFor(BuildStep step) {
        if (!step.placesBlock()) {
            throw new IllegalArgumentException("шаг расчистки ничего не ставит: " + step.pos().toShortString());
        }
        return blockAt(step.paletteIndex());
    }
}
