package com.villagepax.sim.war;

import com.villagepax.VillagePax;
import com.villagepax.core.war.WarParty;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.entity.Looks;
import com.villagepax.sim.Ground;
import com.villagepax.sim.Building;
import com.villagepax.sim.faith.Blessings;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import com.villagepax.sim.work.Schedule;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Набеги: кто их посылает, когда приходят и чем кончаются.
 * <p>
 * Первая половина обещания фазы 3 — «{@code WarParty}, отряды с целью;
 * отряд это данные, энтити спавнятся при подходе к загруженной зоне».
 * Устроено тем же способом, что и обозы, и по той же причине: между
 * деревней и колонией лежат сотни незагруженных чанков, и вести по ним
 * живых мобов нельзя ни дёшево, ни честно.
 * <p>
 * <b>Набег — это ответ, а не погода.</b> Деревня посылает людей не по
 * броску кубика, а когда доверие к игроку упало ниже всякого терпения:
 * ограбленные обозы, убитые жители. Из списка причин войны в
 * дизайн-документе это «агрессия игрока» — единственная, которую мод уже
 * умеет считать честно, потому что считает её сам игрок своими руками.
 * Спор за границу и требование дани приедут вместе с остальной дипломатией.
 * <p>
 * <b>И набег — это выход, а не тупик.</b> Убить пришедших можно, но
 * отношения этим не лечатся: пока доверие ниже терпения, придут снова.
 * Лечится оно единственным способом — подарками и делом, то есть тем же,
 * чем и завоёвывалось. Насилие в этом моде не заменяет дипломатии, и это
 * решение по игре, а не следствие кода.
 * <p>
 * <b>Люди важнее стен.</b> Боец, которому есть кого бить, дерётся; ломать
 * и уносить идёт тот, кому драться не с кем — см. {@link Siege}. Так набег
 * и читается: сперва за людьми, потом за добром.
 * <p>
 * <b>Чего здесь нет.</b> Захвата поселения: ни дани, ни смены владельца.
 * Это другой исход с другими последствиями, и подменять его разорением
 * было бы обманом — колония после набега остаётся колонией игрока,
 * разорённой, но своей.
 */
public final class Raids {

    /** Как часто осматриваются осады. Пять раз в секунду не нужно. */
    private static final int EVERY = 100;

    /**
     * Доверие, ниже которого деревня перестаёт терпеть.
     * <p>
     * Минус сорок — это два ограбленных обоза или один убитый житель
     * с добавкой. Случайно столько не набрать: доверие падает только за
     * то, что игрок сделал руками, и каждый раз ему об этом говорят
     * в чат. Набег не должен быть для игрока новостью о самом себе.
     */
    public static final int PATIENCE_ENDS = -40;

    /**
     * Как далеко деревня посылает отряд.
     * <p>
     * Вдвое дальше, чем обоз: за обидой ходят охотнее, чем за выручкой.
     * Но не бесконечно — деревня с другого конца карты, которую игрок
     * обидел однажды и забыл, не должна присылать людей к его дому:
     * он не свяжет набег ни с чем, а необъяснимое наказание хуже,
     * чем никакое.
     */
    private static final int REACH = 1024;

    /** Сколько дней колония отдыхает между набегами. */
    public static final int COOLDOWN_DAYS = 5;

    /** Больше этого числа бойцов не приходит никогда. */
    public static final int MOST_FIGHTERS = 4;

    /** На сколько ещё падает доверие за каждого следующего бойца. */
    private static final int PER_FIGHTER = 20;

    private Raids() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(Raids::tick);
    }

    /**
     * Сколько бойцов пошлёт деревня при таком доверии.
     * <p>
     * Чистая функция: чем глубже обида, тем больше отряд. Проверяется без
     * запущенной игры, потому что это и есть кривая наказания — единственное
     * в набеге, что можно посчитать неправильно молча.
     */
    public static int fightersFor(int trust) {
        return fightersFor(trust, 0);
    }

    /**
     * Сколько мечей придёт, если у колонии столько-то башен.
     * <p>
     * Каждая стоящая башня убавляет отряд на бойца. Это и есть всё, что
     * делает укрепление, и мерится оно тем, чем игрок его и почувствует:
     * <b>к воротам пришло меньше людей</b>. Не «плюс десять к обороне»,
     * которых не видно, а двое вместо четверых.
     * <p>
     * Но <b>не до нуля</b>. Набег, который не приходит, — это выключенная
     * механика, а не победа: деревня, у которой к игроку счёт, всё равно
     * пошлёт хотя бы одного. Башни делают войну посильной, а не отменяют её.
     */
    public static int fightersFor(int trust, int towers) {
        if (trust > PATIENCE_ENDS) {
            return 0;
        }
        int over = PATIENCE_ENDS - trust;
        int angry = Math.min(MOST_FIGHTERS, 1 + over / PER_FIGHTER);
        return Math.max(1, angry - Math.max(0, towers));
    }

    /**
     * Сколько у поселения готовых башен.
     * <p>
     * Считаются только <b>достроенные</b>: половина стены мечей не убавляет,
     * и обещать иначе значило бы дать игроку оборону, которой у него нет.
     * Что такое башня, решают данные: здание, в котором работает стража.
     */
    public static int towersOf(Settlement colony) {
        int towers = 0;
        for (Building building : colony.buildings()) {
            if (building.isOperational()
                    && BuildingTypes.employs(building.type(), Villages.GUARD)) {
                towers++;
            }
        }
        return towers;
    }

    /**
     * На скольких бойцов убавляется пришедший отряд.
     * <p>
     * Камень и бог считаются вместе и упираются в одно правило — «никогда
     * до нуля». Башня и благословение дозора делают войну посильной,
     * а не отменяют её, и складывать их поэтому можно без опаски: предел
     * стоит не здесь, а в {@link #fightersFor}.
     * <p>
     * Названной функцией, а не выражением на месте, чтобы проверка
     * спрашивала ровно то же, что спрашивает набег. Выражение в теле цикла
     * проверялось бы повтором той же арифметики в тесте — то есть никак.
     */
    public static int defence(Settlement colony, long today) {
        return towersOf(colony) + Blessings.watchBonus(colony, today);
    }

    /**
     * Суточное решение деревни: не пора ли послать людей.
     * <p>
     * Зовётся оттуда же, откуда деревня решает всё остальное, — на смене
     * дня и ровно один раз за день.
     */
    public static void newDay(ServerWorld world, SettlementManager manager, Settlement village) {
        sendIfDue(world, manager, village, Schedule.dayOf(world.getTimeOfDay()));
    }

    /**
     * То же, но с днём числом: иначе «раз в пять дней» не проверить.
     * <p>
     * Тот же урок, что с обозами: тест не может прождать пятеро игровых
     * суток, а несколько вызовов в одном тике — это один и тот же день.
     */
    public static void sendIfDue(ServerWorld world, SettlementManager manager,
                                 Settlement village, long today) {
        if (!village.owner().isAutonomous()) {
            // Колония игрока набегов не устраивает: за неё решает он сам,
            // а «пошли своих на соседа» — это уже приказ, которого в моде нет.
            return;
        }
        if (village.atTruce(today)) {
            // Деревня хоронит своих или взяла откуп. Обида при этом никуда
            // не делась — просто эти дни она никуда не идёт (см. Peace).
            return;
        }

        for (UUID player : List.copyOf(village.reputation().keySet())) {
            int trust = village.reputationOf(player);
            if (fightersFor(trust) <= 0) {
                continue;
            }

            Settlement colony = colonyOf(manager, player);
            if (colony == null || colony.siege().isPresent()) {
                continue;
            }

            // Размер отряда считается ПОСЛЕ того, как нашлась колония:
            // он зависит и от обиды, и от того, что игрок построил.
            int fighters = fightersFor(trust, defence(colony, today));
            if (colony.center().getSquaredDistance(village.center()) > (double) REACH * REACH) {
                continue;
            }
            if (colony.lastRaid() != Settlement.UNSEEN_DAY
                    && today - colony.lastRaid() < COOLDOWN_DAYS) {
                continue;
            }

            BlockPos musters = musterSpot(world, colony);
            if (musters == null) {
                // Некуда встать: чанк не загружен или у колонии нет твёрдой
                // земли по кругу. Придут в другой раз — обида не проходит.
                continue;
            }

            WarParty party = new WarParty(UUID.randomUUID(), village.id(), village.culture(),
                    musters, fighters, today + 1, today + 2);
            manager.update(colony.id(), state -> state.besiege(party, today));

            warn(world, colony, village, fighters);
            VillagePax.LOGGER.info("Деревня {} посылает {} бойцов к {}: доверие {}",
                    village.name(), fighters, colony.name(), trust);
            return;
        }
    }

    /**
     * Каждые сто тиков: поставить телом тех, кого видно, и проводить
     * тех, чей срок вышел.
     */
    static void tick(ServerWorld world) {
        if (world.getTime() % EVERY != 0) {
            return;
        }
        watch(world, SettlementManager.get(world), Schedule.dayOf(world.getTimeOfDay()));
    }

    /**
     * То же, но с днём числом — и потому проверяемое.
     * <p>
     * Третий раз тот же приём (обозы, набеги, подарки), и он себя оправдал:
     * всё, что решается «в такой-то день», принимает день снаружи. Иначе
     * проверка вынуждена ждать смены суток, а игровой тест ждать не умеет.
     */
    public static void watch(ServerWorld world, SettlementManager manager, long today) {
        for (Settlement settlement : List.copyOf(manager.all())) {
            WarParty party = settlement.siege().orElse(null);
            if (party == null) {
                continue;
            }
            if (party.isOver(today)) {
                withdraw(world, manager, settlement, party);
                continue;
            }
            if (party.hasArrived(today) && world.isChunkLoaded(party.musters())) {
                muster(world, manager, settlement, party);
                // И подмога — тем же порядком и в тот же миг: союзники
                // приходят к воротам, а не выходят из них.
                Allies.callUp(world, manager, settlement, party);
                ruin(world, manager, settlement, party);
            }
        }
    }

    /**
     * Поставить телами тех, кого ещё нет.
     * <p>
     * По одному телу на живого бойца. Тела — куклы, как и торговец обоза:
     * у них нет ни записи жителя, ни поселения, и стратегия их не видит.
     * Разница одна и вся в ней: этих послали воевать.
     */
    private static void muster(ServerWorld world, SettlementManager manager, Settlement colony,
                               WarParty party) {
        List<CitizenEntity> standing = bodiesOf(world, party);
        boolean first = standing.isEmpty();

        for (int number = standing.size(); number < party.fighters(); number++) {
            BlockPos where = Ground.spotNear(world, party.musters(), 0, 3);
            CitizenEntity fighter = CitizenSpawner.spawnPuppet(world,
                    where == null ? party.musters() : where);
            if (fighter == null) {
                return;
            }

            fighter.linkRaid(colony.id(), party.id());
            // Налётчик одет стражем своего народа: игрок должен видеть,
            // кто пришёл, ещё до того, как прочтёт имя над головой.
            fighter.setLook(Looks.puppet(party.culture(), "guard"));
            fighter.setCustomName(Text.translatable("villagepax.raid.fighter",
                    Text.translatable("villagepax.culture." + party.culture().getPath())));
            fighter.setCustomNameVisible(true);
            // Оружие в руках — и это не только вид: модификатор меча
            // считается в силу удара, как у любого моба с мечом.
            fighter.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }

        if (first) {
            announce(world, colony, "villagepax.raid.here", Formatting.RED);
        }
    }

    /**
     * Разорить дом, если бойцу некого бить.
     * <p>
     * Люди <b>важнее стен</b>: боец, у которого есть цель, дерётся, а
     * ломать идёт тот, кому драться не с кем. Так набег и читается —
     * сперва за людьми, потом за добром.
     */
    private static void ruin(ServerWorld world, SettlementManager manager, Settlement colony,
                             WarParty party) {
        WarParty current = party;
        for (CitizenEntity fighter : bodiesOf(world, current)) {
            if (!current.canWreck()) {
                return;
            }
            if (fighter.getTarget() != null && fighter.getTarget().isAlive()) {
                continue;
            }
            if (Siege.wreck(world, manager, colony, fighter)) {
                current = current.withWrecked(current.wrecked() + 1);
                WarParty saved = current;
                manager.update(colony.id(), state -> state.updateSiege(saved));
            }
        }
    }

    /** Тела этого отряда, какие есть в мире. */
    public static List<CitizenEntity> bodiesOf(ServerWorld world, WarParty party) {
        Box around = new Box(party.musters()).expand(64);
        // Только налётчики: к тому же отряду привязаны и союзники, пришедшие
        // ему навстречу, а считать их своими значило бы недосчитаться
        // мечей у ворот и уводить домой чужих людей.
        return new ArrayList<>(world.getEntitiesByClass(CitizenEntity.class, around,
                alive -> alive.isRaider() && party.id().equals(alive.raidId())));
    }

    /**
     * Боец пал.
     * <p>
     * <b>Отношений это не меняет</b>, и это осознанно: пришедшие пришли
     * по делу, и то, что они его не сделали, деревню не примиряет. Мириться
     * игроку придётся тем же, чем ссорился, — своими руками.
     * <p>
     * Но воевать ей какое-то время нечем. Каждый павший стоит деревне
     * дней траура, и отбитый набег в четыре меча — это две недели тишины.
     * Разница с примирением та, что тишина кончается, а обида — нет:
     * доверие как было ниже терпения, так и осталось. Это и есть ответ
     * на «я отбился, и что изменилось»: изменилось время, которое у игрока
     * теперь есть.
     */
    public static void fell(ServerWorld world, CitizenEntity body) {
        fell(world, body, Schedule.dayOf(world.getTimeOfDay()));
    }

    /** То же, но с днём числом: траур считается в днях, а тест их не ждёт. */
    public static void fell(ServerWorld world, CitizenEntity body, long today) {
        SettlementManager manager = SettlementManager.get(world);
        Settlement colony = body.raidHost().flatMap(manager::byId).orElse(null);
        UUID raidId = body.raidId();
        if (colony == null || raidId == null) {
            return;
        }

        WarParty party = colony.siege().filter(one -> one.id().equals(raidId)).orElse(null);
        if (party == null) {
            return;
        }

        WarParty thinner = party.withFighters(party.fighters() - 1);
        manager.update(colony.id(), state -> state.updateSiege(thinner));
        manager.update(party.home(), state -> state.restFor(today, Peace.MOURNING_DAYS));

        if (thinner.fighters() <= 0) {
            announce(world, colony, "villagepax.raid.repelled", Formatting.GREEN);
            manager.byId(party.home()).ifPresent(home -> tell(world, colony,
                    Text.translatable("villagepax.raid.mourning", Text.literal(home.name()),
                                    Text.literal(String.valueOf(home.truceDaysLeft(today))))
                            .formatted(Formatting.GRAY)));
            VillagePax.LOGGER.info("Набег на {} отбит", colony.name());
        }
    }

    /**
     * Отряд уходит без добычи: за него заплатили.
     * <p>
     * Пришедшие не грабят и не гибнут — они разворачиваются. Добыча
     * не берётся намеренно: плата и есть их добыча, и брать дважды
     * значило бы, что откуп ничего не стоит.
     */
    public static boolean callOff(ServerWorld world, SettlementManager manager, UUID player,
                                  UUID village) {
        Settlement colony = colonyOf(manager, player);
        if (colony == null) {
            return false;
        }
        WarParty party = colony.siege().filter(one -> one.home().equals(village)).orElse(null);
        if (party == null) {
            return false;
        }

        bodiesOf(world, party).forEach(CitizenEntity::discard);
        manager.update(colony.id(), Settlement::liftSiege);
        announce(world, colony, "villagepax.raid.bought_off", Formatting.GREEN);
        return true;
    }

    /** Отряд уходит: тела убрать, запись снять. */
    private static void withdraw(ServerWorld world, SettlementManager manager, Settlement colony,
                                 WarParty party) {
        List<CitizenEntity> left = bodiesOf(world, party);

        if (!left.isEmpty()) {
            // Уцелевшие уходят не с пустыми руками — и увозят добычу
            // домой, если дом загружен. Перебитый отряд не уносит ничего.
            List<ItemStack> loot = Siege.plunder(world, colony, left.size());
            manager.byId(party.home()).ifPresent(home -> Siege.bringHome(world, home, loot));
        }

        if (left.isEmpty()) {
            // Отряд ушёл не сам — его положили. Деревня это помнит, и с этого
            // дня у игрока есть право требовать с неё дань: не «я сильнее
            // вообще», а «твои люди лежат под моими воротами».
            manager.update(party.home(), home -> home.beaten(party.leavesOn()));
        }

        left.forEach(CitizenEntity::discard);
        Allies.dismiss(world, party);
        manager.update(colony.id(), Settlement::liftSiege);

        if (!left.isEmpty()) {
            // Молча уходят только те, кого уже перебили: об этом игроку
            // сказали, когда пал последний.
            announce(world, colony, "villagepax.raid.left", Formatting.GRAY);
        }
    }

    /** Колония этого игрока — та, к которой и пойдут. */
    private static Settlement colonyOf(SettlementManager manager, UUID player) {
        for (Settlement candidate : manager.all()) {
            if (candidate.owner().isOwnedBy(player)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Где отряд соберётся: у края колонии, а не посреди неё.
     * <p>
     * Десять шагов от ратуши — это «пришли и встали», а не «возникли
     * в спальне». Игрок должен успеть их увидеть и выйти навстречу.
     */
    private static BlockPos musterSpot(ServerWorld world, Settlement colony) {
        return Ground.spotNear(world, colony.center(), 10, 16);
    }

    private static void warn(ServerWorld world, Settlement colony, Settlement village,
                             int fighters) {
        colony.owner().player().ifPresent(owner -> {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
            if (player != null) {
                player.sendMessage(Text.translatable("villagepax.raid.coming",
                                Text.literal(village.name()),
                                Text.literal(String.valueOf(fighters)))
                        .formatted(Formatting.RED), false);
            }
        });
    }

    private static void announce(ServerWorld world, Settlement colony, String key,
                                 Formatting colour) {
        tell(world, colony, Text.translatable(key, Text.literal(colony.name())).formatted(colour));
    }

    /** Сказать хозяину колонии, если он в сети. */
    private static void tell(ServerWorld world, Settlement colony, Text message) {
        colony.owner().player().ifPresent(owner -> {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
            if (player != null) {
                player.sendMessage(message, false);
            }
        });
    }
}
