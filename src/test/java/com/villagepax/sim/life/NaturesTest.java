package com.villagepax.sim.life;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.work.Needs;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Характер: откуда берётся и что меняет.
 * <p>
 * Проверяется здесь, а не в игре, потому что весь вывод характера — чистая
 * арифметика над опознавателем, и поднимать ради неё мир значило бы
 * проверять десять жителей вместо десяти тысяч.
 */
class NaturesTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

    /**
     * Доли должны быть ровными: иначе «одна пятая», которой оправдано
     * существование ровного характера, окажется одной третью, и колония
     * наполнится ленивыми, а стражу поставить будет некому.
     */
    @Test
    void everyNatureGetsItsFifth() {
        Map<Nature, Integer> counted = new EnumMap<>(Nature.class);
        Random random = new Random(20260920L);

        int people = 50_000;
        for (int at = 0; at < people; at++) {
            Nature nature = Natures.of(new UUID(random.nextLong(), random.nextLong()));
            counted.merge(nature, 1, Integer::sum);
        }

        int fifth = people / Nature.values().length;
        for (Nature nature : Nature.values()) {
            int got = counted.getOrDefault(nature, 0);
            assertTrue(Math.abs(got - fifth) < fifth / 10,
                    "характер " + nature + " достался " + got + " жителям из " + people
                            + ", а должен примерно " + fifth);
        }
    }

    /**
     * Характер обязан быть тем же и завтра, и после обновления Java.
     * <p>
     * Это и есть вся причина, по которой смешивание написано своими
     * руками, а не взято у {@code UUID.hashCode()}. Числа здесь написаны
     * <b>от руки</b>, а не получены у {@link Natures}: проверка, которая
     * спрашивает ответ у проверяемого, согласится с любой поломкой.
     */
    @Test
    void theSameCitizenIsAlwaysTheSamePerson() {
        assertEquals(Nature.LAZY, of("1f0a9c31-2d44-4b6e-9a01-5c7e3f18b002"));
        assertEquals(Nature.AMBITIOUS, of("7b3d51a8-0e62-4c19-8f44-21d0a9e7c563"));
        assertEquals(Nature.PIOUS, of("c4e81f07-9a35-4d82-b6c3-0e5f27a94d18"));
        assertEquals(Nature.COWARD, of("2a6b94d3-77c1-4e05-9b8a-3f1d62e0c847"));
        assertEquals(Nature.EVEN, of("45c20e9f-1b76-4d33-a5e8-90c4172bf6d0"));
    }

    /** Ленивый терпит вдвое дольше, честолюбивый вдвое меньше. */
    @Test
    void patienceFollowsTheNature() {
        int leave = Needs.leaveAfterDays();
        int warn = Needs.warnAfterDays();

        assertEquals(leave, Natures.leaveAfterDays(someone(Nature.EVEN, 20)),
                "ровному сроки менять не за что");
        assertEquals(leave * 2, Natures.leaveAfterDays(someone(Nature.LAZY, 20)),
                "ленивому лень уходить");
        assertEquals(Math.max(1, leave / 2), Natures.leaveAfterDays(someone(Nature.AMBITIOUS, 20)),
                "честолюбивый не станет терпеть");

        assertEquals(warn * 2, Natures.warnAfterDays(someone(Nature.LAZY, 20)),
                "жалоба обязана идти по тому же множителю, что и уход");
        assertTrue(Natures.warnAfterDays(someone(Nature.AMBITIOUS, 20))
                        < Natures.leaveAfterDays(someone(Nature.AMBITIOUS, 20)),
                "жалоба, приходящая после ухода, не предупреждение");
    }

    /**
     * Полсилы — одно на всех, и характер входит в него же.
     * <p>
     * Заодно здесь проверяется единственное исключение, ради которого
     * честолюбие вообще что-то даёт: годы ему не помеха.
     */
    @Test
    void theSlowdownIsOneAndTheSame() {
        assertFalse(Natures.worksSlowly(someone(Nature.EVEN, 20)),
                "ровному взрослому замедляться не с чего");
        assertTrue(Natures.worksSlowly(someone(Nature.LAZY, 20)),
                "ленивый тянет вполсилы в любом возрасте");

        int old = Ages.oldAt() + 1;
        assertTrue(Natures.worksSlowly(someone(Nature.EVEN, old)),
                "старость отнимает силы у всех");
        assertFalse(Natures.worksSlowly(someone(Nature.AMBITIOUS, old)),
                "честолюбивому годы не помеха — иначе характер ничего не даёт");
        assertTrue(Natures.worksSlowly(someone(Nature.LAZY, old)),
                "ленивый старик не выздоравливает от старости");
    }

    private static Nature of(String id) {
        return Natures.of(UUID.fromString(id));
    }

    /**
     * Житель с нужным характером.
     * <p>
     * Перебором, а не подобранным руками опознавателем: подобранный
     * пришлось бы переписывать после любой правки смешивания, и проверки
     * характеров молча стали бы проверками одного и того же ровного.
     */
    private static Citizen someone(Nature nature, int lived) {
        for (long at = 0; at < 10_000; at++) {
            UUID id = new UUID(0L, at);
            if (Natures.of(id) != nature) {
                continue;
            }
            return new Citizen(id, "Rollo", "de Bayeux", NORMAN, Gender.MALE,
                    new Citizen.Life(lived, Optional.empty(), java.util.List.of()),
                    Optional.empty(), 70, 20, Optional.empty(), Optional.empty(),
                    Optional.empty(), Citizen.MAX_HEALTH);
        }
        throw new AssertionError("характер " + nature + " не достаётся никому: смешивание сломано");
    }
}
