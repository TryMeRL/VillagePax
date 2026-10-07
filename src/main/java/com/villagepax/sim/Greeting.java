package com.villagepax.sim;

import com.villagepax.core.config.Configs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Первое слово мода игроку.
 * <p>
 * Без него мод невидим. Игрок заходит в мир, ничего не происходит, и узнать
 * о моде можно только прочитав его описание на стороне: деревни стоят
 * в семи сотнях блоков, чертёж ратуши крафтится по рецепту, которого никто
 * не подсказывает, а старейшина ждёт где-то в лесу. Одна строка в чате
 * закрывает эту дыру целиком.
 * <p>
 * Говорится <b>один раз тому, у кого ещё нет колонии</b>: игроку с колонией
 * рассказывать про вход в мод незачем, он уже вошёл. Никакого состояния
 * для этого не хранится — вопрос задаётся поселениям, и они же отвечают.
 */
public final class Greeting {

    private Greeting() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> greet(handler.player));
    }

    private static void greet(ServerPlayerEntity player) {
        if (!Configs.get().greetNewcomers()) {
            return;
        }
        // Колония может стоять в другом измерении: хозяин, вошедший в мир
        // в Незере, — не новичок, и звать его к первой деревне незачем.
        for (net.minecraft.server.world.ServerWorld world : player.getServer().getWorlds()) {
            if (Founding.colonyOf(SettlementManager.get(world), player.getUuid()).isPresent()) {
                return;
            }
        }

        player.sendMessage(Text.translatable("villagepax.greeting.title")
                .formatted(Formatting.GOLD), false);
        player.sendMessage(Text.translatable("villagepax.greeting.how")
                .formatted(Formatting.GRAY), false);
        player.sendMessage(where(player), false);
        player.sendMessage(takeTheBook(), false);
    }

    /**
     * Куда идти за первой деревней.
     * <p>
     * Самое дорогое, что есть у новичка, — это первый час, и весь он
     * уходит на поиск. Деревни стоят по сетке и только в своих биомах:
     * в мире, где вокруг спавна степь, ближайшая может оказаться за
     * две тысячи блоков, и игрок бросит мод, так и не увидев ни одного
     * жителя. Так и вышло у первого игрока — деревня встала в двух
     * с лишним тысячах блоков от спавна.
     * <p>
     * Направление считается тем же поиском, что и команда {@code locate},
     * — по генератору, без загрузки чанков. Один раз на вход, и только
     * тому, у кого ещё нет колонии.
     */
    private static Text where(ServerPlayerEntity player) {
        VillageSites.Guess nearest = VillageSites.guessNearest(player.getServerWorld(),
                player.getBlockPos());
        if (nearest == null) {
            return Text.translatable("villagepax.greeting.nowhere").formatted(Formatting.GRAY);
        }

        int away = (int) Math.sqrt(nearest.where().getSquaredDistance(player.getBlockPos()));
        return Text.translatable("villagepax.greeting.where",
                        Text.translatable("villagepax.culture." + nearest.culture().getPath()),
                        Text.literal(nearest.where().getX() + ", " + nearest.where().getZ()),
                        Text.literal(String.valueOf(away)))
                .formatted(Formatting.YELLOW);
    }

    /**
     * Строка, по которой книга приходит в руки.
     * <p>
     * Нажатием, а не выдачей: книга, которая сама лезет в инвентарь при
     * каждом входе, — это мусор в сумке. Нажал — взял.
     */
    private static Text takeTheBook() {
        return Text.translatable("villagepax.greeting.book")
                .formatted(Formatting.AQUA, Formatting.UNDERLINE)
                .styled(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/villagepax guide"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.translatable("villagepax.greeting.book_hover"))));
    }
}
