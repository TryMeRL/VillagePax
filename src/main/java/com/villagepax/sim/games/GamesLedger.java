package com.villagepax.sim.games;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Память игр: счёт каждого жителя с каждым игроком, потраченное из кошелька
 * за день и день, когда житель играл с хозяином.
 * <p>
 * Своё состояние мира, а не поле записи жителя: кодек жителя полон —
 * шестнадцать полей, предел, — и перекраивать его ради игр значило бы
 * трогать сохранения всех жителей. Память чистится раз в день
 * ({@link #prune}): ушедшие и умершие забываются, старые траты и бодрость —
 * тоже, и мир, живущий годами, не копит чужих счетов.
 */
public final class GamesLedger extends PersistentState {

    private static final String KEY = "villagepax_games";

    /** Счёт: житель → игрок → счёт глазами жителя. */
    private final Map<UUID, Map<UUID, Rivalry>> rivals = new LinkedHashMap<>();

    /** Потраченное из кошелька: житель → день и медяки. */
    private final Map<UUID, Spent> spent = new LinkedHashMap<>();

    /** Когда житель играл с хозяином колонии: наутро он бодрее. */
    private final Map<UUID, Long> cheer = new LinkedHashMap<>();

    private record Spent(long day, int copper) {
    }

    public static GamesLedger get(ServerWorld world) {
        return world.getPersistentStateManager()
                .getOrCreate(GamesLedger::fromNbt, GamesLedger::new, KEY);
    }

    /** Счёт жителя с игроком; незнакомец — {@link Rivalry#NONE}. */
    public Rivalry rivalry(UUID citizen, UUID player) {
        return rivals.getOrDefault(citizen, Map.of()).getOrDefault(player, Rivalry.NONE);
    }

    /** Записать партию глазами жителя. */
    public void record(UUID citizen, UUID player, Rivalry.Result result, long day) {
        rivals.computeIfAbsent(citizen, id -> new LinkedHashMap<>())
                .merge(player, Rivalry.NONE.after(result, day), (was, ignored) -> was.after(result, day));
        markDirty();
    }

    /** Сколько житель спустил сегодня. */
    public int spent(UUID citizen, long day) {
        Spent today = spent.get(citizen);
        return today == null || today.day() != day ? 0 : today.copper();
    }

    /** Житель проиграл игроку столько медяков: кошелёк на вечер тает. */
    public void spend(UUID citizen, long day, int copper) {
        spent.put(citizen, new Spent(day, spent(citizen, day) + copper));
        markDirty();
    }

    public void markCheer(UUID citizen, long day) {
        cheer.put(citizen, day);
        markDirty();
    }

    public boolean cheered(UUID citizen, long day) {
        Long when = cheer.get(citizen);
        return when != null && when == day;
    }

    /**
     * Забыть лишнее: счёт тех, кого нет среди живых, траты не сегодняшние
     * и бодрость старше вчерашней — она нужна только на рассвете.
     */
    public void prune(Set<UUID> living, long today) {
        boolean changed = rivals.keySet().removeIf(citizen -> !living.contains(citizen));
        changed |= spent.entrySet().removeIf(entry -> !living.contains(entry.getKey())
                || entry.getValue().day() != today);
        changed |= cheer.entrySet().removeIf(entry -> !living.contains(entry.getKey())
                || entry.getValue() < today - 1);
        if (changed) {
            markDirty();
        }
    }

    public static GamesLedger fromNbt(NbtCompound nbt) {
        GamesLedger ledger = new GamesLedger();
        for (NbtElement element : nbt.getList("rivals", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            // Битая запись пропускается, а не роняет чтение: без одного
            // счёта мир живёт, без памяти игр целиком — тоже, но хуже.
            if (!entry.containsUuid("citizen") || !entry.containsUuid("player")) {
                continue;
            }
            ledger.rivals.computeIfAbsent(entry.getUuid("citizen"), id -> new HashMap<>())
                    .put(entry.getUuid("player"), new Rivalry(entry.getInt("won"), entry.getInt("lost"),
                            entry.getInt("streak"), entry.getLong("day"), entry.getInt("today")));
        }
        for (NbtElement element : nbt.getList("spent", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            if (entry.containsUuid("citizen")) {
                ledger.spent.put(entry.getUuid("citizen"),
                        new Spent(entry.getLong("day"), entry.getInt("copper")));
            }
        }
        for (NbtElement element : nbt.getList("cheer", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            if (entry.containsUuid("citizen")) {
                ledger.cheer.put(entry.getUuid("citizen"), entry.getLong("day"));
            }
        }
        return ledger;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList rivalList = new NbtList();
        rivals.forEach((citizen, players) -> players.forEach((player, rivalry) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("citizen", citizen);
            entry.putUuid("player", player);
            entry.putInt("won", rivalry.won());
            entry.putInt("lost", rivalry.lost());
            entry.putInt("streak", rivalry.streak());
            entry.putLong("day", rivalry.day());
            entry.putInt("today", rivalry.lostToday());
            rivalList.add(entry);
        }));
        nbt.put("rivals", rivalList);

        NbtList spentList = new NbtList();
        spent.forEach((citizen, today) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("citizen", citizen);
            entry.putLong("day", today.day());
            entry.putInt("copper", today.copper());
            spentList.add(entry);
        });
        nbt.put("spent", spentList);

        NbtList cheerList = new NbtList();
        cheer.forEach((citizen, day) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("citizen", citizen);
            entry.putLong("day", day);
            cheerList.add(entry);
        });
        nbt.put("cheer", cheerList);
        return nbt;
    }
}
