package com.villagepax.sim.trade;

import com.villagepax.item.ModItems;
import com.villagepax.item.PurseItem;
import com.villagepax.sim.Stacks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Монета мода: три достоинства, размен между ними и кошель.
 * <p>
 * Решение заказчика — <b>своя монета вместо изумруда</b>. Довод против
 * своей монеты был такой: игроку пришлось бы её где-то взять, а взять
 * негде, кроме этой же торговли. Заказчик решил иначе, и вышло к лучшему:
 * монета появляется в мире <b>только</b> у деревень — их дневной выручкой
 * и наградой за квесты, — и потому хозяйство мода замкнуто. Изумруд
 * остался обычным товаром, каким и был в ванили.
 * <p>
 * Монету нельзя выковать из руды, и это тоже намеренно. Была бы можно —
 * кошель набивался бы кайлом, и торговля обесценилась бы к третьему дню.
 * Из руды делают товар, товар продают деревне, деревня платит монетой.
 * <p>
 * Достоинства идут по девять, как ванильные слитки из самородков: девять
 * медяков — серебряк, девять серебряков — золотой. Девять, а не десять,
 * потому что размен раскладывается в верстаке три на три и объяснять
 * его не нужно.
 * <p>
 * <b>Кошель считается кошельком, а не сундуком.</b> Всё здесь смотрит и
 * в россыпь, и в кошели: иначе игрок, убравший деньги в кошель, не смог
 * бы ничего купить, пока не вытряхнет его на землю.
 */
public final class Coins {

    /** Во сколько медяков идёт каждое достоинство, от крупного к мелкому. */
    private static final Map<Item, Integer> WORTH = new LinkedHashMap<>();

    /** Что во что разменивается вниз: золотой — в серебряки, и так далее. */
    private static final Map<Item, Item> SMALLER = new LinkedHashMap<>();

    /** Сколько мелких даёт один крупный. */
    public static final int PER_STEP = 9;

    public static final int COPPER = 1;
    public static final int SILVER = COPPER * PER_STEP;
    public static final int GOLD = SILVER * PER_STEP;

    static {
        WORTH.put(ModItems.GOLD_COIN, GOLD);
        WORTH.put(ModItems.SILVER_COIN, SILVER);
        WORTH.put(ModItems.COIN, COPPER);

        SMALLER.put(ModItems.GOLD_COIN, ModItems.SILVER_COIN);
        SMALLER.put(ModItems.SILVER_COIN, ModItems.COIN);
    }

    private Coins() {
    }

    /** Монета ли это, и на сколько медяков. Ноль — не монета. */
    public static int worth(Item item) {
        return WORTH.getOrDefault(item, 0);
    }

    public static boolean isCoin(Item item) {
        return WORTH.containsKey(item);
    }

    /** Достоинства от крупного к мелкому: в этом порядке и платят, и выдают. */
    public static List<Item> denominations() {
        return List.copyOf(WORTH.keySet());
    }

    /**
     * Сумма словами: «2 золотых, 4 серебряка, 7 медяков».
     * <p>
     * Нужно и подсказке кошеля, и сообщениям торга. Достоинства, которых
     * ноль, не называются: «0 золотых, 0 серебряков, 7 медяков» читается
     * хуже, чем «7 медяков».
     */
    public static Text spell(int amount) {
        if (amount <= 0) {
            return Text.translatable("villagepax.coins.none");
        }

        List<Text> parts = new ArrayList<>();
        int left = amount;
        for (Item coin : WORTH.keySet()) {
            int many = left / worth(coin);
            if (many > 0) {
                parts.add(Text.translatable("villagepax.coins." + nameOf(coin), many));
                left -= many * worth(coin);
            }
        }

        Text joined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            joined = Text.translatable("villagepax.coins.join", joined, parts.get(i));
        }
        return joined;
    }

    private static String nameOf(Item coin) {
        if (coin == ModItems.GOLD_COIN) {
            return "gold";
        }
        return coin == ModItems.SILVER_COIN ? "silver" : "copper";
    }

    // --- сколько есть ---

    /** Сколько всего монеты здесь, в медяках: россыпь и кошели вместе. */
    public static int total(Inventory holder) {
        return loose(holder) + inPurses(holder);
    }

    public static boolean has(Inventory holder, int amount) {
        return amount <= 0 || total(holder) >= amount;
    }

    /** Только россыпь — без кошелей. */
    public static int loose(Inventory holder) {
        int sum = 0;
        for (int slot = 0; slot < holder.size(); slot++) {
            ItemStack stack = holder.getStack(slot);
            sum += worth(stack.getItem()) * stack.getCount();
        }
        return sum;
    }

    public static int inPurses(Inventory holder) {
        int sum = 0;
        for (int slot = 0; slot < holder.size(); slot++) {
            ItemStack stack = holder.getStack(slot);
            if (stack.isOf(ModItems.PURSE)) {
                sum += PurseItem.valueOf(stack);
            }
        }
        return sum;
    }

    // --- заплатить ---

    /**
     * Заплатить столько медяков: сперва россыпью, потом из кошеля.
     * <p>
     * Россыпью сперва не из вредности: монета в руках должна тратиться
     * раньше отложенной, иначе кошель пустеет, а карман остаётся полон
     * мелочи. Россыпь при этом разменивается по надобности — золотой
     * распадается на серебряки, серебряк на медяки.
     *
     * @return сдача, которой не нашлось места; пусто, если всё уложилось
     */
    public static List<ItemStack> pay(Inventory holder, int amount) {
        int fromLoose = Math.min(amount, loose(holder));
        List<ItemStack> spilled = payLoose(holder, fromLoose);

        int left = amount - fromLoose;
        for (int slot = 0; slot < holder.size() && left > 0; slot++) {
            ItemStack stack = holder.getStack(slot);
            if (stack.isOf(ModItems.PURSE)) {
                left -= PurseItem.take(stack, left);
                holder.markDirty();
            }
        }
        return spilled;
    }

    /**
     * Выдать столько медяков: сперва в кошель, остаток россыпью.
     * <p>
     * В кошель сперва — потому что за этим он и нужен: деньги сами
     * убираются, а не заполняют карман стопками медяков.
     *
     * @return то, что не влезло
     */
    public static List<ItemStack> earn(Inventory holder, int amount) {
        int left = amount;
        for (int slot = 0; slot < holder.size() && left > 0; slot++) {
            ItemStack stack = holder.getStack(slot);
            if (stack.isOf(ModItems.PURSE)) {
                left = PurseItem.put(stack, left);
                holder.markDirty();
            }
        }
        return earnLoose(holder, left);
    }

    /**
     * Влезет ли выданное. Считается по достоинствам, которыми выдадут:
     * сорок медяков и четыре серебряка занимают разное число слотов.
     */
    public static boolean room(Inventory holder, int amount) {
        int left = amount;
        for (int slot = 0; slot < holder.size() && left > 0; slot++) {
            ItemStack stack = holder.getStack(slot);
            if (stack.isOf(ModItems.PURSE)) {
                left = Math.max(0, left - PurseItem.roomIn(stack));
            }
        }

        for (Item coin : WORTH.keySet()) {
            int many = left / worth(coin);
            if (many > 0 && !Stacks.room(holder, coin, many)) {
                return false;
            }
            left -= many * worth(coin);
        }
        return true;
    }

    /**
     * Монеты на эту сумму — крупными вперёд, стопками не больше полной.
     * <p>
     * Нужно тому, кто кладёт монету не в руки, а в сундук: проигрыш
     * за игорным столом уходит на склад деревни, и девять медяков ложатся
     * туда серебряком, а не девятью монетами.
     */
    public static List<ItemStack> stacksFor(int amount) {
        List<ItemStack> stacks = new ArrayList<>();
        int left = amount;
        for (Item coin : WORTH.keySet()) {
            int many = left / worth(coin);
            left -= many * worth(coin);
            while (many > 0) {
                int chunk = Math.min(many, coin.getMaxCount());
                stacks.add(new ItemStack(coin, chunk));
                many -= chunk;
            }
        }
        return stacks;
    }

    // --- кошель руками ---

    /** Убрать всю россыпь в кошель. Возвращает, сколько убралось. */
    public static int fillPurse(Inventory holder, ItemStack purse) {
        int room = PurseItem.roomIn(purse);
        int moved = Math.min(room, loose(holder));
        if (moved <= 0) {
            return 0;
        }
        payLoose(holder, moved);
        PurseItem.put(purse, moved);
        holder.markDirty();
        return moved;
    }

    /**
     * Вытряхнуть кошель в россыпь — столько, сколько поместится.
     * <p>
     * Сколько поместится, а не всё: на полном инвентаре вытряхнутая
     * монета иначе упала бы на землю, и игрок нашёл бы её не сразу.
     * <p>
     * Не влезшее возвращается в кошель, а не считается заранее. Раньше
     * «влезет ли» спрашивали у сумки, где кошели притворялись пустыми
     * слотами, — и при полной сумке кошель отдавал стопку монет в свой же
     * слот, которого не было: монета пропадала, а игрок читал «вынуто».
     * Место в кошеле есть всегда — монета только что из него вышла.
     */
    public static int emptyPurse(Inventory holder, ItemStack purse) {
        int value = PurseItem.take(purse, PurseItem.valueOf(purse));
        if (value <= 0) {
            return 0;
        }

        int back = 0;
        for (ItemStack rest : earnLoose(holder, value)) {
            back += worth(rest.getItem()) * rest.getCount();
        }
        PurseItem.put(purse, back);
        holder.markDirty();
        return value - back;
    }

    // --- россыпь ---

    /**
     * Заплатить россыпью, разменивая крупное по надобности.
     * <p>
     * Считается сперва, двигается потом — как и всё в торге. Разменянный
     * золотой занимает больше слотов, чем занимал сам, поэтому сдача
     * может не влезть; терять её нельзя, и она возвращается списком.
     */
    private static List<ItemStack> payLoose(Inventory holder, int amount) {
        if (amount <= 0) {
            return List.of();
        }

        Map<Item, Integer> counts = countOf(holder);
        int left = amount;

        while (left > 0) {
            Item spend = largestNotExceeding(counts, left);
            if (spend != null) {
                counts.merge(spend, -1, Integer::sum);
                left -= worth(spend);
                continue;
            }

            Item change = smallestAbove(counts, left);
            if (change == null) {
                // Сюда не попасть: сумма проверена до вызова. Но если
                // однажды попадём — лучше не тронуть ничего, чем забрать
                // половину.
                return List.of();
            }
            counts.merge(change, -1, Integer::sum);
            counts.merge(SMALLER.get(change), PER_STEP, Integer::sum);
        }

        return rewrite(holder, counts);
    }

    /** Выдать россыпью — крупным вперёд: так занимает меньше слотов. */
    private static List<ItemStack> earnLoose(Inventory holder, int amount) {
        List<ItemStack> spilled = new ArrayList<>();
        int left = amount;

        for (Item coin : WORTH.keySet()) {
            int worth = worth(coin);
            int many = left / worth;
            left -= many * worth;

            while (many > 0) {
                int chunk = Math.min(many, coin.getMaxCount());
                ItemStack rest = Stacks.insert(holder, new ItemStack(coin, chunk));
                if (!rest.isEmpty()) {
                    spilled.add(rest);
                }
                many -= chunk;
            }
        }
        return spilled;
    }

    private static Map<Item, Integer> countOf(Inventory holder) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Item coin : WORTH.keySet()) {
            counts.put(coin, 0);
        }
        for (int slot = 0; slot < holder.size(); slot++) {
            ItemStack stack = holder.getStack(slot);
            if (isCoin(stack.getItem())) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private static Item largestNotExceeding(Map<Item, Integer> counts, int left) {
        for (Item coin : WORTH.keySet()) {
            if (counts.getOrDefault(coin, 0) > 0 && worth(coin) <= left) {
                return coin;
            }
        }
        return null;
    }

    /** Самое мелкое из имеющихся достоинств, которое всё ещё крупнее долга. */
    private static Item smallestAbove(Map<Item, Integer> counts, int left) {
        Item found = null;
        for (Item coin : WORTH.keySet()) {
            if (counts.getOrDefault(coin, 0) > 0 && worth(coin) > left
                    && SMALLER.containsKey(coin)) {
                found = coin;
            }
        }
        return found;
    }

    /** Выложить посчитанное заново: старую россыпь убрать, новую положить. */
    private static List<ItemStack> rewrite(Inventory holder, Map<Item, Integer> counts) {
        for (int slot = 0; slot < holder.size(); slot++) {
            if (isCoin(holder.getStack(slot).getItem())) {
                holder.removeStack(slot);
            }
        }
        holder.markDirty();

        List<ItemStack> spilled = new ArrayList<>();
        for (Item coin : WORTH.keySet()) {
            int many = counts.getOrDefault(coin, 0);
            while (many > 0) {
                int chunk = Math.min(many, coin.getMaxCount());
                ItemStack rest = Stacks.insert(holder, new ItemStack(coin, chunk));
                if (!rest.isEmpty()) {
                    spilled.add(rest);
                }
                many -= chunk;
            }
        }
        return spilled;
    }
}
