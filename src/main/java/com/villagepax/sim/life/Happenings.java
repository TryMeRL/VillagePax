package com.villagepax.sim.life;

import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.sim.games.HideAndSeek;
import com.villagepax.sim.games.Lines;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Случай дня: то, чего в поселении не бывает каждый день.
 * <p>
 * До сих пор день деревни был расписанием: утро, работа, вечерний сбор,
 * раз в неделю рынок, раз в восемь дней праздник. Расписание надёжно,
 * но скучно — заходить в деревню во вторник незачем, если ты был
 * в ней в понедельник. Случай — третий вид событий, рядом с расписанием
 * и причиной (набег, свадьба): небольшой, редкий и с местом для игрока.
 * <ul>
 *   <li><b>Щедрое поле</b> — фермеры снимают вдвое;</li>
 *   <li><b>Именины</b> — поздравь жителя, и он угостит;</li>
 *   <li><b>Ссора</b> — двое вечером спорят у ратуши; встань на сторону
 *       одного, и спор кончится: правому — радость, другому — обида.</li>
 * </ul>
 * <b>Случай выводится, а не хранится</b>: из опознавателя поселения и номера
 * дня, тем же броском у всех, кто спрашивает. Поэтому его не нужно ни
 * сохранять, ни рассылать: фермер, вестник при въезде и щелчок по жителю
 * видят один и тот же день. В памяти сервера живёт только то, что сделал
 * игрок: кого уже поздравил, чью ссору уже рассудил.
 */
public final class Happenings {

    /** Что случилось сегодня. */
    public enum Kind {
        NONE,
        BUMPER_HARVEST,
        NAME_DAY,
        QUARREL
    }

    /** Случай дня: имя случая и о ком он, если о ком-то. */
    public record Today(Kind kind, Optional<UUID> one, Optional<UUID> other) {
        static final Today NOTHING = new Today(Kind.NONE, Optional.empty(), Optional.empty());
    }

    /** Из ста дней столько — щедрое поле. */
    static final int BUMPER_CHANCE = 10;

    /** Столько — чьи-то именины. */
    static final int NAME_DAY_CHANCE = 12;

    /** Столько — ссора. Реже прочих: ссора каждую неделю — это уже вражда. */
    static final int QUARREL_CHANCE = 8;

    /** Сколько довольства прибавляет поздравление имениннику и правота в споре. */
    static final int GLAD = 4;

    /** Во что обходится неправота в споре. */
    static final int SORE = 3;

    /** Доверие деревни народа к тому, кто поздравил её жителя. */
    static final int WISHER_TRUST = 1;

    /** Видно случай на столько блоков: искры над именинником, брань спорщиков. */
    static final double NEAR = 16;

    /** Спорщик бранится не чаще раза в столько тиков. */
    static final int SNAP_EVERY = 100;

    /** Кто кого сегодня поздравил: «игрок/житель». */
    private static final Set<String> WISHED = new HashSet<>();

    /** Чью ссору рассудили: поселение → день. */
    private static final Map<UUID, Long> SETTLED = new HashMap<>();

    /** Когда спорщик бранился последний раз: житель → тик. */
    private static final Map<UUID, Long> SNAPPED = new HashMap<>();

    /**
     * Выключатель для игровых тестов: там опознаватели поселений случайны,
     * и случай выпадал бы где попало — ссора уводила бы игрока из-за стола,
     * а именины перехватывали щелчок. Правило случая проверяется отдельно,
     * юнит-тестами.
     */
    private static boolean quiet;

    /** Какой день помнят наборы выше: с новым днём они пустеют. */
    private static long remembered = Long.MIN_VALUE;

    private Happenings() {
    }

    /**
     * Случай этого дня в этом поселении. Чистое правило: тот же ответ
     * на тот же день, сколько ни спрашивай.
     * <p>
     * В праздник и в осаду случаев нет: одного большого события на день
     * хватает, а под стенами не до именин.
     */
    public static Today of(Settlement settlement, long day) {
        if (quiet || settlement.siege().isPresent() || FestivalDay.isOn(settlement, day)) {
            return Today.NOTHING;
        }
        Random dice = new Random(settlement.id().getMostSignificantBits() * 31
                ^ settlement.id().getLeastSignificantBits()
                ^ day * 0x9E3779B97F4A7C15L);
        int roll = dice.nextInt(100);
        List<Citizen> adults = adults(settlement);
        if (roll < BUMPER_CHANCE) {
            return settlement.population() >= 3 ? new Today(Kind.BUMPER_HARVEST,
                    Optional.empty(), Optional.empty()) : Today.NOTHING;
        }
        roll -= BUMPER_CHANCE;
        if (roll < NAME_DAY_CHANCE) {
            if (adults.isEmpty()) {
                return Today.NOTHING;
            }
            return new Today(Kind.NAME_DAY, Optional.of(adults.get(dice.nextInt(adults.size())).id()),
                    Optional.empty());
        }
        roll -= NAME_DAY_CHANCE;
        if (roll < QUARREL_CHANCE) {
            if (adults.size() < 2) {
                return Today.NOTHING;
            }
            int a = dice.nextInt(adults.size());
            int b = (a + 1 + dice.nextInt(adults.size() - 1)) % adults.size();
            return new Today(Kind.QUARREL, Optional.of(adults.get(a).id()),
                    Optional.of(adults.get(b).id()));
        }
        return Today.NOTHING;
    }

    /** Взрослые поселения в постоянном порядке: бросок должен падать на одних и тех же. */
    private static List<Citizen> adults(Settlement settlement) {
        List<Citizen> adults = new ArrayList<>();
        for (Citizen citizen : settlement.citizens()) {
            if (!Ages.isChild(citizen)) {
                adults.add(citizen);
            }
        }
        adults.sort(Comparator.comparing(Citizen::id));
        return adults;
    }

    /** Во сколько раз сегодня богаче урожай. */
    public static int harvestTimes(Settlement settlement, long day) {
        return of(settlement, day).kind() == Kind.BUMPER_HARVEST ? 2 : 1;
    }

    /** Строка новостей о случае: для въезда и глашатая. */
    public static Optional<Text> news(Settlement settlement, long day) {
        Today today = of(settlement, day);
        return switch (today.kind()) {
            case NONE -> Optional.empty();
            case BUMPER_HARVEST -> Optional.of(Text.translatable("villagepax.happening.bumper.news"));
            case NAME_DAY -> Optional.of(Text.translatable("villagepax.happening.name_day.news",
                    nameOf(settlement, today.one())));
            case QUARREL -> SETTLED.getOrDefault(settlement.id(), Long.MIN_VALUE) == day
                    ? Optional.empty()
                    : Optional.of(Text.translatable("villagepax.happening.quarrel.news",
                    nameOf(settlement, today.one()), nameOf(settlement, today.other())));
        };
    }

    private static String nameOf(Settlement settlement, Optional<UUID> citizen) {
        return citizen.flatMap(settlement::citizen).map(Citizen::firstName).orElse("?");
    }

    /**
     * Спорщикам вечером — к ратуше, лицом друг к другу. Остальных
     * это не касается.
     *
     * @return заняли ли решение жителя
     */
    public static boolean takesOver(WorkContext context, Schedule part, long day) {
        if (part != Schedule.LEISURE) {
            return false;
        }
        Settlement settlement = context.settlement();
        Today today = of(settlement, day);
        if (today.kind() != Kind.QUARREL || SETTLED.getOrDefault(settlement.id(), Long.MIN_VALUE) == day) {
            return false;
        }
        UUID me = context.citizen().id();
        boolean first = today.one().filter(me::equals).isPresent();
        boolean second = today.other().filter(me::equals).isPresent();
        if (!first && !second) {
            return false;
        }
        // К северу от ратуши: юг занят свадьбой, а ссора и свадьба
        // в один вечер — разные случаи, но место пусть будет своё.
        BlockPos spot = settlement.center().offset(Direction.NORTH, 4)
                .offset(first ? Direction.WEST : Direction.EAST, 1);
        context.holdNothing();
        context.body().setWorkTarget(context.hasArrivedAt(spot) ? null : spot);
        UUID rival = first ? today.other().orElseThrow() : today.one().orElseThrow();
        settlement.citizen(rival).flatMap(citizen -> bodyNow(context.world(), citizen))
                .ifPresent(other -> context.body().setWorkFocus(other.getBlockPos()));
        return true;
    }

    /**
     * Игрок щёлкнул по жителю: не именинник ли он, не спорщик ли.
     *
     * @return ответил ли случай на щелчок — тогда ничего другого щелчок не делает
     */
    public static boolean answer(ServerWorld world, PlayerEntity player, Settlement settlement,
                                 Citizen citizen, CitizenEntity body, long day) {
        forgetOld(day);
        Today today = of(settlement, day);
        UUID me = citizen.id();
        if (today.kind() == Kind.NAME_DAY && today.one().filter(me::equals).isPresent()) {
            return wish(world, player, settlement, citizen, body);
        }
        if (today.kind() == Kind.QUARREL
                && SETTLED.getOrDefault(settlement.id(), Long.MIN_VALUE) != day
                && Schedule.at(world.getTimeOfDay()) == Schedule.LEISURE
                && (today.one().filter(me::equals).isPresent()
                || today.other().filter(me::equals).isPresent())) {
            UUID loser = today.one().filter(me::equals).isPresent()
                    ? today.other().orElseThrow() : today.one().orElseThrow();
            settle(world, player, settlement, citizen, body, loser, day);
            return true;
        }
        return false;
    }

    /** Поздравить именинника: раз в день от каждого игрока. */
    private static boolean wish(ServerWorld world, PlayerEntity player, Settlement settlement,
                                Citizen citizen, CitizenEntity body) {
        if (!WISHED.add(player.getUuid() + "/" + citizen.id())) {
            return false;
        }
        Item treat = HideAndSeek.treatOf(CultureManager.get(settlement.culture()));
        player.getInventory().offerOrDrop(new ItemStack(treat));
        Lines.sayKey(body, citizen, "villagepax.happening.name_day.thanks", true);
        player.sendMessage(Text.translatable("villagepax.happening.name_day.treat",
                citizen.firstName(), treat.getName()).formatted(Formatting.LIGHT_PURPLE), true);
        world.spawnParticles(ParticleTypes.HEART, body.getX(), body.getY() + 2.2, body.getZ(),
                3, 0.3, 0.2, 0.3, 0.0);
        world.playSound(null, body.getBlockPos(), SoundEvents.ENTITY_VILLAGER_CELEBRATE,
                SoundCategory.NEUTRAL, 1.0f, 1.0f);
        SettlementManager.get(world).update(settlement.id(), state -> {
            state.citizen(citizen.id()).ifPresent(c -> c.setHappiness(c.happiness() + GLAD));
            if (state.owner().isAutonomous()) {
                state.addReputation(player.getUuid(), WISHER_TRUST);
            }
        });
        return true;
    }

    /** Рассудить спор: тот, по кому щёлкнули, прав. */
    private static void settle(ServerWorld world, PlayerEntity player, Settlement settlement,
                               Citizen winner, CitizenEntity body, UUID loser, long day) {
        SETTLED.put(settlement.id(), day);
        String loserName = settlement.citizen(loser).map(Citizen::firstName).orElse("?");
        Lines.sayKey(body, winner, "villagepax.happening.quarrel.won", true);
        settlement.citizen(loser).ifPresent(citizen -> bodyNow(world, citizen).ifPresent(other ->
                Lines.sayKey(other, citizen, "villagepax.happening.quarrel.lost", true)));
        player.sendMessage(Text.translatable("villagepax.happening.quarrel.settled",
                winner.firstName(), loserName).formatted(Formatting.YELLOW), true);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY() + 2.0,
                body.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
        SettlementManager.get(world).update(settlement.id(), state -> {
            state.citizen(winner.id()).ifPresent(c -> c.setHappiness(c.happiness() + GLAD));
            state.citizen(loser).ifPresent(c -> c.setHappiness(c.happiness() - SORE));
        });
    }

    /**
     * Ход случаев: раз в секунду из обхода живых реплик. Искры над
     * именинником и брань спорщиков — когда рядом есть кому смотреть.
     */
    public static void tick(ServerWorld world, SettlementManager manager,
                            List<? extends PlayerEntity> players, long day, Random random) {
        forgetOld(day);
        if (players.isEmpty()) {
            return;
        }
        boolean evening = Schedule.at(world.getTimeOfDay()) == Schedule.LEISURE;
        for (Settlement settlement : manager.all()) {
            Today today = of(settlement, day);
            if (today.kind() == Kind.NAME_DAY) {
                today.one().flatMap(settlement::citizen).flatMap(c -> bodyNow(world, c))
                        .filter(body -> watched(body, players))
                        .ifPresent(body -> world.spawnParticles(ParticleTypes.NOTE, body.getX(),
                                body.getY() + 2.3, body.getZ(), 1, 0.3, 0.1, 0.3, 1.0));
            } else if (today.kind() == Kind.QUARREL && evening
                    && SETTLED.getOrDefault(settlement.id(), Long.MIN_VALUE) != day) {
                for (Optional<UUID> who : List.of(today.one(), today.other())) {
                    who.flatMap(settlement::citizen).ifPresent(citizen ->
                            bodyNow(world, citizen).filter(body -> watched(body, players))
                                    .ifPresent(body -> snap(world, citizen, body, random)));
                }
            }
        }
    }

    private static void snap(ServerWorld world, Citizen citizen, CitizenEntity body, Random random) {
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, body.getX(), body.getY() + 2.0,
                body.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
        long now = world.getTime();
        long last = SNAPPED.getOrDefault(citizen.id(), Long.MIN_VALUE / 2);
        if (now - last >= SNAP_EVERY + random.nextInt(SNAP_EVERY)
                && Lines.sayKey(body, citizen, "villagepax.happening.quarrel.snap", false)) {
            SNAPPED.put(citizen.id(), now);
        }
    }

    private static boolean watched(CitizenEntity body, List<? extends PlayerEntity> players) {
        for (PlayerEntity player : players) {
            if (!player.isSpectator() && player.squaredDistanceTo(body) <= NEAR * NEAR) {
                return true;
            }
        }
        return false;
    }

    private static Optional<CitizenEntity> bodyNow(ServerWorld world, Citizen citizen) {
        return citizen.entityUuid().map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .map(CitizenEntity.class::cast)
                .filter(CitizenEntity::isAlive);
    }

    /** С новым днём вчерашнее не нужно. */
    private static void forgetOld(long day) {
        if (remembered != day) {
            remembered = day;
            WISHED.clear();
            SNAPPED.clear();
            SETTLED.values().removeIf(settled -> settled != day);
        }
    }

    /** Без случаев: для игровых тестов, где поселения случайны. */
    public static void quiet() {
        quiet = true;
    }

    /** Забыть всё: сервер встаёт. */
    public static void forget() {
        WISHED.clear();
        SETTLED.clear();
        SNAPPED.clear();
        remembered = Long.MIN_VALUE;
    }
}
