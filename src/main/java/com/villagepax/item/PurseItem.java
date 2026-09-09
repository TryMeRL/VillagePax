package com.villagepax.item;

import com.villagepax.sim.trade.Coins;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Кошель: монета лежит в нём числом, а не стопками.
 * <p>
 * Решение заказчика: «нужен просто кошелёк как предмет». Ларец-блок,
 * который я было начал, отвергнут — и правильно: деньги игрок носит
 * с собой, а не оставляет в сундуке у ратуши.
 * <p>
 * Внутри не слоты, а <b>одно число</b> — сколько в кошеле медяков. Монета
 * одинакова, и слоты ей ни к чему: девять медяков и один серебряк — это
 * одна и та же сумма, и заставлять игрока их перекладывать значило бы
 * придумать работу на ровном месте. Зато кошель сам показывает сумму
 * тремя достоинствами, а размен получается сам собой.
 * <p>
 * Щелчок — сложить в кошель всю россыпь; щелчок вприсядку — вытряхнуть
 * обратно. Торг при этом заглядывает в кошель сам: игроку не приходится
 * вытряхивать деньги перед каждой покупкой.
 */
public class PurseItem extends Item {

    private static final String VALUE_KEY = "Coins";

    /**
     * Сколько влезает: девять золотых.
     * <p>
     * Предел есть, и он не для баланса: кошель без предела — это банк,
     * а банк в кармане обесценил бы и сундук, и казну деревни. Девять
     * золотых — крупная сумма при ценах в единицы медяков, и упереться
     * в предел игрок сможет только разбогатев по-настоящему.
     */
    public static final int CAPACITY = Coins.GOLD * 9;

    public PurseItem(Settings settings) {
        super(settings);
    }

    public static int valueOf(ItemStack purse) {
        NbtCompound nbt = purse.getNbt();
        return nbt == null ? 0 : Math.max(0, nbt.getInt(VALUE_KEY));
    }

    public static void setValue(ItemStack purse, int value) {
        purse.getOrCreateNbt().putInt(VALUE_KEY, Math.max(0, Math.min(CAPACITY, value)));
    }

    public static int roomIn(ItemStack purse) {
        return CAPACITY - valueOf(purse);
    }

    /** Положить сколько влезет, вернув непоместившееся. */
    public static int put(ItemStack purse, int value) {
        int fits = Math.min(value, roomIn(purse));
        setValue(purse, valueOf(purse) + fits);
        return value - fits;
    }

    /** Взять сколько есть, вернув взятое. */
    public static int take(ItemStack purse, int value) {
        int taken = Math.min(value, valueOf(purse));
        setValue(purse, valueOf(purse) - taken);
        return taken;
    }

    /**
     * Щелчок: убрать россыпь в кошель. Вприсядку: вытряхнуть обратно.
     * <p>
     * Оба действия говорят, что вышло, — иначе игрок щёлкает и не понимает,
     * случилось ли что-нибудь.
     */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack purse = player.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(purse, true);
        }

        int moved = player.isSneaking()
                ? Coins.emptyPurse(player.getInventory(), purse)
                : Coins.fillPurse(player.getInventory(), purse);

        if (moved <= 0) {
            player.sendMessage(Text.translatable(player.isSneaking()
                    ? "villagepax.purse.empty" : "villagepax.purse.nothing_to_add"), true);
            return TypedActionResult.pass(purse);
        }

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 0.7f, player.isSneaking() ? 0.9f : 1.2f);
        player.sendMessage(Text.translatable(player.isSneaking()
                ? "villagepax.purse.took_out" : "villagepax.purse.put_in",
                Coins.spell(moved)), true);
        return TypedActionResult.success(purse, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip,
                              TooltipContext context) {
        tooltip.add(Coins.spell(valueOf(stack)).copy().formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("villagepax.purse.how").formatted(Formatting.DARK_GRAY));
    }

    // --- полоска наполнения: сколько ещё влезет, видно не читая ---

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return valueOf(stack) > 0;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13.0f * valueOf(stack) / CAPACITY);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        // Цвет монеты, а не ванильной прочности: полоска про деньги.
        return 0xE0B030;
    }
}
