package com.villagepax.gametest;

import com.villagepax.core.culture.CultureManager;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.item.ModItems;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.games.Games;
import com.villagepax.sim.games.GamesLedger;
import com.villagepax.sim.games.HideAndSeek;
import com.villagepax.sim.life.Nature;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkTicker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Прятки с детьми: днём ребёнок зовёт сам, дети прячутся, игрок ищет.
 * <p>
 * Прятки, в которые зовут сами жители, и подарок тому, кто нашёл всех, —
 * это Animal Crossing. Нашёл всех — мать дарит сласть своего народа
 * и два медяка; время вышло — дети выходят сами: «Не нашёл!». Подпись
 * над головой у спрятавшегося снята — имя видно сквозь стены и выдало бы
 * его, — а после пряток любого рода она у всех на месте.
 */
public class HideAndSeekTests extends GameTestSupport {

    /** Утро третьего дня: взрослые работают, дети свободны. */
    private static final long DAY = 3;
    private static final long MORNING = 3_000;

    /** Двор для пряток: деревня на лугу, мать и трое её детей, игрок рядом. */
    private record Yard(Meadow ground, Citizen mother, List<Citizen> kids, PlayerEntity player) {
    }

    private static Yard yard(TestContext context, ServerWorld world, SettlementManager manager, int kids) {
        Meadow ground = meadow(context, world, manager);
        Citizen mother = hireWithBody(world, ground.village(), FarmJob.FARMER,
                context.getAbsolutePos(new BlockPos(12, 2, 12)));
        List<Citizen> children = new ArrayList<>();
        for (int i = 0; i < kids; i++) {
            children.add(childWithBody(world, ground.village(),
                    context.getAbsolutePos(new BlockPos(16 + i, 2, 16)), mother));
        }
        PlayerEntity player = playerAt(context, context.getAbsolutePos(new BlockPos(18, 2, 19)));
        return new Yard(ground, mother, children, player);
    }

    private static Citizen childWithBody(ServerWorld world, Settlement village, BlockPos at, Citizen mother) {
        Citizen child = someoneWith(Nature.EVEN, "Дитя", Gender.MALE);
        child.setLived(0);
        child.setParents(UUID.randomUUID(), mother.id());
        child.setPosition(Vec3d.ofBottomCenter(at));
        village.addCitizen(child);
        CitizenSpawner.spawnBody(world, village, child);
        if (child.entityUuid().map(world::getEntity).isEmpty()) {
            throw new IllegalStateException("тело ребёнка не в мире: " + at.toShortString());
        }
        return child;
    }

    private static CitizenEntity body(ServerWorld world, Citizen citizen) {
        UUID id = citizen.entityUuid().orElseThrow(() ->
                new IllegalStateException("у «" + citizen.fullName() + "» нет тела"));
        CitizenEntity body = (CitizenEntity) world.getEntity(id);
        if (body == null) {
            throw new IllegalStateException("тело «" + citizen.fullName() + "» не в мире");
        }
        return body;
    }

    private static void clearYard(ServerWorld world, SettlementManager manager, Yard yard) {
        HideAndSeek.at(yard.ground().village().id()).ifPresent(session -> HideAndSeek.stop(world, session));
        clearMeadow(world, manager, yard.ground());
    }

    private static int count(PlayerEntity player, Item item) {
        return player.getInventory().count(item);
    }

    /** Днём ребёнок рядом с игроком зовёт в прятки — раз в пять минут, а ночью не зовёт вовсе. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hide", tickLimit = 140)
    public void aChildInvitesAPlayerByDay(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Yard yard = yard(context, world, manager, 1);
        Settlement village = yard.ground().village();
        CitizenEntity kid = body(world, yard.kids().get(0));
        if (HideAndSeek.invite(world, village, DAY, 14_000, List.of(yard.player()))
                || kid.speech().isPresent()) {
            clearYard(world, manager, yard);
            context.throwGameTestException("Ночью позвали в прятки");
        }
        if (!HideAndSeek.invite(world, village, DAY, MORNING, List.of(yard.player()))
                || spoken(kid).filter(key -> key.contains(".hide_invite.")).isEmpty()) {
            clearYard(world, manager, yard);
            context.throwGameTestException("Днём ребёнок не позвал: " + spoken(kid));
        }
        context.runAtTick(100, () -> {
            try {
                if (HideAndSeek.invite(world, village, DAY, MORNING, List.of(yard.player()))
                        || kid.speech().isPresent()) {
                    context.throwGameTestException("Позвал второй раз через пять секунд");
                }
            } finally {
                clearYard(world, manager, yard);
            }
            context.complete();
        });
    }

    /** Нет детей — нет и пряток: щелчок по взрослому днём — его отказ, а не игра. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hide")
    public void noChildrenNoHiding(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Yard yard = yard(context, world, manager, 0);
        try {
            Settlement village = yard.ground().village();
            if (HideAndSeek.invite(world, village, DAY, MORNING, List.of(yard.player()))) {
                context.throwGameTestException("В деревне без детей позвали в прятки");
            }
            Games.Answer answer = Games.answer(world, yard.player(), village, yard.mother(), false, DAY,
                    MORNING);
            if (answer == Games.Answer.HIDING || HideAndSeek.at(village.id()).isPresent()) {
                context.throwGameTestException("Щелчок по взрослому начал прятки");
            }
        } finally {
            clearYard(world, manager, yard);
        }
        context.complete();
    }

    /**
     * Дети прячутся без подписей и находятся — подходом и щелчком; нашёл
     * всех — мать дарит гостинец: печенье норманнов и два медяка.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hide", tickLimit = 300)
    public void theChildrenHideAndAreFound(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Yard yard = yard(context, world, manager, 3);
        Settlement village = yard.ground().village();
        if (!HideAndSeek.clicked(world, yard.player(), village, yard.kids().get(0), DAY, MORNING)) {
            clearYard(world, manager, yard);
            context.throwGameTestException("Щелчок по ребёнку днём не начал прятки");
        }
        HideAndSeek.Session session = HideAndSeek.at(village.id()).orElse(null);
        if (session == null) {
            clearYard(world, manager, yard);
            context.throwGameTestException("Щелчок ответил, а прятки не начались: мест не нашлось?"
                    + " Сказал " + spoken(body(world, yard.kids().get(0))));
            return;
        }
        if (session.hiders() != 3) {
            clearYard(world, manager, yard);
            context.throwGameTestException("Прячутся " + session.hiders() + ", ждали троих");
        }
        for (Citizen kid : yard.kids()) {
            WorkTicker.decide(world, manager, village, kid, Schedule.MORNING_WORK, DAY);
            CitizenEntity body = body(world, kid);
            BlockPos spot = session.spotOf(kid.id()).orElse(null);
            if (spot == null) {
                clearYard(world, manager, yard);
                context.throwGameTestException("Ребёнку «" + kid.fullName() + "» не нашлось места");
                return;
            }
            if (!spot.equals(body.workTarget()) || body.isCustomNameVisible()) {
                clearYard(world, manager, yard);
                context.throwGameTestException("Прячущийся идёт в " + body.workTarget() + " вместо " + spot
                        + ", подпись видна " + body.isCustomNameVisible());
            }
            // Дошёл: проверка не ждёт ходьбы, ходьбу проверяют другие.
            body.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
        }
        Citizen first = yard.kids().get(0);
        context.runAtTick(HideAndSeek.COUNTDOWN + 5, () -> {
            session.spotOf(first.id()).ifPresent(spot ->
                    yard.player().setPosition(Vec3d.ofBottomCenter(spot.east())));
        });
        context.runAtTick(HideAndSeek.COUNTDOWN + 20, () -> {
            if (!session.isFound(first.id()) || !body(world, first).isCustomNameVisible()) {
                clearYard(world, manager, yard);
                context.throwGameTestException("Подошёл вплотную, а ребёнок не найден");
            }
            for (Citizen kid : yard.kids().subList(1, 3)) {
                HideAndSeek.clicked(world, yard.player(), village, kid, DAY, MORNING);
            }
        });
        context.runAtTick(HideAndSeek.COUNTDOWN + 30, () -> {
            try {
                if (HideAndSeek.at(village.id()).isPresent()) {
                    context.throwGameTestException("Все найдены, а прятки идут");
                }
                if (count(yard.player(), Items.COOKIE) != 1 || Coins.total(yard.player().getInventory()) != 2) {
                    context.throwGameTestException("Гостинец: печенья " + count(yard.player(), Items.COOKIE)
                            + ", медяков " + Coins.total(yard.player().getInventory()));
                }
                if (spoken(body(world, yard.mother())).filter(key -> key.contains(".hide_thanks.")).isEmpty()) {
                    context.throwGameTestException("Мать не поблагодарила: " + spoken(body(world, yard.mother())));
                }
                for (Citizen kid : yard.kids()) {
                    if (!body(world, kid).isCustomNameVisible()) {
                        context.throwGameTestException("После пряток у ребёнка нет подписи");
                    }
                }
            } finally {
                clearYard(world, manager, yard);
            }
            context.complete();
        });
    }

    /** Время вышло — оставшиеся выходят сами: «Не нашёл!»; гостинца нет. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hide", tickLimit = 2800)
    public void timeRunsOutAndTheyWin(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Yard yard = yard(context, world, manager, 2);
        Settlement village = yard.ground().village();
        HideAndSeek.clicked(world, yard.player(), village, yard.kids().get(0), DAY, MORNING);
        context.runAtTick(HideAndSeek.COUNTDOWN + HideAndSeek.SEEK + 15, () -> {
            try {
                if (HideAndSeek.at(village.id()).isPresent()) {
                    context.throwGameTestException("Время вышло, а прятки идут");
                }
                for (Citizen kid : yard.kids()) {
                    CitizenEntity body = body(world, kid);
                    if (spoken(body).filter(key -> key.contains(".hide_lost.")).isEmpty()
                            || !body.isCustomNameVisible()) {
                        context.throwGameTestException("Ненайденный: сказал " + spoken(body)
                                + ", подпись " + body.isCustomNameVisible());
                    }
                }
                if (count(yard.player(), Items.COOKIE) != 0) {
                    context.throwGameTestException("Гостинец без находки");
                }
            } finally {
                clearYard(world, manager, yard);
            }
            context.complete();
        });
    }

    /** Игрок ушёл посреди поиска — прятки кончились, и все дети с подписями. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hide", tickLimit = 300)
    public void hidingEndsWithEveryoneBack(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Yard yard = yard(context, world, manager, 2);
        Settlement village = yard.ground().village();
        HideAndSeek.clicked(world, yard.player(), village, yard.kids().get(0), DAY, MORNING);
        context.runAtTick(HideAndSeek.COUNTDOWN + 10, () -> yard.player().setPosition(
                Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(18, 2, 80)))));
        context.runAtTick(HideAndSeek.COUNTDOWN + 25, () -> {
            try {
                if (HideAndSeek.at(village.id()).isPresent()) {
                    context.throwGameTestException("Игрок ушёл, а прятки идут");
                }
                for (Citizen kid : yard.kids()) {
                    if (!body(world, kid).isCustomNameVisible()) {
                        context.throwGameTestException("Ребёнок так и остался без подписи");
                    }
                }
            } finally {
                clearYard(world, manager, yard);
            }
            context.complete();
        });
    }

    /** В колонии гостинца нет: дети просто рады и наутро бодрее. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "hide", tickLimit = 300)
    public void aColonysChildrenAreJustGlad(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Yard yard = yard(context, world, manager, 2);
        Settlement colony = yard.ground().village();
        colony.setOwner(Owner.of(UUID.randomUUID()));
        HideAndSeek.clicked(world, yard.player(), colony, yard.kids().get(0), DAY, MORNING);
        context.runAtTick(HideAndSeek.COUNTDOWN + 5, () -> {
            for (Citizen kid : yard.kids()) {
                HideAndSeek.clicked(world, yard.player(), colony, kid, DAY, MORNING);
            }
        });
        context.runAtTick(HideAndSeek.COUNTDOWN + 15, () -> {
            try {
                if (count(yard.player(), Items.COOKIE) != 0 || Coins.total(yard.player().getInventory()) != 0) {
                    context.throwGameTestException("В колонии дали гостинец");
                }
                for (Citizen kid : yard.kids()) {
                    if (!GamesLedger.get(world).cheered(kid.id(), DAY)) {
                        context.throwGameTestException("Ребёнок колонии не порадовался");
                    }
                }
            } finally {
                clearYard(world, manager, yard);
            }
            context.complete();
        });
    }

    /** Гостинец — сласть народа: у пони радужный кекс, у норманнов — печенье, раз своей нет. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hide")
    public void aTreatComesFromThePeople(TestContext context) {
        Item pony = HideAndSeek.treatOf(CultureManager.get(new Identifier("villagepax", "pony")));
        Item norman = HideAndSeek.treatOf(CultureManager.get(NORMAN));
        if (pony != ModItems.RAINBOW_CUPCAKE || norman != Items.COOKIE) {
            context.throwGameTestException("Гостинец пони " + pony + ", норманнов " + norman);
        }
        context.complete();
    }
}
