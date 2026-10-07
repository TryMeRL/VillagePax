package com.villagepax.sim.life;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Founding;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.work.Workplaces;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Пары и дети: то, чем колония растёт сама.
 * <p>
 * До сих пор жители приходили <b>извне</b> — по одному в день, под
 * свободную кровать. Это работало, но означало, что колония не растёт,
 * а <i>набирается</i>: у пришедших нет ни прошлого, ни родни, и ни одного
 * из них игрок не отличает от следующего. Семья — единственное, что
 * превращает список имён в людей: у Роллона есть жена, у них есть сын,
 * и когда старый Роллон умрёт, сын останется.
 * <p>
 * <b>Одна свадьба и одни роды за сутки.</b> Не из скупости: событие,
 * которое случается по пять раз за день, перестаёт быть событием и
 * становится строкой в логе. Раз в день игрок успевает прочитать,
 * кто на ком женился.
 * <p>
 * Приток извне остаётся: колония без единой пары иначе не завелась бы
 * вовсе, а пришлые — это ещё и способ добрать ремесло, которого нет.
 */
public final class Families {

    /**
     * Сколько дней ребёнку, прежде чем у пары появится следующий.
     * <p>
     * Считается по <b>младшему из уже рождённых</b>, а не по записи
     * «когда рожали в прошлый раз». Записи взяться негде — кодек
     * поселения полон, — а младший ребёнок и есть эта запись: он ходит
     * по колонии и помнит свой возраст сам.
     */
    public static final int BETWEEN_BIRTHS = 6;

    private Families() {
    }

    /**
     * Суточный ход жизни: все стареют, кто-то женится, кто-то родится.
     * <p>
     * Порядок несущий. Сперва прожитые сутки — иначе рождённый сегодня
     * ребёнок сразу оказался бы на день старше. Потом свадьба, потом
     * роды: пара, сошедшаяся сегодня, ребёнка ждёт до завтра, и это
     * не стыдливость, а то же правило «одно событие за раз».
     */
    public static void newDay(ServerWorld world, Settlement settlement, Random random) {
        settlement.citizens().forEach(Citizen::liveADay);
        growUp(world, settlement);
        wed(world, settlement);
        birth(world, settlement, random);
    }

    /**
     * Выросшие берутся за дело.
     * <p>
     * Само по себе взросление ничего не требует: пора жизни выводится
     * из прожитых дней, и «стал взрослым» происходит без чьего-либо
     * участия. Но без этой врезки вчерашний ребёнок сидел бы без ремесла
     * <b>навсегда</b>: ремесло в моде раздаётся только пришедшим извне
     * и рукой игрока, а выросшему не досталось бы ни того, ни другого.
     * <p>
     * Берётся самое нужное колонии — тем же способом, каким его получает
     * пришлый. Игрок переназначит, если хочет другого: правило
     * «назначается само, но игрок может переназначить» старше этой правки.
     */
    public static void growUp(ServerWorld world, Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            if (Ages.daysOf(citizen) != Ages.grownAt() || citizen.profession().isPresent()) {
                continue;
            }
            Housing.neededProfession(settlement, citizen).ifPresent(craft -> {
                citizen.setProfession(craft);
                Life.tell(world, settlement, "villagepax.life.grown_up", citizen.fullName());
            });
        }
        Workplaces.assign(world, settlement);
    }

    /**
     * Свадьба: двое взрослых без пары становятся парой.
     * <p>
     * Условия нарочно скупые — взрослые, свободные, не родня. Ни доверия
     * между ними, ни ухаживаний: отношения между жителями дизайн-документ
     * относит к отдельной работе и честно называет дорогими в отладке.
     * Пара здесь — это то, что нужно детям, и не больше.
     */
    public static Optional<Citizen[]> wed(ServerWorld world, Settlement settlement) {
        List<Citizen> free = new ArrayList<>();
        for (Citizen citizen : settlement.citizens()) {
            if (Ages.isChild(citizen) || citizen.spouse().isPresent()) {
                continue;
            }
            free.add(citizen);
        }

        for (Citizen one : free) {
            for (Citizen other : free) {
                if (one == other || one.gender() == other.gender() || areKin(one, other)) {
                    continue;
                }
                one.marry(other.id());
                other.marry(one.id());
                Life.tell(world, settlement, "villagepax.life.wedding",
                        one.fullName(), other.fullName());
                return Optional.of(new Citizen[]{one, other});
            }
        }
        return Optional.empty();
    }

    /**
     * Роды: у пары появляется ребёнок.
     * <p>
     * Условия — те же, по которым в колонию приходит пришлый: свободная
     * кровать и место под предел населения. Это не совпадение: ребёнок
     * ест и спит ровно как взрослый, и притворяться, будто он бесплатен,
     * мод не станет. Разница одна — он не работает, и за это его кормят
     * восемь дней даром.
     */
    public static Optional<Citizen> birth(ServerWorld world, Settlement settlement,
                                          Random random) {
        if (!settlement.hasRoomForCitizen() || Housing.freeSpots(world, settlement) <= 0) {
            return Optional.empty();
        }

        Culture culture = CultureManager.get(settlement.culture());
        if (culture == null) {
            return Optional.empty();
        }

        for (Citizen mother : settlement.citizens()) {
            if (mother.gender() != Gender.FEMALE || Ages.isElder(mother)
                    || Ages.isChild(mother)) {
                continue;
            }
            Citizen father = spouseOf(settlement, mother).orElse(null);
            if (father == null || Ages.isChild(father)) {
                continue;
            }
            if (youngestChildAge(settlement, mother, father) < BETWEEN_BIRTHS) {
                continue;
            }

            Citizen child = Founding.newCitizen(settlement.culture(), culture, random,
                    settlement.citizens());
            child.setLived(0);
            child.setParents(father.id(), mother.id());
            child.setLastName(patronymic(culture, father, child));
            child.setPosition(com.villagepax.entity.CitizenSpawner.arrival(world, settlement));
            settlement.addCitizen(child);

            Housing.assignBeds(world, settlement);
            CitizenSpawner.spawnBody(world, settlement, child);

            Life.tell(world, settlement, "villagepax.life.birth",
                    child.fullName(), mother.fullName(), father.fullName());
            return Optional.of(child);
        }
        return Optional.empty();
    }

    /**
     * Сколько дней младшему ребёнку этой пары; {@link Integer#MAX_VALUE},
     * если детей нет.
     * <p>
     * Это и есть память о прошлых родах, только живая: она ходит
     * по колонии, у неё есть имя, и подделать её нельзя.
     */
    public static int youngestChildAge(Settlement settlement, Citizen mother, Citizen father) {
        int youngest = Integer.MAX_VALUE;
        for (Citizen citizen : settlement.citizens()) {
            if (citizen.isChildOf(mother.id(), father.id())) {
                youngest = Math.min(youngest, Math.max(0, Ages.daysOf(citizen)));
            }
        }
        return youngest;
    }

    /** Супруг этого жителя, если он ещё жив и живёт здесь же. */
    public static Optional<Citizen> spouseOf(Settlement settlement, Citizen citizen) {
        UUID spouse = citizen.spouse().orElse(null);
        if (spouse == null) {
            return Optional.empty();
        }
        return settlement.citizens().stream()
                .filter(other -> other.id().equals(spouse))
                .findFirst();
    }

    /** Дети этого жителя, живущие здесь. */
    public static List<Citizen> childrenOf(Settlement settlement, Citizen parent) {
        return settlement.citizens().stream()
                .filter(other -> other.parents().contains(parent.id()))
                .toList();
    }

    /**
     * Родня ли эти двое.
     * <p>
     * Проверяется и «мои родители» и «общий родитель»: брат с сестрой
     * не женятся, и сын на матери тоже. Правило скучное, но его
     * отсутствие игрок заметил бы сразу — и не так, как хотелось бы.
     */
    public static boolean areKin(Citizen one, Citizen other) {
        if (one.parents().contains(other.id()) || other.parents().contains(one.id())) {
            return true;
        }
        return one.parents().stream().anyMatch(other.parents()::contains);
    }

    /**
     * Имя по отцу.
     * <p>
     * Берётся из <b>датапака народа</b>, а не из кода: «fils de Rollo»
     * норманна и «u-mehen» майя — это примета культуры, такая же как
     * имена и цвет стен. Народ, не назвавший образца, обходится без
     * отчества вовсе — и это законно: не у всех народов оно есть.
     */
    public static String patronymic(Culture culture, Citizen father, Citizen child) {
        String pattern = child.gender() == Gender.MALE
                ? culture.namePools().sonOf()
                : culture.namePools().daughterOf();
        return pattern.isBlank() ? "" : pattern.replace("%s", father.firstName());
    }
}
