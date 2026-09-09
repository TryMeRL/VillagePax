package com.villagepax.sim;

import com.villagepax.core.config.Configs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
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
        if (Founding.colonyOf(SettlementManager.get(player.getServerWorld()),
                player.getUuid()).isPresent()) {
            return;
        }

        player.sendMessage(Text.translatable("villagepax.greeting.title")
                .formatted(Formatting.GOLD), false);
        player.sendMessage(Text.translatable("villagepax.greeting.how")
                .formatted(Formatting.GRAY), false);
    }
}
