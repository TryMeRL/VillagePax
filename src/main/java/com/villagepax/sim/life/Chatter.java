package com.villagepax.sim.life;

import com.villagepax.core.Named;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Standing;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.sim.festival.Matches;
import com.villagepax.sim.games.Bouts;
import com.villagepax.sim.games.HideAndSeek;
import com.villagepax.sim.games.Lines;
import com.villagepax.sim.work.BuilderJob;
import com.villagepax.sim.work.Needs;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * Живые реплики: житель говорит над головой о своём настоящем.
 * <p>
 * Главная жалоба на моды с работниками — «жители как роботы»: работают
 * молча, и о колонии игрок узнаёт только из окна. Здесь житель сам скажет,
 * что голоден, что ему негде спать, что у него родился сын, что завтра
 * праздник и что вчера отбивались от набега, — и поздоровается с игроком
 * так, как деревня к нему относится. Только правда: тема берётся из
 * положения дел, а не наугад из списка.
 * <p>
 * Правило «о чём» отделено от мира и проверяется без него; мир собирает
 * положение и говорит — см. {@link ChatterTicker}.
 */
public final class Chatter {

    /** О чём житель может сказать. Имя — часть ключа словаря: {@code villagepax.say.<тема>}. */
    public enum Topic implements Named {
        /** Враг у ворот: страшно. */
        BESIEGED("besieged"),
        /** Голоден. */
        HUNGRY("hungry"),
        /** У него родился ребёнок: имя — в фразу. */
        NEWBORN("newborn"),
        /** Недавно отбивались от набега. */
        RAIDED("raided"),
        /** Завтра праздник. */
        FESTIVAL("festival"),
        /** Негде спать. */
        HOMELESS("homeless"),
        /** Стройка ждёт, а строить некому. */
        NO_BUILDER("no_builder"),
        /** Пришёл недавно, никого тут не знает. */
        NEWCOMER("newcomer"),
        /** Льёт дождь. */
        RAIN("rain"),
        /** Перед ним хозяин колонии. */
        OWNER("owner"),
        /** Перед ним друг деревни. */
        FRIEND("friend"),
        /** Незнакомец. */
        STRANGER("stranger"),
        /** Тот, кто деревне насолил. */
        WARY("wary"),
        /** О своём деле: {@code villagepax.say.work.<ремесло>}. */
        WORK("work");

        private final String id;

        Topic(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        /** База ключа фразы. */
        public String base() {
            return "villagepax.say." + id;
        }
    }

    /**
     * Положение жителя глазами правила — без мира.
     *
     * @param besieged         у ворот поселения враг
     * @param hungry           голоден
     * @param newborn          имя его ребёнка, родившегося на днях
     * @param raidedLately     набег был день-два назад
     * @param festivalTomorrow завтра праздник
     * @param homeless         нет кровати
     * @param nobodyBuilds     стройка ждёт, а строителя нет
     * @param newcomer         пришёл на днях
     * @param raining          идёт дождь
     * @param owner            перед ним хозяин колонии
     * @param trust            доверие поселения к игроку
     * @param trade            ремесло жителя — путь опознавателя
     * @param child            ребёнок
     */
    public record Situation(boolean besieged, boolean hungry, Optional<String> newborn,
                            boolean raidedLately, boolean festivalTomorrow, boolean homeless,
                            boolean nobodyBuilds, boolean newcomer, boolean raining, boolean owner,
                            int trust, Optional<String> trade, boolean child) {
    }

    private Chatter() {
    }

    /**
     * Что из этого правда сейчас.
     * <p>
     * Осада и голод — срочное: житель, у которого враг у ворот или пусто
     * в животе, о погоде не говорит, и тема у него одна. Иначе — всё, что
     * правда, и выбор между ними наугад: так один и тот же житель говорит
     * то о празднике, то о ремесле, то здоровается.
     */
    public static List<Topic> topics(Situation at) {
        if (at.besieged()) {
            return List.of(Topic.BESIEGED);
        }
        if (at.hungry()) {
            return List.of(Topic.HUNGRY);
        }
        List<Topic> topics = new ArrayList<>();
        at.newborn().ifPresent(name -> topics.add(Topic.NEWBORN));
        if (at.raidedLately()) {
            topics.add(Topic.RAIDED);
        }
        if (at.festivalTomorrow()) {
            topics.add(Topic.FESTIVAL);
        }
        if (at.homeless()) {
            topics.add(Topic.HOMELESS);
        }
        if (at.nobodyBuilds() && !at.child()) {
            topics.add(Topic.NO_BUILDER);
        }
        if (at.newcomer()) {
            topics.add(Topic.NEWCOMER);
        }
        if (at.raining()) {
            topics.add(Topic.RAIN);
        }
        topics.add(greeting(at));
        if (at.trade().isPresent() && !at.child()) {
            topics.add(Topic.WORK);
        }
        return topics;
    }

    /** Как поздороваться: хозяину — как хозяину, дальше — по доверию. */
    static Topic greeting(Situation at) {
        if (at.owner()) {
            return Topic.OWNER;
        }
        if (at.trust() < 0) {
            return Topic.WARY;
        }
        return at.trust() >= Standing.FRIEND.from() ? Topic.FRIEND : Topic.STRANGER;
    }

    /** Одна тема из правдивых: случай по границе — номер от нуля до неё. */
    public static Topic choose(List<Topic> topics, IntUnaryOperator choose) {
        return topics.get(choose.applyAsInt(topics.size()));
    }
    // --- в мире ---

    /** Житель говорит не чаще раза в столько тиков: минута. */
    public static final int CITIZEN_EVERY = 1_200;

    /** Набег «недавно» — не дальше стольких дней. */
    static final int RAID_REMEMBERED = 2;

    /** Когда житель говорил последний раз. Только сервер: голос, а не память. */
    private static final Map<UUID, Long> SPOKE = new HashMap<>();

    /** Положение жителя из мира — то, что правило спросит. */
    public static Situation situation(ServerWorld world, Settlement settlement, Citizen citizen,
                                      PlayerEntity player, long day) {
        Optional<String> newborn = Families.childrenOf(settlement, citizen).stream()
                .filter(child -> child.lived() >= 0 && child.lived() <= 1)
                .map(Citizen::firstName)
                .findFirst();
        boolean raided = settlement.lastRaid() != Settlement.UNSEEN_DAY
                && day - settlement.lastRaid() <= RAID_REMEMBERED && settlement.siege().isEmpty();
        boolean newcomer = citizen.parents().isEmpty() && citizen.lived() >= 0
                && citizen.lived() - Ages.grownAt() <= 1;
        return new Situation(settlement.siege().isPresent(), Needs.isHungry(citizen), newborn, raided,
                FestivalDay.isOn(settlement, day + 1), citizen.isHomeless(),
                BuilderJob.nobodyBuilds(settlement), newcomer, world.isRaining(),
                settlement.owner().isOwnedBy(player.getUuid()), settlement.reputationOf(player.getUuid()),
                citizen.profession().map(net.minecraft.util.Identifier::getPath), Ages.isChild(citizen));
    }

    /** Занят ли житель так, что ему не до разговоров: спит, играет, прячется, состязается. */
    static boolean busy(Settlement settlement, Citizen citizen, CitizenEntity body) {
        return body.isSleeping() || body.isDozing()
                || Bouts.rivalOf(citizen.id()).isPresent()
                || HideAndSeek.at(settlement.id()).flatMap(game -> game.spotOf(citizen.id())).isPresent()
                || Matches.at(settlement.id()).filter(match -> match.isRival(citizen.id())).isPresent();
    }

    /**
     * Сказать что-нибудь правдивое рядом с игроком — если пора и не занят.
     *
     * @return о чём сказал; пусто — промолчал
     */
    public static Optional<Topic> speak(ServerWorld world, Settlement settlement, Citizen citizen,
                                        CitizenEntity body, PlayerEntity player, long day,
                                        Random random) {
        long now = world.getTime();
        Long last = SPOKE.get(citizen.id());
        if (last != null && now - last < CITIZEN_EVERY || busy(settlement, citizen, body)) {
            return Optional.empty();
        }
        Situation at = situation(world, settlement, citizen, player, day);
        Topic topic = choose(topics(at), random::nextInt);
        String base = topic == Topic.WORK
                ? topic.base() + "." + at.trade().orElse("none")
                : topic.base();
        Object argument = topic == Topic.NEWBORN
                ? Text.literal(at.newborn().orElse(""))
                : player.getName();
        if (!Lines.sayKey(body, citizen, base, false, argument)) {
            return Optional.empty();
        }
        SPOKE.put(citizen.id(), now);
        // Сказавший поворачивается к игроку: фраза — ему.
        body.getLookControl().lookAt(player, 30f, 30f);
        return Optional.of(topic);
    }

    /** Забыть, кто когда говорил: мир сменился. */
    public static void forget() {
        SPOKE.clear();
    }
}
