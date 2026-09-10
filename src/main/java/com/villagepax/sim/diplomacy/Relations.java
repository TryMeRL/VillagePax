package com.villagepax.sim.diplomacy;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.diplomacy.Attitude;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Отношения: народ к народу и народ к игроку.
 * <p>
 * Дизайн-документ называет четыре уровня матрицы. Здесь живут три:
 * <ul>
 *   <li><b>народ ↔ народ</b> — из данных культуры ({@code diplomacy_defaults}),
 *       односторонне: см. {@link Attitude};</li>
 *   <li><b>игрок ↔ поселение</b> — доверие, лежит в самом поселении и было
 *       в моде с первой деревни;</li>
 *   <li><b>игрок ↔ народ</b> — <i>считается</i>, а не хранится: средневзвешенное
 *       по тем деревням народа, у которых об игроке есть что сказать.</li>
 * </ul>
 * Четвёртого, <b>поселение ↔ поселение</b>, здесь нет намеренно. Двум деревням
 * пока нечего делать со своим мнением друг о друге: воевать не умеют, союзов
 * не заключают, границы не оспаривают. Хранилище чисел, которые никто не
 * читает, — это не половина механики, а её видимость. Приедет вместе с войной.
 * <p>
 * <b>Главное здесь — эхо.</b> Дизайн-документ: «каждое действие меняет
 * несколько уровней матрицы сразу — подарок конкретной деревне слегка
 * поднимает и отношение всего народа». Одна деревня не живёт в пустоте:
 * о поступке узнают свои, и узнают чужие. Своим достаётся четверть, чужим —
 * столько, сколько велит их собственный взгляд на этот народ: кто смотрит
 * настороженно, тому услуга сопернику слегка не по нраву. Формула одна на
 * оба случая, и знак она разворачивает сама: ограбить обоз народа, на который
 * сосед косится, — это в глазах соседа не преступление.
 */
public final class Relations {

    /**
     * Какая доля поступка доходит до своих, в процентах.
     * <p>
     * Четверть — не круглое число ради круглости. Дело, поднявшее деревню
     * на ступень (двадцать очков в квесте), даёт соседям по народу пять:
     * заметно, но не отменяет знакомства с ними. А мелочь вроде очка за
     * сделку не доходит вовсе — и это правильно, о каждой купленной булке
     * соседям не рассказывают.
     */
    public static final int KIN_SHARE = 25;

    private Relations() {
    }

    /**
     * Сколько от поступка достанется третьей деревне.
     * <p>
     * Чистая функция, и это единственная арифметика отношений в моде:
     * её видно, её можно проверить без запущенной игры, и разойтись
     * ей не с чем.
     *
     * @param amount   размер поступка: со знаком, дурное дело — отрицательное
     * @param kin      та же деревня одного народа с осчастливленной
     * @param attitude как народ той деревни смотрит на народ осчастливленной
     */
    public static int share(int amount, boolean kin, int attitude) {
        int weight = kin ? KIN_SHARE : attitude;
        return amount * weight / 100;
    }

    /**
     * Как народ {@code from} смотрит на народ {@code toward}: число из данных.
     * <p>
     * На себя народ смотрит как на своих — той же четвертью, что доходит
     * до соседних деревень. Это не поблажка коду: спросить «как норманны
     * относятся к норманнам» экран вправе, и ответ «никак, в данных не
     * записано» был бы неправдой.
     */
    public static int attitude(Identifier from, Identifier toward) {
        if (from.equals(toward)) {
            return KIN_SHARE;
        }
        Culture culture = CultureManager.get(from);
        return culture == null ? 0 : culture.initialAttitudeTo(toward);
    }

    /** То же ступенью, для показа игроку. */
    public static Attitude attitudeLadder(Identifier from, Identifier toward) {
        return Attitude.of(attitude(from, toward));
    }

    /**
     * Доверие народа к игроку: средневзвешенное по его деревням.
     * <p>
     * Взвешенное по величине поселения: слово столицы весит больше слова
     * хутора. Считается только по тем деревням, которым об игроке есть что
     * сказать: незнакомая деревня не имеет мнения, и подмешивать её ноль
     * в средний счёт значило бы наказывать игрока за существование деревень,
     * которых он не видел.
     * <p>
     * Колонии игрока в счёт не идут вовсе: у своих поселений мнения о хозяине
     * нет — там его слово и есть решение.
     */
    public static int trustOfPeople(SettlementManager manager, Identifier culture, UUID player) {
        int sum = 0;
        int weights = 0;
        for (Settlement settlement : manager.all()) {
            if (!settlement.owner().isAutonomous() || !settlement.culture().equals(culture)) {
                continue;
            }
            if (!settlement.knows(player)) {
                continue;
            }
            int weight = settlement.level().ordinal() + 1;
            sum += settlement.reputationOf(player) * weight;
            weights += weight;
        }
        return weights == 0 ? 0 : sum / weights;
    }

    /** То же ступенью доверия — той же, что у отдельной деревни. */
    public static Standing standingOfPeople(SettlementManager manager, Identifier culture,
                                            UUID player) {
        return Standing.of(trustOfPeople(manager, culture, player));
    }

    /**
     * Снимок: что о игроке думает каждый народ прямо сейчас.
     * <p>
     * Берётся <b>до</b> поступка, чтобы потом было с чем сравнить. Иначе
     * узнать, что народ перешёл ступень, нельзя: доверие поднимается
     * в нескольких деревнях сразу, и разность по одной из них ничего
     * не говорит о среднем.
     */
    public static Map<Identifier, Integer> trustByPeople(SettlementManager manager, UUID player) {
        Map<Identifier, Integer> trust = new LinkedHashMap<>();
        for (Settlement settlement : manager.all()) {
            if (settlement.owner().isAutonomous()) {
                trust.computeIfAbsent(settlement.culture(),
                        culture -> trustOfPeople(manager, culture, player));
            }
        }
        return trust;
    }

    /**
     * Эхо поступка: своим — доля, чужим — по их взгляду на этот народ.
     * <p>
     * Сама осчастливленная деревня здесь не участвует: своё она уже получила.
     * Отдельным методом — затем, что доверие за квест начисляется внутри
     * {@code manager.update}, где менеджера уже не спросить о других
     * деревнях, и звать эхо приходится снаружи.
     */
    public static void echo(SettlementManager manager, Settlement village, UUID player,
                            int amount) {
        if (amount == 0) {
            return;
        }
        Identifier home = village.culture();

        // Список, а не поток по all(): менять поселения во время обхода
        // коллекции менеджера — верный способ однажды получить
        // ConcurrentModificationException на ровном месте.
        List<Settlement> others = new ArrayList<>(manager.all());
        for (Settlement other : others) {
            if (other.id().equals(village.id()) || !other.owner().isAutonomous()) {
                continue;
            }
            boolean kin = other.culture().equals(home);
            int reached = share(amount, kin, attitude(other.culture(), home));
            if (reached != 0) {
                manager.update(other.id(), state -> state.addReputation(player, reached));
            }
        }
    }

    /**
     * Поступок целиком: и деревне, и народам.
     * <p>
     * Возвращает <b>то, что стоит сказать игроку</b>: народы, у которых
     * от этого поступка сменилась ступень. Про остальных молчит: «доверие
     * норманнов выросло с семи до девяти» — это не новость, а бухгалтерия.
     */
    public static List<Shift> deed(SettlementManager manager, Settlement village, UUID player,
                                   int amount) {
        Map<Identifier, Integer> before = trustByPeople(manager, player);
        manager.update(village.id(), state -> state.addReputation(player, amount));
        echo(manager, village, player, amount);
        return since(before, manager, player);
    }

    /** Что изменилось в ступенях народов с момента снимка. */
    public static List<Shift> since(Map<Identifier, Integer> before, SettlementManager manager,
                                    UUID player) {
        List<Shift> shifts = new ArrayList<>();
        Map<Identifier, Integer> after = trustByPeople(manager, player);

        after.forEach((culture, trust) -> {
            Standing was = Standing.of(before.getOrDefault(culture, 0));
            Standing now = Standing.of(trust);
            if (was != now) {
                shifts.add(new Shift(culture, was, now));
            }
        });
        return shifts;
    }

    /**
     * Сказать игроку, что о нём заговорили.
     * <p>
     * В чат, а не в экран: экран народа он откроет, только если догадается
     * туда заглянуть, а весть о том, что чужой народ начал коситься, — это
     * следствие его собственного поступка, и узнать о нём он должен тогда же,
     * когда поступок совершил.
     */
    public static void tell(ServerPlayerEntity who, List<Shift> shifts) {
        for (Shift shift : shifts) {
            Culture culture = CultureManager.get(shift.culture());
            if (culture == null) {
                continue;
            }
            boolean better = shift.now().ordinal() > shift.was().ordinal();
            who.sendMessage(Text.translatable("villagepax.people.standing_now",
                            Text.translatable(culture.displayName()),
                            Text.translatable(shift.now().displayKey()))
                    .formatted(better ? Formatting.GREEN : Formatting.RED), false);
        }
    }

    /**
     * Смена ступени у народа: было и стало.
     *
     * @param culture народ, о котором речь
     * @param was     ступень до поступка
     * @param now     ступень после
     */
    public record Shift(Identifier culture, Standing was, Standing now) {
    }
}
