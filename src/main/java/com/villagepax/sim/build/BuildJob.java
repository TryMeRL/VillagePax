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
import com.villagepax.screen.TownHallNet;
import net.minecraft.entity.LivingEntity;
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

    /** Один блок за полсекунды: стройка должна быть видна как процесс. */
    public static final int TICKS_PER_STEP = 10;

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

            building.advanceStep();
            if (result == StepResult.WORKED) {
                worked++;
            }
        }

        if (building.nextStep() >= steps.size()) {
            building.setProgress(BuildProgress.DONE);

            // Слоты декора заполняются здесь, а не по ходу плана: так они
            // достаются и после ремонта, который проходит план заново.
            Decor.fill(world, settlement, building, schematic);

            returnLeftovers(world, warehouse, building);
            announceDone(world, settlement, building);
            return Outcome.FINISHED;
        }
        return Outcome.ADVANCED;
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
    private static boolean makeRoomFor(ServerWorld world, BlockPos where, BlockState laid) {
        if (laid.getCollisionShape(world, where).isEmpty() && !Hazards.hurts(laid)) {
            return true;
        }

        Box cell = new Box(where);
        for (CitizenEntity citizen : world.getEntitiesByClass(CitizenEntity.class, cell,
                alive -> true)) {
            citizen.stepAsideFrom(where);
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
            salvage(world, warehouse, building, where, storageIsNearby(warehouse, building));
            Sounds.broke(world, where, world.getBlockState(where));
            world.setBlockState(where, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
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

        if (!makeRoomFor(world, where, laid)) {
            // Материал уже взят со склада — вернём: иначе он пропал бы,
            // а шаг всё равно повторится следующим решением.
            material.ifPresent(item -> Warehouse.of(world, settlement)
                    .addOrScatter(world, where, new ItemStack(item, 1)));
            return StepResult.OCCUPIED;
        }

        world.setBlockState(where, laid, Block.NOTIFY_ALL);
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

    private static boolean hasBuilder(Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            if (citizen.profession().filter(BUILDER::equals).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
