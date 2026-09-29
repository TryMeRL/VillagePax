package com.villagepax.sim.games;

import com.villagepax.core.Named;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Идущие партии: кто с кем играет, можно ли сесть за стол, чем кончилось.
 * <p>
 * Живут в памяти сервера и не сохраняются: ставка списывается только
 * по итогу, и перезапуск посреди партии никому ничего не стоит. Один
 * игрок ведёт одну партию, у одного жителя — один соперник.
 */
public final class Bouts {

    /** Во что играют. */
    public enum Kind implements Named {
        DICE("dice"), ARM("arm");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    /**
     * Можно ли сесть за стол — и если нет, почему.
     * <p>
     * Отказ говорит причину — правило мода: соперник отвечает фразой над
     * головой, а то, чего он сказать не может («подойди ближе», «такой
     * ставки нет»), игрок читает строкой над рукой.
     */
    public enum Verdict implements Named {
        YES("yes", null),
        PLAYING("playing", Say.PLAYING),
        PIOUS("pious", Say.PIOUS),
        FESTIVAL("festival", Say.FESTIVAL),
        ASLEEP("asleep", Say.ASLEEP),
        BUSY("busy", Say.BUSY),
        SULK("sulk", Say.SULK),
        BROKE("broke", Say.BROKE),
        FULL("full", Say.FULL),
        TOO_FAR("too_far", null),
        STAKE("stake", null);

        private final String id;
        private final Say say;

        Verdict(String id, Say say) {
            this.id = id;
            this.say = say;
        }

        @Override
        public String id() {
            return id;
        }

        /** Что ответит соперник; пусто — отвечает не он, а строка над рукой. */
        public Optional<Say> say() {
            return Optional.ofNullable(say);
        }

        public String reasonKey() {
            return "villagepax.games.reason." + id;
        }
    }

    /** Дальше стольких блоков от соперника — не сесть, а сидевший сдаёт партию. */
    public static final double REACH = 8;

    /** Не ушёл ли игрок — проверяется раз в столько тиков. */
    static final int CHECK_EVERY = 20;

    /** Соперник бросает раз в столько тиков: полсекунды, чтобы видно было, как он рискует. */
    static final int ROLL_EVERY = 10;

    private static final Map<UUID, Bout> BY_PLAYER = new LinkedHashMap<>();

    /** Кости для проверок; пусто — случай мира. */
    private static IntSupplier loaded;

    /** Кому сказать об изменениях партии: окну игры. Сюда смотрит сеть, а не наоборот. */
    private static Watcher watcher = Watcher.NONE;

    /** Кто смотрит на партии — окно игры у игрока. */
    public interface Watcher {
        Watcher NONE = new Watcher() {
        };

        /** Партия сдвинулась: бросок, шаг соперника, перевес. */
        default void changed(ServerWorld world, Bout bout) {
        }

        /** Партия кончилась — итогом, сдачей или снятием; {@code reasonKey} — почему снята. */
        default void ended(ServerWorld world, Bout bout, Optional<String> reasonKey) {
        }
    }

    /** Подставленные кости, пока не закрыты: в {@code try}, чтобы не остались навсегда. */
    public interface Loaded extends AutoCloseable {
        @Override
        void close();
    }

    private Bouts() {
    }

    public static void watch(Watcher next) {
        watcher = next;
    }

    static Watcher watcher() {
        return watcher;
    }

    /**
     * Кости для проверок: партии, начатые до закрытия, бросают их.
     * <p>
     * Кость берётся партией при начале, а не при броске: проверки одной
     * партии идут вперемешку, и чужие кости, подставленные позже, не должны
     * бросаться в чужой партии.
     */
    public static Loaded withDice(IntSupplier faces) {
        IntSupplier before = loaded;
        loaded = faces;
        return () -> loaded = before;
    }

    static IntSupplier diceFor(ServerWorld world) {
        IntSupplier faces = loaded;
        return faces != null ? faces : () -> 1 + world.getRandom().nextInt(6);
    }

    /** Сколько осталось в кошельке жителя на этот вечер. */
    public static int purseLeft(ServerWorld world, Citizen rival, long day) {
        return Purse.of(rival.profession(), Natures.of(rival))
                - GamesLedger.get(world).spent(rival.id(), day);
    }

    /** На монеты ли играют здесь: только в деревне народа. Колония — не казино, и для гостей тоже. */
    public static boolean forCoins(Settlement settlement) {
        return settlement.owner().isAutonomous();
    }

    /**
     * Можно ли сесть за стол с этим жителем.
     * <p>
     * Отказ называет первую причину по порядку: игроку, которому сказали
     * «работаю», когда жителю ещё и на деньги играть грех, пришлось бы
     * гадать дважды.
     */
    public static Verdict check(ServerWorld world, PlayerEntity player, Settlement settlement,
                                Citizen rival, long day, long timeOfDay) {
        if (BY_PLAYER.containsKey(player.getUuid()) || rivalOf(rival.id()).isPresent()) {
            return Verdict.PLAYING;
        }
        if (Natures.of(rival) == Nature.PIOUS) {
            return Verdict.PIOUS;
        }
        if (FestivalDay.isOn(settlement, day)) {
            return Verdict.FESTIVAL;
        }
        CitizenEntity body = Company.body(world, rival).orElse(null);
        Schedule part = Schedule.at(timeOfDay);
        SettlementManager manager = SettlementManager.get(world);
        boolean evening = Company.gathers(world, manager, settlement, day, part, timeOfDay,
                List.of(player));
        if (body != null && (body.isSleeping() || body.isDozing()) || !evening && part == Schedule.SLEEP) {
            return Verdict.ASLEEP;
        }
        if (!evening || Ages.isChild(rival)) {
            return Verdict.BUSY;
        }
        if (GamesLedger.get(world).rivalry(rival.id(), player.getUuid()).sulks(day)) {
            return Verdict.SULK;
        }
        if (forCoins(settlement) && purseLeft(world, rival, day) < 2) {
            return Verdict.BROKE;
        }
        List<Citizen> company = Company.of(world, settlement, day, part, timeOfDay, List.of(player));
        if (company.size() >= Company.MAX && !company.contains(rival)) {
            return Verdict.FULL;
        }
        if (body == null || player.squaredDistanceTo(body) > REACH * REACH) {
            return Verdict.TOO_FAR;
        }
        return Verdict.YES;
    }

    /** Начать партию: соперник соглашается вслух. */
    public static Verdict start(ServerWorld world, PlayerEntity player, Settlement settlement,
                                Citizen rival, Kind kind, int stake, long day, long timeOfDay) {
        Verdict verdict = check(world, player, settlement, rival, day, timeOfDay);
        if (verdict != Verdict.YES) {
            return verdict;
        }
        boolean coins = forCoins(settlement);
        boolean fits = coins
                ? Purse.allowed(purseLeft(world, rival, day), Coins.total(player.getInventory()))
                .contains(stake)
                : stake == 0;
        if (!fits) {
            return Verdict.STAKE;
        }
        boolean withOwner = settlement.owner().player().filter(player.getUuid()::equals).isPresent();
        Bout bout = new Bout(world, player, settlement, rival, kind, stake, coins, withOwner, day);
        BY_PLAYER.put(player.getUuid(), bout);
        Company.body(world, rival).ifPresent(body -> {
            body.greet();
            Lines.say(body, rival, Say.ACCEPT, player.getName());
        });
        watcher.changed(world, bout);
        return Verdict.YES;
    }

    public static Optional<Bout> of(UUID player) {
        return Optional.ofNullable(BY_PLAYER.get(player));
    }

    public static Optional<Bout> rivalOf(UUID citizen) {
        return BY_PLAYER.values().stream().filter(bout -> bout.rival().equals(citizen)).findFirst();
    }

    /**
     * Соперник за партией стоит, где стоял, и смотрит на игрока.
     * <p>
     * Цель — своя клетка, а не пусто: без цели тело пошло бы гулять по площади
     * и через минуту стояло бы в десяти шагах от стола, а ушедший дальше
     * восьми блоков соперник — это снятая партия.
     */
    public static boolean steers(WorkContext context) {
        Bout bout = rivalOf(context.citizen().id()).orElse(null);
        if (bout == null) {
            return false;
        }
        CitizenEntity body = context.body();
        context.holdNothing();
        body.setWorkTarget(body.getBlockPos());
        PlayerEntity player = bout.playerOf(context.world());
        body.setWorkFocus(player == null ? null : player.getBlockPos().up());
        body.setWrestling(bout.kind() == Kind.ARM);
        return true;
    }

    /** Тик всех партий этого мира: броски соперника, давление руки, уход и пропажа. */
    public static void tick(ServerWorld world) {
        for (Bout bout : new ArrayList<>(BY_PLAYER.values())) {
            if (bout.in(world) && BY_PLAYER.get(bout.player()) == bout) {
                bout.tick(world);
            }
        }
    }

    /** Кончить партию — убрать из идущих. Зовёт сама партия. */
    static void ended(ServerWorld world, Bout bout, Optional<String> reasonKey) {
        BY_PLAYER.remove(bout.player(), bout);
        watcher.ended(world, bout, reasonKey);
    }

    /** Снять партию без выплаты: соперник пропал, игрок ушёл из игры. */
    public static void cancel(ServerWorld world, Bout bout) {
        bout.cancel(world);
    }

    /** Сервер останавливается: партии снимаются без выплаты, ставка не списана. */
    public static void stopAll(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            for (Bout bout : new ArrayList<>(BY_PLAYER.values())) {
                if (bout.in(world)) {
                    bout.cancel(world);
                }
            }
        }
        BY_PLAYER.clear();
    }
}
