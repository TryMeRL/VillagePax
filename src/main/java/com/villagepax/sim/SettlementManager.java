package com.villagepax.sim;

import com.villagepax.VillagePax;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Все поселения одного измерения. Хранится вместе с миром, а не в статике,
 * иначе ломается переключение между сохранениями.
 * <p>
 * Изменения всегда идут через {@link #update} или через методы этого класса:
 * они помечают состояние грязным. Забытый {@code markDirty} — самый коварный
 * баг в этом месте, потому что всё работает до перезахода в мир.
 */
public class SettlementManager extends PersistentState {

    public static final String KEY = "villagepax_settlements";
    private static final String LIST_TAG = "settlements";

    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();

    public static SettlementManager get(ServerWorld world) {
        return world.getPersistentStateManager()
                .getOrCreate(SettlementManager::fromNbt, SettlementManager::new, KEY);
    }

    public static SettlementManager fromNbt(NbtCompound nbt) {
        SettlementManager manager = new SettlementManager();
        NbtList list = nbt.getList(LIST_TAG, NbtElement.COMPOUND_TYPE);

        int broken = 0;
        for (NbtElement element : list) {
            Optional<Settlement> parsed = Settlement.CODEC
                    .parse(NbtOps.INSTANCE, element)
                    .resultOrPartial(error -> VillagePax.LOGGER.error("Поселение не прочитано: {}", error));

            if (parsed.isPresent()) {
                manager.settlements.put(parsed.get().id(), parsed.get());
            } else {
                broken++;
            }
        }

        if (broken > 0) {
            VillagePax.LOGGER.error("Повреждённых записей поселений: {}. Остальные {} загружены.",
                    broken, manager.settlements.size());
        }
        return manager;
    }

    /**
     * Каждое поселение кодируется отдельно. Список целиком был бы короче кодом,
     * но одна повреждённая запись уносила бы с собой все остальные.
     */
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        int failed = 0;

        for (Settlement settlement : settlements.values()) {
            Optional<NbtElement> encoded = Settlement.CODEC
                    .encodeStart(NbtOps.INSTANCE, settlement)
                    .resultOrPartial(error -> VillagePax.LOGGER.error(
                            "Поселение {} не записано: {}", settlement.name(), error));

            if (encoded.isPresent()) {
                list.add(encoded.get());
            } else {
                failed++;
            }
        }

        if (failed > 0) {
            VillagePax.LOGGER.error("Не удалось сохранить поселений: {} из {}", failed, settlements.size());
        }

        nbt.put(LIST_TAG, list);
        return nbt;
    }

    public void add(Settlement settlement) {
        settlements.put(settlement.id(), settlement);
        markDirty();
    }

    public boolean remove(UUID id) {
        boolean removed = settlements.remove(id) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    /** Единственный безопасный способ поменять поселение: сам пометит состояние грязным. */
    public boolean update(UUID id, Consumer<Settlement> mutation) {
        Settlement settlement = settlements.get(id);
        if (settlement == null) {
            return false;
        }
        mutation.accept(settlement);
        markDirty();
        return true;
    }

    public Optional<Settlement> byId(UUID id) {
        return Optional.ofNullable(settlements.get(id));
    }

    public Collection<Settlement> all() {
        return Collections.unmodifiableCollection(settlements.values());
    }

    public int count() {
        return settlements.size();
    }

    /** Поселение, чьи границы накрывают точку. */
    public Optional<Settlement> at(BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        return settlements.values().stream().filter(settlement -> settlement.claims(chunk)).findFirst();
    }

    /**
     * Можно ли основать здесь новое поселение. Границы не должны пересекаться
     * с уже существующими — иначе два поселения будут спорить за одни чанки.
     */
    public Optional<Settlement> conflictWith(Settlement candidate) {
        return settlements.values().stream().filter(candidate::overlaps).findFirst();
    }
}
