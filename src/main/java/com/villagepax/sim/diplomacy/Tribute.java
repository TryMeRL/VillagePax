package com.villagepax.sim.diplomacy;

import com.mojang.serialization.Codec;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.trade.Coins;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.Inventory;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.Founding;

import java.util.Optional;
import java.util.UUID;

/**
 * Дань: деревня платит тому, кто положил её людей.
 * <p>
 * Вторая половина дипломатии и её <b>кнут</b>. Союз мод уже умеет — за него
 * приходят с мечами на твою сторону; дань — то же самое отношение, вывернутое
 * наизнанку: сосед не помогает, а откупается. Лестница ступеней обещала это
 * с первой недели — «город: союзы и право требовать дань со слабых соседей».
 * <p>
 * <b>Дань берут со страха, а не с силы вообще.</b> Требовать можно только
 * у той деревни, чей отряд <b>только что</b> лёг под твоими воротами, и не
 * дольше, чем память об этом держится. Без этого правила дань была бы
 * налогом на соседство: подрос — и обложил всех вокруг, ничем не рискуя.
 * А так у неё есть цена, и платит её игрок заранее — своей обороной.
 * <p>
 * <b>Друг дани не платит.</b> Деревня, которая считает игрока другом, на
 * требование ответит отказом, и это не мягкость: дружба и дань — разные
 * разговоры, и смешать их значит обесценить оба. Ровно поэтому требование
 * <b>разрывает союз</b>: нельзя разом вступаться за человека и откупаться
 * от него.
 * <p>
 * И дань — это не бесплатные деньги, а <b>петля</b>. Каждый платёж роняет
 * доверие: деревня помнит, кто её обирает, и однажды терпение выйдет снова.
 * Игрок получит второй набег — и либо отобьёт его и продлит дань, либо
 * помирится и потеряет её. Выбор между монетой и покоем и есть содержание.
 */
public final class Tribute {

    /**
     * Сколько дней помнят разгром.
     * <p>
     * Двадцать дней — это вчетверо дольше, чем деревня остывает между
     * набегами, и вдвое дольше самого откупа. Успел прийти и потребовать —
     * твоё; пропустил — деревня отстроилась и забыла, и требовать надо
     * заново, то есть снова отбиваться.
     */
    public static final int MEMORY = 20;

    /** Сколько дней платят. */
    public static final int DAYS = 10;

    /** Сколько платят в день — два серебра. */
    public static final int RATE = Coins.SILVER * 2;

    /**
     * Во сколько обходится деревне каждый платёж.
     * <p>
     * Два очка доверия в день, десять дней — двадцать за срок. Этого мало,
     * чтобы разом сорваться в набег, и достаточно, чтобы дань <b>копила
     * обиду</b>: обобранная деревня не любит обирающего, и рано или поздно
     * это кончится отрядом у ворот.
     */
    public static final int RESENTMENT = 2;

    /** С какой ступени поселения у игрока вообще есть право требовать. */
    public static final SettlementLevel NEEDS = SettlementLevel.TOWN;

    private Tribute() {
    }

    /**
     * Что выйдет, если потребовать дань, — до того, как потребовал.
     * <p>
     * Тот же приём, что у подарка, откупа и союза: приговор считает сервер
     * и показывает заранее, а не отказывает молчащей кнопкой.
     */
    public static Verdict judge(Settlement village, Settlement colony, UUID player, long today) {
        if (!village.owner().isAutonomous()) {
            return Verdict.NOT_A_NEIGHBOUR;
        }
        if (village.owesTributeTo(player, today)) {
            return Verdict.ALREADY;
        }
        if (colony == null || colony.level().ordinal() < NEEDS.ordinal()) {
            return Verdict.NO_TOWN;
        }
        if (village.beatenOn() == Settlement.UNSEEN_DAY
                || today - village.beatenOn() > MEMORY) {
            return Verdict.NOT_BEATEN;
        }
        // Бил не ты — не тебе и требовать. Старые сохранения не знают,
        // кем бита деревня, и для них правило прежнее.
        if (village.beatenBy().filter(winner -> !winner.equals(player)).isPresent()) {
            return Verdict.NOT_BEATEN;
        }
        if (village.reputationOf(player) >= Standing.FRIEND.from()) {
            return Verdict.TOO_FRIENDLY;
        }
        return Verdict.YES;
    }

    /**
     * Потребовать дань.
     * <p>
     * Союз при этом разрывается: нельзя разом вступаться за человека
     * и откупаться от него. Доверие само требование не роняет — его
     * уронят платежи, день за днём, и это честнее: обижает не слово,
     * а серебро, которое каждое утро уходит из своего сундука.
     */
    public static Outcome demand(SettlementManager manager, Settlement village,
                                 Settlement colony, UUID player, long today) {
        Verdict verdict = judge(village, colony, player, today);
        if (verdict != Verdict.YES) {
            return new Outcome(verdict, 0);
        }

        manager.update(village.id(), state -> {
            state.breakAlly(player);
            state.startTribute(player, today + DAYS);
        });
        return new Outcome(Verdict.YES, DAYS);
    }

    /**
     * Заплатить за день.
     * <p>
     * Монета переезжает <b>со склада деревни на склад колонии</b> — из
     * сундука в сундук, как и всё имущество в этом моде. Числа в сохранении
     * не растут: обобрать можно только того, у кого есть что взять, и
     * увидеть это можно, открыв её сундук.
     * <p>
     * Платёж роняет доверие: дань копит обиду, и однажды она кончится
     * отрядом у ворот. Это не побочное следствие, а содержание — иначе
     * дань была бы бесплатным доходом, а не решением.
     *
     * @return сколько заплачено сегодня; ноль — значит взять было нечего
     */
    public static int pay(ServerWorld world, SettlementManager manager, Settlement village,
                          long today) {
        UUID player = village.tributeTo().orElse(null);
        if (player == null) {
            return 0;
        }
        if (!village.owesTributeTo(player, today)) {
            // Срок вышел: запись убирается здесь, а не при следующем
            // разговоре, — иначе деревня «платила бы» в пульте ещё неделю
            // после конца.
            manager.update(village.id(), Settlement::stopTribute);
            tell(world, player, "villagepax.tribute.over", Text.literal(village.name()));
            return 0;
        }

        Settlement colony = Founding.colonyOf(manager, player).orElse(null);
        if (colony == null) {
            // Колонии не стало — дань не кому платить, а деревня свободна.
            manager.update(village.id(), Settlement::stopTribute);
            return 0;
        }

        Warehouse theirs = Warehouse.of(world, village);
        Inventory purse = theirs.coins();
        if (!Coins.has(purse, RATE)) {
            // Деревня разорена. Платёж пропускается, но дань не кончается:
            // завтра она заработает, и завтра же заплатит.
            tell(world, player, "villagepax.tribute.broke", Text.literal(village.name()));
            return 0;
        }

        // Склад колонии — дотянуться, даже если игрок далеко от неё: платят
        // при игроке у деревни, а его ратуша в это время обычно не загружена.
        // Прежде пустой «склад невидимой колонии» не принимал ничего, и дань
        // высыпалась на землю у ратуши, где через пять минут и исчезала.
        Warehouse ours = Warehouse.reach(world, colony);
        Coins.pay(purse, RATE).forEach(change ->
                theirs.addOrScatter(world, village.center(), change));
        Coins.earn(ours.coins(), RATE).forEach(left ->
                ours.addOrScatter(world, colony.center(), left));

        manager.update(village.id(), state -> state.addReputation(player, -RESENTMENT));
        tell(world, player, "villagepax.tribute.paid", Text.literal(village.name()),
                Coins.spell(RATE));
        return RATE;
    }

    private static void tell(ServerWorld world, UUID player, String key, Text... args) {
        ServerPlayerEntity who = world.getServer().getPlayerManager().getPlayer(player);
        if (who != null) {
            who.sendMessage(Text.translatable(key, (Object[]) args), false);
        }
    }

    /**
     * Чем кончилось требование.
     *
     * @param verdict согласились ли платить, и если нет — почему
     * @param days    на сколько дней положена дань
     */
    public record Outcome(Verdict verdict, int days) {

        public boolean taken() {
            return verdict == Verdict.YES;
        }
    }

    /**
     * Приговор требованию.
     * <p>
     * Причина названа вслух: «кнопка серая» игроку ничего не объясняет,
     * а «сперва разбей их отряд» — объясняет всё и вдобавок называет цель.
     */
    public enum Verdict implements Named {

        /** Платят. */
        YES("yes"),

        /** Это своя колония: дань с самого себя не берут. */
        NOT_A_NEIGHBOUR("not_a_neighbour"),

        /** Дань уже идёт. */
        ALREADY("already"),

        /** У игрока нет города: требовать нечем. */
        NO_TOWN("no_town"),

        /** Их отряд не бит — или бит слишком давно, и страх прошёл. */
        NOT_BEATEN("not_beaten"),

        /** Деревня считает игрока другом: с друзей дани не берут. */
        TOO_FRIENDLY("too_friendly");

        public static final Codec<Verdict> CODEC = EnumCodecs.of(values(), "приговор дани");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        /** Ключ объяснения. Согласию объяснять нечего. */
        public Optional<String> reasonKey() {
            return this == YES ? Optional.empty() : Optional.of("villagepax.tribute.reason." + id);
        }
    }
}
