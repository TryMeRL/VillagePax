package com.villagepax.sim.life;

import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.work.Schedule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Глашатай: в полдень на площади города — новости дня, вслух.
 * <p>
 * Въезд говорит новости тому, кто пришёл; глашатай — тому, кто остался.
 * Игрок, который с утра торгует на площади, иначе так и не узнал бы,
 * что вечером свадьба, а у ратуши ссорятся соседи. Кричат только
 * в городе и столице: у хутора и деревни площадь слишком мала, чтобы
 * держать для этого человека.
 * <p>
 * Раз в день на поселение, и только если на площади кто-то есть: кричать
 * пустой площади незачем, а пришедший позже услышит новости при въезде.
 */
public final class Crier {

    /** С какого часа кричат: полдень. */
    static final long NOON = 6_000L;

    /** Слышно на столько блоков от ратуши. */
    static final double HEARD = 48;

    /** Где и в какой день уже кричали. */
    private static final Map<UUID, Long> CRIED = new HashMap<>();

    private Crier() {
    }

    /** Обход: раз в секунду из часов живых реплик. */
    public static void tick(ServerWorld world, SettlementManager manager,
                            List<? extends PlayerEntity> players, long day) {
        long time = Math.floorMod(world.getTimeOfDay(), Schedule.DAY_LENGTH);
        if (players.isEmpty() || time < NOON || time >= Schedule.LEISURE_START) {
            return;
        }
        for (Settlement settlement : manager.all()) {
            if (settlement.level().ordinal() < SettlementLevel.TOWN.ordinal()
                    || CRIED.getOrDefault(settlement.id(), Long.MIN_VALUE) == day) {
                continue;
            }
            List<PlayerEntity> near = new ArrayList<>();
            for (PlayerEntity player : players) {
                if (!player.isSpectator()
                        && player.getBlockPos().getSquaredDistance(settlement.center()) <= HEARD * HEARD) {
                    near.add(player);
                }
            }
            if (near.isEmpty()) {
                continue;
            }
            CRIED.put(settlement.id(), day);
            List<Text> news = Arrival.news(settlement, day);
            if (news.isEmpty()) {
                continue;
            }
            world.playSound(null, settlement.center(), SoundEvents.BLOCK_BELL_USE,
                    SoundCategory.BLOCKS, 2.0f, 1.0f);
            for (PlayerEntity player : near) {
                player.sendMessage(Text.translatable("villagepax.crier.call", settlement.name())
                        .formatted(Formatting.GOLD), false);
                for (Text line : news) {
                    player.sendMessage(line.copy().formatted(Formatting.GRAY), false);
                }
            }
        }
    }

    /** Забыть всё: сервер встаёт. */
    public static void forget() {
        CRIED.clear();
    }
}
