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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
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
import com.villagepax.sim.work.HaulJob;
import com.villagepax.sim.work.JobState;
import com.villagepax.core.profession.Profession;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.sim.work.FarmJob;
import com.villagepax.sim.work.GatherJob;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.work.Jobs;
import com.villagepax.screen.BuildOrders;
import com.villagepax.screen.Mood;
import com.villagepax.screen.TownHallView;
import com.villagepax.sim.work.Assignments;
import com.villagepax.sim.work.Workplaces;
import com.villagepax.sim.work.Needs;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkTicker;
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

        // Служебный блок мода не должен оказаться в шагах установки: маркер
        // либо расчищается, либо подменяется обстановкой своего рода.
        for (BuildStep step : plan.steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            Identifier blockId = Registries.BLOCK.getId(schematic.blockFor(step).getBlock());
            if ("villagepax".equals(blockId.getNamespace()) && blockId.getPath().startsWith("marker_")) {
                context.throwGameTestException("Маркер попал в шаги установки блоков: "
                        + step.pos().toShortString());
            }
        }

        Set<BlockPos> handled = new HashSet<>();
        for (BuildStep step : plan.steps()) {
            handled.add(step.pos());
        }
        for (PointOfInterest poi : plan.pois()) {
            if (!handled.contains(poi.pos())) {
                context.throwGameTestException("Место маркера " + poi.kind().id()
                        + " не обрабатывается планом: " + poi.pos().toShortString());
            }
        }

        // Складской маркер превращается в сундук, и это обычный шаг плана.
        // Отдельной фазой «доводки» сундук ставить нельзя: ремонт сносил бы
        // его вместе с содержимым, а правило «нужный блок уже стоит» защищает.
        for (BlockPos storage : plan.positionsOf(MarkerKind.STORAGE)) {
            BuildStep step = plan.steps().stream()
                    .filter(candidate -> candidate.pos().equals(storage))
                    .findFirst()
                    .orElseThrow();
            if (!step.placesBlock() || !schematic.blockFor(step).isOf(Blocks.CHEST)) {
                context.throwGameTestException("На складском маркере не сундук: "
                        + storage.toShortString());
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
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

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
            if (!Warehouse.of(world, colony).isEmpty()) {
                context.throwGameTestException("Склад не опустел, осталось штук: "
                        + Warehouse.of(world, colony).totalItems());
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
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
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
            stockFor(world, colony, schematic);
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
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);
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
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.CLOCKWISE_90);

        try {
            stockFor(world, colony, schematic);
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
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        // Кладём брёвна ровно туда, где схема требует пустоты.
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

            if (Warehouse.of(world, colony).count(Items.OAK_LOG) < obstacles) {
                context.throwGameTestException("Добыча с расчистки не попала на склад: брёвен "
                        + Warehouse.of(world, colony).count(Items.OAK_LOG) + " из " + obstacles);
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
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Безлюдье", hall);
        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        manager.add(colony);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

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
        Map<Item, Integer> required = Materials.required(loadedTownHall(context));
        if (required.isEmpty()) {
            context.throwGameTestException("Заявка на материалы пуста");
        }
        if (required.containsKey(Items.AIR)) {
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
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не построилось до начала проверки ремонта");
            }

            // Выбиваем три несущих блока и записываем, чем они были.
            List<BlockPos> holes = new java.util.ArrayList<>();
            Map<Item, Integer> missing = new java.util.LinkedHashMap<>();
            for (BuildStep step : schematic.plan().steps()) {
                if (holes.size() >= 3 || !step.placesBlock()) {
                    continue;
                }
                BlockState planned = schematic.blockAt(step.paletteIndex());
                Optional<Item> item = Materials.itemFor(planned);
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
            Warehouse warehouse = Warehouse.of(world, colony);
            missing.forEach((item, count) -> warehouse.add(new ItemStack(item, count)));

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
            if (!Warehouse.of(world, colony).isEmpty()) {
                context.throwGameTestException("Ремонт списал лишнее: на складе осталось "
                        + Warehouse.of(world, colony).totalItems() + ", а завозили ровно на три блока");
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
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 220, batchId = "playerPath")
    public void playerPathRaisesBuildingByItself(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        // Пол: билдер теперь ходит, и ему надо по чему-то идти. Без этого
        // площадка висела бы в воздухе и путь до неё не проложился бы вовсе.
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 11; z++) {
                context.setBlockState(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        FoundingOutcome outcome = ColonyFounder.foundAt(world, UUID.randomUUID(), NORMAN, hall);
        if (!(outcome instanceof FoundingOutcome.Founded founded)) {
            context.throwGameTestException("Колония не основана: " + outcome);
            return;
        }

        Settlement colony = founded.settlement();
        Citizen builder = colony.citizens().stream()
                .filter(citizen -> citizen.profession().equals(Optional.of(BuildJob.BUILDER)))
                .findFirst()
                .orElse(null);
        if (builder == null) {
            context.throwGameTestException("Основание не дало строителя — стройке некому идти");
            return;
        }

        // Площадка рядом с ратушей, но не поверх неё: склад в двух шагах,
        // поэтому курьер не нужен и билдер берёт материалы сам.
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 1, 4));
        Building site = plan(colony, anchor, BlockRotation.NONE);
        stockFor(world, colony, schematic);

        context.runAtTick(200, () -> {
            try {
                if (site.nextStep() == 0) {
                    context.throwGameTestException("За 200 тиков билдер не сделал ни шага. "
                            + "Похоже, тикер работ не подключён к тику мира");
                }

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
                    context.throwGameTestException("Билдер дошёл, но ни одного блока не поставил");
                }

                // Он обязан быть у стройки, а не бродить: иначе работа шла бы
                // сама, а житель был бы декорацией.
                CitizenEntity body = (CitizenEntity) world.getEntity(builder.entityUuid().orElseThrow());
                double distance = Math.sqrt(body.getPos().squaredDistanceTo(Vec3d.ofCenter(anchor)));
                if (distance > 16.0) {
                    context.throwGameTestException("Билдер работает, стоя в " + Math.round(distance)
                            + " блоках от площадки");
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

    // --- задача 1.7а: склад поверх реальных контейнеров ---

    /**
     * Ратуша — стартовое хранилище колонии.
     * <p>
     * Без него получается курица и яйцо: чтобы построить склад, нужен склад.
     * Поэтому ратуша входит в склад всегда, а игрок кладёт материалы рукой.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void townHallIsTheColonyStartingStorage(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Warehouse warehouse = Warehouse.of(world, colony);
            if (warehouse.containerCount() != 1) {
                context.throwGameTestException("Хранилищ у новой колонии " + warehouse.containerCount()
                        + ", ожидалась одна ратуша");
            }
            if (!warehouse.isEmpty() || warehouse.totalItems() != 0) {
                context.throwGameTestException("Новая ратуша не пуста");
            }

            // Положили и забрали: склад — вид поверх настоящего контейнера.
            warehouse.add(new ItemStack(Items.OAK_LOG, 40));
            if (Warehouse.of(world, colony).count(Items.OAK_LOG) != 40) {
                context.throwGameTestException("Склад не увидел то, что в него положили");
            }
            if (!(world.getBlockEntity(hall) instanceof TownHallBlockEntity chest) || chest.isEmpty()) {
                context.throwGameTestException("Предметы легли не в ратушу");
            }

            if (Warehouse.of(world, colony).take(Items.OAK_LOG, 41)) {
                context.throwGameTestException("Склад выдал больше, чем в нём есть");
            }
            if (Warehouse.of(world, colony).count(Items.OAK_LOG) != 40) {
                context.throwGameTestException("Отказ в выдаче не должен трогать склад");
            }
            if (!Warehouse.of(world, colony).take(Items.OAK_LOG, 40)
                    || Warehouse.of(world, colony).count(Items.OAK_LOG) != 0) {
                context.throwGameTestException("Выдача целиком не сработала");
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Выдача идёт «всё или ничего» даже когда нужное размазано по контейнерам. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void warehouseTakesAcrossSeveralContainers(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не достроилось, второго хранилища не появится");
            }

            // Достроенное здание принесло колонии сундук на складском маркере.
            Warehouse warehouse = Warehouse.of(world, colony);
            if (warehouse.containerCount() < 2) {
                context.throwGameTestException("Сундук здания не вошёл в склад: хранилищ "
                        + warehouse.containerCount());
            }
            List<BlockPos> storage = BuildJob.pointsOfInterest(site, schematic, MarkerKind.STORAGE);
            if (storage.isEmpty()) {
                context.throwGameTestException("У здания нет складской точки");
            }
            for (BlockPos spot : storage) {
                if (!world.getBlockState(spot).isOf(Blocks.CHEST)) {
                    context.throwGameTestException("На складской точке не сундук: "
                            + world.getBlockState(spot).getBlock());
                }
            }

            // Раскладываем по десятку в каждое хранилище и просим всё сразу.
            warehouse.add(new ItemStack(Items.COBBLESTONE, 10));
            if (world.getBlockEntity(storage.get(0)) instanceof Inventory chest) {
                chest.setStack(0, new ItemStack(Items.COBBLESTONE, 10));
            }

            Warehouse merged = Warehouse.of(world, colony);
            if (merged.count(Items.COBBLESTONE) != 20) {
                context.throwGameTestException("Склад не сложил содержимое контейнеров: "
                        + merged.count(Items.COBBLESTONE));
            }
            if (!merged.take(Items.COBBLESTONE, 15)) {
                context.throwGameTestException("Выдача через два контейнера не прошла");
            }
            if (Warehouse.of(world, colony).count(Items.COBBLESTONE) != 5) {
                context.throwGameTestException("После выдачи осталось не то количество");
            }
        } finally {
            demolish(world, site, schematic);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Излишки падают на землю, а не исчезают.
     * <p>
     * Контейнеры конечны, и потерять брёвна с расчистки молча хуже, чем
     * оставить игроку уборку: пропажу он заметит только по нехватке
     * материалов через полчаса.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullWarehouseScattersInsteadOfLosing(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            if (!(world.getBlockEntity(hall) instanceof TownHallBlockEntity storage)) {
                context.throwGameTestException("У ратуши нет хранилища");
                return;
            }
            for (int slot = 0; slot < storage.size(); slot++) {
                storage.setStack(slot, new ItemStack(Items.STONE, 64));
            }

            Warehouse warehouse = Warehouse.of(world, colony);
            ItemStack leftover = warehouse.add(new ItemStack(Items.OAK_LOG, 7));
            if (leftover.getCount() != 7) {
                context.throwGameTestException("Полный склад что-то принял: осталось "
                        + leftover.getCount() + " из 7");
            }

            warehouse.addOrScatter(world, hall.up(), new ItemStack(Items.OAK_LOG, 7));
            Box around = new Box(hall).expand(4.0);
            int dropped = world.getEntitiesByClass(ItemEntity.class, around,
                    entity -> entity.getStack().isOf(Items.OAK_LOG)).size();
            if (dropped == 0) {
                context.throwGameTestException("Излишки исчезли вместо того, чтобы упасть на землю");
            }

            world.getEntitiesByClass(ItemEntity.class, around, entity -> true).forEach(Entity::discard);
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Склад без контейнеров ничего не выдаёт и не принимает — но и не падает. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void colonyWithoutContainersHasNoStorage(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos nowhere = context.getAbsolutePos(new BlockPos(3, 5, 3));

        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Без ратуши", nowhere);
        manager.add(colony);

        try {
            Warehouse warehouse = Warehouse.of(world, colony);

            if (warehouse.containerCount() != 0 || !warehouse.isEmpty()) {
                context.throwGameTestException("Склад без контейнеров не пуст");
            }
            if (warehouse.take(Items.OAK_LOG, 1)) {
                context.throwGameTestException("Пустой склад что-то выдал");
            }
            if (warehouse.add(new ItemStack(Items.OAK_LOG, 3)).getCount() != 3) {
                context.throwGameTestException("Складу без контейнеров удалось что-то принять");
            }
            if (!warehouse.has(Items.OAK_LOG, 0)) {
                context.throwGameTestException("Нулевого количества хватает всегда");
            }
        } finally {
            manager.remove(colony.id());
        }

        context.complete();
    }

    // --- задача 1.7б: курьер ---

    /**
     * Приёмка задачи 1.7: курьер носит материалы со склада на стройку.
     * <p>
     * Здесь же проверяется и решение заказчика «рядом сам, далеко — курьер»:
     * та же площадка без курьера встаёт на первом же блоке, хотя склад полон.
     * <p>
     * Ходьба заменена телепортом: тест проверяет решения стратегии, а не поиск
     * пути. Что жители действительно ходят, доказывает отдельный тест на
     * настоящих тиках мира.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "courier")
    public void courierCarriesMaterialsWhenStorageIsFar(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        // Далеко по высоте, а не по горизонтали: те же чанки заведомо загружены.
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 20, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

            if (BuildJob.storageIsNearby(Warehouse.of(world, colony), site)) {
                context.throwGameTestException("Площадка оказалась рядом со складом — "
                        + "курьера проверять нечем");
            }

            // Без курьера стройка встаёт, хотя склад полон: до него не дотянуться.
            BuildJob.Outcome alone = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (alone != BuildJob.Outcome.WAITING_FOR_MATERIALS) {
                context.throwGameTestException("Без курьера при далёком складе ожидалось ожидание, "
                        + "получено: " + alone);
            }
            int stalledAt = site.nextStep();

            Citizen courier = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            runWork(world, manager, colony, courier, 12, Schedule.MORNING_WORK);

            if (site.stock().total() == 0) {
                context.throwGameTestException("Курьер ничего не принёс на площадку");
            }
            if (courier.jobState().isCarrying()) {
                context.throwGameTestException("Курьер остался с грузом в руках: "
                        + courier.jobState().phase().id());
            }

            // Теперь билдеру есть из чего строить — из запаса площадки.
            BuildJob.Outcome withCourier = BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);
            if (site.nextStep() <= stalledAt) {
                context.throwGameTestException("Стройка не двинулась после подвоза: шаг "
                        + site.nextStep() + ", было " + stalledAt + ", исход " + withCourier);
            }
        } finally {
            demolish(world, site, schematic);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Груз, который нести уже некуда, возвращается на склад.
     * <p>
     * Самое коварное место всей задачи: задание отменилось, а тридцать брёвен
     * остались в руках. Обнулить состояние целиком — значит удалить их из мира,
     * и игрок никогда не поймёт, куда они девались.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "courier")
    public void strandedLoadGoesBackToStorage(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen courier = hireWithBody(world, colony, HaulJob.COURIER, hall.up());

            // Нёс на стройку, которой больше нет.
            courier.setJobState(JobState.startAt(UUID.randomUUID(), JobState.Phase.TO_SITE)
                    .carrying(Registries.ITEM.getId(Items.OAK_LOG), 7)
                    .withPhase(JobState.Phase.IDLE));
            if (!courier.jobState().hasStrandedLoad()) {
                context.throwGameTestException("Состояние не считается брошенным грузом");
            }

            runWork(world, manager, colony, courier, 4, Schedule.MORNING_WORK);

            if (Warehouse.of(world, colony).count(Items.OAK_LOG) != 7) {
                context.throwGameTestException("Брошенный груз не вернулся на склад: брёвен "
                        + Warehouse.of(world, colony).count(Items.OAK_LOG));
            }
            if (courier.jobState().isCarrying()) {
                context.throwGameTestException("Груз остался в руках после сдачи");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Билдер работает только в пределах вытянутой руки — но пустые шаги
     * расчистки проскакивает не сходя с места.
     * <p>
     * Второе важнее первого: проверка досягаемости стоит после отсечения
     * пустых шагов, иначе билдер шёл бы к каждой из ста одиннадцати пустых
     * позиций расчистки, и вся выгода от «шагать к участку» пропала бы.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "courier")
    public void builderReachesOnlyAsFarAsHisArm(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

            Vec3d faraway = Vec3d.ofCenter(anchor).add(0, 40, 0);
            BuildJob.Outcome tooFar = BuildJob.advance(world, manager, colony.id(), site.id(), 10, faraway);

            if (tooFar != BuildJob.Outcome.OUT_OF_REACH) {
                context.throwGameTestException("Издалека ожидался переход, получено: " + tooFar);
            }
            if (site.nextStep() == 0) {
                context.throwGameTestException("Пустые шаги расчистки должны проскакивать "
                        + "не сходя с места, иначе билдер обойдёт всю площадку пешком");
            }

            BuildStep next = schematic.plan().steps().get(site.nextStep());
            if (!next.placesBlock()) {
                context.throwGameTestException("Билдер встал не на установке блока");
            }

            // Подошли — и работа пошла.
            Vec3d atWork = Vec3d.ofCenter(BuildJob.worldPos(site, schematic.size(), next.pos()));
            int before = site.nextStep();
            BuildJob.Outcome close = BuildJob.advance(world, manager, colony.id(), site.id(), 3, atWork);

            if (site.nextStep() <= before) {
                context.throwGameTestException("Вплотную к блоку работа не пошла: " + close);
            }
        } finally {
            demolish(world, site, schematic);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Остатки со стройплощадки возвращаются на склад, когда здание сдано.
     * <p>
     * Запас площадки — счётчик: физически предметов нигде нет. Не вернуть
     * их — значит удалить из экономики колонии молча. Курьер приносит по
     * полстопки, зданию нужно четыре блока, и двадцать восемь перестают
     * существовать; игрок заметит это однажды, когда материалы кончатся
     * без причины. Сюда же попадает добыча с расчистки, сложенная у стройки,
     * когда склад был далеко.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "courier")
    public void leftoverSiteStockReturnsToStorageWhenDone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            stockFor(world, colony, schematic);

            // Заведомо ненужный схеме предмет: если он вернётся, значит
            // возвращается вообще всё, а не только угаданные виды.
            Identifier surplus = Registries.ITEM.getId(Items.DIAMOND);
            site.stock().add(surplus, 5);

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не достроилось");
            }

            if (!site.stock().isEmpty()) {
                context.throwGameTestException("Запас площадки не опустел после сдачи: "
                        + site.stock().total() + " штук осталось висеть в счётчике");
            }
            if (Warehouse.of(world, colony).count(Items.DIAMOND) != 5) {
                context.throwGameTestException("Остатки не вернулись на склад: алмазов "
                        + Warehouse.of(world, colony).count(Items.DIAMOND) + " из 5");
            }
        } finally {
            demolish(world, site, schematic);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- задача 1.8: дом, распорядок, еда, счастье ---

    private static final Identifier HOUSE_SCHEMATIC = new Identifier("villagepax", "norman/house_lvl1");
    private static final Identifier HOUSE_TYPE = new Identifier("villagepax", "norman/house");

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

            for (int day = 0; day < 10 && colony.population() > 0; day++) {
                Needs.newDay(world, manager, colony);
                if (colony.population() > 0 && victim.discontent() == Needs.WARN_AFTER_DAYS) {
                    sawWarning = true;
                }
            }

            if (colony.population() != 0) {
                context.throwGameTestException("Житель не ушёл за десять голодных дней: "
                        + "недовольство " + victim.discontent());
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

    // --- задача 1.9а: профессии как данные и лесоруб ---

    private static final Identifier LUMBERJACK_SCHEMATIC =
            new Identifier("villagepax", "norman/lumberjack_lvl1");
    private static final Identifier LUMBERJACK_TYPE = new Identifier("villagepax", "norman/lumberjack");

    /** Профессии приходят из датапака, а логика работы — из кода. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "lumberjack")
    public void professionsComeFromTheDatapack(TestContext context) {
        Profession lumberjack = ProfessionManager.get(GatherJob.LUMBERJACK).orElse(null);
        if (lumberjack == null) {
            context.throwGameTestException("Профессия лесоруба не загружена. Загружены: "
                    + ProfessionManager.ids());
            return;
        }
        if (!lumberjack.job().equals(GatherJob.LOGIC)) {
            context.throwGameTestException("Лесоруб выбрал не ту логику: " + lumberjack.job());
        }
        if (!lumberjack.needsWorkplace()) {
            context.throwGameTestException("Лесорубу нужна мастерская: там его роща");
        }

        // Логика находится по профессии — через данные, а не по таблице в коде.
        if (Jobs.forProfession(Optional.of(GatherJob.LUMBERJACK)).isEmpty()) {
            context.throwGameTestException("По профессии лесоруба не нашлось логики");
        }
        // А билдер и курьер мастерской не требуют: их зданий ещё нет,
        // и жёсткое требование остановило бы уже идущую работу.
        if (ProfessionManager.get(BuildJob.BUILDER).orElseThrow().needsWorkplace()
                || ProfessionManager.get(HaulJob.COURIER).orElseThrow().needsWorkplace()) {
            context.throwGameTestException("Билдеру или курьеру навязали мастерскую");
        }

        // Опечатка в датапаке не должна ронять сервер: житель просто без дела.
        if (Jobs.forProfession(Optional.of(new Identifier("villagepax", "no_such_profession"))).isPresent()) {
            context.throwGameTestException("Неизвестная профессия получила логику");
        }

        // Порядок найма задан данными и устойчив.
        List<Identifier> order = ProfessionManager.byHiringPriority();
        if (!order.get(0).equals(BuildJob.BUILDER)) {
            context.throwGameTestException("Первым нанимают не строителя, а " + order.get(0));
        }
        if (!order.equals(ProfessionManager.byHiringPriority())) {
            context.throwGameTestException("Порядок найма меняется от вызова к вызову");
        }

        context.complete();
    }

    /**
     * Домик лесоруба приносит огороженную рощу и рабочее место.
     * <p>
     * Роща — не новое состояние, а часть схемы: грядки для деревьев есть
     * позиции, где в схеме стоят саженцы.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "lumberjack")
    public void lumberjackHutGivesAGroveAndAWorkplace(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic hutPlan = schematic(context, LUMBERJACK_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building hut = plan(colony, anchor, LUMBERJACK_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, hutPlan);
            if (BuildJob.advance(world, manager, colony.id(), hut.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Домик лесоруба не достроился");
            }

            List<BlockPos> grove = GatherJob.groveTiles(hut);
            if (grove.size() != 4) {
                context.throwGameTestException("Грядок в роще " + grove.size() + ", в схеме четыре");
            }
            for (BlockPos tile : grove) {
                if (!world.getBlockState(tile).isIn(BlockTags.SAPLINGS)) {
                    context.throwGameTestException("На грядке не саженец: "
                            + world.getBlockState(tile).getBlock());
                }
                if (!world.getBlockState(tile.down()).isIn(BlockTags.DIRT)) {
                    context.throwGameTestException("Под саженцем не земля");
                }
            }

            if (Workplaces.stations(hut).isEmpty()) {
                context.throwGameTestException("У домика нет рабочего места");
            }

            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, hall.up());
            Workplaces.assign(world, colony);

            if (Workplaces.of(colony, woodsman).isEmpty()) {
                context.throwGameTestException("Лесорубу не досталась мастерская");
            }
            if (!Workplaces.of(colony, woodsman).orElseThrow().id().equals(hut.id())) {
                context.throwGameTestException("Лесоруб приписан к чужому зданию");
            }
        } finally {
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Приёмка задачи 1.9а: лесоруб валит дерево, сдаёт брёвна на склад
     * и сажает саженец обратно.
     * <p>
     * Посадка — решение заказчика: в роще лес не кончается, а окрестности
     * не превращаются в пустырь. Дикий лес он, наоборот, просто счищает.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "lumberjack")
    public void lumberjackFellsGroveTreeAndReplantsIt(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic hutPlan = schematic(context, LUMBERJACK_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building hut = plan(colony, anchor, LUMBERJACK_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, hutPlan);
            BuildJob.advance(world, manager, colony.id(), hut.id(), 10_000);

            // Одна грядка «выросла»: ствол и немного кроны.
            BlockPos tile = GatherJob.groveTiles(hut).get(0);
            for (int dy = 0; dy < 4; dy++) {
                world.setBlockState(tile.up(dy), Blocks.OAK_LOG.getDefaultState());
            }
            world.setBlockState(tile.up(4),
                    Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));

            // Саженцы на складе: колония живёт своим кругооборотом.
            Warehouse.of(world, colony).add(new ItemStack(Items.OAK_SAPLING, 4));
            int logsBefore = Warehouse.of(world, colony).count(Items.OAK_LOG);

            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, tile.up(6));
            Workplaces.assign(world, colony);

            runWork(world, manager, colony, woodsman, 40, Schedule.MORNING_WORK);

            if (!world.getBlockState(tile.up(1)).isAir()) {
                context.throwGameTestException("Ствол не свален: на высоте один стоит "
                        + world.getBlockState(tile.up(1)).getBlock());
            }
            if (Warehouse.of(world, colony).count(Items.OAK_LOG) < logsBefore + 4) {
                context.throwGameTestException("Брёвна не легли на склад: было " + logsBefore
                        + ", стало " + Warehouse.of(world, colony).count(Items.OAK_LOG));
            }
            if (!world.getBlockState(tile).isIn(BlockTags.SAPLINGS)) {
                context.throwGameTestException("На месте срубленного дерева не посажен саженец: "
                        + world.getBlockState(tile).getBlock());
            }
        } finally {
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Лесоруб счищает дикий лес, но не разбирает зданий.
     * <p>
     * Это не мелочь: фахверк норманнских домов сложен из тёмного дуба, то есть
     * из брёвен. Без проверки «внутри здания» лесоруб унёс бы на склад стены
     * той самой мастерской, в которой работает.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "lumberjack")
    public void lumberjackClearsWildForestButSparesBuildings(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic hutPlan = schematic(context, LUMBERJACK_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building hut = plan(colony, anchor, LUMBERJACK_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, hutPlan);
            BuildJob.advance(world, manager, colony.id(), hut.id(), 10_000);

            // Угловой столб мастерской — тоже бревно.
            BlockPos post = anchor.add(0, 1, 0);
            if (!world.getBlockState(post).isIn(BlockTags.LOGS)) {
                context.throwGameTestException("Ожидался бревенчатый столб мастерской, стоит "
                        + world.getBlockState(post).getBlock());
            }

            // Дикое дерево за пределами следа здания, но в границах колонии.
            BlockPos wild = anchor.add(13, 1, 2);
            for (int dy = 0; dy < 3; dy++) {
                world.setBlockState(wild.up(dy), Blocks.OAK_LOG.getDefaultState());
            }

            // Роща занята саженцами, так что лесоруб пойдёт в дикий лес.
            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, wild.up(4));
            Workplaces.assign(world, colony);

            runWork(world, manager, colony, woodsman, 30, Schedule.MORNING_WORK);

            if (!world.getBlockState(wild).isAir()) {
                context.throwGameTestException("Дикое дерево не счищено: стоит "
                        + world.getBlockState(wild).getBlock());
            }
            if (!world.getBlockState(post).isIn(BlockTags.LOGS)) {
                context.throwGameTestException("Лесоруб разобрал столб собственной мастерской");
            }
            for (BlockPos tile : GatherJob.groveTiles(hut)) {
                if (!world.getBlockState(tile).isIn(BlockTags.SAPLINGS)) {
                    context.throwGameTestException("Лесоруб выдрал саженцы из своей рощи");
                }
            }
        } finally {
            for (int dy = 0; dy < 3; dy++) {
                world.setBlockState(anchor.add(13, 1 + dy, 2), Blocks.AIR.getDefaultState());
            }
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- задача 1.9б: фермер ---

    private static final Identifier FARM_SCHEMATIC = new Identifier("villagepax", "norman/farm_lvl1");
    private static final Identifier FARM_TYPE = new Identifier("villagepax", "norman/farm");

    /**
     * Построенная ферма даёт засеянное поле, воду и рабочее место — а фермер
     * к ней приписывается, хотя имена профессии и здания не совпадают.
     * <p>
     * «Фермер» работает на «ферме», и это ровно тот случай, ради которого
     * профессия называет своё рабочее место в данных, а не выводит его
     * из собственного имени.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void builtFarmGivesASownFieldAndAWorkplace(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            if (BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ферма не достроилась");
            }

            List<BlockPos> plots = FarmJob.plots(farm);
            if (plots.size() != 24) {
                context.throwGameTestException("Грядок на ферме " + plots.size()
                        + ", в схеме двадцать четыре");
            }
            for (BlockPos plot : plots) {
                if (!world.getBlockState(plot).isIn(BlockTags.CROPS)) {
                    context.throwGameTestException("Грядка не засеяна: "
                            + world.getBlockState(plot).getBlock());
                }
                if (!world.getBlockState(plot.down()).isOf(Blocks.FARMLAND)) {
                    context.throwGameTestException("Под посевом не грядка");
                }
            }

            // То, что растёт на поле, житель должен уметь съесть: иначе фермер
            // кормит склад, а не колонию, и голод из задачи 1.8 остаётся на игроке.
            Item food = FarmJob.seedOf(world, farmPlan).orElse(Items.AIR);
            if (!food.getDefaultStack().isIn(ModTags.CITIZEN_FOOD)) {
                context.throwGameTestException("Урожай фермы жителям не еда: " + food);
            }

            // Источник воды в середине поля. Он и есть причина, по которой
            // вода попала в тег декора: иначе она растекается сквозь
            // недостроенную ограду с первого же слоя.
            BlockPos well = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(3, 1, 3));
            if (!world.getBlockState(well).isOf(Blocks.WATER)) {
                context.throwGameTestException("В середине поля нет воды, стоит "
                        + world.getBlockState(well).getBlock());
            }

            if (Workplaces.stations(farm).isEmpty()) {
                context.throwGameTestException("У фермы нет рабочего места");
            }

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, hall.up());
            Workplaces.assign(world, colony);

            Building assigned = Workplaces.of(colony, farmer).orElse(null);
            if (assigned == null) {
                context.throwGameTestException("Фермеру не досталось рабочее место: имя здания "
                        + "задано в данных профессии, а не выведено из её имени");
            }
            if (!assigned.id().equals(farm.id())) {
                context.throwGameTestException("Фермер приписан к чужому зданию");
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Приёмка задачи 1.9б: фермер жнёт поспевшее, сдаёт урожай на склад
     * и засевает грядку заново — за одну морковь из того же склада.
     * <p>
     * Проверяется в два приёма, потому что урожай и посевное — один и тот же
     * предмет: сначала один шаг стратегии на жатву, потом остальные на посев.
     * Иначе тест зависел бы от случайного числа морковок в добыче.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void farmerHarvestsRipeCropAndSowsItAgain(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos plot = FarmJob.plots(farm).get(0);
            world.setBlockState(plot, ((CropBlock) Blocks.CARROTS).withAge(CropBlock.MAX_AGE));

            // Склад пуст по этой культуре: всё, что появится, пришло с грядки.
            Warehouse before = Warehouse.of(world, colony);
            before.take(crop, before.count(crop));

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, plot.up(2));
            Workplaces.assign(world, colony);

            // Один шаг — жатва.
            runWork(world, manager, colony, farmer, 1, Schedule.MORNING_WORK);

            int harvested = Warehouse.of(world, colony).count(crop);
            if (harvested < 1) {
                context.throwGameTestException("Урожай не попал на склад");
            }
            if (!world.getBlockState(plot).isAir()) {
                context.throwGameTestException("Поспевшая грядка не сжата: стоит "
                        + world.getBlockState(plot).getBlock());
            }

            // Остальные шаги — посев за одну морковь со склада.
            runWork(world, manager, colony, farmer, 4, Schedule.MORNING_WORK);

            BlockState sown = world.getBlockState(plot);
            if (!sown.isIn(BlockTags.CROPS)) {
                context.throwGameTestException("Сжатая грядка не засеяна заново: "
                        + sown.getBlock());
            }
            if (sown.getBlock() instanceof CropBlock ripe && ripe.isMature(sown)) {
                context.throwGameTestException("На грядке снова поспевший колос — "
                        + "значит его не сжали, а посчитали");
            }
            int left = Warehouse.of(world, colony).count(crop);
            if (left != harvested - 1) {
                context.throwGameTestException("На посев ушло не одно семя: было " + harvested
                        + ", осталось " + left);
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Без запаса на складе грядка остаётся пустой.
     * <p>
     * Это и есть замкнутый круг: посевное берётся оттуда, куда сам же фермер
     * сдал урожай. Иначе поле заполнялось бы из ничего, и колония кормилась
     * бы воздухом.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void farmerSowsOnlyWhatTheStorageHas(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos bare = FarmJob.plots(farm).get(0);
            world.setBlockState(bare, Blocks.AIR.getDefaultState());

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, bare.up(2));
            Workplaces.assign(world, colony);

            // Посевного на складе нет — грядка остаётся пустой.
            Warehouse empty = Warehouse.of(world, colony);
            empty.take(crop, empty.count(crop));
            runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

            if (!world.getBlockState(bare).isAir()) {
                context.throwGameTestException("Грядка засеяна без запаса на складе: "
                        + world.getBlockState(bare).getBlock());
            }

            // Завезли — и поле снова полное, ровно на одну морковь дешевле.
            Warehouse.of(world, colony).add(new ItemStack(crop, 4));
            runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

            if (!world.getBlockState(bare).isIn(BlockTags.CROPS)) {
                context.throwGameTestException("С запасом на складе грядка так и не засеяна");
            }
            int left = Warehouse.of(world, colony).count(crop);
            if (left != 3) {
                context.throwGameTestException("Со склада ушло не одно семя, а " + (4 - left));
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Приёмка задачи 1.9б целиком: колония кормит себя сама.
     * <p>
     * До этой профессии еду в ратушу носил игрок, и голод из задачи 1.8
     * упирался в него. Здесь на складе нет ни крошки — а через один шаг
     * работы фермера голодный житель ест то, что снято с грядки.
     * <p>
     * Ровно поэтому норманнское поле растит морковь: пшеницу житель съесть
     * не может, и поле пшеницы кормило бы только склад.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void colonyFeedsItselfFromItsOwnFarm(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            if (Warehouse.of(world, colony).hasAny(ModTags.CITIZEN_FOOD)) {
                context.throwGameTestException("На складе есть еда до работы фермера — "
                        + "тогда тест ничего не доказывает");
            }

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos plot = FarmJob.plots(farm).get(0);
            world.setBlockState(plot, ((CropBlock) Blocks.CARROTS).withAge(CropBlock.MAX_AGE));

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, plot.up(2));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, farmer, 1, Schedule.MORNING_WORK);

            int grown = Warehouse.of(world, colony).count(crop);
            if (grown < 1) {
                context.throwGameTestException("После работы фермера склад пуст");
            }

            Citizen eater = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            eater.setSaturation(0);

            // Первое решение отправляет к еде, второе — кормит.
            runWork(world, manager, colony, eater, 3, Schedule.MEAL);

            if (eater.saturation() < Needs.nourishment(crop)) {
                context.throwGameTestException("Житель не поел урожаем: сытость "
                        + eater.saturation());
            }
            if (Warehouse.of(world, colony).count(crop) >= grown) {
                context.throwGameTestException("Житель поел, а со склада ничего не ушло");
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Вытоптанная грядка вскапывается заново, а не выпадает из поля навсегда.
     * <p>
     * Любой прыгнувший на грядку — житель, корова, сам игрок — сбивает её
     * до земли. Без починки ферма год за годом превращается в пустырь,
     * и игрок видит здание, которое перестало работать без причины.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void trampledPlotIsTilledAndSownAgain(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos plot = FarmJob.plots(farm).get(0);

            // Кто-то прыгнул на грядку: земля сбита, посев слетел.
            world.setBlockState(plot, Blocks.AIR.getDefaultState());
            world.setBlockState(plot.down(), Blocks.DIRT.getDefaultState());

            Warehouse.of(world, colony).add(new ItemStack(crop, 4));

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, plot.up(2));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

            if (!world.getBlockState(plot.down()).isOf(Blocks.FARMLAND)) {
                context.throwGameTestException("Грядку не вскопали заново: под посевом "
                        + world.getBlockState(plot.down()).getBlock());
            }
            if (!world.getBlockState(plot).isIn(BlockTags.CROPS)) {
                context.throwGameTestException("Вскопанную грядку не засеяли: "
                        + world.getBlockState(plot).getBlock());
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Чужое на грядке фермер не трогает.
     * <p>
     * Игрок вправе поставить на своём поле что угодно; выкапывать землю
     * из-под его сундука — не работа фермера, а порча имущества.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "farmer")
    public void farmerLeavesPlayerBlocksOnTheFieldAlone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            Item crop = FarmJob.seedOf(world, farmPlan).orElseThrow();
            BlockPos plot = FarmJob.plots(farm).get(0);

            world.setBlockState(plot, Blocks.STONE.getDefaultState());
            world.setBlockState(plot.down(), Blocks.DIRT.getDefaultState());

            Warehouse.of(world, colony).add(new ItemStack(crop, 4));

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, plot.up(2));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

            if (!world.getBlockState(plot).isOf(Blocks.STONE)) {
                context.throwGameTestException("Фермер убрал чужой блок с грядки");
            }
            if (!world.getBlockState(plot.down()).isOf(Blocks.DIRT)) {
                context.throwGameTestException("Фермер вскопал землю под чужим блоком");
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- задача 1.10: пульт колонии ---

    /**
     * Снимок показывает колонию такой, какая она есть.
     * <p>
     * Экран не считает ничего сам: свободные кровати, порции еды и запас
     * дней приходят с сервера готовыми. Поэтому проверять надо именно
     * снимок — вёрстку owo-ui игровой тест увидеть не может, у него нет
     * клиента.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "townhall")
    public void viewShowsTheColonyAsItIs(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);
            if (BuildJob.advance(world, manager, colony.id(), house.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не достроился");
            }

            Citizen starving = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            starving.setSaturation(0);
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 4));
            Housing.assignBeds(world, colony);

            TownHallView view = TownHallView.of(world, colony);

            if (!view.name().equals(colony.name()) || !view.culture().equals(NORMAN)) {
                context.throwGameTestException("Снимок не о той колонии: " + view.name());
            }
            if (view.population() != 2 || view.maxCitizens() != colony.level().maxCitizens()) {
                context.throwGameTestException("Жителей в снимке " + view.population()
                        + ", в колонии " + colony.population());
            }
            if (view.beds() != 2 || view.freeBeds() != 0) {
                context.throwGameTestException("Кроватей " + view.beds() + ", свободно "
                        + view.freeBeds() + "; в схеме дома две, и обе заняты");
            }
            if (view.containers() != 1) {
                context.throwGameTestException("Хранилищ " + view.containers()
                        + ", а стоит одна ратуша");
            }
            if (view.meals() != 4) {
                context.throwGameTestException("Порций еды " + view.meals() + ", завезено четыре");
            }

            // Четыре хлеба по десять сытости на двоих при суточной трате
            // восемь — это ровно два дня, и это то число, по которому
            // игрок решает, ехать ли за едой.
            int expectedDays = Needs.nourishment(Items.BREAD) * 4 / (Needs.DAILY_COST * 2);
            if (view.daysOfFood() != expectedDays) {
                context.throwGameTestException("Запас дней " + view.daysOfFood()
                        + ", ожидалось " + expectedDays);
            }
            if (view.stock().count(Registries.ITEM.getId(Items.BREAD)) != 4) {
                context.throwGameTestException("Хлеб в снимке склада не найден");
            }

            if (view.buildings().size() != 1
                    || view.buildings().get(0).progress() != BuildProgress.DONE) {
                context.throwGameTestException("Зданий в снимке " + view.buildings().size()
                        + ", ожидался один готовый дом");
            }
            if (view.construction().isPresent()) {
                context.throwGameTestException("Снимок говорит о стройке, а строить нечего");
            }

            if (view.citizens().size() != 2) {
                context.throwGameTestException("Жителей в списке " + view.citizens().size());
            }
            TownHallView.CitizenLine hungry = view.citizens().stream()
                    .filter(line -> line.id().equals(starving.id()))
                    .findFirst().orElse(null);
            if (hungry == null || hungry.mood() != Mood.STARVING) {
                context.throwGameTestException("Голодающий житель показан как "
                        + (hungry == null ? "никак" : hungry.mood()));
            }
            if (!hungry.housed()) {
                context.throwGameTestException("Житель с кроватью показан бездомным");
            }

            // Предлагаются только здания своего народа, и все загруженные
            // схемы норманнов в его списке есть.
            if (view.offers().size() != SchematicLoader.ids().size()) {
                context.throwGameTestException("Предложено " + view.offers().size()
                        + " схем из " + SchematicLoader.ids().size() + " загруженных");
            }

            // Профессии едут в снимке вместе с ключами названий: на клиенте,
            // подключённом к выделенному серверу, файлов датапака нет вовсе.
            if (view.professions().size() != ProfessionManager.ids().size()) {
                context.throwGameTestException("Профессий в снимке " + view.professions().size()
                        + ", загружено " + ProfessionManager.ids().size());
            }
            for (TownHallView.ProfessionLine line : view.professions()) {
                if (line.displayName().isBlank()) {
                    context.throwGameTestException("У профессии " + line.id()
                            + " в снимке нет ключа названия");
                }
            }
        } finally {
            demolish(world, house, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Снимок называет то, чего не хватает стройке, — с учётом склада.
     * <p>
     * Игрок действует по этому списку, и «не хватает» для него значит
     * «нет ни у стройки, ни в сундуках». Список, считающий только запас
     * площадки, гнал бы его за материалами, которые уже лежат в ратуше.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "townhall")
    public void viewNamesWhatTheConstructionLacks(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            TownHallView empty = TownHallView.of(world, colony);
            TownHallView.Construction lacking = empty.construction().orElse(null);

            if (lacking == null) {
                context.throwGameTestException("Размеченная стройка не попала в снимок");
            }
            if (!lacking.type().equals(FARM_TYPE) || lacking.step() != 0) {
                context.throwGameTestException("В снимке не та стройка: " + lacking.type());
            }
            if (lacking.steps() != farmPlan.plan().steps().size()) {
                context.throwGameTestException("Шагов в снимке " + lacking.steps()
                        + ", в плане " + farmPlan.plan().steps().size());
            }
            if (lacking.missing().total() <= 0) {
                context.throwGameTestException("Пустой склад, а стройке всего хватает");
            }

            stockFor(world, colony, farmPlan);
            TownHallView supplied = TownHallView.of(world, colony);
            TownHallView.Construction enough = supplied.construction().orElseThrow();

            if (enough.missing().total() != 0) {
                context.throwGameTestException("Материалы на складе, а снимок просит ещё "
                        + enough.missing().total() + " штук");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Заказ здания отвергает то, что испортило бы мир, и при отказе ничего
     * не меняет.
     * <p>
     * Проверки живут в одном месте на команду и на кнопку экрана: кнопка,
     * проверяющая меньше команды, была бы дыркой в обход неё.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "townhall")
    public void orderRefusesWhatWouldBreakTheWorld(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            BuildOrders.Result unknown = BuildOrders.place(manager, colony,
                    new Identifier("villagepax", "norman/no_such_lvl1"), anchor, BlockRotation.NONE);
            if (!(unknown instanceof BuildOrders.Result.NoSchematic)) {
                context.throwGameTestException("Незнакомая схема принята: " + unknown);
            }

            BlockPos faraway = hall.add(200, 0, 200);
            BuildOrders.Result outside = BuildOrders.place(manager, colony, HOUSE_SCHEMATIC,
                    faraway, BlockRotation.NONE);
            if (!(outside instanceof BuildOrders.Result.OutsideClaim)) {
                context.throwGameTestException("Стройка за границами колонии принята: " + outside);
            }
            if (!colony.buildings().isEmpty()) {
                context.throwGameTestException("Отказ всё-таки разметил стройку");
            }

            BuildOrders.Result placed = BuildOrders.place(manager, colony, HOUSE_SCHEMATIC,
                    anchor, BlockRotation.NONE);
            if (!(placed instanceof BuildOrders.Result.Placed done)) {
                context.throwGameTestException("Законный заказ отвергнут: " + placed);
                return;
            }
            if (!done.site().type().equals(HOUSE_TYPE) || done.site().level() != 1) {
                context.throwGameTestException("Размечено не то: " + done.site().type()
                        + " ур. " + done.site().level());
            }
            if (colony.buildings().size() != 1) {
                context.throwGameTestException("Зданий в колонии " + colony.buildings().size());
            }

            BuildOrders.Result clash = BuildOrders.place(manager, colony, HOUSE_SCHEMATIC,
                    anchor.add(1, 0, 1), BlockRotation.NONE);
            if (!(clash instanceof BuildOrders.Result.Overlaps)) {
                context.throwGameTestException("Наложение следов принято: " + clash);
            }
            if (colony.buildings().size() != 1) {
                context.throwGameTestException("Отказ по наложению всё-таки добавил здание");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Смена дела вступает в силу сразу: фермер получает ферму, не дожидаясь
     * рассвета.
     * <p>
     * Решение заказчика — профессии назначаются сами, но игрок вправе
     * переназначить. Кнопка, действующая только со следующего дня,
     * выглядела бы сломанной.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "townhall")
    public void assignmentGivesTheFarmerHisFarmAtOnce(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            Citizen worker = hireWithBody(world, colony, HaulJob.COURIER, hall.up());

            Assignments.Result done = Assignments.set(world, manager, colony, worker.id(),
                    Optional.of(FarmJob.FARMER));
            if (done != Assignments.Result.DONE) {
                context.throwGameTestException("Смена дела отвергнута: " + done);
            }
            if (!worker.profession().orElseThrow().equals(FarmJob.FARMER)) {
                context.throwGameTestException("Профессия не сменилась: "
                        + worker.profession().orElse(null));
            }
            if (Workplaces.of(colony, worker).map(Building::id).filter(farm.id()::equals).isEmpty()) {
                context.throwGameTestException("Новому фермеру не досталась ферма — "
                        + "мастерские раздаются только на смене суток");
            }

            Assignments.Result unknown = Assignments.set(world, manager, colony, worker.id(),
                    Optional.of(new Identifier("villagepax", "no_such_profession")));
            if (unknown != Assignments.Result.NO_SUCH_PROFESSION) {
                context.throwGameTestException("Несуществующая профессия принята: " + unknown);
            }
            if (!worker.profession().orElseThrow().equals(FarmJob.FARMER)) {
                context.throwGameTestException("Отказ всё-таки сменил профессию");
            }

            Assignments.Result stranger = Assignments.set(world, manager, colony,
                    UUID.randomUUID(), Optional.of(FarmJob.FARMER));
            if (stranger != Assignments.Result.NO_SUCH_CITIZEN) {
                context.throwGameTestException("Чужой житель принят: " + stranger);
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- помощники задачи 1.8 ---

    private static Schematic schematic(TestContext context, Identifier id) {
        Optional<Schematic> found = SchematicLoader.get(id);
        if (found.isEmpty()) {
            context.throwGameTestException("Схема " + id + " не загружена. Загружены: "
                    + SchematicLoader.ids());
        }
        return found.orElseThrow();
    }

    private static Building plan(Settlement colony, BlockPos anchor, Identifier type,
                                 BlockRotation rotation) {
        Building site = new Building(UUID.randomUUID(), type, 1, anchor, rotation,
                BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);
        return site;
    }

    private static void runWork(ServerWorld world, SettlementManager manager, Settlement colony,
                                Citizen worker, int rounds, Schedule part) {
        CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());

        for (int round = 0; round < rounds; round++) {
            WorkTicker.decide(world, manager, colony, worker, part);

            BlockPos target = body.workTarget();
            if (target != null) {
                body.refreshPositionAndAngles(target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                        0f, 0f);
            }
        }
    }
    // --- помощники задачи 1.7б ---

    /**
     * Прогон стратегии с телепортом вместо ходьбы.
     * <p>
     * Тесты стратегии не должны зависеть от поиска пути: он медленный,
     * зависит от рельефа и способен сорвать тест по причинам, к решениям
     * жителя не относящимся. Что жители действительно ходят, доказывает
     * {@code playerPathRaisesBuildingByItself} на настоящих тиках мира.
     */
    private static Citizen hireWithBody(ServerWorld world, Settlement colony, Identifier profession,
                                        BlockPos at) {
        Citizen citizen = Citizen.newborn("Работник", "", NORMAN, Gender.FEMALE);
        citizen.setProfession(profession);
        citizen.setPosition(Vec3d.ofBottomCenter(at));
        colony.addCitizen(citizen);
        CitizenSpawner.spawnBody(world, colony, citizen);
        return citizen;
    }

    /** Тела не сохраняются, но живут до выгрузки: игровые тесты делят один мир. */
    private static void discardBodies(ServerWorld world, Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            citizen.entityUuid().map(world::getEntity).ifPresent(Entity::discard);
        }
    }

    // --- помощники задачи 1.6 ---

    /** Ратуша ставится настоящим блоком: без неё у колонии нет ни одного хранилища. */
    private static Settlement colonyWithBuilder(ServerWorld world, SettlementManager manager, BlockPos center) {
        world.setBlockState(center, ModBlocks.TOWN_HALL.getDefaultState());
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

    private static void stockFor(ServerWorld world, Settlement colony, Schematic schematic) {
        Warehouse warehouse = Warehouse.of(world, colony);
        Materials.required(schematic).forEach((item, count) -> {
            int left = count;
            while (left > 0) {
                int chunk = Math.min(left, item.getMaxCount());
                warehouse.add(new ItemStack(item, chunk));
                left -= chunk;
            }
        });
    }

    /** Убрать за собой: игровые тесты делят один мир. */
    private static void demolish(ServerWorld world, Building site, Schematic schematic) {
        for (BuildStep step : schematic.plan().steps()) {
            world.setBlockState(BuildJob.worldPos(site, schematic.size(), step.pos()),
                    Blocks.AIR.getDefaultState(), net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }
}
