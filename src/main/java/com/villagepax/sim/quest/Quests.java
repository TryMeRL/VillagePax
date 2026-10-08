package com.villagepax.sim.quest;

import com.villagepax.core.quest.Quest;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Founding;
import com.villagepax.core.faith.Gods;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.culture.Culture;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.diplomacy.Relations;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Разговор с выдающим квесты.
 * <p>
 * Вход в мод устроен через чужую деревню: игрок находит норманнов, выполняет
 * стартовую цепочку, дорастает до друга и получает от старейшины чертёж
 * ратуши. Это решение дизайна, и оно же ответ на вопрос «откуда у игрока
 * первый чертёж»: крафт остался подстраховкой на неудачный сид.
 * <p>
 * Разговор идёт <b>щелчком по жителю</b>, а не через отдельный экран, и это
 * осознанно. Экран пришлось бы синхронизировать, а разговор здесь короткий:
 * старейшина говорит, чего просит, и забирает принесённое, если оно в руках.
 * Вкладка квестов появится в пульте вместе с колонией игрока — там ей место,
 * потому что там уже есть и снимок, и сеть.
 * <p>
 * <b>Решение отделено от последствий.</b> {@link #handIn} работает с обычным
 * складом-{@link Inventory} и выдаёт награду через переданную функцию —
 * поэтому всю цепочку можно прогнать игровым тестом, где игрока нет вовсе.
 * Иначе единственный способ проверить вход в мод был бы «пройти руками»,
 * а значит, на деле — не проверять.
 */
public final class Quests {

    /** Чем кончилась попытка сдать квест. */
    public enum Handover {

        /** Принято, награда выдана. */
        DONE,

        /** Принесено не всё. */
        NOT_ENOUGH,

        /** Доверия не хватает: об этом квесте с игроком пока не говорят. */
        NO_TRUST,

        /** Просить больше нечего — ни по писаному, ни по делу. */
        NOTHING_OFFERED,

        /**
         * Без своей колонии этого не сделать.
         * <p>
         * Отдельный исход, а не «принесено не всё»: игрок, у которого нет
         * колонии, не принесёт её в сумке, и отказ обязан назвать причину.
         * Сюда же — просьбы, награда которых переселяет человека: отдать
         * его некуда.
         */
        NO_COLONY
    }

    /**
     * О чём сегодня разговор: писаный квест или суточное поручение.
     * <p>
     * Опознаватель едет рядом с самим квестом не для удобства. Поручение
     * <b>не лежит в датапаке</b>: оно выводится из дня, и найти его
     * по опознавателю в {@link QuestManager} нельзя. Значит, тот, кто
     * спросил, обязан унести с собой и то и другое — иначе сдача полезла бы
     * искать в реестре то, чего там нет.
     *
     * @param errand поручение это или писаный квест: по нему разнятся слова
     */
    public record Task(Identifier id, Quest quest, boolean errand) {
    }

    private Quests() {
    }

    /**
     * Какой квест деревня предлагает этому игроку.
     * <p>
     * Цепочка проходится по ссылкам {@code next} с начала, а выполненные
     * пропускаются. Порог доверия здесь <b>не</b> проверяется: игроку надо
     * сказать, чего от него хотят, даже если пока не доверяют, — иначе
     * старейшина молчит, и непонятно, что делать.
     */
    public static Optional<Identifier> offered(Settlement village, UUID player, Identifier giver) {
        Identifier current = QuestManager.firstOf(giver, village.culture()).orElse(null);
        List<Identifier> done = village.questsDone(player);

        // Ограничение обхода — число квестов: кольцевая ссылка в датапаке
        // не должна вешать сервер.
        for (int step = 0; current != null && step <= QuestManager.all().size(); step++) {
            Quest quest = QuestManager.get(current).orElse(null);
            if (quest == null) {
                return Optional.empty();
            }
            if (!done.contains(current)) {
                return Optional.of(current);
            }
            current = quest.next().orElse(null);
        }
        return Optional.empty();
    }

    /**
     * О чём деревня говорит с этим игроком сегодня.
     * <p>
     * Сперва писаное: у цепочки есть начало, конец и смысл, и перебивать
     * её поручением было бы всё равно что перебить рассказ просьбой
     * подержать сумку. Кончилась цепочка — начинаются поручения, и вот
     * они не кончаются уже никогда.
     * <p>
     * Это и есть лекарство от старой беды: раньше после третьего квеста
     * старейшина говорил «просить больше нечего» — навсегда, — и деревня
     * превращалась в лавку.
     */
    public static Optional<Task> task(Settlement village, UUID player, Identifier giver,
                                      long today) {
        Identifier written = offered(village, player, giver).orElse(null);
        if (written != null) {
            return QuestManager.get(written).map(quest -> new Task(written, quest, false));
        }

        Identifier errand = Errands.idFor(giver, today);
        if (village.questsDone(player).contains(errand)) {
            // Сегодня уже носил. Одна просьба в день на ремесло — и это
            // единственный предел, который поручениям нужен.
            return Optional.empty();
        }
        return Errands.forToday(village, giver, today, village.reputationOf(player))
                .map(quest -> new Task(errand, quest, true));
    }

    /**
     * Сдать то, что предложено, — если принесено.
     * <p>
     * Изъятие «всё или ничего»: сперва считаем, потом забираем. Частичное
     * было бы хуже отказа — у игрока отобрали бы половину даром.
     *
     * @param carried где искать принесённое
     * @param payout  куда отдать награду
     */
    public static Handover handIn(Settlement village, UUID player, Identifier giver,
                                  Inventory carried, Consumer<ItemStack> payout) {
        return handIn(null, village, null, player, giver, carried, 0, payout);
    }

    /**
     * Сдать то, что предложено, — если сделано.
     * <p>
     * «Если принесено» стало «если сделано»: цели теперь смотрят не только
     * в сумку, но и на колонию игрока, на его богов и на его знакомства.
     * Всё, что для этого нужно, приходит сюда <b>значениями</b>, а не
     * добывается изнутри: тогда всю цепочку можно прогнать игровым тестом,
     * где игрока нет вовсе.
     * <p>
     * Изъятие «всё или ничего»: сперва считаем, потом забираем. Частичное
     * было бы хуже отказа — у игрока отобрали бы половину даром.
     *
     * @param manager все поселения: нужен целям про другие народы
     * @param colony  колония игрока, если она есть
     * @param carried где искать принесённое
     * @param today   какой сегодня день: по нему выводится поручение
     * @param payout  куда отдать награду
     */
    public static Handover handIn(SettlementManager manager, Settlement village,
                                  Settlement colony, UUID player, Identifier giver,
                                  Inventory carried, long today, Consumer<ItemStack> payout) {
        Task task = task(village, player, giver, today).orElse(null);
        if (task == null) {
            return Handover.NOTHING_OFFERED;
        }

        Quest quest = task.quest();
        if (village.reputationOf(player) < quest.minReputation()) {
            return Handover.NO_TRUST;
        }
        if (colony == null && Progress.needsAColony(quest)) {
            return Handover.NO_COLONY;
        }

        Progress.Seeker seeker = Progress.Seeker.of(player, carried, colony, manager);
        for (Quest.Objective objective : quest.objectives()) {
            if (!Progress.of(objective, seeker).enough()) {
                return Handover.NOT_ENOUGH;
            }
        }

        // Забирается только принесённое руками. Построенное здание,
        // намоленная благосклонность и заведённое знакомство остаются
        // при игроке: их не отдают, ими подтверждают.
        for (Quest.Objective objective : quest.objectives()) {
            if (objective instanceof Quest.Objective.Deliver deliver) {
                take(carried, deliver.item(), deliver.count());
            }
        }

        village.noteQuestDone(player, task.id());
        // Сорванный с доски листок этой просьбы больше не нужен.
        com.villagepax.item.QuestNoteItem.tearUp(carried, village.id(), task.id());
        for (Quest.Reward reward : quest.rewards()) {
            if (reward instanceof Quest.Reward.Trust trust) {
                village.addReputation(player, trust.amount());
            } else if (reward instanceof Quest.Reward.Give give) {
                payout.accept(new ItemStack(give.item(), give.count()));
            } else if (reward instanceof Quest.Reward.Settler settler && colony != null) {
                settle(village, colony, settler);
            } else if (reward instanceof Quest.Reward.Grace grace && colony != null) {
                Gods.inDomain(colony.culture(), grace.domain())
                        .ifPresent(god -> colony.addFavour(god, grace.amount()));
            }
        }
        return Handover.DONE;
    }

    /**
     * Человек из деревни переселяется в колонию игрока.
     * <p>
     * Имя и пол берутся у <b>отпустившего</b> народа, а не у принявшего:
     * пришлый норманн в майяской колонии так и останется норманном,
     * и это видно в списке жителей до конца игры. Ради этого награда
     * и заведена — она делает деревню соседом, а не лавкой.
     * <p>
     * Случайность здесь безобидна: награда выдаётся один раз, и повторить
     * бросок некому. Но зерно всё равно взято от поселения и числа
     * жителей, а не от часов, — чтобы сдача квеста в тесте была
     * повторяемой.
     */
    private static void settle(Settlement village, Settlement colony,
                               Quest.Reward.Settler settler) {
        Culture culture = CultureManager.get(village.culture());
        if (culture == null) {
            return;
        }
        java.util.Random random = new java.util.Random(village.id().getLeastSignificantBits()
                ^ (colony.population() * 1_000_003L));

        Citizen newcomer = Founding.newCitizen(village.culture(), culture, random, colony.citizens());
        // Пришёл взрослым — и стареет со всеми, как пришлый из притока.
        // Без дня рождения он остался бы «без возраста», то есть бессмертным:
        // единственный такой в колонии, где все остальные однажды умирают.
        com.villagepax.sim.life.Ages.arrivedGrown(newcomer);
        settler.profession().ifPresent(newcomer::setProfession);
        colony.addCitizen(newcomer);
    }

    /**
     * Поздороваться: выдающий называет, чего хочет, но ничего не забирает.
     * <p>
     * Отделено от сдачи затем, что щелчок теперь <b>открывает экран</b>,
     * а не отдаёт вещи. Отдать вещи одним щелчком по жителю было бы
     * недобрым: игрок щёлкнул посмотреть, а у него забрали тридцать
     * два хлеба.
     */
    public static void greet(ServerPlayerEntity player, Citizen giver) {
        Identifier profession = giver.profession().orElse(null);
        if (profession == null) {
            return;
        }
        player.getWorld().playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 1.0f, 1.0f);
    }

    /**
     * Заговорить с выдающим.
     * <p>
     * Одно нажатие делает всё, что можно сделать: если принесённое при
     * игроке — забирает и награждает, иначе называет, чего не хватает.
     * Так игрок не гадает, какую кнопку нажать следующей.
     */
    public static void talk(SettlementManager manager, Settlement village,
                            ServerPlayerEntity player, Citizen giver) {
        Identifier profession = giver.profession().orElse(null);
        if (profession == null) {
            return;
        }

        UUID id = player.getUuid();
        long today = Schedule.dayOf(player.getServerWorld().getTimeOfDay());
        Settlement colony = Founding.colonyOf(manager, id).orElse(null);
        int trustBefore = village.reputationOf(id);
        Standing before = Standing.of(trustBefore);
        // Снимок народов — до сдачи: доверие поднимется в нескольких
        // деревнях сразу, и разность по одной из них о среднем не скажет.
        Map<Identifier, Integer> peopleBefore = Relations.trustByPeople(manager, id);

        Handover[] outcome = new Handover[1];
        manager.update(village.id(), state -> outcome[0] = handIn(manager, state, colony, id,
                profession, player.getInventory(), today,
                // Не влезло в руки — падает под ноги: награду терять нельзя.
                stack -> player.getInventory().offerOrDrop(stack)));

        switch (outcome[0] == null ? Handover.NOTHING_OFFERED : outcome[0]) {
            case NOTHING_OFFERED -> say(player, giver, "villagepax.quest.nothing_left",
                    Text.translatable(before.displayKey()));

            case NO_TRUST -> say(player, giver, "villagepax.quest.not_yet",
                    Text.translatable(before.displayKey()));

            // Без своей колонии просьбу не выполнить, и молчать об этом
            // нельзя: игрок принёс бы всё требуемое и не понял, почему
            // у него не берут.
            case NO_COLONY -> say(player, giver, "villagepax.quest.needs_colony");

            case NOT_ENOUGH -> {
                Task task = task(village, id, profession, today).orElse(null);
                if (task != null) {
                    say(player, giver, task.quest().dialogue());
                    Progress.Seeker seeker = Progress.Seeker.of(id, player.getInventory(),
                            colony, manager);
                    task.quest().objectives().forEach(objective ->
                            askFor(player, objective, seeker));
                }
            }

            case DONE -> {
                say(player, giver, "villagepax.quest.done");
                player.getWorld().playSound(null, player.getBlockPos(),
                        SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);

                // Эхо — здесь, а не в награде: награду выдаёт handIn изнутри
                // manager.update, где менеджера о других деревнях уже
                // не спросить. Размер поступка берётся разностью, а не
                // из данных квеста: так эхо не разойдётся с наградой,
                // сколько бы наград «доверием» квест ни объявил.
                int awarded = village.reputationOf(id) - trustBefore;
                Relations.echo(manager, village, id, awarded);

                Standing now = Standing.of(village.reputationOf(id));
                if (now != before) {
                    player.sendMessage(Text.translatable("villagepax.quest.standing_up",
                            Text.literal(village.name()), Text.translatable(now.displayKey())), false);
                }

                // О своём народе игрок только что услышал от самой деревни:
                // пока других знакомых деревень нет, её слово и есть слово
                // народа, и повторять то же вторую строку незачем.
                List<Relations.Shift> shifts = Relations.since(peopleBefore, manager, id);
                Relations.tell(player, now != before
                        ? shifts.stream()
                                .filter(shift -> !shift.culture().equals(village.culture()))
                                .toList()
                        : shifts);
            }
        }
    }

    private static void take(Inventory carried, Item item, int count) {
        int left = count;
        for (int slot = 0; slot < carried.size() && left > 0; slot++) {
            ItemStack stack = carried.getStack(slot);
            if (stack.isOf(item)) {
                left -= carried.removeStack(slot, Math.min(left, stack.getCount())).getCount();
            }
        }
        carried.markDirty();
    }

    /**
     * Назвать одно требование словами.
     * <p>
     * Через то же правило, каким считает и сдача, и экран. Раньше здесь
     * жил свой подсчёт «сколько в сумке», и пока цель была одна, это
     * сходилось; теперь целей четыре, и три из них в сумку не смотрят.
     */
    private static void askFor(ServerPlayerEntity player, Quest.Objective objective,
                               Progress.Seeker seeker) {
        Progress.Step step = Progress.of(objective, seeker);
        Text about = step.item()
                .map(item -> (Text) Text.translatable(item.getTranslationKey()))
                .orElseGet(() -> Text.translatable(step.what().orElse("")));

        player.sendMessage(Text.translatable(step.key(), about,
                Text.literal(String.valueOf(step.have())),
                Text.literal(String.valueOf(step.need()))), false);
    }

    /** Слова жителя. Имя впереди, чтобы было видно, кто говорит. */
    private static void say(ServerPlayerEntity player, Citizen giver, String key, Text... args) {
        player.sendMessage(Text.translatable("villagepax.quest.said",
                Text.literal(giver.fullName()), Text.translatable(key, (Object[]) args)), false);
    }
}
