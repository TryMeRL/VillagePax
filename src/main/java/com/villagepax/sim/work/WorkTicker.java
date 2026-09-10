package com.villagepax.sim.work;

import com.villagepax.core.config.Configs;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Стратегический слой ИИ: раз в {@link #ticksPerDecision()} тиков каждый
 * житель решает, что делать дальше — по времени суток и по своим нуждам.
 * <p>
 * Три вещи, на которых держится производительность, и все три взяты из того,
 * как это ломается у существующих модов:
 * <ol>
 *   <li><b>Решения редки.</b> Раз в десять тиков, а не каждый тик.</li>
 *   <li><b>Решения разнесены</b> смещением по хешу жителя: иначе все работники
 *       мира думают в один и тот же тик, и всплеск нагрузки складывается.</li>
 *   <li><b>Нет тела — нет работы.</b> Тело есть только при загруженном чанке,
 *       поэтому далёкое поселение не стоит серверу ничего.</li>
 * </ol>
 * Цель навигации на сущности ходит каждый тик, но пути не считает: точку ей
 * называет стратегия, и она же кладёт её в тело.
 */
public final class WorkTicker {

    /**
     * Полсекунды между решениями по умолчанию. Дизайн-документ отводит
     * на это 20–100 тиков, а число живёт в настройках: это первое, чем
     * игрок будет расплачиваться за размер колонии.
     */
    public static int ticksPerDecision() {
        return Configs.get().ticksPerDecision();
    }

    private WorkTicker() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(WorkTicker::tick);
    }

    public static void tick(ServerWorld world) {
        SettlementManager manager = SettlementManager.get(world);
        if (manager.count() == 0) {
            return;
        }

        long time = world.getTime();
        long timeOfDay = world.getTimeOfDay();
        Schedule part = Schedule.at(timeOfDay);
        long today = Schedule.dayOf(timeOfDay);

        for (Settlement settlement : manager.all()) {
            rollOverDay(world, manager, settlement, today);

            for (Citizen citizen : settlement.citizens()) {
                if (isItsTurn(time, citizen)) {
                    decide(world, manager, settlement, citizen, part);
                }
            }
        }
    }

    /**
     * Суточные нужды считаются на смене дня.
     * <p>
     * Ровно один день за раз, даже если игрок промотал сотню командой
     * {@code /time add}: голодная смерть всей колонии за одну команду была бы
     * наказанием без предупреждения. И ни одного дня в тот тик, когда
     * поселение увидено впервые — только что основанная колония не должна
     * проголодаться сразу.
     */
    private static void rollOverDay(ServerWorld world, SettlementManager manager,
                                    Settlement settlement, long today) {
        if (settlement.lastDay() == today) {
            return;
        }

        boolean firstSight = !settlement.hasSeenADay();
        manager.update(settlement.id(), state -> {
            state.setLastDay(today);
            if (firstSight) {
                // В первый же тик кровати надо раздать, иначе только что
                // основанная колония ночует под открытым небом целые сутки.
                Housing.assignBeds(world, state);
                Workplaces.assign(world, state);
            } else {
                Needs.newDay(world, manager, state);
                if (state.owner().isAutonomous()) {
                    // Деревня решает за себя сама: игрока, который разметил
                    // бы ей здание, у неё нет.
                    Villages.newDay(world, manager, state);
                }
            }
        });
    }

    /**
     * Один шаг стратегии в заданной части суток.
     * <p>
     * Распорядок <b>подменяет цель, но не стирает состояние работы</b>: ночью
     * курьер идёт спать, не бросая груз, и утром доносит его к той же стройке.
     * Иначе каждый закат обнулял бы задания, и наутро всё начиналось заново.
     */
    public static void decide(ServerWorld world, SettlementManager manager,
                              Settlement settlement, Citizen citizen, Schedule part) {
        CitizenEntity body = liveBody(world, citizen);
        if (body == null) {
            return;
        }

        WorkContext context = new WorkContext(world, manager, settlement, citizen, body);

        // Подпись над головой — здесь: это единственное место, куда житель
        // с телом заходит регулярно, и потому единственное, где она не
        // может отстать от смены ремесла.
        body.label(citizen, Configs.get().citizenLabels());

        if (part != Schedule.SLEEP && body.isSleeping()) {
            body.wakeUp();
        }

        switch (part) {
            case SLEEP -> {
                // Спать с топором в руке житель не должен: инструмент —
                // это показ работы, а не часть одежды.
                context.holdNothing();
                goToBed(context);
            }
            case LEISURE -> {
                context.holdNothing();
                gather(context);
            }
            case MEAL -> {
                if (Needs.isHungry(citizen)) {
                    manager.update(settlement.id(), ignored -> Needs.goEat(context));
                } else {
                    work(context);
                }
            }
            case MORNING_WORK, DAY_WORK -> work(context);
        }
    }

    /**
     * Вечерний сбор: житель идёт на площадь, а дойдя — отпускает цель.
     * <p>
     * Отпускает намеренно: стоять в строю кругом было бы страннее, чем
     * расходиться. Без цели его забирает прогулка, и он топчется у ратуши
     * сам собой — это и есть та жизнь, которой не хватало вечерам.
     */
    private static void gather(WorkContext context) {
        BlockPos spot = Gathering.spot(context.world(), context.settlement(), context.citizen());
        context.body().setWorkTarget(context.hasArrivedAt(spot) ? null : spot);
    }

    /**
     * Житель идёт к своему месту и ложится. Бездомный остаётся бродить —
     * и суточный подсчёт это заметит через недовольство.
     */
    private static void goToBed(WorkContext context) {
        BlockPos bed = context.citizen().bed().orElse(null);
        if (bed == null) {
            context.body().setWorkTarget(null);
            return;
        }

        context.body().setWorkTarget(bed);
        if (context.hasArrivedAt(bed) && !context.body().isSleeping()) {
            context.body().sleep(bed);
        }
    }

    private static void work(WorkContext context) {
        Job job = Jobs.forProfession(context.citizen().profession()).orElse(null);
        if (job == null) {
            context.holdNothing();
            context.body().setWorkTarget(null);
            return;
        }

        // Недовольный тянет вполсилы: работает через решение. Цель при этом
        // не сбрасывается, поэтому он не замирает на месте, а просто медленнее
        // делает дело — так игрок видит последствия, а не поломку.
        if (!Needs.worksAtFullStrength(context.citizen()) && isSlacking(context)) {
            return;
        }

        // Цель приходит из того же вызова, что и работа: отдельный запрос
        // считал бы то же самое второй раз, а у лесоруба это второй обход
        // леса вокруг мастерской.
        BlockPos[] destination = new BlockPos[1];
        context.manager().update(context.settlement().id(),
                ignored -> destination[0] = job.tick(context).orElse(null));

        // Ремесло называет <b>дело</b>, а тикер решает, откуда за него
        // браться. Разделение не украшение: фермер возвращал грядку,
        // лесоруб — ствол, курьер — сундук, и всех троих посылали
        // внутрь блока. Работало это только потому, что ванильная
        // навигация останавливается рядом сама; когда не останавливалась —
        // житель топтался, и игрок видел, как он «тупит».
        context.body().setWorkTarget(destination[0] == null
                ? null : Standing.besideOrAt(context.world(), destination[0]));

        // Если житель решение за решением метит в одну и ту же точку и не
        // приближается — он от неё отступится, и следующее решение выберет
        // другое дело. Без этого недостижимая цель держала его навсегда.
        context.body().noteReachAttempt(destination[0]);
    }

    private static boolean isSlacking(WorkContext context) {
        long decision = context.world().getTime() / ticksPerDecision();
        return Math.floorMod(decision + context.citizen().id().hashCode(), 2) == 0;
    }

    private static CitizenEntity liveBody(ServerWorld world, Citizen citizen) {
        return citizen.entityUuid()
                .map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .map(CitizenEntity.class::cast)
                .filter(Entity::isAlive)
                .orElse(null);
    }

    private static boolean isItsTurn(long time, Citizen citizen) {
        int tempo = ticksPerDecision();
        int offset = Math.floorMod(citizen.id().hashCode(), tempo);
        return Math.floorMod(time + offset, tempo) == 0;
    }
}
