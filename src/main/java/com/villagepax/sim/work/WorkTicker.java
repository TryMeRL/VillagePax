package com.villagepax.sim.work;

import com.villagepax.core.Safely;
import com.villagepax.core.config.Configs;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.util.math.Vec3i;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.life.Life;
import com.villagepax.sim.diplomacy.Yoke;
import com.villagepax.sim.trade.Wages;
import com.villagepax.sim.faith.Faith;
import com.villagepax.sim.war.Campaigns;
import com.villagepax.sim.Villages;
import com.villagepax.core.Profiled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.sim.Warehouse;
import net.minecraft.util.math.BlockPos;

import java.util.List;

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
        ServerTickEvents.END_WORLD_TICK.register(Profiled.tick("work", WorkTicker::tick));
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

        // Обход по снимкам, а не по живым спискам. Списки поселений
        // и жителей отдаются представлениями, и прибавь или убери кого-то
        // любое решение посреди обхода — сервер упал бы на полпути. Сейчас
        // так не делает ни одно ремесло, но держится это на совпадении,
        // а снимок стоит одной короткой копии на поселение.
        for (Settlement settlement : List.copyOf(manager.all())) {
            // Сломанный день одной деревни не останавливает мир. День ей уже
            // засчитан — смена дня отмечается первой, — и назавтра деревня
            // попробует заново, а не будет падать каждый тик.
            Safely.run(settlement.name(), "Смена дня",
                    () -> rollOverDay(world, manager, settlement, today));

            for (Citizen citizen : List.copyOf(settlement.citizens())) {
                if (Campaigns.isAway(settlement, citizen)) {
                    // Страж в походе решений не принимает. Иначе стратегия
                    // каждые полсекунды велела бы ему идти патрулировать
                    // родную колонию — и он шёл бы домой через всю карту
                    // прямо из-под стен осаждаемой деревни.
                    continue;
                }
                if (isItsTurn(time, citizen)) {
                    decideSafely(world, manager, settlement, citizen, part, today);
                }
            }
        }
    }

    /**
     * Решение, которое не роняет мир.
     * <p>
     * Исключение в ремесле одного жителя без оградки роняет тик мира, а
     * значит и сервер. Житель, чьё решение упало, пропускает ход, а в лог
     * уходит одна трасса с тем, кто и где, — по ней и чинят. См. {@link Safely}.
     */
    private static void decideSafely(ServerWorld world, SettlementManager manager,
                                     Settlement settlement, Citizen citizen, Schedule part,
                                     long day) {
        Safely.run(citizen.fullName() + " из " + settlement.name(), "Решение жителя",
                () -> decide(world, manager, settlement, citizen, part, day));
    }

    /**
     * Суточные нужды считаются на смене дня.
     * <p>
     * Ровно один день за раз, даже если игрок промотал сотню командой
     * {@code /time add}: голодная смерть всей колонии за одну команду была бы
     * наказанием без предупреждения. И ни одного дня в тот тик, когда
     * поселение увидено впервые — только что основанная колония не должна
     * проголодаться сразу.
     * <p>
     * <b>Голод считается только там, где есть мир.</b> Это оказалось той
     * самой причиной, по которой у заказчика находились колонии с нулём
     * жителей. Сытость убывала <b>везде</b>, включая незагруженные чанки,
     * а поесть житель может только решением; решение бывает только у тела,
     * тела в выгруженном чанке нет. Игрок уходил исследовать мир на неделю
     * и возвращался к пустой колонии, не сделав ничего плохого.
     * <p>
     * Правило парное к несущему правилу мода: нет тела — нет работы,
     * и значит нет тела — <b>нет и голода</b>. Колония, которой никто
     * не видит, не ест и не растёт; день ей засчитывается, но нужд у неё
     * в этот день нет.
     * <p>
     * <b>Но деревня продолжает жить.</b> Замирает только голод, а не весь
     * день: деревня по-прежнему зарабатывает, планирует стройку и посылает
     * обозы — иначе обоз приходил бы только от той деревни, в которой
     * игрок стоит, а это уже не обоз, а прилавок.
     */
    private static void rollOverDay(ServerWorld world, SettlementManager manager,
                                    Settlement settlement, long today) {
        if (settlement.lastDay() == today) {
            return;
        }

        boolean seen = world.isChunkLoaded(settlement.center());
        boolean firstSight = !settlement.hasSeenADay();
        manager.update(settlement.id(), state -> {
            state.setLastDay(today);
            tellTwinsApart(world, state);
            if (firstSight) {
                // В первый же тик кровати надо раздать, иначе только что
                // основанная колония ночует под открытым небом целые сутки.
                Housing.assignBeds(world, state);
                Workplaces.assign(world, state);
            } else {
                // Голод, приток и уходы — только под присмотром: см. выше.
                if (seen) {
                    // Жизнь — перед нуждами: рождённый сегодня обязан
                    // попасть в тот же подсчёт голода, что и все. Иначе
                    // первый день он не ест вовсе, и это видно числом
                    // на второй.
                    Life.newDay(world, manager, state,
                            new java.util.Random(world.getRandom().nextLong()));
                    // Расчёт — до нужд: невыплата бьёт по довольству, а нужды
                    // в тот же день прибавляют за сытость. Порядок выбран так,
                    // чтобы игрок видел разницу одним числом, а не гадал,
                    // что из двух случилось раньше.
                    Wages.newDay(world, manager, state);
                    Needs.newDay(world, manager, state, today);
                    // Дань, если колония под ярмом. Здесь, а не в суточном
                    // решении деревни: платит колония, и платит со своего
                    // склада — то есть из сундуков, а сундуки бывают только
                    // в загруженных чанках.
                    Yoke.pay(world, manager, state, today);
                    // Утро под благословением урожая: посевы подрастают сами.
                    // Здесь, а не в суточном решении деревни, потому что поле
                    // есть и у колонии игрока, а деревня своего решения
                    // для неё не принимает.
                    Faith.growCrops(world, state, today);
                    // Молчание — худшее, что мод может ответить на «почему
                    // ничего не строится». Раз в день, и только когда стройка
                    // действительно ждёт.
                    BuilderJob.remindIfNobodyBuilds(world, state);
                }
                if (state.owner().isAutonomous()) {
                    // Деревня решает за себя сама: игрока, который разметил
                    // бы ей здание, у неё нет.
                    Villages.newDay(world, manager, state);
                }
            }
        });
    }

    /**
     * Развести тёзок на рассвете: двойники из миров, начатых при коротком
     * именнике, получают свободные имена. Хозяину колонии — по строке
     * на каждого: житель, которого вчера звали иначе, без объяснения
     * выглядел бы подменой.
     */
    private static void tellTwinsApart(ServerWorld world, Settlement settlement) {
        com.villagepax.core.culture.Culture culture =
                com.villagepax.core.culture.CultureManager.get(settlement.culture());
        if (culture == null) {
            return;
        }
        String was = settlement.name();
        com.villagepax.sim.Founding.renameIfTwin(SettlementManager.get(world), settlement, culture,
                        new java.util.Random(world.getRandom().nextLong()))
                .ifPresent(name -> com.villagepax.VillagePax.LOGGER.info(
                        "Деревня {} названа {}: такое название на карте уже было", was, name));
        List<com.villagepax.sim.Founding.Renamed> renamed = com.villagepax.sim.Founding.tellTwinsApart(
                settlement, culture, new java.util.Random(world.getRandom().nextLong()));
        if (renamed.isEmpty()) {
            return;
        }
        com.villagepax.VillagePax.LOGGER.info("В {} разведены тёзки: {}", settlement.name(), renamed);
        settlement.owner().player()
                .map(owner -> world.getServer().getPlayerManager().getPlayer(owner))
                .ifPresent(player -> renamed.forEach(one -> player.sendMessage(
                        net.minecraft.text.Text.translatable("villagepax.names.renamed",
                                one.was(), one.now()), false)));
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
        decide(world, manager, settlement, citizen, part, Schedule.dayOf(world.getTimeOfDay()));
    }

    /**
     * То же, но в названный день.
     * <p>
     * День — довод, а не спрос у мира: праздник зависит от дня, а мир
     * игровых проверок общий, и время суток в нём не подвинешь.
     */
    public static void decide(ServerWorld world, SettlementManager manager,
                              Settlement settlement, Citizen citizen, Schedule part, long day) {
        CitizenEntity body = liveBody(world, citizen);
        if (body == null) {
            return;
        }

        WorkContext context = new WorkContext(world, manager, settlement, citizen, body);

        // Подпись над головой — здесь: это единственное место, куда житель
        // с телом заходит регулярно, и потому единственное, где она не
        // может отстать от смены ремесла.
        body.label(citizen, Configs.get().citizenLabels());

        // Раненый отлёживается: днём в постели раны затягиваются вдвое
        // быстрее, и житель, которого крипер едва не убил, не идёт тут же
        // полоть грядку. Будить такого с утра незачем.
        boolean resting = mustRest(context);
        if (part != Schedule.SLEEP && body.isSleeping() && !resting) {
            body.wakeUp();
        }
        if (part != Schedule.SLEEP && !resting) {
            body.setDozing(false);
        }
        leash(context, part);
        // Дело на это решение назовёт работа; сон, сбор и обед смотрят
        // не на вчерашнюю грядку.
        body.setWorkFocus(null);

        // Праздник подменяет цель, как распорядок: гулянье у сердца ярмарки
        // вместо работы и сбора. Работу при этом не стирает — наутро
        // стройка там, где её оставили.
        if (com.villagepax.sim.festival.Revels.takesOver(context, part, day)) {
            return;
        }
        body.setDancing(false);

        // Вечер у стола — тоже подмена цели, а не работы: компания стоит
        // у места игры, партия держит соперника лицом к игроку.
        if (com.villagepax.sim.games.Games.takesOver(context, part, day)) {
            return;
        }
        // Свадебный вечер — молодым у ратуши; гости на своём вечернем круге.
        if (com.villagepax.sim.life.Weddings.takesOver(context, part, day)) {
            return;
        }

        if (resting) {
            context.holdNothing();
            goToBed(context);
            return;
        }

        // В грозу по домам все, кроме стражи: под молнией в поле не стоят.
        // А в дождь вечерний сбор — не на площади, а под своей крышей.
        boolean guard = Jobs.forProfession(citizen.profession())
                .filter(job -> job instanceof GuardJob).isPresent();
        if (!guard && part != Schedule.SLEEP && shelters(context, part)) {
            context.holdNothing();
            shelter(context);
            return;
        }

        switch (part) {
            case SLEEP -> {
                // Ночной дозор: страж встаёт с постели, если по деревне ходит
                // нечисть, и ложится, когда её не станет.
                if (guard && GuardJob.nearestMonster(context) != null) {
                    if (body.isSleeping()) {
                        body.wakeUp();
                    }
                    body.setDozing(false);
                    work(context);
                    return;
                }
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

    /** Ниже этой доли здоровья житель отлёживается в постели, а не работает. */
    static final float REST_BELOW = 0.4f;

    /** До этой доли — и только тогда встаёт: полузаживший тут же слёг бы снова. */
    static final float REST_UNTIL = 0.8f;

    /**
     * Пора ли отлежаться.
     * <p>
     * Только тому, у кого есть постель, и не в осаде: в бою раненый бежит,
     * а не ложится. Уже лежащий встаёт, лишь поправившись по-настоящему.
     */
    static boolean mustRest(WorkContext context) {
        CitizenEntity body = context.body();
        if (body.isBesieged() || context.citizen().bed().isEmpty()) {
            return false;
        }
        float health = body.getHealth() / body.getMaxHealth();
        boolean lying = body.isSleeping() || body.isDozing();
        return health < (lying ? REST_UNTIL : REST_BELOW);
    }

    /** Укрыться ли от непогоды в эту часть дня. */
    private static boolean shelters(WorkContext context, Schedule part) {
        if (context.citizen().bed().isEmpty() || context.body().isBesieged()) {
            return false;
        }
        if (context.world().isThundering()) {
            return true;
        }
        // Спрашивается погода над деревней, а не над головой: под крышей
        // дождя не видно, и житель, укрывшийся дома, тут же шёл бы на площадь.
        BlockPos square = context.settlement().center();
        return part == Schedule.LEISURE && context.world().isRaining()
                && context.world().getBiome(square).value().getPrecipitation(square)
                != net.minecraft.world.biome.Biome.Precipitation.NONE;
    }

    /**
     * Под свою крышу: к постели, но не в неё — день ещё не кончился,
     * и непогода пройдёт раньше, чем захочется спать.
     */
    private static void shelter(WorkContext context) {
        BlockPos bed = context.citizen().bed().orElseThrow();
        // Дошёл — и стоит у постели, а не гуляет: прогулку держит привязь.
        context.body().keepNear(bed, SHELTER_RANGE);
        context.body().setWorkTarget(bed);
    }

    /** Насколько укрывшийся отходит от своей постели: в пределах дома. */
    private static final int SHELTER_RANGE = 3;

    /** Досуг и безделье держат жителя у площади: круг вечернего сбора и немного сверх. */
    private static final int PLAZA_RANGE = 12;

    /** Работник без дела держится своей мастерской. */
    private static final int WORKSHOP_RANGE = 10;

    /**
     * Где житель вправе гулять, когда делать нечего.
     * <p>
     * Тело привязывалось к ратуше при появлении на всю границу поселения —
     * у хутора это тридцать два блока, у столицы девяносто шесть. Прогулка
     * берёт точку в десяти блоках от того места, где житель стоит, и
     * следующую от новой, и за утро он уходил на край границы: в сохранениях
     * заказчика купец стоял в тридцати блоках от ларька, старейшина — на
     * склоне высоко над площадью. Граница — это «не дальше», а не «где гулять».
     * <p>
     * Теперь безделье держит жителя у места, которое ему своё: у мастерской,
     * если она есть, иначе у площади, а на досуге — у площади всех. Дело
     * этой привязи не видит — путь к работе прокладывает навигация, — а
     * бегство видит, и потому в осаде тело отпущено на всю границу: бежать
     * от меча в двенадцати блоках от ратуши некуда.
     */
    private static void leash(WorkContext context, Schedule part) {
        CitizenEntity body = context.body();
        Settlement settlement = context.settlement();
        if (body.isBesieged()) {
            body.keepNear(settlement.center(), CitizenSpawner.tetherRange(settlement));
            return;
        }
        if (part != Schedule.LEISURE) {
            BlockPos workshop = Workplaces.of(settlement, context.citizen())
                    .map(WorkTicker::middleOf).orElse(null);
            if (workshop != null) {
                body.keepNear(workshop, WORKSHOP_RANGE);
                return;
            }
        }
        body.keepNear(settlement.center(), PLAZA_RANGE);
    }

    /** Середина следа здания — туда тянет работника его мастерская. */
    private static BlockPos middleOf(Building building) {
        return SchematicLoader.get(BuildJob.schematicId(building))
                .map(schematic -> {
                    Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
                    return building.anchor().add(size.getX() / 2, 0, size.getZ() / 2);
                })
                .orElse(building.anchor());
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
        if (!context.hasArrivedAt(bed)) {
            // Лежанку сменили или житель сдвинулся — идти, а не дремать.
            context.body().setDozing(false);
            return;
        }
        if (context.body().isSleeping()) {
            return;
        }
        // Настоящая кровать — ложится. Лежанка на полу — дремлет стоя:
        // ванильный сон вне кровати игра обрывает на следующем же тике.
        if (context.world().getBlockState(bed).isIn(net.minecraft.registry.tag.BlockTags.BEDS)) {
            context.body().sleep(bed);
        } else {
            context.body().setDozing(true);
        }
    }

    private static void work(WorkContext context) {
        Job job = Jobs.forProfession(context.citizen().profession()).orElse(null);

        // Груз прежнего ремесла — сперва на склад. Возвращают его только
        // курьер и строитель, а курьера, переведённого в стражи или пахари,
        // никто больше не спрашивал о сумке: два-три слота досок исчезали
        // из колонии вместе со сменой ремесла.
        if (context.state().isCarrying() && !(job instanceof HaulJob) && !(job instanceof BuilderJob)) {
            BlockPos store = context.warehouse().nearest(context.body().getBlockPos())
                    .map(Warehouse.Container::pos).orElse(null);
            context.manager().update(context.settlement().id(), ignored -> Hauling.returnLoad(context));
            context.body().setWorkTarget(store == null || !context.state().isCarrying()
                    ? null : Standing.besideOrAt(context.world(), store));
            return;
        }

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
        context.body().setWorkFocus(destination[0]);

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
