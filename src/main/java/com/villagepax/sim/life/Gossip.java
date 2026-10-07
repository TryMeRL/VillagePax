package com.villagepax.sim.life;

import com.villagepax.core.Named;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.sim.games.Lines;
import com.villagepax.sim.work.Needs;
import com.villagepax.sim.work.Schedule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Сплетни на вечерней площади: двое переговариваются о делах деревни.
 * <p>
 * Вечером жители сходятся к ратуше, и раньше просто стояли кругом. Теперь
 * один скажет другому то, что правда сейчас: у кого прибавление, кто
 * новенький, кто ходит голодный или ночует под небом, что завтра праздник,
 * что недавно был набег, что снова заходил игрок, — а другой через секунду
 * ответит. Игрок, стоящий рядом, слышит о деревне больше, чем видно
 * в окне, — и о голодном узнаёт раньше, чем тот уйдёт.
 */
public final class Gossip {

    /** О чём судачат. Имя — часть ключа: {@code villagepax.gossip.<тема>}. */
    public enum Topic implements Named {
        NEWBORN("newborn"),
        NEWCOMER("newcomer"),
        HUNGRY("hungry"),
        HOMELESS("homeless"),
        FESTIVAL("festival"),
        RAIDED("raided"),
        PLAYER("player"),
        VILLAGE("village");

        private final String id;

        Topic(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        public String base() {
            return "villagepax.gossip." + id;
        }
    }

    /** База ответа: «Да ну?», «Вот это новость!». */
    public static final String REPLY = "villagepax.gossip.reply";

    /**
     * Житель глазами сплетни.
     *
     * @param newParent у него на днях родился ребёнок
     */
    public record Person(UUID id, String name, boolean hungry, boolean homeless, boolean newcomer,
                         boolean newParent) {
    }

    /** Одна сплетня: тема и о ком; пусто — о деревне вообще. */
    public record Item(Topic topic, String about) {
    }

    /** Площадь слышно — игрок не дальше стольких блоков от ратуши. */
    static final double AUDIENCE = 16;

    /** Двое разговаривают — если стоят не дальше стольких блоков. */
    static final double TOGETHER = 4;

    /** Поселение сплетничает не чаще раза в столько тиков: двадцать секунд. */
    static final int EVERY = 400;

    /** Ответ — через столько тиков после сплетни. */
    public static final int REPLY_AFTER = 20;

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, Reply> REPLIES = new LinkedHashMap<>();

    private record Reply(RegistryKey<World> world, UUID village, UUID citizen, long at) {
    }

    private Gossip() {
    }

    /** Что можно сказать: только правда, и не о себе. Деревня — тема всегда. */
    public static List<Item> items(UUID speaker, List<Person> people, boolean festivalTomorrow,
                                   boolean raidedLately, Optional<String> player) {
        List<Item> items = new ArrayList<>();
        for (Person person : people) {
            if (person.id().equals(speaker)) {
                continue;
            }
            if (person.newParent()) {
                items.add(new Item(Topic.NEWBORN, person.name()));
            }
            if (person.newcomer()) {
                items.add(new Item(Topic.NEWCOMER, person.name()));
            }
            if (person.hungry()) {
                items.add(new Item(Topic.HUNGRY, person.name()));
            }
            if (person.homeless()) {
                items.add(new Item(Topic.HOMELESS, person.name()));
            }
        }
        if (festivalTomorrow) {
            items.add(new Item(Topic.FESTIVAL, ""));
        }
        if (raidedLately) {
            items.add(new Item(Topic.RAIDED, ""));
        }
        player.ifPresent(name -> items.add(new Item(Topic.PLAYER, name)));
        // О деревне вообще — всегда: иначе в спокойной деревне сплетня была бы
        // одна, про игрока, и он слушал бы о себе каждый вечер.
        items.add(new Item(Topic.VILLAGE, ""));
        return items;
    }

    // --- в мире ---

    /**
     * Сплетня на площади этого поселения — если вечер, игрок рядом и пора.
     *
     * @return о чём сказали; пусто — промолчали
     */
    public static Optional<Item> tick(ServerWorld world, Settlement settlement, long day, long timeOfDay,
                                      List<? extends PlayerEntity> players, Random random) {
        if (Schedule.at(timeOfDay) != Schedule.LEISURE) {
            return Optional.empty();
        }
        Vec3d centre = Vec3d.ofCenter(settlement.center());
        PlayerEntity listener = players.stream()
                .filter(player -> !player.isSpectator()
                        && player.squaredDistanceTo(centre) <= AUDIENCE * AUDIENCE)
                .findFirst().orElse(null);
        if (listener == null) {
            return Optional.empty();
        }
        long now = world.getTime();
        Long last = LAST.get(settlement.id());
        if (last != null && now - last < EVERY) {
            return Optional.empty();
        }

        // Судачат там, где игрок слышит: пара в другом конце деревни тратила
        // бы двадцать секунд тишины на слова, которых никто не услышал.
        PlayerEntity ear = listener;
        List<Citizen> adults = settlement.citizens().stream()
                .filter(citizen -> !Ages.isChild(citizen))
                .filter(citizen -> bodyOf(world, citizen)
                        .filter(body -> !Chatter.busy(settlement, citizen, body))
                        .filter(body -> body.squaredDistanceTo(ear) <= AUDIENCE * AUDIENCE)
                        .isPresent())
                .toList();
        List<Citizen[]> pairs = new ArrayList<>();
        for (int a = 0; a < adults.size(); a++) {
            for (int b = a + 1; b < adults.size(); b++) {
                CitizenEntity one = bodyOf(world, adults.get(a)).orElseThrow();
                CitizenEntity two = bodyOf(world, adults.get(b)).orElseThrow();
                if (one.squaredDistanceTo(two) <= TOGETHER * TOGETHER) {
                    pairs.add(new Citizen[]{adults.get(a), adults.get(b)});
                }
            }
        }
        if (pairs.isEmpty()) {
            return Optional.empty();
        }
        Citizen[] pair = pairs.get(random.nextInt(pairs.size()));
        Citizen speaker = pair[random.nextInt(2)];
        Citizen hearer = pair[0] == speaker ? pair[1] : pair[0];

        boolean raided = settlement.lastRaid() != Settlement.UNSEEN_DAY
                && day - settlement.lastRaid() <= Chatter.RAID_REMEMBERED && settlement.siege().isEmpty();
        // И не о том, кто слушает: «У Эммы прибавление!» — самой Эмме.
        List<Person> people = settlement.citizens().stream()
                .filter(citizen -> !citizen.id().equals(hearer.id()))
                .map(citizen -> personOf(settlement, citizen)).toList();
        List<Item> items = items(speaker.id(), people, FestivalDay.isOn(settlement, day + 1), raided,
                Optional.of(listener.getName().getString()));
        Item item = items.get(random.nextInt(items.size()));

        CitizenEntity body = bodyOf(world, speaker).orElseThrow();
        if (!Lines.sayKey(body, speaker, item.topic().base(), false, Text.literal(item.about()))) {
            return Optional.empty();
        }
        body.getLookControl().lookAt(bodyOf(world, hearer).orElseThrow(), 30f, 30f);
        LAST.put(settlement.id(), now);
        REPLIES.put(settlement.id(), new Reply(world.getRegistryKey(), settlement.id(), hearer.id(),
                now + REPLY_AFTER));
        return Optional.of(item);
    }

    /** Ответы, которым пришёл срок: «Да ну?» через секунду после сплетни. */
    public static void replies(ServerWorld world, SettlementManager manager, long now) {
        for (Iterator<Reply> it = REPLIES.values().iterator(); it.hasNext(); ) {
            Reply reply = it.next();
            if (!reply.world().equals(world.getRegistryKey()) || reply.at() > now) {
                continue;
            }
            it.remove();
            manager.byId(reply.village()).flatMap(village -> village.citizen(reply.citizen()))
                    .ifPresent(citizen -> bodyOf(world, citizen)
                            .ifPresent(body -> Lines.sayKey(body, citizen, REPLY, true)));
        }
    }

    /** Забыть всё: сервер встаёт. */
    public static void forget() {
        LAST.clear();
        REPLIES.clear();
    }

    private static Person personOf(Settlement settlement, Citizen citizen) {
        boolean newcomer = citizen.parents().isEmpty() && citizen.lived() >= 0
                && citizen.lived() - Ages.grownAt() <= 1;
        boolean newParent = Families.childrenOf(settlement, citizen).stream()
                .anyMatch(child -> child.lived() >= 0 && child.lived() <= 1);
        return new Person(citizen.id(), citizen.firstName(), Needs.isHungry(citizen),
                citizen.isHomeless() && !Ages.isChild(citizen), newcomer, newParent);
    }

    private static Optional<CitizenEntity> bodyOf(ServerWorld world, Citizen citizen) {
        return citizen.entityUuid().map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .map(CitizenEntity.class::cast)
                .filter(CitizenEntity::isAlive);
    }
}
