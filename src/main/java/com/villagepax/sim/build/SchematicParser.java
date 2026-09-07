package com.villagepax.sim.build;

import com.villagepax.VillagePax;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Разбор ванильного structure NBT.
 * <p>
 * Свой разбор, а не {@code StructureTemplate}: тот держит палитру и список
 * блоков в приватном поле и отдаёт наружу только позиции <b>одного</b> типа
 * блока, а стройке нужны все. Оставались access widener или круговой прогон
 * через {@code writeNbt} — обе сделки хуже тридцати строк своего кода.
 * <p>
 * Формат при этом остаётся ванильным, как и требует дизайн-документ: схему
 * можно сохранить структурным блоком прямо в игре и подложить файлом.
 */
public final class SchematicParser {

    private static final String SIZE = "size";
    private static final String PALETTE = "palette";
    private static final String BLOCKS = "blocks";
    private static final String POS = "pos";
    private static final String STATE = "state";
    private static final String NAME = "Name";

    /** Воздух в палитре — законная запись, а не пропавший блок. */
    private static final Set<String> AIR_NAMES =
            Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private SchematicParser() {
    }

    public static Schematic parse(Identifier id, NbtCompound nbt, RegistryEntryLookup<Block> blocks) {
        List<BlockState> palette = readPalette(id, nbt, blocks);
        return new Schematic(id, readSize(nbt), palette, readBlocks(nbt, palette.size()));
    }

    private static Vec3i readSize(NbtCompound nbt) {
        NbtList size = nbt.getList(SIZE, NbtElement.INT_TYPE);
        if (size.size() != 3) {
            throw new IllegalArgumentException("размер схемы задан " + size.size() + " числами вместо трёх");
        }
        Vec3i result = new Vec3i(size.getInt(0), size.getInt(1), size.getInt(2));
        if (result.getX() <= 0 || result.getY() <= 0 || result.getZ() <= 0) {
            throw new IllegalArgumentException("размер схемы неположителен: " + result.toShortString());
        }
        return result;
    }

    private static List<BlockState> readPalette(Identifier schematic, NbtCompound nbt,
                                                RegistryEntryLookup<Block> blocks) {
        NbtList entries = nbt.getList(PALETTE, NbtElement.COMPOUND_TYPE);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("палитра пуста — схема ничего не описывает");
        }

        List<BlockState> palette = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            NbtCompound entry = entries.getCompound(i);
            BlockState state = NbtHelper.toBlockState(blocks, entry);
            warnIfBlockIsMissing(schematic, entry, state);
            palette.add(state);
        }
        return palette;
    }

    /**
     * Неизвестный блок ванильный {@code toBlockState} подменяет воздухом —
     * молча. Схема, ссылающаяся на блок неустановленного мода, тогда построится
     * с дырками, и понять причину будет нечем: ни ошибки, ни предупреждения.
     * Одна строка в логе превращает это из загадки в сообщение.
     */
    private static void warnIfBlockIsMissing(Identifier schematic, NbtCompound entry, BlockState resolved) {
        if (!resolved.isAir()) {
            return;
        }
        String requested = entry.getString(NAME);
        if (requested.isEmpty() || AIR_NAMES.contains(requested)) {
            return;
        }
        VillagePax.LOGGER.warn("Схема {}: блок {} не найден и заменён воздухом — "
                + "в здании будет дырка. Нужен мод, который его добавляет.", schematic, requested);
    }

    /**
     * Позиции проверяются на повторы здесь, а не только в {@link BuildPlan}:
     * план считается лениво, уже в игре, и его отказ прошёл бы мимо обработки
     * ошибок загрузчика. Битая схема должна отваливаться при загрузке
     * с записью в лог, а не падать посреди стройки.
     */
    private static List<Schematic.PalettedBlock> readBlocks(NbtCompound nbt, int paletteSize) {
        NbtList entries = nbt.getList(BLOCKS, NbtElement.COMPOUND_TYPE);
        List<Schematic.PalettedBlock> blocks = new ArrayList<>(entries.size());
        Set<BlockPos> taken = new HashSet<>();

        for (int i = 0; i < entries.size(); i++) {
            NbtCompound entry = entries.getCompound(i);

            NbtList pos = entry.getList(POS, NbtElement.INT_TYPE);
            if (pos.size() != 3) {
                throw new IllegalArgumentException(
                        "блок " + i + ": позиция задана " + pos.size() + " числами вместо трёх");
            }

            int paletteIndex = entry.getInt(STATE);
            if (paletteIndex < 0 || paletteIndex >= paletteSize) {
                throw new IllegalArgumentException(
                        "блок " + i + ": номер в палитре " + paletteIndex + " вне диапазона 0.." + (paletteSize - 1));
            }

            BlockPos where = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            if (!taken.add(where)) {
                throw new IllegalArgumentException(
                        "блок " + i + ": позиция " + where.toShortString() + " занята дважды");
            }

            blocks.add(new Schematic.PalettedBlock(where, paletteIndex));
        }
        return blocks;
    }
}
