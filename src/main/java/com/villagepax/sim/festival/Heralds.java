package com.villagepax.sim.festival;

import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Villages;
import com.villagepax.sim.work.Schedule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Зов праздника: на рассвете и накануне — в чат, у ярмарки — рогом,
 * а в будни затейник зазывает прохожих сам.
 * <p>
 * Праздник, о котором не сказано, для игрока не случился: деревня гуляет
 * в трёхстах блоках, а он узнаёт об этом через неделю, заглянув туда
 * по делу. Зов идёт тем, кто рядом (160 блоков), и хозяину колонии,
 * где бы он ни был: свой праздник пропускать обидно.
 */
public final class Heralds {

    /** Кто слышит зов деревни: в этом круге от её середины. */
    public static final double REACH = 160.0;

    /** Как часто затейник зазывает, в тиках: раз в минуту. */
    private static final long CALL_EVERY = 1200L;

    /** Кого из прохожих зазывают. */
    private static final double BARKER_REACH = 8.0;

    private static final int WEEKDAY_LINES = 6;
    private static final int FESTIVAL_LINES = 4;

    /** Когда затейник зазывал последний раз — чтобы не чаще раза в минуту. */
    private static final Map<UUID, Long> LAST_CALL = new HashMap<>();

    private Heralds() {
    }

    /**
     * Что сказать поселению в этот день: «сегодня праздник», «завтра праздник»
     * или ничего. Праздник, которого не будет — нет ярмарки, затейника или
     * мира, — не объявляется ни в день, ни накануне.
     */
    public static Optional<String> lineFor(Settlement settlement, long day) {
        if (FestivalDay.isOn(settlement, day)) {
            return Optional.of("villagepax.festival.today");
        }
        if (FestivalDay.isOn(settlement, day + 1)) {
            return Optional.of("villagepax.festival.tomorrow");
        }
        return Optional.empty();
    }

    /** Зов на смене дня: строка тем, кто слышит, и рог у сердца ярмарки. */
    public static void dawn(ServerWorld world, Settlement settlement, long day) {
        String key = lineFor(settlement, day).orElse(null);
        Festival festival = Festivals.of(settlement.culture()).orElse(null);
        if (key == null || festival == null) {
            return;
        }
        Text line = Text.translatable(key, settlement.name(), Text.translatable(festival.name()))
                .formatted(Formatting.GOLD);
        for (ServerPlayerEntity player : listeners(world, settlement)) {
            player.sendMessage(line, false);
        }
        if (key.endsWith("today")) {
            Fairs.of(settlement).map(Fair::heart).filter(world::isChunkLoaded).ifPresent(heart ->
                    world.playSound(null, heart.up(2), SoundEvents.GOAT_HORN_SOUNDS.get(0).value(),
                            SoundCategory.RECORDS, 4.0f, 1.0f));
        }
    }

    /** Кто слышит зов: все в круге от середины поселения и хозяин колонии. */
    static Set<ServerPlayerEntity> listeners(ServerWorld world, Settlement settlement) {
        Set<ServerPlayerEntity> heard = new LinkedHashSet<>();
        BlockPos centre = settlement.center();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.getBlockPos().getSquaredDistance(centre) <= REACH * REACH) {
                heard.add(player);
            }
        }
        settlement.owner().player()
                .map(owner -> world.getServer().getPlayerManager().getPlayer(owner))
                .ifPresent(heard::add);
        return heard;
    }

    /**
     * Реплика зазывалы.
     * <p>
     * В праздник — зовёт играть; в будни — считает дни до праздника.
     * Выбор по минуте, чтобы подряд не повторялось.
     *
     * @param daysUntil сколько дней до праздника; ноль — праздник сегодня
     * @param minute    номер минуты мира
     */
    public static String barkerLine(int daysUntil, long minute) {
        if (daysUntil == 0) {
            return "villagepax.entertainer.today." + Math.floorMod(minute, FESTIVAL_LINES);
        }
        return "villagepax.entertainer.call." + Math.floorMod(minute, WEEKDAY_LINES);
    }

    /**
     * Затейник зазывает ближайшего прохожего — раз в минуту, не чаще.
     * <p>
     * Строка в чат, а не над головой: подпись над жителем занята его именем,
     * и реплика, сменяющая имя, путала бы, кто перед тобой.
     */
    public static void barker(ServerWorld world, Settlement settlement, Citizen citizen,
                              CitizenEntity body) {
        long now = world.getTime();
        Long last = LAST_CALL.get(citizen.id());
        if (last != null && now - last < CALL_EVERY) {
            return;
        }
        PlayerEntity passer = world.getClosestPlayer(body.getX(), body.getY(), body.getZ(),
                BARKER_REACH, player -> !player.isSpectator());
        Festival festival = Festivals.of(settlement.culture()).orElse(null);
        if (passer == null || festival == null) {
            return;
        }
        long day = Schedule.dayOf(world.getTimeOfDay());
        int days = 0;
        if (!FestivalDay.isOn(settlement, day)) {
            days = FestivalCalendar.daysUntil(day, festival.moonPhase());
            if (days == 0) {
                // Сегодня праздника не вышло (набег) — следующий через месяц.
                days = FestivalCalendar.LUNAR_MONTH;
            }
            if (!FestivalDay.isOn(settlement, day + days)) {
                // Звать некуда: без ярмарки или затейника праздника не будет.
                return;
            }
        }
        LAST_CALL.put(citizen.id(), now);
        String key = barkerLine(days, now / CALL_EVERY);
        Text festivalName = Text.translatable(festival.name());
        Text line = days == 0 ? Text.translatable(key, festivalName)
                : Text.translatable(key, festivalName, days);
        passer.sendMessage(Text.translatable("villagepax.entertainer.says", title(settlement, citizen),
                citizen.fullName(), line), false);
        body.greet();
    }

    /** Как народ зовёт затейника: жонглёр, скальд, менестрель — или общим именем. */
    public static Text title(Settlement settlement, Citizen citizen) {
        return Text.translatable(titleKey(settlement));
    }

    /** Ключ имени затейника у народа поселения. */
    public static String titleKey(Settlement settlement) {
        Culture culture = CultureManager.get(settlement.culture());
        String key = culture == null ? null : culture.titleOf(Villages.ENTERTAINER).orElse(null);
        return key != null ? key : "villagepax.profession.entertainer";
    }
}
