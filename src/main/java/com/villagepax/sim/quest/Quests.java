package com.villagepax.sim.quest;

import com.villagepax.core.quest.Quest;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
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

        /** Просить больше нечего — цепочка кончилась. */
        NOTHING_OFFERED
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
        Identifier current = QuestManager.firstOf(giver).orElse(null);
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
        Identifier questId = offered(village, player, giver).orElse(null);
        if (questId == null) {
            return Handover.NOTHING_OFFERED;
        }

        Quest quest = QuestManager.get(questId).orElseThrow();
        if (village.reputationOf(player) < quest.minReputation()) {
            return Handover.NO_TRUST;
        }
        if (!hasAll(carried, quest)) {
            return Handover.NOT_ENOUGH;
        }

        for (Quest.Objective objective : quest.objectives()) {
            if (objective instanceof Quest.Objective.Deliver deliver) {
                take(carried, deliver.item(), deliver.count());
            }
        }

        village.noteQuestDone(player, questId);
        for (Quest.Reward reward : quest.rewards()) {
            if (reward instanceof Quest.Reward.Trust trust) {
                village.addReputation(player, trust.amount());
            } else if (reward instanceof Quest.Reward.Give give) {
                payout.accept(new ItemStack(give.item(), give.count()));
            }
        }
        return Handover.DONE;
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
        Standing before = Standing.of(village.reputationOf(id));

        Handover[] outcome = new Handover[1];
        manager.update(village.id(), state -> outcome[0] = handIn(state, id, profession,
                player.getInventory(),
                // Не влезло в руки — падает под ноги: награду терять нельзя.
                stack -> player.getInventory().offerOrDrop(stack)));

        switch (outcome[0] == null ? Handover.NOTHING_OFFERED : outcome[0]) {
            case NOTHING_OFFERED -> say(player, giver, "villagepax.quest.nothing_left",
                    Text.translatable(before.displayKey()));

            case NO_TRUST -> say(player, giver, "villagepax.quest.not_yet",
                    Text.translatable(before.displayKey()));

            case NOT_ENOUGH -> {
                Quest quest = offered(village, id, profession)
                        .flatMap(QuestManager::get).orElse(null);
                if (quest != null) {
                    say(player, giver, quest.dialogue());
                    quest.objectives().forEach(objective -> askFor(player, objective));
                }
            }

            case DONE -> {
                say(player, giver, "villagepax.quest.done");
                player.getWorld().playSound(null, player.getBlockPos(),
                        SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);

                Standing now = Standing.of(village.reputationOf(id));
                if (now != before) {
                    player.sendMessage(Text.translatable("villagepax.quest.standing_up",
                            Text.literal(village.name()), Text.translatable(now.displayKey())), false);
                }
            }
        }
    }

    private static boolean hasAll(Inventory carried, Quest quest) {
        for (Quest.Objective objective : quest.objectives()) {
            if (objective instanceof Quest.Objective.Deliver deliver
                    && carried.count(deliver.item()) < deliver.count()) {
                return false;
            }
        }
        return true;
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

    private static void askFor(ServerPlayerEntity player, Quest.Objective objective) {
        if (objective instanceof Quest.Objective.Deliver deliver) {
            player.sendMessage(Text.translatable(objective.describeKey(),
                    Text.translatable(deliver.item().getTranslationKey()),
                    Text.literal(String.valueOf(deliver.count())),
                    Text.literal(String.valueOf(player.getInventory().count(deliver.item())))), false);
        }
    }

    /** Слова жителя. Имя впереди, чтобы было видно, кто говорит. */
    private static void say(ServerPlayerEntity player, Citizen giver, String key, Text... args) {
        player.sendMessage(Text.translatable("villagepax.quest.said",
                Text.literal(giver.fullName()), Text.translatable(key, (Object[]) args)), false);
    }
}
