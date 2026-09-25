package com.villagepax.sim.war;

import com.mojang.serialization.Codec;
import com.villagepax.VillagePax;
import com.villagepax.core.EnumCodecs;
import com.villagepax.core.Named;
import com.villagepax.core.war.WarParty;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Ground;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.Villages;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Поход: колония идёт на деревню.
 * <p>
 * Вторая половина захвата поселений и та, которой у игрока не было вовсе.
 * Он умел отбиться, откупиться и обобрать разбитого — но пойти сам
 * не мог, и вся война работала в одну сторону.
 *
 * <h2>Устроено набегом наизнанку</h2>
 * Отряд — та же {@link WarParty}, и лежит он <b>у деревни</b>, потому что
 * спрашивают о нём там же, где и всегда: «кто у моих ворот». Приход,
 * сбор телами, разорение, добыча и уход считаются тем же кодом, что
 * и у набега: это одно и то же событие, и писать его дважды значило бы
 * получить две войны с разными правилами.
 *
 * <h2>Отряд — настоящие стражи, а не куклы</h2>
 * И это главное решение здесь. Кукла ничего не стоит: потерял — набрал
 * новых. Страж нанимался днями, у него есть имя, ремесло и, может быть,
 * жена, — и павший не возвращается. Поход поэтому решение, а не кнопка.
 * <p>
 * Вторая его цена <b>дома</b>: пока отряд в пути, колония без стражи.
 * Обиженная деревня, пославшая свой отряд ровно в эти дни, застанет
 * пустые ворота — и это честно, потому что игрок сам увёл людей.
 *
 * <h2>Драться идёт стража, а не игрок</h2>
 * Решение заказчика: «командование — только приказы с карты, игрок
 * назначает цели, стража исполняет». Прийти и помочь мечом ему никто
 * не мешает, и это стоит делать: исход решает, пролилась ли кровь отряда.
 */
public final class Campaigns {

    /**
     * Сколько дней отряд идёт.
     * <p>
     * Двое суток, вдвое дольше, чем идёт чужой набег к колонии. Разница
     * намеренная: набег — это ответ, и он должен быть быстрым, а поход —
     * решение игрока, и у решения обязано быть время передумать. Заодно
     * эти два дня — то самое окно, в которое его колония стоит без стражи.
     */
    public static final int MARCH_DAYS = 2;

    /** Сколько дней отряд стоит у деревни, прежде чем уйти. */
    public static final int STAY_DAYS = 1;

    /**
     * Сколько стражей нужно, чтобы выступить.
     * <p>
     * Двое, а не один: поход одиночки — это не война, а прогулка
     * с мечом, и кончалась бы она одним телом под чужой стеной.
     */
    public static final int LEAST = 2;

    /** Больше четверых не уходит: столько же присылает и деревня. */
    public static final int MOST = Raids.MOST_FIGHTERS;

    /** С какой ступени колония воюет сама. Та же, что и для дани. */
    public static final SettlementLevel NEEDS = SettlementLevel.TOWN;

    private Campaigns() {
    }

    /**
     * Чем кончится приказ — до того, как он отдан.
     * <p>
     * Тот же приём, что у дани, союза и откупа: приговор считает сервер
     * и показывает заранее, а не отказывает молчащей кнопкой.
     */
    public enum Verdict implements Named {

        /** Можно идти. */
        YES("yes"),

        /** Это не деревня народа: на свою колонию походом не ходят. */
        NOT_A_NEIGHBOUR("not_a_neighbour"),

        /** Колония ещё не город. */
        NO_TOWN("no_town"),

        /** Стражи мало: нужны двое. */
        NO_GUARDS("no_guards"),

        /** Отряд уже в пути: второго у колонии нет. */
        ALREADY("already"),

        /** У деревни уже кто-то стоит. */
        BUSY("busy"),

        /** Свои ворота под ударом — не до походов. */
        BESIEGED("besieged"),

        /** Деревня уже платит. */
        PAYING("paying"),

        /**
         * Это друг.
         * <p>
         * Отказ, а не «можно, но дружбе конец». То же решение, что
         * и у дани: «друг дани не платит». Дружба и война — разные
         * разговоры, и смешать их значит обесценить оба. Заодно это
         * единственное, что даёт дружбе цену <b>в обе стороны</b>:
         * она защищает деревню от тебя ровно так же, как тебя от неё.
         */
        TOO_FRIENDLY("too_friendly"),

        /** Слишком далеко: столько отряд не пройдёт. */
        TOO_FAR("too_far");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        public static final Codec<Verdict> CODEC = EnumCodecs.of(values(), "приговор походу");

        public boolean ready() {
            return this == YES;
        }

        /** Чем объяснить отказ. У согласия причины нет. */
        public Optional<String> reasonKey() {
            return this == YES ? Optional.empty()
                    : Optional.of("villagepax.campaign." + id);
        }
    }

    /** Приговор походу. */
    public static Verdict judge(Settlement colony, Settlement village, UUID player, long today) {
        if (village == null || !village.owner().isAutonomous()) {
            return Verdict.NOT_A_NEIGHBOUR;
        }
        if (colony == null || colony.level().ordinal() < NEEDS.ordinal()) {
            return Verdict.NO_TOWN;
        }
        if (colony.marchingOn().isPresent()) {
            return Verdict.ALREADY;
        }
        if (colony.siege().isPresent()) {
            return Verdict.BESIEGED;
        }
        if (village.siege().isPresent()) {
            return Verdict.BUSY;
        }
        if (village.owesTributeTo(player, today)) {
            return Verdict.PAYING;
        }
        if (village.reputationOf(player) >= Standing.FRIEND.from()) {
            return Verdict.TOO_FRIENDLY;
        }
        if (guardsOf(colony).size() < LEAST) {
            return Verdict.NO_GUARDS;
        }
        if (colony.center().getSquaredDistance(village.center())
                > (double) Raids.REACH * Raids.REACH) {
            return Verdict.TOO_FAR;
        }
        return Verdict.YES;
    }

    /**
     * Сколько мечей уйдёт, если выступить сейчас.
     * <p>
     * Без колонии — ни одного, и это не проверка ради проверки: снимок
     * разговора со старейшиной строится и для человека, у которого
     * колонии нет вовсе, — он с деревни и начинает игру.
     */
    public static int fightersFor(Settlement colony) {
        return colony == null ? 0 : Math.min(MOST, guardsOf(colony).size());
    }

    /**
     * Выступить.
     * <p>
     * Союз с деревней при этом разрывается, и молча: тот же порядок, что
     * и у требования дани. Вступаться за человека и идти на него разом
     * нельзя, и выбирает здесь игрок — тем, что отдал приказ.
     */
    public static Verdict march(ServerWorld world, SettlementManager manager, Settlement colony,
                                Settlement village, UUID player, long today) {
        Verdict verdict = judge(colony, village, player, today);
        if (!verdict.ready()) {
            return verdict;
        }

        int fighters = fightersFor(colony);
        WarParty party = new WarParty(UUID.randomUUID(), colony.id(), colony.culture(),
                approach(colony, village), fighters,
                today + MARCH_DAYS, today + MARCH_DAYS + STAY_DAYS);

        manager.update(village.id(), state -> {
            state.breakAlly(player);
            state.besiege(party, today);
        });
        manager.update(colony.id(), state -> state.marchOn(village.id()));

        tell(world, colony, Text.translatable("villagepax.campaign.set_out",
                Text.literal(String.valueOf(fighters)), Text.literal(village.name()),
                Text.literal(String.valueOf(MARCH_DAYS))).formatted(Formatting.GOLD));
        VillagePax.LOGGER.info("Колония {} послала {} бойцов на {}",
                colony.name(), fighters, village.name());
        return Verdict.YES;
    }

    /**
     * Откуда отряд подойдёт: с той стороны, откуда пришёл.
     * <p>
     * Считается без мира нарочно. Приказ отдаётся в своей ратуше, а
     * деревня в этот миг за сотни блоков и, скорее всего, не загружена —
     * спросить у неё твёрдую землю попросту не у кого. Точное место
     * под ногами находится потом, при сборе, когда игрок придёт смотреть.
     */
    static BlockPos approach(Settlement colony, Settlement village) {
        Vec3d away = Vec3d.of(colony.center().subtract(village.center()));
        if (away.lengthSquared() < 1.0) {
            return village.center();
        }
        Vec3d step = away.normalize().multiply(12.0);
        return village.center().add((int) Math.round(step.x), 0, (int) Math.round(step.z));
    }

    /**
     * Поставить телами тех, кто дошёл.
     * <p>
     * Отличается от {@link Raids} ровно одним, и в этом всё: тела здесь
     * <b>настоящие</b>. У бойца есть запись жителя, имя над головой и
     * колония, из которой он вышел; погибнув, он уходит из неё насовсем
     * тем же кодом, которым уходит любой убитый житель. Кукле пришлось бы
     * считать потери отдельно — и однажды счёт разошёлся бы с людьми.
     */
    static void muster(ServerWorld world, SettlementManager manager, Settlement village,
                       WarParty party) {
        Settlement colony = manager.byId(party.home()).orElse(null);
        if (colony == null) {
            return;
        }

        List<CitizenEntity> standing = Raids.bodiesOf(world, party);
        List<Citizen> guards = guardsOf(colony);
        boolean first = standing.isEmpty();

        for (int number = standing.size(); number < party.fighters()
                && number < guards.size(); number++) {
            Citizen soldier = guards.get(number);
            BlockPos where = Ground.spotNear(world, party.musters(), 0, 5);
            soldier.setPosition(Vec3d.ofBottomCenter(where == null ? party.musters() : where));

            CitizenEntity body = CitizenSpawner.spawnBody(world, colony, soldier);
            if (body == null) {
                return;
            }
            body.linkRaid(village.id(), party.id());
            body.equipStack(EquipmentSlot.MAINHAND,
                    com.villagepax.item.gear.ModGear.armsFor(colony.culture()));
        }

        if (first) {
            tell(world, colony, Text.translatable("villagepax.campaign.arrived",
                    Text.literal(village.name())).formatted(Formatting.GOLD));
        }
    }

    /**
     * Отряд возвращается.
     * <p>
     * Выжившие идут домой — то есть их запись переносится к ратуше
     * колонии, а тело снимается: у края чужой деревни ему делать больше
     * нечего, а дома оно появится само, когда игрок туда придёт. Павшие
     * домой не идут, и считать их не надо — их уже нет в колонии.
     */
    static void comeHome(ServerWorld world, SettlementManager manager, Settlement village,
                         WarParty party) {
        Settlement colony = manager.byId(party.home()).orElse(null);
        if (colony == null) {
            return;
        }

        List<CitizenEntity> alive = Raids.bodiesOf(world, party);
        for (CitizenEntity body : alive) {
            body.writeBackTo(world);
        }
        manager.update(colony.id(), state -> {
            for (CitizenEntity body : alive) {
                body.citizenId().flatMap(state::citizen).ifPresent(soldier ->
                        soldier.setPosition(Vec3d.ofBottomCenter(state.center().up())));
            }
            state.cameHome();
        });
        alive.forEach(CitizenEntity::discard);

        int lost = lostOf(party, alive.size());
        tell(world, colony, Text.translatable(
                lost > 0 ? "villagepax.campaign.home_bloodied" : "villagepax.campaign.home_whole",
                Text.literal(village.name()), Text.literal(String.valueOf(alive.size())),
                Text.literal(String.valueOf(lost))).formatted(Formatting.GRAY));
    }

    /**
     * Сколько не вернулось.
     * <p>
     * Считается по тому, кто стоит, а не по записи отряда: запись знает
     * число живых на прошлый удар, а земля знает, кто на ней остался.
     */
    private static int lostOf(WarParty party, int alive) {
        return Math.max(0, party.fighters() - alive);
    }

    /** Стража колонии: те, кому и идти. */
    public static List<Citizen> guardsOf(Settlement colony) {
        List<Citizen> found = new ArrayList<>();
        for (Citizen citizen : colony.citizens()) {
            if (citizen.profession().filter(Villages.GUARD::equals).isPresent()) {
                found.add(citizen);
            }
        }
        return found;
    }

    /**
     * В походе ли этот житель.
     * <p>
     * Спрашивается на каждом решении, и потому отвечает по одному полю
     * колонии, а не обходом поселений мира. Работать в походе нельзя:
     * страж, которому стратегия каждые полсекунды велит идти патрулировать
     * родную колонию, шёл бы домой через всю карту прямо из-под стен
     * осаждаемой деревни.
     */
    public static boolean isAway(Settlement colony, Citizen citizen) {
        return colony.marchingOn().isPresent()
                && citizen.profession().filter(Villages.GUARD::equals).isPresent();
    }

    private static void tell(ServerWorld world, Settlement colony, Text line) {
        UUID player = colony.owner().player().orElse(null);
        if (player == null) {
            return;
        }
        ServerPlayerEntity who = world.getServer().getPlayerManager().getPlayer(player);
        if (who != null) {
            who.sendMessage(line, false);
        }
    }
}
