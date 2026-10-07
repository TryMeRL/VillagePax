package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.core.ModTags;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.core.config.Configs;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Comfort;
import com.villagepax.sim.life.Bonds;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.faith.Faith;
import com.villagepax.sim.trade.Wages;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.festival.FestivalDay;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Нужды жителя: голод, довольство и уход из колонии.
 * <p>
 * Здесь колония перестаёт быть механизмом и становится живой системой:
 * игрок начинает не только строить, но и содержать. Счастье — главный
 * регулятор сложности всего мода, и это его первая настоящая работа.
 */
public final class Needs {

    /** Ниже этого житель идёт есть, вместо того чтобы работать. */
    public static final int HUNGRY_BELOW = 12;

    /** Сколько сытости уходит за игровой день. */
    public static final int DAILY_COST = 8;

    public static final int MAX_SATURATION = 40;

    /** Сколько здоровья запись без тела набирает за ночь: половину. */
    static final float OVERNIGHT_HEAL = 10.0f;

    /**
     * Сроки удвоены после первой игры (решение заказчика): четыре дня
     * до предупреждения, шесть до ухода. Числа живут в настройках —
     * здесь только чтение, чтобы двух источников правды не было.
     * <p>
     * Прежние два и три выглядели разумно на бумаге, но игрок в это время
     * занят стройкой и не смотрит в чат: житель успевал уйти прежде, чем
     * причину заметили. Последствия те же — предупреждение, работа
     * вполсилы, уход навсегда, — но заметить и исправить теперь успеваешь.
     */
    public static int warnAfterDays() {
        return Configs.get().hungerWarnDays();
    }

    public static int leaveAfterDays() {
        return Configs.get().hungerLeaveDays();
    }

    /**
     * Сроки этого жителя: у ленивого вдвое дольше, у честолюбивого вдвое
     * короче.
     * <p>
     * Множителем поверх настройки, а не своими числами: иначе у сроков
     * стало бы два источника правды, и они разошлись бы в тот день,
     * когда игрок правит настройку.
     */
    public static int warnAfterDays(Citizen citizen) {
        return Natures.warnAfterDays(citizen);
    }

    public static int leaveAfterDays(Citizen citizen) {
        return Natures.leaveAfterDays(citizen);
    }

    private static final int HAPPINESS_STARVING = 20;
    private static final int HAPPINESS_HUNGRY = 8;
    private static final int HAPPINESS_FED = 5;

    private Needs() {
    }

    public static boolean isHungry(Citizen citizen) {
        return citizen.saturation() < HUNGRY_BELOW;
    }

    /**
     * Голодный житель идёт к еде. Возвращает {@code true}, если поел.
     * <p>
     * Еда берётся со склада колонии, а не из воздуха. С задачи 1.9б её
     * туда кладёт фермер, а до первой фермы — игрок своими руками.
     */
    public static boolean goEat(WorkContext context) {
        Warehouse.Container source = context.warehouse()
                .nearestWithTag(context.body().getBlockPos(), ModTags.CITIZEN_FOOD)
                .orElse(null);

        if (source == null) {
            // Еды нет вовсе: идти некуда, и суточный подсчёт это заметит.
            context.body().setWorkTarget(null);
            return false;
        }

        // Рядом с сундуком, а не в него: та же оговорка, что у курьера.
        context.body().setWorkTarget(Standing.besideOrAt(context.world(), source.pos()));
        if (!context.hasArrivedAt(source.pos())) {
            return false;
        }

        Item eaten = Warehouse.takeTagged(source, ModTags.CITIZEN_FOOD).orElse(null);
        if (eaten == null) {
            return false;
        }

        Citizen citizen = context.citizen();
        citizen.setSaturation(Math.min(MAX_SATURATION, citizen.saturation() + nourishment(eaten)));
        citizen.contented();
        return true;
    }

    /**
     * Насколько сытно. Берётся из ванильной еды и удваивается: сутки стоят
     * восемь, а хлеб даёт пять — иначе один житель съедал бы каравай в день.
     */
    public static int nourishment(Item item) {
        return item.getFoodComponent() == null ? 2 : item.getFoodComponent().getHunger() * 2;
    }

    /**
     * Суточный подсчёт: голод, довольство, уход.
     * <p>
     * Считается днями, а не тиками, потому что игрок мыслит днями: «не кормил
     * две ночи» — понятная причина ухода, «12400 тиков неудовлетворённости» —
     * нет. Обрабатывается ровно один день за раз, даже если игрок промотал
     * сотню: голодная смерть всей колонии от команды {@code /time add} была
     * бы наказанием без предупреждения.
     */
    public static void newDay(ServerWorld world, SettlementManager manager, Settlement settlement) {
        newDay(world, manager, settlement, Schedule.dayOf(world.getTimeOfDay()));
    }

    /**
     * То же в названный день: {@code today} — день, который начался.
     * <p>
     * День — довод, а не спрос у мира: праздник вчерашнего дня веселит
     * сегодня, а мир игровых проверок общий, и время в нём не подвинешь.
     */
    public static void newDay(ServerWorld world, SettlementManager manager, Settlement settlement,
                              long today) {
        List<Citizen> leaving = new ArrayList<>();

        for (Citizen citizen : settlement.citizens()) {
            citizen.setSaturation(citizen.saturation() - DAILY_COST);
            // Без тела раны затягиваются за ночь: тело лечится само, а запись
            // жителя, которого никто не видел, иначе хранила бы рану вечно.
            if (!com.villagepax.entity.CitizenSpawner.hasLiveBody(world, citizen)) {
                citizen.setHealth(citizen.health() + OVERNIGHT_HEAL);
            }

            if (citizen.saturation() <= 0) {
                citizen.setHappiness(citizen.happiness() - HAPPINESS_STARVING);
                citizen.addDiscontent();
            } else if (isHungry(citizen)) {
                citizen.setHappiness(citizen.happiness() - HAPPINESS_HUNGRY);
                citizen.addDiscontent();
            } else {
                // Сытость — основа, уют — прибавка. Голодному никакой
                // фонарь не поможет, и складывать их поэтому нельзя:
                // уют достаётся только тому, кто поел.
                //
                // Вера — прибавка того же рода и по той же причине здесь:
                // храм стоит дорого, а благосклонность копится долго,
                // и без этого первые двадцать дней здание просто занимало бы
                // место. «Нам есть куда пойти и нас слышат» — это ровно то,
                // за что житель любит своё поселение.
                // Налог вычитается здесь же, рядом с прибавками, и это
                // не бухгалтерия: игрок задаёт ставку и обязан увидеть,
                // во что она обошлась, — а увидеть можно только там, где
                // числа складываются в одно.
                //
                // Праздник — за вчерашний день: подсчёт идёт на рассвете,
                // а гуляли вчера. И вечер за игрой с хозяином — тоже вчерашний.
                citizen.setHappiness(citizen.happiness() + HAPPINESS_FED
                        + Comfort.of(world, settlement, citizen)
                        + Faith.solace(world, settlement)
                        + Bonds.moodOf(settlement, citizen)
                        + FestivalDay.cheer(settlement, today - 1)
                        + com.villagepax.sim.games.GameCheer.of(world, citizen, today - 1)
                        - Wages.discontentOf(settlement));
                citizen.contented();
            }

            if (citizen.discontent() >= leaveAfterDays(citizen)) {
                leaving.add(citizen);
            } else if (citizen.discontent() == warnAfterDays(citizen)) {
                tell(world, settlement, "villagepax.citizen.hungry", citizen.fullName());
            }
        }

        for (Citizen citizen : leaving) {
            leave(world, settlement, citizen);
        }

        Housing.assignBeds(world, settlement);
        Workplaces.assign(world, settlement);
        Housing.welcomeNewcomer(world, settlement, new java.util.Random(world.getRandom().nextLong()))
                .ifPresent(newcomer -> tell(world, settlement,
                        "villagepax.citizen.arrived", newcomer.fullName()));
    }

    /**
     * Житель уходит навсегда — решение заказчика: сперва предупреждение,
     * потом полсилы, потом уход. Потеря больная, но заслуженная.
     */
    private static void leave(ServerWorld world, Settlement settlement, Citizen citizen) {
        citizen.entityUuid()
                .map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .ifPresent(body -> body.discard());

        // Горя нет — ушедшего провожают, умершего оплакивают, — но
        // прибраться надо так же: супруг снова свободен, а память о друге
        // стёрта. Иначе колония горюет по нему вечно, а вдова не выйдет
        // замуж никогда.
        Bonds.parted(settlement, citizen);
        settlement.removeCitizen(citizen.id());
        tell(world, settlement, "villagepax.citizen.left", citizen.fullName());
        VillagePax.LOGGER.info("Житель {} ушёл из поселения {}: {} дней недовольства",
                citizen.fullName(), settlement.name(), citizen.discontent());
    }

    /**
     * Работает ли житель в полную силу.
     * <p>
     * Вполсилы тянут трое: недовольный, старик и ленивый. Замедление
     * у них <b>одно</b>, и это нарочно — три разных однажды сложились бы,
     * и недовольный ленивый старик встал бы на месте. Причины разные,
     * следствие общее: работа идёт через решение, но цель не сбрасывается,
     * и житель не замирает, а просто медленнее делает дело.
     * <p>
     * Годы и характер сведены в {@link Natures#worksSlowly}: там же лежит
     * и единственное исключение — честолюбивому старость не помеха.
     */
    public static boolean worksAtFullStrength(Citizen citizen) {
        return !citizen.isUnhappy() && !Natures.worksSlowly(citizen);
    }

    /** Сообщение хозяину колонии. Автономная деревня никому не жалуется. */
    private static void tell(ServerWorld world, Settlement settlement, String key, Object... args) {
        Optional<java.util.UUID> owner = settlement.owner().player();
        if (owner.isEmpty()) {
            return;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner.get());
        if (player != null) {
            player.sendMessage(Text.translatable(key, args), false);
        }
    }
}
