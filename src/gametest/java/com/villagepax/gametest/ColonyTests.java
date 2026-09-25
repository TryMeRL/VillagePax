package com.villagepax.gametest;

import com.villagepax.block.ModBlocks;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureKind;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.ColonyFounder;
import com.villagepax.sim.Founding;
import com.villagepax.sim.FoundingOutcome;
import com.villagepax.block.entity.TownHallBlockEntity;
import net.minecraft.server.world.ServerWorld;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.entity.ModEntities;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Levels;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Guide;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.config.Config;
import com.villagepax.core.config.Configs;
import com.villagepax.screen.ColonyNet;
import com.villagepax.sim.build.Roads;
import com.villagepax.sim.work.Needs;
import com.villagepax.sim.Villages;
import net.minecraft.block.Block;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.work.HaulJob;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.Housing;
import com.villagepax.screen.BuildOrders;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkTicker;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Колония: основание, дом, распорядок, еда, рост, вечерний сбор, настройки и играбельность.
 * <p>
 * Помощники и постоянные — в {@link GameTestSupport}.
 */
public class ColonyTests extends GameTestSupport {

    // --- основа: датапак, реестры, сохранение, основание и тела жителей ---

    /**
     * Каждый рецепт мода открывается в книге рецептов.
     * <p>
     * Книга показывает только открытые рецепты, а открывает их рецептурное
     * достижение. Без него рецепт крафтится, но в книге не появляется
     * никогда — и скамью можно сделать, только заранее зная раскладку.
     * Достижения пишет {@code tools/make-recipe-advancements.py}; здесь
     * проверяется, что игра их прочла и что награда каждого — свой рецепт.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyRecipeOpensInTheRecipeBook(TestContext context) {
        net.minecraft.server.MinecraftServer server = context.getWorld().getServer();
        Set<Identifier> rewarded = new java.util.HashSet<>();
        for (net.minecraft.advancement.Advancement advancement
                : server.getAdvancementLoader().getAdvancements()) {
            rewarded.addAll(List.of(advancement.getRewards().getRecipes()));
        }

        List<Identifier> ours = server.getRecipeManager().keys()
                .filter(id -> id.getNamespace().equals("villagepax"))
                .sorted()
                .toList();
        if (ours.size() < 30) {
            context.throwGameTestException("Рецептов мода подозрительно мало: " + ours.size());
        }
        List<Identifier> locked = ours.stream().filter(id -> !rewarded.contains(id)).toList();
        if (!locked.isEmpty()) {
            context.throwGameTestException("Рецепты, которые книга не откроет никогда: " + locked);
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cultureIsLoadedFromDatapack(TestContext context) {
        Culture norman = CultureManager.get(NORMAN);

        if (norman == null) {
            context.throwGameTestException("Культура " + NORMAN + " не загружена. Загружены: " + CultureManager.ids());
        }
        if (norman.kind() != CultureKind.HISTORICAL) {
            context.throwGameTestException("Ожидался исторический народ, получено: " + norman.kind());
        }
        if (norman.namePools().settlement().isEmpty()) {
            context.throwGameTestException("У норманнов пустой список названий поселений");
        }
        if (norman.buildings().isEmpty()) {
            context.throwGameTestException("У норманнов не объявлено ни одного здания");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blocksAreRegisteredAndPlaceable(TestContext context) {
        Identifier townHallId = Registries.BLOCK.getId(ModBlocks.TOWN_HALL);
        if (!townHallId.equals(new Identifier("villagepax", "town_hall"))) {
            context.throwGameTestException("Ратуша зарегистрирована под чужим идентификатором: " + townHallId);
        }

        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.TOWN_HALL.getDefaultState());
        context.expectBlock(ModBlocks.TOWN_HALL, pos);

        BlockPos markerPos = new BlockPos(2, 1, 1);
        context.setBlockState(markerPos, ModBlocks.MARKER_WORKSTATION.getDefaultState());
        context.expectBlock(ModBlocks.MARKER_WORKSTATION, markerPos);

        // Маркеры обязаны ломаться мгновенно: билдер снимает их сотнями за постройку.
        float hardness = ModBlocks.MARKER_WORKSTATION.getDefaultState()
                .getHardness(context.getWorld(), markerPos);
        if (hardness > 0.5f) {
            context.throwGameTestException("Маркер слишком прочный: " + hardness);
        }

        context.setBlockState(pos, Blocks.AIR.getDefaultState());
        context.setBlockState(markerPos, Blocks.AIR.getDefaultState());
        context.complete();
    }

    /**
     * Настоящая проверка сохранения: поселение проходит через тот же
     * PersistentStateManager, что и в живой игре, записывается в NBT
     * и читается обратно. Это перезагрузка мира без перезапуска сервера.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void settlementSurvivesWorldReload(TestContext context) {
        SettlementManager manager = SettlementManager.get(context.getWorld());
        UUID player = UUID.randomUUID();

        // Далёкие координаты, чтобы не пересечься с поселениями других тестов.
        BlockPos center = new BlockPos(1_000_000, 70, 1_000_000);
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Бовуар", center);
        colony.setLevel(SettlementLevel.VILLAGE);

        Building lumberjack = Building.planned(
                new Identifier("villagepax", "norman/lumberjack"),
                center.add(12, 0, 4),
                BlockRotation.CLOCKWISE_180);
        Citizen worker = evenNewborn("Thibault", "de Beauvoir", NORMAN, Gender.MALE);
        lumberjack.assign(worker.id());
        colony.addBuilding(lumberjack);
        colony.addCitizen(worker);

        try {
            manager.setDirty(false);
            manager.add(colony);

            if (!manager.isDirty()) {
                context.throwGameTestException(
                        "Менеджер не помечен грязным после добавления — состояние не сохранится");
            }

            NbtCompound saved = manager.writeNbt(new NbtCompound());
            SettlementManager reloaded = SettlementManager.fromNbt(saved);

            Optional<Settlement> restored = reloaded.byId(colony.id());
            if (restored.isEmpty()) {
                context.throwGameTestException("После перезагрузки поселение исчезло");
            }

            Settlement after = restored.get();
            if (!after.name().equals("Бовуар")) {
                context.throwGameTestException("Имя поселения потерялось: " + after.name());
            }
            if (after.level() != SettlementLevel.VILLAGE) {
                context.throwGameTestException("Уровень поселения потерялся: " + after.level());
            }
            if (!after.owner().isOwnedBy(player)) {
                context.throwGameTestException("Владелец колонии потерялся");
            }
            if (after.buildings().size() != 1 || after.citizens().size() != 1) {
                context.throwGameTestException("Потеряны здания или жители: зданий "
                        + after.buildings().size() + ", жителей " + after.citizens().size());
            }
            if (after.buildings().get(0).rotation() != BlockRotation.CLOCKWISE_180) {
                context.throwGameTestException("Поворот схемы потерялся — здание встанет не так");
            }
            if (after.buildings().get(0).workers().size() != 1) {
                context.throwGameTestException("Назначение работника потерялось");
            }

            // Границы должны работать и после перезагрузки.
            if (reloaded.at(center).isEmpty()) {
                context.throwGameTestException("Поселение не находится по своей же ратуше");
            }
            if (reloaded.at(center.add(2000, 0, 0)).isPresent()) {
                context.throwGameTestException("Границы поселения растянулись слишком далеко");
            }

            // Изменение через update тоже обязано помечать состояние грязным.
            manager.setDirty(false);
            manager.update(colony.id(), settlement -> settlement.rename("Бовуар-сюр-Мер"));
            if (!manager.isDirty()) {
                context.throwGameTestException("update не пометил состояние грязным");
            }
        } finally {
            manager.remove(colony.id());
        }

        context.complete();
    }

    /** Два поселения не должны спорить за одни и те же чанки. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void overlappingSettlementIsRejected(TestContext context) {
        SettlementManager manager = SettlementManager.get(context.getWorld());
        BlockPos center = new BlockPos(2_000_000, 70, 2_000_000);

        Settlement first = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Рокмон", center);
        Settlement tooClose = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Валберж", center.add(32, 0, 0));
        Settlement farEnough = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Фонтене", center.add(2000, 0, 0));

        try {
            manager.add(first);

            if (manager.conflictWith(tooClose).isEmpty()) {
                context.throwGameTestException("Пересечение границ не обнаружено");
            }
            if (manager.conflictWith(farEnough).isPresent()) {
                context.throwGameTestException("Далёкое поселение ошибочно считается конфликтующим");
            }
        } finally {
            manager.remove(first.id());
        }

        context.complete();
    }

    /**
     * Полный путь основания колонии в живом мире: чертёж ставит ратушу,
     * блок связывается с поселением, поселение попадает в менеджер.
     */
    // Своя пачка: тесты внутри одной пачки идут параллельно в общем мире, а
    // границы поселения — два чанка. Любой сосед, зарегистрировавший своё
    // поселение, ломал бы основание по "слишком близко", и падение зависело
    // бы от порядка запуска.
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "founding")
    public void colonyIsFoundedOnSolidGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        // Своя земля и своя полка по высоте. Основание теперь дарит колонии
        // дом и поле, а они ищут ровное место кольцами вокруг ратуши —
        // и в общем мире проверок находят его НА ЧУЖОЙ ДЕЛЯНКЕ, если своей
        // земли нет. Соседняя проверка потом падает непонятно отчего.
        List<BlockPos> floor = new ArrayList<>();
        for (int x = -8; x <= 12; x++) {
            for (int z = -8; z <= 12; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 30, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }
        BlockPos target = context.getAbsolutePos(new BlockPos(1, 31, 1));

        FoundingOutcome outcome = ColonyFounder.foundAt(world, player, NORMAN, target);

        if (!(outcome instanceof FoundingOutcome.Founded founded)) {
            context.throwGameTestException("Колония не основана: "
                    + ((FoundingOutcome.Refused) outcome).translationKey());
            return;
        }

        Settlement colony = founded.settlement();
        try {
            if (!world.getBlockState(target).isOf(ModBlocks.TOWN_HALL)) {
                context.throwGameTestException("Ратуша не поставлена: " + world.getBlockState(target));
            }

            if (!(world.getBlockEntity(target) instanceof TownHallBlockEntity townHall)) {
                context.throwGameTestException("У ратуши нет блок-энтити");
                return;
            }
            if (!townHall.settlementId().equals(Optional.of(colony.id()))) {
                context.throwGameTestException("Ратуша не связана со своим поселением");
            }

            if (manager.byId(colony.id()).isEmpty()) {
                context.throwGameTestException("Поселение не попало в менеджер");
            }
            // Ратуша — первое здание колонии и сразу готовое. Считать
            // здания поштучно больше нельзя: рядом с ратушей встают дом
            // и поле первого надела, и сколько их выйдет, решает земля.
            Building hall = colony.buildings().get(0);
            if (!BuildingTypes.isTownHall(hall.type()) || hall.progress() != BuildProgress.DONE) {
                context.throwGameTestException("Ратуша не записана как готовое здание поселения: "
                        + hall.type() + " " + hall.progress());
            }

            // Первый житель — строитель, и он обязан появиться сразу с телом:
            // иначе игрок основал колонию и никого не увидел, а без строителя
            // не встанет ни одно здание.
            if (colony.population() != 1) {
                context.throwGameTestException("Ожидался один житель, получено " + colony.population());
            }
            Citizen firstBuilder = colony.citizens().get(0);
            if (!firstBuilder.profession().equals(Optional.of(Founding.PROFESSION_BUILDER))) {
                context.throwGameTestException("Первый житель не строитель: " + firstBuilder.profession());
            }
            if (firstBuilder.firstName().isEmpty()) {
                context.throwGameTestException("У строителя нет имени");
            }
            UUID bodyId = firstBuilder.entityUuid().orElse(null);
            if (bodyId == null || !(world.getEntity(bodyId) instanceof CitizenEntity)) {
                context.throwGameTestException("У первого строителя нет тела в мире");
            }

            // Одна колония на игрока: вторая попытка того же игрока отклоняется.
            BlockPos second = context.getAbsolutePos(new BlockPos(3, 31, 1));
            FoundingOutcome again = ColonyFounder.foundAt(world, player, NORMAN, second);
            if (!(again instanceof FoundingOutcome.Refused refusedAgain)
                    || !refusedAgain.translationKey().equals(Founding.KEY_ALREADY_OWNER)) {
                context.throwGameTestException("Вторая колония того же игрока должна отклоняться, получено: " + again);
            }

            // Другому игроку мешают уже границы, а не правило одной колонии.
            FoundingOutcome neighbour = ColonyFounder.foundAt(world, UUID.randomUUID(), NORMAN, second);
            if (!(neighbour instanceof FoundingOutcome.Refused refusedNeighbour)
                    || !refusedNeighbour.translationKey().equals(Founding.KEY_TOO_CLOSE)) {
                context.throwGameTestException("Соседняя колония должна отклоняться по границам, получено: " + neighbour);
            }
        } finally {
            razeHolding(world, colony);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(target, Blocks.AIR.getDefaultState());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
        }

        context.complete();
    }

    /** Ратуша не должна вставать в воздухе. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void foundingRequiresSolidGround(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos midAir = context.getAbsolutePos(new BlockPos(1, 5, 1));

        FoundingOutcome outcome = ColonyFounder.foundAt(world, UUID.randomUUID(), NORMAN, midAir);

        if (!(outcome instanceof FoundingOutcome.Refused refused)
                || !refused.translationKey().equals(ColonyFounder.KEY_BAD_GROUND)) {
            context.throwGameTestException("В воздухе колония основываться не должна, получено: " + outcome);
        }
        if (world.getBlockState(midAir).isOf(ModBlocks.TOWN_HALL)) {
            context.throwGameTestException("Ратуша всё-таки поставлена при отказе");
        }

        context.complete();
    }

    /**
     * Проверка ключевого архитектурного решения мода: тело жителя одноразовое,
     * сам житель — нет. Выгрузка чанка должна вернуть состояние в данные,
     * загрузка — восстановить тело там же, где его оставили.
     * <p>
     * Настоящую выгрузку чанка внутри игрового теста не устроить, поэтому
     * вызываются ровно те же методы, которые вызывают события чанков.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void citizenBodySurvivesChunkCycle(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos base = context.getAbsolutePos(new BlockPos(1, 2, 1));
        ChunkPos chunk = new ChunkPos(base);

        Settlement colony = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Тестовое", base);
        Citizen citizen = evenNewborn("Rollo", "de Beauvoir", NORMAN, Gender.MALE);
        citizen.setPosition(Vec3d.ofBottomCenter(base));
        colony.addCitizen(citizen);

        try {
            manager.add(colony);

            if (CitizenSpawner.onChunkLoad(world, chunk) != 1) {
                context.throwGameTestException("Тело жителя не появилось при загрузке чанка");
            }

            UUID bodyUuid = citizen.entityUuid().orElse(null);
            if (bodyUuid == null) {
                context.throwGameTestException("Житель не запомнил своё тело");
                return;
            }
            if (!(world.getEntity(bodyUuid) instanceof CitizenEntity body)) {
                context.throwGameTestException("Тело жителя не найдено в мире");
                return;
            }
            if (body.getCustomName() == null
                    || !body.getCustomName().getString().equals("Rollo de Beauvoir")) {
                context.throwGameTestException("Имя жителя не перенесено на тело");
            }

            // Житель отошёл: это изменение обязано пережить выгрузку.
            Vec3d moved = new Vec3d(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
            body.setPosition(moved);

            if (CitizenSpawner.unloadChunk(world, chunk) != 1) {
                context.throwGameTestException("Тело не убрано при выгрузке чанка");
            }
            if (!body.isRemoved()) {
                context.throwGameTestException("Тело осталось в мире после выгрузки");
            }
            if (citizen.entityUuid().isPresent()) {
                context.throwGameTestException("Ссылка на исчезнувшее тело не очищена");
            }
            if (citizen.position().isEmpty() || citizen.position().get().squaredDistanceTo(moved) > 0.01) {
                context.throwGameTestException("Позиция не вернулась в данные: " + citizen.position());
            }

            // И снова загрузка: житель должен появиться там же, где исчез.
            if (CitizenSpawner.onChunkLoad(world, chunk) != 1) {
                context.throwGameTestException("Житель не возродился при повторной загрузке чанка");
            }

            UUID secondBody = citizen.entityUuid().orElse(null);
            if (secondBody == null || secondBody.equals(bodyUuid)) {
                context.throwGameTestException("Ожидалось новое тело для того же жителя");
                return;
            }
            if (!(world.getEntity(secondBody) instanceof CitizenEntity restored)) {
                context.throwGameTestException("Новое тело не найдено");
                return;
            }
            if (restored.getPos().squaredDistanceTo(moved) > 0.01) {
                context.throwGameTestException("Житель возродился не там, где исчез: " + restored.getPos());
            }
            if (!restored.citizenId().equals(Optional.of(citizen.id()))) {
                context.throwGameTestException("Новое тело привязано к чужому жителю");
            }

            // Повторная загрузка не должна плодить двойников.
            if (CitizenSpawner.onChunkLoad(world, chunk) != 0) {
                context.throwGameTestException("Повторная загрузка чанка создала лишнее тело");
            }
        } finally {
            CitizenSpawner.unloadChunk(world, chunk);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /** Тело жителя не должно попадать в сохранение чанка: источник правды один. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void citizenBodyIsNeverSaved(TestContext context) {
        ServerWorld world = context.getWorld();
        CitizenEntity body = ModEntities.CITIZEN.create(world);

        if (body == null) {
            context.throwGameTestException("Тип жителя не создаёт сущность");
            return;
        }
        if (body.shouldSave()) {
            context.throwGameTestException("Тело жителя записывается в чанк — источников правды станет два");
        }
        if (ModEntities.CITIZEN.isSaveable()) {
            context.throwGameTestException("Тип жителя объявлен сохраняемым");
        }
        body.discard();

        context.complete();
    }

    /**
     * Раненый житель не должен исцеляться от того, что игрок отошёл.
     * Иначе осада из фазы 3 перестанет работать как механика.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void woundsSurviveChunkCycle(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos base = context.getAbsolutePos(new BlockPos(1, 2, 1));
        ChunkPos chunk = new ChunkPos(base);

        Settlement colony = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Лазарет", base);
        Citizen citizen = evenNewborn("Aubert", "", NORMAN, Gender.MALE);
        citizen.setPosition(Vec3d.ofBottomCenter(base));
        colony.addCitizen(citizen);

        try {
            manager.add(colony);
            CitizenSpawner.onChunkLoad(world, chunk);

            CitizenEntity body = (CitizenEntity) world.getEntity(citizen.entityUuid().orElseThrow());
            body.setHealth(6.0f);

            CitizenSpawner.unloadChunk(world, chunk);

            if (Math.abs(citizen.health() - 6.0f) > 0.01f) {
                context.throwGameTestException("Урон не вернулся в данные, здоровье: " + citizen.health());
            }

            CitizenSpawner.onChunkLoad(world, chunk);
            CitizenEntity restored = (CitizenEntity) world.getEntity(citizen.entityUuid().orElseThrow());
            if (Math.abs(restored.getHealth() - 6.0f) > 0.01f) {
                context.throwGameTestException("Житель исцелился при перезагрузке: " + restored.getHealth());
            }
        } finally {
            CitizenSpawner.unloadChunk(world, chunk);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /** Погибший житель уходит из поселения, а не возрождается целым. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deadCitizenLeavesSettlement(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos base = context.getAbsolutePos(new BlockPos(1, 2, 1));
        ChunkPos chunk = new ChunkPos(base);

        Settlement colony = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Погост", base);
        Citizen citizen = evenNewborn("Ancel", "", NORMAN, Gender.MALE);
        citizen.setPosition(Vec3d.ofBottomCenter(base));
        colony.addCitizen(citizen);

        try {
            manager.add(colony);
            CitizenSpawner.onChunkLoad(world, chunk);

            CitizenEntity body = (CitizenEntity) world.getEntity(citizen.entityUuid().orElseThrow());
            body.remove(Entity.RemovalReason.KILLED);

            if (colony.citizen(citizen.id()).isPresent()) {
                context.throwGameTestException("Погибший житель остался в поселении");
            }
            if (colony.population() != 0) {
                context.throwGameTestException("Население не уменьшилось: " + colony.population());
            }
            if (CitizenSpawner.onChunkLoad(world, chunk) != 0) {
                context.throwGameTestException("Погибший житель возродился");
            }
        } finally {
            CitizenSpawner.unloadChunk(world, chunk);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Житель, чья записанная позиция оказалась за границами поселения,
     * обязан вернуться к ратуше, а не пропасть навсегда.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void citizenOutsideClaimIsRecovered(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos base = context.getAbsolutePos(new BlockPos(1, 2, 1));
        ChunkPos chunk = new ChunkPos(base);

        Settlement colony = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Заблудший", base);
        Citizen citizen = evenNewborn("Foulques", "", NORMAN, Gender.MALE);
        // Далеко за пределами хутора: два чанка радиуса, а это больше тысячи блоков.
        citizen.setPosition(new Vec3d(base.getX() + 2000, base.getY(), base.getZ()));
        colony.addCitizen(citizen);

        try {
            manager.add(colony);

            if (CitizenSpawner.onChunkLoad(world, chunk) != 1) {
                context.throwGameTestException("Заблудившийся житель не вернулся к ратуше");
            }

            CitizenEntity body = (CitizenEntity) world.getEntity(citizen.entityUuid().orElseThrow());
            if (body.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(colony.center().up())) > 1.0) {
                context.throwGameTestException("Житель появился не у ратуши: " + body.getPos());
            }

            // И его должно удерживать в границах, чтобы он не ушёл снова.
            if (!body.hasPositionTarget()) {
                context.throwGameTestException("Житель ничем не привязан к поселению");
            }
            if (body.getPositionTargetRange() > SettlementLevel.HAMLET.claimRadiusChunks() * 16f + 0.5f) {
                context.throwGameTestException("Привязка шире границ поселения: " + body.getPositionTargetRange());
            }
        } finally {
            CitizenSpawner.unloadChunk(world, chunk);
            manager.remove(colony.id());
        }

        context.complete();
    }

    // --- задача 1.8: дом, распорядок, еда, счастье ---

    /**
     * Достроенный дом даёт настоящие кровати, и жители их получают.
     * <p>
     * Кровати стоят в схеме блоками, а не маркерами: кровать занимает две
     * позиции, а маркер одну. Заодно проверяется, что обе половины выжили —
     * досчёт состояния по окружению уничтожил бы первую, пока нет второй.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "home")
    public void builtHouseGivesRealBedsToCitizens(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, house);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не достроился");
            }

            List<BlockPos> spots = Housing.sleepingSpots(world, colony);
            if (spots.size() != 2) {
                context.throwGameTestException("Мест для сна " + spots.size() + ", в схеме дома две кровати");
            }
            for (BlockPos spot : spots) {
                if (!world.getBlockState(spot).isIn(BlockTags.BEDS)) {
                    context.throwGameTestException("Место для сна не кровать: "
                            + world.getBlockState(spot).getBlock());
                }
            }

            Housing.assignBeds(world, colony);
            Citizen builder = colony.citizens().get(0);
            if (builder.isHomeless()) {
                context.throwGameTestException("Строитель остался без кровати при двух свободных");
            }
            if (!spots.contains(builder.bed().orElseThrow())) {
                context.throwGameTestException("Строителю досталось место вне дома");
            }
            if (Housing.freeSpots(world, colony) != 1) {
                context.throwGameTestException("Свободных мест " + Housing.freeSpots(world, colony)
                        + ", ожидалось одно");
            }

            // Сломали кровать — место обязано отобраться, иначе житель будет
            // ходить спать в воздух и числиться устроенным.
            world.setBlockState(builder.bed().orElseThrow(), Blocks.AIR.getDefaultState());
            Housing.assignBeds(world, colony);
            if (builder.bed().map(spot -> world.getBlockState(spot).isIn(BlockTags.BEDS)).orElse(false)
                    == Boolean.FALSE && !builder.isHomeless()) {
                context.throwGameTestException("После сноса кровати житель остался при ней");
            }
        } finally {
            demolish(world, site, house);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Голодный житель идёт к еде и ест — со склада, а не из воздуха. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "home")
    public void hungryCitizenEatsFromStorage(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen eater = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            eater.setSaturation(0);
            eater.addDiscontent();

            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 4));

            // Первое решение отправляет к еде, второе — кормит.
            runWork(world, manager, colony, eater, 1, Schedule.MEAL);
            CitizenEntity body = (CitizenEntity) world.getEntity(eater.entityUuid().orElseThrow());
            if (body.workTarget() == null) {
                context.throwGameTestException("Голодный житель не пошёл к еде");
            }
            runWork(world, manager, colony, eater, 2, Schedule.MEAL);

            if (eater.saturation() <= 0) {
                context.throwGameTestException("Житель не поел: сытость " + eater.saturation());
            }
            if (eater.discontent() != 0) {
                context.throwGameTestException("Поевший житель остался недовольным");
            }
            // Ест, пока не насытится: один хлеб порога голода не закрывает,
            // поэтому важно, что еда именно уходит со склада, а не берётся
            // из воздуха.
            int left = Warehouse.of(world, colony).count(Items.BREAD);
            if (left >= 4) {
                context.throwGameTestException("Житель поел, а со склада ничего не ушло");
            }
            if (eater.saturation() < Needs.nourishment(Items.BREAD)) {
                context.throwGameTestException("Сытость меньше одного хлеба: " + eater.saturation());
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Приёмка задачи 1.8: без еды счастье падает, а при долгом голоде житель
     * уходит из колонии — решение заказчика: предупреждение, полсилы, уход.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "home")
    public void starvingCitizenLeavesAfterAWarning(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen victim = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            int startingHappiness = victim.happiness();
            boolean sawWarning = false;

            // Сроки спрашиваются у жителя, а не у настройки: с появлением
            // характеров ленивый терпит вдвое дольше общего срока, и
            // десяти дней ему мало. Проверка от этого не ослабла — она
            // перестала зависеть от того, кем родился курьер.
            int patience = Needs.leaveAfterDays() * 2 + 2;
            for (int day = 0; day < patience && colony.population() > 0; day++) {
                Needs.newDay(world, manager, colony);
                if (colony.population() > 0
                        && victim.discontent() == Needs.warnAfterDays(victim)) {
                    sawWarning = true;
                }
            }

            if (colony.population() != 0) {
                context.throwGameTestException("Житель не ушёл за " + patience
                        + " голодных дней: недовольство " + victim.discontent());
            }
            if (!sawWarning) {
                context.throwGameTestException("Ухода не предупредили: день предупреждения не наступал");
            }
            if (victim.happiness() >= startingHappiness) {
                context.throwGameTestException("Счастье не упало от голода: было " + startingHappiness
                        + ", стало " + victim.happiness());
            }
            if (victim.entityUuid().map(world::getEntity).isPresent()) {
                context.throwGameTestException("Тело ушедшего жителя осталось в мире");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Ночью житель идёт к своей кровати и ложится. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "home")
    public void citizenSleepsInHisBedAtNight(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos bed = context.getAbsolutePos(new BlockPos(4, 1, 4));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            context.setBlockState(new BlockPos(4, 0, 4), Blocks.STONE);
            world.setBlockState(bed, Blocks.RED_BED.getDefaultState());

            Citizen sleeper = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            sleeper.setBed(bed);

            CitizenEntity body = (CitizenEntity) world.getEntity(sleeper.entityUuid().orElseThrow());

            WorkTicker.decide(world, manager, colony, sleeper, Schedule.SLEEP);
            if (!bed.equals(body.workTarget())) {
                context.throwGameTestException("Ночью житель идёт не к кровати: " + body.workTarget());
            }

            body.refreshPositionAndAngles(bed.getX() + 0.5, bed.getY(), bed.getZ() + 0.5, 0f, 0f);
            WorkTicker.decide(world, manager, colony, sleeper, Schedule.SLEEP);
            if (!body.isSleeping()) {
                context.throwGameTestException("Житель дошёл до кровати и не лёг");
            }

            // Утро: обязан встать, иначе проспит всю игру.
            WorkTicker.decide(world, manager, colony, sleeper, Schedule.MORNING_WORK);
            if (body.isSleeping()) {
                context.throwGameTestException("Житель не встал с рассветом");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(bed, Blocks.AIR.getDefaultState());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Приток жителей: один за игровой день, если есть свободная кровать и еда.
     * <p>
     * Еда в условии не для строгости: без неё пришедший сразу начал бы
     * голодать и уходить, и игрок видел бы вереницу людей, приходящих умирать.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "home")
    public void newcomerArrivesWhenThereIsABedAndFood(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, house);
            BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            Housing.assignBeds(world, colony);

            int before = colony.population();

            // Кровать есть, а еды нет — никто не придёт.
            if (Housing.welcomeNewcomer(world, colony, new java.util.Random(1)).isPresent()) {
                context.throwGameTestException("Житель пришёл в колонию без еды");
            }

            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 8));
            Citizen newcomer = Housing.welcomeNewcomer(world, colony, new java.util.Random(2))
                    .orElse(null);

            if (newcomer == null) {
                context.throwGameTestException("Никто не пришёл при свободной кровати и еде");
                return;
            }
            if (colony.population() != before + 1) {
                context.throwGameTestException("Население не выросло");
            }
            if (newcomer.firstName().isEmpty()) {
                context.throwGameTestException("У пришедшего нет имени");
            }
            if (newcomer.isHomeless()) {
                context.throwGameTestException("Пришедшему не досталось кровати");
            }
            if (!newcomer.profession().equals(Optional.of(HaulJob.COURIER))) {
                context.throwGameTestException("Второму жителю положена профессия курьера, а не "
                        + newcomer.profession());
            }
            if (newcomer.entityUuid().map(world::getEntity).isEmpty()) {
                context.throwGameTestException("У пришедшего нет тела в мире");
            }
        } finally {
            demolish(world, site, house);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Достроенное здание начинает работать в тот же день, а не с рассветом.
     * <p>
     * Кровати и мастерские раздаются на смене суток, и без этой проверки
     * дом, законченный в полдень, стоял бы пустым ровно ту ночь, в которую
     * он готов. Поэтому тест ведёт стройку руками билдера, от начала
     * до конца, и не зовёт раздачу сам.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "home")
    public void finishedHouseGivesBedsWithoutWaitingForDawn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, house);

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            if (!mason.isHomeless()) {
                context.throwGameTestException("Житель с кроватью до постройки дома");
            }

            runWork(world, manager, colony, mason, 300, Schedule.MORNING_WORK);

            if (!site.isOperational()) {
                context.throwGameTestException("Дом не достроился за 300 решений: шаг "
                        + site.nextStep());
            }
            if (mason.isHomeless()) {
                context.throwGameTestException("Дом готов, а житель всё ещё без кровати: "
                        + "раздача ждёт рассвета");
            }
            if (!Housing.sleepingSpots(world, colony).contains(mason.bed().orElseThrow())) {
                context.throwGameTestException("Кровать досталась вне дома");
            }
        } finally {
            demolish(world, site, house);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- рост колонии по уровню ратуши ---

    /**
     * Приёмка решения заказчика: колония растёт вместе с ратушей.
     * <p>
     * До этого уровень не повышал никто, и колония навсегда оставалась
     * хутором — шесть жителей и радиус два чанка. Игрок упирался в стену
     * без объяснения.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "levels")
    public void upgradedTownHallRaisesTheColony(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic bigger = schematic(context, TOWN_HALL_LVL2);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building townHall = raisedTownHall(colony, hall);

        try {
            if (!townHall.type().equals(TOWN_HALL_TYPE) || townHall.level() != 1) {
                context.throwGameTestException("Колония начинается не с ратуши первого уровня: "
                        + townHall.type() + " ур. " + townHall.level());
            }
            if (colony.level() != SettlementLevel.HAMLET) {
                context.throwGameTestException("Новая колония не хутор: " + colony.level().id());
            }

            BuildOrders.Result ordered = BuildOrders.upgrade(manager, colony, townHall.id());
            if (!(ordered instanceof BuildOrders.Result.Placed)) {
                context.throwGameTestException("Улучшение ратуши отвергнуто: " + ordered);
            }
            if (townHall.level() != 2 || townHall.nextStep() != 0) {
                context.throwGameTestException("Улучшение не перезапустило стройку: уровень "
                        + townHall.level() + ", шаг " + townHall.nextStep());
            }
            if (colony.level() != SettlementLevel.HAMLET) {
                context.throwGameTestException("Уровень колонии вырос до постройки — "
                        + "он обязан ждать готового здания");
            }

            stockFor(world, colony, bigger);
            if (BuildJob.advance(world, manager, colony.id(), townHall.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ратуша второго уровня не достроилась");
            }
            Levels.refresh(colony);

            if (colony.level() != SettlementLevel.VILLAGE) {
                context.throwGameTestException("Ратуша второго уровня, а колония всё ещё "
                        + colony.level().id());
            }
            if (colony.level().maxCitizens() <= SettlementLevel.HAMLET.maxCitizens()) {
                context.throwGameTestException("Предел населения не вырос");
            }

            // А вот теперь есть куда: третий уровень появился вместе
            // со ступенью «город», и лестница перестала упираться в стену.
            // Раньше здесь стояло обратное утверждение — «улучшать некуда», —
            // и оно было правдой ровно до тех пор, пока ступени ничего
            // не открывали.
            BuildOrders.Result again = BuildOrders.upgrade(manager, colony, townHall.id());
            if (!(again instanceof BuildOrders.Result.Placed)) {
                context.throwGameTestException("Ратушу не дают поднять до третьего уровня: "
                        + again + ". Тогда ступень «город» недостижима, а пульт её обещает");
            }
            if (townHall.level() != 3) {
                context.throwGameTestException("Улучшение принято, а уровень остался "
                        + townHall.level());
            }
        } finally {
            demolish(world, townHall, bigger);
            demolish(world, townHall, schematic(context,
                    new Identifier("villagepax", "norman/town_hall_lvl3")));
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Улучшение достраивает, а не перестраивает.
     * <p>
     * Игрок сказал прямо: улучшение должно делать здание лучше, а не ломать
     * и создавать новое. Проверяется это <b>расходом</b>, потому что расход
     * не обманешь: если бы билдер сносил дом и ставил заново, он списал бы
     * со склада полную стоимость второго уровня. Здесь он обязан израсходовать
     * заметно меньше половины — остальное уже стоит.
     * <p>
     * И заодно проверяется, что обстановка первого этажа <b>не тронута</b>:
     * ковёр и трибуна старейшины стоят там же. Схемы уровней связаны
     * правилом «второй содержит первый», и правило это держит сам
     * генератор схем, проверяя его при сборке.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "levels")
    public void upgradeAddsInsteadOfRebuilding(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic first = schematic(context, TOWN_HALL_SCHEMATIC);
        Schematic second = schematic(context, TOWN_HALL_LVL2);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building townHall = raisedTownHall(colony, hall);
        BlockPos anchor = townHall.anchor();

        try {
            // Первый уровень ставится целиком и по-настоящему.
            townHall.restartBuilding();
            stockFor(world, colony, first);
            if (BuildJob.advance(world, manager, colony.id(), townHall.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Первый уровень не встал");
            }

            BlockPos carpet = BuildJob.worldPos(townHall, first.size(), new BlockPos(3, 1, 3));
            BlockPos lectern = BuildJob.worldPos(townHall, first.size(), new BlockPos(3, 1, 5));

            if (!(BuildOrders.upgrade(manager, colony, townHall.id())
                    instanceof BuildOrders.Result.Placed)) {
                context.throwGameTestException("Улучшение отвергнуто на пустом месте");
            }
            if (!townHall.anchor().equals(anchor)) {
                context.throwGameTestException("Улучшение перенесло здание");
            }

            int bill = 0;
            for (int count : Materials.required(second).values()) {
                bill += count;
            }
            stockFor(world, colony, second);

            if (BuildJob.advance(world, manager, colony.id(), townHall.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Второй уровень не достроился: шаг "
                        + townHall.nextStep());
            }

            int left = Warehouse.of(world, colony).totalItems();
            int spent = bill - left;
            if (spent > bill / 2) {
                context.throwGameTestException("Улучшение израсходовало " + spent + " из "
                        + bill + " — это перестройка, а не надстройка");
            }

            // Обстановка первого этажа на месте: ничего не снесли.
            if (!world.getBlockState(carpet).isOf(Blocks.RED_CARPET)) {
                context.throwGameTestException("Ковёр первого этажа снесён: теперь там "
                        + world.getBlockState(carpet).getBlock());
            }
            if (!world.getBlockState(lectern).isOf(Blocks.LECTERN)) {
                context.throwGameTestException("Трибуна старейшины снесена: теперь там "
                        + world.getBlockState(lectern).getBlock());
            }
        } finally {
            demolish(world, townHall, second);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Улучшать некуда, если рядом уже стоит здание: растёт-то оно от своего
     * угла, и места ему нужно больше.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "levels")
    public void upgradeRefusesWhenThereIsNoRoom(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building townHall = raisedTownHall(colony, hall);
        BlockPos hallAnchor = townHall.anchor();

        try {
            // Ратуша в тесноте больше не отказывает, и это по делу: её след
            // не растёт вовсе — второй уровень надстраивается вверх на том
            // же пятне. Отказ «некуда расти» остался у полей: они растут
            // на восток и юг, и сосед там мешает. Проверяется именно поле.
            Building neighbour = plan(colony, hall.add(6, 0, 6), HOUSE_TYPE, BlockRotation.NONE);

            BuildOrders.Result grown = BuildOrders.upgrade(manager, colony, townHall.id());
            if (!(grown instanceof BuildOrders.Result.Placed)) {
                context.throwGameTestException("Ратуша не улучшилась, хотя её след не растёт "
                        + "и мешать нечему: " + grown);
            }
            if (!townHall.anchor().equals(hallAnchor)) {
                context.throwGameTestException("Улучшение перенесло ратушу: якорь был "
                        + hallAnchor.toShortString() + ", стал "
                        + townHall.anchor().toShortString());
            }

            // Строящееся здание улучшать нельзя.
            BuildOrders.Result busy = BuildOrders.upgrade(manager, colony, neighbour.id());
            if (!(busy instanceof BuildOrders.Result.Busy)) {
                context.throwGameTestException("Строящееся здание приняли к улучшению: " + busy);
            }

            // И чужого опознавателя тоже нет.
            BuildOrders.Result missing = BuildOrders.upgrade(manager, colony, UUID.randomUUID());
            if (!(missing instanceof BuildOrders.Result.NotFound)) {
                context.throwGameTestException("Улучшение несуществующего здания принято: "
                        + missing);
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Центр поселения не перестраивается ничем.
     * <p>
     * Там ратуша со складом колонии: схема, накрывшая это место, снесла бы
     * её вместе с материалами, пульт перестал бы открываться, а стройка
     * встала бы, потому что брать со склада стало нечего.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "levels")
    public void nothingBuildsOverTheTownHallBlock(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        // Дом размечен прямо поверх ратуши.
        Building site = plan(colony, hall, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);
            BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);

            if (!world.getBlockState(hall).isOf(ModBlocks.TOWN_HALL)) {
                context.throwGameTestException("Ратушу перестроили: на её месте "
                        + world.getBlockState(hall).getBlock());
            }
            if (Warehouse.of(world, colony).containerCount() == 0) {
                context.throwGameTestException("Колония лишилась хранилища");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- задача 1.14: настройки ---

    /**
     * Выключатель в настройках действительно выключает.
     * <p>
     * Приёмка задачи. План называл смертность, но её в моде нет — жители
     * бессмертны по решению заказчика, — поэтому проверяется то же самое
     * на том, что есть: подписи, предел жителей, запас на улицы и сроки
     * голода. Настройка, которая ничего не меняет, хуже отсутствующей:
     * она обещает то, чего не будет.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "config")
    public void configSwitchesActuallySwitch(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, context.getAbsolutePos(new BlockPos(0, 8, 0)),
                HOUSE_TYPE, BlockRotation.NONE);

        try {
            Citizen worker = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());

            // По умолчанию: подпись есть, подпись здания есть.
            WorkTicker.decide(world, manager, colony, worker, Schedule.MORNING_WORK);
            if (body.getCustomName() == null) {
                context.throwGameTestException("По умолчанию подписи над жителем нет");
            }
            if (ColonyNet.mapOf(colony).signs().isEmpty()) {
                context.throwGameTestException("По умолчанию подписей над зданиями нет");
            }

            int roomByDefault = colony.maxCitizens();

            // Выключаем всё, что выключается.
            Configs.override(new Config(false, 48, 2.0,
                    Config.DEFAULT.hungerWarnDays(), 30,
                    Config.DEFAULT.villageTradePerDay(), Config.DEFAULT.villageIncomePerDay(),
                    8, 3, false, false, Config.DEFAULT.carrySlots(), false,
                    Config.DEFAULT.childDays(), Config.DEFAULT.lifeDays(),
                    Config.DEFAULT.mortality(), Config.DEFAULT.structureDistanceChunks()));

            WorkTicker.decide(world, manager, colony, worker, Schedule.MORNING_WORK);
            if (body.getCustomName() != null || body.isCustomNameVisible()) {
                context.throwGameTestException("Подпись над жителем выключена, но висит: "
                        + body.getCustomName());
            }
            if (!ColonyNet.mapOf(colony).signs().isEmpty()) {
                context.throwGameTestException("Подписи над зданиями выключены, но едут клиенту");
            }
            if (colony.maxCitizens() != roomByDefault * 2) {
                context.throwGameTestException("Множитель населения не подействовал: было "
                        + roomByDefault + ", стало " + colony.maxCitizens());
            }
            if (Roads.reserve() != 8) {
                context.throwGameTestException("Запас на улицы не из настроек: " + Roads.reserve());
            }
            if (Needs.leaveAfterDays() != 30) {
                context.throwGameTestException("Срок ухода не из настроек: "
                        + Needs.leaveAfterDays());
            }
            if (WorkTicker.ticksPerDecision() != 3) {
                context.throwGameTestException("Темп решений не из настроек: "
                        + WorkTicker.ticksPerDecision());
            }
            if (Villages.tradePerDay() != Config.DEFAULT.villageTradePerDay()) {
                // Это поле мы не меняли — проверяем, что чтение настроек
                // не перепутало соседние значения местами.
                context.throwGameTestException("Привоз деревням поехал вместе с чужой настройкой");
            }

            // И обратно: подпись возвращается, а не остаётся снятой.
            Configs.override(Config.DEFAULT);
            WorkTicker.decide(world, manager, colony, worker, Schedule.MORNING_WORK);
            if (body.getCustomName() == null) {
                context.throwGameTestException("Подпись не вернулась после включения");
            }
        } finally {
            Configs.override(Config.DEFAULT);
            SchematicLoader.get(BuildJob.schematicId(house))
                    .ifPresent(schematic -> demolish(world, house, schematic));
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- целость данных: объявленное обязано существовать ---

    /**
     * Всё, что культура объявила, должно быть построимо — и наоборот.
     * <p>
     * Эта проверка написана по следам настоящей оплошности: склад и
     * мастерская строителя были объявлены у норманнов с первого дня и
     * <b>не имели схемы</b>. Тип есть, а построить нельзя: деревня молча
     * пропускала их, выбирая, что строить дальше, а игрок не находил их
     * в пульте заказов. Молчание — худший исход из возможных, потому что
     * искать причину негде.
     * <p>
     * Проверяется в три стороны сразу:
     * <ul>
     *   <li>у каждого объявленного здания есть <b>тип</b> с ролью;
     *   <li>у каждого есть <b>схема первого уровня</b>;
     *   <li>у каждой схемы есть народ, который её объявил, — иначе
     *       схема лежит в датапаке мёртвым грузом.
     * </ul>
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "data")
    public void everyDeclaredBuildingCanBeBuilt(TestContext context) {
        List<String> complaints = new ArrayList<>();
        List<Identifier> declared = new ArrayList<>();

        CultureManager.all().forEach((cultureId, culture) -> {
            for (Identifier type : culture.buildings()) {
                declared.add(type);

                if (BuildingTypes.get(type).isEmpty()) {
                    complaints.add(cultureId + " объявил здание " + type
                            + ", а типа с такой ролью нет");
                }
                Identifier first = new Identifier(type.getNamespace(),
                        type.getPath() + "_lvl1");
                if (SchematicLoader.get(first).isEmpty()) {
                    complaints.add(cultureId + " объявил здание " + type
                            + ", а схемы " + first + " нет: построить нельзя");
                }
            }
        });

        for (Identifier schematic : SchematicLoader.ids()) {
            if (BuildJob.levelOf(schematic).orElse(1) != 1) {
                continue;
            }
            Identifier type = BuildJob.buildingTypeOf(schematic).orElse(null);
            if (type != null && !declared.contains(type)) {
                complaints.add("схема " + schematic + " лежит зря: народа, "
                        + "который объявил бы " + type + ", нет");
            }
        }

        if (!complaints.isEmpty()) {
            context.throwGameTestException("Данные не сходятся:\n  "
                    + String.join("\n  ", complaints));
        }

        context.complete();
    }

    /**
     * Ни один блок мода не остаётся без применения.
     * <p>
     * Проверка написана по следам второй настоящей оплошности того же
     * рода. Блок белья был зарегистрирован, отрисован, включён в тег
     * декора и имел рецепт — а <b>ни одна культура его не перечисляла
     * и ни одна схема не ставила</b>. Встретить его в игре было нельзя
     * иначе как достав из творческой вкладки.
     * <p>
     * Блок считается применённым, если он стоит в какой-нибудь схеме или
     * назван в наборе декора какого-нибудь народа. Исключение одно —
     * ратуша: её ставит основание колонии, а не схема, и это записано
     * прямо здесь, чтобы исключение нельзя было завести молча.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "data")
    public void noModBlockIsLeftUnused(TestContext context) {
        Set<Block> used = new HashSet<>();

        for (Identifier id : SchematicLoader.ids()) {
            Schematic schematic = SchematicLoader.get(id).orElseThrow();
            for (BuildStep step : schematic.plan().steps()) {
                if (step.placesBlock()) {
                    used.add(schematic.blockAt(step.paletteIndex()).getBlock());
                }
            }
        }
        CultureManager.all().values().forEach(culture -> {
            for (Identifier id : culture.decor()) {
                used.add(Registries.BLOCK.get(id));
            }
        });

        // Маркеры в готовом плане не встречаются: разборщик схемы меняет
        // их на сундук или воздух, потому что маркер — это МЕСТО, а не
        // блок. Спрашивать о них надо у плана, и тогда маркер, которого
        // не ставит ни одна схема, всё равно найдётся.
        for (Identifier id : SchematicLoader.ids()) {
            Schematic schematic = SchematicLoader.get(id).orElseThrow();
            for (MarkerKind kind : MarkerKind.values()) {
                if (!schematic.plan().positionsOf(kind).isEmpty()) {
                    used.add(Registries.BLOCK.get(
                            new Identifier("villagepax", kind.blockPath())));
                }
            }
        }

        // Ратушу ставит основание колонии, а не схема: ColonyFounder
        // кладёт её блок сам, и в схеме её нет.
        used.add(ModBlocks.TOWN_HALL);

        // И третий законный способ быть применённым: РЕЦЕПТ. Блок, который
        // игрок может скрафтить, живой по определению — он для того
        // и заведён, чтобы игрок строил им сам. Строительный набор
        // (ступени, плиты, стены наших материалов) именно таков: деревни
        // народов кладут стены кубами, а карниз и скат — дело хозяина.
        //
        // Правило при этом не размякло: блок без схемы, без декора И без
        // рецепта достать неоткуда, кроме творческой вкладки, — и проверка
        // на него по-прежнему падает.
        for (net.minecraft.recipe.Recipe<?> recipe
                : context.getWorld().getRecipeManager().values()) {
            ItemStack made = recipe.getOutput(context.getWorld().getRegistryManager());
            if (made.getItem() instanceof net.minecraft.item.BlockItem block) {
                used.add(block.getBlock());
            }
        }

        List<Identifier> idle = new ArrayList<>();
        ModBlocks.registered().forEach((id, block) -> {
            if (!used.contains(block)) {
                idle.add(id);
            }
        });

        if (!idle.isEmpty()) {
            context.throwGameTestException("Блоки мода, которых нет ни в схемах, "
                    + "ни в наборах декора: " + idle.stream().map(Identifier::toString)
                    .sorted().toList());
        }

        context.complete();
    }

    // --- играбельность ---

    /**
     * Норманны селятся и на открытой земле, и это правда мира, а не файла.
     * <p>
     * Написано по следам первой настоящей игры: деревня встала в двух
     * с лишним тысячах блоков от спавна, потому что вокруг не было леса.
     * Мод, который начинается с «найди деревню», не может начинаться
     * с двухчасовой прогулки.
     * <p>
     * Проверяется <b>тег из датапака</b>, а не строка в json культуры:
     * ошибка в пути файла тега (а он лежит не там, где все остальные —
     * в {@code tags/worldgen/biome}) не ломает ничего заметно. Тег просто
     * оказывается пустым, и народ молча перестаёт селиться где бы то ни было.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void normanHomeTagIsLoadedAndOpen(TestContext context) {
        ServerWorld world = context.getWorld();
        Registry<net.minecraft.world.biome.Biome> biomes =
                world.getRegistryManager().get(RegistryKeys.BIOME);

        TagKey<net.minecraft.world.biome.Biome> home = TagKey.of(RegistryKeys.BIOME,
                new Identifier("villagepax", "norman_home"));
        var listed = biomes.getEntryList(home).orElse(null);

        if (listed == null || listed.size() == 0) {
            context.throwGameTestException("Тег биомов норманнов не загрузился: "
                    + "деревни этого народа не встанут нигде");
            return;
        }

        boolean plains = false;
        boolean forest = false;
        for (var entry : listed) {
            plains = plains || entry.matchesId(new Identifier("minecraft", "plains"));
            forest = forest || entry.matchesId(new Identifier("minecraft", "forest"));
        }
        if (!plains) {
            context.throwGameTestException("Равнины не в списке: норманн на открытой земле "
                    + "не поселится, а именно там чаще всего стоит игрок");
        }
        if (!forest) {
            context.throwGameTestException("Лес выпал из списка: у норманнов он основной");
        }

        // И культура смотрит именно на этот тег.
        Culture norman = CultureManager.get(NORMAN);
        if (norman == null || !"#villagepax:norman_home".equals(norman.spawn().biomes())) {
            context.throwGameTestException("Культура норманнов не смотрит на свой тег: "
                    + (norman == null ? "культуры нет" : norman.spawn().biomes()));
        }

        context.complete();
    }

    /**
     * Путевые записки — настоящая книга, а не пустой предмет.
     * <p>
     * Книга собирается кодом, и собрать её неправильно легко: страница —
     * это JSON текстового компонента, и строка, которая им не является,
     * не покажет ничего. Проверяется то, что увидит игрок: подписанная
     * книга, все страницы на месте, каждая читается обратно.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void wayfarerNotesAreARealBook(TestContext context) {
        ItemStack book = Guide.book();

        if (!book.isOf(Items.WRITTEN_BOOK)) {
            context.throwGameTestException("Записки — не подписанная книга: " + book);
            return;
        }
        NbtCompound nbt = book.getNbt();
        if (nbt == null || !nbt.contains("pages")) {
            context.throwGameTestException("У книги нет страниц вовсе");
            return;
        }

        NbtList pages = nbt.getList("pages", NbtElement.STRING_TYPE);
        if (pages.size() != Guide.pageCount()) {
            context.throwGameTestException("Страниц " + pages.size() + " вместо "
                    + Guide.pageCount());
        }
        for (int page = 0; page < pages.size(); page++) {
            Text read = Text.Serializer.fromJson(pages.getString(page));
            if (read == null) {
                context.throwGameTestException("Страница " + (page + 1) + " не читается");
                return;
            }
            if (!(read.getContent() instanceof net.minecraft.text.TranslatableTextContent)) {
                context.throwGameTestException("Страница " + (page + 1)
                        + " не переводится: в книге окажется английский текст навсегда");
            }
        }

        context.complete();
    }

    /**
     * Разметил здание — узнал, что нести.
     * <p>
     * До этой строки список материалов игрок узнавал только от билдера,
     * который уже взялся за стройку и встал без камня. То есть он размечал
     * дом и уходил в шахту наугад — а это ровно та жалоба, с которой
     * у модов этого жанра и начинается «непонятно, что делать».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void markingABuildingSaysWhatToBring(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            Map<Item, Integer> empty = BuildOrders.stillNeeded(world, colony, site);
            if (empty.isEmpty()) {
                context.throwGameTestException("На пустом складе не нужно ничего — "
                        + "значит список считается не оттуда");
                return;
            }
            if (!empty.containsKey(Items.COBBLESTONE)) {
                context.throwGameTestException("В списке нет булыжника, а цоколь из него: "
                        + empty.keySet());
            }

            Text line = BuildOrders.shoppingLine(empty, 2);
            if (line.getString().isBlank()) {
                context.throwGameTestException("Список пуст строкой, хотя не пуст числом");
            }

            // Завезли всё — и нести больше нечего.
            stockFor(world, colony, schematic);
            Map<Item, Integer> stocked = BuildOrders.stillNeeded(world, colony, site);
            if (!stocked.isEmpty()) {
                context.throwGameTestException("Склад полон, а список всё просит: " + stocked);
            }
        } finally {
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- вечерний сбор ---

    /**
     * Вечером житель идёт на площадь, а дойдя — расходится.
     * <p>
     * Просьба заказчика про «мелкую жизнь». Досуг был пустым: цель
     * снималась, работника забирала прогулка, и деревня пустела как раз
     * в те часы, когда игрок чаще всего дома и смотрит на неё.
     * <p>
     * Проверяются три обещания: место у ратуши и на твёрдом; одно и то же
     * от решения к решению (иначе житель метался бы); отпущенное, когда
     * дошёл (иначе он стоял бы в строю кругом).
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "evening")
    public void eveningBringsCitizensToTheSquare(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(8, 9, 8));
        List<BlockPos> square = new ArrayList<>();

        try {
            // Площадь: без твёрдой земли стоять негде.
            for (int x = 0; x <= 16; x++) {
                for (int z = 0; z <= 16; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    square.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall);
            Citizen idler = hireWithBody(world, colony, FarmJob.FARMER, hall.up(12));
            CitizenEntity body = (CitizenEntity) world.getEntity(idler.entityUuid().orElseThrow());

            try {
                WorkTicker.decide(world, manager, colony, idler, Schedule.LEISURE);
                BlockPos spot = body.workTarget();

                if (spot == null) {
                    context.throwGameTestException("Вечером житель никуда не идёт");
                }
                double away = Math.max(Math.abs(spot.getX() - hall.getX()),
                        Math.abs(spot.getZ() - hall.getZ()));
                if (away > 10) {
                    context.throwGameTestException("Собрался не у ратуши, а в " + away
                            + " блоках от неё");
                }
                if (!world.getBlockState(spot.down()).isSolidBlock(world, spot.down())) {
                    context.throwGameTestException("Место сбора висит в воздухе: "
                            + spot.toShortString());
                }

                // То же место и на следующем решении: иначе он будет метаться.
                WorkTicker.decide(world, manager, colony, idler, Schedule.LEISURE);
                if (!spot.equals(body.workTarget())) {
                    context.throwGameTestException("Место сбора сменилось за одно решение: было "
                            + spot.toShortString() + ", стало " + body.workTarget());
                }

                // Дошёл — цель отпущена, дальше он топчется сам.
                body.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                        0f, 0f);
                WorkTicker.decide(world, manager, colony, idler, Schedule.LEISURE);
                if (body.workTarget() != null) {
                    context.throwGameTestException("Пришедший на площадь всё ещё держит цель: "
                            + body.workTarget());
                }
            } finally {
                discardBodies(world, colony);
                manager.remove(colony.id());
            }
        } finally {
            for (BlockPos at : square) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

}
