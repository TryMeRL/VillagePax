package com.villagepax.sim.life;

import com.villagepax.core.Named;
import com.villagepax.sim.Standing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
}
