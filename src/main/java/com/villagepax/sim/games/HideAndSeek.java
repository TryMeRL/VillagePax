package com.villagepax.sim.games;

import com.villagepax.core.config.Configs;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.sim.festival.HidingPlaces;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkContext;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Прятки с детьми: днём ребёнок зовёт сам, дети прячутся, игрок ищет.
 * <p>
 * Прятки, в которые зовут сами жители, и подарок тому, кто нашёл всех, —
 * Animal Crossing. Прячутся там же, где ярмарочный поиск прячет вещицы:
 * не в домах, не на улице, не кучкой. Подпись над спрятавшимся снята —
 * имя видно сквозь стены и выдало бы его; найденному она возвращается,
 * а по концу пряток любого рода — всем.
 * <p>
 * Живут в памяти сервера и не сохраняются: прятки — минута игры, и после
 * перезапуска их просто нет, а подпись тело ставит себе само при появлении.
 */
public final class HideAndSeek {

    /** Ребёнок зовёт игрока, стоящего в стольких блоках. */
    static final double INVITE_REACH = 8;

    /** Не чаще раза в столько тиков на поселение: пять минут. */
    static final int INVITE_EVERY = 6_000;

    /** Играют дети в стольких блоках от позвавшего. */
    static final double GATHER = 32;

    /** Больше четырёх — уже не прятки, а беготня. */
    public static final int MAX_CHILDREN = 4;

    /** Отсчёт — десять секунд: дети бегут прятаться. */
    public static final int COUNTDOWN = 200;

    /** Поиск — две минуты. */
    public static final int SEEK = 2_400;

    /** Найден — когда игрок подошёл на столько. */
    static final double FOUND_AT = 2.5;

    /** Ушёл дальше — прятки кончились: искать некому. */
    static final double STRAY = 48;

    /** Два медяка к сласти: мать платит тому, кто занял её детей. */
    static final int GIFT_COPPER = 2;

    /** Прятки оглядываются раз в столько тиков: подход к ребёнку — не реакция на шаг. */
    static final int TICK_EVERY = 5;

    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();

    /** Когда в поселении звали последний раз. */
    private static final Map<UUID, Long> INVITED = new HashMap<>();

    /** Одни прятки: кто водит, кто где спрятался, кого нашли. */
    public static final class Session {
        private final RegistryKey<World> world;
        private final UUID settlement;
        private final UUID player;
        /** Подставной игрок проверок в списке игроков сервера не числится. */
        private final PlayerEntity starter;
        private final UUID inviter;
        private final BlockPos base;
        private final long started;
        private final long day;
        private final Map<UUID, BlockPos> spots = new LinkedHashMap<>();
        private final Set<UUID> found = new LinkedHashSet<>();
        private ServerBossBar bar;
        private boolean seeking;

        private Session(ServerWorld world, UUID settlement, PlayerEntity player, UUID inviter,
                        BlockPos base, long day) {
            this.world = world.getRegistryKey();
            this.settlement = settlement;
            this.player = player.getUuid();
            this.starter = player;
            this.inviter = inviter;
            this.base = base;
            this.started = world.getTime();
            this.day = day;
        }

        public int hiders() {
            return spots.size();
        }

        public Optional<BlockPos> spotOf(UUID child) {
            return Optional.ofNullable(spots.get(child));
        }

        public boolean isFound(UUID child) {
            return found.contains(child);
        }

        PlayerEntity playerOf(ServerWorld world) {
            ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(player);
            if (online != null) {
                return online;
            }
            return starter instanceof ServerPlayerEntity || starter.isRemoved() ? null : starter;
        }
    }

    /** Чем кончились прятки. */
    private enum Ending { ALL_FOUND, TIME, GAVE_UP }

    private HideAndSeek() {
    }

    public static Optional<Session> at(UUID settlement) {
        return Optional.ofNullable(SESSIONS.get(settlement));
    }

    /** Днём, в часы работы взрослых; ночью и в праздник пряток нет. */
    static boolean open(Settlement settlement, long day, long timeOfDay) {
        return Schedule.at(timeOfDay).isWork() && !FestivalDay.isOn(settlement, day);
    }

    /**
     * Позвать проходящего: свободный ребёнок рядом с игроком машет —
     * «Поиграешь с нами в прятки?». Нет детей — нет и приглашений.
     *
     * @return позвали ли
     */
    public static boolean invite(ServerWorld world, Settlement settlement, long day, long timeOfDay,
                                 List<? extends PlayerEntity> players) {
        if (!open(settlement, day, timeOfDay) || SESSIONS.containsKey(settlement.id())) {
            return false;
        }
        long now = world.getTime();
        Long last = INVITED.get(settlement.id());
        if (last != null && now - last < INVITE_EVERY) {
            return false;
        }
        for (Citizen child : settlement.citizens()) {
            if (!Ages.isChild(child)) {
                continue;
            }
            CitizenEntity body = Company.body(world, child).orElse(null);
            if (body == null) {
                continue;
            }
            for (PlayerEntity player : players) {
                if (!player.isSpectator() && player.squaredDistanceTo(body) <= INVITE_REACH * INVITE_REACH
                        && Lines.say(body, child, Say.HIDE_INVITE)) {
                    body.greet();
                    INVITED.put(settlement.id(), now);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Щелчок по ребёнку: начать прятки, а в идущих — найти его.
     *
     * @return ответили ли прятки; ложь — щелчок идёт дальше
     */
    public static boolean clicked(ServerWorld world, PlayerEntity player, Settlement settlement,
                                  Citizen child, long day, long timeOfDay) {
        Session session = SESSIONS.get(settlement.id());
        if (session != null) {
            if (!session.player.equals(player.getUuid()) || !session.spots.containsKey(child.id())) {
                return false;
            }
            if (session.seeking && !session.found.contains(child.id())) {
                found(world, session, settlement, child);
            }
            return true;
        }
        if (!open(settlement, day, timeOfDay)) {
            return false;
        }
        return start(world, player, settlement, child, day);
    }

    /** Собрать детей в 32 блоках от позвавшего, до четырёх, и найти им места. */
    private static boolean start(ServerWorld world, PlayerEntity player, Settlement settlement,
                                 Citizen inviter, long day) {
        CitizenEntity caller = Company.body(world, inviter).orElse(null);
        if (caller == null) {
            return false;
        }
        BlockPos base = caller.getBlockPos();
        List<Citizen> kids = settlement.citizens().stream()
                .filter(Ages::isChild)
                .filter(kid -> Company.body(world, kid)
                        .filter(body -> body.squaredDistanceTo(caller) <= GATHER * GATHER).isPresent())
                .sorted(Comparator.comparing((Citizen kid) -> !kid.id().equals(inviter.id())))
                .limit(MAX_CHILDREN)
                .toList();
        // Только туда, где мир ведёт тела: ребёнок, забежавший в секцию без
        // сущностей, отрывается от своей записи, и прятки потеряли бы его.
        List<BlockPos> places = HidingPlaces.find(world, settlement, base, base.getY(), kids.size(),
                world.getRandom(), world::shouldTickEntity);
        if (places.isEmpty()) {
            Lines.sayNow(caller, inviter, Say.HIDE_NOWHERE);
            return true;
        }
        Session session = new Session(world, settlement.id(), player, inviter.id(), base, day);
        for (int i = 0; i < places.size(); i++) {
            session.spots.put(kids.get(i).id(), places.get(i));
            Company.body(world, kids.get(i)).ifPresent(body -> body.setCustomNameVisible(false));
        }
        if (player instanceof ServerPlayerEntity online) {
            session.bar = new ServerBossBar(Text.translatable("villagepax.hide.bar_counting"),
                    BossBar.Color.GREEN, BossBar.Style.PROGRESS);
            session.bar.addPlayer(online);
        }
        SESSIONS.put(settlement.id(), session);
        return true;
    }

    /**
     * Спрятавшийся бежит к своему месту без подписи, найденный — к месту
     * сбора; стоят, куда пришли, и смотрят на того, кто водит.
     */
    public static boolean steers(WorkContext context) {
        Session session = SESSIONS.get(context.settlement().id());
        UUID id = context.citizen().id();
        if (session == null || !session.spots.containsKey(id)) {
            return false;
        }
        CitizenEntity body = context.body();
        context.holdNothing();
        boolean found = session.found.contains(id);
        if (!found) {
            // Подпись снова ставится в начале каждого решения — снимаем
            // её здесь же, в том же тике: сеть увидит только итог.
            body.setCustomNameVisible(false);
        }
        body.setWorkTarget(found ? session.base : session.spots.get(id));
        body.setWorkFocus(null);
        return true;
    }

    /** Часы пряток: отсчёт, поиск, находки подходом, время и уход игрока. */
    public static void tick(ServerWorld world) {
        if (world.getTime() % TICK_EVERY != 0) {
            return;
        }
        SettlementManager manager = SettlementManager.get(world);
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            if (!session.world.equals(world.getRegistryKey()) || SESSIONS.get(session.settlement) != session) {
                continue;
            }
            Settlement settlement = manager.byId(session.settlement).orElse(null);
            if (settlement == null) {
                SESSIONS.remove(session.settlement, session);
                clearBar(session);
                continue;
            }
            PlayerEntity player = session.playerOf(world);
            if (player == null || !player.isAlive() || player.getWorld() != world
                    || player.squaredDistanceTo(Vec3d.ofCenter(session.base)) > STRAY * STRAY) {
                end(world, session, settlement, Ending.GAVE_UP);
                continue;
            }
            long elapsed = world.getTime() - session.started;
            if (elapsed < COUNTDOWN) {
                countdown(player, elapsed);
                continue;
            }
            if (elapsed >= COUNTDOWN + SEEK) {
                end(world, session, settlement, Ending.TIME);
                continue;
            }
            if (!session.seeking) {
                session.seeking = true;
                title(player, Text.translatable("villagepax.hide.seek"));
            }
            for (UUID id : new ArrayList<>(session.spots.keySet())) {
                if (session.found.contains(id) || SESSIONS.get(session.settlement) != session) {
                    continue;
                }
                Citizen child = settlement.citizen(id).orElse(null);
                CitizenEntity body = child == null ? null : Company.body(world, child).orElse(null);
                if (body != null && body.squaredDistanceTo(player) <= FOUND_AT * FOUND_AT) {
                    found(world, session, settlement, child);
                }
            }
            if (SESSIONS.get(session.settlement) == session) {
                updateBar(session, elapsed);
            }
        }
    }

    /** «Считаю… 10» — раз в секунду, крупно посреди экрана. */
    private static void countdown(PlayerEntity player, long elapsed) {
        if (elapsed % 20 >= TICK_EVERY) {
            return;
        }
        long seconds = (COUNTDOWN - elapsed + 19) / 20;
        title(player, Text.translatable("villagepax.hide.counting", seconds));
    }

    private static void title(PlayerEntity player, Text text) {
        if (player instanceof ServerPlayerEntity online) {
            online.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 25, 5));
            online.networkHandler.sendPacket(new TitleS2CPacket(text));
        }
    }

    private static void updateBar(Session session, long elapsed) {
        if (session.bar == null) {
            return;
        }
        long left = Math.max(0, COUNTDOWN + SEEK - elapsed) / 20;
        String clock = left / 60 + ":" + String.format("%02d", left % 60);
        session.bar.setName(Text.translatable("villagepax.hide.bar", session.found.size(),
                session.spots.size(), clock));
        session.bar.setPercent(Math.max(0f, Math.min(1f, (COUNTDOWN + SEEK - elapsed) / (float) SEEK)));
    }

    private static void clearBar(Session session) {
        if (session.bar != null) {
            session.bar.clearPlayers();
            session.bar = null;
        }
    }

    /** Нашёл: подпись возвращается, ребёнок хохочет и бежит к месту сбора. */
    private static void found(ServerWorld world, Session session, Settlement settlement, Citizen child) {
        if (!session.found.add(child.id())) {
            return;
        }
        Company.body(world, child).ifPresent(body -> {
            body.label(child, Configs.get().citizenLabels());
            body.cheer();
            Lines.sayNow(body, child, Say.HIDE_FOUND);
        });
        if (session.found.size() == session.spots.size()) {
            end(world, session, settlement, Ending.ALL_FOUND);
        }
    }

    /**
     * Конец пряток любого рода: полоса снята, у всех детей подпись видна,
     * и они возвращаются к обычному дню. Нашёл всех — гостинец в деревне,
     * радость в колонии; не нашёл — оставшиеся выходят сами.
     */
    private static void end(ServerWorld world, Session session, Settlement settlement, Ending how) {
        SESSIONS.remove(session.settlement, session);
        clearBar(session);
        for (UUID id : session.spots.keySet()) {
            settlement.citizen(id).ifPresent(child -> Company.body(world, child).ifPresent(body -> {
                body.label(child, Configs.get().citizenLabels());
                if (how != Ending.ALL_FOUND && !session.found.contains(id)) {
                    Lines.sayNow(body, child, Say.HIDE_LOST);
                }
            }));
        }
        if (how != Ending.ALL_FOUND) {
            return;
        }
        if (Bouts.forCoins(settlement)) {
            gift(world, session, settlement);
        } else {
            GamesLedger ledger = GamesLedger.get(world);
            session.spots.keySet().forEach(id -> ledger.markCheer(id, session.day));
        }
    }

    /**
     * Гостинец тому, кто нашёл всех: сласть народа и два медяка от матери
     * позвавшего — или отца, или любого взрослого, кто есть рядом телом.
     */
    private static void gift(ServerWorld world, Session session, Settlement settlement) {
        PlayerEntity player = session.playerOf(world);
        Citizen giver = giverOf(world, settlement, session.inviter).orElse(null);
        Item treat = treatOf(CultureManager.get(settlement.culture()));
        if (player != null) {
            PlayerInventory inventory = player.getInventory();
            inventory.offerOrDrop(new ItemStack(treat));
            for (ItemStack rest : Coins.earn(inventory, GIFT_COPPER)) {
                inventory.offerOrDrop(rest);
            }
            player.sendMessage(Text.translatable("villagepax.hide.gift",
                    giver == null ? Text.translatable("villagepax.hide.village") : Text.literal(giver.fullName()),
                    treat.getName()), true);
        }
        if (giver != null) {
            Company.body(world, giver).ifPresent(body -> Lines.sayNow(body, giver, Say.HIDE_THANKS));
        }
    }

    /** Кто дарит: мать позвавшего, иначе отец, иначе любой взрослый с телом. */
    private static Optional<Citizen> giverOf(ServerWorld world, Settlement settlement, UUID inviter) {
        List<Citizen> parents = settlement.citizen(inviter).map(child -> child.parents().stream()
                .map(settlement::citizen).flatMap(Optional::stream).toList()).orElse(List.of());
        return parents.stream()
                .filter(parent -> Company.body(world, parent).isPresent())
                .min(Comparator.comparing(parent -> parent.gender() != Gender.FEMALE))
                .or(() -> settlement.citizens().stream()
                        .filter(citizen -> !Ages.isChild(citizen))
                        .filter(citizen -> Company.body(world, citizen).isPresent())
                        .findFirst());
    }

    /** Сласть народа для гостинца; народ, который её не назвал, дарит печенье. */
    public static Item treatOf(Culture culture) {
        return Optional.ofNullable(culture).flatMap(Culture::treat)
                .map(Registries.ITEM::get)
                .filter(item -> item != Items.AIR)
                .orElse(Items.COOKIE);
    }

    /** Прекратить прятки без подарка: подписи на место, полоса снята. */
    public static void stop(ServerWorld world, Session session) {
        SettlementManager.get(world).byId(session.settlement).ifPresentOrElse(
                settlement -> end(world, session, settlement, Ending.GAVE_UP),
                () -> {
                    SESSIONS.remove(session.settlement, session);
                    clearBar(session);
                });
    }

    /** Сервер встаёт: прятки кончаются без подарка. Подписи тела поставят себе сами при появлении. */
    public static void stopAll(MinecraftServer server) {
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            clearBar(session);
        }
        SESSIONS.clear();
    }
}
