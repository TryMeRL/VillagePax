package com.villagepax.sim;

import com.villagepax.VillagePax;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final String DECOR_TAG = "decor";
    private static final String DRESSED_TAG = "dressed";
    private static final String FESTIVE_TAG = "festive";
    private static final String AWARDED_TAG = "awarded";
    private static final String CHRONICLE_TAG = "chronicle";

    /** Сколько записей летописи помнит поселение: последние, старые уходят. */
    public static final int CHRONICLE_LENGTH = 60;

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

    /**
     * Записи, которые не прочитались, — нетронутыми.
     * <p>
     * Не прочитаться запись может не только от порчи: от ошибки в новой
     * версии мода, от убранного из датапака народа, от поля, которое
     * кодек перестал понимать. Выброси их менеджер — первое же автосохранение
     * стёрло бы колонию навсегда, хотя исправленная версия прочитала бы
     * её без потерь. Поэтому мод их не видит, но и не стирает: записи
     * уходят в файл такими же, какими пришли.
     */
    private final List<NbtElement> unreadable = new ArrayList<>();

    /**
     * Убранство улиц, поставленное деревне: колодец, фонари, цветы.
     * <p>
     * Помнится поблочно, чтобы его можно было убрать вместе с деревней:
     * снесённая деревня, после которой на лугу стоят одинокие фонари, —
     * это уже не убранство, а мусор.
     */
    private final Map<UUID, List<Long>> decor = new LinkedHashMap<>();

    /** Что уже убрано: здания и сами деревни (их колодец). Убирается однажды. */
    private final Set<UUID> dressed = new HashSet<>();

    /**
     * Что поставил праздник этому поселению и в какой день.
     * <p>
     * Помнится поблочно и с именем блока, как убранство улиц, но по другой
     * причине: убранство стоит, пока стоит деревня, а праздничное живёт день.
     * Имя нужно уборке: убирается только то, что там <b>всё ещё наше</b> —
     * цветок, посаженный игроком на место съеденного пирога, остаётся цветком.
     */
    private final Map<UUID, Festive> festive = new LinkedHashMap<>();

    /** Поставленный праздником блок: где и какой. */
    public record Placed(long at, Identifier block) {
        public BlockPos pos() {
            return BlockPos.fromLong(at);
        }
    }

    /** Память праздника поселения: в какой день ставили и что. */
    public record Festive(long day, List<Placed> placed) {
    }

    /**
     * Кому праздник уже дал приз: приз — раз за праздник на игрока
     * и состязание.
     * <p>
     * Хранится, а не держится в памяти сервера: перезапуск посреди праздника
     * иначе раздавал бы призы заново. Помнится только последний праздник —
     * вчерашний приз сегодняшнему не мешает, и память не растёт.
     */
    private final Map<UUID, Awarded> awarded = new LinkedHashMap<>();

    /** Призы праздника поселения: день и ключи «игрок/номер состязания». */
    private record Awarded(long day, List<String> keys) {
    }

    /**
     * Летопись поселения: что в нём случилось, по дням.
     * <p>
     * Здесь, а не в самом поселении: у кодека поселения шестнадцать полей,
     * и все они заняты. Хранится рядом, как память о призах, и уходит
     * вместе с поселением.
     */
    private final Map<UUID, List<ChronicleEntry>> chronicles = new LinkedHashMap<>();

    /**
     * Запись летописи: день, ключ перевода и его аргументы. Аргумент,
     * начатый с {@code #}, — сам ключ перевода (ступень, состязание),
     * прочие — имена как есть.
     */
    public record ChronicleEntry(long day, String key, List<String> args) {
    }

    public static SettlementManager get(ServerWorld world) {
        return world.getPersistentStateManager()
                .getOrCreate(SettlementManager::fromNbt, SettlementManager::new, KEY);
    }

    public static SettlementManager fromNbt(NbtCompound nbt) {
        SettlementManager manager = new SettlementManager();
        NbtList list = nbt.getList(LIST_TAG, NbtElement.COMPOUND_TYPE);

        for (NbtElement element : list) {
            Optional<Settlement> parsed = Settlement.CODEC
                    .parse(NbtOps.INSTANCE, element)
                    .resultOrPartial(error -> VillagePax.LOGGER.error("Поселение не прочитано: {}", error));

            if (parsed.isPresent()) {
                manager.settlements.put(parsed.get().id(), parsed.get());
            } else {
                manager.unreadable.add(element.copy());
            }
        }

        if (!manager.unreadable.isEmpty()) {
            VillagePax.LOGGER.error("Не прочитано записей поселений: {}. Остальные {} загружены,"
                            + " а эти сохранятся как были — до версии, которая их поймёт.",
                    manager.unreadable.size(), manager.settlements.size());
        }

        for (long site : nbt.getLongArray(SITES_TAG)) {
            manager.settledSites.add(site);
        }
        for (NbtElement element : nbt.getList(DECOR_TAG, NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            List<Long> placed = new ArrayList<>();
            for (long at : entry.getLongArray("at")) {
                placed.add(at);
            }
            manager.decor.put(entry.getUuid("village"), placed);
        }
        for (NbtElement element : nbt.getList(DRESSED_TAG, NbtElement.INT_ARRAY_TYPE)) {
            manager.dressed.add(net.minecraft.nbt.NbtHelper.toUuid(element));
        }
        for (NbtElement element : nbt.getList(FESTIVE_TAG, NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            long[] at = entry.getLongArray("at");
            NbtList blocks = entry.getList("blocks", NbtElement.STRING_TYPE);
            List<Placed> placed = new ArrayList<>();
            for (int i = 0; i < at.length && i < blocks.size(); i++) {
                Identifier block = Identifier.tryParse(blocks.getString(i));
                if (block != null) {
                    placed.add(new Placed(at[i], block));
                }
            }
            manager.festive.put(entry.getUuid("village"), new Festive(entry.getLong("day"), placed));
        }
        for (NbtElement element : nbt.getList(AWARDED_TAG, NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            List<String> keys = new ArrayList<>();
            NbtList written = entry.getList("keys", NbtElement.STRING_TYPE);
            for (int i = 0; i < written.size(); i++) {
                keys.add(written.getString(i));
            }
            manager.awarded.put(entry.getUuid("village"),
                    new Awarded(entry.getLong("day"), List.copyOf(keys)));
        }
        for (NbtElement element : nbt.getList(CHRONICLE_TAG, NbtElement.COMPOUND_TYPE)) {
            NbtCompound book = (NbtCompound) element;
            List<ChronicleEntry> entries = new ArrayList<>();
            for (NbtElement written : book.getList("entries", NbtElement.COMPOUND_TYPE)) {
                NbtCompound entry = (NbtCompound) written;
                List<String> args = new ArrayList<>();
                NbtList saved = entry.getList("args", NbtElement.STRING_TYPE);
                for (int i = 0; i < saved.size(); i++) {
                    args.add(saved.getString(i));
                }
                entries.add(new ChronicleEntry(entry.getLong("day"), entry.getString("key"),
                        List.copyOf(args)));
            }
            manager.chronicles.put(book.getUuid("village"), entries);
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
        unreadable.forEach(kept -> list.add(kept.copy()));

        nbt.put(LIST_TAG, list);
        nbt.putLongArray(SITES_TAG,
                settledSites.stream().mapToLong(Long::longValue).toArray());
        NbtList placed = new NbtList();
        decor.forEach((village, positions) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("village", village);
            entry.putLongArray("at", positions.stream().mapToLong(Long::longValue).toArray());
            placed.add(entry);
        });
        nbt.put(DECOR_TAG, placed);
        NbtList done = new NbtList();
        dressed.forEach(id -> done.add(net.minecraft.nbt.NbtHelper.fromUuid(id)));
        nbt.put(DRESSED_TAG, done);
        NbtList festivities = new NbtList();
        festive.forEach((village, memory) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("village", village);
            entry.putLong("day", memory.day());
            entry.putLongArray("at", memory.placed().stream().mapToLong(Placed::at).toArray());
            NbtList blocks = new NbtList();
            memory.placed().forEach(one -> blocks.add(
                    net.minecraft.nbt.NbtString.of(one.block().toString())));
            entry.put("blocks", blocks);
            festivities.add(entry);
        });
        nbt.put(FESTIVE_TAG, festivities);
        NbtList prizes = new NbtList();
        awarded.forEach((village, memory) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("village", village);
            entry.putLong("day", memory.day());
            NbtList keys = new NbtList();
            memory.keys().forEach(key -> keys.add(net.minecraft.nbt.NbtString.of(key)));
            entry.put("keys", keys);
            prizes.add(entry);
        });
        nbt.put(AWARDED_TAG, prizes);
        NbtList books = new NbtList();
        chronicles.forEach((village, entries) -> {
            NbtCompound book = new NbtCompound();
            book.putUuid("village", village);
            NbtList written = new NbtList();
            for (ChronicleEntry entry : entries) {
                NbtCompound one = new NbtCompound();
                one.putLong("day", entry.day());
                one.putString("key", entry.key());
                NbtList args = new NbtList();
                entry.args().forEach(arg -> args.add(net.minecraft.nbt.NbtString.of(arg)));
                one.put("args", args);
                written.add(one);
            }
            book.put("entries", written);
            books.add(book);
        });
        nbt.put(CHRONICLE_TAG, books);
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

    /** Убрано ли уже здание (или колодец деревни — по её опознавателю). */
    public boolean isDressed(UUID id) {
        return dressed.contains(id);
    }

    public void markDressed(UUID id) {
        if (dressed.add(id)) {
            markDirty();
        }
    }

    /** Запомнить блок убранства, поставленный деревне. */
    public void recordDecor(UUID village, BlockPos at) {
        decor.computeIfAbsent(village, ignored -> new ArrayList<>()).add(at.asLong());
        markDirty();
    }

    /** Всё убранство деревни — чтобы убрать его вместе с ней. */
    public List<BlockPos> decorOf(UUID village) {
        return decor.getOrDefault(village, List.of()).stream().map(BlockPos::fromLong).toList();
    }

    public boolean remove(UUID id) {
        boolean removed = settlements.remove(id) != null;
        // Убранство забывается вместе с деревней: сносящий зовёт decorOf
        // до remove, если хочет убрать и его.
        decor.remove(id);
        dressed.remove(id);
        festive.remove(id);
        awarded.remove(id);
        chronicles.remove(id);
        if (removed) {
            markDirty();
        }
        return removed;
    }

    /** Память праздника поселения, если праздник что-то ставил. */
    public Optional<Festive> festiveOf(UUID village) {
        return Optional.ofNullable(festive.get(village));
    }

    /**
     * Запомнить, что праздник в этот день уже ставил, — даже если ставить
     * было некуда: иначе каждую секунду праздник пробовал бы снова.
     * Прежние записи остаются: их уберут, когда до них дойдёт уборка.
     */
    public void startFestive(UUID village, long day) {
        Festive old = festive.get(village);
        festive.put(village, new Festive(day, old == null ? List.of() : old.placed()));
        markDirty();
    }

    /** Запомнить блок, поставленный праздником. */
    public void recordFestive(UUID village, long day, BlockPos at, Identifier block) {
        Festive old = festive.get(village);
        List<Placed> placed = new ArrayList<>(old == null ? List.of() : old.placed());
        placed.add(new Placed(at.asLong(), block));
        festive.put(village, new Festive(day, List.copyOf(placed)));
        markDirty();
    }

    /** Забыть блок праздника: убран, съеден или его место занял чужой. */
    public void forgetFestive(UUID village, BlockPos at) {
        Festive old = festive.get(village);
        if (old == null) {
            return;
        }
        long key = at.asLong();
        festive.put(village, new Festive(old.day(),
                old.placed().stream().filter(placed -> placed.at() != key).toList()));
        markDirty();
    }

    /** Брал ли игрок приз за это состязание в этот праздник. */
    /** Вписать в летопись поселения; самое старое уходит, когда места нет. */
    public void chronicle(UUID village, long day, String key, String... args) {
        List<ChronicleEntry> entries = chronicles.computeIfAbsent(village, id -> new ArrayList<>());
        entries.add(new ChronicleEntry(day, key, List.of(args)));
        while (entries.size() > CHRONICLE_LENGTH) {
            entries.remove(0);
        }
        markDirty();
    }

    /** Летопись поселения, от старого к новому. */
    public List<ChronicleEntry> chronicleOf(UUID village) {
        return List.copyOf(chronicles.getOrDefault(village, List.of()));
    }

    public boolean awarded(UUID village, long day, UUID player, int contest) {
        Awarded memory = awarded.get(village);
        return memory != null && memory.day() == day && memory.keys().contains(prizeKey(player, contest));
    }

    /** Запомнить приз; память прошлого праздника при этом забывается. */
    public void markAwarded(UUID village, long day, UUID player, int contest) {
        Awarded old = awarded.get(village);
        List<String> keys = new ArrayList<>(old == null || old.day() != day ? List.of() : old.keys());
        String key = prizeKey(player, contest);
        if (!keys.contains(key)) {
            keys.add(key);
        }
        awarded.put(village, new Awarded(day, List.copyOf(keys)));
        markDirty();
    }

    private static String prizeKey(UUID player, int contest) {
        return player + "/" + contest;
    }

    /** Забыть праздник поселения целиком. */
    public void clearFestive(UUID village) {
        if (festive.remove(village) != null) {
            markDirty();
        }
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

    /**
     * Поселение, чьи границы накрывают точку; если таких несколько — то,
     * к чьей середине чанк ближе.
     * <p>
     * Границы растут со ступенью, а пересечение проверяется лишь при
     * основании: выросшие колония и деревня делят общие чанки. Прежде
     * земля доставалась тому, кто раньше в списке, — обычно деревне, — и
     * колония на своей половине теряла защиту. Делёж по близости — тот,
     * который и видно глазами: ближе к ратуше — её земля.
     */
    public Optional<Settlement> at(BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        return settlements.values().stream()
                .filter(settlement -> settlement.claims(chunk))
                .min(java.util.Comparator.comparingInt(settlement -> {
                    ChunkPos centre = settlement.centerChunk();
                    int dx = chunk.x - centre.x;
                    int dz = chunk.z - centre.z;
                    return dx * dx + dz * dz;
                }));
    }

    /**
     * Можно ли основать здесь новое поселение. Границы не должны пересекаться
     * с уже существующими — иначе два поселения будут спорить за одни чанки.
     */
    public Optional<Settlement> conflictWith(Settlement candidate) {
        return settlements.values().stream().filter(candidate::overlaps).findFirst();
    }
}
