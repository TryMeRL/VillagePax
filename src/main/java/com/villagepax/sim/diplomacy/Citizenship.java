package com.villagepax.sim.diplomacy;

import com.mojang.serialization.Codec;
import com.villagepax.VillagePax;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.building.BuildingType;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.Named;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Гражданство в чужой деревне: свой дом у чужого народа.
 * <p>
 * Последний невыплаченный пункт фазы 3 и отдельная ветка игры —
 * дизайн-документ называет её прямо: «может ли игрок жить внутри чужой
 * деревни как гражданин… это отдельная ветка геймплея, <b>ближе всего
 * к оригинальному Millénaire</b>». Решение заказчика: «гражданство
 * в чужой деревне (работать на них, получить дом, дорасти
 * до старейшины) — реализуем в фазе 3».
 *
 * <h2>Работать на них уже можно</h2>
 * Просьбы старейшины, поручения ремесленников, подарки — всё это в моде
 * есть с фазы 2, и это и есть «работать на них». Не хватало второго
 * шага: чтобы за работу давали не только цену получше.
 *
 * <h2>Свой дом</h2>
 * Другу деревня отводит дом, и <b>его кровати перестают раздаваться
 * жителям</b>. Иначе «свой дом» означал бы дом, в который в первую же
 * ночь ляжет чужой пахарь.
 * <p>
 * <b>И его сундук не входит в склад деревни.</b> Правило снималось
 * однажды как заглушка: жильё тогда сундуков не имело вовсе,
 * и запрещать было нечего. Дома перестроены на след 7x7, сундук
 * в них появился — вернулось и правило. Иначе «свой сундук» означал бы
 * сундук, из которого к утру всё унесёт курьер.
 * <p>
 * <b>Запись лежит на здании, а не на поселении.</b> Дом с именем игрока
 * и есть гражданство: отдельного поля «граждане» не нужно нигде,
 * а потерять дом и остаться гражданином невозможно по устройству.
 *
 * <h2>Дорасти до старейшины</h2>
 * Почётный житель размечает деревне стройку и двигает её очередь —
 * в её же пульте, который до сих пор отвечал ему «не ваше поселение».
 * Больше ничего: ни налогов, ни ремёсел, ни жертв. Он не хозяин,
 * он свой.
 *
 * <h2>И отбирают</h2>
 * Гражданство держится дружбой, а не записью: упало доверие ниже
 * дружбы — дом отобрали. Так же устроен союз, и по той же причине:
 * купить один раз и забыть нельзя ничего.
 */
public final class Citizenship {

    /** С какой ступени доверия деревня пускает жить. */
    public static final Standing NEEDS = Standing.FRIEND;

    /**
     * С какой ступени гражданин говорит на равных со старейшиной.
     * <p>
     * Дизайн-документ обещает «дорасти до старейшины», и вот куда
     * дорастают: почётный житель размечает деревне стройку и двигает
     * её очередь. Ни налогов, ни ремёсел, ни жертв — он не хозяин,
     * он свой.
     */
    public static final Standing ELDER = Standing.HONOURED;

    private Citizenship() {
    }

    /** Чем кончится просьба — до того, как она высказана. */
    public enum Verdict implements Named {

        /** Пустят. */
        YES("yes"),

        /** Это не деревня народа: в своей колонии игрок и так хозяин. */
        NOT_A_VILLAGE("not_a_village"),

        /** Доверия мало: чужаку дома не дают. */
        NOT_A_FRIEND("not_a_friend"),

        /** Свободных домов нет — все заселены своими. */
        NO_ROOM("no_room"),

        /** Дом уже есть. */
        ALREADY("already");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        public static final Codec<Verdict> CODEC = EnumCodecs.of(values(), "приговор гражданству");

        public boolean ready() {
            return this == YES;
        }

        /** Чем объяснить отказ. У согласия причины нет. */
        public Optional<String> reasonKey() {
            return this == YES ? Optional.empty()
                    : Optional.of("villagepax.citizenship." + id);
        }
    }

    /** Приговор просьбе о доме. */
    public static Verdict judge(Settlement village, UUID player) {
        if (village == null || !village.owner().isAutonomous()) {
            return Verdict.NOT_A_VILLAGE;
        }
        if (houseOf(village, player).isPresent()) {
            return Verdict.ALREADY;
        }
        if (village.reputationOf(player) < NEEDS.from()) {
            return Verdict.NOT_A_FRIEND;
        }
        return spare(village).isPresent() ? Verdict.YES : Verdict.NO_ROOM;
    }

    /**
     * Попроситься жить.
     * <p>
     * Отводится <b>готовый дом</b>, а не строится новый: деревня решает
     * свою стройку сама, и заказ дома от гостя был бы правом, которого
     * у него нет. Нет свободного — приходи, когда построят.
     */
    public static Verdict settle(ServerWorld world, SettlementManager manager,
                                 Settlement village, ServerPlayerEntity player) {
        Verdict verdict = judge(village, player.getUuid());
        if (!verdict.ready()) {
            return verdict;
        }

        UUID home = spare(village).orElseThrow().id();
        manager.update(village.id(), state ->
                state.building(home).ifPresent(house -> house.setResident(player.getUuid())));

        VillagePax.LOGGER.info("Деревня {} отвела дом игроку {}", village.name(),
                player.getGameProfile().getName());
        player.sendMessage(Text.translatable("villagepax.citizenship.granted",
                Text.literal(village.name())).formatted(Formatting.GREEN), false);
        return Verdict.YES;
    }

    /**
     * Дом этого игрока в этой деревне, если он есть.
     * <p>
     * Гражданство спрашивается отсюда и только отсюда: другого места,
     * где оно записано, нет, и разойтись двум ответам не о чем.
     */
    public static Optional<Building> houseOf(Settlement village, UUID player) {
        for (Building building : village.buildings()) {
            if (building.resident().filter(player::equals).isPresent()) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

    /** Гражданин ли он здесь. */
    public static boolean isCitizen(Settlement village, UUID player) {
        return houseOf(village, player).isPresent();
    }

    /**
     * Дорос ли он до старейшины.
     * <p>
     * Дом и почёт вместе, а не по отдельности: почётный гость, у которого
     * здесь нет дома, деревне всё ещё гость, а гражданин без почёта —
     * житель, но не голос. «Дорасти до старейшины» — это про обе
     * половины сразу.
     */
    public static boolean isElder(Settlement village, UUID player) {
        return isCitizen(village, player)
                && village.reputationOf(player) >= ELDER.from();
    }

    /**
     * Суточный ход: деревня смотрит, друг ли ещё её жилец.
     * <p>
     * <b>Пайка здесь нет, и это решение, а не пропуск.</b> Деревня
     * могла бы класть гражданину еду в сундук его дома — но дома
     * в этом моде без сундуков, а в норманнской избе нет и свободной
     * клетки, чтобы его поставить: две кровати, очаг, место убранства
     * и лаз на второй этаж занимают весь пол. Дать паёк там, где
     * сундук помещается, и не дать там, где нет, значило бы сделать
     * гражданство у майя лучше, чем у норманнов, — а народы в этом моде
     * отличаются обликом, а не выгодой.
     */
    public static void newDay(ServerWorld world, SettlementManager manager, Settlement village) {
        if (!village.owner().isAutonomous()) {
            return;
        }
        for (Building house : List.copyOf(village.buildings())) {
            UUID resident = house.resident().orElse(null);
            if (resident == null) {
                continue;
            }
            if (village.reputationOf(resident) < NEEDS.from()) {
                evict(world, manager, village, house, resident);
            }
        }
    }

    /**
     * Дом отбирают.
     * <p>
     * Без объяснения это выглядело бы поломкой: игрок пришёл ночевать,
     * а кровать чужая. Поэтому в чат — и с причиной.
     */
    public static void evict(ServerWorld world, SettlementManager manager, Settlement village,
                             Building house, UUID resident) {
        manager.update(village.id(), state ->
                state.building(house.id()).ifPresent(known -> known.setResident(null)));

        ServerPlayerEntity who = world.getServer().getPlayerManager().getPlayer(resident);
        if (who != null) {
            who.sendMessage(Text.translatable("villagepax.citizenship.evicted",
                    Text.literal(village.name())).formatted(Formatting.RED), false);
        }
        VillagePax.LOGGER.info("Деревня {} отобрала дом: доверие упало", village.name());
    }

    /**
     * Свободный дом: жильё, достроенное, без жильца и <b>без сундука
     * с чужим добром</b>.
     * <p>
     * Роль «жильё» спрашивается у типа здания, а не у имени: народ вправе
     * звать дом избой, хижиной или домом какао, и узнавать его надо
     * по тому, чем он является.
     */
    public static Optional<Building> spare(Settlement village) {
        for (Building building : village.buildings()) {
            if (!building.isOperational() || building.resident().isPresent()) {
                continue;
            }
            BuildingType type = BuildingTypes.get(building.type()).orElse(null);
            if (type != null && type.role() == BuildingType.Role.HOME) {
                return Optional.of(building);
            }
        }
        return Optional.empty();
    }

}
