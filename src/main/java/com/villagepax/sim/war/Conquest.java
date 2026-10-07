package com.villagepax.sim.war;

import com.villagepax.VillagePax;
import com.villagepax.core.war.WarParty;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.diplomacy.Tribute;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;
import java.util.UUID;

/**
 * Взятие поселения — исход, которого у войны не было.
 * <p>
 * Последний пункт фазы 3 из дорожной карты: «осады, <b>захват поселений</b>».
 * До сих пор набег кончался одинаково при любом сопротивлении: отряд бил
 * стены, уносил со склада и уходил. Игрок, отсидевшийся в подвале, терял
 * несколько блоков и сундук пшеницы — то есть война была погодой.
 *
 * <h2>Одно правило на обе стороны</h2>
 * <b>Поселение взято, если отряд простоял свой срок и не потерял ни одного
 * бойца.</b> Читается одинаково с обеих сторон: из колонии — «они ушли,
 * не оставив ни одного тела, значит взяли»; из похода — «привёл четверых,
 * увёл четверых, деревня моя».
 * <p>
 * Почему не «дошёл до ратуши»: до неё доходит и тот, кого некому было
 * встретить, и тот, кто перебил стражу, — а это две разные войны. Кровь
 * отряда игрок <b>видит своими глазами</b> и может пролить её прямо сейчас:
 * убей одного — и тебя не возьмут. Слабой колонии нужен выход, который
 * не требует сперва разбогатеть.
 *
 * <h2>Что значит быть взятым</h2>
 * Не исчезает ничего: ни здания, ни жители, ни владелец. Побеждённый
 * <b>платит дань</b> — тем же механизмом, которым деревня платит игроку,
 * только вывернутым наизнанку.
 * <p>
 * <b>{@code Owner} при этом не меняется</b>, хотя дизайн-документ обещает
 * «смену владельца поселения». Колония с чужим владельцем — это колония,
 * в которую игрок больше не может войти, то есть удаление мира другими
 * словами; а решение заказчика прямое: «поражение больно, но обратимо».
 * Смена владельца останется словом документа до тех пор, пока в моде
 * не появится второй игрок, у которого есть что отнимать.
 */
public final class Conquest {

    private Conquest() {
    }

    /**
     * Отряд сделал своё дело: обложить побеждённого данью.
     * <p>
     * Кому платят, решается одним правилом: <b>человеку, если победитель —
     * его колония; самой деревне, если победила деревня</b>. Дань в этом
     * моде хранится опознавателем получателя, и два разных получателя —
     * не путаница, а то же самое отношение с двух сторон.
     *
     * @return {@code true}, если поселение и вправду взято
     */
    public static boolean take(ServerWorld world, SettlementManager manager, Settlement loser,
                               WarParty party, long today) {
        Settlement winner = manager.byId(party.home()).orElse(null);
        if (winner == null) {
            // Победителя не стало, пока отряд стоял. Брать некому: дань
            // без того, кому её носят, — это просто налог в пустоту.
            return false;
        }
        if (loser.owesTributeTo(owedTo(winner), today)) {
            // Уже платит этому же. Второй захват не продлевает срок:
            // иначе сюзерен держал бы вассала вечно, посылая отряд
            // за отрядом, и выхода у побеждённого не было бы вовсе.
            return false;
        }

        UUID owner = owedTo(winner);
        manager.update(loser.id(), state -> state.startTribute(owner, today + Tribute.DAYS));

        VillagePax.LOGGER.info("Поселение {} взято поселением {}: дань {} дней",
                loser.name(), winner.name(), Tribute.DAYS);
        told(world, loser, winner, "villagepax.conquest.taken");
        told(world, winner, loser, "villagepax.conquest.took");
        com.villagepax.sim.life.Chronicle.note(world, loser, "villagepax.chronicle.taken",
                winner.name(), String.valueOf(Tribute.DAYS));
        com.villagepax.sim.life.Chronicle.note(world, winner, "villagepax.chronicle.took",
                loser.name(), String.valueOf(Tribute.DAYS));
        return true;
    }

    /**
     * Ярмо снято: отряд сюзерена лёг под воротами вассала.
     * <p>
     * Дань держится страхом — там же и кончается. Без этого выхода
     * побеждённому оставалось бы только ждать конца срока, а ждать —
     * это не игра. Отбить отряд трудно ровно настолько, насколько
     * трудно было его не отбить в прошлый раз.
     *
     * @return {@code true}, если ярмо и вправду было
     */
    public static boolean free(ServerWorld world, SettlementManager manager, Settlement vassal,
                               UUID overlord, long today) {
        if (!vassal.owesTributeTo(overlord, today)) {
            return false;
        }
        manager.update(vassal.id(), Settlement::stopTribute);
        VillagePax.LOGGER.info("Поселение {} сбросило дань", vassal.name());

        manager.byId(overlord).ifPresentOrElse(
                lord -> told(world, vassal, lord, "villagepax.conquest.freed"),
                () -> told(world, vassal, null, "villagepax.conquest.freed"));
        return true;
    }

    /**
     * Платят человеку или деревне.
     * <p>
     * У колонии игрока получателем дани обязан быть <b>он сам</b>: так
     * устроена дань с первого дня, и второй способ хранить то же самое
     * означал бы два кода платежа вместо одного.
     */
    public static UUID owedTo(Settlement winner) {
        return winner.owner().player().orElse(winner.id());
    }

    /** Платит ли это поселение дань названному поселению. */
    public static boolean underYokeOf(Settlement payer, Settlement lord, long today) {
        return payer.owesTributeTo(owedTo(lord), today);
    }

    /** Кому платит колония, если платит поселению, а не человеку. */
    public static Optional<Settlement> overlordOf(SettlementManager manager, Settlement colony,
                                                  long today) {
        UUID to = colony.tributeTo().orElse(null);
        if (to == null || colony.tributeDaysLeft(today) <= 0) {
            return Optional.empty();
        }
        return manager.byId(to);
    }

    /**
     * Сказать обеим сторонам.
     * <p>
     * Хозяину поселения, а не всем игрокам мира: взятие деревни на другом
     * конце карты никого больше не касается. Автономная деревня в чат
     * не пишет — ей некому.
     */
    private static void told(ServerWorld world, Settlement whose, Settlement other, String key) {
        UUID player = whose.owner().player().orElse(null);
        if (player == null) {
            return;
        }
        ServerPlayerEntity who = world.getServer().getPlayerManager().getPlayer(player);
        if (who == null) {
            return;
        }
        who.sendMessage(Text.translatable(key,
                Text.literal(other == null ? "?" : other.name()),
                Text.literal(String.valueOf(Tribute.DAYS))).formatted(Formatting.GOLD), false);
    }
}
