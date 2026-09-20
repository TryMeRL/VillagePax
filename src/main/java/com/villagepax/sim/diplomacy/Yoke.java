package com.villagepax.sim.diplomacy;

import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.war.Conquest;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Ярмо: дань, которую платит колония игрока.
 * <p>
 * Оборотная сторона {@link Tribute}, и нарочно отдельным кодом, хотя
 * запись в поселении одна на оба случая. Разница не в числах, а в том,
 * <b>кто спрашивает</b>: деревня платит человеку и ищет его колонию,
 * а колония платит деревне и ищет саму деревню. Свести их в одну функцию
 * значило бы завести внутри неё развилку «а кто я сейчас» — то есть те же
 * два кода, только переплетённые.
 * <p>
 * <b>Платёж доверия не поднимает ни на очко.</b> Это то же решение, что
 * и у откупа: деньгами покупается тишина, а не дружба. Деревня берёт
 * серебро и продолжает считать игрока разбойником — мирится он делами,
 * а не кошельком, иначе дипломатия отменялась бы деньгами.
 * <p>
 * <b>А вот неуплата обижает.</b> Пустая казна роняет доверие каждый день,
 * и это единственная настоящая угроза вассала: не заплатил — приблизил
 * следующий отряд. Так у ярма появляется выбор вместо строки в пульте:
 * отдавать серебро или готовиться к драке.
 */
public final class Yoke {

    private Yoke() {
    }

    /**
     * Заплатить за день.
     * <p>
     * Монета переезжает <b>со склада колонии на склад деревни</b> — из
     * сундука в сундук, как и всё имущество в этом моде. Числа
     * в сохранении не растут: отнять можно только то, что лежит, и увидеть
     * это можно, открыв свой сундук.
     *
     * @return сколько заплачено сегодня; ноль — значит взять было нечего
     */
    public static int pay(ServerWorld world, SettlementManager manager, Settlement colony,
                          long today) {
        if (colony.owner().isAutonomous()) {
            // Деревня платит человеку другим кодом и по другим правилам:
            // см. Tribute. Здесь — только колония игрока.
            return 0;
        }
        UUID to = colony.tributeTo().orElse(null);
        if (to == null) {
            return 0;
        }
        Settlement lord = manager.byId(to).orElse(null);
        if (lord == null) {
            // Платят человеку, а не поселению: это не ярмо, а дань наоборот,
            // и её считает Tribute со стороны плательщика-деревни. Сюда
            // такая запись попасть не может, но молча уйти отсюда — может.
            return 0;
        }

        if (colony.tributeDaysLeft(today) <= 0) {
            // Срок вышел: запись снимается здесь, а не при следующем
            // разговоре, — иначе пульт показывал бы ярмо ещё неделю
            // после конца.
            manager.update(colony.id(), Settlement::stopTribute);
            tell(world, colony, "villagepax.yoke.over", Text.literal(lord.name()));
            return 0;
        }

        Warehouse ours = Warehouse.of(world, colony);
        Inventory purse = ours.coins();
        if (!Coins.has(purse, Tribute.RATE)) {
            // Платить нечем. Дань от этого не кончается — кончается
            // терпение: каждый неоплаченный день деревня записывает
            // на счёт игрока, и однажды этот счёт приведёт отряд.
            manager.update(lord.id(), state ->
                    state.addReputation(colony.owner().player().orElseThrow(),
                            -Tribute.RESENTMENT));
            tell(world, colony, "villagepax.yoke.unpaid", Text.literal(lord.name()));
            return 0;
        }

        Warehouse theirs = Warehouse.of(world, lord);
        Coins.pay(purse, Tribute.RATE).forEach(change ->
                ours.addOrScatter(world, colony.center(), change));
        Coins.earn(theirs.coins(), Tribute.RATE).forEach(left ->
                theirs.addOrScatter(world, lord.center(), left));

        tell(world, colony, "villagepax.yoke.paid", Text.literal(lord.name()),
                Coins.spell(Tribute.RATE));
        return Tribute.RATE;
    }

    /** Под чьим ярмом колония — для пульта и совета. */
    public static java.util.Optional<Settlement> overlord(SettlementManager manager,
                                                         Settlement colony, long today) {
        return Conquest.overlordOf(manager, colony, today);
    }

    private static void tell(ServerWorld world, Settlement colony, String key, Text... args) {
        UUID player = colony.owner().player().orElse(null);
        if (player == null) {
            return;
        }
        ServerPlayerEntity who = world.getServer().getPlayerManager().getPlayer(player);
        if (who != null) {
            who.sendMessage(Text.translatable(key, (Object[]) args)
                    .formatted(Formatting.GRAY), false);
        }
    }
}
