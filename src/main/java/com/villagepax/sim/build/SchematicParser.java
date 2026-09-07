package com.villagepax.sim.build;

import com.villagepax.VillagePax;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        List<BlockState> palette = new ArrayList<>(readPalette(id, nbt, blocks));
        List<Schematic.PalettedBlock> placed = readBlocks(nbt, palette.size());
        List<PointOfInterest> markers = new ArrayList<>();

        replaceMarkers(palette, placed, markers);

        return new Schematic(id, readSize(nbt), palette, placed, markers);
    }

    /**
     * Маркер схемы — служебный блок: он не ставится, а оставляет после себя
     * точку интереса. Само место занимает обстановка этого рода: у сундучного
     * маркера — сундук, у остальных пока воздух.
     * <p>
     * Подмена делается здесь, а не отдельной фазой «доводки» после стройки,
     * и это важно для ремонта: сундук становится обычным шагом плана, а
     * значит правило «нужный блок уже стоит» защищает его от переустановки.
     * Иначе ремонт сносил бы сундук вместе с содержимым.
     */
    private static void replaceMarkers(List<BlockState> palette,
                                       List<Schematic.PalettedBlock> placed,
                                       List<PointOfInterest> markers) {
        Map<Integer, MarkerKind> markerPalette = new HashMap<>();
        for (int i = 0; i < palette.size(); i++) {
            Optional<MarkerKind> kind = markerKind(palette.get(i));
            if (kind.isPresent()) {
                markerPalette.put(i, kind.get());
            }
        }
        if (markerPalette.isEmpty()) {
            return;
        }

        Map<MarkerKind, Integer> fixtureIndex = new HashMap<>();
        for (int i = 0; i < placed.size(); i++) {
            Schematic.PalettedBlock block = placed.get(i);
            MarkerKind kind = markerPalette.get(block.paletteIndex());
            if (kind == null) {
                continue;
            }

            markers.add(new PointOfInterest(kind, block.pos()));

            BlockState fixture = fixtureFor(kind);
            int index = fixtureIndex.computeIfAbsent(kind, ignored -> {
                palette.add(fixture);
                return palette.size() - 1;
            });
            placed.set(i, new Schematic.PalettedBlock(block.pos(), index));
        }
    }

    /** Что встаёт на место маркера. Данными это станет вместе с типами зданий. */
    private static BlockState fixtureFor(MarkerKind kind) {
        return kind == MarkerKind.STORAGE
                ? Blocks.CHEST.getDefaultState()
                : Blocks.AIR.getDefaultState();
    }

    private static Optional<MarkerKind> markerKind(BlockState state) {
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return VillagePax.MOD_ID.equals(id.getNamespace())
                ? MarkerKind.byBlockPath(id.getPath())
                : Optional.empty();
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
