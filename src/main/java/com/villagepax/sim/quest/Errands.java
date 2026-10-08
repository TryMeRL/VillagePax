package com.villagepax.sim.quest;

import com.villagepax.VillagePax;
import com.villagepax.core.quest.Quest;
import com.villagepax.item.ModItems;
import com.villagepax.sim.Settlement;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Суточная просьба: то, что деревня просит, когда просить по писаному
 * больше нечего.
 * <p>
 * Это лекарство от самой заметной беды старой системы: три квеста
 * у старейшины, и после третьего он говорит «просить больше нечего» —
 * <b>навсегда</b>. Деревня, с которой больше не о чем разговаривать,
 * перестаёт быть деревней и становится лавкой. Поручения не кончаются
 * никогда, и у каждого ремесла они свои: лесорубу нужны саженцы, страже
 * древки, ткачу шерсть.
 * <p>
 * <b>Почему без загляда на склад.</b> Просьба обязана быть одинаковой
 * в окне и в миг сдачи. Считай её по содержимому склада — и она менялась
 * бы, пока игрок листает экран: открыл «принеси хлеба», нажал «отдать» —
 * а у деревни уже другая нужда, и с игрока взяли не то. Поэтому просьба
 * выводится из <b>дня, деревни и ремесла</b>, и больше ни из чего.
 * Правдоподобие берётся не из склада, а из того, что просит каждое
 * ремесло по своему делу.
 * <p>
 * У каждого народа свои просьбы и свои слова ({@link ErrandBook}):
 * норманнский пивовар просит яблок на сидр, гномий — грибов на пиво.
 * Общая таблица ниже — для народов и ремёсел, которых книга не знает.
 * <p>
 * Награда мелкая — монета с крупицей доверия, а у старейшины и пивовара
 * ещё и питьё народа: цепочки строят отношения, поручения — это торговля. Одна просьба в день на ремесло,
 * и это же единственный предел: перемолоть отношения поручениями нельзя,
 * потому что день один.
 */
public final class Errands {

    /** Что просит ремесло: предмет и сколько за раз. */
    record Ask(Item item, int count) {
    }

    /**
     * Чем деревня отдаривается сверх монеты: своим питьём или сладким.
     * <p>
     * Только у старейшины и пивовара — у тех, у кого это питьё в руках.
     * Награда, которую узнаёшь по народу, и есть то, что отличает
     * поручение у майя от поручения у гномов, сильнее любых слов.
     */
    private static final Map<String, Item> TREATS = Map.of(
            "norman", ModItems.ALE, "nord", ModItems.ALE, "maya", ModItems.CACAO,
            "pony", ModItems.RAINBOW_CUPCAKE, "dwarf", ModItems.DWARVEN_STOUT,
            "elf", ModItems.ELVEN_NECTAR);

    /** Ремёсла, которые отдариваются питьём народа. */
    private static final java.util.Set<String> TREAT_GIVERS = java.util.Set.of("elder", "brewer");

    /**
     * Чего просит каждое ремесло.
     * <p>
     * Список по <b>делу</b>, а не по редкости: лесоруб просит саженцы
     * не потому, что они дёшевы, а потому что он их сажает. Именно
     * из этого просьба и читается как просьба, а не как случайный налог.
     */
    private static final Map<String, List<Ask>> BY_TRADE = Map.of(
            "elder", List.of(new Ask(Items.BREAD, 8), new Ask(Items.WHEAT, 16),
                    new Ask(Items.HAY_BLOCK, 4)),
            "lumberjack", List.of(new Ask(Items.OAK_SAPLING, 6), new Ask(Items.STICK, 32),
                    new Ask(Items.OAK_LOG, 16)),
            "farmer", List.of(new Ask(Items.BONE_MEAL, 8), new Ask(Items.WHEAT_SEEDS, 16),
                    new Ask(Items.CARROT, 12)),
            "brewer", List.of(new Ask(Items.SUGAR_CANE, 12), new Ask(Items.GLASS_BOTTLE, 6),
                    new Ask(Items.HONEYCOMB, 4)),
            "weaver", List.of(new Ask(Items.WHITE_WOOL, 8), new Ask(Items.STRING, 16),
                    new Ask(Items.SHEARS, 1)),
            "guard", List.of(new Ask(Items.ARROW, 16), new Ask(Items.IRON_INGOT, 3),
                    new Ask(Items.SHIELD, 1)),
            "builder", List.of(new Ask(Items.COBBLESTONE, 32), new Ask(Items.STONE_BRICKS, 16),
                    new Ask(Items.CLAY_BALL, 12)),
            "courier", List.of(new Ask(Items.LEATHER, 4), new Ask(Items.STICK, 16),
                    new Ask(Items.TORCH, 12)));

    /*
     * КУПЦА ЗДЕСЬ НЕТ НАМЕРЕННО.
     * <p>
     * Решение заказчика: «у торговли своё лицо». Экран купца открывается
     * сразу на прилавке и лишних вкладок не показывает — потому что
     * купцу нечего сказать, кроме цены. Дай ему поручение — и разговор
     * вернётся, а вместе с ним и те самые лишние вкладки вместо товара.
     * Кто хочет услугу, идёт к старейшине или к ремеслу.
     */

    /** Сколько доверия даёт поручение. Крупица: дружбу строят делами, а не подвозом. */
    public static final int ERRAND_TRUST = 1;

    private Errands() {
    }

    /**
     * Опознаватель сегодняшней просьбы этого ремесла.
     * <p>
     * День входит в имя нарочно. Выполненные квесты помнятся списком
     * опознавателей, и без дня поручение попало бы в него навсегда —
     * то есть повторилось бы ровно ноль раз, чего и добивались избежать.
     */
    public static Identifier idFor(Identifier giver, long today) {
        return new Identifier(VillagePax.MOD_ID, "errand/" + giver.getPath() + "_" + today);
    }

    /**
     * Поручение на этот день для игрока с таким доверием: у мастера
     * гильдии это контракт его ранга, у прочих — поручение ремесла.
     */
    public static Optional<Quest> forToday(Settlement village, Identifier giver, long today, int reputation) {
        if (Contracts.gives(giver)) {
            return Contracts.forToday(village, today, reputation);
        }
        return forToday(village, giver, today);
    }

    /** Поручение этого ремесла на этот день — или ничего, если ремесло молчит. */
    public static Optional<Quest> forToday(Settlement village, Identifier giver, long today) {
        String people = village.culture().getPath();
        String trade = giver.getPath();
        List<Ask> own = ErrandBook.BY_PEOPLE.getOrDefault(people, Map.of()).get(trade);
        List<Ask> table = own != null && !own.isEmpty() ? own : BY_TRADE.get(trade);
        if (table == null || table.isEmpty()) {
            // Ремесло, которому нечего просить, молчит — и это законно.
            // Придумывать просьбу за автора датапака мод не станет.
            return Optional.empty();
        }

        // По кругу, со сдвигом у каждой деревни: соседние дни просят разное,
        // а две деревни одного народа в один день — не обязательно одно.
        int index = Math.floorMod(seed(village, giver) + today, table.size());
        Ask ask = table.get(index);
        int count = scaled(village, ask.count());

        List<Quest.Reward> rewards = new java.util.ArrayList<>();
        rewards.add(new Quest.Reward.Give(ModItems.COIN, pay(ask.item(), count)));
        Item treat = TREATS.get(people);
        if (treat != null && TREAT_GIVERS.contains(trade)) {
            rewards.add(new Quest.Reward.Give(treat, 1));
        }
        rewards.add(new Quest.Reward.Trust(ERRAND_TRUST));

        // Своя просьба народа — своими словами; общая — общими.
        String words = table == own
                ? "villagepax.errand." + people + "." + trade + "." + index
                : "villagepax.errand." + trade;
        return Optional.of(new Quest(
                giver,
                Optional.of(village.culture()),
                0,
                List.of(new Quest.Objective.Deliver(ask.item(), count)),
                List.copyOf(rewards),
                words,
                Optional.empty()));
    }

    /**
     * Смешение деревни и ремесла в одно число: с него начинается круг просьб.
     * <p>
     * Своё, а не {@code Random}: просьба обязана быть одинаковой у всех,
     * кто о ней спросит, — у экрана, у сдачи и у соседа по серверу. Любой
     * генератор с состоянием это правило нарушает, потому что зависит
     * от того, кто спросил первым.
     */
    private static int seed(Settlement village, Identifier giver) {
        long mixed = village.id().getLeastSignificantBits() * 31L
                + giver.toString().hashCode() * 131L;
        return (int) (mixed ^ (mixed >>> 32));
    }

    /**
     * Большая деревня просит больше.
     * <p>
     * Половина за каждую ступень сверх хутора, и не больше чем вдвое:
     * город, просящий восемь хлебов, выглядел бы нищим, а просящий
     * сотню — грабителем.
     */
    private static int scaled(Settlement village, int base) {
        int step = Math.min(2, village.level().ordinal());
        return base + base * step / 2;
    }

    /**
     * Сколько медяков стоит такая охапка.
     * <p>
     * По числу вещей, а не по их редкости: настоящую цену знает стол
     * торга, и дублировать его здесь значило бы завести вторую цену
     * на ту же вещь. Поручение — мелкая услуга, и платят за неё мелко;
     * кто хочет выручки, идёт к купцу.
     */
    private static int pay(Item item, int count) {
        int coins = 2 + count / 4;
        // Штучные вещи (щит, ножницы) стоят труда, а не счёта.
        return item.getMaxCount() == 1 ? Math.max(coins, 6) : coins;
    }

    /** Есть ли у этого ремесла поручения вообще. */
    public static boolean knows(Identifier giver) {
        return BY_TRADE.containsKey(giver.getPath());
    }

    /** Ключи слов всех поручений, общих и народных: их проверяет словарь. */
    public static List<String> dialogueKeys() {
        List<String> keys = new java.util.ArrayList<>(BY_TRADE.keySet().stream().sorted()
                .map(trade -> "villagepax.errand." + trade)
                .toList());
        ErrandBook.BY_PEOPLE.forEach((people, trades) -> trades.forEach((trade, asks) -> {
            for (int i = 0; i < asks.size(); i++) {
                keys.add("villagepax.errand." + people + "." + trade + "." + i);
            }
        }));
        keys.addAll(Contracts.dialogueKeys());
        return keys.stream().sorted().toList();
    }

    /** Предметы всех поручений: по ним проверяют, что мод их знает. */
    public static List<Identifier> asked() {
        return java.util.stream.Stream.concat(BY_TRADE.values().stream(),
                        ErrandBook.BY_PEOPLE.values().stream().flatMap(trades -> trades.values().stream()))
                .flatMap(List::stream)
                .map(ask -> Registries.ITEM.getId(ask.item()))
                .distinct()
                .sorted(java.util.Comparator.comparing(Identifier::toString))
                .toList();
    }
}
