package com.villagepax.sim.life;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Граф знакомств как свойство, а не как пример.
 * <p>
 * Дизайн-документ пометил отношения между жителями «дорого в отладке»,
 * и дорога в них ровно одна вещь: <b>двусторонний граф умеет разъехаться
 * концами</b>. Односторонняя дружба не роняет ничего и ничего не печатает
 * в лог — она проявляется тем, что один горюет, а другой нет, через
 * двадцать игровых дней, у игрока. Примером такое не ловится: пример
 * проверяет ту пару, которую я догадался написать.
 * <p>
 * Поэтому здесь перебор. Каждое свойство — утверждение обо <b>всех</b>
 * колониях, какие мод может собрать: с пустой памятью и полной, с одним
 * жителем и с тремя десятками, со всеми сочетаниями характеров.
 */
class BondsProperty {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    /**
     * Дружба симметрична.
     * <p>
     * Главное свойство всей затеи: если A помнит B, то B помнит A.
     * Ради него дружба и записывается <b>одним</b> местом в моде.
     */
    @Property(tries = 300)
    void friendshipIsMutual(@ForAll("crowds") Settlement colony) {
        liveADay(colony);
        List<Citizen> crowd = colony.citizens();

        for (Citizen one : crowd) {
            for (UUID friend : one.friends()) {
                Citizen other = find(crowd, friend);
                assertTrue(other != null && other.friends().contains(one.id()),
                        one.firstName() + " помнит друга, который о нём не помнит: "
                                + friend);
            }
        }
    }

    /**
     * Никто не друг сам себе, и никто не записан дважды.
     * <p>
     * Знакомство с самим собой спрашивается <b>прямо</b>, а не через
     * суточный ход: тот перебирает пары и одного и того же жителя
     * дважды не подаёт никогда. Свойство, которое не может упасть, —
     * украшение; заслон же стоит на пути у любого будущего звавшего,
     * и проверять надо его.
     */
    @Property(tries = 300)
    void nobodyBefriendsHimself(@ForAll("crowds") Settlement colony) {
        liveADay(colony);
        List<Citizen> crowd = colony.citizens();

        for (Citizen alone : crowd) {
            assertFalse(Bonds.befriend(alone, alone),
                    alone.firstName() + " подружился сам с собой");
            assertFalse(alone.friends().contains(alone.id()),
                    alone.firstName() + " записал себя себе в друзья");
        }

        for (Citizen one : crowd) {
            assertFalse(one.friends().contains(one.id()),
                    one.firstName() + " подружился сам с собой");
            assertEquals(one.friends().size(), new HashSet<>(one.friends()).size(),
                    "друг записан дважды: " + one.friends());
        }
    }

    /**
     * Память не переполняется.
     * <p>
     * Предел — не аккуратность, а цена горя: смерть друга стоит довольства
     * каждому, кто его помнил. Колония, где все дружат со всеми, хоронила
     * бы каждого разом всем поселением.
     */
    @Property(tries = 300)
    void memoryStaysSmall(@ForAll("crowds") Settlement colony) {
        liveADay(colony);
        List<Citizen> crowd = colony.citizens();

        for (Citizen one : crowd) {
            assertTrue(one.friends().size() <= Citizen.Life.MOST_FRIENDS,
                    one.firstName() + " помнит " + one.friends().size() + " друзей");
        }
    }

    /**
     * Знакомство идемпотентно: свести дважды — то же, что свести однажды.
     * <p>
     * Суточный ход зовётся каждое утро и сводит одних и тех же людей
     * снова и снова. Без этого свойства память переполнялась бы
     * повторами за неделю.
     */
    @Property(tries = 200)
    void meetingTwiceChangesNothing(@ForAll("crowds") Settlement colony) {
        liveADay(colony);
        List<List<UUID>> once = snapshot(colony.citizens());

        liveADay(colony);
        liveADay(colony);

        assertEquals(once, snapshot(colony.citizens()),
                "повторное знакомство поменяло память");
    }

    /**
     * Ушедшего забывают все и сразу.
     * <p>
     * Оставленная запись означала бы, что колония горюет по тому, кого
     * в ней нет, — и горевала бы вечно, при каждом подсчёте довольства.
     */
    @Property(tries = 200)
    void theGoneAreForgottenByEveryone(@ForAll("crowds") Settlement colony) {
        liveADay(colony);
        List<Citizen> crowd = colony.citizens();
        if (crowd.isEmpty()) {
            return;
        }

        // Стирает не проверка, а тот же код, что и в игре.
        Citizen gone = crowd.get(0);
        Bonds.forgotten(colony, gone.id());

        for (Citizen other : crowd) {
            assertFalse(other.friends().contains(gone.id()),
                    other.firstName() + " всё ещё помнит ушедшего");
        }
    }

    /**
     * Вражда симметрична и без петель — по построению.
     * <p>
     * Она не хранится, а выводится из характеров, и потому разъехаться
     * концами не может в принципе. Свойство это и утверждает: перебором
     * по всем парам характеров, которые мод умеет выдать.
     */
    @Property(tries = 400)
    void enmityIsMutualAndNeverSelfInflicted(@ForAll("crowds") Settlement colony) {
        List<Citizen> crowd = colony.citizens();
        for (Citizen one : crowd) {
            assertFalse(Bonds.atOdds(one, one), one.firstName() + " не ладит сам с собой");
            for (Citizen other : crowd) {
                assertEquals(Bonds.atOdds(one, other), Bonds.atOdds(other, one),
                        "ссора в одну сторону: " + one.firstName() + " и " + other.firstName());
            }
        }
    }

    /**
     * Поссорившиеся не дружат.
     * <p>
     * Дружба и вражда — взаимоисключающие, и держится это одним местом:
     * суточный ход не сводит тех, чьи характеры не сходятся. Свойство
     * стережёт именно это правило, а не запись.
     */
    @Property(tries = 300)
    void thoseAtOddsNeverBecomeFriends(@ForAll("crowds") Settlement colony) {
        liveADay(colony);
        List<Citizen> crowd = colony.citizens();

        for (Citizen one : crowd) {
            for (UUID friend : one.friends()) {
                Citizen other = find(crowd, friend);
                if (other == null) {
                    continue;
                }
                assertFalse(Bonds.atOdds(one, other),
                        one.firstName() + " сдружился с тем, с кем не ладит");
            }
        }
    }

    /**
     * Родня определяется в обе стороны.
     * <p>
     * Супруг записан у обоих, а родитель — только у ребёнка: «чей ты сын»
     * знает сын, а не отец. Поэтому вопрос «родня ли» обязан смотреть
     * в обе записи, иначе отец не грел бы сына, а сын отца — грел.
     */
    @Property(tries = 300)
    void kinshipReadsBothWays(@ForAll("crowds") Settlement colony) {
        List<Citizen> crowd = colony.citizens();
        for (Citizen one : crowd) {
            for (Citizen other : crowd) {
                assertEquals(Bonds.isKin(one, other), Bonds.isKin(other, one),
                        "родство в одну сторону: " + one.firstName() + " и "
                                + other.firstName());
            }
        }
    }

    // --- как сводят ---

    /**
     * Прожить колонии день — <b>настоящим</b> суточным ходом.
     * <p>
     * Первая написанная здесь версия сводила людей сама, своим циклом,
     * и повторяла в нём заслон «не сводить поссорившихся». Свойство
     * «поссорившиеся не дружат» на такой версии проходило при любой
     * поломке: оно проверяло заслон самого теста. Теперь зовётся тот же
     * код, что и в игре, — и проверять есть что.
     * <p>
     * Мир для этого не нужен: знакомство смотрит на здание и на дом,
     * а не на блоки. Все живут в одном доме нарочно — это худший случай
     * для памяти, «все видятся со всеми».
     */
    private static void liveADay(Settlement colony) {
        Bonds.newDay(colony);
    }

    private static List<List<UUID>> snapshot(List<Citizen> crowd) {
        List<List<UUID>> memory = new ArrayList<>();
        for (Citizen one : crowd) {
            memory.add(List.copyOf(one.friends()));
        }
        return memory;
    }

    private static Citizen find(List<Citizen> crowd, UUID id) {
        for (Citizen one : crowd) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    @Provide
    Arbitrary<Settlement> crowds() {
        return Arbitraries.integers().between(0, 30).map(BondsProperty::people);
    }

    /**
     * Колония из стольких взрослых — <b>с роднёй</b>.
     * <p>
     * Опознаватели идут подряд, а не случайно, и это важно: характер
     * выводится из опознавателя, и подряд идущие дают <b>все пять</b>
     * характеров поровну. Случайные дали бы то же в среднем, но перебор
     * из трёх человек мог бы не дать ни одного ленивого — то есть
     * половина свойств не проверила бы ничего.
     * <p>
     * <b>Родня раздаётся здесь же, и это не украшение.</b> Первая написанная
     * толпа была из одиночек, и свойство «родство читается в обе стороны»
     * на ней проходило при любой поломке: родни не было вовсе, и проверять
     * было нечего. Пустое свойство хуже отсутствующего — оно зелёное.
     * <p>
     * Родство нарочно <b>несимметрично в записи</b>: супруги помнят друг
     * друга, а ребёнок помнит родителей, которые о нём не помнят. Так
     * устроены настоящие данные мода, и именно в этом месте вопрос
     * «родня ли» обязан смотреть в обе записи.
     */
    private static Settlement people(int many) {
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Проверка",
                new BlockPos(0, 64, 0));
        UUID roof = UUID.randomUUID();
        List<Citizen> crowd = new ArrayList<>();
        for (long at = 0; at < many; at++) {
            Citizen one = new Citizen(new UUID(0L, at + 1), "Ж" + at, "", NORMAN,
                    at % 2 == 0 ? Gender.MALE : Gender.FEMALE,
                    new Citizen.Life(20, java.util.Optional.empty(), List.of()),
                    java.util.Optional.empty(), 70, 20, java.util.Optional.empty(),
                    java.util.Optional.empty(), java.util.Optional.empty(),
                    Citizen.MAX_HEALTH);
            // Один кров на всех: знакомятся те, кто делит дело или дом,
            // и общий дом даёт худший случай — каждый видится с каждым.
            one.setHome(roof);
            crowd.add(one);
            colony.addCitizen(one);
        }

        if (crowd.size() >= 2) {
            // Пара: помнят друг друга оба.
            crowd.get(0).marry(crowd.get(1).id());
            crowd.get(1).marry(crowd.get(0).id());
        }
        for (int at = 2; at < crowd.size(); at += 3) {
            // Дети: помнят родителей, а родители о них — нет.
            crowd.get(at).setParents(crowd.get(0).id(), crowd.get(1).id());
        }
        return colony;
    }
}
