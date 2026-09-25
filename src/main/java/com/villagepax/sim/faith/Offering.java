package com.villagepax.sim.faith;

import com.villagepax.core.faith.God;
import com.villagepax.core.faith.Gods;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.work.Schedule;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Жертва на алтаре: что бог берёт, сколько за это даёт и почему отказывает.
 * <p>
 * Вердикт отделён от действия нарочно, и это в моде уже правило: судящая
 * функция чистая и проверяется без мира, а действие только исполняет её
 * решение. Так же устроены союз и дань, и по той же причине — «отказ
 * обязан говорить причину» проверить можно лишь тогда, когда причина
 * есть отдельным значением, а не веткой внутри обработчика.
 */
public final class Offering {

    /**
     * Чем кончилась попытка положить вещь на алтарь.
     * <p>
     * Отказов больше, чем согласий, и это нормально: каждый из них —
     * ответ на вопрос игрока «почему не получилось». Молчание вместо
     * любого из них превратило бы алтарь в сломанный блок.
     */
    public enum Verdict {

        /** Принято. */
        TAKEN("taken"),

        /** У этого народа никому не молятся: пантеона нет в данных. */
        NO_PANTHEON("no_pantheon"),

        /** Такого никто из здешних богов не берёт. */
        NOT_THEIRS("not_theirs"),

        /** Этому богу сегодня уже клали. */
        ALREADY_TODAY("already_today"),

        /** Алтарь чужой деревни: со своим небом каждый говорит сам. */
        NOT_YOURS("not_yours"),

        /** Руки пусты: это не отказ, а вопрос. */
        EMPTY_HANDED("empty_handed");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        /** Ключ строки, которой объясняются игроку. */
        public String key() {
            return "villagepax.faith.offering." + id;
        }

        public boolean isTaken() {
            return this == TAKEN;
        }
    }

    /** Решение: кому пойдёт жертва, сколько даст и почему нет. */
    public record Judgement(Verdict verdict, Optional<Identifier> god, int favour) {

        static Judgement no(Verdict verdict) {
            return new Judgement(verdict, Optional.empty(), 0);
        }
    }

    private Offering() {
    }

    /**
     * Возьмёт ли бог эту вещь сегодня.
     * <p>
     * Чистая функция: мира не спрашивает, в мир не пишет. Поэтому её
     * можно проверить модульным тестом на всех шести богах разом, а не
     * поднимать ради каждого отказа игровой мир.
     */
    public static Judgement judge(Settlement settlement, ItemStack held, long today) {
        if (held.isEmpty()) {
            return Judgement.no(Verdict.EMPTY_HANDED);
        }

        List<Identifier> pantheon = Gods.of(settlement.culture());
        if (pantheon.isEmpty()) {
            return Judgement.no(Verdict.NO_PANTHEON);
        }

        Identifier chosen = Gods.whoTakes(settlement.culture(), held.getItem()).orElse(null);
        if (chosen == null) {
            return Judgement.no(Verdict.NOT_THEIRS);
        }
        if (settlement.offeredToday(chosen, today)) {
            return new Judgement(Verdict.ALREADY_TODAY, Optional.of(chosen), 0);
        }

        God god = Gods.get(chosen).orElseThrow();
        return new Judgement(Verdict.TAKEN, Optional.of(chosen), god.worthOf(held.getItem()));
    }

    /**
     * То же, но с вопросом «твоё ли это поселение».
     * <p>
     * Вынесено сюда, а не оставлено веткой в обработчике нажатия, по той
     * самой причине, по которой в моде вообще есть вердикты: проверить
     * «отказ обязан говорить причину» можно только тогда, когда причина
     * есть <b>значением</b>. Обработчику нужен живой игрок, а правилу —
     * один его опознаватель, и проверке тоже.
     */
    public static Judgement judgeFor(Settlement settlement, java.util.UUID player,
                                     ItemStack held, long today) {
        if (!settlement.owner().isOwnedBy(player)) {
            return Judgement.no(Verdict.NOT_YOURS);
        }
        return judge(settlement, held, today);
    }

    /**
     * Положить вещь на алтарь по-настоящему.
     * <p>
     * Съедается <b>одна</b> вещь из стопки, а не вся, и это то самое
     * место, где решается, остаётся ли вера долгой целью. Сундук пшеницы,
     * высыпанный разом, купил бы избранничество за минуту, и никакого
     * «медленно» не осталось бы; одна вещь в день от каждого бога — это
     * ровно та скорость, о которой договаривались.
     */
    public static Judgement accept(ServerWorld world, SettlementManager manager,
                                   Settlement settlement, ItemStack held, long today) {
        return accept(world, manager, settlement, held, today, null);
    }

    /** Насколько жертва весомее, когда у алтаря дымит кадильница. */
    public static final double INCENSE_BONUS = 1.5;

    /** Докуда от алтаря дотягивается дым кадильницы. */
    public static final int INCENSE_REACH = 4;

    /**
     * То же, но у конкретного алтаря: если рядом дымит кадильница, боги
     * слышат лучше, и жертва весит в полтора раза больше. Благовоние —
     * старейший способ сказать «я пришёл с почтением».
     */
    public static Judgement accept(ServerWorld world, SettlementManager manager,
                                   Settlement settlement, ItemStack held, long today,
                                   net.minecraft.util.math.BlockPos altar) {
        Judgement judged = judge(settlement, held, today);
        if (!judged.verdict().isTaken()) {
            return judged;
        }
        Judgement verdict = altar != null
                && com.villagepax.block.wonder.Wonders.incenseNear(world, altar, INCENSE_REACH)
                ? new Judgement(judged.verdict(), judged.god(),
                (int) Math.ceil(judged.favour() * INCENSE_BONUS))
                : judged;

        Identifier god = verdict.god().orElseThrow();
        manager.update(settlement.id(), state -> {
            state.addFavour(god, verdict.favour());
            state.noteOffering(god, today);
        });
        held.decrement(1);
        return verdict;
    }

    /**
     * Нажатие по алтарю: жертва, рассказ или отказ — но всегда ответ.
     * <p>
     * Чужой алтарь не принимает, и это то же правило, что у чужой ратуши.
     * Дело не в жадности: благосклонность лежит <b>в поселении</b>, и
     * жертва в чужом храме подняла бы чужое небо, а игрок ждал бы своего.
     * Мод об этом прямо говорит и называет, где его собственный храм.
     */
    public static void atAltar(ServerWorld world, ServerPlayerEntity player, BlockPos altar,
                               ItemStack held) {
        SettlementManager manager = SettlementManager.get(world);
        Settlement settlement = manager.at(altar).orElse(null);

        if (settlement == null) {
            player.sendMessage(Text.translatable("villagepax.faith.altar.nowhere"), false);
            return;
        }

        long today = Schedule.dayOf(world.getTimeOfDay());

        if (judgeFor(settlement, player.getUuid(), held, today).verdict() == Verdict.NOT_YOURS) {
            player.sendMessage(Text.translatable(Verdict.NOT_YOURS.key(),
                    Text.literal(settlement.name())), false);
            return;
        }

        if (held.isEmpty()) {
            tellAboutPantheon(player, settlement, today);
            return;
        }

        Judgement verdict = accept(world, manager, settlement, held, today, altar);
        Settlement after = manager.byId(settlement.id()).orElse(settlement);

        switch (verdict.verdict()) {
            case TAKEN -> {
                Identifier god = verdict.god().orElseThrow();
                Faith.Tier tier = Faith.tierOf(after, god);
                player.sendMessage(Text.translatable("villagepax.faith.offering.taken",
                        Text.translatable(Gods.get(god).orElseThrow().displayName()),
                        Text.literal(String.valueOf(verdict.favour())),
                        Text.translatable(tier.key())), false);
                world.playSound(null, altar, SoundEvents.BLOCK_BEACON_ACTIVATE,
                        SoundCategory.BLOCKS, 0.4f, 1.6f);
                Artifacts.grantIfEarned(world, manager, after, god, player);
            }
            case ALREADY_TODAY -> player.sendMessage(Text.translatable(
                    Verdict.ALREADY_TODAY.key(),
                    Text.translatable(Gods.get(verdict.god().orElseThrow())
                            .orElseThrow().displayName())), false);
            case NOT_THEIRS -> player.sendMessage(Text.translatable(
                    Verdict.NOT_THEIRS.key(), held.getName()), false);
            case NO_PANTHEON -> player.sendMessage(Text.translatable(
                    Verdict.NO_PANTHEON.key()), false);
            default -> player.sendMessage(Text.translatable(Verdict.EMPTY_HANDED.key()), false);
        }
    }

    /**
     * Пустая рука: алтарь рассказывает, кто здесь слушает и что берёт.
     * <p>
     * Это единственное место, где игрок узнаёт пантеон, и потому оно
     * не может быть молчанием. Списка богов в меню нет намеренно: бога
     * выбирает вещь, и правило это запоминается руками, а не чтением, —
     * но узнать его откуда-то надо.
     */
    private static void tellAboutPantheon(ServerPlayerEntity player, Settlement settlement,
                                          long today) {
        List<Identifier> pantheon = Gods.of(settlement.culture());
        if (pantheon.isEmpty()) {
            player.sendMessage(Text.translatable(Verdict.NO_PANTHEON.key()), false);
            return;
        }

        player.sendMessage(Text.translatable("villagepax.faith.altar.head",
                Text.literal(settlement.name())), false);

        for (Identifier id : pantheon) {
            God god = Gods.get(id).orElseThrow();
            player.sendMessage(Text.translatable("villagepax.faith.altar.line",
                    Text.translatable(god.displayName()),
                    Text.translatable(Faith.tierOf(settlement, id).key()),
                    Text.literal(String.valueOf(settlement.favourOf(id))),
                    takes(god)), false);
        }
    }

    /** Через запятую — то, что бог берёт. */
    private static Text takes(God god) {
        Text list = null;
        for (Identifier item : god.offerings().keySet().stream()
                .sorted(java.util.Comparator.comparing(Identifier::toString)).toList()) {
            Item found = Registries.ITEM.get(item);
            Text name = found.getName();
            list = list == null ? name.copy() : list.copy().append(Text.literal(", ")).append(name);
        }
        return list == null ? Text.literal("—") : list;
    }

}
