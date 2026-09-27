package com.villagepax.entity.festival;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Зверёк ловли: у него есть загон, и он знает, чьё состязание.
 * <p>
 * Общее у пяти зверьков — поле {@link Pen}: наследуют они пятерых разных
 * ванильных животных, и общего предка, кроме ванильного, у них нет.
 */
public interface PenRunner {

    /** Загон зверька и его состязание. */
    Pen pen();

    /**
     * Во сколько раз быстрее шага зверёк удирает.
     * <p>
     * Решение по игре: идущего человека зверёк обгоняет, бегущего — нет.
     * Шаг ванильного животного растёт как квадрат скорости, поэтому числа
     * подобраны под каждый вид, а не одно на всех.
     */
    double dash();

    /** Клетки загона и опознаватель состязания. */
    final class Pen {

        private List<BlockPos> cells = List.of();
        private Set<Long> columns = Set.of();
        private UUID match;

        /** Посадить в загон. Состязание пусто — зверёк сам по себе. */
        public void enter(List<BlockPos> cells, UUID match) {
            this.cells = List.copyOf(cells);
            Set<Long> columns = new HashSet<>();
            cells.forEach(cell -> columns.add(BlockPos.asLong(cell.getX(), 0, cell.getZ())));
            this.columns = Set.copyOf(columns);
            this.match = match;
        }

        public List<BlockPos> cells() {
            return cells;
        }

        /** Стоит ли эта точка в загоне — по колонне, высота не в счёт. */
        public boolean holds(BlockPos pos) {
            return columns.contains(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
        }

        public Optional<UUID> match() {
            return Optional.ofNullable(match);
        }
    }
}
