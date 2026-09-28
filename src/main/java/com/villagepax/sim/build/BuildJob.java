package com.villagepax.sim.build;

import com.villagepax.VillagePax;
import com.villagepax.sim.BuildProgress;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import com.villagepax.sim.Hazards;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Sounds;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.work.Crafting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import com.villagepax.screen.TownHallNet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/**
 * Двигатель стройки: билдер идёт по плану и ставит блоки по одному.
 * <p>
 * Прогресс живёт в {@link Building#nextStep()}, то есть в данных поселения.
 * Билдер может выгрузиться вместе с чанком, погибнуть или сменить работу —
 * стройка продолжится с того же места, а не начнётся заново.
 * <p>
 * Темп задаётся снаружи: {@link #advance} выполняет до {@code maxSteps} шагов
 * и возвращает, чем всё кончилось. Тикер вызывает с единицей раз в
 * {@link #TICKS_PER_STEP}, игровой тест — с большим числом сразу. Иначе
 * приёмочный тест либо занял бы полторы минуты игрового времени, либо
 * проверял бы не то, что работает в игре.
 */
public final class BuildJob {

    /** Докуда валится остаток ствола над расчищенной клеткой. */
    private static final int TRUNK = 12;

    /** Один блок за полсекунды: стройка должна быть видна как процесс. */
    public static final int TICKS_PER_STEP = 10;

    /**
     * Как ложится посев: без толчка соседям.
     * <p>
     * Грядку проверяет на свет каждый толчок соседа, а свет движок
     * досчитывает своим ходом, не сразу. Деревня при закладке встаёт
     * за один тик: лампы уже висят, а свет от них ещё не посчитан, — и каждая
     * следующая морковь, толкнув соседнюю, осыпала её на пол. Без толчка
     * посев доживает до света, а дальше держится сам.
     */
    private static final int SOWN = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    /**
     * Насколько близко должно быть хранилище, чтобы билдер брал материалы сам.
     * <p>
     * Решение заказчика: стройка под боком у склада идёт без курьера, а
     * вынесенная за околицу требует людей. Так выбор места становится
     * решением игрока, а не декорацией.
     */
    public static final int NEARBY_STORAGE = 12;

    /**
     * Насколько далеко билдер тянется от того места, куда пришёл.
     * <p>
     * Он шагает к участку, а не к каждому блоку: путей считается в десятки
     * раз меньше, а поиск пути идёт в главном потоке сервера и потому
     * дороже всего остального вместе.
     * <p>
     * Рука длиннее человеческой, и это осознанно. Короткая заставляет билдера
     * перебегать на каждый второй блок стены, а каждый переход — это путь,
     * толкотня у стройки и лишняя секунда, в которую ничего не происходит.
     */
    public static final double WORK_REACH = 7.0;

    /**
     * И насколько выше или ниже себя. <b>Много</b> больше горизонтальной руки,
     * потому что вверх билдер не лазает — стоя на недоделанной стене он ломает
     * себе путь, и это давняя записанная ошибка.
     * <p>
     * Величина обязана покрывать <b>самую высокую схему мода целиком</b>, считая
     * от земли. Прежние шесть блоков покрывали дом первого уровня (пять) и
     * ратушу первого (шесть), но не второй уровень: ратуша восемь блоков, а дом
     * с дымоходом — девять. До крыши билдер не дотягивался ниоткуда, места для
     * работы не находилось вовсе, и тогда {@code standingSpot} отдавала ему
     * <b>сам блок крыши</b> — точку в воздухе. Он шёл к ней, не доходил,
     * решение гнало его снова, и стройка вставала на последних двадцати пяти
     * блоках. Игрок это и описал: «пытается дотянуться, а не может».
     * <p>
     * Двенадцать — с запасом на схемы будущих уровней: девять блоков высоты
     * плюс полблока на центр цели плюс блок на то, что билдер может стоять
     * на блок ниже основания. Дальше расти незачем: чем больше, тем дальше от
     * стройки соглашается встать билдер, а работа должна быть видна рядом
     * со стеной.
     */
    public static final double WORK_HEIGHT = 12.0;

    /** Профессия, без которой стройка не идёт. Данными станет в задаче 1.9. */
    public static final Identifier BUILDER = new Identifier(VillagePax.MOD_ID, "builder");

    /**
     * Инструмент билдера. Нужен только для расчёта добычи с расчистки: без
     * инструмента таблицы добычи ванили не отдают ни булыжника, ни руды,
     * и расчистка каменистой площадки не приносила бы ничего.
     * <p>
     * Каждый раз новый: {@code ItemStack} изменяем, а общего изменяемого
     * состояния в моде быть не должно.
     */
    private static ItemStack tool() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    public enum Outcome {
        /** Поселения или здания с такими идентификаторами нет. */
        NOT_FOUND,
        /** Для типа и уровня здания не нашлось схемы в датапаках. */
        NO_SCHEMATIC,
        /** Чанк здания не загружен: ставить блоки в него нельзя. */
        NOT_LOADED,
        /** В поселении нет ни одного строителя. */
        NO_BUILDER,
        /** Не хватает материалов на складе. Билдер ждёт. */
        WAITING_FOR_MATERIALS,
        /** Шаги выполнены, стройка продолжается. */
        ADVANCED,
        /** Здание достроено на этом обращении. */
        FINISHED,
        /** Здание уже готово, делать нечего. */
        ALREADY_DONE,
        /** Следующий блок дальше вытянутой руки: билдеру надо перейти. */
        OUT_OF_REACH
    }

    private BuildJob() {
    }

    /**
     * Схема для здания: приписка {@code _lvlN} к типу.
     * <p>
     * Соглашение об именовании <b>осталось намеренно</b> и после того, как
     * типы зданий стали данными. Оно не наделяет здание правами — права
     * объявляет роль в файле типа, — а лишь называет файл схемы. Лишнее
     * поле «где моя схема» в каждом типе было бы обрядом без смысла.
     */
    public static Identifier schematicId(Building building) {
        return new Identifier(building.type().getNamespace(),
                building.type().getPath() + "_lvl" + building.level());
    }

    /** Обратное соглашение: {@code norman/town_hall_lvl1} → тип {@code norman/town_hall}. */
    public static Optional<Identifier> buildingTypeOf(Identifier schematic) {
        String path = schematic.getPath();
        int marker = path.lastIndexOf("_lvl");
        return marker < 0
                ? Optional.empty()
                : Optional.of(new Identifier(schematic.getNamespace(), path.substring(0, marker)));
    }

    public static Optional<Integer> levelOf(Identifier schematic) {
        String path = schematic.getPath();
        int marker = path.lastIndexOf("_lvl");
        if (marker < 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(path.substring(marker + "_lvl".length())));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    public static boolean isUnderConstruction(Building building) {
        return building.progress() == BuildProgress.PLANNED
                || building.progress() == BuildProgress.BUILDING
                || building.progress() == BuildProgress.DAMAGED;
    }

    /**
     * Продвинуть стройку. Изменения идут через {@link SettlementManager#apply},
     * поэтому состояние помечается грязным и переживает перезаход в мир.
     */
    public static Outcome advance(ServerWorld world, SettlementManager manager,
                                  UUID settlementId, UUID buildingId, int maxSteps) {
        return advance(world, manager, settlementId, buildingId, maxSteps, null);
    }

    /**
     * То же, но с ограничением по вытянутой руке: билдер работает только там,
     * куда дотянулся с того места, где стоит. {@code workFrom} равный
     * {@code null} снимает ограничение — так стройку гоняют тесты и отладка.
     */
    public static Outcome advance(ServerWorld world, SettlementManager manager,
                                  UUID settlementId, UUID buildingId, int maxSteps, Vec3d workFrom) {
        return manager.apply(settlementId, settlement -> settlement.building(buildingId)
                        .map(building -> run(world, settlement, building, maxSteps, workFrom))
                        .orElse(Outcome.NOT_FOUND))
                .orElse(Outcome.NOT_FOUND);
    }

    private static Outcome run(ServerWorld world, Settlement settlement, Building building,
                               int maxSteps, Vec3d workFrom) {
        if (!isUnderConstruction(building)) {
            return Outcome.ALREADY_DONE;
        }
        if (!hasBuilder(settlement)) {
            return Outcome.NO_BUILDER;
        }

        Schematic schematic = SchematicLoader.get(schematicId(building)).orElse(null);
        if (schematic == null) {
            return Outcome.NO_SCHEMATIC;
        }

        if (!footprintIsLoaded(world, building, schematic)) {
            // Ставить блоки в незагруженный чанк — значит принудительно его
            // загрузить. Проверяется весь след, а не чанк якоря: здание 7x7
            // спокойно ложится на два чанка. Далёкие поселения тикают
            // упрощённо, это задача 1.12.
            return Outcome.NOT_LOADED;
        }

        Warehouse warehouse = Warehouse.of(world, settlement);
        List<BuildStep> steps = schematic.plan().steps();

        // Слоты декора план не расчищает: см. perform. Считаются один раз
        // на обращение — их два-три на здание.
        Set<BlockPos> decorSlots = new HashSet<>(
                pointsOfInterest(building, schematic, MarkerKind.DECOR));

        // Повреждённое здание чинится по той же схеме, и план обязан пройтись
        // заново: у него nextStep стоит в конце с прошлой стройки, иначе
        // ремонт мгновенно "завершился" бы, не поставив ни блока.
        if (building.progress() == BuildProgress.DAMAGED) {
            building.restartBuilding();
        }

        // Сперва площадка, и только потом стены. Жалоба заказчика:
        // «пусть берёт точку, ровняет все блоки под неё и только тогда
        // строит». Прежде опора подводилась после стройки и из излишков,
        // и дом честно оставался на сваях, если камня не хватило.
        //
        // Один раз, на первом шаге: рельеф под следом меняется однажды.
        // Ремонт проходит план заново и площадку тоже — за годы под домом
        // успевает появиться и яма от крипера.
        //
        // И только тем народам, кто на земле стоит. Гному ровнять нечего —
        // под полом сплошной камень; эльфу ровнять <b>нельзя</b>: подсыпка
        // увидела бы ствол под углом настила, сочла бы это землёй и вывела
        // из-под дома земляной столб. Дом в кронах висит нарочно.
        if (building.nextStep() == 0 && Footing.of(settlement).levelsTheGround()) {
            Terrace.level(world, building, schematic);
            // И сразу за площадкой — откос вокруг неё: без него под домом
            // ровно, а в шаге за стеной прежний склон, и вход висит над ним.
            Grading.grade(world, settlement, building, schematic);
            // Землю переносят разом, и пасущуюся рядом скотину ею засыпает.
            com.villagepax.sim.work.Standing.rescueBuried(world, around(building, schematic));
        }
        building.setProgress(BuildProgress.BUILDING);

        // Бюджет тратят только шаги, на которых билдер что-то сделал. Пустая
        // расчистка на ровном месте — а это первая треть плана — иначе съедала
        // бы минуту игрового времени, и игрок смотрел бы, как ничего не
        // происходит. По той же причине ремонт проскакивает целые стены,
        // задерживаясь только на пробоинах.
        int worked = 0;
        while (worked < maxSteps && building.nextStep() < steps.size()) {
            StepResult result = perform(world, settlement, warehouse, building, schematic,
                    steps.get(building.nextStep()), workFrom, decorSlots);

            if (result == StepResult.BLOCKED) {
                return Outcome.WAITING_FOR_MATERIALS;
            }
            if (result == StepResult.TOO_FAR || result == StepResult.OCCUPIED) {
                // Пусть билдер перейдёт. Если он уже успел поработать —
                // это обычное продвижение, а не простой.
                return worked > 0 ? Outcome.ADVANCED : Outcome.OUT_OF_REACH;
            }

            BuildStep laid = steps.get(building.nextStep());
            building.advanceStep();
            if (result == StepResult.WORKED) {
                worked++;
            }
            openTheWay(world, settlement, warehouse, building, schematic, steps, laid);
        }

        if (building.nextStep() >= steps.size()) {
            building.setProgress(BuildProgress.DONE);

            // Слоты декора заполняются здесь, а не по ходу плана: так они
            // достаются и после ремонта, который проходит план заново.
            Decor.fill(world, settlement, building, schematic);

            // Опоры здесь больше нет: земля под домом выровнена ещё
            // до первого блока — см. Terrace. Держать два способа
            // засыпать одну и ту же пустоту значило бы засыпать её
            // дважды и по-разному.

            // Ступени у входов. После площадки, а не до: она поднимает
            // подошву, и крыльцо надо мерить уже от готового порога.
            Access.porch(world, building, schematic);
            com.villagepax.sim.work.Standing.rescueBuried(world, around(building, schematic));

            returnLeftovers(world, warehouse, building);
            announceDone(world, settlement, building);
            return Outcome.FINISHED;
        }
        return Outcome.ADVANCED;
    }

    /**
     * Сделать дом проходимым, как только лёг цоколь.
     * <p>
     * Заказчик: «пусть строители строят сразу так, чтоб можно было войти
     * в дом». До этого опора и ступени клались <b>после крыши</b>, и всё
     * время стройки дом стоял с порогом на высоте пояса: ни игроку зайти,
     * ни жителю. А стройка идёт долго.
     * <p>
     * Теперь ступени кладутся в тот миг, когда нижний венец закончен
     * и порог обрёл опору, — дальше стены растут уже вокруг проходимого
     * входа.
     * <p>
     * <b>Только ступени, и только они.</b> Подсыпка опоры под цоколь
     * тратит материалы со склада, и запущенная посреди стройки она
     * объедает саму стройку: первая версия этой правки уронила четырнадцать
     * проверок разом, и все — про нехватку материалов. Опора остаётся
     * на потом, когда материалы уже не нужны никому. Крыльцо же даровое:
     * оно повторяет блок из-под порога, и ждать ему нечего.
     * <p>
     * Условие стоит O(1) на шаг: сравниваются вид и высота положенного
     * и следующего шагов плана. Никаких обходов, никакого состояния
     * в данных — «куча проверок, но не нагружая систему».
     * <p>
     * Считается именно <b>укладка</b>, а не любой шаг: план идёт сперва
     * расчисткой всего объёма, и на ней порога ещё нет.
     * <p>
     * И на <b>каждом</b> законченном ярусе, а не только на цоколе. Порог
     * опирается на разное: у дома — на цоколь, у поля — на грядку слоем
     * выше, и привязка к одному ярусу промахивалась то там, то тут.
     * Ярусов у здания пять, крыльцо на готовой земле не делает ничего
     * и выходит на первой же проверке, — дешевле, чем искать, какой ярус
     * «тот самый».
     */
    private static void openTheWay(ServerWorld world, Settlement settlement, Warehouse warehouse,
                                   Building building, Schematic schematic, List<BuildStep> steps,
                                   BuildStep laid) {
        if (!laid.placesBlock() || building.nextStep() >= steps.size()) {
            return;
        }
        BuildStep next = steps.get(building.nextStep());
        if (!next.placesBlock() || next.pos().getY() <= laid.pos().getY()) {
            // Ярус ещё кладётся: рано.
            return;
        }
        Access.porch(world, building, schematic);
    }

    /**
     * Сказать хозяину колонии, что здание готово.
     * <p>
     * До сих пор об этом сообщал только колокол на площадке — а его слышно
     * лишь тем, кто стоит рядом. В логе настоящей игры видно, как игрок
     * размечает дом, завозит материалы и <b>больше ничего не узнаёт</b>:
     * готово оно или нет, приходится ходить и смотреть.
     * <p>
     * Сообщается о своих зданиях. Деревни народов строят сами, и их
     * стройки игрока не касаются.
     */
    private static void announceDone(ServerWorld world, Settlement settlement,
                                     Building building) {
        settlement.owner().player().ifPresent(owner -> {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(owner);
            if (player != null) {
                player.sendMessage(Text.translatable("villagepax.build.done",
                        Text.translatable(TownHallNet.buildingKey(building.type())),
                        Text.literal(String.valueOf(building.level()))), false);
            }
        });
    }

    /** Чем кончился один шаг. */
    private enum StepResult {
        /** Билдер поработал: снёс или поставил блок. Шаг стоит тика. */
        WORKED,
        /** Делать было нечего: место уже пустое или нужный блок уже стоит. */
        SKIPPED,
        /** Не хватило материала: индекс не двигается, билдер ждёт. */
        BLOCKED,

        /**
         * До места не дотянуться. Проверяется <b>после</b> отсечения пустых
         * шагов намеренно: иначе билдер шёл бы к каждой из ста одиннадцати
         * пустых позиций расчистки, и вся выгода от «шагать к участку»
         * пропала бы.
         */
        TOO_FAR,

        /**
         * В клетке кто-то стоит. Обрабатывается как «слишком далеко»:
         * билдер отойдёт, и следующим решением шаг повторится.
         */
        OCCUPIED
    }

    /**
     * Освободить клетку от живых, прежде чем что-то в неё ставить.
     * <p>
     * Написано по следам смерти строителя. Ставить блок в того, кто в этой
     * клетке стоит, нельзя никогда, но с костром это ещё и смертельно:
     * житель оказывается <b>внутри огня</b> и не может выйти — из огненного
     * узла ваниль не строит пути вовсе.
     * <p>
     * Своих жителей отодвигают всегда: стоять в стене им незачем.
     * А <b>ждёт</b> стройка только опасного — костра, огня, лавы. Ждать
     * ради обычной стены нельзя: клетка может не освободиться никогда
     * (житель зажат в углу, игрок смотрит на стройку), и дом не
     * достроится вовсе. Обычный блок ваниль вытолкнет сама, а костёр
     * убил бы.
     *
     * @return можно ли ставить
     */
    static boolean makeRoomFor(ServerWorld world, Building building, Schematic schematic,
                               BlockPos where, BlockState laid) {
        if (laid.getCollisionShape(world, where).isEmpty() && !Hazards.hurts(laid)) {
            return true;
        }

        Box cell = new Box(where);
        for (CitizenEntity citizen : world.getEntitiesByClass(CitizenEntity.class, cell,
                alive -> true)) {
            citizen.stepAsideFrom(where);
        }
        // Скотину — тоже, и прочь со стройки, а не на шаг: курица, оказавшаяся
        // в кладке, задыхается — после закладки деревни на холмах на земле
        // лежали перья и тушки, дом встал разом прямо на стаю. Отойди она
        // на соседнюю клетку внутри — её накроет стол или очаг.
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, cell,
                mob -> !(mob instanceof CitizenEntity))) {
            com.villagepax.sim.work.Standing.freeOutside(world, where, spot ->
                    BuildSite.covers(building.anchor(), schematic.size(), building.rotation(), spot))
                    .ifPresent(spot -> mob.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(),
                            spot.getZ() + 0.5, mob.getYaw(), mob.getPitch()));
        }

        if (!Hazards.hurts(laid)) {
            return true;
        }
        return world.getEntitiesByClass(LivingEntity.class, cell,
                alive -> !alive.isSpectator()).isEmpty();
    }

    private static StepResult perform(ServerWorld world, Settlement settlement, Warehouse warehouse,
                                      Building building, Schematic schematic, BuildStep step,
                                      Vec3d workFrom, Set<BlockPos> decorSlots) {
        BlockPos where = worldPos(building, schematic.size(), step.pos());

        // Центр поселения не перестраивается никогда.
        //
        // Там стоит ратуша, и в ней — стартовое хранилище колонии. Схема,
        // накрывшая это место, снесла бы ратушу вместе со складом: материалы
        // высыпались бы на землю, пульт перестал бы открываться, а стройка
        // встала бы, потому что брать со склада стало нечего. Ровно это
        // и случилось при первой же попытке улучшить ратушу до второго
        // уровня — её собственная схема ложится поверх её же блока.
        if (where.equals(settlement.center())) {
            return StepResult.SKIPPED;
        }

        if (!step.placesBlock()) {
            if (world.getBlockState(where).isAir()) {
                return StepResult.SKIPPED;
            }
            // Засеянную грядку ремонт не перепахивает: под посев расчищают
            // породу и сорняк, а не морковь, которая уже растёт.
            if (step.paletteIndex() != BuildStep.NO_BLOCK && world.getBlockState(where)
                    .isOf(schematic.blockAt(step.paletteIndex()).getBlock())) {
                return StepResult.SKIPPED;
            }

            // Расчистка подхода выходит за след здания, и там уже может
            // стоять соседний дом. Прогрызть в нём дыру ради прохода —
            // не проход, а погром: билдер просто обходит чужую стену,
            // а житель обойдёт её сам.
            if (!BuildSite.covers(building.anchor(), schematic.size(), building.rotation(), where)
                    && (standsInAnother(settlement, building, where)
                    || Grading.isEarth(world.getBlockState(where)))) {
                // И землю подход не роет: его дело — дерево и хлам у двери,
                // а грунт ровняет откос. Выкопай землю на уровне порога —
                // и на склоне, поднимающемся от двери, выйдет ямка в три
                // шага, а за ней уступ в два блока; это поймала проверка
                // «деревня пони на холмах».
                return StepResult.SKIPPED;
            }

            // Слот декора план не расчищает. Обстановку в него поставил
            // {@link Decor} — бесплатно, потому что заявка на материалы
            // считается по плану, а декор в плане только место. Снеси его
            // ремонтом, сдай на склад и поставь новый бесплатно — и колония
            // начнёт печатать предметы из воздуха при каждом ремонте.
            // Заодно это защищает то, что игрок поставил в слот сам.
            if (decorSlots.contains(where)) {
                return StepResult.SKIPPED;
            }
            if (isTooFar(workFrom, where)) {
                return StepResult.TOO_FAR;
            }
            boolean nearby = storageIsNearby(warehouse, building);
            boolean trunk = world.getBlockState(where).isIn(BlockTags.LOGS);
            salvage(world, warehouse, building, where, nearby);
            Sounds.broke(world, where, world.getBlockState(where));
            world.setBlockState(where, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            if (trunk) {
                fellTrunkAbove(world, settlement, warehouse, building, where, nearby);
            }
            return StepResult.WORKED;
        }

        BlockState planned = schematic.blockAt(step.paletteIndex()).rotate(building.rotation());

        // Нужный блок уже на месте — ни материала, ни установки. Без этого
        // ремонт повреждённого здания списывал бы со склада всю схему целиком,
        // хотя починить надо пару блоков.
        if (world.getBlockState(where).isOf(planned.getBlock())) {
            return StepResult.SKIPPED;
        }
        if (isTooFar(workFrom, where)) {
            return StepResult.TOO_FAR;
        }

        boolean nearStorage = storageIsNearby(warehouse, building);
        Optional<Item> material = Materials.itemFor(planned);
        if (material.isPresent()
                && !takeMaterial(world, warehouse, building, material.get(), nearStorage)) {
            return StepResult.BLOCKED;
        }

        salvage(world, warehouse, building, where, nearStorage);

        // Состояние досчитывается по окружению до установки, а соседей
        // уведомляем после: иначе стёкла и заборы встают несоединёнными —
        // setBlockState, в отличие от установки блока игроком, окружение
        // не смотрит.
        //
        // Пустой ответ означает «так стоять нельзя»: у кровати нет второй
        // половины, потому что она ещё не поставлена. Ставим как задумано —
        // следующий шаг плана сделает блок законным. Без этой оговорки
        // ни одна кровать в схеме не выжила бы: первая же половина
        // уничтожила бы себя досчётом.
        BlockState settled = Block.postProcessState(planned, world, where);
        BlockState laid = settled.isAir() ? planned : settled;

        if (!makeRoomFor(world, building, schematic, where, laid)) {
            // Материал уже взят со склада — вернём: иначе он пропал бы,
            // а шаг всё равно повторится следующим решением.
            material.ifPresent(item -> Warehouse.of(world, settlement)
                    .addOrScatter(world, where, new ItemStack(item, 1)));
            return StepResult.OCCUPIED;
        }

        world.setBlockState(where, laid,
                step.category() == BuildCategory.SOWING ? SOWN : Block.NOTIFY_ALL);
        // Что кладётся внутрь поставленного: бельё на верёвку и прочая
        // обстановка. Для всего остального — одна проверка типа блока.
        Furnishings.stock(world, where, laid, where.asLong());
        Sounds.placed(world, where, laid);
        return StepResult.WORKED;
    }


    /**
     * Остатки со стройплощадки возвращаются на склад, когда здание сдано.
     * <p>
     * Без этого они исчезают из экономики колонии. Запас площадки — счётчик,
     * физически предметы нигде не лежат: курьер приносит по полстопки, зданию
     * нужно четыре блока, и двадцать восемь просто перестают существовать.
     * Игрок этого даже не заметит — просто однажды кончатся материалы.
     * <p>
     * Сюда же попадает и добыча с расчистки, сложенная у стройки, когда склад
     * был далеко.
     */
    private static void returnLeftovers(ServerWorld world, Warehouse warehouse, Building building) {
        ItemTally stock = building.stock();
        if (stock.isEmpty()) {
            return;
        }

        for (Map.Entry<Identifier, Integer> entry : Map.copyOf(stock.contents()).entrySet()) {
            Item item = Registries.ITEM.get(entry.getKey());
            int count = entry.getValue();
            stock.take(entry.getKey(), count);

            while (count > 0) {
                int chunk = Math.min(count, item.getMaxCount());
                warehouse.addOrScatter(world, building.anchor(), new ItemStack(item, chunk));
                count -= chunk;
            }
        }
    }

    /**
     * Откуда билдер берёт материал: сначала из того, что курьер сложил
     * у стройки, и только потом со склада — если тот под боком.
     */
    private static boolean takeMaterial(ServerWorld world, Warehouse warehouse, Building building,
                                        Item item, boolean nearStorage) {
        if (building.stock().take(Registries.ITEM.getId(item), 1)) {
            return true;
        }
        if (!nearStorage) {
            return false;
        }
        if (warehouse.take(item, 1)) {
            return true;
        }
        // Нет — так сделаем. Колония умеет ровно то, что умеет игрок
        // за верстаком, и требовать от него принести фахверк руками
        // незачем.
        return Crafting.make(world, warehouse, item) && warehouse.take(item, 1);
    }

    public static boolean storageIsNearby(Warehouse warehouse, Building building) {
        return warehouse.hasContainerWithin(building.anchor(), NEARBY_STORAGE);
    }

    /** {@code null} снимает ограничение — так стройку гоняют тесты и отладка. */
    private static boolean isTooFar(Vec3d workFrom, BlockPos target) {
        return workFrom != null && !withinReach(workFrom, target);
    }

    /**
     * Дотягивается ли работник до блока, стоя вот здесь.
     * <p>
     * По горизонтали — длина руки, по вертикали — заметно больше. Разделение
     * не косметическое: с общим шаровым радиусом билдер обязан <b>залезть
     * на стройку</b>, чтобы доложить второй ряд стены, а стоя на недоделанной
     * стене он ломает себе путь — навигация ведёт его вниз, решение гонит
     * наверх, и он топчется на месте. Стоя на земле у стены, он выкладывает
     * её всю, и это ещё и выглядит как работа, а не как лазание.
     */
    public static boolean withinReach(Vec3d workFrom, BlockPos target) {
        Vec3d centre = Vec3d.ofCenter(target);
        double dx = workFrom.x - centre.x;
        double dz = workFrom.z - centre.z;

        return dx * dx + dz * dz <= WORK_REACH * WORK_REACH
                && Math.abs(workFrom.y - centre.y) <= WORK_HEIGHT;
    }

    public static boolean withinReach(BlockPos standing, BlockPos target) {
        return withinReach(Vec3d.ofBottomCenter(standing), target);
    }

    /**
     * То же, но с запасом: место для работы выбирается так, чтобы житель
     * дотянулся, даже подойдя к нему не в упор. К цели он подходит со своей
     * стороны — иначе работники толкаются на одном блоке.
     */
    public static boolean withinReach(BlockPos standing, BlockPos target, double margin) {
        Vec3d centre = Vec3d.ofCenter(target);
        Vec3d from = Vec3d.ofBottomCenter(standing);
        double dx = from.x - centre.x;
        double dz = from.z - centre.z;
        double reach = Math.max(0.0, WORK_REACH - margin);

        return dx * dx + dz * dz <= reach * reach
                && Math.abs(from.y - centre.y) <= WORK_HEIGHT;
    }

    /**
     * Снести то, что мешает, и сдать добычу на склад.
     * <p>
     * Решение заказчика: расчистка приносит материалы. Поэтому выбор места —
     * экономическое решение, а не только эстетическое: стройка в лесу дороже
     * по времени, но выгоднее по брёвнам.
     */
    private static void salvage(ServerWorld world, Warehouse warehouse, Building building,
                                BlockPos pos, boolean nearStorage) {
        BlockState existing = world.getBlockState(pos);
        if (existing.isAir()) {
            return;
        }

        BlockEntity blockEntity = existing.hasBlockEntity() ? world.getBlockEntity(pos) : null;
        for (ItemStack drop : Block.getDroppedStacks(existing, world, pos, blockEntity, null, tool())) {
            if (drop.isEmpty()) {
                continue;
            }
            if (nearStorage) {
                warehouse.addOrScatter(world, pos, drop);
            } else {
                // Склад далеко: снесённое остаётся у стройки и пойдёт в стены.
                // Тащить брёвна через полкарты, чтобы принести обратно, —
                // работа ради работы.
                building.stock().add(Registries.ITEM.getId(drop.getItem()), drop.getCount());
            }
        }
    }

    /** След здания с откосом и крыльцом вокруг — там, где стройка кладёт землю и ступени. */
    private static Box around(Building building, Schematic schematic) {
        Vec3i footprint = BuildSite.rotatedSize(schematic.size(), building.rotation());
        BlockPos anchor = building.anchor();
        return new Box(anchor, anchor.add(footprint)).expand(Grading.MARGIN + Grading.APPROACH + 1,
                Terrace.DEEP, Grading.MARGIN + Grading.APPROACH + 1);
    }

    private static boolean footprintIsLoaded(ServerWorld world, Building building, Schematic schematic) {
        Vec3i footprint = BuildSite.rotatedSize(schematic.size(), building.rotation());
        BlockPos anchor = building.anchor();
        BlockPos far = anchor.add(footprint.getX() - 1, 0, footprint.getZ() - 1);
        return world.isChunkLoaded(anchor)
                && world.isChunkLoaded(far)
                && world.isChunkLoaded(new BlockPos(anchor.getX(), anchor.getY(), far.getZ()))
                && world.isChunkLoaded(new BlockPos(far.getX(), anchor.getY(), anchor.getZ()));
    }

    public static BlockPos worldPos(Building building, Vec3i size, BlockPos local) {
        return BuildSite.toWorld(building.anchor(), size, building.rotation(), local);
    }

    /** Точки интереса готового здания в координатах мира. */
    public static List<BlockPos> pointsOfInterest(Building building, Schematic schematic, MarkerKind kind) {
        return schematic.plan().positionsOf(kind).stream()
                .map(local -> worldPos(building, schematic.size(), local))
                .toList();
    }

    /**
     * Свалить остаток ствола над расчищенной клеткой.
     * <p>
     * Без этого над проходом висит обрубок: билдер вырубает клетку в рост
     * человека, а дерево было в пять брёвен. Игрок видит пенёк, парящий
     * над крыльцом, и это хуже, чем дерево, — дерево хоть выглядело деревом.
     * <p>
     * Столбом вверх, а не всем деревом по связности: обход веток — работа
     * лесоруба, у него на неё и топор, и грядка в роще. Билдеру довольно
     * колонны над своей клеткой; листва без ствола осыпается сама.
     * <p>
     * Брёвна идут туда же, куда всё снесённое, — на склад или в запас
     * стройки. Дерево у порога оборачивается материалом для стены.
     */
    private static void fellTrunkAbove(ServerWorld world, Settlement settlement, Warehouse warehouse,
                                       Building building, BlockPos base, boolean nearStorage) {
        for (int up = 1; up <= TRUNK; up++) {
            BlockPos at = base.up(up);
            if (!world.getBlockState(at).isIn(BlockTags.LOGS)
                    || standsInAnother(settlement, building, at)) {
                return;
            }
            salvage(world, warehouse, building, at, nearStorage);
            world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
    }

    /**
     * Занята ли эта точка следом другого здания поселения.
     * <p>
     * Спрашивается только про клетки <b>за следом</b> своего здания —
     * то есть про расчистку подхода, а это шесть клеток на вход. Внутри
     * своего следа вопрос не имеет смысла: два здания там пересечься
     * не могут, это проверено при разметке.
     */
    private static boolean standsInAnother(Settlement settlement, Building building, BlockPos where) {
        for (Building other : settlement.buildings()) {
            if (other.id().equals(building.id())) {
                continue;
            }
            Schematic plan = SchematicLoader.get(schematicId(other)).orElse(null);
            if (plan != null && BuildSite.covers(other.anchor(), plan.size(), other.rotation(), where)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBuilder(Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            if (citizen.profession().filter(BUILDER::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
