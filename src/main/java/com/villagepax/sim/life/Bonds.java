package com.villagepax.sim.life;

import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Кто кому кто: родня, друзья и те, кто не ладит.
 * <p>
 * Последний пункт фазы 4 и единственный, который дизайн-документ сам
 * пометил дорогим: «дружба и вражда между жителями со влиянием на работу —
 * фаза 4, <b>дорого в отладке</b>». Дорога здесь ровно одна вещь —
 * двусторонний граф, который умеет разъехаться концами; всё остальное
 * дешево. Поэтому граф сведён к минимуму, а на то, что осталось,
 * написаны свойства.
 *
 * <h2>Дружба помнится, вражда выводится</h2>
 * <b>Дружба</b> лежит в записи жителя и переживает перевод в другую
 * мастерскую: иначе это не дружба, а соседство по верстаку.
 * <p>
 * <b>Вражда</b> не хранится вовсе. Ссорятся те, чьи характеры не сходятся,
 * и только пока они делят дело. У выведенного не бывает рассинхрона,
 * его не надо чинить при смерти и переносе, — а главное, игрок может
 * <b>развести поссорившихся руками</b>, переназначив одному ремесло.
 * Вражда, которую нельзя прекратить, была бы не механикой, а погодой.
 *
 * <h2>Что это меняет</h2>
 * <ul>
 *   <li><b>Родня рядом греет.</b> Живой супруг, родитель или ребёнок
 *       в той же колонии прибавляет довольства. Это обещание
 *       дизайн-документа с первого дня: «счастье складывается из… еды,
 *       дома, безопасности, досуга, <b>отношений в семье</b>, налогов».
 *   <li><b>Враг рядом холодит</b> — каждый день, пока делят дело.
 *   <li><b>Смерть близкого — горе.</b> Разовый удар по довольству каждому,
 *       кто помнил ушедшего.
 * </ul>
 * <b>Сама дружба довольства не прибавляет</b>, и это решение. Прибавка
 * досталась бы почти всем — люди работают рядом, — то есть стала бы
 * прибавкой всем поровну, а такая не значит ничего. Дружба здесь не
 * награда: она то, что можно <b>потерять</b>.
 */
public final class Bonds {

    /** Сколько довольства даёт один близкий рядом. */
    public static final int WARMTH = 1;

    /** И сколько отнимает один недруг. */
    public static final int CHILL = 1;

    /**
     * Больше этого родня не греет, а недруги не студят.
     * <p>
     * Предел по той же причине, по какой он есть у уюта и у покоя
     * от богов: иначе семья из восьми человек решала бы довольство
     * целиком, и всё остальное — еда, дом, работа — перестало бы
     * значить что-либо.
     */
    public static final int MOST = 2;

    /**
     * Во что обходится смерть близкого.
     * <p>
     * Восемь — меньше невыплаченного жалования (двенадцать) и больше
     * дневной прибавки за сытость (пять): горе перевешивает хороший день,
     * но не ломает колонию. Один день скорби, а не неделя.
     */
    public static final int GRIEF = 8;

    private Bonds() {
    }

    /**
     * Суточное знакомство: кто сегодня работал бок о бок, тот и сошёлся.
     * <p>
     * Дело или кров — два места, где люди видят друг друга каждый день.
     * Третьего в моде нет: площадь вечером собирает всех со всеми,
     * и дружба со всеми — это дружба ни с кем.
     */
    public static void newDay(Settlement colony) {
        List<Citizen> grown = new ArrayList<>();
        for (Citizen citizen : colony.citizens()) {
            if (!Ages.isChild(citizen)) {
                grown.add(citizen);
            }
        }

        for (int one = 0; one < grown.size(); one++) {
            for (int other = one + 1; other < grown.size(); other++) {
                Citizen first = grown.get(one);
                Citizen second = grown.get(other);
                if (!sideBySide(colony, first, second) || atOdds(first, second)) {
                    continue;
                }
                befriend(first, second);
            }
        }
    }

    /**
     * Свести двоих — <b>с обоих концов сразу</b>.
     * <p>
     * Единственное место во всём моде, где дружба записывается, и это
     * нарочно: граф, который пишут из двух мест, однажды разъедется,
     * и найти это можно будет только по тому, что один горюет,
     * а другой нет.
     *
     * @return {@code true}, если знакомство и вправду состоялось
     */
    public static boolean befriend(Citizen one, Citizen other) {
        if (one == null || other == null || one.id().equals(other.id())) {
            return false;
        }
        // Обе памяти должны принять: если у одного полно, второй не
        // заводит односторонней записи. Иначе симметрия ломается ровно
        // на пятом друге — то есть в колонии, дожившей до интересного.
        if (one.friends().contains(other.id()) || other.friends().contains(one.id())) {
            return false;
        }
        if (one.friends().size() >= Citizen.Life.MOST_FRIENDS
                || other.friends().size() >= Citizen.Life.MOST_FRIENDS) {
            return false;
        }
        return one.befriend(other.id()) && other.befriend(one.id());
    }

    /**
     * Житель покидает колонию — <b>одна дверь на все способы</b>.
     * <p>
     * Написана по следам разбора, который нашёл настоящую беду, и не
     * в новом коде. Уйти из колонии можно тремя путями: умереть
     * от старости, погибнуть от чужой руки и уйти своими ногами, —
     * и каждый прибирал за собой <b>по-своему</b>. Вдовство снималось
     * ровно в одном из трёх, в смерти от старости; в двух других
     * оставшийся супруг навсегда сохранял запись о браке.
     * <p>
     * Видно это не было никак. Вдова с мёртвым мужем в записи выпадает
     * из сватовства ({@code Families.wed} пропускает всех, у кого супруг
     * записан), и колония <b>тихо перестаёт расти</b>. Пара набегов —
     * и рождения в поселении кончаются совсем, а причину в игре искать
     * негде: видно только, что детей больше нет.
     * <p>
     * Поэтому дверь одна, и следующий способ покинуть колонию —
     * пленение, переезд, выдача сюзерену — пройдёт через неё же.
     */
    public static void parted(Settlement colony, Citizen gone) {
        Families.spouseOf(colony, gone).ifPresent(Citizen::widow);
        for (Citizen citizen : colony.citizens()) {
            citizen.forget(gone.id());
        }
    }

    /**
     * То же, но когда известен только опознаватель.
     * <p>
     * Оставлено ради тех, кто зовёт это из мест, где записи жителя уже
     * нет: память стирается, а вдовство снять не по чему.
     */
    public static void forgotten(Settlement colony, UUID gone) {
        for (Citizen citizen : colony.citizens()) {
            citizen.forget(gone);
        }
    }

    /**
     * Делят ли эти двое дело или кров.
     * <p>
     * Мастерская сравнивается по зданию, а не по ремеслу: двое лесорубов
     * из разных делянок друг друга не видят, а лесоруб с курьером,
     * заглядывающим в ту же хижину, — видят.
     */
    public static boolean sideBySide(Settlement colony, Citizen one, Citizen other) {
        if (one.id().equals(other.id())) {
            return false;
        }
        // Опознавателями, а не поиском зданий. Поиск здания — обход
        // всего списка построек, и в снимке пульта он оказывался внутри
        // двойного цикла по жителям: в столице это десятки тысяч обходов
        // каждые несколько тиков, в главном потоке сервера. Сравнение
        // отвечает на тот же вопрос — «одно ли у них место» — и стоит
        // одного сравнения.
        if (one.workplace().isPresent() && one.workplace().equals(other.workplace())) {
            return true;
        }
        return one.home().isPresent() && one.home().equals(other.home());
    }

    /**
     * Не сходятся ли характерами.
     * <p>
     * Одна пара на весь мод: <b>ленивый и честолюбивый</b>. Список из шести
     * пар читался бы таблицей, которую надо держать в голове; одна пара
     * запоминается с первого раза и объясняет сама себя — один тянет,
     * другой погоняет.
     * <p>
     * Чистая функция от двоих и потому симметрична по построению:
     * ссориться «в одну сторону» тут нечем.
     */
    public static boolean atOdds(Citizen one, Citizen other) {
        if (one.id().equals(other.id())) {
            return false;
        }
        // Дети не ссорятся — ровно потому же, почему и не сходятся:
        // круга у них ещё нет. Без этой строки правило соблюдалось бы
        // наполовину, и ленивый ребёнок студил бы честолюбивого отца,
        // а развести их игрок мог бы только выселив ребёнка.
        if (Ages.isChild(one) || Ages.isChild(other)) {
            return false;
        }
        Nature mine = Natures.of(one);
        Nature theirs = Natures.of(other);
        return (mine == Nature.LAZY && theirs == Nature.AMBITIOUS)
                || (mine == Nature.AMBITIOUS && theirs == Nature.LAZY);
    }

    /**
     * Сколько довольства этому жителю прибавляет родня и отнимают недруги.
     * <p>
     * Одним числом, а не двумя: считается оно в тех же суточных нуждах,
     * где складываются сытость, уют, покой и налог, и разводить его
     * на две строки значило бы показывать игроку бухгалтерию вместо жизни.
     */
    public static int moodOf(Settlement colony, Citizen citizen) {
        return Math.min(MOST, kinNear(colony, citizen)) * WARMTH
                - Math.min(MOST, foesNear(colony, citizen)) * CHILL;
    }

    /** Сколько близких живёт рядом: супруг, родители, дети. */
    public static int kinNear(Settlement colony, Citizen citizen) {
        int near = 0;
        for (Citizen other : colony.citizens()) {
            if (other.id().equals(citizen.id())) {
                continue;
            }
            if (isKin(citizen, other)) {
                near++;
            }
        }
        return near;
    }

    /** Родня в обе стороны: муж/жена, отец/мать, сын/дочь. */
    public static boolean isKin(Citizen one, Citizen other) {
        return one.spouse().filter(other.id()::equals).isPresent()
                || other.spouse().filter(one.id()::equals).isPresent()
                || one.parents().contains(other.id())
                || other.parents().contains(one.id());
    }

    /** Со сколькими недругами он делит дело. */
    public static int foesNear(Settlement colony, Citizen citizen) {
        int near = 0;
        for (Citizen other : colony.citizens()) {
            if (atOdds(citizen, other) && sideBySide(colony, citizen, other)) {
                near++;
            }
        }
        return near;
    }

    /**
     * Кто-то ушёл: близкие горюют, а память о нём стирается.
     * <p>
     * Горюют друзья и родня — те и другие разом, потому что для довольства
     * разницы нет, а для игрока она в сообщении: он и так знает, кто кому
     * кем приходился.
     *
     * @return сколько человек горюет
     */
    public static int mourn(ServerWorld world, Settlement colony, Citizen gone) {
        List<Citizen> grieving = new ArrayList<>();
        for (Citizen other : colony.citizens()) {
            if (other.id().equals(gone.id())) {
                continue;
            }
            if (other.friends().contains(gone.id()) || isKin(gone, other)) {
                grieving.add(other);
            }
        }

        for (Citizen who : grieving) {
            who.setHappiness(who.happiness() - GRIEF);
        }
        // Горе считается до расставания: после него ни супруга, ни друзей
        // у ушедшего уже не числится, и горевать было бы некому.
        parted(colony, gone);

        if (!grieving.isEmpty()) {
            tell(world, colony, Text.translatable("villagepax.bonds.grief",
                    Text.literal(gone.fullName()),
                    Text.literal(String.valueOf(grieving.size()))).formatted(Formatting.GRAY));
        }
        return grieving.size();
    }

    /** Имена близких одной строкой — для пульта. */
    public static String namesOfFriends(Settlement colony, Citizen citizen) {
        List<String> names = new ArrayList<>();
        for (UUID id : citizen.friends()) {
            colony.citizen(id).ifPresent(friend -> names.add(friend.firstName()));
        }
        return String.join(", ", names);
    }

    /** И тех, с кем не ладит. */
    public static String namesOfFoes(Settlement colony, Citizen citizen) {
        List<String> names = new ArrayList<>();
        for (Citizen other : colony.citizens()) {
            if (atOdds(citizen, other) && sideBySide(colony, citizen, other)) {
                names.add(other.firstName());
            }
        }
        return String.join(", ", names);
    }

    private static void tell(ServerWorld world, Settlement colony, Text line) {
        UUID player = colony.owner().player().orElse(null);
        if (player == null) {
            return;
        }
        ServerPlayerEntity who = world.getServer().getPlayerManager().getPlayer(player);
        if (who != null) {
            who.sendMessage(line, false);
        }
    }
}
