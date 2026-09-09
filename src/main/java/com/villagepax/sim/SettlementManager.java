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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private static final String SITES_TAG = "settled_sites";

    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();

    /**
     * Места, на которых деревня уже возникала — или отказалась возникнуть.
     * <p>
     * Хранится затем, что места деревень считаются по семени мира и потому
     * вечны. Без этой памяти снесённая игроком деревня возникала бы снова
     * при каждом его приходе, а клетка, где деревне не ужиться с соседом,
     * проверялась бы заново каждые пять секунд.
     * <p>
     * Позиция, а не опознаватель поселения: поселение можно распустить, а
     * место остаётся тем же.
     */
    private final Set<Long> settledSites = new HashSet<>();

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

        for (long site : nbt.getLongArray(SITES_TAG)) {
            manager.settledSites.add(site);
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
        nbt.putLongArray(SITES_TAG,
                settledSites.stream().mapToLong(Long::longValue).toArray());
        return nbt;
    }

    /** Возникала ли деревня на этом месте. */
    public boolean isSettled(BlockPos site) {
        return settledSites.contains(site.asLong());
    }

    /** Запомнить место как обжитое — навсегда, даже если деревня не встала. */
    public void remember(BlockPos site) {
        if (settledSites.add(site.asLong())) {
            markDirty();
        }
    }

    /**
     * Забыть место. Нужно там, где деревню убирают начисто, — в игровых
     * тестах, которые делят один мир, и позже понадобится отладочной
     * команде: без этого снесённую деревню не поднять обратно.
     */
    public boolean forget(BlockPos site) {
        boolean forgotten = settledSites.remove(site.asLong());
        if (forgotten) {
            markDirty();
        }
        return forgotten;
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

    /**
     * Как {@link #update}, но возвращает результат изменения.
     * <p>
     * Нужен там, где вызывающему важно, чем кончилось действие — например
     * стройке: продвинулась, ждёт материалов или уже готова. Без этого
     * приходилось бы либо доставать результат через изменяемую обёртку,
     * либо менять поселение в обход менеджера и забыть {@code markDirty}.
     */
    public <T> Optional<T> apply(UUID id, Function<Settlement, T> mutation) {
        Settlement settlement = settlements.get(id);
        if (settlement == null) {
            return Optional.empty();
        }
        T result = mutation.apply(settlement);
        markDirty();
        return Optional.ofNullable(result);
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
