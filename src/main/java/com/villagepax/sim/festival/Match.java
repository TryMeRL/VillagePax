package com.villagepax.sim.festival;

import com.villagepax.core.festival.Festival;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Идущее состязание: отсчёт, минута игры, итоги.
 * <p>
 * Общее у поиска, ловли и стрельбы — здесь: кто участвует, счёт, полоса над
 * экраном, отсчёт, выбывшие, конец и призы. Своё у каждого вида — только
 * четыре дела: приготовить место, повести соперника, понять, что всё
 * кончилось раньше срока, и убрать за собой.
 * <p>
 * Очко засчитывается только пока идёт игра: вещица, найденная во время
 * отсчёта, — это фора, а не находка. Игрок, ушедший дальше 48 блоков или
 * вышедший из игры, выбывает вместе со счётом; выбыли все игроки —
 * состязание кончается без призов. Жители без игроков не играют:
 * состязание — для игрока, а не спектакль для пустой площади.
 * <p>
 * Подставной игрок проверок не живёт в списке игроков сервера. Поэтому
 * игрок ищется по опознавателю среди живых, а если его там нет и это не
 * игрок сервера — берётся тот, кого видели на старте.
 */
public abstract class Match {

    /** Три секунды отсчёта. */
    public static final int COUNTDOWN = 60;

    /** Минута игры. */
    public static final int LENGTH = 1200;

    /** Кто стоит ближе к сердцу на миг старта — участвует. */
    public static final double CATCHMENT = 16;

    /** Кто ушёл от сердца дальше — выбыл. */
    public static final double LEAVE = 48;

    /** Сколько жителей-соперников. */
    public static final int RIVALS = 3;

    /** Как часто вести соперников: чаще путь пересчитывался бы зря. */
    public static final int STEER_EVERY = 5;

    /** Как далеко от сердца отпускают соперника: поиск идёт по округе ярмарки. */
    static final int RIVAL_LEASH = 32;

    /** Кому в конце хлопать: жители в этом радиусе от сердца. */
    private static final double CHEERING = 24;

    private final UUID id;
    private final UUID settlement;
    private final RegistryKey<World> world;
    protected final Fair fair;
    protected final Festival festival;
    private final int index;
    private final long day;

    private final Map<UUID, Contestant> players = new LinkedHashMap<>();
    private final Map<UUID, PlayerEntity> seen = new HashMap<>();
    private final Map<UUID, Contestant> rivals = new LinkedHashMap<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, ServerBossBar> bars = new HashMap<>();

    private int age;
    private boolean over;
    private boolean barStale = true;

    protected Match(ServerWorld world, UUID id, Settlement settlement, Fair fair, Festival festival,
                    int index, long day) {
        this.id = id;
        this.settlement = settlement.id();
        this.world = world.getRegistryKey();
        this.fair = fair;
        this.festival = festival;
        this.index = index;
        this.day = day;
    }

    public UUID id() {
        return id;
    }

    public UUID settlement() {
        return settlement;
    }

    public RegistryKey<World> world() {
        return world;
    }

    public int index() {
        return index;
    }

    public long day() {
        return day;
    }

    public Fair fair() {
        return fair;
    }

    public Festival festival() {
        return festival;
    }

    public Festival.Contest contest() {
        return festival.contests().get(index);
    }

    /** Идёт ли игра: отсчёт кончился, итогов ещё нет. */
    public boolean isRunning() {
        return !over && age > COUNTDOWN;
    }

    public boolean isOver() {
        return over;
    }

    /** Сколько тиков игры осталось; во время отсчёта — вся минута. */
    public int ticksLeft() {
        return Math.max(0, LENGTH - Math.max(0, age - COUNTDOWN));
    }

    public boolean involves(UUID contestant) {
        return players.containsKey(contestant) || rivals.containsKey(contestant);
    }

    public boolean isPlayer(UUID contestant) {
        return players.containsKey(contestant);
    }

    public boolean isRival(UUID contestant) {
        return rivals.containsKey(contestant);
    }

    /** Игроки-участники, в порядке прихода. */
    public Collection<UUID> players() {
        return Collections.unmodifiableCollection(players.keySet());
    }

    /** Жители-соперники, в порядке выбора. */
    public Collection<UUID> rivals() {
        return Collections.unmodifiableCollection(rivals.keySet());
    }

    /** На старте: игрок участвует. Второй раз — ничего. */
    public void join(ServerWorld world, PlayerEntity player) {
        UUID who = player.getUuid();
        if (players.containsKey(who)) {
            return;
        }
        players.put(who, new Contestant(who, true, player.getName().getString()));
        seen.put(who, player);
        scores.putIfAbsent(who, 0);
        player.sendMessage(Text.translatable("villagepax.contest.joined",
                Text.translatable(contest().name()),
                Text.translatable("villagepax.contest.rule." + contest().kind().id()))
                .formatted(Formatting.YELLOW), false);
        joined(world, player);
    }

    public void addRival(Citizen citizen) {
        rivals.put(citizen.id(), new Contestant(citizen.id(), false, citizen.fullName()));
        scores.putIfAbsent(citizen.id(), 0);
    }

    /**
     * Очко участнику — только пока идёт игра.
     *
     * @return засчитано ли
     */
    public boolean score(UUID contestant, int points) {
        if (!isRunning() || !involves(contestant) || points <= 0) {
            return false;
        }
        scores.merge(contestant, points, Integer::sum);
        barStale = true;
        return true;
    }

    public int scoreOf(UUID contestant) {
        return scores.getOrDefault(contestant, 0);
    }

    /**
     * Игрок по опознавателю: живой на сервере — или тот, кого видели
     * на старте, если это не игрок сервера. Ушедший игрок сервера — никто.
     */
    public PlayerEntity playerOf(ServerWorld world, UUID player) {
        ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(player);
        if (online != null) {
            return online;
        }
        PlayerEntity once = seen.get(player);
        return once instanceof ServerPlayerEntity ? null : once;
    }

    /** Приготовить место: вещицы, зверьки, луки. Ложь — негде, и отказ назовёт причину. */
    protected abstract boolean prepare(ServerWorld world, Random random);

    /** Повести соперника: куда идти и что делать в этот тик. */
    protected abstract void steer(ServerWorld world, CitizenEntity body, Citizen rival);

    /** Кончилось ли раньше срока: всё найдено, пойманы все, выстрелы кончились. */
    protected abstract boolean exhausted(ServerWorld world);

    /** Убрать всё своё из мира — только то, что там всё ещё наше. */
    protected abstract void clear(ServerWorld world);

    /** Игрок только что вступил: стрельба даёт ему лук. */
    protected void joined(ServerWorld world, PlayerEntity player) {
    }

    /** Отсчёт кончился, игра пошла. */
    protected void began(ServerWorld world) {
    }

    /** Отсчёт, время, полоса, выбывшие, конец. */
    final void tick(ServerWorld world) {
        if (over) {
            return;
        }
        Settlement home = SettlementManager.get(world).byId(settlement).orElse(null);
        if (home == null) {
            cancel(world, "villagepax.contest.cancel.gone");
            return;
        }
        FestivalDay.Verdict today = FestivalDay.today(home, day);
        if (today != FestivalDay.Verdict.ON) {
            cancel(world, today == FestivalDay.Verdict.BESIEGED ? "villagepax.contest.cancel.besieged"
                    : "villagepax.contest.cancel.over");
            return;
        }
        dropLeavers(world);
        if (players.isEmpty()) {
            cancel(world, "villagepax.contest.cancel.empty");
            return;
        }
        age++;
        if (age <= COUNTDOWN) {
            if ((age - 1) % 20 == 0) {
                title(world, Text.literal(String.valueOf(3 - (age - 1) / 20)).formatted(Formatting.GOLD),
                        Text.translatable(contest().name()));
            }
            if (age % STEER_EVERY == 0) {
                steerRivals(world, home);
            }
            return;
        }
        if (age == COUNTDOWN + 1) {
            title(world, Text.translatable("villagepax.contest.go").formatted(Formatting.GREEN),
                    Text.empty());
            began(world);
        }
        if (age % STEER_EVERY == 0) {
            steerRivals(world, home);
        }
        int left = ticksLeft();
        if (left <= 0 || exhausted(world)) {
            finish(world);
            return;
        }
        if (barStale || age % 20 == 0) {
            showBars(world, left);
        }
    }

    /** Места → строки итогов → призы → ликование → уборка. */
    public final void finish(ServerWorld world) {
        if (over) {
            return;
        }
        over = true;
        SettlementManager manager = SettlementManager.get(world);
        Settlement home = manager.byId(settlement).orElse(null);
        List<Standings.Placing> placings = Standings.rank(table());
        tellAll(world, results(placings));
        if (home != null) {
            Awards.grant(world, manager, home, festival, index, day, placings,
                    player -> players.containsKey(player) ? playerOf(world, player) : null);
            cheer(world, home);
        }
        world.playSound(null, fair.heart(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL,
                1.0f, 1.0f);
        close(world);
    }

    /** Без призов: набег, праздник кончился, игроки ушли, сервер встаёт. */
    public final void cancel(ServerWorld world, String reasonKey) {
        if (over) {
            return;
        }
        over = true;
        tellAll(world, List.of(Text.translatable(reasonKey, Text.translatable(contest().name()))
                .formatted(Formatting.RED)));
        close(world);
    }

    /**
     * Повести соперника: общее — снять с хоровода и держать у ярмарки,
     * дело — у вида состязания. Во время отсчёта соперник стоит и ждёт.
     */
    final void steerRival(ServerWorld world, CitizenEntity body, Citizen citizen) {
        body.setDancing(false);
        body.keepNear(fair.heart(), RIVAL_LEASH);
        if (isRunning()) {
            steer(world, body, citizen);
        } else if (!over) {
            body.setWorkTarget(body.getBlockPos());
            body.setWorkFocus(fair.heart());
        }
    }

    private void steerRivals(ServerWorld world, Settlement home) {
        for (UUID rival : rivals.keySet()) {
            Citizen citizen = home.citizen(rival).orElse(null);
            CitizenEntity body = citizen == null ? null : bodyOf(world, citizen);
            if (body != null) {
                steerRival(world, body, citizen);
            }
        }
    }

    /** Живое тело жителя, если оно в мире. */
    static CitizenEntity bodyOf(ServerWorld world, Citizen citizen) {
        return citizen.entityUuid()
                .map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .map(CitizenEntity.class::cast)
                .filter(Entity::isAlive)
                .orElse(null);
    }

    private void dropLeavers(ServerWorld world) {
        Vec3d heart = Vec3d.ofCenter(fair.heart());
        for (UUID player : List.copyOf(players.keySet())) {
            PlayerEntity who = playerOf(world, player);
            boolean gone = who == null || who.isRemoved() || who.isSpectator() || who.getWorld() != world
                    || who.squaredDistanceTo(heart) > LEAVE * LEAVE;
            if (!gone) {
                continue;
            }
            players.remove(player);
            scores.remove(player);
            ServerBossBar bar = bars.remove(player);
            if (bar != null) {
                bar.clearPlayers();
            }
            if (who != null && !who.isRemoved()) {
                who.sendMessage(Text.translatable("villagepax.contest.left",
                        Text.translatable(contest().name())).formatted(Formatting.GRAY), false);
            }
            left(world, player);
        }
    }

    /** Игрок выбыл: стрельба забирает у него лук. */
    protected void left(ServerWorld world, UUID player) {
    }

    private Map<Contestant, Integer> table() {
        Map<Contestant, Integer> table = new LinkedHashMap<>();
        players.values().forEach(one -> table.put(one, scoreOf(one.id())));
        rivals.values().forEach(one -> table.put(one, scoreOf(one.id())));
        return table;
    }

    private List<Text> results(List<Standings.Placing> placings) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable("villagepax.contest.results", Text.translatable(contest().name()))
                .formatted(Formatting.GOLD));
        if (placings.isEmpty()) {
            lines.add(Text.translatable("villagepax.contest.results.none").formatted(Formatting.GRAY));
        }
        for (Standings.Placing placing : placings) {
            if (placing.place() > 3) {
                break;
            }
            lines.add(Text.translatable("villagepax.contest.results.line", placing.place(),
                    placing.who().name(), placing.score()));
        }
        return lines;
    }

    private void tellAll(ServerWorld world, List<Text> lines) {
        for (UUID player : players.keySet()) {
            PlayerEntity who = playerOf(world, player);
            if (who != null) {
                lines.forEach(line -> who.sendMessage(line, false));
            }
        }
    }

    private void title(ServerWorld world, Text title, Text subtitle) {
        for (UUID player : players.keySet()) {
            if (playerOf(world, player) instanceof ServerPlayerEntity online) {
                online.networkHandler.sendPacket(new TitleFadeS2CPacket(0, 20, 5));
                online.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
                online.networkHandler.sendPacket(new TitleS2CPacket(title));
            }
        }
        world.playSound(null, fair.heart(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                SoundCategory.NEUTRAL, 1.0f, 1.0f);
    }

    /** Полоса у каждого своя: в ней его собственный счёт. */
    private void showBars(ServerWorld world, int left) {
        barStale = false;
        Map.Entry<UUID, Integer> best = scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .orElse(null);
        Text leader = best == null ? Text.translatable("villagepax.contest.bar.nobody")
                : Text.literal(nameOf(best.getKey()));
        int bestScore = best == null ? 0 : best.getValue();
        int seconds = (left + 19) / 20;
        for (UUID player : players.keySet()) {
            if (!(playerOf(world, player) instanceof ServerPlayerEntity online)) {
                continue;
            }
            ServerBossBar bar = bars.computeIfAbsent(player, key -> new ServerBossBar(Text.empty(),
                    BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10));
            if (!bar.getPlayers().contains(online)) {
                bar.addPlayer(online);
            }
            bar.setName(Text.translatable("villagepax.contest.bar", Text.translatable(contest().name()),
                    seconds, scoreOf(player), leader, bestScore));
            bar.setPercent(Math.max(0f, left / (float) LENGTH));
        }
    }

    private String nameOf(UUID contestant) {
        Contestant one = players.containsKey(contestant) ? players.get(contestant) : rivals.get(contestant);
        return one == null ? "?" : one.name();
    }

    private void cheer(ServerWorld world, Settlement home) {
        Vec3d heart = Vec3d.ofCenter(fair.heart());
        for (Citizen citizen : home.citizens()) {
            CitizenEntity body = bodyOf(world, citizen);
            if (body != null && body.squaredDistanceTo(heart) <= CHEERING * CHEERING) {
                body.cheer();
            }
        }
    }

    private void close(ServerWorld world) {
        bars.values().forEach(ServerBossBar::clearPlayers);
        bars.clear();
        clear(world);
    }
}
