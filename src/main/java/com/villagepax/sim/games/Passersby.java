package com.villagepax.sim.games;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.work.Schedule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Живой соперник на улице: проигравший окликает «отыграться», выигравший
 * хвалится.
 * <p>
 * Тот, кто помнит, что ты его обыграл, и зовёт отыграться, — это и есть
 * живой соперник, а не автомат за столом. Окликают не только вечером:
 * «Сочтёмся сегодня вечером!» говорится утром, — а во сне не окликают.
 * Не чаще раза в пять минут на жителя: иначе оклик стал бы шумом улицы.
 */
public final class Passersby {

    /** В скольких блоках окликают. */
    static final double HAIL = 6;

    /** Не чаще раза в столько тиков на жителя: пять минут. */
    static final int HAIL_EVERY = 6_000;

    /** Когда житель окликал последний раз. Только сервер: оклик — не память. */
    private static final Map<UUID, Long> HAILED = new HashMap<>();

    private Passersby() {
    }

    /** Окликнуть проходящих, если есть за что. */
    public static void tick(ServerWorld world, SettlementManager manager, long day, long timeOfDay,
                            List<? extends PlayerEntity> players) {
        if (Schedule.at(timeOfDay) == Schedule.SLEEP) {
            return;
        }
        GamesLedger ledger = GamesLedger.get(world);
        long now = world.getTime();
        for (PlayerEntity player : players) {
            if (player.isSpectator()) {
                continue;
            }
            for (CitizenEntity body : world.getEntitiesByClass(CitizenEntity.class,
                    player.getBoundingBox().expand(HAIL), body -> body.squaredDistanceTo(player) <= HAIL * HAIL)) {
                Citizen citizen = body.data(world).orElse(null);
                if (citizen == null || Ages.isChild(citizen) || Bouts.rivalOf(citizen.id()).isPresent()) {
                    continue;
                }
                Rivalry record = ledger.rivalry(citizen.id(), player.getUuid());
                // Обиженный не окликает: он сегодня с этим игроком не играет.
                if (record.streak() == 0 || record.sulks(day)) {
                    continue;
                }
                Long last = HAILED.get(citizen.id());
                if (last != null && now - last < HAIL_EVERY) {
                    continue;
                }
                Say call = record.streak() < 0 ? Say.REMATCH : Say.BOAST;
                if (Lines.say(body, citizen, call, player.getName())) {
                    HAILED.put(citizen.id(), now);
                }
            }
        }
    }
}
