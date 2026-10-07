package com.villagepax.sim.life;

import com.villagepax.core.festival.Festival;
import com.villagepax.sim.Milestones;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.festival.FestivalCalendar;
import com.villagepax.sim.festival.FestivalDay;
import com.villagepax.core.festival.Festivals;
import com.villagepax.sim.trade.MarketDay;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Въезд в поселение: название крупно, ступень и доверие под ним, а в чат —
 * новости дня.
 * <p>
 * Прежде граница деревни ничем не отмечалась: игрок узнавал, где он, только
 * щёлкнув по жителю, а о рынке, празднике или свадьбе — случайно. Теперь,
 * переступив границу, он видит, куда пришёл и кто он здесь, а в чате — то,
 * ради чего стоит задержаться: сегодня рынок, завтра праздник, вечером
 * свадьба, у ворот враг, деревня вот-вот вырастет.
 * <p>
 * Не чаще раза в {@link #AGAIN} тиков на поселение: ходить вдоль границы
 * туда-обратно не значит въезжать заново.
 */
public final class Arrival {

    /** Повторно о той же деревне — не раньше чем через столько тиков: пять минут. */
    static final int AGAIN = 6_000;

    /** Где игрок был на прошлом обходе: опознаватель поселения. */
    private static final Map<UUID, UUID> WHERE = new HashMap<>();

    /** Когда о поселении говорили этому игроку: «игрок/поселение» → тик. */
    private static final Map<String, Long> TOLD = new HashMap<>();

    private Arrival() {
    }

    /** Обход: раз в секунду из часов живых реплик. */
    public static void tick(ServerWorld world, SettlementManager manager,
                            List<? extends PlayerEntity> players, long day) {
        long now = world.getTime();
        for (PlayerEntity player : players) {
            if (player.isSpectator()) {
                continue;
            }
            Settlement here = manager.at(player.getBlockPos()).orElse(null);
            UUID was = WHERE.get(player.getUuid());
            if (here == null) {
                WHERE.remove(player.getUuid());
                continue;
            }
            if (here.id().equals(was)) {
                continue;
            }
            WHERE.put(player.getUuid(), here.id());
            String key = player.getUuid() + "/" + here.id();
            Long last = TOLD.get(key);
            if (last != null && now - last < AGAIN) {
                continue;
            }
            TOLD.put(key, now);
            greet(player, here, day);
        }
    }

    /** Сказать въехавшему: крупно — куда, мельче — кто он здесь, в чат — новости. */
    static void greet(PlayerEntity player, Settlement here, long day) {
        Text title = Text.literal(here.name()).formatted(Formatting.GOLD);
        Text subtitle = subtitle(player, here);
        if (player instanceof ServerPlayerEntity server) {
            server.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 50, 20));
            server.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
            server.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
        for (Text line : news(here, day)) {
            player.sendMessage(line.copy().formatted(Formatting.GRAY), false);
        }
    }

    /** Ступень, народ, сколько жителей — и кто игрок для этой деревни. */
    static Text subtitle(PlayerEntity player, Settlement here) {
        Text level = Text.translatable(Milestones.levelKey(here.level()));
        if (here.owner().isOwnedBy(player.getUuid())) {
            return Text.translatable("villagepax.arrival.own", level, here.population());
        }
        return Text.translatable("villagepax.arrival.subtitle", level,
                Text.translatable("villagepax.culture." + here.culture().getPath()),
                here.population(),
                Text.translatable(Standing.of(here.reputationOf(player.getUuid())).displayKey()));
    }

    /**
     * Новости поселения на этот день — то, ради чего стоит задержаться.
     * Чистое правило: мира не спрашивает, проверяется без игры.
     */
    public static List<Text> news(Settlement here, long day) {
        List<Text> lines = new ArrayList<>();
        if (here.siege().isPresent()) {
            lines.add(Text.translatable("villagepax.arrival.siege"));
        }
        if (MarketDay.isOn(here, day)) {
            lines.add(Text.translatable("villagepax.arrival.market_today"));
        } else if (here.owner().isAutonomous()
                && here.level().ordinal() >= com.villagepax.sim.SettlementLevel.VILLAGE.ordinal()
                && MarketDay.daysUntil(here.culture(), day) == 1) {
            lines.add(Text.translatable("villagepax.arrival.market_tomorrow"));
        }
        Optional<Festival> festival = Festivals.of(here.culture());
        if (FestivalDay.isOn(here, day)) {
            lines.add(Text.translatable("villagepax.arrival.festival_today"));
        } else if (festival.isPresent()) {
            int days = FestivalCalendar.daysUntil(day, festival.get().moonPhase());
            if (days > 0 && days <= 3) {
                lines.add(Text.translatable("villagepax.arrival.festival_in", days));
            }
        }
        Weddings.tonight(here, day).ifPresent(couple -> lines.add(Text.translatable(
                "villagepax.arrival.wedding",
                here.citizen(couple[0]).map(c -> c.firstName()).orElse("?"),
                here.citizen(couple[1]).map(c -> c.firstName()).orElse("?"))));
        if (here.owner().isAutonomous() && !here.level().isMax()) {
            lines.add(Text.translatable("villagepax.arrival.growth", here.population(),
                    here.maxCitizens(), Text.translatable(Milestones.levelKey(here.level().next()))));
        }
        return lines;
    }

    /** Забыть всё: сервер встаёт. */
    public static void forget() {
        WHERE.clear();
        TOLD.clear();
    }
}
