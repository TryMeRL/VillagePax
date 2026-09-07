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
import com.villagepax.sim.Owner;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import com.villagepax.core.ModTags;
import com.villagepax.sim.build.BuildCategory;
import com.villagepax.sim.build.BuildPlan;
import com.villagepax.sim.build.BuildPlanner;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.PointOfInterest;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.build.SchematicParser;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resource.Resource;
import net.minecraft.util.math.Vec3i;

import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Materials;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

import java.util.List;
import java.util.Map;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Игровые тесты — всё, что нельзя проверить без запущенного мира:
 * загрузка датапаков, реестры, сохранение состояния, поведение жителей.
 */
public class VillagePaxGameTests implements FabricGameTest {

    private static final Identifier NORMAN = new Identifier("villagepax", "norman");

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
        Citizen worker = Citizen.newborn("Thibault", "de Beauvoir", NORMAN, Gender.MALE);
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
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "founding")
    public void colonyIsFoundedOnSolidGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        BlockPos target = context.getAbsolutePos(new BlockPos(1, 2, 1));

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
            if (colony.buildings().size() != 1
                    || colony.buildings().get(0).progress() != BuildProgress.DONE) {
                context.throwGameTestException("Ратуша не записана как готовое здание поселения");
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
            context.setBlockState(new BlockPos(3, 1, 1), Blocks.STONE);
            BlockPos second = context.getAbsolutePos(new BlockPos(3, 2, 1));
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
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(target, Blocks.AIR.getDefaultState());
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
        Citizen citizen = Citizen.newborn("Rollo", "de Beauvoir", NORMAN, Gender.MALE);
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
        Citizen citizen = Citizen.newborn("Aubert", "", NORMAN, Gender.MALE);
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
        Citizen citizen = Citizen.newborn("Ancel", "", NORMAN, Gender.MALE);
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
        Citizen citizen = Citizen.newborn("Foulques", "", NORMAN, Gender.MALE);
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

    // --- задача 1.5: схемы зданий и план стройки ---

    private static final Identifier TOWN_HALL_SCHEMATIC =
            new Identifier("villagepax", "norman/town_hall_lvl1");

    private static final Identifier TOWN_HALL_SCHEMATIC_FILE =
            new Identifier("villagepax", "villagepax/schematics/norman/town_hall_lvl1.nbt");

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void schematicIsLoadedFromDatapack(TestContext context) {
        Schematic schematic = loadedTownHall(context);
        Vec3i size = schematic.size();

        if (!size.equals(new Vec3i(7, 6, 7))) {
            context.throwGameTestException("Размер схемы " + size.toShortString() + ", ожидался 7, 6, 7");
        }
        if (schematic.palette().isEmpty()) {
            context.throwGameTestException("Палитра схемы пуста");
        }

        int volume = size.getX() * size.getY() * size.getZ();
        if (schematic.blocks().size() != volume) {
            context.throwGameTestException("Блоков в схеме " + schematic.blocks().size()
                    + ", а объём " + volume + " — часть схемы потерялась при разборе");
        }

        // Каждый блок схемы обязан стать либо установкой, либо расчисткой:
        // потерянный блок — это дырка в готовом здании.
        BuildPlan plan = schematic.plan();
        long clearing = plan.steps().stream().filter(step -> !step.placesBlock()).count();
        if (plan.blockCount() + clearing != plan.steps().size()) {
            context.throwGameTestException("Шаги не сходятся: установок " + plan.blockCount()
                    + ", расчисток " + clearing + ", всего " + plan.steps().size());
        }
        if (plan.steps().size() != volume) {
            context.throwGameTestException("Шагов " + plan.steps().size() + " при объёме " + volume);
        }

        context.complete();
    }

    /**
     * Маркеры — то, чем одна схема описывает и геометрию здания, и его логику.
     * Проверяется вся сделка: каждый род найден, ни один маркер не остался
     * блоком к установке, и место каждого расчищается.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void schematicMarkersBecomePointsOfInterest(TestContext context) {
        Schematic schematic = loadedTownHall(context);
        BuildPlan plan = schematic.plan();

        for (MarkerKind kind : MarkerKind.values()) {
            if (plan.positionsOf(kind).isEmpty()) {
                context.throwGameTestException("В схеме ратуши не найден маркер: " + kind.id());
            }
        }

        for (BuildStep step : plan.steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            BlockState state = schematic.blockFor(step);
            if (BuildPlanner.markerKind(state).isPresent()) {
                context.throwGameTestException("Маркер попал в шаги установки блоков: "
                        + step.pos().toShortString());
            }
        }

        Set<BlockPos> cleared = new HashSet<>();
        for (BuildStep step : plan.steps()) {
            if (step.category() == BuildCategory.CLEAR) {
                cleared.add(step.pos());
            }
        }
        for (PointOfInterest poi : plan.pois()) {
            if (!cleared.contains(poi.pos())) {
                context.throwGameTestException("Место маркера " + poi.kind().id()
                        + " не расчищается: " + poi.pos().toShortString());
            }
        }

        context.complete();
    }

    /**
     * Приёмка задачи 1.5: список блоков детерминирован и одинаков между
     * запусками. Порядок сверяется с планом, который загрузчик построил
     * при старте сервера — то есть в другом прогоне и на другом потоке.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void buildPlanIsDeterministic(TestContext context) {
        Schematic loaded = loadedTownHall(context);

        Optional<Resource> resource = context.getWorld().getServer()
                .getResourceManager().getResource(TOWN_HALL_SCHEMATIC_FILE);
        if (resource.isEmpty()) {
            context.throwGameTestException("Файл схемы не найден: " + TOWN_HALL_SCHEMATIC_FILE);
        }

        Schematic first;
        Schematic second;
        try (InputStream stream = resource.orElseThrow().getInputStream()) {
            NbtCompound nbt = NbtIo.readCompressed(stream);
            first = SchematicParser.parse(TOWN_HALL_SCHEMATIC, nbt, Registries.BLOCK.getReadOnlyWrapper());
            second = SchematicParser.parse(TOWN_HALL_SCHEMATIC, nbt, Registries.BLOCK.getReadOnlyWrapper());
        } catch (Exception failure) {
            context.throwGameTestException("Схема не перечиталась: " + failure);
            return;
        }

        if (!first.plan().equals(second.plan())) {
            context.throwGameTestException("Два разбора одного файла дали разные планы стройки");
        }
        if (!first.plan().equals(loaded.plan())) {
            context.throwGameTestException("План из файла не совпал с планом загрузчика");
        }

        // План считается один раз: приёмка требует кэширования.
        if (loaded.plan() != loaded.plan()) {
            context.throwGameTestException("План пересчитывается на каждое обращение");
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void buildPlanOrdersClearingThenStructureThenDecor(TestContext context) {
        BuildPlan plan = loadedTownHall(context).plan();

        // Если в схеме нет всех трёх категорий, порядок между ними не
        // подтверждён ничем и тест был бы пустым.
        EnumSet<BuildCategory> present = EnumSet.noneOf(BuildCategory.class);
        for (BuildStep step : plan.steps()) {
            present.add(step.category());
        }
        if (present.size() != BuildCategory.values().length) {
            context.throwGameTestException("В плане есть только " + present
                    + " — порядок категорий проверять нечем");
        }

        BuildCategory category = null;
        int height = 0;
        for (BuildStep step : plan.steps()) {
            if (step.category() != category) {
                if (category != null && step.category().ordinal() < category.ordinal()) {
                    context.throwGameTestException("Категории перемешаны: " + category.id()
                            + " встретилась перед " + step.category().id());
                }
                category = step.category();
                height = step.pos().getY();
                continue;
            }
            int current = step.pos().getY();
            if (category.topDown() ? current > height : current < height) {
                context.throwGameTestException("Высота идёт не туда в " + category.id()
                        + ": " + height + " -> " + current);
            }
            height = current;
        }

        context.complete();
    }

    /**
     * Тег вместо предиката по блокстейту — решение задачи 1.5, и проверять
     * его надо на том самом случае, на котором предикат бы и сломался:
     * ступени крыши не полный куб, поэтому {@code isSolid} у них ложь,
     * и крыша уехала бы вноситься после мебели.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void decorTagSeparatesFurnitureFromStructure(TestContext context) {
        if (!Blocks.LECTERN.getDefaultState().isIn(ModTags.BUILD_DECOR)) {
            context.throwGameTestException(
                    "Тег villagepax:build_decor не загружен или не содержит пюпитр");
        }
        if (Blocks.DARK_OAK_PLANKS.getDefaultState().isIn(ModTags.BUILD_DECOR)) {
            context.throwGameTestException("Планки попали в декор — стены вносились бы последними");
        }

        if (BuildPlanner.categoryOf(Blocks.LECTERN.getDefaultState()) != BuildCategory.DECOR) {
            context.throwGameTestException("Пюпитр не признан обстановкой");
        }
        if (BuildPlanner.categoryOf(Blocks.COBBLESTONE.getDefaultState()) != BuildCategory.STRUCTURE) {
            context.throwGameTestException("Булыжник не признан несущим");
        }
        if (BuildPlanner.categoryOf(Blocks.DARK_OAK_STAIRS.getDefaultState()) != BuildCategory.STRUCTURE) {
            context.throwGameTestException("Ступени крыши уехали в декор");
        }

        context.complete();
    }

    private static Schematic loadedTownHall(TestContext context) {
        Optional<Schematic> schematic = SchematicLoader.get(TOWN_HALL_SCHEMATIC);
        if (schematic.isEmpty()) {
            context.throwGameTestException("Схема " + TOWN_HALL_SCHEMATIC
                    + " не загружена. Загружены: " + SchematicLoader.ids());
        }
        return schematic.orElseThrow();
    }

    // --- задача 1.6: билдер строит ---

    private static final Identifier TOWN_HALL_TYPE = new Identifier("villagepax", "norman/town_hall");

    /**
     * Приёмка задачи 1.6: дать билдеру схему и полный склад — здание построено
     * полностью и совпадает со схемой поблочно.
     * <p>
     * Состояния сравниваются по блоку, а не целиком: {@code postProcessState}
     * досчитывает у ступеней форму, а у стёкол соединения по окружению — как
     * и при установке блока игроком. Поворот проверяется отдельно, по тем
     * свойствам, которые он и меняет.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void builderRaisesWholeBuilding(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 0));

        Settlement colony = colonyWithBuilder(manager, anchor);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(colony, schematic);

            BuildJob.Outcome outcome = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (outcome != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Стройка не завершилась: " + outcome
                        + ", шаг " + site.nextStep() + " из " + schematic.plan().steps().size());
            }
            if (site.progress() != BuildProgress.DONE) {
                context.throwGameTestException("Состояние здания " + site.progress().id() + ", ожидалось done");
            }

            for (BuildStep step : schematic.plan().steps()) {
                BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
                BlockState actual = world.getBlockState(where);

                if (!step.placesBlock()) {
                    if (!actual.isAir()) {
                        context.throwGameTestException("Расчищенное место занято " + actual.getBlock()
                                + " в " + step.pos().toShortString());
                    }
                    continue;
                }

                BlockState expected = schematic.blockAt(step.paletteIndex());
                if (!actual.isOf(expected.getBlock())) {
                    context.throwGameTestException("В " + step.pos().toShortString() + " ожидался "
                            + expected.getBlock() + ", стоит " + actual.getBlock());
                }
            }

            // Материалы обязаны быть израсходованы: иначе стройка бесплатна.
            if (!colony.warehouse().isEmpty()) {
                context.throwGameTestException("Склад не опустел, осталось штук: "
                        + colony.warehouse().total());
            }

            // Точки интереса пересчитаны в мировые координаты и лежат внутри следа.
            List<BlockPos> doors = BuildJob.pointsOfInterest(site, schematic, MarkerKind.DOOR);
            if (doors.isEmpty()) {
                context.throwGameTestException("У готового здания нет точки входа");
            }
            for (BlockPos door : doors) {
                if (!BuildSite.covers(anchor, schematic.size(), BlockRotation.NONE, door)) {
                    context.throwGameTestException("Точка входа вне следа здания: " + door.toShortString());
                }
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /** Второе требование приёмки: материалов нет — билдер ждёт, а не ломается. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void builderWaitsWithoutMaterials(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 0));

        Settlement colony = colonyWithBuilder(manager, anchor);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            BuildJob.Outcome outcome = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (outcome != BuildJob.Outcome.WAITING_FOR_MATERIALS) {
                context.throwGameTestException("Без материалов ожидалось ожидание, получено: " + outcome);
            }
            if (site.progress() != BuildProgress.BUILDING) {
                context.throwGameTestException("Здание должно остаться в стройке, а не в "
                        + site.progress().id());
            }

            int stuckAt = site.nextStep();
            if (stuckAt >= schematic.plan().steps().size()) {
                context.throwGameTestException("Здание достроилось без материалов");
            }
            if (!schematic.plan().steps().get(stuckAt).placesBlock()) {
                context.throwGameTestException("Билдер встал на шаге расчистки, а тот материалов не требует");
            }

            // Повторное обращение не должно ни двигать индекс, ни падать.
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 100)
                    != BuildJob.Outcome.WAITING_FOR_MATERIALS) {
                context.throwGameTestException("Второе обращение без материалов дало другой исход");
            }
            if (site.nextStep() != stuckAt) {
                context.throwGameTestException("Индекс шага сдвинулся без материалов: "
                        + stuckAt + " → " + site.nextStep());
            }

            // Подвезли материалы — стройка продолжилась с того же места.
            stockFor(colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("После подвоза материалов стройка не завершилась");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Прогресс живёт в данных поселения, а не в билдере: стройка обязана
     * продолжаться с того же места после перезахода в мир.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void buildProgressSurvivesReload(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 0));

        Settlement colony = colonyWithBuilder(manager, anchor);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(colony, schematic);
            BuildJob.advance(world, manager, colony.id(), site.id(), 40);

            int reached = site.nextStep();
            if (reached == 0 || reached >= schematic.plan().steps().size()) {
                context.throwGameTestException("Нужна недостроенная стройка, а шаг " + reached);
            }

            NbtElement saved = Settlement.CODEC.encodeStart(NbtOps.INSTANCE, colony).result().orElseThrow();
            Settlement restored = Settlement.CODEC.parse(NbtOps.INSTANCE, saved).result().orElseThrow();
            Building restoredSite = restored.building(site.id()).orElseThrow();

            if (restoredSite.nextStep() != reached) {
                context.throwGameTestException("Шаг стройки не пережил сохранение: "
                        + reached + " → " + restoredSite.nextStep());
            }
            if (restoredSite.progress() != BuildProgress.BUILDING) {
                context.throwGameTestException("Состояние стройки не пережило сохранение");
            }
            if (restored.warehouse().total() != colony.warehouse().total()) {
                context.throwGameTestException("Склад не пережил сохранение");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Поворот применяется и к позициям, и к блокстейтам. Повернуть только
     * позиции — значит получить дом с лестницами, ведущими в стену.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rotatedBuildingTurnsItsBlocksToo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 0));

        Settlement colony = colonyWithBuilder(manager, anchor);
        Building site = plan(colony, anchor, BlockRotation.CLOCKWISE_90);

        try {
            stockFor(colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Повёрнутое здание не достроилось");
            }

            int checkedStairs = 0;
            for (BuildStep step : schematic.plan().steps()) {
                if (!step.placesBlock()) {
                    continue;
                }
                BlockState planned = schematic.blockAt(step.paletteIndex());
                if (!planned.contains(Properties.HORIZONTAL_FACING)) {
                    continue;
                }

                BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
                Direction expected = BlockRotation.CLOCKWISE_90
                        .rotate(planned.get(Properties.HORIZONTAL_FACING));
                BlockState actual = world.getBlockState(where);

                if (!actual.contains(Properties.HORIZONTAL_FACING)) {
                    context.throwGameTestException("В " + where.toShortString() + " стоит "
                            + actual.getBlock() + " без направления");
                    return;
                }
                if (actual.get(Properties.HORIZONTAL_FACING) != expected) {
                    context.throwGameTestException("Поворот блока не применён в "
                            + step.pos().toShortString() + ": ожидалось " + expected
                            + ", стоит " + actual.get(Properties.HORIZONTAL_FACING));
                }
                checkedStairs++;
            }

            if (checkedStairs == 0) {
                context.throwGameTestException("В схеме нет блоков с направлением — поворот нечем проверить");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Расчистка сдаёт добычу на склад — решение заказчика. Поэтому выбор
     * места становится экономическим: стройка в лесу дороже по времени,
     * но выгоднее по материалам.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void clearingSalvageGoesToWarehouse(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 0));

        Settlement colony = colonyWithBuilder(manager, anchor);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        // Кладём брёвна ровно туда, где схема требует пустоты.
        Identifier logItem = new Identifier("minecraft", "oak_log");
        int obstacles = 0;
        List<BlockPos> blocked = new java.util.ArrayList<>();
        for (BuildStep step : schematic.plan().steps()) {
            if (step.placesBlock() || obstacles >= 4) {
                continue;
            }
            BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
            world.setBlockState(where, Blocks.OAK_LOG.getDefaultState());
            blocked.add(where);
            obstacles++;
        }

        try {
            if (obstacles == 0) {
                context.throwGameTestException("В схеме нет шагов расчистки — проверять нечего");
            }

            BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);

            if (colony.warehouse().count(logItem) < obstacles) {
                context.throwGameTestException("Добыча с расчистки не попала на склад: брёвен "
                        + colony.warehouse().count(logItem) + " из " + obstacles);
            }
            for (BlockPos where : blocked) {
                if (!world.getBlockState(where).isAir()) {
                    context.throwGameTestException("Помеха не убрана: " + where.toShortString());
                }
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /** Без строителя стройка не идёт: это работа профессии, а не самого поселения. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void constructionNeedsABuilder(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 0));

        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Безлюдье", anchor);
        manager.add(colony);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(colony, schematic);

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 100)
                    != BuildJob.Outcome.NO_BUILDER) {
                context.throwGameTestException("Без строителя стройка не должна идти");
            }
            if (site.nextStep() != 0) {
                context.throwGameTestException("Без строителя индекс шага сдвинулся");
            }

            // Нанимаем строителя — и та же стройка идёт.
            Citizen builder = Citizen.newborn("Rollo", "le Macon", NORMAN, Gender.MALE);
            builder.setProfession(BuildJob.BUILDER);
            colony.addCitizen(builder);

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10)
                    == BuildJob.Outcome.NO_BUILDER) {
                context.throwGameTestException("Строитель нанят, а стройка всё равно не идёт");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void schematicNamingRoundTrips(TestContext context) {
        Building site = new Building(UUID.randomUUID(), TOWN_HALL_TYPE, 1,
                BlockPos.ORIGIN, BlockRotation.NONE, BuildProgress.PLANNED, List.of());

        Identifier schematicId = BuildJob.schematicId(site);
        if (!schematicId.equals(TOWN_HALL_SCHEMATIC)) {
            context.throwGameTestException("Имя схемы собрано неверно: " + schematicId);
        }
        if (!BuildJob.buildingTypeOf(schematicId).equals(Optional.of(TOWN_HALL_TYPE))) {
            context.throwGameTestException("Тип здания из имени схемы не восстановился");
        }
        if (!BuildJob.levelOf(schematicId).equals(Optional.of(1))) {
            context.throwGameTestException("Уровень из имени схемы не восстановился");
        }
        if (BuildJob.buildingTypeOf(new Identifier("villagepax", "norman/house")).isPresent()) {
            context.throwGameTestException("Имя без _lvl не должно разбираться");
        }

        // Заявка на материалы обязана быть непустой и не содержать воздуха.
        Map<Identifier, Integer> required = Materials.required(loadedTownHall(context));
        if (required.isEmpty()) {
            context.throwGameTestException("Заявка на материалы пуста");
        }
        if (required.containsKey(new Identifier("minecraft", "air"))) {
            context.throwGameTestException("В заявку попал воздух");
        }

        context.complete();
    }


    /**
     * Повреждённое здание чинится по той же схеме — и платит только за то,
     * что действительно пропало.
     * <p>
     * Здесь сходятся два решения. Первое: у повреждённого здания {@code nextStep}
     * стоит в конце с прошлой стройки, поэтому ремонт обязан начать план заново,
     * иначе он мгновенно «завершился» бы, не поставив ни блока. Второе: уже
     * стоящий нужный блок не переставляется и не оплачивается, иначе починка
     * трёх блоков списывала бы со склада схему целиком.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void damagedBuildingIsRepairedAndPaysOnlyForWhatIsMissing(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 0));

        Settlement colony = colonyWithBuilder(manager, anchor);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не построилось до начала проверки ремонта");
            }

            // Выбиваем три несущих блока и записываем, чем они были.
            List<BlockPos> holes = new java.util.ArrayList<>();
            Map<Identifier, Integer> missing = new java.util.LinkedHashMap<>();
            for (BuildStep step : schematic.plan().steps()) {
                if (holes.size() >= 3 || !step.placesBlock()) {
                    continue;
                }
                BlockState planned = schematic.blockAt(step.paletteIndex());
                Optional<Identifier> item = Materials.itemFor(planned);
                if (item.isEmpty()) {
                    continue;
                }

                BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
                world.setBlockState(where, Blocks.AIR.getDefaultState());
                holes.add(where);
                missing.merge(item.get(), 1, Integer::sum);
            }
            if (holes.size() != 3) {
                context.throwGameTestException("Не удалось выбить три блока для проверки ремонта");
            }

            site.setProgress(BuildProgress.DAMAGED);
            missing.forEach((item, count) -> colony.warehouse().add(item, count));

            BuildJob.Outcome outcome = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (outcome != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ремонт не завершился: " + outcome
                        + ", шаг " + site.nextStep());
            }

            for (BlockPos hole : holes) {
                if (world.getBlockState(hole).isAir()) {
                    context.throwGameTestException("Пробоина не заделана: " + hole.toShortString());
                }
            }
            if (!colony.warehouse().isEmpty()) {
                context.throwGameTestException("Ремонт списал лишнее: на складе осталось "
                        + colony.warehouse().total() + ", а завозили ровно на три блока");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
        }

        context.complete();
    }

    /**
     * Тот самый путь, который проходит живой игрок, и ничего кроме него.
     * <p>
     * Чертёж основывает колонию, вместе с ней появляется строитель, площадка
     * размечается, материалы завозятся — и дальше <b>никто ничего не вызывает
     * руками</b>. Блоки ставит {@code BuildTicker}, который висит на тике мира
     * с самой инициализации мода, ровно как в игре. Остальные тесты стройки
     * дёргают двигатель напрямую и потому не доказывают, что он вообще
     * подключён к игре; этот доказывает.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120, batchId = "playerPath")
    public void playerPathRaisesBuildingByItself(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        context.setBlockState(new BlockPos(1, 1, 1), Blocks.STONE);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 2, 1));

        FoundingOutcome outcome = ColonyFounder.foundAt(world, UUID.randomUUID(), NORMAN, hall);
        if (!(outcome instanceof FoundingOutcome.Founded founded)) {
            context.throwGameTestException("Колония не основана: " + outcome);
            return;
        }

        Settlement colony = founded.settlement();
        if (colony.citizens().stream().noneMatch(
                citizen -> citizen.profession().equals(Optional.of(BuildJob.BUILDER)))) {
            context.throwGameTestException("Основание не дало строителя — стройке некому идти");
        }

        // Площадку ставим над шаблоном: след 7x7 иначе залез бы в область
        // соседнего игрового теста, а они делят один мир.
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));
        Building site = plan(colony, anchor, BlockRotation.NONE);
        stockFor(colony, schematic);

        int expected = 6;
        long wait = BuildJob.TICKS_PER_STEP * (expected + 1L);

        context.runAtTick(wait, () -> {
            try {
                if (site.nextStep() < expected) {
                    context.throwGameTestException("За " + wait + " тиков сделано шагов "
                            + site.nextStep() + ", ожидалось не меньше " + expected
                            + ". Похоже, тикер стройки не подключён к тику мира");
                }
                if (site.progress() != BuildProgress.BUILDING) {
                    context.throwGameTestException("Площадка не перешла в стройку: "
                            + site.progress().id());
                }

                // Блоки обязаны появиться в мире, а не только в счётчике шагов.
                int placed = 0;
                for (int step = 0; step < site.nextStep(); step++) {
                    BuildStep done = schematic.plan().steps().get(step);
                    if (!done.placesBlock()) {
                        continue;
                    }
                    BlockPos where = BuildJob.worldPos(site, schematic.size(), done.pos());
                    if (!world.getBlockState(where).isOf(schematic.blockAt(done.paletteIndex()).getBlock())) {
                        context.throwGameTestException("Шаг " + step + " засчитан, а блока в мире нет: "
                                + where.toShortString());
                    }
                    placed++;
                }
                if (placed == 0) {
                    context.throwGameTestException("Ни одного блока не поставлено — "
                            + "первые шаги плана оказались только расчисткой");
                }

                context.complete();
            } finally {
                demolish(world, site, schematic);
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
        });
    }

    /** Тела не сохраняются, но живут до выгрузки: игровые тесты делят один мир. */
    private static void discardBodies(ServerWorld world, Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            citizen.entityUuid().map(world::getEntity).ifPresent(Entity::discard);
        }
    }

    // --- помощники задачи 1.6 ---

    private static Settlement colonyWithBuilder(SettlementManager manager, BlockPos center) {
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Стройка", center);
        Citizen builder = Citizen.newborn("Rollo", "le Macon", NORMAN, Gender.MALE);
        builder.setProfession(BuildJob.BUILDER);
        colony.addCitizen(builder);
        manager.add(colony);
        return colony;
    }

    private static Building plan(Settlement colony, BlockPos anchor, BlockRotation rotation) {
        Building site = new Building(UUID.randomUUID(), TOWN_HALL_TYPE, 1, anchor, rotation,
                BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);
        return site;
    }

    private static void stockFor(Settlement colony, Schematic schematic) {
        Materials.required(schematic).forEach((item, count) -> colony.warehouse().add(item, count));
    }

    /** Убрать за собой: игровые тесты делят один мир. */
    private static void demolish(ServerWorld world, Building site, Schematic schematic) {
        for (BuildStep step : schematic.plan().steps()) {
            world.setBlockState(BuildJob.worldPos(site, schematic.size(), step.pos()),
                    Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }
}
