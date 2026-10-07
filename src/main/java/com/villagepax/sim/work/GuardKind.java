package com.villagepax.sim.work;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Выучка стражника: мечник, лучник или лекарь.
 * <p>
 * Одинаковая стража из одних мечников — это толпа, бегущая к врагу.
 * Три выучки дают ей строй: мечник встречает налётчика у околицы,
 * лучник бьёт с башни и из-за спин, лекарь держится позади и
 * перевязывает раненых — и своих жителей, и хозяина колонии.
 * <p>
 * <b>Выучку ставит порядок, а не случай.</b> Стражники поселения,
 * упорядоченные по опознавателю, разбирают её по кругу: первый —
 * мечник, второй — лучник, третий — лекарь, четвёртый снова мечник.
 * Так в деревне с одним стражем стоит мечник (одинокий лекарь без
 * защитника — пустая трата рта), с двумя — мечник и лучник, а с тремя
 * уже весь строй. Правило без хранения не расходится с миром: уволили
 * стражника — остальные переразобрались сами.
 * <p>
 * Игрок своей колонии может переучить стражника вручную — дать ему
 * в руки меч, лук или целебное: такая выучка помнится и правилу
 * не уступает.
 */
public enum GuardKind {

    /** Ближний бой: оружие своего народа. */
    SWORD("sword"),

    /** Стрельба из лука: держит дистанцию, на башне — первым. */
    BOW("bow"),

    /** Лекарь: не дерётся, а лечит раненых вокруг. */
    MEDIC("medic");

    private static final GuardKind[] ROTA = {SWORD, BOW, MEDIC};

    private final String id;

    GuardKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Ключ названия выучки. */
    public String key() {
        return "villagepax.guard.kind." + id;
    }

    public static Optional<GuardKind> byId(String id) {
        for (GuardKind kind : values()) {
            if (kind.id.equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** Выучка по месту в строю: мечник, лучник, лекарь — по кругу. */
    public static GuardKind byRank(int rank) {
        return ROTA[Math.floorMod(rank, ROTA.length)];
    }

    /**
     * Выучка стражника среди стражи поселения.
     *
     * @param guards  опознаватели всех стражников поселения, в любом порядке
     * @param who     о ком спрашивают
     * @param drilled выучка, данная игроком, если есть
     */
    public static GuardKind of(List<UUID> guards, UUID who, Optional<GuardKind> drilled) {
        if (drilled.isPresent()) {
            return drilled.get();
        }
        List<UUID> rota = guards.stream().sorted(Comparator.naturalOrder()).toList();
        int rank = rota.indexOf(who);
        return byRank(Math.max(rank, 0));
    }

    /** Дерётся ли он: лекарь не бьёт, а лечит. */
    public boolean fights() {
        return this != MEDIC;
    }
}
