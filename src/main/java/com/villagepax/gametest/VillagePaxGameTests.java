package com.villagepax.gametest;

import com.villagepax.block.LaundryBlock;
import com.villagepax.block.ModBlocks;
import com.villagepax.sim.build.Furnishings;
import com.villagepax.block.entity.RopeBlockEntity;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureKind;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.core.culture.Trait;
import com.villagepax.core.culture.Traits;
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
import com.villagepax.core.war.WarParty;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Comfort;
import com.villagepax.sim.Gender;
import com.villagepax.sim.Levels;
import com.villagepax.sim.Owner;
import com.villagepax.sim.Hazards;
import com.villagepax.sim.Guide;
import com.villagepax.sim.Ground;
import com.villagepax.sim.ItemTally;
import com.villagepax.sim.Raising;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementLevel;
import com.villagepax.sim.SettlementManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.inventory.Inventory;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
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
import com.villagepax.core.ModTags;
import com.villagepax.screen.QuestView;
import com.villagepax.screen.QuestNet;
import com.villagepax.sim.work.CraftJob;
import com.villagepax.sim.war.Peace;
import com.villagepax.sim.diplomacy.Alliance;
import com.villagepax.sim.diplomacy.Tribute;
import com.villagepax.sim.war.Allies;
import com.villagepax.sim.war.Raids;
import com.villagepax.sim.war.Siege;
import com.villagepax.sim.work.Hauling;
import com.villagepax.core.config.Config;
import com.villagepax.core.config.Configs;
import com.villagepax.screen.ColonyNet;
import com.villagepax.sim.build.Roads;
import com.villagepax.sim.work.Needs;
import com.villagepax.core.quest.Quest;
import com.villagepax.core.trade.Caravan;
import com.villagepax.core.trade.TradeTable;
import net.minecraft.inventory.SimpleInventory;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.sim.Standing;
import com.villagepax.sim.diplomacy.Gifts;
import com.villagepax.sim.diplomacy.Relations;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.trade.Caravans;
import com.villagepax.sim.trade.Coins;
import com.villagepax.sim.trade.Trading;
import com.villagepax.item.ModItems;
import com.villagepax.item.PurseItem;
import com.villagepax.sim.Villages;
import com.villagepax.sim.VillageSites;
import net.minecraft.util.math.ChunkPos;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.build.Decor;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import com.villagepax.screen.ColonyMap;
import com.villagepax.screen.ColonyNet;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
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
import com.villagepax.sim.work.GuardJob;
import com.villagepax.sim.work.Housing;
import com.villagepax.sim.work.Jobs;
import com.villagepax.screen.Advice;
import com.villagepax.screen.BuildOrders;
import com.villagepax.screen.GhostPlan;
import com.villagepax.screen.Mood;
import com.villagepax.screen.TownHallConsole;
import com.villagepax.screen.TownHallView;
import com.villagepax.sim.work.Assignments;
import com.villagepax.sim.work.WorkContext;
import com.villagepax.sim.work.Workplaces;
import com.villagepax.sim.work.Needs;
import com.villagepax.sim.work.Schedule;
import com.villagepax.sim.work.WorkTicker;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resource.Resource;
import net.minecraft.util.math.Vec3i;

import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.Access;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Roads;
import com.villagepax.sim.work.BuilderJob;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;

import java.util.ArrayList;
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
        // Шагов теперь больше, чем клеток схемы, и это правило, а не
        // погрешность: сверх следа план расчищает ПОДХОД ко входу —
        // проход в рост человека на три шага от порога. Им билдер и валит
        // дерево, выросшее у калитки, из-за которого на ферму было не войти.
        int inside = 0;
        int outside = 0;
        for (BuildStep step : plan.steps()) {
            BlockPos at = step.pos();
            boolean within = at.getX() >= 0 && at.getY() >= 0 && at.getZ() >= 0
                    && at.getX() < size.getX() && at.getY() < size.getY() && at.getZ() < size.getZ();
            if (within) {
                inside++;
                continue;
            }
            outside++;
            if (step.placesBlock()) {
                context.throwGameTestException("За следом здания план ставит блок на "
                        + at.toShortString() + ": туда билдеру можно только с топором");
            }
        }
        if (inside != volume) {
            context.throwGameTestException("Клеток схемы в плане " + inside
                    + " при объёме " + volume + " — часть схемы потерялась");
        }
        if (outside == 0) {
            context.throwGameTestException("Подход ко входу не расчищается: "
                    + "дерево у порога снова останется стоять");
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

            // Слоты декора из проверки исключены намеренно: они и есть
            // расчищенные места, в которые обстановку ставит Decor — у одного
            // дома поленница, у другого бельё. Пустыми они быть не обязаны.
            Set<BlockPos> decorSlots = new HashSet<>(
                    BuildJob.pointsOfInterest(site, schematic, MarkerKind.DECOR));

            for (BuildStep step : schematic.plan().steps()) {
                BlockPos where = BuildJob.worldPos(site, schematic.size(), step.pos());
                BlockState actual = world.getBlockState(where);

                if (!step.placesBlock()) {
                    if (!actual.isAir() && !decorSlots.contains(where)) {
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

        // Первый надел колонии тут же и сносится. Эта проверка — про
        // приказ игрока и про то, что тикер работ подключён к тику мира;
        // подаренные дом и поле встают сами, без билдера, и вдобавок
        // занимают ту самую площадку, которую проверка размечает следом.
        razeHolding(world, colony);

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
                if (colony.population() > 0 && victim.discontent() == Needs.warnAfterDays()) {
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

            // Двадцать три, а не двадцать четыре: одну клетку занял тюк
            // пугала, которое с этой правки стоит на поле с первого
            // уровня. Число здесь написано числом намеренно — считать
            // грядки по той же схеме, которую строит проверка, значит
            // спрашивать ответ у проверяемого.
            List<BlockPos> plots = FarmJob.plots(farm);
            if (plots.size() != 23) {
                context.throwGameTestException("Грядок на ферме " + plots.size()
                        + ", в схеме двадцать три");
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

            // Предлагаются только здания своего народа, только первого
            // уровня и без ратуши: уровни растут кнопкой «Улучшить», а
            // ратуша стоит с основания. Считается ожидаемое по загруженным
            // схемам, а не числом: добавится ещё одно здание — тест не
            // придётся править.
            //
            // «Своего народа» пришлось начать считать всерьёз, когда народов
            // стало двое: до майя список схем и список своих зданий совпадали,
            // и тест этого не различал.
            List<Identifier> ourBuildings = CultureManager.get(NORMAN).buildings();
            long expectedOffers = SchematicLoader.ids().stream()
                    .filter(schematic -> BuildJob.levelOf(schematic).orElse(1) == 1)
                    .filter(schematic -> BuildJob.buildingTypeOf(schematic)
                            .filter(type -> !Levels.isTownHallType(type)
                                    && ourBuildings.contains(type))
                            .isPresent())
                    .count();
            if (view.offers().size() != expectedOffers) {
                context.throwGameTestException("Предложено " + view.offers().size()
                        + " схем, а первых уровней без ратуши " + expectedOffers);
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

    /**
     * Дерево валится целиком: с ветками и кроной, без висящих остатков.
     * <p>
     * Прежний обход шёл одной колонной над подножием и снимал крону
     * коробкой в три блока. У дуба ветки отходят в сторону, у тёмного дуба
     * ствол вообще толщиной в четыре бревна, а крона шире трёх блоков —
     * и игрок видел обрубок с висящей листвой вместо сваленного дерева.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wholetree")
    public void wholeTreeComesDownWithBranchesAndCanopy(TestContext context) {
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

            BlockPos tile = GatherJob.groveTiles(hut).get(0);

            // Ствол в шесть брёвен, ветка углом и крона шире прежней коробки.
            for (int dy = 0; dy < 6; dy++) {
                world.setBlockState(tile.up(dy), Blocks.OAK_LOG.getDefaultState());
            }
            BlockPos elbow = tile.add(1, 4, 1);
            BlockPos tip = elbow.add(1, 0, 0);
            world.setBlockState(elbow, Blocks.OAK_LOG.getDefaultState());
            world.setBlockState(tip, Blocks.OAK_LOG.getDefaultState());

            List<BlockPos> canopy = new ArrayList<>();
            for (int dx = -2; dx <= 3; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 5; dy <= 6; dy++) {
                        BlockPos at = tile.add(dx, dy, dz);
                        if (world.getBlockState(at).isAir()) {
                            world.setBlockState(at, Blocks.OAK_LEAVES.getDefaultState());
                            canopy.add(at);
                        }
                    }
                }
            }
            if (canopy.size() < 40) {
                context.throwGameTestException("Крона не поставилась: листьев "
                        + canopy.size());
            }

            Warehouse.of(world, colony).add(new ItemStack(Items.OAK_SAPLING, 4));
            int logsBefore = Warehouse.of(world, colony).count(Items.OAK_LOG);

            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, tile.up(8));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, woodsman, 60, Schedule.MORNING_WORK);

            // Восемь брёвен: шесть ствола и два ветки.
            if (Warehouse.of(world, colony).count(Items.OAK_LOG) < logsBefore + 8) {
                context.throwGameTestException("Ветку не срубили: брёвен на складе "
                        + (Warehouse.of(world, colony).count(Items.OAK_LOG) - logsBefore)
                        + " из восьми");
            }
            if (!world.getBlockState(elbow).isAir() || !world.getBlockState(tip).isAir()) {
                context.throwGameTestException("Ветка осталась висеть");
            }
            for (BlockPos leaf : canopy) {
                if (!world.getBlockState(leaf).isAir()) {
                    context.throwGameTestException("Крона осталась висеть: "
                            + world.getBlockState(leaf).getBlock() + " в " + leaf.toShortString());
                }
            }

            // Стены мастерской из тёмного дуба остались на месте: они тоже брёвна.
            BlockPos post = anchor.add(0, 1, 0);
            if (!world.getBlockState(post).isIn(BlockTags.LOGS)) {
                context.throwGameTestException("Связный обход дошёл до стены мастерской");
            }
        } finally {
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- рост колонии по уровню ратуши ---

    private static final Identifier TOWN_HALL_LVL2 =
            new Identifier("villagepax", "norman/town_hall_lvl2");

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

    // --- кто чем занят и когда отступается ---

    /**
     * Курьер и билдер делят одну стройку: подвоз материалов не запрещает
     * стройку.
     * <p>
     * Занятость считается по делу, а не по зданию, и это не тонкость.
     * Курьер, несущий материалы, держит в состоянии работы ту же стройку,
     * что и билдер, — общий счёт означал бы, что стоит курьеру взяться
     * за подвоз, и здание перестаёт строиться вообще. Ровно это и было
     * после починки толкотни.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "claims")
    public void courierAndBuilderShareOneSite(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        // Далеко по высоте: те же чанки заведомо загружены, а склад «не рядом».
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 24, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);
            if (BuildJob.storageIsNearby(Warehouse.of(world, colony), site)) {
                context.throwGameTestException("Склад оказался рядом — курьеру нечего делать");
            }

            Citizen porter = hireWithBody(world, colony, HaulJob.COURIER, hall.up());
            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());

            runWork(world, manager, colony, porter, 1, Schedule.MORNING_WORK);
            if (porter.jobState().building().filter(site.id()::equals).isEmpty()) {
                context.throwGameTestException("Курьер не взялся за заявку — проверять нечего");
            }

            runWork(world, manager, colony, mason, 1, Schedule.MORNING_WORK);
            if (mason.jobState().building().filter(site.id()::equals).isEmpty()) {
                context.throwGameTestException("Билдер не взялся за стройку, к которой едет "
                        + "курьер: подвоз материалов запретил стройку");
            }

            // А вот второй билдер по-прежнему за неё не берётся.
            Citizen second = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, second, 1, Schedule.MORNING_WORK);
            if (!second.jobState().isIdle()) {
                context.throwGameTestException("Двое билдеров на одной стройке снова толкаются");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Житель отступается от точки, до которой не может дойти, и берётся
     * за следующее дело.
     * <p>
     * Работа выбирается как первая подходящая. Без отступления недостижимая
     * грядка держала фермера навсегда: он выбирал её решение за решением,
     * а всё остальное поле стояло. Игрок видит это как зависшего работника.
     * <p>
     * Проверяется <b>без телепорта</b>: обычный прогон работы подносит
     * жителя к цели, и недостижимости в нём не бывает по определению.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "claims")
    public void workerGivesUpOnAnUnreachableTarget(TestContext context) {
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

            // Две грядки поспели: отступившись от первой, фермер должен
            // взяться за вторую.
            List<BlockPos> plots = FarmJob.plots(farm);
            BlockState mature = ((CropBlock) Blocks.CARROTS).withAge(CropBlock.MAX_AGE);
            world.setBlockState(plots.get(0), mature);
            world.setBlockState(plots.get(5), mature);

            // Фермер далеко и с места не двигается: тест не телепортирует его.
            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, hall.up(40));
            Workplaces.assign(world, colony);
            CitizenEntity body = (CitizenEntity) world.getEntity(farmer.entityUuid().orElseThrow());

            WorkTicker.decide(world, manager, colony, farmer, Schedule.MORNING_WORK);
            BlockPos first = body.workTarget();
            if (first == null) {
                context.throwGameTestException("Фермер не выбрал грядку");
            }

            // Смотрим на память о недостижимом, а не на саму цель: цель
            // теперь — место, ОТКУДА берутся за грядку, а не сама грядка,
            // и у двух соседних грядок это место может быть одним и тем же.
            for (int decision = 0; decision < 12; decision++) {
                WorkTicker.decide(world, manager, colony, farmer, Schedule.MORNING_WORK);
            }

            boolean gaveUp = body.isUnreachable(plots.get(0))
                    || body.isUnreachable(plots.get(5));
            if (!gaveUp) {
                context.throwGameTestException("Фермер двенадцать решений метит в одну "
                        + "недостижимую грядку и не отступается: цель "
                        + body.workTarget());
            }

            BlockPos later = body.workTarget();
            if (later == null) {
                context.throwGameTestException("Отступившись, фермер не взялся ни за что");
                return;
            }
            boolean besidePlot = plots.stream()
                    .anyMatch(plot -> plot.getSquaredDistance(later) <= 9);
            if (!besidePlot) {
                context.throwGameTestException("Отступившись, фермер пошёл не к грядке: "
                        + later.toShortString());
            }
        } finally {
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- работа в руках ---

    /**
     * Строитель держит тот блок, который ставит.
     * <p>
     * Просьба заказчика, и не косметическая: без предмета в руке понять,
     * что происходит, можно только по растущей стене. Проверяется на сервере,
     * потому что снаряжение — серверное состояние: клиент его лишь рисует.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hands")
    public void builderHoldsTheBlockHeIsPlacing(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(mason.entityUuid().orElseThrow());

            if (!body.getMainHandStack().isEmpty()) {
                context.throwGameTestException("Житель родился с предметом в руке");
            }

            runWork(world, manager, colony, mason, 40, Schedule.MORNING_WORK);

            // В руке должен быть ровно тот блок, который стоит следующим
            // в плане, — а не просто «что-нибудь».
            Item expected = expectedBlockInHand(housePlan, site);
            if (expected == null) {
                context.throwGameTestException("План кончился раньше, чем тест успел проверить руку");
            }
            if (!body.getMainHandStack().isOf(expected)) {
                context.throwGameTestException("В руке " + body.getMainHandStack()
                        + ", а ставит он " + expected);
            }

            // Отпустил работу — руки пусты.
            mason.setJobState(JobState.IDLE);
            mason.setProfession(null);
            runWork(world, manager, colony, mason, 1, Schedule.MORNING_WORK);
            if (!body.getMainHandStack().isEmpty()) {
                context.throwGameTestException("Житель без дела остался с инструментом: "
                        + body.getMainHandStack());
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Какой блок стоит следующим в плане — тот и должен быть в руке. */
    private static Item expectedBlockInHand(Schematic schematic, Building site) {
        List<BuildStep> steps = schematic.plan().steps();
        if (site.nextStep() >= steps.size()) {
            return null;
        }

        BuildStep step = steps.get(site.nextStep());
        return step.placesBlock()
                ? Materials.itemFor(schematic.blockAt(step.paletteIndex())).orElse(null)
                : Items.IRON_PICKAXE;
    }

    /**
     * Лесоруб держит топор, курьер — свой груз.
     * <p>
     * У курьера это самый честный показ работы в моде: игрок видит не
     * «житель идёт», а «житель несёт двадцать брёвен вон туда».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hands")
    public void workersCarryTheirToolsAndLoads(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic hutPlan = schematic(context, LUMBERJACK_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building hut = plan(colony, anchor, LUMBERJACK_TYPE, BlockRotation.NONE);
        // Далеко по высоте, а не по горизонтали: те же чанки заведомо загружены.
        Building far = plan(colony, anchor.add(0, 22, 0), HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, hutPlan);
            BuildJob.advance(world, manager, colony.id(), hut.id(), 10_000);

            // Лесоруб: на грядке выросло дерево, значит есть работа.
            BlockPos tile = GatherJob.groveTiles(hut).get(0);
            for (int dy = 0; dy < 3; dy++) {
                world.setBlockState(tile.up(dy), Blocks.OAK_LOG.getDefaultState());
            }
            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, tile.up(5));
            Workplaces.assign(world, colony);
            runWork(world, manager, colony, woodsman, 1, Schedule.MORNING_WORK);

            CitizenEntity axeman = (CitizenEntity) world.getEntity(woodsman.entityUuid().orElseThrow());
            if (!axeman.getMainHandStack().isOf(Items.IRON_AXE)) {
                context.throwGameTestException("Лесоруб без топора: "
                        + axeman.getMainHandStack());
            }

            // Курьер: далёкая стройка и материалы на складе — он их понесёт.
            // Завозится ровно то, что нужно дому: иначе курьеру нечего нести
            // и тест проверял бы не руки, а собственную догадку.
            stockFor(world, colony, schematic(context, HOUSE_SCHEMATIC));
            Citizen porter = hireWithBody(world, colony, HaulJob.COURIER, hall.up());

            CitizenEntity carrier = (CitizenEntity) world.getEntity(porter.entityUuid().orElseThrow());

            // Груз живёт недолго: взял, донёс, сдал. Поэтому руки проверяются
            // в тот шаг, когда груз действительно в них, а не после.
            boolean seenCarrying = false;
            for (int round = 0; round < 24 && !seenCarrying; round++) {
                runWork(world, manager, colony, porter, 1, Schedule.MORNING_WORK);

                Identifier load = porter.jobState().firstLoad()
                        .map(JobState.Load::item)
                        .orElse(null);
                if (load == null) {
                    continue;
                }
                seenCarrying = true;

                if (!carrier.getMainHandStack().isOf(Registries.ITEM.get(load))) {
                    context.throwGameTestException("Курьер несёт " + load
                            + ", а в руках у него " + carrier.getMainHandStack());
                }
            }
            if (!seenCarrying) {
                context.throwGameTestException("Курьер так и не взял груз — тест проверяет не то");
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
     * Инструмент не выпадает из погибшего жителя.
     * <p>
     * Иначе топор лесоруба — бесконечный источник железа: житель погиб,
     * топор упал, наняли нового — и снова топор. Проверяется смертью,
     * а не полем «шанс выпадения»: ваниль его наружу не отдаёт, а важно
     * тут поведение.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hands")
    public void toolsDoNotDropFromTheBody(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen worker = hireWithBody(world, colony, GatherJob.LUMBERJACK, hall.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());
            body.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));

            body.kill();

            Box around = new Box(hall).expand(6.0);
            for (ItemEntity dropped : world.getEntitiesByClass(ItemEntity.class, around, entity -> true)) {
                if (dropped.getStack().isOf(Items.IRON_AXE)) {
                    context.throwGameTestException("Из погибшего жителя выпал топор");
                }
                dropped.discard();
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- задача 1.11: голограмма ---

    /**
     * Примерка места ничего не меняет в колонии.
     * <p>
     * Голограмма спрашивает сервер при каждом сдвиге на блок — то есть
     * несколько раз в секунду. Если бы проверка что-то откладывала
     * в колонию, водя призраком по земле игрок засеивал бы её стройками.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hologram")
    public void probingASpotChangesNothing(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (int step = 0; step < 20; step++) {
                BuildOrders.Result verdict = BuildOrders.check(colony, HOUSE_SCHEMATIC,
                        anchor.add(step, 0, 0), BlockRotation.NONE);
                if (!(verdict instanceof BuildOrders.Result.Placed)) {
                    context.throwGameTestException("Примерка на своей земле отвергнута: " + verdict);
                }
            }

            if (!colony.buildings().isEmpty()) {
                context.throwGameTestException("Примерка разметила " + colony.buildings().size()
                        + " стройек, а не должна ни одной");
            }

            // А заказ — меняет, и ровно одну.
            BuildOrders.place(manager, colony, HOUSE_SCHEMATIC, anchor, BlockRotation.NONE);
            if (colony.buildings().size() != 1) {
                context.throwGameTestException("Заказ не разметил стройку");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Приёмка задачи 1.11 со стороны данных: стройка встаёт ровно там
     * и ровно так, как показывал призрак.
     * <p>
     * Голограмма отправляет место и поворот, которые игрок видел; если
     * заказ поставит здание иначе, вся задача бессмысленна.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hologram")
    public void orderLandsExactlyWhereTheGhostStood(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos ghost = context.getAbsolutePos(new BlockPos(3, 8, 5));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            BuildOrders.Result result = BuildOrders.place(manager, colony, HOUSE_SCHEMATIC,
                    ghost, BlockRotation.CLOCKWISE_90);

            if (!(result instanceof BuildOrders.Result.Placed placed)) {
                context.throwGameTestException("Заказ по месту голограммы отвергнут: " + result);
                return;
            }
            if (!placed.site().anchor().equals(ghost)) {
                context.throwGameTestException("Стройка встала не там: "
                        + placed.site().anchor().toShortString() + " вместо "
                        + ghost.toShortString());
            }
            if (placed.site().rotation() != BlockRotation.CLOCKWISE_90) {
                context.throwGameTestException("Поворот потерялся: " + placed.site().rotation());
            }

            Building inColony = colony.buildings().get(0);
            if (!inColony.anchor().equals(ghost)
                    || inColony.rotation() != BlockRotation.CLOCKWISE_90) {
                context.throwGameTestException("В колонии здание лежит иначе, чем вернул заказ");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * План призрака переживает дорогу по сети.
     * <p>
     * Клиент схем не видит — они в датапаке сервера, — поэтому план едет
     * пакетом. Потерянный при этом блок означает дырку в призраке, а
     * потерянное состояние — дверь, повёрнутую не туда.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "hologram")
    public void ghostPlanSurvivesTheWire(TestContext context) {
        Schematic townHall = schematic(context, TOWN_HALL_SCHEMATIC);

        List<GhostPlan.Ghost> blocks = new ArrayList<>();
        for (BuildStep step : townHall.plan().steps()) {
            if (step.placesBlock()) {
                blocks.add(new GhostPlan.Ghost(step.pos(), townHall.blockAt(step.paletteIndex())));
            }
        }

        GhostPlan sent = new GhostPlan(TOWN_HALL_SCHEMATIC, townHall.size(), blocks);
        PacketByteBuf buf = PacketByteBufs.create();
        sent.write(buf);
        GhostPlan back = GhostPlan.read(buf);

        if (!back.schematic().equals(sent.schematic()) || !back.size().equals(sent.size())) {
            context.throwGameTestException("Схема или размер потерялись: " + back.schematic()
                    + " " + back.size());
        }
        if (back.blocks().size() != sent.blocks().size()) {
            context.throwGameTestException("Блоков доехало " + back.blocks().size()
                    + " из " + sent.blocks().size());
        }
        for (int index = 0; index < sent.blocks().size(); index++) {
            GhostPlan.Ghost before = sent.blocks().get(index);
            GhostPlan.Ghost after = back.blocks().get(index);

            if (!before.pos().equals(after.pos()) || before.state() != after.state()) {
                context.throwGameTestException("Блок " + index + " доехал искажённым: "
                        + before.state() + " в " + before.pos().toShortString() + " стало "
                        + after.state() + " в " + after.pos().toShortString());
            }
        }
        if (buf.readableBytes() != 0) {
            context.throwGameTestException("В пакете осталось " + buf.readableBytes()
                    + " непрочитанных байт");
        }

        // Расчистка в призрак не входит: игрок выбирает, как встанет здание,
        // а не что будет снесено.
        if (sent.blocks().size() >= townHall.plan().steps().size()) {
            context.throwGameTestException("В призрак попали шаги расчистки");
        }

        context.complete();
    }

    // --- где стоит билдер и кто занял стройку ---

    /**
     * Билдер стоит на земле рядом со стройкой, а не лезет на неё.
     * <p>
     * Стоя на недоделанной стене, он ломает себе путь: навигация ведёт вниз,
     * решение гонит наверх, и он топчется. Именно это игрок видит как
     * «путь сбивается».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "stand")
    public void builderStandsOnTheGroundBesideTheSite(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));
        Vec3i size = housePlan.size();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);
        List<BlockPos> platform = new ArrayList<>();

        try {
            // Площадка готовится начисто: пустота на всю высоту здания, потом
            // земля, и обе поверх того, что было. Мир игровых тестов один
            // на все проверки, области раздаются по-разному от прогона
            // к прогону, а блок, случайно совпавший с планом, стройку
            // не тормозит, а ускоряет — совпавший шаг пропускается, не тратя
            // бюджета, и те же шестьдесят шагов то недостраивают дом,
            // то достраивают целиком.
            for (int dx = -4; dx < size.getX() + 4; dx++) {
                for (int dz = -4; dz < size.getZ() + 4; dz++) {
                    for (int dy = 0; dy <= size.getY() + 2; dy++) {
                        BlockPos clear = anchor.add(dx, dy, dz);
                        if (!world.getBlockState(clear).isAir()) {
                            world.setBlockState(clear, Blocks.AIR.getDefaultState());
                            platform.add(clear);
                        }
                    }

                    BlockPos ground = anchor.add(dx, -1, dz);
                    world.setBlockState(ground, Blocks.DIRT.getDefaultState());
                    platform.add(ground);
                }
            }

            stockFor(world, colony, housePlan);
            // Часть стен уже стоит: раньше билдер лез именно на них.
            BuildJob.advance(world, manager, colony.id(), site.id(), 60);

            if (!BuildJob.isUnderConstruction(site)) {
                context.throwGameTestException("Посылка теста не выполнена: дом достроился "
                        + "за шестьдесят шагов, а проверять надо недостроенный");
            }

            // Спрашивается сам выбор места, а не то, что успел решить тикер.
            // Через жителя эта проверка соревновалась бы с самим модом:
            // мод тикает и принимает решения за того же билдера, и тест
            // мигал через раз не по своей вине.
            BlockPos stand = BuilderJob.standingSpot(world, site).orElse(null);

            if (stand == null) {
                context.throwGameTestException("Билдеру негде встать у стройки на шаге "
                        + site.nextStep());
            }
            if (!world.getBlockState(stand.down()).isSolidBlock(world, stand.down())) {
                context.throwGameTestException("Под ногами билдера не твёрдый блок: "
                        + world.getBlockState(stand.down()).getBlock()
                        + " в " + stand.toShortString());
            }
            if (stand.getY() > anchor.getY() + 2) {
                context.throwGameTestException("Билдер полез наверх: стоит на "
                        + (stand.getY() - anchor.getY()) + " блока выше земли стройки");
            }

            boolean insideFootprint = stand.getX() >= anchor.getX()
                    && stand.getX() < anchor.getX() + size.getX()
                    && stand.getZ() >= anchor.getZ()
                    && stand.getZ() < anchor.getZ() + size.getZ();
            if (insideFootprint) {
                context.throwGameTestException("Билдер встал внутрь следа здания "
                        + stand.toShortString() + ", хотя снаружи есть земля");
            }
        } finally {
            demolish(world, site, housePlan);
            for (BlockPos ground : platform) {
                world.setBlockState(ground, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Одна стройка — один билдер. Второй за неё не берётся.
     * <p>
     * Иначе оба идут к одному блоку и толкаются на нём: путь у каждого
     * сбивается о соседа, никто не доходит, стройка встаёт. Игрок сообщает
     * об этом как «работники повисли».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "stand")
    public void secondBuilderLeavesAClaimedSiteAlone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            Citizen first = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            Citizen second = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());

            runWork(world, manager, colony, first, 1, Schedule.MORNING_WORK);
            if (first.jobState().building().filter(site.id()::equals).isEmpty()) {
                context.throwGameTestException("Первый билдер не взялся за стройку");
            }

            runWork(world, manager, colony, second, 1, Schedule.MORNING_WORK);
            if (!second.jobState().isIdle()) {
                context.throwGameTestException("Второй билдер взялся за занятую стройку: "
                        + second.jobState().phase().id());
            }

            CitizenEntity idle = (CitizenEntity) world.getEntity(second.entityUuid().orElseThrow());
            if (idle.workTarget() != null) {
                context.throwGameTestException("Праздный билдер всё равно идёт на стройку");
            }

            // Первый отпустил работу — стройка снова свободна.
            first.setJobState(JobState.IDLE);
            runWork(world, manager, colony, second, 1, Schedule.MORNING_WORK);
            if (second.jobState().building().filter(site.id()::equals).isEmpty()) {
                context.throwGameTestException("Освободившуюся стройку никто не взял");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- дорога под ногами ---

    /**
     * Житель предпочитает идти по дороге, а не напрямик через газон.
     * <p>
     * Проверяется сравнением: одна и та же местность с мостовой и без неё.
     * Без сравнения тест ничего не значил бы — путь и так мог бы случайно
     * лежать на нужном ряду.
     * <p>
     * Что считать дорогой, решает тег {@code villagepax:preferred_path}:
     * этот тест заодно проверяет, что тег вообще доехал до датапака.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "road")
    public void citizenPrefersThePavedRoute(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos corner = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        List<BlockPos> ground = new ArrayList<>();

        try {
            // Луг три блока в ширину и тринадцать в длину.
            for (int x = 0; x < 13; x++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos at = corner.add(x, 0, z);
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    ground.add(at);
                }
            }

            BlockPos from = corner.add(0, 1, 0);
            BlockPos to = corner.add(12, 1, 0);

            Citizen walker = hireWithBody(world, colony, HaulJob.COURIER, from);
            CitizenEntity body = (CitizenEntity) world.getEntity(walker.entityUuid().orElseThrow());
            body.setOnGround(true);

            int overGrass = pavedSteps(world, body, to, corner);

            // Замостим средний ряд — и путь обязан на него перейти.
            for (int x = 0; x < 13; x++) {
                world.setBlockState(corner.add(x, 0, 1), Blocks.DIRT_PATH.getDefaultState());
            }
            int overRoad = pavedSteps(world, body, to, corner);

            if (overRoad <= overGrass) {
                context.throwGameTestException("Дорога ничего не изменила: шагов по среднему ряду "
                        + overRoad + " с мостовой и " + overGrass + " без неё");
            }
            if (overRoad < 6) {
                context.throwGameTestException("По мостовой прошли всего " + overRoad
                        + " шагов из тринадцати — надбавка за бездорожье не работает");
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Сколько шагов пути легло на средний ряд — тот, который мостят. */
    private static int pavedSteps(ServerWorld world, CitizenEntity body, BlockPos to,
                                  BlockPos corner) {
        body.getNavigation().stop();
        Path path = body.getNavigation().findPathTo(to, 0);
        if (path == null) {
            return 0;
        }

        int steps = 0;
        for (int index = 0; index < path.getLength(); index++) {
            if (path.getNode(index).getBlockPos().getZ() == corner.getZ() + 1) {
                steps++;
            }
        }
        return steps;
    }

    /**
     * Житель не ходит по верху забора.
     * <p>
     * Жалоба игрока: строитель шёл по верхам заборов, скатывался с них
     * и ходил кругами, повиснув насмерть. Ваниль такой путь разрешает —
     * у забора коробка столкновений в полтора блока, и моб как бы на нём
     * стоит, — а стоит он там плохо.
     * <p>
     * Проверяется настоящим поиском пути через огороженное поле: путь
     * внутрь есть (калитка открыта), и ни один его узел не стоит
     * на заборе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gates")
    public void citizenNeverWalksAlongFenceTops(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);

        // Житель ставится с той стороны, где через забор — КОРОЧЕ: калитка
        // на западе, а он приходит с востока. Без запрета ваниль полезет
        // прямо через ограду, потому что обход к калитке вдвое дальше.
        // С площадки у калитки этот тест не кусался бы вовсе: туда путь
        // и так идёт по земле.
        BlockPos landing = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(7, 1, 3));

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            world.setBlockState(landing, Blocks.DIRT.getDefaultState());
            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, landing.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(farmer.entityUuid().orElseThrow());
            body.setOnGround(true);

            BlockPos plot = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(5, 2, 3));
            Path through = body.getNavigation().findPathTo(plot, 0);
            if (through == null) {
                context.throwGameTestException("Пути на поле нет вовсе — калитка сломалась");
                return;
            }

            for (int index = 0; index < through.getLength(); index++) {
                BlockPos step = through.getNode(index).getBlockPos();
                BlockPos under = step.down();
                if (world.getBlockState(under).isIn(BlockTags.FENCES)
                        || world.getBlockState(under).isIn(BlockTags.FENCE_GATES)) {
                    context.throwGameTestException("Путь идёт по верху забора: узел "
                            + step.toShortString() + " стоит на "
                            + world.getBlockState(under).getBlock());
                }
            }
        } finally {
            demolish(world, farm, farmPlan);
            world.setBlockState(landing, Blocks.AIR.getDefaultState());
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * С забора житель сходит сам.
     * <p>
     * Жалоба игрока: строитель шёл по верхам заборов, скатывался с них
     * и ходил кругами, повиснув насмерть. Поиск пути через забор его
     * не ведёт — ваниль считает забор непроходимым, — но оказаться на нём
     * он может: коробка столкновений у забора полтора блока, и в толкотне
     * у калитки жители выталкивают друг друга наверх.
     * <p>
     * Стоя там, житель ломает себе путь: ноги на полуторной высоте, узла
     * пути в этой точке нет, и он съезжает к цели снова и снова. Поэтому
     * его снимают на землю.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gates")
    public void citizenStepsOffAFenceByItself(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos ground = context.getAbsolutePos(new BlockPos(6, 8, 6));
        BlockPos fence = ground.add(2, 0, 0);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        List<BlockPos> laid = new ArrayList<>();

        try {
            // Площадка и забор на ней.
            for (int dx = -2; dx <= 3; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos at = ground.add(dx, 0, dz);
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    laid.add(at);
                }
            }
            world.setBlockState(fence.up(), Blocks.OAK_FENCE.getDefaultState());
            laid.add(fence.up());

            // Житель ставится ровно на забор — так его и выталкивает толкотня.
            Citizen worker = hireWithBody(world, colony, BuildJob.BUILDER, fence.up(2));
            CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());
            body.refreshPositionAndAngles(fence.getX() + 0.5, fence.getY() + 2,
                    fence.getZ() + 0.5, 0f, 0f);

            if (!world.getBlockState(body.getBlockPos().down()).isOf(Blocks.OAK_FENCE)) {
                context.throwGameTestException("Посылка теста не выполнена: житель не на заборе, "
                        + "а на " + world.getBlockState(body.getBlockPos().down()).getBlock());
            }

            if (!body.stepOutOfTrouble()) {
                context.throwGameTestException("Житель остался стоять на заборе");
            }
            if (world.getBlockState(body.getBlockPos().down()).isOf(Blocks.OAK_FENCE)) {
                context.throwGameTestException("Житель сошёл, но опять на забор");
            }
            if (!world.getBlockState(body.getBlockPos().down())
                    .isSolidBlock(world, body.getBlockPos().down())) {
                context.throwGameTestException("Житель сошёл в пустоту: под ним "
                        + world.getBlockState(body.getBlockPos().down()).getBlock());
            }

            // И на твёрдой земле его больше не трогают.
            if (body.stepOutOfTrouble()) {
                context.throwGameTestException("Жителя на земле всё равно переносят");
            }
        } finally {
            for (BlockPos at : laid) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- калитки в ограде ---

    /**
     * На огороженное поле можно войти: в ограде есть открытая калитка.
     * <p>
     * Ванильный поиск пути считает закрытую калитку непроходимой
     * ({@code PathNodeType.FENCE}), а открывать их умеют только двери
     * у деревенских жителей. С глухой оградой фермер стоял бы снаружи
     * своего поля и не делал ничего — и это было ровно так.
     * <p>
     * Проверяется настоящим поиском пути, а не наличием блока: калитку
     * можно поставить и оставить закрытой.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gates")
    public void fencedFarmCanBeEntered(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = plan(colony, anchor, FARM_TYPE, BlockRotation.NONE);
        BlockPos landing = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(-1, 1, 3));

        try {
            stockFor(world, colony, farmPlan);
            BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);

            BlockPos gate = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(0, 2, 3));
            BlockPos plot = BuildJob.worldPos(farm, farmPlan.size(), new BlockPos(1, 2, 3));

            if (!(world.getBlockState(gate).getBlock() instanceof FenceGateBlock)) {
                context.throwGameTestException("В ограде поля нет калитки, стоит "
                        + world.getBlockState(gate).getBlock());
            }

            // Площадка снаружи: поле стоит в пустоте, идти жителю неоткуда.
            world.setBlockState(landing, Blocks.DIRT.getDefaultState());

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, landing.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(farmer.entityUuid().orElseThrow());

            // Только что созданное тело ещё не коснулось земли, а поиск пути
            // из воздуха ванилью запрещён. В игре это происходит само
            // на первом же тике.
            body.setOnGround(true);

            Path through = body.getNavigation().findPathTo(plot, 0);
            if (through == null || !through.reachesTarget()) {
                context.throwGameTestException("Фермер не может войти на своё поле: "
                        + (through == null ? "пути нет вовсе" : "путь не доходит"));
            }

            // Заменим калитку забором — и пути внутрь больше нет. Проверка
            // держится на этом: иначе тест прошёл бы и с глухой оградой.
            world.setBlockState(gate, Blocks.OAK_FENCE.getDefaultState());
            Path blocked = body.getNavigation().findPathTo(plot, 0);
            if (blocked != null && blocked.reachesTarget()) {
                context.throwGameTestException("Путь нашёлся сквозь глухую ограду — "
                        + "значит проверка ничего не проверяет");
            }
        } finally {
            world.setBlockState(landing, Blocks.AIR.getDefaultState());
            demolish(world, farm, farmPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** В рощу лесоруба тоже есть вход — по той же причине. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "gates")
    public void fencedGroveCanBeEntered(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic hutPlan = schematic(context, LUMBERJACK_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building hut = plan(colony, anchor, LUMBERJACK_TYPE, BlockRotation.NONE);
        BlockPos landing = BuildJob.worldPos(hut, hutPlan.size(), new BlockPos(10, 0, 2));

        try {
            stockFor(world, colony, hutPlan);
            BuildJob.advance(world, manager, colony.id(), hut.id(), 10_000);

            BlockPos gate = BuildJob.worldPos(hut, hutPlan.size(), new BlockPos(9, 1, 2));
            BlockPos inside = BuildJob.worldPos(hut, hutPlan.size(), new BlockPos(8, 1, 2));

            if (!(world.getBlockState(gate).getBlock() instanceof FenceGateBlock)) {
                context.throwGameTestException("В ограде рощи нет калитки, стоит "
                        + world.getBlockState(gate).getBlock());
            }

            world.setBlockState(landing, Blocks.DIRT.getDefaultState());

            Citizen woodsman = hireWithBody(world, colony, GatherJob.LUMBERJACK, landing.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(woodsman.entityUuid().orElseThrow());
            body.setOnGround(true);

            Path through = body.getNavigation().findPathTo(inside, 0);
            if (through == null || !through.reachesTarget()) {
                context.throwGameTestException("Лесоруб не может войти в свою рощу: "
                        + (through == null ? "пути нет вовсе" : "путь не доходит"));
            }
        } finally {
            world.setBlockState(landing, Blocks.AIR.getDefaultState());
            demolish(world, hut, hutPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- подпись над жителем ---

    /**
     * Над жителем видно имя и ремесло.
     * <p>
     * Просьба заказчика. Ремесло рядом с именем не украшение: игрок раздаёт
     * работу и хочет видеть, кто перед ним, не открывая пульта.
     * <p>
     * Проверяется ключ перевода, а не готовая строка: на сервере словаря
     * мода нет, и {@code getString} вернул бы сам ключ. Игрок увидит
     * подпись собранной у себя на клиенте — а собирать её не из чего,
     * если ключ не тот.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "labels")
    public void citizenWearsNameAndCraft(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(mason.entityUuid().orElseThrow());

            WorkTicker.decide(world, manager, colony, mason, Schedule.MORNING_WORK);

            if (!body.isCustomNameVisible()) {
                context.throwGameTestException("Подпись жителя не показывается");
            }
            Text label = body.getCustomName();
            if (label == null || !(label.getContent() instanceof TranslatableTextContent craft)
                    || !craft.getKey().equals("villagepax.citizen.label")) {
                context.throwGameTestException("В подписи нет ремесла: " + label);
            }

            // Ремесло отобрали — осталось одно имя, и оно настоящее.
            mason.setProfession(null);
            WorkTicker.decide(world, manager, colony, mason, Schedule.MORNING_WORK);

            Text plain = body.getCustomName();
            if (plain == null || !plain.getString().equals(mason.fullName())) {
                context.throwGameTestException("Житель без ремесла подписан не своим именем: "
                        + plain);
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- колония делает то, чего у неё нет ---

    /**
     * Ферму можно построить, не имея моркови.
     * <p>
     * Игрок сообщил: «они требуют пашни, а собрать их нельзя». Пашня
     * и правда бесплатна — у неё нет предмета, — а вот у морковной грядки
     * предмет есть: морковь. Ферма требовала со склада сорок семь морковок,
     * которых у новой колонии взяться негде. Построить ферму, чтобы
     * получить морковь, можно было только имея морковь.
     * <p>
     * Записанное решение заказчика гласит, что первый посев приходит
     * вместе с постройкой, — и теперь оно наконец выполняется.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "craft")
    public void farmCostsNoSeedToBuild(TestContext context) {
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);
        Map<Item, Integer> bill = Materials.required(farmPlan);

        if (bill.containsKey(Items.CARROT)) {
            context.throwGameTestException("Ферма всё ещё требует морковь: "
                    + bill.get(Items.CARROT) + " штук. Построить ферму, чтобы получить "
                    + "морковь, можно было бы только имея морковь");
        }
        for (Item asked : bill.keySet()) {
            if (asked == Items.AIR) {
                context.throwGameTestException("В заявке оказался воздух");
            }
        }

        // Но грядки на поле всё-таки есть: посев приходит со схемой,
        // а не отменяется вовсе.
        long crops = farmPlan.plan().steps().stream()
                .filter(BuildStep::placesBlock)
                .filter(step -> farmPlan.blockAt(step.paletteIndex()).getBlock()
                        instanceof CropBlock)
                .count();
        if (crops == 0) {
            context.throwGameTestException("На ферме не осталось ни одной грядки");
        }

        context.complete();
    }

    /**
     * Колония сама делает фахверк, доски, ступени и стёкла.
     * <p>
     * Просьба игрока. Без крафта схема дома требовала фахверк, ступени,
     * доски, кровати и стёкла, а колония не умела ничего из этого: всё
     * это игрок обязан был скрафтить руками и принести в сундук, иначе
     * стройка стояла. Деревня из шести домов превращалась в двести
     * походов к верстаку.
     * <p>
     * На склад завозится только <b>сырьё</b>: брёвна, глина, песок,
     * булыжник, стекло и шерсть. Ни одной доски, ни одной ступени, ни
     * одного фахверка. Дом обязан встать целиком.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "craft")
    public void colonyCraftsWhatTheHouseNeeds(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            Warehouse warehouse = Warehouse.of(world, colony);
            raw(world, warehouse, hall, Items.DARK_OAK_LOG, 128);
            raw(world, warehouse, hall, Items.COBBLESTONE, 64);
            raw(world, warehouse, hall, Items.CLAY_BALL, 64);
            raw(world, warehouse, hall, Items.SAND, 64);
            raw(world, warehouse, hall, Items.GLASS, 16);
            // Шерсть красная, а не белая: ванильной красной кровати нужна
            // именно красная шерсть, а перекрасить белую колония не может —
            // на это нужен краситель, а он растёт в поле, не на складе.
            raw(world, warehouse, hall, Items.RED_WOOL, 32);
            // Уголь на очаг: костёр колония сложит сама из брёвен, палок
            // и угля, но уголь ей взять негде — он в шахте.
            raw(world, warehouse, hall, Items.COAL, 16);

            // Проверка посылки: готового в завозе нет.
            Warehouse stocked = Warehouse.of(world, colony);
            for (Item ready : List.of(Items.DARK_OAK_PLANKS, Items.DARK_OAK_STAIRS,
                    Items.GLASS_PANE, Items.RED_BED, ModBlocks.TIMBER_FRAME.asItem())) {
                if (stocked.count(ready) > 0) {
                    context.throwGameTestException("Посылка теста не выполнена: на складе "
                            + "уже лежит готовое — " + ready);
                }
            }

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не встал из сырья: шаг " + site.nextStep()
                        + " из " + housePlan.plan().steps().size() + ", не хватает "
                        + Materials.shortfall(housePlan, site, 200));
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Завезти сырьё на склад стопками: ручной завоз в один вызов. */
    private static void raw(ServerWorld world, Warehouse warehouse, BlockPos where,
                            Item item, int count) {
        int left = count;
        while (left > 0) {
            int chunk = Math.min(left, item.getMaxCount());
            warehouse.addOrScatter(world, where, new ItemStack(item, chunk));
            left -= chunk;
        }
    }

    // --- носильщик со слотами ---

    /**
     * Далеко от склада: билдер сам до сундука не дотянется.
     * <p>
     * Двенадцать блоков, а не двадцать. Дело не в правиле — до сундука
     * не дотянуться уже с трёх, — а в том, что мир игровых тестов
     * <b>общий</b>, площадки стоят в нём сеткой, и стройка за двадцать
     * блоков от своей уезжала к соседям, в область, которая не всегда
     * загружена. Тело билдера, отправленное туда, переставало
     * существовать, стройка стояла на нуле, и падало это через раз —
     * причём падало у тех, кто рядом. Нашлось, когда добавление двух
     * новых проверок сдвинуло раскладку.
     */
    private static final BlockPos FAR_SITE = new BlockPos(12, 8, 0);

    /**
     * Один житель поднимает стройку без курьера.
     * <p>
     * С этого начался разговор: курьеру нужен дом, дому нужны материалы
     * у стройки, а материалы к далёкой стройке без курьера не попадают.
     * Замкнутый круг. Теперь первый житель делает всё сам — ходит на склад,
     * набирает слоты, возвращается и строит.
     * <p>
     * Склад намеренно дальше, чем вытянутая рука билдера: иначе он брал бы
     * из сундука не сходя с места, и проверять было бы нечего.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slots")
    public void loneBuilderFetchesMaterialsHimself(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(FAR_SITE);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            if (BuildJob.storageIsNearby(Warehouse.of(world, colony), site)) {
                context.throwGameTestException("Посылка теста не выполнена: склад оказался "
                        + "под боком, и носить ничего не надо");
            }
            if (!Hauling.nobodyElseWillCarry(colony)) {
                context.throwGameTestException("Посылка теста не выполнена: в колонии есть "
                        + "кому носить");
            }

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 600, Schedule.MORNING_WORK);

            if (!site.isOperational()) {
                context.throwGameTestException("Дом не достроился без курьера: шаг "
                        + site.nextStep() + " из " + housePlan.plan().steps().size()
                        + ", на площадке " + site.stock().contents()
                        + ", на складе " + Warehouse.of(world, colony).tally().contents()
                        + ", не хватает " + Materials.shortfall(housePlan, site, 64)
                        + ", фаза " + mason.jobState().phase().id());
            }

            // Материалы обязаны сойтись, и это не придирка к бухгалтерии.
            // Носильщик со слотами легко начинает печатать предметы: стоит
            // после разгрузки поработать с устаревшим состоянием, и груз
            // возвращается в руки, уже лежа на площадке. Так на площадке
            // и оказалось семнадцать сотен булыжника вместо двадцати пяти.
            // Здесь склад завезли ровно под схему, сносить в пустоте нечего,
            // значит после сдачи дома не должно остаться ничего.
            int leftOver = Warehouse.of(world, colony).totalItems();
            if (leftOver != 0) {
                context.throwGameTestException("После стройки на складе осталось " + leftOver
                        + " предметов, а завозили ровно под схему: значит, где-то "
                        + "размножились");
            }
            if (mason.jobState().isCarrying()) {
                context.throwGameTestException("Билдер остался с грузом в руках: "
                        + mason.jobState().carried());
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Появился курьер — билдер снова только строит.
     * <p>
     * Иначе решение заказчика «стройка под боком у склада идёт сама,
     * вынесенная за околицу требует людей» перестало бы что-либо значить:
     * география снова стала бы декорацией. Носить билдер берётся только
     * когда носить больше <b>некому</b>.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slots")
    public void builderLeavesHaulingToTheCourier(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(FAR_SITE);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            // Курьер в колонии есть — пусть даже он ещё не в загруженном чанке.
            Citizen porter = Citizen.newborn("Носильщик", "", NORMAN, Gender.MALE);
            porter.setProfession(HaulJob.COURIER);
            colony.addCitizen(porter);

            if (Hauling.nobodyElseWillCarry(colony)) {
                context.throwGameTestException("Курьера в колонии не заметили");
            }

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            // Меньше терпения: пока есть надежда на курьера, билдер ждёт
            // на месте. Что он вмешается, когда курьер не справится,
            // проверяет соседний тест.
            runWork(world, manager, colony, mason, Hauling.PATIENCE / 2, Schedule.MORNING_WORK);

            if (mason.jobState().isCarrying()) {
                context.throwGameTestException("Билдер сразу понёс материалы сам, хотя есть "
                        + "курьер и терпение не вышло: " + mason.jobState().carried());
            }
            if (mason.jobState().phase() == JobState.Phase.TO_STORAGE) {
                context.throwGameTestException("Билдер сразу пошёл на склад, хотя есть курьер");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Курьер не справляется — билдер вмешивается.
     * <p>
     * Решение заказчика: помогать, <b>если курьер не справляется</b>, а не
     * только когда его нет вовсе. Курьер может спать, застрять, не дойти
     * или не успевать — стройка стоять из-за этого не должна.
     * <p>
     * Здесь курьер есть, но телом в мире не появлялся: работать он не может
     * никак. Это самый чистый способ изобразить «не справляется», не
     * подпирая тест сном и заблудившимся путём.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slots")
    public void builderStepsInWhenTheCourierCannotCope(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(FAR_SITE);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            Citizen idle = Citizen.newborn("Лежебока", "", NORMAN, Gender.MALE);
            idle.setProfession(HaulJob.COURIER);
            colony.addCitizen(idle);

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 600, Schedule.MORNING_WORK);

            if (!site.isOperational()) {
                context.throwGameTestException("Стройка встала при неработающем курьере: шаг "
                        + site.nextStep() + " из " + housePlan.plan().steps().size()
                        + ", тел рядом " + world.getEntitiesByClass(CitizenEntity.class,
                                new Box(hall).expand(48), alive -> true).size());
            }
            int leftOver = Warehouse.of(world, colony).totalItems();
            if (leftOver != 0) {
                context.throwGameTestException("После стройки осталось " + leftOver
                        + " предметов, а завозили ровно под схему");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * За одну ходку носильщик берёт несколько видов груза.
     * <p>
     * Раньше слот был один, и дом из шестнадцати видов блоков требовал
     * шестнадцати походов через полдеревни — работа ради работы, которую
     * игрок и видел.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "slots")
    public void oneTripCarriesSeveralKinds(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(FAR_SITE);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);

            Citizen porter = hireWithBody(world, colony, HaulJob.COURIER, hall.up());

            int mostSlots = 0;
            for (int round = 0; round < 40; round++) {
                runWork(world, manager, colony, porter, 1, Schedule.MORNING_WORK);
                mostSlots = Math.max(mostSlots, porter.jobState().usedSlots());
            }

            if (mostSlots < 2) {
                context.throwGameTestException("Курьер за ходку взял видов груза: " + mostSlots
                        + ". Слотов у него " + Hauling.slots() + ", а дому нужно много разного");
            }
            if (mostSlots > Hauling.slots()) {
                context.throwGameTestException("Курьер унёс " + mostSlots
                        + " видов груза, а слотов у него " + Hauling.slots());
            }

            // И принесённое доходит до площадки, а не остаётся в руках.
            if (site.stock().isEmpty()) {
                context.throwGameTestException("На площадку ничего не донесли");
            }
        } finally {
            demolish(world, site, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- типы зданий из датапака ---

    /**
     * Права зданию даёт объявленный тип, а не его имя.
     * <p>
     * Самый старый долг мода, и вот чем он был опасен: ратушу узнавали
     * по тому, что путь типа кончается на {@code town_hall}. Здание
     * с именем {@code norman/town_hall_ruins} мод счёл бы ратушей
     * и позволил бы поднимать по нему уровень колонии — до второго уровня
     * ратуши, которой нет.
     * <p>
     * Теперь роль объявлена данными, а молчание датапака <b>не наделяет
     * здание правами</b>: неописанное здание строится и чинится, но
     * колонии уровня не даёт и мастерской никому не служит.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "orders")
    public void onlyDeclaredTypesGetRights(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        Identifier ruins = new Identifier("villagepax", "norman/town_hall_ruins");
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            // Объявленное узнаётся.
            if (!Levels.isTownHallType(TOWN_HALL_TYPE)) {
                context.throwGameTestException("Настоящая ратуша не узнана по данным");
            }
            if (!BuildingTypes.isHome(HOUSE_TYPE)) {
                context.throwGameTestException("Дом не объявлен жильём");
            }
            if (!BuildingTypes.employs(FARM_TYPE, FarmJob.FARMER)) {
                context.throwGameTestException("Ферма не объявлена мастерской фермера");
            }

            // А похожее имя — нет.
            if (Levels.isTownHallType(ruins)) {
                context.throwGameTestException("Здание " + ruins + " сочли ратушей по имени — "
                        + "это и был тот самый долг");
            }
            if (BuildingTypes.employs(ruins, FarmJob.FARMER)) {
                context.throwGameTestException("Необъявленное здание служит мастерской");
            }

            // И уровень колонии от него не растёт.
            colony.addBuilding(new Building(UUID.randomUUID(), ruins, 2, hall.add(20, 0, 20),
                    BlockRotation.NONE, BuildProgress.DONE, List.of()));
            Levels.refresh(colony);
            if (colony.level() != SettlementLevel.HAMLET) {
                context.throwGameTestException("Колония выросла от здания, которое ратушей "
                        + "не объявлено: " + colony.level().id());
            }

            // Имя у неописанного здания всё-таки есть: пустой строки
            // в интерфейсе быть не должно.
            if (!BuildingTypes.displayName(ruins)
                    .equals("villagepax.building.norman.town_hall_ruins")) {
                context.throwGameTestException("У неописанного здания нет запасного имени: "
                        + BuildingTypes.displayName(ruins));
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- очередь заказов ---

    /**
     * Билдер берётся за то, что игрок поставил вперёд.
     * <p>
     * Заказал три дома — решаешь, какой первым. Без очереди билдер брался
     * за первую размеченную стройку, и переставить порядок было нечем:
     * приходилось отменять заказы и размечать заново.
     * <p>
     * Проверяется на двух стройках, из которых <b>вторая</b> объявлена
     * важной: если бы очередь не работала, билдер взялся бы за первую.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "orders")
    public void builderTakesTheUrgentSiteFirst(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        Building first = plan(colony, context.getAbsolutePos(new BlockPos(0, 8, 0)),
                HOUSE_TYPE, BlockRotation.NONE);
        Building urgent = plan(colony, context.getAbsolutePos(new BlockPos(0, 8, 8)),
                HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, housePlan);
            stockFor(world, colony, housePlan);

            // Без приоритета очередь идёт по времени заказа.
            if (!colony.byPriority().get(0).id().equals(first.id())) {
                context.throwGameTestException("При равной важности порядок заказа не сохранён");
            }

            urgent.setPriority(5);
            if (!colony.byPriority().get(0).id().equals(urgent.id())) {
                context.throwGameTestException("Важная стройка не стала первой в очереди");
            }

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 4, Schedule.MORNING_WORK);

            UUID taken = mason.jobState().building().orElse(null);
            if (taken == null) {
                context.throwGameTestException("Билдер не взялся ни за что");
            }
            if (!taken.equals(urgent.id())) {
                context.throwGameTestException("Билдер взялся не за важную стройку: "
                        + (taken.equals(first.id()) ? "за первую по времени" : taken.toString()));
            }
        } finally {
            demolish(world, first, housePlan);
            demolish(world, urgent, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- список заказов ---

    /**
     * Каждое здание предлагается один раз, и ратуша не предлагается вовсе.
     * <p>
     * Игрок сообщил: «в ратуше двоятся здания». Причина была в списке
     * заказов: он собирался из <b>всех</b> схем культуры, а подпись
     * у уровней одна на тип — и «Дом норманнов» стоял в списке дважды,
     * за первый уровень и за второй. Заказать второй уровень к тому же
     * значило бы поставить дом, у которого не было первого: уровни растут
     * кнопкой «Улучшить».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "orders")
    public void orderListShowsEachBuildingOnce(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            List<Identifier> offers = TownHallView.of(world, colony).offers();

            if (offers.isEmpty()) {
                context.throwGameTestException("Заказать нечего вовсе");
            }

            Set<Identifier> types = new HashSet<>();
            for (Identifier schematic : offers) {
                if (BuildJob.levelOf(schematic).orElse(1) != 1) {
                    context.throwGameTestException("В заказах не первый уровень: " + schematic
                            + ". Уровни растут кнопкой «Улучшить», а не заказом с нуля");
                }

                Identifier type = BuildJob.buildingTypeOf(schematic).orElseThrow();
                if (Levels.isTownHallType(type)) {
                    context.throwGameTestException("Ратушу предлагают построить второй раз: "
                            + "она уже стоит с основания");
                }
                if (!types.add(type)) {
                    context.throwGameTestException("Тип " + type + " предложен дважды — "
                            + "именно это игрок и видел как двоящиеся здания");
                }
            }

            // И то, что предлагается, обязано быть построимо: схема есть.
            for (Identifier schematic : offers) {
                if (SchematicLoader.get(schematic).isEmpty()) {
                    context.throwGameTestException("Предложена схема, которой нет: " + schematic);
                }
            }
        } finally {
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
                    8, 3, false, false, Config.DEFAULT.carrySlots(), false));

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

    // --- задача 1.13: квесты и репутация ---

    private static final Identifier FOUNDING_1 = new Identifier("villagepax", "norman/founding_1");
    private static final Identifier FOUNDING_2 = new Identifier("villagepax", "norman/founding_2");
    private static final Identifier FOUNDING_3 = new Identifier("villagepax", "norman/founding_3");

    /**
     * Цепочка проходится по порядку и кончается.
     * <p>
     * Порядок задан не списком, а ссылками {@code next}: каждый квест
     * называет следующий. Проверяется именно проход по ссылкам, потому что
     * кольцевая или оборванная ссылка в датапаке — это игрок, у которого
     * цепочка не идёт дальше первого шага.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void questChainWalksInOrderAndEnds(TestContext context) {
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        UUID player = UUID.randomUUID();

        if (!QuestManager.get(FOUNDING_1).isPresent()) {
            context.throwGameTestException("Стартовая цепочка не загружена. Загружены: "
                    + QuestManager.ids());
            return;
        }

        if (!Quests.offered(village, player, Villages.ELDER).equals(Optional.of(FOUNDING_1))) {
            context.throwGameTestException("Первым предложен не первый квест: "
                    + Quests.offered(village, player, Villages.ELDER));
        }

        village.noteQuestDone(player, FOUNDING_1);
        if (!Quests.offered(village, player, Villages.ELDER).equals(Optional.of(FOUNDING_2))) {
            context.throwGameTestException("После первого квеста не предложен второй: "
                    + Quests.offered(village, player, Villages.ELDER));
        }

        village.noteQuestDone(player, FOUNDING_2);
        if (!Quests.offered(village, player, Villages.ELDER).equals(Optional.of(FOUNDING_3))) {
            context.throwGameTestException("После второго квеста не предложен третий: "
                    + Quests.offered(village, player, Villages.ELDER));
        }

        village.noteQuestDone(player, FOUNDING_3);
        if (Quests.offered(village, player, Villages.ELDER).isPresent()) {
            context.throwGameTestException("Цепочка не кончилась: предложено "
                    + Quests.offered(village, player, Villages.ELDER));
        }

        // Другому игроку та же деревня предлагает цепочку с начала.
        if (!Quests.offered(village, UUID.randomUUID(), Villages.ELDER)
                .equals(Optional.of(FOUNDING_1))) {
            context.throwGameTestException("Второму игроку цепочка не начинается заново");
        }

        manager.remove(village.id());
        context.complete();
    }

    /**
     * Цепочка кончается чертежом ратуши, и это вход в мод.
     * <p>
     * Решение дизайна: игрок находит деревню, выполняет цепочку, дорастает
     * до друга и получает чертёж. Крафт остался подстраховкой на неудачный
     * сид. Поэтому награда последнего квеста проверяется прямо: без неё
     * весь вход в мод обрывается, а понять это можно было бы только
     * доиграв цепочку до конца руками.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void foundingChainRewardsTheBlueprint(TestContext context) {
        Quest last = QuestManager.get(FOUNDING_3).orElse(null);
        if (last == null) {
            context.throwGameTestException("Последнего квеста цепочки нет. Загружены: "
                    + QuestManager.ids());
            return;
        }

        boolean givesBlueprint = last.rewards().stream()
                .anyMatch(reward -> reward instanceof Quest.Reward.Give give
                        && give.item() == ModItems.TOWN_HALL_BLUEPRINT);
        if (!givesBlueprint) {
            context.throwGameTestException("Цепочка не выдаёт чертёж ратуши: " + last.rewards());
        }

        int trust = 0;
        for (Identifier id : List.of(FOUNDING_1, FOUNDING_2, FOUNDING_3)) {
            Quest quest = QuestManager.get(id).orElseThrow();
            for (Quest.Reward reward : quest.rewards()) {
                if (reward instanceof Quest.Reward.Trust up) {
                    trust += up.amount();
                }
            }
            if (!quest.giver().equals(Villages.ELDER)) {
                context.throwGameTestException("Квест " + id + " выдаёт не старейшина: "
                        + quest.giver());
            }
            if (quest.objectives().isEmpty()) {
                context.throwGameTestException("У квеста " + id + " нет ни одной цели");
            }
        }

        if (Standing.of(trust).ordinal() < Standing.FRIEND.ordinal()) {
            context.throwGameTestException("Вся цепочка даёт доверия " + trust
                    + " — это " + Standing.of(trust).id() + ", а чертёж отдают другу");
        }

        context.complete();
    }

    /**
     * Снимок разговора показывает то, что игрок должен видеть.
     * <p>
     * Решение заказчика: чата было мало. Экран обязан говорить, сколько
     * доверия набрано, сколько до следующей ступени, что просят и сколько
     * из этого уже в сумке, — и включать кнопку только когда принесено всё.
     * <p>
     * Считает всё сервер, и проверяется тоже сервер: клиенту достаются
     * готовые числа, иначе правило «сколько считается принесённым» жило бы
     * в двух местах и разошлось бы.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void questScreenShowsWhatIsAskedAndWhatIsBrought(TestContext context) {
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);

        QuestView empty = QuestNet.viewOf(SettlementManager.get(context.getWorld()), village, player, hands,
                Villages.ELDER,
                Warehouse.of(context.getWorld(), village)).orElse(null);
        if (empty == null) {
            context.throwGameTestException("Разговор не собрался вовсе");
            return;
        }
        if (!empty.villageName().equals("Бовуар") || !empty.village().equals(village.id())) {
            context.throwGameTestException("Снимок не про эту деревню: " + empty.villageName());
        }
        if (empty.reputation() != 0 || empty.nextAt().isEmpty()) {
            context.throwGameTestException("У чужака доверие " + empty.reputation()
                    + ", а до следующей ступени " + empty.nextAt());
        }

        QuestView.Offer offer = empty.quest().orElse(null);
        if (offer == null) {
            context.throwGameTestException("Старейшина ничего не просит у чужака — "
                    + "тогда непонятно, что делать");
            return;
        }
        if (offer.objectives().isEmpty()) {
            context.throwGameTestException("В квесте нет требований");
        }
        if (offer.ready()) {
            context.throwGameTestException("Кнопка «Отдать» включена с пустыми руками");
        }

        QuestView.Need need = offer.objectives().get(0);
        if (need.have() != 0 || need.enough()) {
            context.throwGameTestException("С пустыми руками принесено " + need.have());
        }

        // Принесли ровно столько, сколько просят: кнопка обязана включиться.
        hands.addStack(new ItemStack(need.item(), need.need()));

        QuestView full = QuestNet.viewOf(SettlementManager.get(context.getWorld()), village, player, hands,
                Villages.ELDER,
                Warehouse.of(context.getWorld(), village)).orElseThrow();
        QuestView.Offer ready = full.quest().orElseThrow();
        if (!ready.ready()) {
            context.throwGameTestException("Принесено всё, а кнопка выключена");
        }
        if (!ready.objectives().get(0).enough()) {
            context.throwGameTestException("Требование не считается выполненным: "
                    + ready.objectives().get(0).have() + " из "
                    + ready.objectives().get(0).need());
        }
        if (ready.rewards().isEmpty()) {
            context.throwGameTestException("Награда не показана — за что тогда нести");
        }

        context.complete();
    }

    /**
     * Полный проход входа в мод: цепочка сдаётся и кончается чертежом.
     * <p>
     * <b>Приёмочный тест всей версии 0.1</b> в той части, которую можно
     * проверить без игрока: найти деревню, сдать три квеста, получить
     * чертёж ратуши. Игрока в игровом тесте нет, поэтому вместо его рук —
     * обычный склад, а вместо инвентаря награды — список. Ровно теми же
     * вызовами работает щелчок по старейшине: {@code talk} — тонкая
     * обёртка над {@code handIn}.
     * <p>
     * Проверяется и то, что <b>не</b> отдают: принёс мало — не забрали
     * ничего. Отобранная половина даром была бы хуже отказа.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void wholeFoundingChainPaysOutTheBlueprint(TestContext context) {
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        UUID player = UUID.randomUUID();

        SimpleInventory hands = new SimpleInventory(36);
        List<ItemStack> paid = new ArrayList<>();

        // Пустые руки: сдавать нечего, но и отбирать нечего.
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.NOT_ENOUGH) {
            context.throwGameTestException("С пустыми руками квест приняли");
        }
        if (!village.questsDone(player).isEmpty() || village.reputationOf(player) != 0) {
            context.throwGameTestException("Отказ всё-таки что-то изменил");
        }

        // Принёс меньше нужного — тоже отказ, и брёвна остались при игроке.
        hands.addStack(new ItemStack(Items.OAK_LOG, 15));
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.NOT_ENOUGH) {
            context.throwGameTestException("Приняли пятнадцать брёвен вместо шестнадцати");
        }
        if (hands.count(Items.OAK_LOG) != 15) {
            context.throwGameTestException("У игрока отобрали недостающее: осталось "
                    + hands.count(Items.OAK_LOG));
        }

        // Донёс шестнадцатое — приняли ровно шестнадцать.
        hands.addStack(new ItemStack(Items.OAK_LOG, 1));
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.DONE) {
            context.throwGameTestException("Шестнадцать брёвен не приняли");
        }
        if (hands.count(Items.OAK_LOG) != 0) {
            context.throwGameTestException("Забрали не всё нужное или лишнее: осталось "
                    + hands.count(Items.OAK_LOG));
        }
        if (village.reputationOf(player) != 15) {
            context.throwGameTestException("Доверие после первого квеста "
                    + village.reputationOf(player) + ", а обещали пятнадцать");
        }

        // Второй и третий шаги цепочки.
        hands.addStack(new ItemStack(Items.COBBLESTONE, 24));
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.DONE) {
            context.throwGameTestException("Второй квест не приняли");
        }

        hands.addStack(new ItemStack(Items.BREAD, 8));
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.DONE) {
            context.throwGameTestException("Третий квест не приняли: доверия "
                    + village.reputationOf(player));
        }

        // Чертёж выдан, и деревня считает игрока другом.
        boolean gotBlueprint = paid.stream()
                .anyMatch(stack -> stack.isOf(ModItems.TOWN_HALL_BLUEPRINT));
        if (!gotBlueprint) {
            context.throwGameTestException("Чертёж ратуши не выдан. Выдано: " + paid);
        }
        if (village.standingOf(player).ordinal() < Standing.FRIEND.ordinal()) {
            context.throwGameTestException("После всей цепочки игрок всё ещё "
                    + village.standingOf(player).id());
        }

        // Цепочка кончилась: просить больше нечего.
        if (Quests.handIn(village, player, Villages.ELDER, hands, paid::add)
                != Quests.Handover.NOTHING_OFFERED) {
            context.throwGameTestException("Деревня просит что-то после конца цепочки");
        }

        context.complete();
    }

    /**
     * В деревне есть с кем заговорить.
     * <p>
     * Старейшина ставится явно при основании, а не через приоритет найма:
     * без неё деревня — набор домов, в котором игроку нечего делать.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void villageHasAnElderToTalkTo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала: место занято="
                        + manager.isSettled(centre) + ", помеха=" + whoBlocks(manager, centre));
                return;
            }

            Citizen elder = village.citizens().stream()
                    .filter(citizen -> citizen.profession().filter(Villages.ELDER::equals).isPresent())
                    .findFirst().orElse(null);
            if (elder == null) {
                context.throwGameTestException("В деревне нет старейшины: "
                        + village.citizens().stream().map(citizen -> citizen.profession()
                                .map(Identifier::toString).orElse("без дела")).toList());
                return;
            }

            // И ей есть что предложить пришедшему.
            if (Quests.offered(village, UUID.randomUUID(), Villages.ELDER).isEmpty()) {
                context.throwGameTestException("Старейшине нечего предложить игроку");
            }

            // Приток жителей старейшину не назначает: в колонии игрока
            // выдавать квесты некому.
            Settlement colony = colonyWithBuilder(world, manager,
                    context.getAbsolutePos(new BlockPos(0, 40, 0)));
            try {
                for (int newcomer = 0; newcomer < 4; newcomer++) {
                    Housing.welcomeNewcomer(world, colony, new java.util.Random(newcomer));
                }
                boolean hiredElder = colony.citizens().stream()
                        .anyMatch(citizen -> citizen.profession()
                                .filter(Villages.ELDER::equals).isPresent());
                if (hiredElder) {
                    context.throwGameTestException("Колония игрока наняла старейшину сама собой");
                }
            } finally {
                discardBodies(world, colony);
                manager.remove(colony.id());
                world.setBlockState(context.getAbsolutePos(new BlockPos(0, 40, 0)),
                        Blocks.AIR.getDefaultState());
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    // --- задача 1.12: деревни народов ---

    /**
     * Деревня встаёт уже стоящей и сразу берётся за следующее здание.
     * <p>
     * Приёмка задачи: в мире находится норманнская деревня с жителями,
     * которые работают. «Уже стоящей» — потому что деревня старше игрока:
     * ратуша, дом и ферма ставятся мгновенно. А следующее здание она
     * начинает при игроке, и это важнее готовых стен: видно не декорацию,
     * а работу.
     * <p>
     * В своей партии намеренно: деревне нужна ровная площадка в двадцать
     * блоков в каждую сторону, и с соседом по партии они наступили бы друг
     * другу на застройку.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "village")
    public void villageRisesAlreadyStandingAndKeepsBuilding(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала на ровном лугу: место занято="
                        + manager.isSettled(centre) + ", помеха=" + whoBlocks(manager, centre));
                return;
            }

            if (!village.owner().isAutonomous()) {
                context.throwGameTestException("Деревня оказалась чьей-то колонией");
            }
            if (village.name().isEmpty()) {
                context.throwGameTestException("У деревни нет имени из списка народа");
            }
            if (village.population() < 2) {
                context.throwGameTestException("Жителей в деревне " + village.population()
                        + ", а одиночка — это не деревня");
            }

            long done = village.buildings().stream().filter(Building::isOperational).count();
            long building = village.buildings().stream()
                    .filter(BuildJob::isUnderConstruction).count();

            // Ратуша, дом и ферма готовы — это три; четвёртое строится.
            if (done < 3) {
                context.throwGameTestException("Готовых зданий " + done
                        + ", а деревня должна найтись стоящей: ратуша, дом и ферма");
            }
            if (building != 1) {
                context.throwGameTestException("Строящихся зданий " + building
                        + ", а деревня обязана браться ровно за одно");
            }

            // Дом настоящий: у жителей есть где спать.
            if (Housing.sleepingSpots(world, village).isEmpty()) {
                context.throwGameTestException("В деревне нет ни одного места для сна");
            }

            // Второй раз на том же месте деревня не возникает.
            if (!manager.isSettled(centre)) {
                context.throwGameTestException("Место деревни не запомнилось");
            }
            if (Villages.found(world, NORMAN, centre).isPresent()) {
                context.throwGameTestException("Деревня встала на том же месте второй раз");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Суточное решение деревни: обоз двигает стройку.
     * <p>
     * Деревня не умеет ни выплавить стекло, ни соткать кровать, и без обоза
     * её стройка встала бы навсегда на первом же окне. Проверяется, что за
     * день стройка <b>продвинулась</b>, а не что склад наполнился:
     * наполнение без продвижения означало бы, что материал привозят не тот.
     * <p>
     * Заодно проверяется, что обоз работает <b>на дневную выручку</b>:
     * деревня начинает без монеты, зарабатывает её сама и на неё же
     * покупает. Прежде материалы появлялись даром.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "village")
    public void villageGrowsFromDayToDay(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала: место занято="
                        + manager.isSettled(centre) + ", помеха=" + whoBlocks(manager, centre));
                return;
            }

            Building site = village.buildings().stream()
                    .filter(BuildJob::isUnderConstruction).findFirst().orElse(null);
            if (site == null) {
                context.throwGameTestException("Деревня ничего не строит, двигать нечего");
                return;
            }

            int before = site.nextStep();
            for (int day = 0; day < 3; day++) {
                Villages.newDay(world, manager, village);
                BuildJob.advance(world, manager, village.id(), site.id(), 2_000);
            }

            if (site.nextStep() <= before) {
                context.throwGameTestException("За три дня стройка не продвинулась: шаг "
                        + site.nextStep() + ", был " + before);
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Команда поиска отвечает, и отвечает одно и то же.
     * <p>
     * Без неё мод буквально нельзя найти: места деревень стоят в семи
     * с половиной сотнях блоков друг от друга, и игрок, не знающий, куда
     * идти, решит, что мод не работает. Догадка нарочно <b>не смотрит
     * в мир</b>: проверить биом и грунт нельзя, не сгенерировав чанк,
     * а генерировать полкарты ради ответа недопустимо.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "village")
    public void locateAlwaysAnswersTheSameWay(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos from = context.getAbsolutePos(BlockPos.ORIGIN);

        VillageSites.Guess first = VillageSites.guessNearest(world, from);

        // Пусто — законный ответ, и это главное в проверке. Прежняя версия
        // всегда называла середину клетки, ничего не проверяя, и уводила
        // игрока за семь сотен блоков в пустоту. Теперь ответ «рядом нет
        // подходящего биома» честен: в мире игровых тестов лесов и нет.
        if (!java.util.Objects.equals(first, VillageSites.guessNearest(world, from))) {
            context.throwGameTestException("Второй раз поиск ответил иначе: было " + first
                    + ", стало " + VillageSites.guessNearest(world, from));
        }

        if (first != null) {
            if (!first.culture().equals(NORMAN)) {
                context.throwGameTestException("Названа не та культура: " + first.culture());
            }

            // Названное место обязано проходить ту же проверку, которой
            // деревня возникает: иначе поиск снова уводил бы в пустоту.
            Culture norman = CultureManager.get(NORMAN);
            int spacing = VillageSites.spacing(norman);
            int cellX = Math.floorDiv(new ChunkPos(first.where()).x, spacing);
            int cellZ = Math.floorDiv(new ChunkPos(first.where()).z, spacing);

            BlockPos checked = VillageSites.plannedSite(world, NORMAN, norman, cellX, cellZ);
            if (!first.where().equals(checked)) {
                context.throwGameTestException("Поиск назвал " + first.where().toShortString()
                        + ", а проверка того же места даёт " + checked);
            }
        }

        context.complete();
    }

    /**
     * Место деревни выводится из семени мира и потому одно и то же.
     * <p>
     * Не мелочь: если бы место выбиралось случайно при каждом обращении,
     * деревня возникала бы у игрока то тут, то там, а на сервере два игрока
     * получили бы две разные деревни в одной клетке.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "village")
    public void villageSitesAreTheSameEveryTime(TestContext context) {
        ServerWorld world = context.getWorld();
        Culture norman = CultureManager.get(NORMAN);
        if (norman == null) {
            context.throwGameTestException("Культура норманнов не загружена");
            return;
        }

        int cellX = new ChunkPos(context.getAbsolutePos(BlockPos.ORIGIN)).x
                / VillageSites.spacing(norman);
        int cellZ = new ChunkPos(context.getAbsolutePos(BlockPos.ORIGIN)).z
                / VillageSites.spacing(norman);

        Optional<BlockPos> first = VillageSites.candidate(world, NORMAN, norman, cellX, cellZ);
        Optional<BlockPos> again = VillageSites.candidate(world, NORMAN, norman, cellX, cellZ);

        if (!first.equals(again)) {
            context.throwGameTestException("Место деревни поменялось между двумя вопросами: "
                    + first + " и " + again);
        }

        // Соседняя клетка обязана дать другое место, иначе сетка не работает.
        Optional<BlockPos> neighbour =
                VillageSites.candidate(world, NORMAN, norman, cellX + 1, cellZ);
        if (first.isPresent() && first.equals(neighbour)) {
            context.throwGameTestException("Две клетки сетки дали одно место");
        }

        context.complete();
    }

    // --- фаза 0.2: монета и торг вместо заглушки «привоз со стороны» ---

    /**
     * Торг двигает и товар, и монету — в обе стороны.
     * <p>
     * Проверяется целиком, потому что <b>сделка обязана быть «всё или
     * ничего»</b>: половина сделки — это либо товар из ниоткуда, либо
     * монеты в никуда. Считается по обеим сторонам сразу: что убыло у
     * игрока, что прибыло на складе, и наоборот.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void tradeMovesGoodsAndCoinBothWays(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);
        // Сдача, которой не нашлось места, — признак ошибки: в тесте
        // инвентарь заведомо просторен, и список обязан остаться пустым.
        List<ItemStack> spilled = new ArrayList<>();

        try {
            TradeTable.Deal bread = Trading.find(colony, Trading.Side.VILLAGE_SELLS,
                    Items.BREAD, 6).orElse(null);
            if (bread == null) {
                context.throwGameTestException("Норманны не продают хлеб — "
                        + "стол торга не загрузился");
                return;
            }

            TradeTable.Deal glass = Trading.find(colony, Trading.Side.VILLAGE_BUYS,
                    Items.GLASS_PANE, 8).orElseThrow();

            // У деревни хлеб, у игрока монета и стекло. Кошель пуст.
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 12));
            hands.addStack(new ItemStack(ModItems.COIN, 3));
            hands.addStack(new ItemStack(Items.GLASS_PANE, 8));

            // Деревня без монеты не покупает. Отказ — это содержание, не сбой.
            Trading.Outcome poor = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_BUYS, glass,
                    spilled::add);
            if (poor != Trading.Outcome.NO_COIN) {
                context.throwGameTestException("Деревня без монеты купила стекло: " + poor);
            }
            if (hands.count(Items.GLASS_PANE) != 8 || Coins.total(hands) != 3) {
                context.throwGameTestException("Отказ тронул сумку: стекла "
                        + hands.count(Items.GLASS_PANE) + ", монет " + Coins.total(hands));
            }

            // Деревня продаёт. Чужак платит полторы цены: хлеб по цене
            // датапака стоит один изумруд, а с него берут два.
            Trading.Outcome bought = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_SELLS, bread,
                    spilled::add);
            if (bought != Trading.Outcome.DONE) {
                context.throwGameTestException("Покупка хлеба отказана: " + bought);
            }
            if (hands.count(Items.BREAD) != 6 || Coins.total(hands) != 1) {
                context.throwGameTestException("У игрока после покупки хлеба "
                        + hands.count(Items.BREAD) + " хлеба и " + Coins.total(hands)
                        + " монет, ожидалось 6 и 1");
            }
            Warehouse after = Warehouse.of(world, colony);
            if (after.count(Items.BREAD) != 6 || Coins.total(after.coins()) != 2) {
                context.throwGameTestException("На складе после продажи хлеба "
                        + after.count(Items.BREAD) + " хлеба и " + Coins.total(after.coins())
                        + " монет, ожидалось 6 и 2");
            }

            // Теперь кошель не пуст, и стекло деревня берёт. Чужаку платят
            // половину: цена датапака — две монеты, чужаку одна.
            Trading.Outcome sold = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_BUYS, glass,
                    spilled::add);
            if (sold != Trading.Outcome.DONE) {
                context.throwGameTestException("Продажа стекла отказана: " + sold);
            }
            if (hands.count(Items.GLASS_PANE) != 0 || Coins.total(hands) != 2) {
                context.throwGameTestException("У игрока после продажи стекла "
                        + hands.count(Items.GLASS_PANE) + " стекла и " + Coins.total(hands)
                        + " монет, ожидалось 0 и 2");
            }
            Warehouse paid = Warehouse.of(world, colony);
            if (paid.count(Items.GLASS_PANE) != 8 || Coins.total(paid.coins()) != 1) {
                context.throwGameTestException("На складе после покупки стекла "
                        + paid.count(Items.GLASS_PANE) + " стекла и " + Coins.total(paid.coins())
                        + " монет, ожидалось 8 и 1");
            }

            if (!spilled.isEmpty()) {
                context.throwGameTestException("Сдача не влезла в просторный инвентарь: "
                        + spilled);
            }

            // Две удачные сделки — два очка знакомства. Торговлей тебя
            // запоминают в лицо, и это единственное, что она даёт.
            if (colony.reputationOf(player) != 2) {
                context.throwGameTestException("Доверие за две сделки: "
                        + colony.reputationOf(player) + ", ожидалось 2");
            }
        } finally {
            Warehouse.of(world, colony).take(Items.BREAD, 64);
            Warehouse.of(world, colony).take(Items.GLASS_PANE, 64);
            Coins.pay(Warehouse.of(world, colony).coins(),
                    Coins.total(Warehouse.of(world, colony).coins()));
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Доверие решает, что вообще выложат на прилавок, — и торгом его выше
     * знакомства не поднять.
     * <p>
     * Оба правила проверяются вместе, потому что вместе они и работают:
     * колокол норманны отдают только другу, а другом за покупки не
     * становятся. Без верхней черты чертёж ратуши — награда за цепочку
     * квестов — покупался бы хлебом в двести приёмов, и вход в мод
     * превратился бы в перекладывание стопок.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void trustGatesTheGoodsAndTradeStopsAtAcquaintance(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);
        List<ItemStack> spilled = new ArrayList<>();

        try {
            TradeTable.Deal bell = Trading.find(colony, Trading.Side.VILLAGE_SELLS,
                    Items.BELL, 1).orElse(null);
            if (bell == null) {
                context.throwGameTestException("Норманны не продают колокол — "
                        + "стол торга не загрузился");
                return;
            }

            Warehouse.of(world, colony).add(new ItemStack(Items.BELL, 1));
            hands.addStack(new ItemStack(ModItems.COIN, 48));

            // Чужаку колокол не продают, даже когда монета при нём.
            Trading.Outcome stranger = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_SELLS, bell,
                    spilled::add);
            if (stranger != Trading.Outcome.NO_TRUST) {
                context.throwGameTestException("Колокол продан чужаку: " + stranger);
            }

            // На один шаг ниже порога — по-прежнему нет.
            colony.addReputation(player, bell.minReputation() - 1);
            if (Trading.trade(colony, player, hands, Warehouse.of(world, colony),
                    Trading.Side.VILLAGE_SELLS, bell, spilled::add)
                    != Trading.Outcome.NO_TRUST) {
                context.throwGameTestException("Порог доверия сдвинут: "
                        + colony.reputationOf(player) + " хватило при пороге "
                        + bell.minReputation());
            }

            colony.addReputation(player, 1);
            int trusted = colony.reputationOf(player);
            Trading.Outcome friend = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_SELLS, bell,
                    spilled::add);
            if (friend != Trading.Outcome.DONE) {
                context.throwGameTestException("Другу колокол не продали: " + friend);
            }
            if (hands.count(Items.BELL) != 1 || Coins.total(hands) != 24) {
                context.throwGameTestException("После покупки колокола у игрока "
                        + hands.count(Items.BELL) + " колоколов и " + Coins.total(hands)
                        + " монет, ожидалось 1 и 24");
            }
            if (colony.reputationOf(player) != trusted) {
                context.throwGameTestException("Торг поднял доверие выше знакомства: было "
                        + trusted + ", стало " + colony.reputationOf(player));
            }
        } finally {
            Warehouse.of(world, colony).take(Items.BELL, 8);
            Coins.pay(Warehouse.of(world, colony).coins(),
                    Coins.total(Warehouse.of(world, colony).coins()));
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Обоз возит за деньги, а не даром.
     * <p>
     * Это и есть замена <b>заглушки</b>, которая жила в моде до сих пор:
     * материалы появлялись на складе деревни из ниоткуда. Проверяется
     * прямо: деревня без монеты не получает ничего, та же деревня с
     * монетой — получает. Если однажды привоз снова станет подарком,
     * первая половина теста упадёт.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void caravanBringsNothingWithoutCoin(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            // Выручка выключена: иначе деревня заработает монету в том же
            // дне и проверка «без монеты» проверяла бы не то.
            Configs.override(new Config(true, 0, 1.0,
                    Config.DEFAULT.hungerWarnDays(), Config.DEFAULT.hungerLeaveDays(),
                    Config.DEFAULT.villageTradePerDay(), 0,
                    Config.DEFAULT.roadReserve(), Config.DEFAULT.ticksPerDecision(),
                    true, true, Config.DEFAULT.carrySlots(), true));

            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала: помеха="
                        + whoBlocks(manager, centre));
                return;
            }
            if (village.buildings().stream().noneMatch(BuildJob::isUnderConstruction)) {
                context.throwGameTestException("Деревня ничего не строит — возить нечего");
                return;
            }

            // Никаких колоний поблизости: суточная смена деревни — это
            // не только привоз, но и обоз, а обоз УВОЗИТ товар, и счёт
            // предметов уходит в минус. Появилось это, когда деревни стали
            // встречать игрока полным прилавком: возить стало что, и
            // проверка начала мигать через раз, потому что день обоза
            // зависит от опознавателя деревни.
            for (Settlement colony : List.copyOf(manager.all())) {
                if (!colony.owner().isAutonomous()) {
                    manager.remove(colony.id());
                }
            }

            Warehouse warehouse = Warehouse.of(world, village);
            // Опустошить кошель целиком: монета бывает трёх достоинств,
            // и вычитать её штуками одного вида было бы неверно.
            Coins.pay(warehouse.coins(), Coins.total(warehouse.coins()));

            int beggarly = Warehouse.of(world, village).totalItems();
            Villages.newDay(world, manager, village);
            int afterEmptyDay = Warehouse.of(world, village).totalItems();
            if (afterEmptyDay != beggarly) {
                context.throwGameTestException("Без монеты обоз всё равно привёз "
                        + (afterEmptyDay - beggarly) + " предметов — привоз снова даровой");
            }

            // А с монетой — привозит, и монета убывает.
            Warehouse.of(world, village).add(new ItemStack(ModItems.COIN, 32));
            int withPurse = Warehouse.of(world, village).totalItems();
            Villages.newDay(world, manager, village);

            Warehouse rich = Warehouse.of(world, village);
            if (rich.totalItems() <= withPurse) {
                context.throwGameTestException("С монетой обоз ничего не привёз: было "
                        + withPurse + ", стало " + rich.totalItems());
            }
            if (Coins.total(rich.coins()) >= 32) {
                context.throwGameTestException("Обоз привёз бесплатно: монеты осталось "
                        + Coins.total(rich.coins()) + " из 32");
            }

            // Округление цены: вверх при покупке, вниз при подсчёте, сколько
            // по карману. Наоборот — и деревня возила бы себе даром.
            TradeTable.Deal rate = new TradeTable.Deal(Items.OAK_PLANKS, 4, 1, 0);
            if (Trading.costOf(rate, 5) != 2 || Trading.costOf(rate, 4) != 1) {
                context.throwGameTestException("Цена пяти штук по «1 за 4»: "
                        + Trading.costOf(rate, 5) + ", ожидалось 2");
            }
            if (Trading.affordable(rate, 3) != 12 || Trading.affordable(rate, 0) != 0) {
                context.throwGameTestException("На три монеты по «1 за 4» доступно "
                        + Trading.affordable(rate, 3) + ", ожидалось 12");
            }
        } finally {
            Configs.override(Config.DEFAULT);
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Прилавок доезжает до экрана — вместе с причиной, почему нельзя.
     * <p>
     * Причина важнее самого прилавка. Серая кнопка без объяснения — это
     * загадка: игрок не знает, идти ему за монетой, за доверием или просто
     * прийти назавтра. Порядок причин тоже проверяется: недоверие
     * называется <b>раньше</b> безденежья, иначе игрок пойдёт искать
     * изумруды под товар, который ему всё равно не продадут.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void stallsReachTheScreenWithTheReasonWhyNot(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);

        try {
            QuestView view = QuestNet.viewOf(manager, colony, player, hands, Villages.ELDER,
                    Warehouse.of(world, colony)).orElseThrow();
            if (!view.trades() || view.stalls().isEmpty()) {
                context.throwGameTestException("Прилавок не доехал до экрана");
                return;
            }
            if (view.purse() != 0) {
                context.throwGameTestException("Кошель пустой деревни: " + view.purse());
            }

            // Пустой склад: продавать нечего, покупать не на что. А заодно
            // проверяется, что сервер находит у себя <b>каждую</b> сделку,
            // которую сам же показал: клиент присылает её назад именно так.
            for (QuestView.Stall stall : view.stalls()) {
                Trading.Side side = stall.villageSells()
                        ? Trading.Side.VILLAGE_SELLS : Trading.Side.VILLAGE_BUYS;
                TradeTable.Deal named = Trading.find(colony, side, stall.item(),
                        stall.count()).orElse(null);
                if (named == null) {
                    context.throwGameTestException("Показанная сделка не находится обратно: "
                            + Registries.ITEM.getId(stall.item()));
                    return;
                }

                QuestView.Ready expected = named.minReputation() > 0
                        ? QuestView.Ready.NO_TRUST : QuestView.Ready.VILLAGE_CANT;
                if (stall.ready() != expected) {
                    context.throwGameTestException("У пустой деревни сделка "
                            + Registries.ITEM.getId(stall.item()) + " в состоянии "
                            + stall.ready() + ", ожидалось " + expected);
                }
                if (stall.ready().reasonKey().isEmpty()) {
                    context.throwGameTestException("Отказ без объяснения: "
                            + Registries.ITEM.getId(stall.item()));
                }
            }

            // Товар на складе есть, монеты у игрока нет: теперь дело в нём.
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 12));
            QuestView stocked = QuestNet.viewOf(manager, colony, player, hands, Villages.ELDER,
                    Warehouse.of(world, colony)).orElseThrow();
            QuestView.Stall bread = stocked.stalls().stream()
                    .filter(stall -> stall.villageSells() && stall.item() == Items.BREAD)
                    .findFirst().orElseThrow();
            if (bread.ready() != QuestView.Ready.PLAYER_CANT) {
                context.throwGameTestException("Хлеб на складе, монеты нет, а причина: "
                        + bread.ready());
            }

            hands.addStack(new ItemStack(ModItems.COIN, 4));
            QuestView ready = QuestNet.viewOf(manager, colony, player, hands, Villages.ELDER,
                    Warehouse.of(world, colony)).orElseThrow();
            QuestView.Stall now = ready.stalls().stream()
                    .filter(stall -> stall.villageSells() && stall.item() == Items.BREAD)
                    .findFirst().orElseThrow();
            if (now.ready() != QuestView.Ready.YES || now.ready().reasonKey().isPresent()) {
                context.throwGameTestException("Сделка сходится, а кнопка не включена: "
                        + now.ready());
            }
            if (ready.purse() != 0) {
                context.throwGameTestException("Кошель деревни изменился без сделки: "
                        + ready.purse());
            }

            // Колокол по-прежнему заперт доверием, хотя монета уже при игроке:
            // недоверие называется раньше безденежья.
            hands.addStack(new ItemStack(ModItems.COIN, 48));
            QuestView rich = QuestNet.viewOf(manager, colony, player, hands, Villages.ELDER,
                    Warehouse.of(world, colony)).orElseThrow();
            QuestView.Stall bell = rich.stalls().stream()
                    .filter(stall -> stall.item() == Items.BELL)
                    .findFirst().orElseThrow();
            if (bell.ready() != QuestView.Ready.NO_TRUST) {
                context.throwGameTestException("Колокол чужаку показан доступным: "
                        + bell.ready());
            }
        } finally {
            Warehouse.of(world, colony).take(Items.BREAD, 64);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Цена зависит от доверия: другу продают дешевле, а покупают у него
     * дороже.
     * <p>
     * Решение плана, и оно важнее, чем кажется: без него доверие остаётся
     * строкой в экране и порогом у пары товаров, а с ним — тем, что видно
     * кошельком на каждой сделке. Цена в датапаке — цена <b>для друга</b>.
     * <p>
     * Проверяется вся лестница целиком, а не пара чисел: ступень, добавленную
     * когда-нибудь без цены, поймает именно этот обход.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade")
    public void pricesFollowStanding(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            TradeTable.Deal bell = Trading.find(colony, Trading.Side.VILLAGE_SELLS,
                    Items.BELL, 1).orElse(null);
            TradeTable.Deal shelf = Trading.find(colony, Trading.Side.VILLAGE_BUYS,
                    Items.BOOKSHELF, 2).orElse(null);
            if (bell == null || shelf == null) {
                context.throwGameTestException("Стол торга норманнов не загрузился");
                return;
            }

            // Округление названо числами: чужак платит полторы цены
            // колокола, почётный житель — три четверти, и обе округлены
            // против него.
            int forStranger = Trading.priceFor(bell, Trading.Side.VILLAGE_SELLS, 0);
            int forFriend = Trading.priceFor(bell, Trading.Side.VILLAGE_SELLS,
                    Standing.FRIEND.from());
            int forHonoured = Trading.priceFor(bell, Trading.Side.VILLAGE_SELLS,
                    Standing.HONOURED.from());
            if (forStranger != 36 || forFriend != 24 || forHonoured != 18) {
                context.throwGameTestException("Колокол по цене 24 обходится в "
                        + forStranger + " чужаку, " + forFriend + " другу и "
                        + forHonoured + " почётному; ожидалось 36, 24 и 18");
            }
            if (forFriend != bell.price()) {
                context.throwGameTestException("Цена датапака — не цена для друга: "
                        + bell.price() + " против " + forFriend);
            }

            // Лестница целиком: чем больше доверия, тем дешевле покупка
            // и тем дороже продажа. Ни одна цена не ноль — бесплатный
            // товар это уже не торговля.
            int cheapest = Integer.MAX_VALUE;
            int dearest = 0;
            for (Standing standing : Standing.values()) {
                int pay = Trading.priceFor(bell, Trading.Side.VILLAGE_SELLS, standing.from());
                int get = Trading.priceFor(shelf, Trading.Side.VILLAGE_BUYS, standing.from());

                if (pay > cheapest) {
                    context.throwGameTestException("У ступени " + standing.id()
                            + " покупка дороже, чем у предыдущей: " + pay + " против " + cheapest);
                }
                if (get < dearest) {
                    context.throwGameTestException("У ступени " + standing.id()
                            + " продажа дешевле, чем у предыдущей: " + get + " против " + dearest);
                }
                if (pay < 1 || get < 1) {
                    context.throwGameTestException("Даром: ступень " + standing.id()
                            + " платит " + pay + ", получает " + get);
                }
                cheapest = pay;
                dearest = get;
            }

            // Обоз при этом торгует по цене датапака: у телеги нет доверия.
            if (Trading.rate(colony, Items.GLASS_PANE).price() != 2) {
                context.throwGameTestException("Обоз берёт за стекло не цену датапака: "
                        + Trading.rate(colony, Items.GLASS_PANE).price());
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- фаза 0.2: черты народа ---

    /**
     * Черта народа разбирается громко: описку не принимают за отсутствие.
     * <p>
     * Черта — это <b>включатель кода</b>, а не число. Народ без своей черты
     * перестаёт быть собой, и лишняя буква в json не должна означать
     * «черты нет»: искать причину игрок будет в поведении жителей, а не
     * в датапаке. Проверяется и чужое пространство имён — {@code othermod:}
     * молча считать своим нельзя.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "traits")
    public void unknownTraitsAreNamedOutLoud(TestContext context) {
        if (!Traits.has(NORMAN, Trait.STONE_MASONRY)) {
            context.throwGameTestException("У норманнов нет объявленного каменного дела: "
                    + Traits.of(NORMAN));
        }

        Identifier typo = new Identifier("villagepax", "stone_masonery");
        Identifier alien = new Identifier("othermod", "stone_masonry");
        List<Identifier> declared = List.of(typo, alien,
                new Identifier("villagepax", Trait.STONE_MASONRY.id()));

        if (!Traits.resolve(declared).equals(java.util.Set.of(Trait.STONE_MASONRY))) {
            context.throwGameTestException("Из трёх строк узнана не одна черта: "
                    + Traits.resolve(declared));
        }
        List<Identifier> strangers = Traits.unknown(declared);
        if (strangers.size() != 2 || !strangers.contains(typo) || !strangers.contains(alien)) {
            context.throwGameTestException("Непонятые черты не названы: " + strangers);
        }

        // И темп: камень быстрее только у того, у кого есть черта.
        Identifier nobody = new Identifier("villagepax", "no_such_people");
        int stone = Traits.blocksPerTurn(NORMAN, Blocks.COBBLESTONE.getDefaultState());
        int wood = Traits.blocksPerTurn(NORMAN, Blocks.OAK_PLANKS.getDefaultState());
        int stranger = Traits.blocksPerTurn(nobody, Blocks.COBBLESTONE.getDefaultState());

        if (stone != 2 || wood != 1 || stranger != 1) {
            context.throwGameTestException("Темп по черте: камень " + stone + ", дерево "
                    + wood + ", чужой народ " + stranger + "; ожидалось 2, 1 и 1");
        }
        if (Traits.blocksPerTurn(NORMAN, null) != 1) {
            context.throwGameTestException("У расчистки, где блока нет, темп "
                    + Traits.blocksPerTurn(NORMAN, null));
        }

        context.complete();
    }

    /**
     * Каменное дело видно на стройке: мастер по камню кончает раньше.
     * <p>
     * Сравниваются <b>два прогона одной и той же площадки</b> — народом
     * с чертой и народом без неё, — а не число шагов за одно решение.
     * Так пришлось потому, что план бесплатно пропускает шаги, которые уже
     * совпали с миром: над готовой площадкой первое же решение «сдвигает»
     * план на два десятка шагов расчистки, не положив ни блока. Счётчик
     * решений от этого не страдает: пропуски одинаковы в обоих прогонах.
     * <p>
     * Мир между прогонами возвращается в прежний вид — иначе второй прогон
     * получил бы даром то, что первый уже расчистил, и сравнение врало бы.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "traits")
    public void masonsFinishStoneworkSooner(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, new Identifier("villagepax", "norman/house_lvl1"));

        // Ратуша вплотную к стройке намеренно: дальше двенадцати блоков
        // билдер не берёт со склада сам, и оба прогона встали бы
        // по нехватке материалов, а сравниваем мы темп.
        BlockPos storage = context.getAbsolutePos(new BlockPos(8, 9, 8));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> ground = new ArrayList<>();

        try {
            for (int x = -2; x <= 12; x++) {
                for (int z = -2; z <= 12; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            // Народ без объявленных черт: культуры с таким именем в датапаке
            // нет, и черт у неё поэтому нет никаких. Это же проверяет, что
            // отсутствие культуры не роняет стройку.
            Identifier plainFolk = new Identifier("villagepax", "no_such_people");

            int mason = decisionsToBuild(context, world, manager, NORMAN, anchor, storage, house);
            int plain = decisionsToBuild(context, world, manager, plainFolk, anchor, storage, house);

            if (mason <= 0 || plain <= 0) {
                context.throwGameTestException("Дом не достроился: у мастера " + mason
                        + " решений, у прочих " + plain + " (ноль означает недострой)");
                return;
            }
            if (mason >= plain) {
                context.throwGameTestException("Каменное дело не ускорило стройку: "
                        + mason + " решений у мастера против " + plain + " у народа без черты");
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(storage, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Сколько решений уходит на дом у этого народа. Ноль — не достроил.
     * <p>
     * За собой убирает начисто: план возвращается в воздух, тела
     * исчезают, поселение забывается. Следующий прогон обязан начинаться
     * с того же мира, иначе сравнивать нечего.
     */
    private static int decisionsToBuild(TestContext context, ServerWorld world,
                                        SettlementManager manager, Identifier culture,
                                        BlockPos anchor, BlockPos storage, Schematic schematic) {
        world.setBlockState(storage, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(culture, Owner.of(UUID.randomUUID()),
                "Проба", storage);
        manager.add(colony);

        Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 1, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);

        try {
            stockFor(world, colony, schematic);
            Citizen builder = hireWithBody(world, colony, BuildJob.BUILDER,
                    context.getAbsolutePos(new BlockPos(-1, 9, -1)));
            CitizenEntity body = (CitizenEntity) world
                    .getEntity(builder.entityUuid().orElseThrow());

            for (int round = 1; round <= 600; round++) {
                WorkTicker.decide(world, manager, colony, builder, Schedule.MORNING_WORK);
                if (site.isOperational()) {
                    return round;
                }

                BlockPos target = body.workTarget();
                if (target != null && standable(world, target)) {
                    body.refreshPositionAndAngles(target.getX() + 0.5, target.getY(),
                            target.getZ() + 0.5, 0f, 0f);
                }
            }
            return 0;
        } finally {
            demolish(world, site, schematic);
            discardBodies(world, colony);
            manager.remove(colony.id());
        }
    }

    // --- фаза 0.2: второй народ ---

    private static final Identifier MAYA = new Identifier("villagepax", "maya");
    private static final Identifier MAYA_TOWN_HALL_TYPE =
            new Identifier("villagepax", "maya/town_hall");
    private static final Identifier MAYA_HOUSE_TYPE = new Identifier("villagepax", "maya/house");

    /**
     * Каждый народ просит своё.
     * <p>
     * Квесты в датапаке разложены по выдающей профессии, а старейшина
     * у всех народов один и тот же. Без народа у самой просьбы майя
     * встречали бы игрока норманнской фразой про зимние поленницы — и это
     * не мелочь, а первое, что игрок в чужой деревне слышит.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "maya")
    public void eachPeopleAsksItsOwnQuests(TestContext context) {
        BlockPos where = context.getAbsolutePos(new BlockPos(1, 1, 1));
        UUID player = UUID.randomUUID();

        Settlement norman = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", where);
        Settlement maya = Settlement.found(MAYA, Owner.AUTONOMOUS, "Йашчилан", where);

        Identifier asksNorman = Quests.offered(norman, player, Villages.ELDER).orElse(null);
        Identifier asksMaya = Quests.offered(maya, player, Villages.ELDER).orElse(null);

        if (asksNorman == null || asksMaya == null) {
            context.throwGameTestException("Старейшина молчит: у норманнов " + asksNorman
                    + ", у майя " + asksMaya);
            return;
        }
        if (!asksNorman.getPath().startsWith("norman/")) {
            context.throwGameTestException("Норманны просят чужое: " + asksNorman);
        }
        if (!asksMaya.getPath().startsWith("maya/")) {
            context.throwGameTestException("Майя просят чужое: " + asksMaya);
        }
        if (asksNorman.equals(asksMaya)) {
            context.throwGameTestException("Оба народа просят одно и то же: " + asksMaya);
        }

        context.complete();
    }

    /**
     * Деревня майя встаёт своей, а не перекрашенной норманнской.
     * <p>
     * Проверяется по трём разным следам сразу, потому что «второй народ»
     * ломается по-разному: типы зданий могут оказаться чужими, схема —
     * норманнской, а материал — общим. Поэтому смотрим и на объявленные
     * типы, и на то, что <b>в мире действительно стоит охряная стена</b>.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "maya")
    public void mayaVillageIsBuiltOfItsOwnMaterial(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, MAYA, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня майя не встала: помеха="
                        + whoBlocks(manager, centre));
                return;
            }

            for (Building building : village.buildings()) {
                if (!building.type().getPath().startsWith("maya/")) {
                    context.throwGameTestException("У майя чужое здание: " + building.type());
                    return;
                }
            }
            if (village.buildings().size() < 3) {
                context.throwGameTestException("Деревня встала неполной: зданий "
                        + village.buildings().size() + ", ожидались ратуша, дом и поле");
            }

            // Ратуша ставится при основании целиком, значит охряная стена
            // обязана уже стоять в мире. Ищем её в следе поселения.
            boolean ochre = false;
            for (BlockPos at : BlockPos.iterate(centre.add(-10, -1, -10), centre.add(10, 12, 10))) {
                if (world.getBlockState(at).isOf(ModBlocks.OCHRE_PLASTER)) {
                    ochre = true;
                    break;
                }
            }
            if (!ochre) {
                context.throwGameTestException("В деревне майя нет ни одной охряной стены — "
                        + "стоит что-то чужое");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Террасники берутся за склон, на который норманны не пойдут.
     * <p>
     * Это и есть черта {@code terrace_farming} в деле. Проверяется на
     * настоящей площадке со ступенью, а не только числом из настройки:
     * число могло бы остаться неприменённым.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "maya")
    public void terraceBuildersTakeSlopesOthersRefuse(TestContext context) {
        if (Traits.maxSlope(MAYA) <= Traits.maxSlope(NORMAN)) {
            context.throwGameTestException("Уклон у террасников не больше: майя "
                    + Traits.maxSlope(MAYA) + ", норманны " + Traits.maxSlope(NORMAN));
            return;
        }
        if (!Traits.has(MAYA, Trait.TERRACE_FARMING)) {
            context.throwGameTestException("У майя нет объявленной черты террас: "
                    + Traits.of(MAYA));
        }
        if (Traits.has(NORMAN, Trait.TERRACE_FARMING)) {
            context.throwGameTestException("Черта террас досталась и норманнам");
        }

        context.complete();
    }

    /**
     * Храм совета второго уровня достраивается, ни разу не встав в воздух.
     * <p>
     * Двенадцать блоков высотой — самая высокая схема мода, и на ней
     * проверяется тот же предел досягаемости, на котором споткнулся дом
     * норманнов. Если билдер не дотянется до гребня, играть за майя будет
     * нельзя: ратуша у них выше всего остального.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "reach")
    public void mayaTempleIsBuiltWithoutStandingInMidair(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic temple = schematic(context, new Identifier("villagepax", "maya/town_hall_lvl2"));

        // Ратуша вплотную: дальше двенадцати блоков билдер не берёт
        // со склада сам, и проверка досягаемости выродилась бы в проверку
        // подвоза.
        BlockPos hall = context.getAbsolutePos(new BlockPos(8, 9, 8));
        List<BlockPos> ground = new ArrayList<>();

        try {
            for (int x = -2; x <= 12; x++) {
                for (int z = -2; z <= 12; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall, MAYA);
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
            Building site = new Building(UUID.randomUUID(), MAYA_TOWN_HALL_TYPE, 2, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(site);

            try {
                stockFor(world, colony, temple);
                Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER,
                        context.getAbsolutePos(new BlockPos(-1, 9, -1)));

                int stalled = runWorkOnFoot(world, manager, colony, mason, 1_200);

                if (!site.isOperational()) {
                    context.throwGameTestException("Храм не достроился: билдер встал на шаге "
                            + site.nextStep() + " из " + temple.plan().steps().size() + ", и "
                            + stalled + " раз его посылали туда, где человек стоять не может");
                }
                if (stalled > 0) {
                    context.throwGameTestException("Билдера " + stalled
                            + " раз посылали стоять в воздух — в игре он туда не дойдёт");
                }
            } finally {
                demolish(world, site, temple);
                discardBodies(world, colony);
                manager.remove(colony.id());
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- монета мода и кошель ---

    /**
     * Монета размениваетcя сама: заплатить пять медяков золотым можно.
     * <p>
     * Это главное в своей монете и единственное, что в ней непросто.
     * Игрок с одним золотым в кармане обязан иметь возможность купить
     * хлеб за два медяка — иначе крупная монета становится обузой,
     * а не богатством. Сдача при этом должна быть <b>ровной</b>: не
     * «примерно семьдесят шесть», а именно восемь серебряков и четыре
     * медяка.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "coins")
    public void coinsBreakIntoChangeWhenPaying(TestContext context) {
        SimpleInventory pocket = new SimpleInventory(36);
        pocket.addStack(new ItemStack(ModItems.GOLD_COIN, 1));

        if (Coins.total(pocket) != Coins.GOLD) {
            context.throwGameTestException("Золотой стоит " + Coins.total(pocket)
                    + " медяков, а должен " + Coins.GOLD);
            return;
        }
        if (Coins.has(pocket, Coins.GOLD + 1)) {
            context.throwGameTestException("На один золотой хватает больше, чем на золотой");
        }

        List<ItemStack> change = Coins.pay(pocket, 5);
        if (!change.isEmpty()) {
            context.throwGameTestException("Сдача не влезла в пустой инвентарь: " + change);
        }
        if (Coins.total(pocket) != Coins.GOLD - 5) {
            context.throwGameTestException("После уплаты пяти осталось "
                    + Coins.total(pocket) + " вместо " + (Coins.GOLD - 5));
        }
        if (pocket.count(ModItems.SILVER_COIN) != 8 || pocket.count(ModItems.COIN) != 4
                || pocket.count(ModItems.GOLD_COIN) != 0) {
            context.throwGameTestException("Сдача неровная: золотых "
                    + pocket.count(ModItems.GOLD_COIN) + ", серебряков "
                    + pocket.count(ModItems.SILVER_COIN) + ", медяков "
                    + pocket.count(ModItems.COIN) + "; ожидалось 0, 8 и 4");
        }

        // И обратно: выдача идёт крупным вперёд, а не горстью медяков.
        SimpleInventory paid = new SimpleInventory(36);
        Coins.earn(paid, Coins.GOLD + Coins.SILVER * 2 + 3);
        if (paid.count(ModItems.GOLD_COIN) != 1 || paid.count(ModItems.SILVER_COIN) != 2
                || paid.count(ModItems.COIN) != 3) {
            context.throwGameTestException("Выдано мелочью: золотых "
                    + paid.count(ModItems.GOLD_COIN) + ", серебряков "
                    + paid.count(ModItems.SILVER_COIN) + ", медяков "
                    + paid.count(ModItems.COIN));
        }

        context.complete();
    }

    /**
     * Кошель — это деньги, а не сундук: торг заглядывает в него сам.
     * <p>
     * Иначе игрок, убравший монету в кошель, не смог бы ничего купить,
     * пока не вытряхнет его на землю, — и кошель из удобства стал бы
     * помехой. Проверяется покупкой при <b>пустых руках</b>: вся монета
     * лежит в кошеле.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "coins")
    public void purseCountsAsMoneyWhenTrading(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);
        List<ItemStack> spilled = new ArrayList<>();

        try {
            TradeTable.Deal bread = Trading.find(colony, Trading.Side.VILLAGE_SELLS,
                    Items.BREAD, 6).orElse(null);
            if (bread == null) {
                context.throwGameTestException("Стол торга норманнов не загрузился");
                return;
            }

            // setStack, а не addStack: SimpleInventory кладёт КОПИЮ стопки,
            // и тест держал бы в руках не тот кошель, который списывают.
            ItemStack purse = new ItemStack(ModItems.PURSE);
            PurseItem.setValue(purse, Coins.SILVER);
            hands.setStack(0, purse);
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 12));

            if (Coins.total(hands) != Coins.SILVER) {
                context.throwGameTestException("Монета в кошеле не считается: "
                        + Coins.total(hands) + " вместо " + Coins.SILVER);
            }
            if (Coins.loose(hands) != 0) {
                context.throwGameTestException("В руках нашлась россыпь, которой нет");
            }

            int price = Trading.priceFor(bread, Trading.Side.VILLAGE_SELLS, 0);
            Trading.Outcome bought = Trading.trade(colony, player, hands,
                    Warehouse.of(world, colony), Trading.Side.VILLAGE_SELLS, bread,
                    spilled::add);

            if (bought != Trading.Outcome.DONE) {
                context.throwGameTestException("Покупка из кошеля отказана: " + bought);
                return;
            }
            if (PurseItem.valueOf(purse) != Coins.SILVER - price) {
                context.throwGameTestException("Из кошеля списано неверно: осталось "
                        + PurseItem.valueOf(purse) + " вместо " + (Coins.SILVER - price));
            }
            if (Coins.loose(hands) != 0) {
                context.throwGameTestException("Плата развалилась на россыпь в руках: "
                        + Coins.loose(hands));
            }
            if (hands.count(Items.BREAD) != bread.count()) {
                context.throwGameTestException("Хлеба получено " + hands.count(Items.BREAD));
            }
            if (!spilled.isEmpty()) {
                context.throwGameTestException("Что-то просыпалось: " + spilled);
            }
        } finally {
            Coins.pay(Warehouse.of(world, colony).coins(),
                    Coins.total(Warehouse.of(world, colony).coins()));
            Warehouse.of(world, colony).take(Items.BREAD, 64);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Кошель наполняется и вытряхивается, и больше своего не берёт.
     * <p>
     * Предел нужен не для баланса: кошель без предела — это банк
     * в кармане, и он обесценил бы сундук. Проверяется и он, и то, что
     * вытряхнутое возвращается <b>крупной монетой</b>, а не горстью.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "coins")
    public void purseStowsAndShakesOut(TestContext context) {
        SimpleInventory pocket = new SimpleInventory(36);
        // setStack, а не addStack: SimpleInventory кладёт копию стопки.
        ItemStack purse = new ItemStack(ModItems.PURSE);
        pocket.setStack(0, purse);
        pocket.addStack(new ItemStack(ModItems.COIN, 12));

        int stowed = Coins.fillPurse(pocket, purse);
        if (stowed != 12 || PurseItem.valueOf(purse) != 12 || Coins.loose(pocket) != 0) {
            context.throwGameTestException("Убрано " + stowed + ", в кошеле "
                    + PurseItem.valueOf(purse) + ", в руках " + Coins.loose(pocket)
                    + "; ожидалось 12, 12 и 0");
        }

        int taken = Coins.emptyPurse(pocket, purse);
        if (taken != 12 || PurseItem.valueOf(purse) != 0 || Coins.loose(pocket) != 12) {
            context.throwGameTestException("Вынуто " + taken + ", в кошеле "
                    + PurseItem.valueOf(purse) + ", в руках " + Coins.loose(pocket));
        }
        if (pocket.count(ModItems.SILVER_COIN) != 1 || pocket.count(ModItems.COIN) != 3) {
            context.throwGameTestException("Вытряхнуто мелочью: серебряков "
                    + pocket.count(ModItems.SILVER_COIN) + ", медяков "
                    + pocket.count(ModItems.COIN) + "; ожидались 1 и 3");
        }

        // Предел: больше своего кошель не возьмёт, а остаток вернёт.
        int over = PurseItem.put(purse, PurseItem.CAPACITY + 100);
        if (PurseItem.valueOf(purse) != PurseItem.CAPACITY || over != 100) {
            context.throwGameTestException("Предел кошеля не держит: внутри "
                    + PurseItem.valueOf(purse) + " при пределе " + PurseItem.CAPACITY
                    + ", отказано " + over);
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

    // --- по следам жалоб из игры: огонь и пустые места ---

    /**
     * Житель выходит из огня, а не сгорает в нём.
     * <p>
     * Из настоящей игры: строитель погиб, перестраивая дом, — сгорел
     * на очаге. Это не невезение, а ванильная ловушка: узел пути в огне
     * стоит шестнадцать шагов, но <b>из</b> него путь почти не строится,
     * и моб, оказавшийся в костре, стоит и горит до смерти.
     * <p>
     * Проверяется ровно это: жителя ставят в горящий костёр и дают
     * рефлексу сработать. Он обязан оказаться <b>не в огне</b> и живым.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire")
    public void citizenStepsOutOfTheFire(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 2, 1));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            for (int x = -2; x <= 4; x++) {
                for (int z = -2; z <= 4; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            BlockPos hearth = context.getAbsolutePos(new BlockPos(3, 2, 3));
            world.setBlockState(hearth, Blocks.CAMPFIRE.getDefaultState());
            floor.add(hearth);

            Citizen citizen = hireWithBody(world, colony, BuildJob.BUILDER, hearth);
            CitizenEntity body = (CitizenEntity) world
                    .getEntity(citizen.entityUuid().orElseThrow());
            body.refreshPositionAndAngles(hearth.getX() + 0.5, hearth.getY(),
                    hearth.getZ() + 0.5, 0f, 0f);

            if (!Hazards.standingHurts(world, body.getBlockPos())) {
                context.throwGameTestException("Тест не поставил жителя в огонь: "
                        + world.getBlockState(body.getBlockPos()));
                return;
            }

            if (!body.stepOutOfTrouble()) {
                context.throwGameTestException("Житель остался в костре: рефлекс не сработал");
                return;
            }
            if (Hazards.standingHurts(world, body.getBlockPos())) {
                context.throwGameTestException("Житель вышел из огня в огонь: "
                        + body.getBlockPos().toShortString());
            }
            if (!body.isAlive()) {
                context.throwGameTestException("Житель не пережил собственного очага");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Блок не ставится в того, кто в этой клетке стоит.
     * <p>
     * Вторая половина той же смерти: костёр из схемы ставится <b>внутрь</b>
     * жителя, и он оказывается в огне, ничего не сделав. Проверяется, что
     * стройка сперва отодвигает своего, а если клетка всё равно занята —
     * ждёт, а не ставит.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire")
    public void nothingIsBuiltInsideTheLiving(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, new Identifier("villagepax", "norman/house_lvl1"));

        BlockPos hall = context.getAbsolutePos(new BlockPos(7, 2, 7));
        List<BlockPos> floor = new ArrayList<>();

        try {
            for (int x = -2; x <= 9; x++) {
                for (int z = -2; z <= 9; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall);
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 2, 0));
            Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 1, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(site);

            try {
                stockFor(world, colony, house);

                // Житель встаёт ровно туда, где по плану встанет очаг.
                BlockPos hearth = hearthOf(house, site);
                if (hearth == null) {
                    context.throwGameTestException("В схеме дома нет очага — проверять нечего");
                    return;
                }

                Citizen citizen = hireWithBody(world, colony, BuildJob.BUILDER, hearth);
                CitizenEntity body = (CitizenEntity) world
                        .getEntity(citizen.entityUuid().orElseThrow());
                body.refreshPositionAndAngles(hearth.getX() + 0.5, hearth.getY(),
                        hearth.getZ() + 0.5, 0f, 0f);

                // Стройка гонится целиком, без ограничения вытянутой руки.
                BuildJob.advance(world, manager, colony.id(), site.id(), 2_000);

                if (world.getBlockState(hearth).isOf(Blocks.CAMPFIRE)
                        && new net.minecraft.util.math.Box(hearth)
                                .intersects(body.getBoundingBox())) {
                    context.throwGameTestException("Костёр поставлен прямо в жителя: "
                            + hearth.toShortString());
                }
                if (!body.isAlive()) {
                    context.throwGameTestException("Житель не пережил стройку");
                }
            } finally {
                demolish(world, site, house);
                discardBodies(world, colony);
                manager.remove(colony.id());
            }
        } finally {
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Где в этой схеме очаг, в координатах мира. */
    private static BlockPos hearthOf(Schematic schematic, Building site) {
        List<BuildStep> steps = schematic.plan().steps();
        for (BuildStep step : steps) {
            if (step.placesBlock()
                    && schematic.blockAt(step.paletteIndex()).isOf(Blocks.CAMPFIRE)) {
                return BuildJob.worldPos(site, schematic.size(), step.pos());
            }
        }
        return null;
    }

    /**
     * Земля под пологом леса находится, а верхушка листвы — не земля.
     * <p>
     * Это причина, по которой деревни <b>не появлялись вовсе</b>. Поиск
     * места считал высоту по генератору, то есть по рельефу без деревьев,
     * а основание деревни спрашивало у мира карту высот, куда входит
     * листва. Игрок приходил по указанным координатам, стоял на месте
     * полминуты — и не видел ничего: опоры на верхушке дерева нет,
     * и деревня молча отказывалась вставать. Ровно так это и выглядело
     * в его логе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire")
    public void groundIsFoundUnderTheCanopy(TestContext context) {
        ServerWorld world = context.getWorld();
        List<BlockPos> planted = new ArrayList<>();

        try {
            BlockPos soil = context.getAbsolutePos(new BlockPos(2, 1, 2));
            world.setBlockState(soil, Blocks.GRASS_BLOCK.getDefaultState());
            planted.add(soil);

            // Полог: листва в шести блоках над землёй, как в джунглях.
            for (int y = 7; y <= 9; y++) {
                BlockPos leaf = context.getAbsolutePos(new BlockPos(2, y, 2));
                world.setBlockState(leaf, Blocks.JUNGLE_LEAVES.getDefaultState());
                planted.add(leaf);
            }

            BlockPos found = Ground.buildableAt(world, soil.getX(), soil.getZ()).orElse(null);
            if (found == null) {
                context.throwGameTestException("Под пологом земля не нашлась вовсе — "
                        + "деревня в лесу не встанет никогда");
                return;
            }
            if (found.getY() != soil.getY() + 1) {
                context.throwGameTestException("Землёй сочтено " + found.toShortString()
                        + " вместо клетки над дёрном " + soil.up().toShortString());
            }

            // А в стволе строить нельзя: там нет ни воздуха, ни опоры.
            BlockPos trunk = context.getAbsolutePos(new BlockPos(4, 1, 4));
            world.setBlockState(trunk, Blocks.GRASS_BLOCK.getDefaultState());
            planted.add(trunk);
            for (int y = 2; y <= 6; y++) {
                BlockPos log = context.getAbsolutePos(new BlockPos(4, y, 4));
                world.setBlockState(log, Blocks.JUNGLE_LOG.getDefaultState());
                planted.add(log);
            }
            BlockPos onTrunk = Ground.buildableAt(world, trunk.getX(), trunk.getZ())
                    .orElse(null);
            if (onTrunk != null && onTrunk.getY() > trunk.getY()) {
                context.throwGameTestException("В стволе дерева нашлась «земля» выше дёрна: "
                        + onTrunk.toShortString() + ", там "
                        + world.getBlockState(onTrunk.down()).getBlock());
            }
        } finally {
            for (BlockPos at : planted) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
        }

        context.complete();
    }

    /**
     * Деревня встаёт под пологом леса и строит по земле, а не по кронам.
     * <p>
     * Это и есть та жалоба целиком: «деревни всё ещё не ищутся». Поиск
     * звал игрока по координатам, тот приходил, стоял полминуты — и видел
     * пустое место. Причина была в двух разных ответах на вопрос «где
     * тут земля»: поиск считал по рельефу без деревьев, а деревня искала
     * опору по карте высот мира, куда входит листва. Под пологом джунглей
     * опоры не находилось ни в одной колонне, и деревня молча отказывалась
     * вставать.
     * <p>
     * Проверяется на настоящем пологе: поляна с листвой в шести блоках
     * над землёй. Деревня обязана встать и разметить здания <b>у земли</b>.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "fire")
    public void villageRisesUnderTheCanopy(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);

                    // Полог: сплошная листва в шести блоках над землёй.
                    BlockPos leaf = context.getAbsolutePos(new BlockPos(x, 15, z));
                    world.setBlockState(leaf, Blocks.JUNGLE_LEAVES.getDefaultState());
                    meadow.add(leaf);
                }
            }

            // И стволы: в настоящих джунглях полог держится на деревьях,
            // а не висит в воздухе. Ствол — твёрдая колонна, в которой
            // строить нельзя, и её нельзя путать с «здесь нет земли».
            for (int x = -18; x <= 18; x += 4) {
                for (int z = -18; z <= 18; z += 4) {
                    if (Math.abs(x) <= 4 && Math.abs(z) <= 4) {
                        continue;   // серединку оставляем под ратушу
                    }
                    for (int y = 9; y <= 14; y++) {
                        BlockPos log = context.getAbsolutePos(new BlockPos(x, y, z));
                        world.setBlockState(log, Blocks.JUNGLE_LOG.getDefaultState());
                        meadow.add(log);
                    }
                }
            }

            village = Villages.found(world, MAYA, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Под пологом деревня не встала — "
                        + "ровно то, на что жаловался игрок. Помеха="
                        + whoBlocks(manager, centre));
                return;
            }

            int ground = centre.getY();
            for (Building building : village.buildings()) {
                int high = building.anchor().getY() - ground;
                if (high > 2) {
                    context.throwGameTestException("Здание " + building.type()
                            + " размечено на " + high + " блоков выше земли — это крона");
                }
            }
            if (village.buildings().size() < 3) {
                context.throwGameTestException("Под пологом встала неполная деревня: зданий "
                        + village.buildings().size());
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Ни одно здание мода не остаётся без источника света.
     * <p>
     * Темнота в Minecraft — это не настроение, а правило порождения:
     * в клетке с освещённостью ноль ночью встаёт моб. Мастерская, склад
     * и ткацкая стояли глухими коробками без окон и очага, и к утру
     * внутри ждал скелет — того самого жителя, который придёт работать.
     * Поэтому свет здесь не украшение, и проверка не «косметическая».
     * <p>
     * Проверка одна на все схемы нарочно: фонарь вешает генератор сам,
     * по одному правилу на весь мод, и новое здание получит его, не
     * спросив меня. Но правило ищет потолок — у поля и рощи потолка нет,
     * и там свет написан руками на угловых столбах ограды. Забыть его
     * в новом уличном здании легче всего, и ловит это ровно эта строка.
     * <p>
     * Спрашивается яркость <b>состояния блока</b>, а не список имён:
     * список пришлось бы дописывать под каждый новый светильник, и он
     * молча устарел бы на первом же факеле души.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "everything")
    public void noBuildingIsLeftDark(TestContext context) {
        List<Identifier> all = new ArrayList<>(SchematicLoader.ids());
        all.sort(java.util.Comparator.comparing(Identifier::toString));

        List<String> dark = new ArrayList<>();
        for (Identifier id : all) {
            Schematic schematic = SchematicLoader.get(id).orElseThrow();
            boolean lit = schematic.blocks().stream()
                    .anyMatch(block -> schematic.blockAt(block.paletteIndex()).getLuminance() > 0);
            if (!lit) {
                dark.add(id.toString());
            }
        }

        if (!dark.isEmpty()) {
            context.throwGameTestException("Здания, в которых ночью нечем светить:\n  "
                    + String.join("\n  ", dark));
        }

        context.complete();
    }

    /**
     * Билдер достраивает <b>каждую</b> схему мода, ни разу не встав в воздух.
     * <p>
     * До сих пор досягаемость проверялась на двух зданиях: доме норманнов
     * второго уровня и храме майя. Обоих я выбирал сам — «самые высокие», —
     * и это ровно та ошибка, за которую игрок меня отругал: я проверял там,
     * где смотрел, а не там, где играют. Складов, мастерских и террасных
     * полей не проверял никто, а схем в моде восемнадцать.
     * <p>
     * Проверяется <b>чистый выбор</b> билдера, без тела в мире: у стройки
     * спрашивается место, где он будет стоять, и оттуда же делается шаг.
     * Так надо по двум причинам. Тело, созданное в этом же тике, мир ещё
     * не отдаёт по опознавателю — тест с телами падал бы не по своей вине.
     * А главное, проверять надо именно решение: «куда стратегия его
     * послала», а не «дошёл ли он», — иначе тест мигает от чужой ходьбы.
     * <p>
     * Проверка нарочно одна на все схемы: добавится девятнадцатая — она
     * проверится сама, и забыть о ней будет нельзя.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "everything")
    public void everySchematicIsBuiltFromStandableSpots(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        List<Identifier> all = new ArrayList<>(SchematicLoader.ids());
        all.sort(java.util.Comparator.comparing(Identifier::toString));

        List<String> complaints = new ArrayList<>();
        List<BlockPos> ground = new ArrayList<>();

        // Площадка с запасом на самую большую схему: поле второго уровня
        // девять на девять, а билдеру надо где стоять вокруг.
        //
        // Ратуша стоит в десяти блоках от угла стройки и вне её следа.
        // Ближе двенадцати — потому что дальше билдер не берёт со склада
        // сам (BuildJob.NEARBY_STORAGE), и проверка досягаемости
        // выродилась бы в проверку подвоза. Вне следа — чтобы схема
        // не накрыла ратушу.
        BlockPos hall = context.getAbsolutePos(new BlockPos(10, 9, 3));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));

        try {
            for (int x = -3; x <= 15; x++) {
                for (int z = -3; z <= 15; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            for (Identifier id : all) {
                Schematic schematic = SchematicLoader.get(id).orElseThrow();
                Identifier type = BuildJob.buildingTypeOf(id).orElse(null);
                if (type == null) {
                    complaints.add(id + ": тип здания не выводится из имени схемы");
                    continue;
                }

                Identifier culture = new Identifier(type.getNamespace(),
                        type.getPath().split("/")[0]);
                Settlement colony = colonyWithBuilder(world, manager, hall, culture);
                Building site = new Building(UUID.randomUUID(), type,
                        BuildJob.levelOf(id).orElse(1), anchor, BlockRotation.NONE,
                        BuildProgress.PLANNED, List.of());
                colony.addBuilding(site);

                try {
                    stockFor(world, colony, schematic);
                    complaints.addAll(raiseFromStandableSpots(world, manager, colony, site,
                            schematic, id));
                } finally {
                    demolish(world, site, schematic);
                    manager.remove(colony.id());
                }
            }

            if (!complaints.isEmpty()) {
                context.throwGameTestException("Схемы, на которых билдер спотыкается:\n  "
                        + String.join("\n  ", complaints));
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Построить, делая каждый шаг с того места, которое назвала стратегия,
     * и жалуясь на всё, что при этом пошло не так.
     */
    private static List<String> raiseFromStandableSpots(ServerWorld world,
                                                        SettlementManager manager,
                                                        Settlement colony, Building site,
                                                        Schematic schematic, Identifier id) {
        List<String> complaints = new ArrayList<>();
        int steps = schematic.plan().steps().size();
        int midair = 0;
        int inFire = 0;

        for (int round = 0; round < steps * 4 + 200 && !site.isOperational(); round++) {
            BlockPos spot = BuilderJob.standingSpot(world, site).orElse(null);
            if (spot == null) {
                complaints.add(id + ": стройка не назвала места, где стоять, на шаге "
                        + site.nextStep());
                return complaints;
            }
            if (!standable(world, spot)) {
                midair++;
            }
            if (Hazards.standingHurts(world, spot)) {
                inFire++;
            }

            BuildJob.Outcome outcome = BuildJob.advance(world, manager, colony.id(), site.id(),
                    1, Vec3d.ofBottomCenter(spot));
            if (outcome == BuildJob.Outcome.NO_BUILDER
                    || outcome == BuildJob.Outcome.NO_SCHEMATIC
                    || outcome == BuildJob.Outcome.NOT_LOADED
                    || outcome == BuildJob.Outcome.NOT_FOUND) {
                complaints.add(id + ": стройка отказала — " + outcome);
                return complaints;
            }
            if (outcome == BuildJob.Outcome.WAITING_FOR_MATERIALS) {
                complaints.add(id + ": не хватило материалов на шаге " + site.nextStep()
                        + " из " + steps + ", хотя завезли всё по заявке");
                return complaints;
            }
        }

        if (!site.isOperational()) {
            complaints.add(id + ": не достроилось, шаг " + site.nextStep() + " из " + steps);
        }
        if (midair > 0) {
            complaints.add(id + ": " + midair + " раз стоять предлагалось там, "
                    + "где человек стоять не может");
        }
        if (inFire > 0) {
            complaints.add(id + ": " + inFire + " раз стоять предлагалось в огне");
        }
        return complaints;
    }

    // --- по следам жалобы: остальные ремёсла тоже никто не гонял ---

    /**
     * Фермера посылают только туда, где можно стоять.
     * <p>
     * Билдера я после жалобы игрока проверил на всех схемах, а фермера,
     * лесоруба и курьера — никто. У них та же беда: цель ставит стратегия,
     * и если она назовёт точку в воздухе или в огне, житель послушно туда
     * пойдёт. Проверяется тот же инвариант, что у билдера.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trades")
    public void farmerIsSentOnlyWhereHeCanStand(TestContext context) {
        checkTradeReach(context, new Identifier("villagepax", "norman/farm_lvl1"),
                FARM_TYPE, new Identifier("villagepax", "farmer"));
    }

    /** Лесоруба — тоже: у него роща, и в ствол вставать нельзя. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trades")
    public void lumberjackIsSentOnlyWhereHeCanStand(TestContext context) {
        checkTradeReach(context, new Identifier("villagepax", "norman/lumberjack_lvl1"),
                new Identifier("villagepax", "norman/lumberjack"),
                new Identifier("villagepax", "lumberjack"));
    }

    /**
     * Курьера — тоже: он ходит между складом и стройкой, и обе точки
     * выбирает стратегия.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trades")
    public void courierIsSentOnlyWhereHeCanStand(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, new Identifier("villagepax", "norman/house_lvl1"));

        BlockPos hall = context.getAbsolutePos(new BlockPos(20, 9, 2));
        List<BlockPos> ground = new ArrayList<>();

        try {
            for (int x = -3; x <= 24; x++) {
                for (int z = -3; z <= 10; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall);
            // Стройка нарочно далеко от склада: ближе двенадцати блоков
            // билдер носит сам, и курьеру нечего было бы делать.
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
            Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 1, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(site);

            try {
                stockFor(world, colony, house);
                Citizen courier = hireWithBody(world, colony,
                        new Identifier("villagepax", "courier"),
                        context.getAbsolutePos(new BlockPos(18, 9, 2)));

                List<String> complaints = watchWhereHeIsSent(world, manager, colony, courier,
                        200, "курьер");
                if (!complaints.isEmpty()) {
                    context.throwGameTestException(String.join("\n  ", complaints));
                }
            } finally {
                demolish(world, site, house);
                discardBodies(world, colony);
                manager.remove(colony.id());
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Общая проверка ремесла с мастерской: здание строится целиком,
     * житель нанимается, и дальше смотрим, куда его посылают.
     */
    private static void checkTradeReach(TestContext context, Identifier schematicId,
                                        Identifier buildingType, Identifier profession) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = schematic(context, schematicId);

        // Ратуша ближе двенадцати блоков к стройке и вне её следа: дальше
        // билдер не берёт материалы сам (BuildJob.NEARBY_STORAGE), и
        // мастерская не достроится вовсе. На это я наступил трижды.
        BlockPos hall = context.getAbsolutePos(new BlockPos(11, 9, 2));
        List<BlockPos> ground = new ArrayList<>();

        try {
            for (int x = -3; x <= 18; x++) {
                for (int z = -3; z <= 12; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    ground.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall);
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
            Building workplace = new Building(UUID.randomUUID(), buildingType, 1, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(workplace);

            try {
                stockFor(world, colony, schematic);
                BuildJob.Outcome raised = BuildJob.advance(world, manager, colony.id(),
                        workplace.id(), 20_000);
                if (!workplace.isOperational()) {
                    context.throwGameTestException("Мастерская не достроилась (" + raised
                            + "), проверять нечего: шаг " + workplace.nextStep() + " из "
                            + schematic.plan().steps().size());
                    return;
                }

                // Мастерская, в которой нечего делать, ничего не проверяет:
                // поле приходит уже засеянным, а роща — саженцами. Дадим
                // им работу: урожай поспел, дерево выросло.
                giveWork(world, workplace, schematic);

                Citizen worker = hireWithBody(world, colony, profession,
                        context.getAbsolutePos(new BlockPos(12, 9, 2)));
                Workplaces.assign(world, colony);

                List<String> complaints = watchWhereHeIsSent(world, manager, colony, worker,
                        300, profession.getPath());
                if (!complaints.isEmpty()) {
                    context.throwGameTestException(String.join("\n  ", complaints));
                }
            } finally {
                demolish(world, workplace, schematic);
                discardBodies(world, colony);
                manager.remove(colony.id());
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Гонять решения и смотреть, куда жителя посылают.
     * <p>
     * Тело переносится только туда, где может стоять человек, — как
     * в проверке билдера. Считаются два промаха: точка, в которой стоять
     * нельзя, и точка, в которой жжётся. И третий случай: если жителя
     * не послали никуда ни разу, проверять было нечего, и об этом надо
     * сказать, а не молча зачесть.
     */
    private static List<String> watchWhereHeIsSent(ServerWorld world, SettlementManager manager,
                                                   Settlement colony, Citizen worker,
                                                   int rounds, String who) {
        CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());
        List<String> complaints = new ArrayList<>();
        int midair = 0;
        int inFire = 0;
        int sent = 0;

        for (int round = 0; round < rounds; round++) {
            WorkTicker.decide(world, manager, colony, worker, Schedule.MORNING_WORK);

            BlockPos target = body.workTarget();
            if (target == null) {
                continue;
            }
            sent++;

            if (Hazards.standingHurts(world, target)) {
                inFire++;
            }
            if (standable(world, target)) {
                body.refreshPositionAndAngles(target.getX() + 0.5, target.getY(),
                        target.getZ() + 0.5, 0f, 0f);
            } else {
                midair++;
            }
        }

        if (sent == 0) {
            complaints.add(who + ": не послан никуда ни разу — проверка ничего не проверила");
        }
        if (midair > 0) {
            complaints.add(who + ": " + midair + " раз из " + sent
                    + " послан туда, где человек стоять не может");
        }
        if (inFire > 0) {
            complaints.add(who + ": " + inFire + " раз из " + sent + " послан в огонь");
        }
        return complaints;
    }

    /**
     * Дать ремеслу работу: поспевший урожай фермеру, выросшее дерево
     * лесорубу.
     * <p>
     * Без этого проверка ничего не проверяет: и поле, и роща приходят
     * из схемы в том состоянии, в котором делать нечего, — грядки уже
     * вскопаны и засеяны, саженцы уже посажены.
     */
    private static void giveWork(ServerWorld world, Building workplace, Schematic schematic) {
        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            BlockState planned = schematic.blockAt(step.paletteIndex());
            BlockPos where = BuildJob.worldPos(workplace, schematic.size(), step.pos());

            if (planned.getBlock() instanceof net.minecraft.block.CropBlock crop) {
                world.setBlockState(where, crop.withAge(crop.getMaxAge()));
            } else if (planned.isOf(Blocks.OAK_SAPLING)) {
                for (int up = 0; up < 4; up++) {
                    world.setBlockState(where.up(up), Blocks.OAK_LOG.getDefaultState());
                }
                world.setBlockState(where.up(4), Blocks.OAK_LEAVES.getDefaultState());
            }
        }
    }

    // --- фаза 0.2: обозы ---

    /**
     * Обоз приходит к колонии игрока и привозит настоящий товар деревни.
     * <p>
     * Исполнение обещания из плана: «привоз станет настоящим обозом,
     * который можно перехватить». Заодно это <b>единственная торговля
     * колонии игрока</b>: своего старейшины у неё нет, и до сих пор
     * продать ей было некому.
     * <p>
     * Проверяется главное: товар обоз берёт <b>со склада деревни</b>,
     * а не из воздуха. Иначе торговля печатала бы вещи, и деревня стала
     * бы бездонным сундуком с ногами.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "caravan")
    public void caravanBringsTheVillageGoodsNotThinAir(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(20, 2, 2));
        List<BlockPos> floor = new ArrayList<>();

        try {
            for (int x = -2; x <= 26; x++) {
                for (int z = -4; z <= 8; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
            world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
            manager.add(village);

            Settlement colony = colonyWithBuilder(world, manager, colonyAt);

            // У деревни есть чем торговать: хлеб и монета.
            Warehouse store = Warehouse.of(world, village);
            store.add(new ItemStack(Items.BREAD, 40));
            Coins.earn(store.coins(), 64);

            int breadBefore = Warehouse.of(world, village).count(Items.BREAD);
            int coinBefore = Coins.total(Warehouse.of(world, village).coins());

            // Обоз ходит не каждый день, и у каждой деревни свой день:
            // перебираем дни числом, потому что шесть вызовов в одном тике
            // для мода — один и тот же день.
            Caravan guest = null;
            for (long day = 0; day < 6 && guest == null; day++) {
                Caravans.sendIfDue(world, manager, village, day);
                guest = manager.byId(colony.id()).orElseThrow().visitors()
                        .stream().findFirst().orElse(null);
            }

            if (guest == null) {
                context.throwGameTestException("Деревня с хлебом и монетой не послала обоз "
                        + "ни за шесть дней");
                return;
            }

            if (guest.cargo().isEmpty()) {
                context.throwGameTestException("Обоз пришёл пустым");
            }
            int breadAfter = Warehouse.of(world, village).count(Items.BREAD);
            int coinAfter = Coins.total(Warehouse.of(world, village).coins());
            if (breadAfter >= breadBefore || coinAfter >= coinBefore) {
                context.throwGameTestException("Обоз гружён из воздуха: хлеба на складе было "
                        + breadBefore + ", стало " + breadAfter + "; монеты было " + coinBefore
                        + ", стало " + coinAfter);
            }
            if (guest.purse() <= 0) {
                context.throwGameTestException("Обоз без монеты: покупать ему нечем");
            }
            if (!guest.home().equals(village.id())) {
                context.throwGameTestException("Обоз не помнит, кто его послал");
            }
        } finally {
            manager.all().stream().map(Settlement::id).toList().forEach(manager::remove);
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
            world.setBlockState(colonyAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * У обоза покупают из его телеги, а не со склада деревни.
     * <p>
     * Это то, ради чего обоз и нужен: у него можно скупить всё, и тогда
     * торговать станет нечем до следующего раза. Проверяется, что после
     * сделки товар убыл <b>из телеги</b>.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "caravan")
    public void caravanSellsFromItsOwnCart(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 2, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        manager.add(village);
        UUID player = UUID.randomUUID();
        SimpleInventory hands = new SimpleInventory(36);
        List<ItemStack> spilled = new ArrayList<>();

        try {
            TradeTable.Deal bread = Trading.find(village, Trading.Side.VILLAGE_SELLS,
                    Items.BREAD, 6).orElse(null);
            if (bread == null) {
                context.throwGameTestException("Стол торга норманнов не загрузился");
                return;
            }

            // Телега: хлеб и ничего больше. Склад деревни при этом пуст.
            SimpleInventory cart = new SimpleInventory(27);
            cart.addStack(new ItemStack(Items.BREAD, 12));
            Coins.earn(hands, 8);

            Trading.Outcome bought = Trading.trade(village, player, hands,
                    Warehouse.over(villageAt, cart), Trading.Side.VILLAGE_SELLS, bread,
                    spilled::add);

            if (bought != Trading.Outcome.DONE) {
                context.throwGameTestException("Покупка у обоза отказана: " + bought);
                return;
            }
            if (cart.count(Items.BREAD) != 6) {
                context.throwGameTestException("Из телеги убыло не то: хлеба осталось "
                        + cart.count(Items.BREAD) + " из 12, ожидалось 6");
            }
            if (hands.count(Items.BREAD) != 6) {
                context.throwGameTestException("Игрок не получил хлеба: "
                        + hands.count(Items.BREAD));
            }
            if (Coins.total(cart) <= 0) {
                context.throwGameTestException("Монета за хлеб не легла в телегу");
            }
            if (!spilled.isEmpty()) {
                context.throwGameTestException("Сдача просыпалась: " + spilled);
            }
        } finally {
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Убитый торговец рассыпает товар, и деревня это помнит.
     * <p>
     * Это «перехватить» из плана. Грабёж возможен и наказуем: товар
     * достаётся грабителю, но доверие пославшей деревни падает — и её
     * старейшина перестанет и говорить, и торговать.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "caravan")
    public void robbingTheCaravanCostsTrust(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos standsAt = context.getAbsolutePos(new BlockPos(10, 2, 2));
        List<BlockPos> floor = new ArrayList<>();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Моя", standsAt);
        // Сосед по народу: об ограблении обязаны услышать и свои.
        Settlement kin = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан",
                context.getAbsolutePos(new BlockPos(13, 2, 2)));
        manager.add(village);
        manager.add(colony);
        manager.add(kin);
        UUID thief = UUID.randomUUID();

        try {
            for (int x = 0; x <= 14; x++) {
                for (int z = 0; z <= 4; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            ItemTally cart = new ItemTally();
            cart.add(Registries.ITEM.getId(Items.BREAD), 8);
            Caravan guest = new Caravan(UUID.randomUUID(), village.id(), NORMAN, standsAt,
                    cart, 27, 999);
            manager.update(colony.id(), state -> state.welcome(guest));

            CitizenEntity merchant = CitizenSpawner.spawnPuppet(world, standsAt);
            if (merchant == null) {
                context.throwGameTestException("Торговец не появился");
                return;
            }
            merchant.linkCaravan(colony.id(), guest.id());

            int trustBefore = village.reputationOf(thief);
            Caravans.robbed(world, merchant, thief);

            if (!manager.byId(colony.id()).orElseThrow().visitors().isEmpty()) {
                context.throwGameTestException("Разграбленный обоз всё ещё гостит");
            }
            if (village.reputationOf(thief) >= trustBefore) {
                context.throwGameTestException("Грабёж не стоил доверия: было " + trustBefore
                        + ", стало " + village.reputationOf(thief));
            }

            boolean loot = !world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                    new net.minecraft.util.math.Box(standsAt).expand(4), any -> true).isEmpty();
            if (!loot) {
                context.throwGameTestException("Товар не рассыпался: грабить нечего");
            }

            // Грабёж — поступок, а поступки слышат не только там, где
            // случились: свои пославшей деревни обязаны обидеться тоже.
            if (kin.reputationOf(thief) >= 0) {
                context.throwGameTestException("Свои ограбленной деревни не услышали: у соседа "
                        + kin.reputationOf(thief));
            }
        } finally {
            world.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                            new net.minecraft.util.math.Box(standsAt).expand(6), any -> true)
                    .forEach(net.minecraft.entity.ItemEntity::discard);
            world.getEntitiesByClass(CitizenEntity.class,
                            new net.minecraft.util.math.Box(standsAt).expand(6), any -> true)
                    .forEach(CitizenEntity::discard);
            manager.remove(village.id());
            manager.remove(colony.id());
            manager.remove(kin.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
        }

        context.complete();
    }

    // --- фаза 3: отношения ---

    /**
     * Слух о поступке идёт по всему народу — и к его соседям.
     * <p>
     * Дизайн-документ: «каждое действие меняет несколько уровней матрицы
     * сразу — подарок конкретной деревне слегка поднимает и отношение
     * всего народа». Проверяется ровно это: услугу одной деревне зачтут
     * её своим, а тем, кто на этот народ косится, она слегка не по нраву.
     * <p>
     * И проверяется <b>на трёх деревнях сразу</b>, потому что двумя это
     * правило не отличить от «доверие складывается»: нужны и свои, и чужие.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "diplomacy")
    public void fameSpreadsToKinAndCostsRivals(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        Settlement first = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар",
                context.getAbsolutePos(new BlockPos(2, 2, 2)));
        Settlement kin = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан",
                context.getAbsolutePos(new BlockPos(6, 2, 2)));
        Settlement rival = Settlement.found(MAYA, Owner.AUTONOMOUS, "Ушмаль",
                context.getAbsolutePos(new BlockPos(10, 2, 2)));
        manager.add(first);
        manager.add(kin);
        manager.add(rival);

        try {
            List<Relations.Shift> shifts = Relations.deed(manager, first, player, 60);

            if (first.reputationOf(player) != 60) {
                context.throwGameTestException("Своё деревня не получила: "
                        + first.reputationOf(player) + " вместо 60");
            }
            if (kin.reputationOf(player) != 15) {
                context.throwGameTestException("Свои не услышали: у соседа по народу "
                        + kin.reputationOf(player) + " вместо четверти, то есть 15");
            }
            if (rival.reputationOf(player) != -6) {
                context.throwGameTestException("Чужие не заметили: у майя "
                        + rival.reputationOf(player) + " вместо -6");
            }

            boolean toldAboutNormans = shifts.stream()
                    .anyMatch(shift -> shift.culture().equals(NORMAN)
                            && shift.now() == Standing.KNOWN);
            if (!toldAboutNormans) {
                context.throwGameTestException("Народ сменил ступень, а игроку не сказали: "
                        + shifts);
            }
        } finally {
            manager.remove(first.id());
            manager.remove(kin.id());
            manager.remove(rival.id());
        }

        context.complete();
    }

    /**
     * Подарок принимают, если он деревне нужен, и берут ровно сколько надо.
     * <p>
     * Три правила одним тестом, потому что они об одном подарке: дарить
     * надо то, что деревня скупает; за сутки благодарят один раз; и лишнего
     * из рук не забирают. Последнее — не мелочь: молча взять восемь золотых
     * за те же восемь очков было бы надувательством.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "diplomacy")
    public void giftsMustBeSomethingTheVillageWants(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар",
                context.getAbsolutePos(new BlockPos(2, 2, 2)));
        manager.add(village);

        try {
            // Своего хлеба норманнам не надо: они его сами продают.
            ItemStack bread = new ItemStack(Items.BREAD, 64);
            Gifts.Outcome refused = Gifts.give(manager, village, player, bread, 5L);
            if (refused.verdict() != Gifts.Verdict.NOT_WANTED) {
                context.throwGameTestException("Норманнский хлеб норманнам приняли как подарок: "
                        + refused.verdict());
            }
            if (bread.getCount() != 64) {
                context.throwGameTestException("Отвергнутый подарок всё равно забрали");
            }

            // А золото деревне нужно всегда — но одной монеты уже довольно.
            ItemStack gold = new ItemStack(ModItems.GOLD_COIN, 8);
            Gifts.Outcome taken = Gifts.give(manager, village, player, gold, 5L);
            if (!taken.accepted()) {
                context.throwGameTestException("Золото не приняли: " + taken.verdict());
                return;
            }
            if (taken.trust() != Gifts.MOST_PER_DAY) {
                context.throwGameTestException("За золотой дали " + taken.trust()
                        + " доверия вместо суточного предела " + Gifts.MOST_PER_DAY);
            }
            if (gold.getCount() != 7) {
                context.throwGameTestException("Из рук взяли лишнее: осталось "
                        + gold.getCount() + " золотых вместо семи");
            }
            if (village.reputationOf(player) != Gifts.MOST_PER_DAY) {
                context.throwGameTestException("Доверие не выросло: "
                        + village.reputationOf(player));
            }

            // Второй раз в тот же день — нет.
            Gifts.Outcome again = Gifts.give(manager, village, player,
                    new ItemStack(ModItems.GOLD_COIN, 8), 5L);
            if (again.verdict() != Gifts.Verdict.ALREADY_TODAY) {
                context.throwGameTestException("Второй подарок за день приняли: "
                        + again.verdict());
            }

            // А назавтра — снова да.
            Gifts.Outcome tomorrow = Gifts.give(manager, village, player,
                    new ItemStack(ModItems.GOLD_COIN, 8), 6L);
            if (!tomorrow.accepted()) {
                context.throwGameTestException("Назавтра подарок не приняли: "
                        + tomorrow.verdict());
            }
        } finally {
            manager.remove(village.id());
        }

        context.complete();
    }

    /**
     * Народ судит об игроке по всем своим деревням, а не по одной.
     * <p>
     * Взвешенно: слово города весит больше слова хутора. И только по тем,
     * кто игрока знает: деревня, которой он в глаза не видел, мнения не
     * имеет, и подмешивать её ноль значило бы наказывать игрока за
     * существование деревень, до которых он не дошёл.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "diplomacy")
    public void peopleJudgePlayerByAllTheirVillages(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        Settlement hamlet = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Хутор",
                context.getAbsolutePos(new BlockPos(2, 2, 6)));
        Settlement town = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Кан",
                context.getAbsolutePos(new BlockPos(6, 2, 6)));
        Settlement stranger = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Дальняя",
                context.getAbsolutePos(new BlockPos(10, 2, 6)));
        manager.add(hamlet);
        manager.add(town);
        manager.add(stranger);

        try {
            manager.update(town.id(), state -> state.setLevel(SettlementLevel.TOWN));
            manager.update(hamlet.id(), state -> state.addReputation(player, 0));
            manager.update(town.id(), state -> state.addReputation(player, 80));

            // Хутор весит один, город — три: (0 + 240) / 4 = 60.
            int trust = Relations.trustOfPeople(manager, NORMAN, player);
            if (trust != 60) {
                context.throwGameTestException("Средний счёт народа " + trust
                        + " вместо взвешенных 60");
            }
            if (Relations.standingOfPeople(manager, NORMAN, player) != Standing.FRIEND) {
                context.throwGameTestException("Народ, где один город считает другом, "
                        + "а хутор молчит, не признал друга");
            }
            if (stranger.knows(player)) {
                context.throwGameTestException("Незнакомая деревня откуда-то знает игрока");
            }
        } finally {
            manager.remove(hamlet.id());
            manager.remove(town.id());
            manager.remove(stranger.id());
        }

        context.complete();
    }

    /**
     * Экран старейшины показывает и народ, и цену подарка в руке.
     * <p>
     * И проверяется, что снимок <b>кодируется</b>: он едет на клиент, и
     * поле, которое не кодируется, роняет разговор целиком. Однажды так
     * и вышло — с грузом обоза, — и стоило это всех модульных тестов
     * поселения.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "diplomacy")
    public void elderScreenShowsThePeopleAndTheGift(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар",
                context.getAbsolutePos(new BlockPos(2, 2, 10)));
        manager.add(village);

        try {
            QuestView view = QuestNet.viewOf(manager, village, player,
                    new SimpleInventory(36), Villages.ELDER,
                    Warehouse.of(world, village), Optional.empty(),
                    new ItemStack(Items.IRON_INGOT, 64), 4L).orElseThrow();

            if (!"villagepax.culture.norman".equals(view.people().name())) {
                context.throwGameTestException("Народ деревни доехал до экрана как "
                        + view.people().name());
            }
            if (view.people().neighbours().isEmpty()) {
                context.throwGameTestException("Второй народ в мире есть, а в соседях его нет");
            }

            QuestView.Gift gift = view.gift().orElse(null);
            if (gift == null) {
                context.throwGameTestException("Железо в руке, а карточки подарка нет");
                return;
            }
            if (!gift.ready() || gift.trust() != Gifts.MOST_PER_DAY) {
                context.throwGameTestException("Стопка железа стоит " + gift.trust()
                        + " доверия и приговор " + gift.verdict());
            }

            QuestView back = QuestView.CODEC
                    .parse(NbtOps.INSTANCE, QuestView.CODEC
                            .encodeStart(NbtOps.INSTANCE, view).result().orElseThrow())
                    .result().orElse(null);
            if (back == null) {
                context.throwGameTestException("Снимок разговора не пережил кодирования");
                return;
            }
            if (!back.people().standing().equals(view.people().standing())
                    || back.gift().orElseThrow().trust() != gift.trust()) {
                context.throwGameTestException("Снимок вернулся из кодека другим");
            }
        } finally {
            manager.remove(village.id());
        }

        context.complete();
    }


    // --- фаза 3: набеги и стража ---

    /**
     * Разбой кончается отрядом у ворот — и не сразу, и не каждый день.
     * <p>
     * Три правила одной проверкой, потому что они об одном решении:
     * терпение у деревни кончается на определённом счёте, второй отряд
     * не посылают, пока стоит первый, и после набега колония отдыхает.
     * Последнее — не поблажка, а условие играбельности: без остывания
     * разбойник получил бы отряд каждое утро и не смог бы ни отстроиться,
     * ни помириться.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid")
    public void patienceEndsAndAWarBandIsSent(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(16, 2, 16));
        List<BlockPos> floor = new ArrayList<>();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар",
                context.getAbsolutePos(new BlockPos(1, 2, 1)));
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(village);
        manager.add(colony);

        try {
            // Земля по кругу: отряд собирается у края колонии, и ему нужно,
            // на чём стоять.
            for (int x = 2; x <= 30; x++) {
                for (int z = 2; z <= 30; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            // Пока доверие терпимое, никто не идёт.
            manager.update(village.id(), state -> state.addReputation(player, -20));
            Raids.sendIfDue(world, manager, village, 10L);
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Отряд вышел на доверии -20: терпения нет вовсе");
            }

            // А ниже предела — идут, и числом по обиде.
            manager.update(village.id(), state -> state.addReputation(player, -40));
            Raids.sendIfDue(world, manager, village, 10L);

            WarParty party = manager.byId(colony.id()).orElseThrow().siege().orElse(null);
            if (party == null) {
                context.throwGameTestException("Доверие -60, а отряда нет");
                return;
            }
            if (party.fighters() != Raids.fightersFor(-60)) {
                context.throwGameTestException("Бойцов " + party.fighters() + " вместо "
                        + Raids.fightersFor(-60));
            }
            if (party.arrivesOn() != 11L) {
                context.throwGameTestException("Отряд приходит в день " + party.arrivesOn()
                        + " вместо назавтра: о набеге предупреждают заранее");
            }
            if (!party.home().equals(village.id())) {
                context.throwGameTestException("Отряд не помнит, кто его послал");
            }

            // Второго отряда, пока стоит первый, не бывает.
            Raids.sendIfDue(world, manager, village, 11L);
            if (!manager.byId(colony.id()).orElseThrow().siege().orElseThrow().id()
                    .equals(party.id())) {
                context.throwGameTestException("Пока стоял один отряд, послали второй");
            }

            // И после набега колония отдыхает.
            manager.update(colony.id(), Settlement::liftSiege);
            Raids.sendIfDue(world, manager, village, 12L);
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Набег на следующий же день после набега: "
                        + "колония не успевает ни отстроиться, ни помириться");
            }

            Raids.sendIfDue(world, manager, village, 10L + Raids.COOLDOWN_DAYS);
            if (manager.byId(colony.id()).orElseThrow().siege().isEmpty()) {
                context.throwGameTestException("Отдых кончился, а обида осталась — "
                        + "отряд должен был выйти снова");
            }
        } finally {
            manager.byId(colony.id()).flatMap(Settlement::siege)
                    .ifPresent(one -> Raids.bodiesOf(world, one)
                            .forEach(CitizenEntity::discard));
            manager.remove(village.id());
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Отряд встаёт телами в обещанный день — вооружённый и подписанный.
     * <p>
     * До этого дня он только предупреждение в чате, и это то же правило,
     * на котором стоят обозы: нет тела — нет события. Проверяется и то,
     * что тела <b>не</b> появляются раньше срока: иначе предупреждение
     * теряло бы смысл, а игрок просыпался бы уже в бою.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid")
    public void warBandStandsUpOnTheDayItPromised(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 2, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(10, 2, 6));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(colony);
        // Отряд объявляется до try: за своими телами убирать надо и тогда,
        // когда проверка упала на первом же утверждении.
        WarParty party = new WarParty(UUID.randomUUID(), UUID.randomUUID(), NORMAN,
                musters, 2, 20L, 21L);

        try {
            for (int x = 2; x <= 14; x++) {
                for (int z = 2; z <= 10; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            manager.update(colony.id(), state -> state.besiege(party, 19L));

            // Осада обязана пережить сохранение: она лежит в поселении,
            // а поселение читается с диска. Кодек, который её теряет,
            // проявился бы только у игрока и только после перезахода —
            // отряд исчезал бы за ночь, и никто бы не понял почему.
            Settlement reread = Settlement.CODEC.parse(NbtOps.INSTANCE,
                            Settlement.CODEC.encodeStart(NbtOps.INSTANCE, colony)
                                    .result().orElseThrow())
                    .result().orElse(null);
            WarParty saved = reread == null ? null : reread.siege().orElse(null);
            if (saved == null || !saved.id().equals(party.id())
                    || saved.fighters() != party.fighters()
                    || saved.arrivesOn() != party.arrivesOn()
                    || !saved.musters().equals(party.musters())) {
                context.throwGameTestException("Осада не пережила запись на диск: " + saved);
            }
            if (reread != null && reread.lastRaid() != 19L) {
                context.throwGameTestException("День набега не сохранился: " + reread.lastRaid());
            }

            // Днём раньше — только слово.
            Raids.watch(world, manager, 19L);
            if (!Raids.bodiesOf(world, party).isEmpty()) {
                context.throwGameTestException("Отряд встал раньше обещанного дня");
            }

            Raids.watch(world, manager, 20L);
            List<CitizenEntity> fighters = Raids.bodiesOf(world, party);
            if (fighters.size() != 2) {
                context.throwGameTestException("Тел у отряда " + fighters.size() + " вместо двух");
                return;
            }
            for (CitizenEntity fighter : fighters) {
                if (!fighter.isRaider() || !fighter.isFighter()) {
                    context.throwGameTestException("Боец не считает себя налётчиком");
                }
                if (!fighter.raidHost().filter(colony.id()::equals).isPresent()) {
                    context.throwGameTestException("Боец не знает, к кому пришёл");
                }
                if (fighter.getMainHandStack().isEmpty()) {
                    context.throwGameTestException("Боец пришёл с пустыми руками");
                }
            }

            // Второй осмотр в тот же день новых не поднимает.
            Raids.watch(world, manager, 20L);
            if (Raids.bodiesOf(world, party).size() != 2) {
                context.throwGameTestException("Отряд удвоился на втором осмотре: "
                        + Raids.bodiesOf(world, party).size());
            }

            // А когда срок вышел — уходят, и запись снимается.
            Raids.watch(world, manager, 22L);
            if (!Raids.bodiesOf(world, party).isEmpty()
                    || manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Отряд не ушёл, когда вышел срок");
            }
        } finally {
            Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Павший боец убывает из отряда, а последний снимает осаду.
     * <p>
     * Проверяется <b>настоящей смертью</b>, а не вызовом учёта: тело
     * убирается из мира не там, где его убили, — смерть моба идёт через
     * анимацию, — и весь смысл проверки в том, что путь от удара до
     * записи отряда действительно связан.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid", tickLimit = 200)
    public void fallenFighterThinsTheBand(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 2, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(9, 2, 6));
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 2; x <= 12; x++) {
            for (int z = 2; z <= 10; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }

        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Моя", centre);
        manager.add(colony);

        WarParty party = new WarParty(UUID.randomUUID(), UUID.randomUUID(), NORMAN,
                musters, 1, 30L, 31L);
        manager.update(colony.id(), state -> state.besiege(party, 29L));
        Raids.watch(world, manager, 30L);

        List<CitizenEntity> fighters = Raids.bodiesOf(world, party);
        if (fighters.size() != 1) {
            cleanUpRaid(world, manager, colony, party, floor);
            context.throwGameTestException("Отряд не встал: тел " + fighters.size());
            return;
        }
        fighters.get(0).kill();

        context.runAtTick(100, () -> {
            try {
                if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                    context.throwGameTestException("Последний боец пал, а осада всё стоит");
                }
            } finally {
                cleanUpRaid(world, manager, colony, party, floor);
            }
            context.complete();
        });
    }

    /**
     * Страж идёт на налётчика, а без него обходит колонию.
     * <p>
     * Обе половины разом, потому что вторая без первой — просто гуляющий
     * житель с мечом, а первая без второй — часовой, стоящий на месте.
     * И проверяется <b>решение стратегии</b>, а не бой: драться умеет
     * ванильная тактика, и проверять её заново незачем.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard")
    public void guardGoesForTheRaiderAndPatrolsOtherwise(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos far = context.getAbsolutePos(new BlockPos(12, 2, 2));
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 6; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen watchman = Citizen.newborn("Turold", "le Veilleur", NORMAN, Gender.MALE);
        watchman.setProfession(Villages.GUARD);
        manager.update(colony.id(), state -> state.addCitizen(watchman));

        CitizenEntity body = CitizenSpawner.spawnBody(world, colony, watchman);
        CitizenEntity raider = CitizenSpawner.spawnPuppet(world,
                context.getAbsolutePos(new BlockPos(5, 2, 2)));

        try {
            if (body == null || raider == null) {
                context.throwGameTestException("Тела не появились");
                return;
            }
            UUID watched = UUID.randomUUID();
            raider.linkRaid(colony.id(), watched);
            rememberRaid(manager, colony, watched, raider.getBlockPos(), 1);

            GuardJob guard = new GuardJob();
            WorkContext seen = new WorkContext(world, manager, colony, watchman, body);
            BlockPos goes = guard.tick(seen).orElse(null);

            if (body.getTarget() != raider) {
                context.throwGameTestException("Страж не взял налётчика на прицел: "
                        + body.getTarget());
            }
            if (goes == null || goes.getSquaredDistance(raider.getBlockPos()) > 1) {
                context.throwGameTestException("Страж пошёл не на врага, а в " + goes);
            }
            if (body.getMainHandStack().isEmpty()) {
                context.throwGameTestException("Страж без оружия");
            }

            // Врага убрали — страж отпускает цель и идёт в обход,
            // к самому дальнему зданию колонии.
            raider.discard();
            manager.update(colony.id(), state -> state.addBuilding(
                    Building.planned(TOWN_HALL_TYPE, far, BlockRotation.NONE)));

            BlockPos patrol = guard.tick(seen).orElse(null);
            if (body.getTarget() != null) {
                context.throwGameTestException("Страж гонится за тем, кого нет");
            }
            if (patrol == null || patrol.getSquaredDistance(far) > 1) {
                context.throwGameTestException("Обход ведёт не к дальнему краю, а в " + patrol);
            }
        } finally {
            // Только свои тела, и по ссылке, а не по радиусу. Проверки
            // одного батча делят один мир, и уборка «всё живое в тридцати
            // блоках» однажды уже убрала <b>чужого</b> бойца — того, что
            // умирал в соседней проверке, — и та упала на ровном месте.
            if (body != null) {
                body.discard();
            }
            if (raider != null) {
                raider.discard();
            }
            cleanUpVillage(world, manager, colony, hall, floor);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Налётчик берёт на прицел жителя осаждённой колонии — и только его.
     * <p>
     * Самая важная проверка всего набега: в ней вся его ставка. Отряд,
     * который приходит и стоит, — это декорация, а отряд, который бьёт
     * кого попало, включая своих, — это поломка. Проверяется на живой
     * тактике, прогоном тиков: цели ставит ванильный поиск врага, и
     * убедиться надо именно в том, что <b>предикат</b> в нём написан
     * верно, а не в том, что предикат существует.
     * <p>
     * Своим батчем, а не вместе с остальными набегами: проверки одного
     * батча идут в одном мире одновременно и в десятках блоков друг от
     * друга, а страж чует налётчика на сорок восемь блоков. Соседняя
     * проверка так однажды и увела чужого бойца.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid_fight", tickLimit = 200)
    public void raiderGoesForTheColonysPeople(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 6; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen townsman = colony.citizens().get(0);
        CitizenEntity peaceful = CitizenSpawner.spawnBody(world, colony, townsman);
        CitizenEntity raider = CitizenSpawner.spawnPuppet(world,
                context.getAbsolutePos(new BlockPos(6, 2, 3)));
        CitizenEntity brother = CitizenSpawner.spawnPuppet(world,
                context.getAbsolutePos(new BlockPos(7, 2, 3)));

        if (peaceful == null || raider == null || brother == null) {
            cleanUpFight(world, manager, colony, hall, floor, peaceful, raider, brother);
            context.throwGameTestException("Тела не появились");
            return;
        }
        UUID party = UUID.randomUUID();
        raider.linkRaid(colony.id(), party);
        brother.linkRaid(colony.id(), party);
        // Осаду поселение обязано помнить: тела отряда, о котором нет
        // записи, мод убирает сам — и это не придирка проверки, а правило
        // игры. Состояние «бойцы есть, осады нет» в мире не встречается.
        rememberRaid(manager, colony, party, raider.getBlockPos(), 2);

        context.runAtTick(100, () -> {
            try {
                if (raider.getTarget() != peaceful) {
                    context.throwGameTestException("Налётчик не взял жителя на прицел, а взял "
                            + raider.getTarget());
                }
                if (brother.getTarget() == raider) {
                    context.throwGameTestException("Отряд перерезал сам себя: боец пошёл "
                            + "на своего");
                }
                if (!peaceful.isFighter() && peaceful.getTarget() != null) {
                    context.throwGameTestException("Мирный житель полез в драку: "
                            + peaceful.getTarget());
                }
            } finally {
                cleanUpFight(world, manager, colony, hall, floor, peaceful, raider, brother);
            }
            context.complete();
        });
    }

    /** Убрать за схваткой: только свои тела, запись колонии и пол. */
    private static void cleanUpFight(ServerWorld world, SettlementManager manager,
                                     Settlement colony, BlockPos hall, List<BlockPos> floor,
                                     CitizenEntity... bodies) {
        for (CitizenEntity body : bodies) {
            if (body != null) {
                body.discard();
            }
        }
        cleanUpVillage(world, manager, colony, hall, floor);
        world.setBlockState(hall, Blocks.AIR.getDefaultState());
    }


    /**
     * Набег бьёт стены — и разорённое чинится тем же билдером.
     * <p>
     * Проверяется вся дуга обещания разом: отряд выбивает блоки, здание
     * становится повреждённым, билдер восстанавливает его по той же схеме
     * и <b>за материалы со склада</b>. Ломать выгодно ровно тем, что
     * чинить платно.
     * <p>
     * И проверяется, чего не ломают: блок ратуши с его хранилищем стоит
     * как стоял. Сломанный сундук высыпал бы игроку под ноги то, что
     * отряд пришёл унести, а снос ратуши — это уже захват поселения,
     * которого мод не умеет.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "siege")
    public void raidWrecksTheWallsAndTheBuilderMendsThem(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));
        BlockPos musters = context.getAbsolutePos(new BlockPos(3, 9, 3));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);
        WarParty party = new WarParty(UUID.randomUUID(), UUID.randomUUID(), NORMAN,
                musters, 1, 40L, 41L);

        try {
            stockFor(world, colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не построилось до набега");
                return;
            }

            // Сундуки склада считаем до набега: их обязано остаться столько же.
            int chestsBefore = containersIn(world, site, schematic);
            if (chestsBefore == 0) {
                context.throwGameTestException("В схеме ратуши не оказалось сундуков — "
                        + "проверять «чего не ломают» не на чем");
            }

            manager.update(colony.id(), state -> state.besiege(party, 39L));
            Raids.watch(world, manager, 40L);

            // И спрашиваем правило прямо: в списке «что можно выбить»
            // хранилищ быть не должно. Шесть блоков из сотни почти никогда
            // не попадут именно в сундук, и снятый запрет иначе прошёл бы
            // незамеченным.
            for (BlockPos spot : Siege.breakable(world, site, schematic)) {
                if (world.getBlockEntity(spot) != null) {
                    context.throwGameTestException("Сундук попал в список того, что ломают: "
                            + spot.toShortString());
                    break;
                }
            }

            if (containersIn(world, site, schematic) != chestsBefore) {
                context.throwGameTestException("Отряд разбил сундук: было " + chestsBefore
                        + ", стало " + containersIn(world, site, schematic)
                        + ". Содержимое высыпалось бы игроку под ноги");
            }

            Building after = manager.byId(colony.id()).orElseThrow()
                    .building(site.id()).orElseThrow();
            if (after.progress() != BuildProgress.DAMAGED) {
                context.throwGameTestException("Набег прошёл мимо здания: " + after.progress());
            }
            if (!world.getBlockState(hall).isOf(ModBlocks.TOWN_HALL)) {
                context.throwGameTestException("Отряд сломал ратушу — это уже захват, "
                        + "а не разорение");
            }

            int holes = 0;
            for (BuildStep step : schematic.plan().steps()) {
                if (step.placesBlock() && world.getBlockState(
                        BuildJob.worldPos(site, schematic.size(), step.pos())).isAir()) {
                    holes++;
                }
            }
            if (holes == 0) {
                context.throwGameTestException("Здание помечено разорённым, а стены целы");
            }

            // Чинится тем же билдером и за материалы: завозим ровно на пробоины.
            stockFor(world, colony, schematic);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Разорённое не починилось");
            }
            for (BuildStep step : schematic.plan().steps()) {
                if (step.placesBlock() && world.getBlockState(
                        BuildJob.worldPos(site, schematic.size(), step.pos())).isAir()) {
                    context.throwGameTestException("После ремонта осталась пробоина");
                    break;
                }
            }
        } finally {
            Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            demolish(world, site, schematic);
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Уцелевшие уходят с добычей, и добыча доезжает до дома.
     * <p>
     * Иначе набег был бы чистым уроном без приобретения — а деревня
     * посылает людей не затем, чтобы сжечь чужое, и не в последнюю
     * очередь затем, чтобы взять своё.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "siege")
    public void survivorsCarryTheLootHome(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos village = context.getAbsolutePos(new BlockPos(10, 2, 2));
        BlockPos musters = context.getAbsolutePos(new BlockPos(5, 2, 2));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        world.setBlockState(village, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement home = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", village);
        manager.add(home);

        WarParty party = new WarParty(UUID.randomUUID(), home.id(), NORMAN, musters,
                1, 50L, 51L);

        try {
            for (int x = 0; x <= 12; x++) {
                for (int z = 0; z <= 4; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 64));
            int before = Warehouse.of(world, colony).count(Items.BREAD);

            manager.update(colony.id(), state -> state.besiege(party, 49L));
            Raids.watch(world, manager, 50L);
            if (Raids.bodiesOf(world, party).isEmpty()) {
                context.throwGameTestException("Отряд не встал — некому уносить");
                return;
            }

            // Срок вышел: уцелевшие уходят.
            Raids.watch(world, manager, 52L);

            int after = Warehouse.of(world, colony).count(Items.BREAD);
            if (after >= before) {
                context.throwGameTestException("Со склада не унесли ничего: было " + before
                        + ", стало " + after);
            }
            if (Warehouse.of(world, home).count(Items.BREAD) != before - after) {
                context.throwGameTestException("Добыча не доехала до деревни: у неё "
                        + Warehouse.of(world, home).count(Items.BREAD) + ", а унесли "
                        + (before - after));
            }
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Отряд ушёл, а осада осталась");
            }
        } finally {
            Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            manager.remove(home.id());
            cleanUpVillage(world, manager, colony, hall, floor);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            world.setBlockState(village, Blocks.AIR.getDefaultState());
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


    // --- проходимость ---

    /**
     * Деревня не лезет на скалу: место выбирается там, куда можно дойти.
     * <p>
     * Написано по настоящей деревне из игры заказчика: дом и ферма встали
     * <b>на восемнадцать блоков выше</b> ратуши в десяти шагах от неё.
     * Стройка встала на середине, потому что билдер туда не добирался,
     * а игрок сказал: «строится высоко и не пройти».
     * <p>
     * Проверяется само правило, а не расстановка: правило — это одна
     * дробь, и ошибиться в ней можно молча, а поймать за руку расстановку
     * нельзя без настоящего рельефа.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void villageDoesNotClimbCliffs(TestContext context) {
        BlockPos centre = new BlockPos(0, 69, 0);

        if (Raising.isWalkableFrom(centre, new BlockPos(7, 87, -7))) {
            context.throwGameTestException("Полка на восемнадцать блоков выше в десяти шагах "
                    + "считается годной — это та самая деревня на скале");
        }
        if (!Raising.isWalkableFrom(centre, new BlockPos(7, 71, 0))) {
            context.throwGameTestException("Холмик в два блока у самой ратуши объявлен "
                    + "непроходимым: так деревню не построить нигде");
        }
        if (!Raising.isWalkableFrom(centre, new BlockPos(35, 78, 0))) {
            context.throwGameTestException("Пологий склон — блок подъёма на три шага — "
                    + "должен считаться проходимым");
        }
        if (Raising.isWalkableFrom(centre, new BlockPos(35, 88, 0))) {
            context.throwGameTestException("Подъём круче одного блока на три шага "
                    + "проходимым не считается");
        }

        context.complete();
    }

    /**
     * Готовый дом стоит на земле, а не на сваях из воздуха.
     * <p>
     * Схема кладётся от своего пола, и на склоне у дома с одной стороны
     * оставалась пустота: ни зайти, ни подойти. Теперь билдер подводит
     * опору — из излишков и не глубже склона.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void finishedBuildingStandsOnTheGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = loadedTownHall(context);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 2, 6));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, BlockRotation.NONE);

        try {
            // Земля под зданием — но с провалом под одним углом: ровно то,
            // что бывает на склоне.
            Vec3i size = schematic.size();
            for (int dx = 0; dx < size.getX(); dx++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    BlockPos under = anchor.add(dx, -1, dz);
                    boolean hole = dx >= size.getX() - 2;
                    world.setBlockState(under, hole ? Blocks.AIR.getDefaultState()
                            : Blocks.STONE.getDefaultState());
                    floor.add(under);
                    if (hole) {
                        BlockPos bottom = under.down(2);
                        world.setBlockState(bottom, Blocks.STONE.getDefaultState());
                        floor.add(bottom);
                    }
                }
            }

            // Вдвое больше плана: подсыпка идёт только из излишков.
            stockFor(world, colony, schematic);
            stockFor(world, colony, schematic);

            if (BuildJob.advance(world, manager, colony.id(), site.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Здание не достроилось");
                return;
            }

            int propped = 0;
            for (int dx = size.getX() - 2; dx < size.getX(); dx++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    if (!world.getBlockState(anchor.add(dx, -1, dz)).isAir()) {
                        propped++;
                    }
                }
            }
            if (propped == 0) {
                context.throwGameTestException("Под домом всё ещё пусто: он висит на воздухе");
            }
        } finally {
            demolish(world, site, schematic);
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Улица спускается с уступа ступенью, а не обрывается на нём.
     * <p>
     * До этой правки дорожка шла только по существующей земле и на любом
     * уступе выше двух блоков просто кончалась: дом на полке оставался
     * без подхода. Игрок сказал про это «не пройти ни к зданиям, ни
     * к фермам».
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void streetStepsDownALedge(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic schematic = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 1, 2));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(10, 4, 2));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            // Нижняя площадка у ратуши и верхняя полка под домом: между
            // ними уступ в три блока — ровно тот, на котором улица рвалась.
            for (int x = 0; x <= 18; x++) {
                for (int z = 0; z <= 10; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, x < 8 ? 0 : 3, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    ground.add(at);
                }
            }
            stockFor(world, colony, schematic);
            stockFor(world, colony, schematic);
            BuildJob.advance(world, manager, colony.id(), site.id(), 10_000);

            List<BlockPos> route = Roads.route(world, colony, site);
            if (route.isEmpty()) {
                context.throwGameTestException("Улицы нет вовсе");
                return;
            }

            int drops = 0;
            for (int step = 1; step < route.size(); step++) {
                int fall = route.get(step - 1).getY() - route.get(step).getY();
                if (fall > 1) {
                    context.throwGameTestException("Улица падает на " + fall
                            + " блока разом: по такой лестнице не спуститься");
                }
                if (fall == 1) {
                    drops++;
                }
            }
            if (drops == 0) {
                context.throwGameTestException("Уступ в три блока улица не заметила: "
                        + "она идёт по одной высоте и обрывается");
            }
        } finally {
            demolish(world, site, schematic);
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Опустевшая колония оживает, а не остаётся руиной навсегда.
     * <p>
     * Найдено в сохранениях заказчика: колонии с <b>нулём жителей</b>
     * и вечно недостроенным домом. Умерли все — кровать строить стало
     * некому, а без кровати никто не приходил. Выхода не было ни одного,
     * и игрок сказал про это «строить здания не могу».
     * <p>
     * Теперь первому кровать не нужна — он ночует в ратуше, как и самый
     * первый житель при основании. А второму нужна: иначе колония росла бы
     * в чистом поле, и дома были бы не нужны вовсе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void desertedColonyComesBackToLife(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));

        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Пустая", hall);
        manager.add(colony);

        try {
            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 16));

            Citizen first = Housing.welcomeNewcomer(world, colony, new java.util.Random(7))
                    .orElse(null);
            if (first == null) {
                context.throwGameTestException("В пустую колонию с едой никто не пришёл — "
                        + "это тупик без выхода");
                return;
            }
            if (first.profession().isEmpty()) {
                context.throwGameTestException("Пришедший без ремесла: строить снова некому");
            }

            // А второму кровать уже нужна: домов в колонии нет.
            if (Housing.welcomeNewcomer(world, colony, new java.util.Random(7)).isPresent()) {
                context.throwGameTestException("Второй пришёл в колонию без кроватей — "
                        + "тогда дома не нужны вовсе");
            }
        } finally {
            world.getEntitiesByClass(CitizenEntity.class,
                            new net.minecraft.util.math.Box(hall).expand(16), any -> true)
                    .forEach(CitizenEntity::discard);
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Колония без строителя говорит об этом, а не молчит.
     * <p>
     * Мод умел жаловаться только устами самого билдера — «не хватает
     * камня». Если билдера нет, жаловаться было некому, и игрок сидел
     * над недостроенным домом без единого объяснения. Это и есть его
     * «строить здания не могу», вид второй.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void colonyWithoutABuilderSaysSo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = plan(colony, context.getAbsolutePos(new BlockPos(8, 2, 2)),
                BlockRotation.NONE);

        try {
            if (BuilderJob.nobodyBuilds(colony)) {
                context.throwGameTestException("Строитель на месте, а мод считает, "
                        + "что строить некому");
            }

            // Сняли ремесло — и теперь стройка действительно брошена.
            colony.citizens().forEach(citizen -> citizen.setProfession(null));
            if (!BuilderJob.nobodyBuilds(colony)) {
                context.throwGameTestException("Строителя нет, стройка стоит, "
                        + "а мод молчит — ровно на это и жаловался игрок");
            }

            // А без стройки и жаловаться не на что.
            site.setProgress(BuildProgress.DONE);
            if (BuilderJob.nobodyBuilds(colony)) {
                context.throwGameTestException("Всё достроено, а мод всё равно зовёт строителя");
            }
        } finally {
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Колония живёт неделю сама: растёт, кормится и не вымирает.
     * <p>
     * Написано после разбора сохранений заказчика, где нашлись колонии
     * с <b>нулём жителей</b> и вечно недостроенным домом. Каждая отдельная
     * механика — еда, кровати, приток, ферма — была проверена и работала;
     * не был проверен только <b>ход времени в целом</b>, а рушится именно он.
     * <p>
     * Это не проверка одного правила, а лакмус: семь суточных смен подряд
     * с работой фермера между ними. Если хоть одно звено цепи «поле →
     * склад → еда → новый житель» порвётся, колония не вырастет — и здесь
     * это будет видно сразу, а не через неделю у игрока.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "week", tickLimit = 400)
    public void colonyLivesAWeekOnItsOwn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(0, 8, 0));
        BlockPos farmAt = context.getAbsolutePos(new BlockPos(0, 8, 8));

        BlockPos secondAt = context.getAbsolutePos(new BlockPos(10, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, houseAt, HOUSE_TYPE, BlockRotation.NONE);
        Building second = plan(colony, secondAt, HOUSE_TYPE, BlockRotation.NONE);
        Building farm = plan(colony, farmAt, FARM_TYPE, BlockRotation.NONE);

        try {
            // Дом и ферма уже стоят: проверяется жизнь колонии, а не стройка.
            stockFor(world, colony, housePlan);
            if (BuildJob.advance(world, manager, colony.id(), house.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не встал: проверять неделю не на чем");
                return;
            }
            // Второй дом — чтобы колонии было куда расти: в одном доме
            // две кровати, а жителей и так двое.
            stockFor(world, colony, housePlan);
            if (BuildJob.advance(world, manager, colony.id(), second.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Второй дом не встал");
                return;
            }

            stockFor(world, colony, farmPlan);
            BuildJob.Outcome raised = BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000);
            if (raised != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ферма не встала: " + raised + ", шаг "
                        + farm.nextStep() + " из " + farmPlan.plan().steps().size()
                        + ", не хватает " + Materials.shortfall(farmPlan, farm,
                        farmPlan.plan().steps().size())
                        + ", на складе " + Warehouse.of(world, colony).tally().contents());
                return;
            }

            // Начальный запас еды — как у игрока, который принёс мешок
            // моркови и ушёл по делам.
            Warehouse.of(world, colony).add(new ItemStack(Items.CARROT, 8));
            Housing.assignBeds(world, colony);
            Workplaces.assign(world, colony);

            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, farmAt.up());
            // Телом обзаводятся все: у кого его нет, тот не ест и не работает.
            for (Citizen citizen : colony.citizens()) {
                if (bodyOf(world, colony, citizen) == null) {
                    citizen.setPosition(Vec3d.ofBottomCenter(hall.up()));
                    CitizenSpawner.spawnBody(world, colony, citizen);
                }
            }
            Workplaces.assign(world, colony);
            int before = colony.population();

            for (int day = 0; day < 7; day++) {
                // Поле поспевает к утру: солнце в проверке не светит.
                for (BlockPos plot : FarmJob.plots(farm)) {
                    if (world.getBlockState(plot).getBlock() instanceof CropBlock crop) {
                        world.setBlockState(plot, crop.withAge(crop.getMaxAge()));
                    }
                }
                runWork(world, manager, colony, farmer, 6, Schedule.MORNING_WORK);

                // Обедают все: житель ест решением, а решение бывает
                // только у того, кого тикают. Без этого колония голодает
                // при полном складе — и первая же неделя это показала.
                for (Citizen citizen : List.copyOf(colony.citizens())) {
                    if (bodyOf(world, colony, citizen) != null) {
                        runWork(world, manager, colony, citizen, 3, Schedule.MEAL);
                    }
                }
                Needs.newDay(world, manager, colony);
            }

            if (colony.population() <= before) {
                context.throwGameTestException("За неделю в колонию с кроватями, полем "
                        + "и едой никто не пришёл: жителей было " + before + ", стало "
                        + colony.population());
            }
            if (!Warehouse.of(world, colony).hasAny(ModTags.CITIZEN_FOOD)) {
                context.throwGameTestException("Склад пуст за неделю при работающей ферме: "
                        + "колония кормиться сама не умеет");
            }
            // Один голодный день за неделю — не беда: суточная убыль
            // списывается раньше обеда, и житель встречает утро голодным.
            // А вот три подряд означают, что цепь «поле — склад — еда»
            // где-то порвалась: после четырёх житель жалуется, после
            // шести уходит насовсем.
            for (Citizen citizen : colony.citizens()) {
                if (citizen.discontent() >= 3) {
                    context.throwGameTestException("Житель " + citizen.fullName()
                            + " голодает при работающей ферме: недовольство "
                            + citizen.discontent() + ", на складе "
                            + Warehouse.of(world, colony).tally().contents());
                }
            }
        } finally {
            discardBodies(world, colony);
            demolish(world, house, housePlan);
            demolish(world, second, housePlan);
            demolish(world, farm, farmPlan);
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Колония, которой никто не видит, не голодает.
     * <p>
     * <b>Это и было причиной вымерших колоний заказчика.</b> Сутки шли
     * везде, включая незагруженные чанки: сытость убывала каждый день,
     * а поесть житель может только решением, решение бывает только
     * у тела, а тела в выгруженном чанке нет. Игрок уходил исследовать
     * мир на неделю и возвращался к пустой колонии, не сделав ничего
     * плохого — и нашёл это не он, а проверка «живёт ли колония неделю»,
     * когда у неё за ту же неделю жителей стало меньше.
     * <p>
     * Правило парное к несущему правилу мода: нет тела — нет работы,
     * нет мира — нет суток.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "week")
    public void unseenColonyDoesNotStarve(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        long today = Schedule.dayOf(world.getTimeOfDay());

        // Одно поселение здесь, второе — за сто тысяч блоков, где мира нет.
        BlockPos here = context.getAbsolutePos(new BlockPos(2, 2, 2));
        Settlement near = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Рядом", here);
        Settlement far = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Далёкая",
                new BlockPos(150_000, 64, 150_000));

        Citizen watched = Citizen.newborn("Видимый", "", NORMAN, Gender.MALE);
        Citizen forgotten = Citizen.newborn("Забытый", "", NORMAN, Gender.MALE);
        watched.setSaturation(30);
        forgotten.setSaturation(30);
        near.addCitizen(watched);
        far.addCitizen(forgotten);

        manager.add(near);
        manager.add(far);

        try {
            // «Вчера видели»: иначе смена суток не наступит вовсе.
            near.setLastDay(today - 1);
            far.setLastDay(today - 1);

            WorkTicker.tick(world);

            if (forgotten.saturation() != 30) {
                context.throwGameTestException("У забытой колонии убыла сытость: "
                        + forgotten.saturation() + " вместо 30. Игрок ушёл за горизонт — "
                        + "и вернулся к пустой колонии");
            }
            if (watched.saturation() >= 30) {
                context.throwGameTestException("У колонии под боком сутки не прошли: "
                        + "сытость " + watched.saturation()
                        + ". Тогда колония не голодает никогда, и еда не нужна вовсе");
            }
            // День забытой колонии засчитан — иначе вернувшийся игрок
            // получил бы голод задним числом за всю неделю разом.
            if (far.lastDay() != today) {
                context.throwGameTestException("Забытая колония осталась во вчера: "
                        + far.lastDay() + ". Тогда голод придёт задним числом");
            }
        } finally {
            discardBodies(world, near);
            manager.remove(near.id());
            manager.remove(far.id());
            world.setBlockState(here, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Пульт говорит, что делать дальше, и говорит самое срочное.
     * <p>
     * Главная жалоба на моды этого жанра — и своя, слово в слово:
     * непонятен не механизм, а следующий шаг. Пульт показывает десяток
     * правдивых чисел, и ни одно не отвечает на единственный вопрос
     * новичка. Проверяется лестница срочности: пустая колония важнее
     * голода, голод важнее строителя, строитель важнее кроватей.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void consoleSaysWhatToDoNext(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));

        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Совет", hall);
        manager.add(colony);

        try {
            // Отряд у ворот важнее даже пустой колонии: у этой беды есть
            // срок, а у остальных — нет.
            WarParty band = new WarParty(UUID.randomUUID(), UUID.randomUUID(), NORMAN,
                    hall, 2, 1L, 2L);
            colony.besiege(band, 0L);
            if (!"villagepax.advice.under_siege".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Отряд у ворот не назван первой бедой: "
                        + Advice.nextStep(world, colony));
            }
            colony.liftSiege();

            // Пусто — и это важнее всего остального.
            if (!"villagepax.advice.deserted".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Пустая колония не названа первой бедой: "
                        + Advice.nextStep(world, colony));
            }

            Citizen builder = Citizen.newborn("Rollo", "", NORMAN, Gender.MALE);
            builder.setProfession(BuildJob.BUILDER);
            colony.addCitizen(builder);

            // Жители есть, еды нет.
            if (!"villagepax.advice.no_food".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Голод не назван: "
                        + Advice.nextStep(world, colony));
            }

            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 8));

            // Еда есть, строителя нет.
            builder.setProfession(null);
            if (!"villagepax.advice.no_builder".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Отсутствие строителя не названо: "
                        + Advice.nextStep(world, colony));
            }

            // Строитель есть, а спать негде: дом важнее поля, потому что
            // без кровати колония не вырастет вовсе, а еду пока носит игрок.
            builder.setProfession(BuildJob.BUILDER);
            if (!"villagepax.advice.no_beds".equals(
                    Advice.nextStep(world, colony).orElse(null))) {
                context.throwGameTestException("Совет поставить дом не дан: "
                        + Advice.nextStep(world, colony));
            }
        } finally {
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Откуп уводит отряд, но не возвращает доверия.
     * <p>
     * Главная проверка всей задачи, и вторая её половина важнее первой.
     * Что монета уводит отряд — это удобство; что <b>доверие после откупа
     * то же самое</b> — это решение по игре. Кошелёк покупает время, а не
     * дружбу: иначе богатый игрок отменял бы всю дипломатию одним сундуком
     * золота, и ни подарки, ни квесты больше ничего не значили бы.
     * <p>
     * Хвост проверки про то же: через десять дней, когда тишина кончится,
     * отряд выходит снова — потому что обиду никто не отменял.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "peace")
    public void paidPeaceSendsTheBandHomeButBuysNoTrust(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 2, 1));
        BlockPos centre = context.getAbsolutePos(new BlockPos(16, 2, 16));
        List<BlockPos> floor = new ArrayList<>();

        // Ратуша деревни — это её кошель: плату кладут туда же, куда
        // выручку с торга.
        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(village);
        manager.add(colony);

        WarParty party = null;
        try {
            for (int x = 2; x <= 30; x++) {
                for (int z = 2; z <= 30; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            manager.update(village.id(), state -> state.addReputation(player, -60));
            Raids.sendIfDue(world, manager, village, 10L);
            Raids.watch(world, manager, 11L);

            party = manager.byId(colony.id()).orElseThrow().siege().orElse(null);
            if (party == null) {
                context.throwGameTestException("Отряд не вышел: мириться не с кем");
                return;
            }
            if (Raids.bodiesOf(world, party).isEmpty()) {
                context.throwGameTestException("Отряд не встал телами: уводить некого");
                return;
            }

            SimpleInventory purse = new SimpleInventory(36);
            purse.addStack(new ItemStack(ModItems.GOLD_COIN, 4));
            int carried = Coins.total(purse);
            int price = Peace.price(-60);

            Peace.Outcome outcome = Peace.buy(world, manager, village, player, purse, 11L,
                    left -> purse.addStack(left));
            if (!outcome.bought()) {
                context.throwGameTestException("Монету не взяли: " + outcome.verdict()
                        + ", просили " + price + ", при себе " + carried);
                return;
            }
            if (Coins.total(purse) != carried - price) {
                context.throwGameTestException("С игрока взяли " + (carried - Coins.total(purse))
                        + " вместо " + price);
            }
            if (Coins.total(Warehouse.of(world, village).coins()) != price) {
                context.throwGameTestException("В кошель деревни легло "
                        + Coins.total(Warehouse.of(world, village).coins()) + " вместо " + price);
            }

            Settlement paid = manager.byId(village.id()).orElseThrow();
            if (paid.truceDaysLeft(11L) != Peace.TRUCE_DAYS) {
                context.throwGameTestException("Куплено " + paid.truceDaysLeft(11L)
                        + " дней тишины вместо " + Peace.TRUCE_DAYS);
            }
            if (paid.reputationOf(player) != -60) {
                context.throwGameTestException("Откуп поднял доверие до "
                        + paid.reputationOf(player) + ": деньгами покупается тишина, а не дружба");
            }

            // Отряд уходит сейчас же, а не достаивает свой срок.
            if (!Raids.callOff(world, manager, player, village.id())) {
                context.throwGameTestException("Отряд не увели: мир куплен, а бойцы стоят");
            }
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Осада не снята после откупа");
            }
            if (!Raids.bodiesOf(world, party).isEmpty()) {
                context.throwGameTestException("Тела остались стоять после уплаченного мира");
            }

            // Пока идёт перемирие — не приходят, хотя обида на месте.
            Raids.sendIfDue(world, manager, village, 11L + Raids.COOLDOWN_DAYS);
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Набег во время оплаченного перемирия");
            }

            // А когда кончится — приходят снова: обиду не покупали.
            Raids.sendIfDue(world, manager, village, 11L + Peace.TRUCE_DAYS);
            if (manager.byId(colony.id()).orElseThrow().siege().isEmpty()) {
                context.throwGameTestException("Перемирие кончилось, а отряда нет: "
                        + "выходит, откуп отменил обиду насовсем");
            }
        } finally {
            manager.byId(colony.id()).flatMap(Settlement::siege)
                    .ifPresent(one -> Raids.bodiesOf(world, one).forEach(CitizenEntity::discard));
            if (party != null) {
                Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            }
            manager.remove(village.id());
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Отбитый набег покупает дни тишины — и ни очка доверия.
     * <p>
     * Ответ на «я отбился, и что изменилось». До сих пор — ничего: отряды
     * приходили каждые пять дней, сколько бы их ни полегло, и оборона
     * не значила <b>ровно ничего</b>. Теперь каждый павший стоит деревне
     * дней траура, потому что мёртвые не ходят в походы.
     * <p>
     * Доверие при этом не меняется, и это то же решение, что у откупа:
     * убитый боец деревню не примиряет. Кровью, как и монетой, покупается
     * только время.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "peace")
    public void fallenFightersBuyQuietDays(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 2, 1));
        BlockPos centre = context.getAbsolutePos(new BlockPos(16, 2, 16));
        List<BlockPos> floor = new ArrayList<>();

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", hall);
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(village);
        manager.add(colony);

        WarParty party = null;
        try {
            for (int x = 2; x <= 30; x++) {
                for (int z = 2; z <= 30; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            manager.update(village.id(), state -> state.addReputation(player, -60));
            Raids.sendIfDue(world, manager, village, 10L);
            Raids.watch(world, manager, 11L);

            party = manager.byId(colony.id()).orElseThrow().siege().orElse(null);
            if (party == null) {
                context.throwGameTestException("Отряд не вышел: бить некого");
                return;
            }
            List<CitizenEntity> band = Raids.bodiesOf(world, party);
            if (band.size() != party.fighters()) {
                context.throwGameTestException("Тел " + band.size() + " на "
                        + party.fighters() + " бойцов");
                return;
            }

            int fighters = party.fighters();
            for (CitizenEntity fighter : band) {
                Raids.fell(world, fighter, 11L);
            }

            Settlement mourning = manager.byId(village.id()).orElseThrow();
            int quiet = fighters * Peace.MOURNING_DAYS;
            if (mourning.truceDaysLeft(11L) != quiet) {
                context.throwGameTestException("За " + fighters + " павших деревня молчит "
                        + mourning.truceDaysLeft(11L) + " дней вместо " + quiet);
            }
            if (mourning.reputationOf(player) != -60) {
                context.throwGameTestException("Убитые бойцы изменили доверие до "
                        + mourning.reputationOf(player) + ": кровь не мирит");
            }

            // Пять дней остывания прошло, а траур — нет.
            Raids.sendIfDue(world, manager, village, 11L + Raids.COOLDOWN_DAYS);
            if (manager.byId(colony.id()).orElseThrow().siege().isPresent()) {
                context.throwGameTestException("Деревня, потерявшая " + fighters
                        + " бойцов, вышла снова через " + Raids.COOLDOWN_DAYS + " дней: "
                        + "оборона не значит ничего");
            }

            // А когда отгоревали — выходят: обида-то осталась.
            Raids.sendIfDue(world, manager, village, 11L + quiet);
            if (manager.byId(colony.id()).orElseThrow().siege().isEmpty()) {
                context.throwGameTestException("Траур кончился, а отряда нет: "
                        + "выходит, отбитый набег примирил деревню навсегда");
            }
        } finally {
            manager.byId(colony.id()).flatMap(Settlement::siege)
                    .ifPresent(one -> Raids.bodiesOf(world, one).forEach(CitizenEntity::discard));
            if (party != null) {
                Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
            }
            manager.remove(village.id());
            manager.remove(colony.id());
            for (BlockPos at : floor) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(centre, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Боец отряда, о котором поселение забыло, уходит сам.
     * <p>
     * Обычно тела уводит сам набег — но запись об осаде может исчезнуть
     * помимо него: старое сохранение, выкупленный мир, правка данных.
     * Без этой проверки вооружённые куклы остались бы стоять у колонии
     * навсегда, и единственным способом от них избавиться было бы
     * перебить их всех.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "peace", tickLimit = 100)
    public void forgottenFighterLeavesOnItsOwn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(4, 2, 4));
        List<BlockPos> floor = new ArrayList<>();
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                world.setBlockState(at, Blocks.STONE.getDefaultState());
                floor.add(at);
            }
        }

        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Моя", centre);
        manager.add(colony);

        CitizenEntity ghost = CitizenSpawner.spawnPuppet(world,
                context.getAbsolutePos(new BlockPos(6, 2, 6)));
        if (ghost == null) {
            context.throwGameTestException("Кукла не встала: проверять нечего");
            return;
        }
        // Отряда с таким опознавателем у колонии нет и не было.
        ghost.linkRaid(colony.id(), UUID.randomUUID());

        context.runAtTick(40, () -> {
            try {
                if (!ghost.isRemoved()) {
                    context.throwGameTestException("Боец забытого отряда всё стоит: "
                            + "такие куклы остаются в мире навсегда");
                }
            } finally {
                ghost.discard();
                manager.remove(colony.id());
                for (BlockPos at : floor) {
                    world.setBlockState(at, Blocks.AIR.getDefaultState());
                }
                world.setBlockState(centre, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }


    /**
     * Деревня за горизонтом не затягивает в память свои чанки.
     * <p>
     * Правило мода «спрашивать блоки в незагруженном чанке нельзя» до сих
     * пор соблюдалось везде, кроме одного места — склада. А суточная смена
     * зовёт склад у <b>каждого</b> поселения мира, включая те, до которых
     * игрок за всю игру не дошёл: каждая деревня раз в игровой день
     * заставляла мир загрузить свои чанки, и все разом в один тик.
     * <p>
     * Проверяется не «быстро ли», а <b>случилось ли</b>: чанк, которого
     * не было в памяти, после суточной смены не должен там оказаться.
     * Это тот редкий случай, когда производительность проверяется точным
     * условием, а не секундомером.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "playable")
    public void farVillageDoesNotDragItsChunksIn(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        // Далеко за пределами прогона: чанк туда никто не загружал.
        BlockPos away = new BlockPos(220_000, 64, 220_000);
        ChunkPos chunk = new ChunkPos(away);
        if (world.isChunkLoaded(chunk.x, chunk.z)) {
            context.throwGameTestException("Чанк за 220 тысяч блоков уже загружен: "
                    + "проверять нечего");
            return;
        }

        Settlement far = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Дальняя", away);
        manager.add(far);

        try {
            Villages.newDay(world, manager, far);
            if (world.isChunkLoaded(chunk.x, chunk.z)) {
                context.throwGameTestException("Суточная смена загрузила чанк дальней "
                        + "деревни: так каждая деревня мира тянет в память свои чанки "
                        + "раз в игровой день, все разом в один тик");
            }

            // И склад её тоже не дотягивается — того же правила ради.
            if (Warehouse.of(world, far).containerCount() != 0) {
                context.throwGameTestException("У невидимой деревни нашёлся склад");
            }
            if (world.isChunkLoaded(chunk.x, chunk.z)) {
                context.throwGameTestException("Склад дотянулся до незагруженного чанка "
                        + "и заставил мир его поднять");
            }
        } finally {
            manager.remove(far.id());
        }

        context.complete();
    }


    /**
     * Житель переодевается, когда меняет ремесло.
     * <p>
     * Облик едет на клиент отслеживаемым полем, и вся ценность затеи —
     * в том, что он <b>не застывает</b>: игрок даёт человеку ремесло
     * через пульт и должен через секунду увидеть на нём фартук, а не
     * ждать перезахода в мир.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "looks", tickLimit = 120)
    public void citizenChangesClothesWithTheCraft(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos stands = context.getAbsolutePos(new BlockPos(4, 2, 4));

        world.setBlockState(hall, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(NORMAN, Owner.of(UUID.randomUUID()), "Облик", hall);
        manager.add(colony);

        Citizen citizen = Citizen.newborn("Adeline", "la Fermiere", NORMAN, Gender.FEMALE);
        citizen.setPosition(Vec3d.ofBottomCenter(stands));
        colony.addCitizen(citizen);
        CitizenEntity body = CitizenSpawner.spawnBody(world, colony, citizen);
        if (body == null) {
            manager.remove(colony.id());
            context.throwGameTestException("Тело не встало: смотреть не на кого");
            return;
        }

        if (!body.look().endsWith("norman/female.png")) {
            cleanUpLooks(world, manager, colony, body, hall);
            context.throwGameTestException("Без ремесла облик "
                    + body.look() + ", а ждали будничный норманнский женский");
            return;
        }

        citizen.setProfession(FarmJob.FARMER);

        context.runAtTick(40, () -> {
            try {
                if (body.look() == null || !body.look().endsWith("norman/female_farmer.png")) {
                    context.throwGameTestException("Дали ремесло пахаря, а на человеке "
                            + body.look() + ": убрано=" + body.isRemoved()
                            + ", запись=" + manager.byId(colony.id())
                                    .flatMap(state -> state.citizen(citizen.id()))
                                    .flatMap(Citizen::profession)
                            + ", тел=" + world.getEntitiesByClass(CitizenEntity.class,
                                    new Box(hall).expand(16), alive -> true).size());
                }
            } finally {
                cleanUpLooks(world, manager, colony, body, hall);
            }
            context.complete();
        });
    }

    private static void cleanUpLooks(ServerWorld world, SettlementManager manager,
                                     Settlement colony, CitizenEntity body, BlockPos hall) {
        body.discard();
        manager.remove(colony.id());
        world.setBlockState(hall, Blocks.AIR.getDefaultState());
    }


    private static final Identifier BREWERY_SCHEMATIC =
            new Identifier("villagepax", "norman/brewery_lvl1");
    private static final Identifier BREWERY_TYPE =
            new Identifier("villagepax", "norman/brewery");

    /**
     * Пивоварня открывается деревней — и варит то, чего не добыть киркой.
     * <p>
     * Вся задача одной проверкой, потому что это одна цепь, и рвётся она
     * в любом звене. Заказчик сказал: играть скучно, зацепиться не за что.
     * Зацепиться теперь есть за что ровно потому, что цепь целая: ступень
     * запирает ремесло → ратуша второго уровня открывает его → мастерская
     * даёт работу → работа даёт эль, которого нигде больше нет.
     * <p>
     * Каждое звено проверяется отдельным утверждением, и каждое из них
     * когда-нибудь спасёт: заперто ли до срока, открылось ли вовремя,
     * ушло ли зерно, появился ли эль.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth", tickLimit = 400)
    public void breweryOpensWithTheVillageAndBrewsAle(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, BREWERY_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos shopAt = context.getAbsolutePos(new BlockPos(0, 8, 6));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen brewer = null;

        try {
            // --- заперто, пока колония хутор ---
            if (!(BuildOrders.check(colony, BREWERY_SCHEMATIC, shopAt, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Locked)) {
                context.throwGameTestException("Пивоварню дали разметить на хуторе: "
                        + "ступень ничего не значит");
                return;
            }

            Citizen worker = Citizen.newborn("Ansel", "le Brasseur", NORMAN, Gender.MALE);
            colony.addCitizen(worker);
            if (Assignments.set(world, manager, colony, worker.id(),
                    Optional.of(CraftJob.BREWER)) != Assignments.Result.LOCKED) {
                context.throwGameTestException("Пивовара наняли на хуторе: "
                        + "ремесло не заперто ступенью");
                return;
            }

            // --- деревня открывает и то и другое ---
            colony.setLevel(SettlementLevel.VILLAGE);
            if (!(BuildOrders.check(colony, BREWERY_SCHEMATIC, shopAt, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Placed)) {
                context.throwGameTestException("Колония стала деревней, а пивоварня "
                        + "всё заперта: ступень не открывает обещанного");
                return;
            }

            Building shop = plan(colony, shopAt, BREWERY_TYPE, BlockRotation.NONE);
            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), shop.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Пивоварня не встала: варить негде");
                return;
            }

            brewer = worker;
            if (Assignments.set(world, manager, colony, brewer.id(),
                    Optional.of(CraftJob.BREWER)) != Assignments.Result.DONE) {
                context.throwGameTestException("В деревне пивовара всё ещё не нанять");
                return;
            }
            brewer.setPosition(Vec3d.ofBottomCenter(shopAt.up()));
            CitizenSpawner.spawnBody(world, colony, brewer);
            Workplaces.assign(world, colony);

            // --- работа: зерно в эль ---
            Warehouse before = Warehouse.of(world, colony);
            before.add(new ItemStack(Items.WHEAT, 12));
            int wheat = before.count(Items.WHEAT);

            runWork(world, manager, colony, brewer, 8, Schedule.MORNING_WORK);

            Warehouse after = Warehouse.of(world, colony);
            if (after.count(ModItems.ALE) <= 0) {
                context.throwGameTestException("Пивовар отработал восемь решений "
                        + "и не сварил ничего: эля на складе " + after.count(ModItems.ALE)
                        + ", зерна " + after.count(Items.WHEAT));
            }
            if (after.count(Items.WHEAT) >= wheat) {
                context.throwGameTestException("Эль взялся из воздуха: зерна было "
                        + wheat + ", осталось " + after.count(Items.WHEAT));
            }
        } finally {
            if (brewer != null) {
                discardBodies(world, colony);
            }
            // Здания может и не быть: проверка падает на первом же
            // утверждении, если ворота ступени сняли, — и уборка не имеет
            // права заслонить собой настоящую причину падения.
            colony.buildings().stream()
                    .filter(one -> one.type().equals(BREWERY_TYPE))
                    .findFirst()
                    .ifPresent(one -> demolish(world, one, plan));
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Записать поселению осаду, которую проверка изображает телами.
     * <p>
     * Без записи тела набега живут до первой секунды: мод убирает бойцов,
     * о чьём отряде поселение не помнит. Правильно так и есть — а проверке
     * остаётся не выдумывать состояний, которых в игре не бывает.
     */
    private static void rememberRaid(SettlementManager manager, Settlement colony, UUID party,
                                     BlockPos musters, int fighters) {
        manager.update(colony.id(), state -> state.besiege(
                new WarParty(party, UUID.randomUUID(), NORMAN, musters, fighters, 0L, 9_000L), 0L));
    }


    /**
     * Деревня встречает игрока живым прилавком, а не двумя отказами.
     * <p>
     * Заказчик назвал торговлю среди того, что «криво работает», и разбор
     * кода объяснил почему. Первая встреча была <b>тупиком с обеих
     * сторон</b>: купить игрок не мог (монеты у него ещё нет и взяться
     * ей неоткуда), продать тоже — у самой деревни кошель был пуст,
     * потому что наполнялся только на суточной смене. Оба прилавка
     * серые, мод выглядит сломанным.
     * <p>
     * Проверяется ровно то, что делает игрок в первые минуты: подходит
     * и пробует и то и другое. Обе сделки обязаны пройти на нулевом
     * доверии — на большее в первую встречу ему рассчитывать не на что.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade_first", tickLimit = 300)
    public void villageMeetsThePlayerWithAFullStall(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(16, 2, 16));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = 0; x <= 32; x++) {
                for (int z = 0; z <= 32; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала: помеха="
                        + whoBlocks(manager, centre));
                return;
            }

            Warehouse wares = Warehouse.of(world, village);
            if (Trading.purse(wares) <= 0) {
                context.throwGameTestException("У деревни пустой кошель в день знакомства: "
                        + "продать ей нечего и некому");
            }

            // --- игрок покупает: у него монета, у деревни товар ---
            TradeTable.Deal sells = Trading.dealsOn(village, Trading.Side.VILLAGE_SELLS).get(0);
            SimpleInventory hands = new SimpleInventory(36);
            hands.addStack(new ItemStack(ModItems.COIN, 32));

            Trading.Outcome bought = Trading.trade(village, player, hands,
                    Warehouse.of(world, village), Trading.Side.VILLAGE_SELLS, sells,
                    left -> hands.addStack(left));
            if (bought != Trading.Outcome.DONE) {
                context.throwGameTestException("Купить у деревни нельзя в первый же день: "
                        + bought + ", товар " + sells.item()
                        + ", на складе " + Warehouse.of(world, village).count(sells.item()));
            }

            // --- и продаёт: у деревни монета ---
            TradeTable.Deal buys = Trading.dealsOn(village, Trading.Side.VILLAGE_BUYS).get(0);
            hands.addStack(new ItemStack(buys.item(), buys.count() * 2));
            Trading.Outcome sold = Trading.trade(village, player, hands,
                    Warehouse.of(world, village), Trading.Side.VILLAGE_BUYS, buys,
                    left -> hands.addStack(left));
            if (sold != Trading.Outcome.DONE) {
                context.throwGameTestException("Продать деревне нельзя в первый же день: "
                        + sold + ", товар " + buys.item()
                        + ", в кошеле " + Trading.purse(Warehouse.of(world, village)));
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }


    /**
     * На поле можно войти с земли, а не только спрыгнуть в него.
     * <p>
     * Жалоба заказчика, повторённая трижды: «который раз не взобраться
     * на ферму, с неё не смогут забрать посев». Здание стоит на цоколе,
     * под цоколь мод подсыпает опору, и порог оказывается на два блока
     * выше земли. Шаг в один блок делают и человек, и ванильный поиск
     * пути; <b>два не делает никто</b>, и поле стоит нетронутым.
     * <p>
     * Проверяется ровно то, чего не хватало: у калитки снаружи обязана
     * быть ступень не ниже чем на блок под порогом. Всё остальное —
     * дело ног.
     * <p>
     * И проверяется это <b>дважды</b>: на недостроенном поле и на готовом.
     * Заказчик попросил, чтобы войти можно было сразу, — а стройка идёт
     * долго, и дом, в который нельзя войти всю стройку, бесполезен ровно
     * так же, как дом, в который нельзя войти совсем.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "porch", tickLimit = 400)
    public void farmCanBeWalkedIntoFromTheGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, FARM_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        // Поле на два блока выше земли вокруг — ровно то, что получается
        // в игре после подсыпки опоры на склоне. Ступени пойдут на запад
        // от калитки, и место под них оставлено внутри площадки: за её
        // краем начинается соседняя проверка.
        BlockPos farmAt = context.getAbsolutePos(new BlockPos(3, 4, 3));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = null;

        try {
            // Земля кладётся только там, где стоит поле и куда ляжет
            // крыльцо. Мир игровых тестов общий, и площадка, расписанная
            // на двадцать блоков вокруг, затирает соседние проверки —
            // на этом я и попался, получив мигание в чужих проверках
            // подвоза.
            // Площадка с запасом по всем сторонам: спуск с крыльца идёт
            // на три-четыре клетки, и край площадки не должен попадать
            // в эти клетки — иначе проверка объявит обрывом свою границу.
            for (int x = -6; x <= 14; x++) {
                for (int z = -6; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 2, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            farm = plan(colony, farmAt, FARM_TYPE, BlockRotation.NONE);
            stockFor(world, colony, plan);

            // Сперва только цоколь и немного стен: заказчик просил, чтобы
            // войти можно было СРАЗУ, а не после крыши. Стройка идёт долго,
            // и всё это время дом с порогом на высоте пояса бесполезен.
            BuildJob.advance(world, manager, colony.id(), farm.id(), 120);
            if (farm.isOperational()) {
                context.throwGameTestException("Поле достроилось за сто двадцать шагов: "
                        + "проверять «вход до крыши» не на чем");
                return;
            }
            for (BlockPos door : Access.entrances(farm, plan)) {
                String trouble = descentTrouble(world, farm, plan, door);
                if (trouble != null) {
                    context.throwGameTestException("Недостроенное поле не пускает: "
                            + trouble);
                }
            }

            if (BuildJob.advance(world, manager, colony.id(), farm.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Поле не встало: входить некуда");
                return;
            }

            List<BlockPos> doors = Access.entrances(farm, plan);
            if (doors.isEmpty()) {
                context.throwGameTestException("У поля не нашлось ни калитки, ни двери");
                return;
            }

            for (BlockPos door : doors) {
                // Снаружи — это в сторону от середины поля; у калитки
                // норманнского поля это запад.
                String trouble = descentTrouble(world, farm, plan, door);
                if (trouble != null) {
                    context.throwGameTestException("На готовое поле не войти: " + trouble);
                }
            }
        } finally {
            if (farm != null) {
                demolish(world, farm, plan);
            }
            cleanUpVillage(world, manager, colony, hall, ground);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Проходим ли выход целиком: от порога и на четыре шага наружу.
     * <p>
     * Считается ВЫСОТА НОГ, а не место блока: в проёме ноги на уровне
     * самого проёма, снаружи — на блок выше найденной опоры. Мерка
     * блоками давала ошибку в единицу, и из-за неё крыльцо выглядело
     * сделанным при перепаде в два блока.
     * <p>
     * И проверяется <b>весь спуск</b>, а не первая клетка за порогом.
     * Первая редакция смотрела только её — и пропускала обрыв на второй,
     * то есть ровно ту беду, на которую жаловался заказчик: ступень
     * стоит, а войти нельзя. Поймал это поиск пути в соседней проверке,
     * и мерку пришлось растить до его строгости.
     * <p>
     * Вторая редакция останавливалась на первой же ровной клетке — и это
     * была та же ошибка, от которой чинили крыльцо, только переписанная
     * в проверку: ровно перед обрывом клетка как раз ровная. Теперь
     * спуск идёт до конца, а кончается он на краю площадки, где опоры
     * нет вовсе.
     *
     * @return пусто, если пройти можно, иначе рассказ о том, где обрыв
     */
    private static String descentTrouble(ServerWorld world, Building building,
                                         Schematic schematic, BlockPos door) {
        Direction out = Access.awayFrom(building, schematic, door);

        // Сторона обязана вести НАРУЖУ следа. Без этого вопроса проверка
        // меряет спуск там, куда показал сам проверяемый код: уйди он
        // в горницу — под ногами ровный пол, обрыва нет, всё «хорошо»,
        // а с улицы в дом по-прежнему не войти. Спрашивается только
        // у тех входов, от которых до края следа вообще можно дойти:
        // внутренняя дверь большого дома наружу и не должна выводить.
        if (leavesFootprint(building, schematic, door) && !leavesFootprint(building, schematic, door, out)) {
            return "сторона " + out + " от входа " + door.toShortString()
                    + " ведёт внутрь следа: крыльцо ляжет в горнице, а не на улице";
        }

        int walk = door.getY();
        for (int step = 1; step <= 4; step++) {
            BlockPos column = door.offset(out, step);
            // Ищем опору только рядом: глубже четырёх блоков — это уже
            // обрыв или край испытательной площадки, а крыльцо мостов
            // не строит и спрашивать с него нечего.
            int ground = Integer.MIN_VALUE;
            for (int y = walk + 2; y >= walk - 4; y--) {
                BlockPos at = column.withY(y);
                if (!world.getBlockState(at).getCollisionShape(world, at).isEmpty()) {
                    ground = y;
                    break;
                }
            }
            if (ground == Integer.MIN_VALUE) {
                // Опоры нет вовсе: это край испытательной площадки,
                // а не порог. Дальше мерить нечего.
                return null;
            }
            int feet = ground + 1;
            if (feet < walk - 1) {
                return "на " + column.toShortString() + " ноги на " + feet
                        + ", а шагом раньше на " + walk + " — обрыв в " + (walk - feet);
            }
            if (feet > walk + 1) {
                return "на " + column.toShortString() + " стена высотой "
                        + (feet - walk);
            }
            walk = feet;
        }
        return null;
    }

    /** Выводит ли эта сторона за след здания — на улицу, а не в горницу. */
    private static boolean leavesFootprint(Building building, Schematic schematic,
                                           BlockPos door, Direction way) {
        for (int step = 1; step <= 4; step++) {
            if (!BuildSite.covers(building.anchor(), schematic.size(), building.rotation(),
                    door.offset(way, step))) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли у этого входа вообще выход наружу в четыре шага. */
    private static boolean leavesFootprint(Building building, Schematic schematic, BlockPos door) {
        for (Direction way : Direction.Type.HORIZONTAL) {
            if (leavesFootprint(building, schematic, door, way)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Обжитой дом поднимает настроение, а голый — нет.
     * <p>
     * Убранство в моде было с самого начала и не значило ничего: фонарь,
     * ковёр и бельё ставились билдером, стояли и ни на что не влияли.
     * Заказчик попросил, чтобы декор что-то делал, и делает он то,
     * чего от него и ждут.
     * <p>
     * Проверяется разницей, а не числом: два сытых жителя одной колонии,
     * у одного дом есть, у другого нет. Разница в настроении и есть уют.
     * Так проверка переживёт любую правку самих прибавок.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "comfort", tickLimit = 400)
    public void cosyHomeLiftsTheMood(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        // Низко и рядом: мир игровых тестов общий, площадки стоят сеткой,
        // и проверка, расписавшаяся на двадцать блоков вокруг, ломает
        // соседей. На этом я и попался — мигали чужие проверки подвоза.
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(2, 1, 2));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, houseAt, HOUSE_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), house.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не встал: уюту неоткуда взяться");
                return;
            }

            int cosy = Comfort.of(world, house);
            if (cosy <= 0) {
                context.throwGameTestException("В достроенном доме с фонарём и ковром "
                        + "уюта " + cosy + ": убранство снова ничего не значит");
                return;
            }

            Citizen homed = Citizen.newborn("Adeline", "", NORMAN, Gender.FEMALE);
            Citizen homeless = Citizen.newborn("Rollo", "", NORMAN, Gender.MALE);
            for (Citizen citizen : List.of(homed, homeless)) {
                citizen.setSaturation(30);
                citizen.setHappiness(50);
                colony.addCitizen(citizen);
            }
            homed.setHome(house.id());

            Warehouse.of(world, colony).add(new ItemStack(Items.BREAD, 16));
            Needs.newDay(world, manager, colony);

            int withHome = homed.happiness() - 50;
            int without = homeless.happiness() - 50;
            if (withHome <= without) {
                context.throwGameTestException("Дом не согрел: с домом прибавка "
                        + withHome + ", без дома " + without);
            }
            if (withHome - without != cosy) {
                context.throwGameTestException("Уют посчитан не тот: разница "
                        + (withHome - without) + ", а дом стоит " + cosy);
            }
        } finally {
            demolish(world, house, plan);
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * На верёвку вешают и с неё снимают — а что попало не вешают.
     * <p>
     * Решение заказчика: «пусть это будет просто верёвка, но на которую
     * можно будет вешать кожаные вещи, кожу и тканевую одежду». Проверка
     * держит три обещания разом: вещь из тега вешается, снимается та,
     * что повесили последней, и больше четырёх на бечеву не лезет.
     * <p>
     * Ещё одно обещание — что сломанная верёвка возвращает повешенное —
     * проверяется тем же ходом: без него игрок теряет вещи молча.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rope")
    public void ropeHoldsWhatYouHangOnIt(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos at = context.getAbsolutePos(new BlockPos(1, 2, 1));

        try {
            world.setBlockState(at, ModBlocks.LAUNDRY.getDefaultState());
            if (!(world.getBlockEntity(at) instanceof RopeBlockEntity rope)) {
                context.throwGameTestException("У верёвки нет блок-сущности: вешать некуда");
                return;
            }

            if (!rope.isEmpty()) {
                context.throwGameTestException("Поставленная руками верёвка пришла не пустой");
            }

            if (!rope.hang(new ItemStack(Items.LEATHER, 3))) {
                context.throwGameTestException("Кожу на верёвку не повесили");
                return;
            }
            if (rope.hung().get(0).getCount() != 1) {
                context.throwGameTestException("На верёвке повисло "
                        + rope.hung().get(0).getCount() + " штук: вешают по одной, "
                        + "иначе на бечеве будет «кожа ×64»");
            }

            rope.hang(new ItemStack(Items.WHITE_WOOL));
            rope.hang(new ItemStack(Items.LEATHER_BOOTS));
            rope.hang(new ItemStack(Items.WHITE_CARPET));
            if (rope.hang(new ItemStack(Items.LEATHER))) {
                context.throwGameTestException("На верёвку влезло пятое: мест у неё "
                        + RopeBlockEntity.SIZE + ", и складом она быть не должна");
            }

            ItemStack taken = rope.takeDown();
            if (!taken.isOf(Items.WHITE_CARPET)) {
                context.throwGameTestException("Сняли не то, что вешали последним: "
                        + taken.getItem());
            }

            // Сломали — повешенное падает наземь, а не пропадает.
            int before = world.getEntitiesByClass(ItemEntity.class,
                    new Box(at).expand(4), alive -> true).size();
            world.breakBlock(at, false);
            int after = world.getEntitiesByClass(ItemEntity.class,
                    new Box(at).expand(4), alive -> true).size();
            if (after - before < 3) {
                context.throwGameTestException("Сломанная верёвка вернула " + (after - before)
                        + " вещей из трёх: игрок теряет их молча");
            }
        } finally {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
            world.getEntitiesByClass(ItemEntity.class, new Box(at).expand(6), alive -> true)
                    .forEach(ItemEntity::discard);
        }

        context.complete();
    }

    /**
     * Строитель вешает бельё сам, а игрок получает верёвку пустой.
     * <p>
     * Пустая бечева посреди деревенского двора выглядит недоделкой,
     * а верёвка, которая сама родит шерсть в руках игрока, — это
     * бесплатная шерсть. Поэтому вешает <b>строитель</b>, и только он.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "rope")
    public void theBuilderHangsTheWashingHimself(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos at = context.getAbsolutePos(new BlockPos(3, 2, 3));

        try {
            world.setBlockState(at, ModBlocks.LAUNDRY.getDefaultState());
            Furnishings.stock(world, at, world.getBlockState(at), at.asLong());

            if (!(world.getBlockEntity(at) instanceof RopeBlockEntity rope) || rope.isEmpty()) {
                context.throwGameTestException("Строитель натянул верёвку и ничего не повесил");
                return;
            }
            if (rope.lastHung() != 1) {
                context.throwGameTestException("Во дворе повисло не две вещи, а "
                        + (rope.lastHung() + 1));
            }
        } finally {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
            world.getEntitiesByClass(ItemEntity.class, new Box(at).expand(6), alive -> true)
                    .forEach(ItemEntity::discard);
        }

        context.complete();
    }


    private static final Identifier WEAVERY_SCHEMATIC =
            new Identifier("villagepax", "norman/weavery_lvl1");
    private static final Identifier WEAVERY_TYPE =
            new Identifier("villagepax", "norman/weavery");
    private static final Identifier WEAVER = new Identifier("villagepax", "weaver");

    /**
     * Город открывает ткача, а ткач даёт колонии товар на вывоз.
     * <p>
     * Вторая ступень лестницы, и без неё первая висела в пустоте: до сих
     * пор выше «деревни» не открывалось <b>ничего</b>, потому что и самой
     * ратуши третьего уровня в моде не было. Карточка роста звала игрока
     * туда, куда дойти нельзя.
     * <p>
     * Проверяется вся цепь: в деревне ткач заперт, в городе открыт,
     * ткацкая встаёт, шерсть уходит, сукно появляется. И отдельно —
     * что <b>сукно у деревень в цене</b>: ради этого оно и заведено,
     * иначе это просто ещё одна вещь на складе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth", tickLimit = 400)
    public void townOpensTheWeaverAndClothIsWorthSelling(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, WEAVERY_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos shopAt = context.getAbsolutePos(new BlockPos(3, 6, 3));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Citizen weaver = null;

        try {
            colony.setLevel(SettlementLevel.VILLAGE);
            Citizen worker = Citizen.newborn("Mahaut", "la Tisserande", NORMAN, Gender.FEMALE);
            colony.addCitizen(worker);
            if (Assignments.set(world, manager, colony, worker.id(), Optional.of(WEAVER))
                    != Assignments.Result.LOCKED) {
                context.throwGameTestException("Ткача наняли в деревне: ступень «город» "
                        + "ничего не значит");
                return;
            }
            if (!(BuildOrders.check(colony, WEAVERY_SCHEMATIC, shopAt, BlockRotation.NONE)
                    instanceof BuildOrders.Result.Locked)) {
                context.throwGameTestException("Ткацкую дали разметить в деревне");
                return;
            }

            colony.setLevel(SettlementLevel.TOWN);
            Building shop = plan(colony, shopAt, WEAVERY_TYPE, BlockRotation.NONE);
            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), shop.id(), 10_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ткацкая не встала");
                return;
            }

            weaver = worker;
            if (Assignments.set(world, manager, colony, weaver.id(), Optional.of(WEAVER))
                    != Assignments.Result.DONE) {
                context.throwGameTestException("В городе ткача всё ещё не нанять");
                return;
            }
            weaver.setPosition(Vec3d.ofBottomCenter(shopAt.up()));
            CitizenSpawner.spawnBody(world, colony, weaver);
            Workplaces.assign(world, colony);

            Warehouse.of(world, colony).add(new ItemStack(Items.WHITE_WOOL, 12));
            int wool = Warehouse.of(world, colony).count(Items.WHITE_WOOL);
            runWork(world, manager, colony, weaver, 8, Schedule.MORNING_WORK);

            Warehouse after = Warehouse.of(world, colony);
            if (after.count(ModItems.CLOTH) <= 0) {
                context.throwGameTestException("Ткач отработал восемь решений и не соткал "
                        + "ничего: сукна " + after.count(ModItems.CLOTH)
                        + ", шерсти " + after.count(Items.WHITE_WOOL));
            }
            if (after.count(Items.WHITE_WOOL) >= wool) {
                context.throwGameTestException("Сукно взялось из воздуха: шерсти было "
                        + wool + ", осталось " + after.count(Items.WHITE_WOOL));
            }

            // Ради чего всё: деревни берут сукно, и берут дорого.
            Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Торг",
                    context.getAbsolutePos(new BlockPos(12, 1, 12)));
            manager.add(village);
            try {
                TradeTable.Deal deal = Trading
                        .find(village, Trading.Side.VILLAGE_BUYS, ModItems.CLOTH, 2)
                        .orElse(null);
                if (deal == null) {
                    context.throwGameTestException("Деревня не скупает сукно: колонии "
                            + "нечем торговать, и монета в мир по-прежнему не приходит");
                    return;
                }
                if (deal.price() < 4) {
                    context.throwGameTestException("За сукно дают " + deal.price()
                            + " — это не товар на вывоз, а безделица");
                }
            } finally {
                manager.remove(village.id());
            }
        } finally {
            if (weaver != null) {
                discardBodies(world, colony);
            }
            colony.buildings().stream()
                    .filter(one -> one.type().equals(WEAVERY_TYPE))
                    .findFirst()
                    .ifPresent(one -> demolish(world, one, plan));
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Ратуша растёт до третьего уровня, и колония становится городом.
     * <p>
     * Ступень «город» была обещана пультом и недостижима в мире: схемы
     * ратуши третьего уровня попросту не существовало, и кнопка
     * «Улучшить» отвечала «выше некуда». Проверка держит обещание —
     * схема есть, улучшение проходит, ступень поднимается.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth", tickLimit = 600)
    public void townHallGrowsToTheThirdLevel(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic third = schematic(context, new Identifier("villagepax",
                "norman/town_hall_lvl3"));

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos at = context.getAbsolutePos(new BlockPos(2, 1, 2));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building seat = null;

        try {
            seat = plan(colony, at, TOWN_HALL_TYPE, BlockRotation.NONE);
            seat.setLevel(3);
            seat.restartBuilding();
            stockFor(world, colony, third);
            if (BuildJob.advance(world, manager, colony.id(), seat.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ратуша третьего уровня не встала: шаг "
                        + seat.nextStep() + " из " + third.plan().steps().size());
                return;
            }

            Levels.refresh(colony);
            if (colony.level() != SettlementLevel.TOWN) {
                context.throwGameTestException("Ратуша третьего уровня стоит, а колония "
                        + "всё ещё " + colony.level().id());
            }
        } finally {
            if (seat != null) {
                demolish(world, seat, third);
            }
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Житель доходит внутрь дома и внутрь поля — своими ногами.
     * <p>
     * Жалоба заказчика, повторённая в четвёртый раз: «жители не могут
     * попасть как в дом, так и на ферму». Три прошлых починки мерили
     * <b>высоту ступени</b> — и мерили верно, а войти всё равно нельзя.
     * Значит, мерили не то.
     * <p>
     * Эта проверка не мерит ничего. Она спрашивает <b>ванильный поиск
     * пути</b> — тот самый, которым ходят жители: построй дорогу отсюда
     * вон туда. Не построил — войти нельзя, и неважно, что там со
     * ступенями. Ровно этот вопрос задаёт себе житель каждую секунду,
     * и ровно на него до сих пор никто не отвечал.
     * <p>
     * Здание ставится на цоколь <b>выше земли вокруг</b> — так, как оно
     * и выходит в игре на склоне, потому что мод сам подсыпает опору.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "walkin", tickLimit = 600)
    public void citizensCanWalkIntoHouseAndFarm(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        // Высота — вместо простора. Делянки игровых проверок стоят
        // в двенадцати блоках друг от друга, а этой площадке нужно
        // пятнадцать: соседи затирали бы её ровно тогда, когда мир общий
        // и проверки идут разом. По высоте соседей нет, и разъехаться
        // вверх дешевле, чем ужимать землю до бесполезной.
        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 41, 0));
        // Земля вокруг на два блока ниже пола зданий: так и выходит
        // в игре на склоне, и ровно так выглядят снимки заказчика.
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(3, 42, 2));
        BlockPos farmAt = context.getAbsolutePos(new BlockPos(3, 42, 10));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);

        Building house;
        Building farm;
        CitizenEntity walker;
        BlockPos inHouse;
        BlockPos inFarm;
        BuildJob.Outcome built;
        BuildJob.Outcome grown;

        // Мир у игровых проверок общий, и брошенная плита камня валит
        // не эту проверку, а соседнюю — через прогон, непонятно отчего.
        // Поэтому за собой убирают оба исхода: и провал посреди стройки,
        // и разбор на двадцатом тике.
        try {
            // Площадка ровно под нужду: дом, поле, место жителя и шаг
            // спуска вокруг. Мир у игровых проверок общий, и лишние
            // двадцать блоков камня — это не запас, а чужая площадка.
            for (int x = -2; x <= 13; x++) {
                for (int z = -2; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 40, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            house = plan(colony, houseAt, HOUSE_TYPE, BlockRotation.NONE);
            stockFor(world, colony, housePlan);
            built = BuildJob.advance(world, manager, colony.id(), house.id(), 20_000);
            farm = plan(colony, farmAt, FARM_TYPE, BlockRotation.NONE);
            stockFor(world, colony, farmPlan);
            grown = BuildJob.advance(world, manager, colony.id(), farm.id(), 20_000);

            Citizen citizen = Citizen.newborn("Пешеход", "", NORMAN, Gender.MALE);
            citizen.setPosition(Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(1, 41, 6))));
            colony.addCitizen(citizen);
            walker = CitizenSpawner.spawnBody(world, colony, citizen);

            inHouse = insideOf(world, house, housePlan);
            inFarm = insideOf(world, farm, farmPlan);
        } catch (RuntimeException | Error trouble) {
            cleanUpVillage(world, manager, colony, hall, ground);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            throw trouble;
        }

        // Тело обязано отстояться: ванильная навигация отказывает тому,
        // кто ещё не коснулся земли, а только что появившееся тело висит
        // в воздухе до первого тика. Первая редакция этой проверки на том
        // и споткнулась — и хорошо, что споткнулась на себе, а не на игроке.
        context.runAtTick(20, () -> {
            try {
                if (built != BuildJob.Outcome.FINISHED || grown != BuildJob.Outcome.FINISHED) {
                    context.throwGameTestException("Не встало: дом " + built + ", поле " + grown);
                    return;
                }
                if (walker == null || inHouse == null || inFarm == null) {
                    context.throwGameTestException("Некому или некуда идти: тело "
                            + (walker != null) + ", в доме " + inHouse + ", на поле " + inFarm);
                    return;
                }

                String toHouse = whyCannotReach(walker, inHouse);
                if (toHouse != null) {
                    context.throwGameTestException(walkFailure("В дом не войти", toHouse,
                            world, house, housePlan, walker, inHouse));
                }
                String toFarm = whyCannotReach(walker, inFarm);
                if (toFarm != null) {
                    context.throwGameTestException(walkFailure("На поле не войти", toFarm,
                            world, farm, farmPlan, walker, inFarm));
                }
            } finally {
                // Сносить дом и поле отдельно не нужно: уборка деревни
                // разбирает все её здания и разгоняет тела сама.
                cleanUpVillage(world, manager, colony, hall, ground);
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /**
     * Ровная клетка перед обрывом не обманывает крыльцо.
     * <p>
     * Это та самая земля, на которой мод и попался: у порога площадка
     * шириной в шаг, а за ней уступ в два блока. Ровно так лежит склон,
     * подсыпанный опорой, и ровно это видно на снимках заказчика.
     * <p>
     * Прежнее крыльцо доходило до ровной клетки, объявляло дело сделанным
     * и выходило — ни одной ступени. Прежние проверки этого <b>не ловили</b>:
     * они строили на ровной плите, где обрыв начинается сразу за порогом,
     * и одной ступени хватало. Ошибку нашёл заказчик, четвёртый раз подряд.
     * <p>
     * Поэтому земля тут нарочно с уступом, а судит по-прежнему ванильный
     * поиск пути: дойдёт житель внутрь — крыльцо своё дело сделало.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "walkin", tickLimit = 600)
    public void aFlatCellBeforeTheDropDoesNotFoolThePorch(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        // Высота — вместо простора. Делянки игровых проверок стоят
        // в двенадцати блоках друг от друга, а этой площадке нужно
        // пятнадцать: соседи затирали бы её ровно тогда, когда мир общий
        // и проверки идут разом. По высоте соседей нет, и разъехаться
        // вверх дешевле, чем ужимать землю до бесполезной.
        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 82, 0));
        BlockPos houseAt = context.getAbsolutePos(new BlockPos(4, 76, 4));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);

        Building house;
        CitizenEntity walker;
        BlockPos inHouse;
        BuildJob.Outcome built;

        try {
            house = plan(colony, houseAt, HOUSE_TYPE, BlockRotation.NONE);

            // Высота порога спрашивается у чертежа до стройки: землю надо
            // разложить относительно него, а не наугад.
            int door = Access.entrances(house, housePlan).get(0).getY();
            Vec3i size = BuildSite.rotatedSize(housePlan.size(), BlockRotation.NONE);

            for (int dx = -3; dx <= size.getX() + 2; dx++) {
                for (int dz = -3; dz <= size.getZ() + 2; dz++) {
                    boolean under = dx >= 0 && dz >= 0 && dx < size.getX() && dz < size.getZ();
                    boolean ledge = dx >= -1 && dz >= -1 && dx <= size.getX() && dz <= size.getZ();
                    // Под домом — опора, кольцом вокруг — площадка вровень
                    // с порогом, дальше — земля на два блока ниже неё.
                    int top = under ? houseAt.getY() - 1 : (ledge ? door - 1 : door - 3);
                    BlockPos at = houseAt.add(dx, 0, dz).withY(top);
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            stockFor(world, colony, housePlan);
            built = BuildJob.advance(world, manager, colony.id(), house.id(), 20_000);

            Citizen citizen = Citizen.newborn("Ходок", "", NORMAN, Gender.MALE);
            citizen.setPosition(Vec3d.ofBottomCenter(houseAt.add(-3, 0, -3).withY(door - 2)));
            colony.addCitizen(citizen);
            walker = CitizenSpawner.spawnBody(world, colony, citizen);

            inHouse = insideOf(world, house, housePlan);
        } catch (RuntimeException | Error trouble) {
            cleanUpVillage(world, manager, colony, hall, ground);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            throw trouble;
        }

        context.runAtTick(20, () -> {
            try {
                if (built != BuildJob.Outcome.FINISHED) {
                    context.throwGameTestException("Дом не встал: " + built);
                    return;
                }
                if (walker == null || inHouse == null) {
                    context.throwGameTestException("Некому или некуда идти: тело "
                            + (walker != null) + ", в доме " + inHouse);
                    return;
                }
                String trouble = whyCannotReach(walker, inHouse);
                if (trouble != null) {
                    context.throwGameTestException(walkFailure(
                            "С уступа в дом не войти", trouble,
                            world, house, housePlan, walker, inHouse));
                }
            } finally {
                cleanUpVillage(world, manager, colony, hall, ground);
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    /**
     * Дерево у калитки валит сам билдер, и на поле можно войти.
     * <p>
     * Жалоба заказчика: «построил ферму, а зайти нельзя, дерево блокирует».
     * Дерево росло вплотную к калитке, но <b>за следом здания</b>, а план
     * расчищал только след. Крыльцо потом честно клало ступени под стволом,
     * и войти всё равно было нельзя: мерили одно, мешало другое.
     * <p>
     * Теперь подход ко входу — часть плана: три шага от порога в рост
     * человека. Билдер валит дерево сам, тем же шагом расчистки, каким
     * убирает бугор под фундаментом, и остаток ствола не висит над
     * проходом.
     * <p>
     * Судит по-прежнему ванильный поиск пути: «расчищено» — это не когда
     * клетка пуста по нашей мерке, а когда житель дошёл.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "walkin", tickLimit = 600)
    public void theBuilderFellsTheTreeAtTheGate(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic farmPlan = schematic(context, FARM_SCHEMATIC);

        // Высота — вместо простора. Делянки игровых проверок стоят
        // в двенадцати блоках друг от друга, а этой площадке нужно
        // пятнадцать: соседи затирали бы её ровно тогда, когда мир общий
        // и проверки идут разом. По высоте соседей нет, и разъехаться
        // вверх дешевле, чем ужимать землю до бесполезной.
        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 103, 0));
        BlockPos farmAt = context.getAbsolutePos(new BlockPos(4, 103, 4));
        List<BlockPos> ground = new ArrayList<>();

        Settlement colony = colonyWithBuilder(world, manager, hall);

        Building farm;
        CitizenEntity walker;
        BlockPos inFarm;
        BlockPos trunk;
        BuildJob.Outcome grown;

        try {
            // Ровная земля: единственная помеха в этой проверке — дерево.
            for (int x = -1; x <= 13; x++) {
                for (int z = -1; z <= 13; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 102, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    ground.add(at);
                }
            }

            farm = plan(colony, farmAt, FARM_TYPE, BlockRotation.NONE);

            // Дуб вырос ровно там, где выходят с поля.
            BlockPos gate = Access.entrances(farm, farmPlan).get(0);
            trunk = gate.offset(Access.awayFrom(farm, farmPlan, gate));
            for (int up = 0; up < 5; up++) {
                world.setBlockState(trunk.up(up), Blocks.OAK_LOG.getDefaultState());
            }
            world.setBlockState(trunk.up(5), Blocks.OAK_LEAVES.getDefaultState());

            stockFor(world, colony, farmPlan);
            grown = BuildJob.advance(world, manager, colony.id(), farm.id(), 20_000);

            Citizen citizen = Citizen.newborn("Прохожий", "", NORMAN, Gender.MALE);
            citizen.setPosition(Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(0, 103, 8))));
            colony.addCitizen(citizen);
            walker = CitizenSpawner.spawnBody(world, colony, citizen);

            inFarm = insideOf(world, farm, farmPlan);

            if (grown != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Поле не встало: " + grown);
            }
            if (walker == null || inFarm == null) {
                context.throwGameTestException("Некому или некуда идти: тело "
                        + (walker != null) + ", на поле " + inFarm);
            }

            // Глазами — сразу: проход в рост человека свободен, и обрубок
            // ствола над ним не висит. Ждать тика тут нечего, а падение
            // со словами читается лучше, чем падение по времени.
            for (int up = 0; up < 5; up++) {
                if (!world.getBlockState(trunk.up(up)).isAir()) {
                    context.throwGameTestException("Дерево у калитки не свалено: на "
                            + trunk.up(up).toShortString() + " стоит "
                            + world.getBlockState(trunk.up(up)).getBlock());
                }
            }
        } catch (RuntimeException | Error trouble) {
            cleanUpVillage(world, manager, colony, hall, ground);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
            throw trouble;
        }

        context.runAtTick(20, () -> {
            try {
                // А ногами — на двадцатом тике: ванильная навигация
                // отказывает телу, которое ещё не коснулось земли.
                String trouble = whyCannotReach(walker, inFarm);
                if (trouble != null) {
                    context.throwGameTestException(walkFailure("На поле не войти", trouble,
                            world, farm, farmPlan, walker, inFarm));
                }
            } finally {
                cleanUpVillage(world, manager, colony, hall, ground);
                world.setBlockState(hall, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }


    /** Рассказ о том, почему не дойти: порог, ступени и всё, что рядом. */
    private static String walkFailure(String what, String why, ServerWorld world,
                                      Building building, Schematic schematic,
                                      CitizenEntity walker, BlockPos target) {
        StringBuilder story = new StringBuilder(what + ": " + why
                + ". Цель " + target.toShortString() + ", житель "
                + walker.getBlockPos().toShortString());
        for (BlockPos door : Access.entrances(building, schematic)) {
            story.append(" | порог ").append(door.toShortString())
                    .append(" сам=").append(world.getBlockState(door).getBlock())
                    .append(" под=").append(world.getBlockState(door.down()).getBlock());
        }
        for (BlockPos spot : Access.stepSpots(building, schematic)) {
            // Полоса возможных мест широка, а рассказывать стоит о занятых:
            // пустые клетки только прячут в себе те, где что-то лежит.
            if (!world.getBlockState(spot).isAir()) {
                story.append(" | ступень ").append(spot.toShortString())
                        .append("=").append(world.getBlockState(spot).getBlock());
            }
        }
        // Сколько положит крыльцо, если позвать его прямо сейчас: ноль
        // значит «отказывается», больше нуля — «его не звали».
        story.append(" | крыльцо видит: ").append(Access.story(world, building, schematic))
                .append(" | повторный вызов положил ")
                .append(Access.porch(world, building, schematic));
        return story.toString();
    }

    /**
     * Почему житель не дойдёт до этой точки — или пусто, если дойдёт.
     * <p>
     * Спрашивается сам ванильный поиск пути. Это единственная честная
     * мерка проходимости: всё остальное — наши догадки о том, что он
     * считает проходимым.
     */
    private static String whyCannotReach(CitizenEntity walker, BlockPos target) {
        Path path = walker.getNavigation().findPathTo(target, 0);
        if (path == null) {
            return "поиск пути не построил дороги вовсе";
        }
        if (!path.reachesTarget()) {
            BlockPos end = path.getTarget();
            return "дорога обрывается на " + end.toShortString();
        }
        return null;
    }

    /**
     * Место внутри здания, до которого житель обязан доходить.
     * <p>
     * Ищется <b>по миру</b>, а не по меткам схемы: у норманнского дома
     * кровати стоят настоящими блоками, у поля метка одна и та на пугале,
     * и опираться на метки значило бы проверять не то. Годится любая
     * клетка внутри следа, где есть воздух в рост и твёрдая опора под
     * ногами, — ближайшая к середине.
     */
    private static BlockPos insideOf(ServerWorld world, Building building, Schematic schematic) {
        Vec3i size = BuildSite.rotatedSize(schematic.size(), building.rotation());
        BlockPos anchor = building.anchor();
        BlockPos best = null;
        double closest = Double.MAX_VALUE;
        double midX = anchor.getX() + size.getX() / 2.0;
        double midZ = anchor.getZ() + size.getZ() / 2.0;

        for (int dx = 1; dx < size.getX() - 1; dx++) {
            for (int dz = 1; dz < size.getZ() - 1; dz++) {
                for (int dy = 0; dy < size.getY() - 1; dy++) {
                    BlockPos at = anchor.add(dx, dy, dz);
                    // Проходимость меряется столкновениями, а не воздухом:
                    // фермер стоит ПОСРЕДИ моркови, а морковь — не воздух.
                    boolean standable = !world.getBlockState(at.down())
                            .getCollisionShape(world, at.down()).isEmpty();
                    boolean roomToStand = world.getBlockState(at)
                            .getCollisionShape(world, at).isEmpty()
                            && world.getBlockState(at.up())
                            .getCollisionShape(world, at.up()).isEmpty();
                    if (!standable || !roomToStand) {
                        continue;
                    }
                    double away = Math.abs(at.getX() + 0.5 - midX)
                            + Math.abs(at.getZ() + 0.5 - midZ);
                    if (away < closest) {
                        closest = away;
                        best = at;
                    }
                }
            }
        }
        return best;
    }


    /**
     * В каждое здание мода можно войти с земли — во все и у обоих народов.
     * <p>
     * Заказчик написал про дом и поле, но беда была не в них: пол любого
     * здания стоит на цоколе, под цоколь мод подсыпает опору, и порог
     * оказывается выше земли вокруг. Чинить по одному зданию — значит
     * возвращаться к этому каждый раз, когда в моде появится новое;
     * их уже десять.
     * <p>
     * Поэтому проверяется <b>весь список схем разом</b> и на той высоте,
     * какая и выходит в игре на склоне: здание ставится на два блока выше
     * земли. У каждого входа снаружи обязана быть опора не ниже чем
     * на шаг от порога — всё остальное сделают ноги.
     * <p>
     * Ходьбу как таковую проверяет соседняя проверка, спрашивая ванильный
     * поиск пути; здесь же — <b>геометрия у порога</b>, зато у всех зданий
     * и быстро.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "walkin", tickLimit = 900)
    public void everyBuildingLetsYouInFromTheGround(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        List<Identifier> all = new ArrayList<>(SchematicLoader.ids());
        all.sort(java.util.Comparator.comparing(Identifier::toString));

        List<String> complaints = new ArrayList<>();
        List<BlockPos> ground = new ArrayList<>();

        // Высота — вместо простора. Делянки игровых проверок стоят
        // в двенадцати блоках друг от друга, а этой площадке нужно
        // пятнадцать: соседи затирали бы её ровно тогда, когда мир общий
        // и проверки идут разом. По высоте соседей нет, и разъехаться
        // вверх дешевле, чем ужимать землю до бесполезной.
        BlockPos hall = context.getAbsolutePos(new BlockPos(10, 139, 3));
        // На два блока выше земли: так здание и встаёт после подсыпки опоры.
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 141, 0));

        try {
            for (int x = -9; x <= 20; x++) {
                for (int z = -9; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 138, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            for (Identifier id : all) {
                Schematic schematic = SchematicLoader.get(id).orElseThrow();
                Identifier type = BuildJob.buildingTypeOf(id).orElse(null);
                if (type == null) {
                    continue;
                }

                Identifier culture = new Identifier(type.getNamespace(),
                        type.getPath().split("/")[0]);
                Settlement colony = colonyWithBuilder(world, manager, hall, culture);
                Building site = new Building(UUID.randomUUID(), type,
                        BuildJob.levelOf(id).orElse(1), anchor, BlockRotation.NONE,
                        BuildProgress.PLANNED, List.of());
                colony.addBuilding(site);

                try {
                    stockFor(world, colony, schematic);
                    BuildJob.Outcome outcome =
                            BuildJob.advance(world, manager, colony.id(), site.id(), 40_000);
                    if (outcome != BuildJob.Outcome.FINISHED) {
                        // Недостроенное здание молча прошло бы проверку: порог
                        // висит в воздухе, под ним ровная площадка, спуск
                        // безупречен — и вход при этом не существует.
                        complaints.add(id + ": не достроилось (" + outcome
                                + "), вход проверять не на чем");
                        continue;
                    }

                    List<BlockPos> doors = Access.entrances(site, schematic);
                    if (doors.isEmpty()) {
                        // Здание без входа — отдельный разговор: у поленницы
                        // и у второго уровня дверь наследуется от первого.
                        continue;
                    }
                    for (BlockPos door : doors) {
                        String trouble = descentTrouble(world, site, schematic, door);
                        if (trouble != null) {
                            complaints.add(id + ": " + trouble);
                        }
                    }
                } finally {
                    demolish(world, site, schematic);
                    manager.remove(colony.id());
                }
            }

            if (!complaints.isEmpty()) {
                context.throwGameTestException("Здания, в которые не войти с земли:\n  "
                        + String.join("\n  ", complaints));
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    private static final Identifier STALL_TYPE =
            new Identifier("villagepax", "norman/market_stall");
    private static final Identifier STALL_SCHEMATIC =
            new Identifier("villagepax", "norman/market_stall_lvl1");
    private static final Identifier FARMER = new Identifier("villagepax", "farmer");

    /**
     * Колония начинается с крыши над головой и поля под боком.
     * <p>
     * Заказчик: «добавь для начала колонии гарантированный дом и ферму,
     * чтоб уже были построены». До этого первый час игры выглядел так:
     * ратуша, один строитель и пустырь. Спать негде, есть нечего, а чтобы
     * появился первый дом, надо разметить его, добыть материалы, завезти
     * и дождаться стройки — и всё это <b>до</b> того, как в моде случится
     * хоть что-то.
     * <p>
     * Проверяется не запись в данных, а <b>блоки в мире</b>: здание,
     * записанное готовым и не поставленное, — ровно та беда, от которой
     * игрок и жаловался, только теперь молча.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "founding", tickLimit = 600)
    public void colonyStartsWithARoofAndAField(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement colony = null;

        try {
            for (int x = -18; x <= 18; x++) {
                for (int z = -18; z <= 18; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            FoundingOutcome outcome = ColonyFounder.foundAt(world, UUID.randomUUID(), NORMAN, hall);
            if (!(outcome instanceof FoundingOutcome.Founded founded)) {
                context.throwGameTestException("Колония не основана: "
                        + ((FoundingOutcome.Refused) outcome).translationKey());
                return;
            }
            colony = founded.settlement();

            Building home = null;
            Building field = null;
            for (Building site : colony.buildings()) {
                if (BuildingTypes.isHome(site.type())) {
                    home = site;
                }
                if (BuildingTypes.employs(site.type(), FARMER)) {
                    field = site;
                }
            }

            if (home == null || field == null) {
                context.throwGameTestException("Колонии не дали надела: "
                        + colony.buildings().stream().map(site -> site.type() + " "
                                + site.progress()).toList());
                return;
            }

            for (Building site : List.of(home, field)) {
                if (site.progress() != BuildProgress.DONE) {
                    context.throwGameTestException(site.type() + " числится "
                            + site.progress() + ", а обещано готовым");
                }
                Schematic plan = SchematicLoader.get(BuildJob.schematicId(site)).orElseThrow();
                int raised = raisedBlocks(world, site, plan);
                if (raised < plan.blocks().size() / 2) {
                    context.throwGameTestException(site.type() + " записано готовым, а в мире "
                            + raised + " блоков из " + plan.blocks().size()
                            + ": здание есть только на бумаге");
                }
            }
        } finally {
            cleanUpVillage(world, manager, colony, hall, meadow);
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Снести всё, кроме ратуши: первый надел колонии проверкам стройки
     * только мешает — он встаёт даром и занимает место у ратуши.
     */
    private static void razeHolding(ServerWorld world, Settlement colony) {
        for (Building site : colony.buildings()) {
            if (BuildingTypes.isTownHall(site.type())) {
                continue;
            }
            SchematicLoader.get(BuildJob.schematicId(site))
                    .ifPresent(plan -> demolish(world, site, plan));
        }
    }

    /** Сколько клеток следа здания заняты не воздухом. */
    private static int raisedBlocks(ServerWorld world, Building site, Schematic schematic) {
        int standing = 0;
        for (Schematic.PalettedBlock block : schematic.blocks()) {
            BlockPos at = BuildJob.worldPos(site, schematic.size(), block.pos());
            if (!world.getBlockState(at).isAir()) {
                standing++;
            }
        }
        return standing;
    }

    /**
     * За прилавком стоит купец, а не старейшина.
     * <p>
     * Заказчик: «ларёк для купца, чтоб у него покупать и продавать вещи,
     * а не у старейшины — разделим обязанности». До этого деревня была
     * одним человеком с четырьмя руками: старейшина давал квесты, принимал
     * подарки, мирился и торговал.
     * <p>
     * Проверяется <b>обе стороны разделения</b>: у купца товар есть,
     * у старейшины его нет. Одной половины мало — прилавок, открытый
     * у обоих, выглядел бы как работающее разделение ровно до того мига,
     * когда игрок подойдёт к старейшине.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests", tickLimit = 600)
    public void theCounterIsKeptByTheMerchantNotTheElder(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала: место занято="
                        + manager.isSettled(centre) + ", помеха=" + whoBlocks(manager, centre));
                return;
            }

            Citizen merchant = village.citizens().stream()
                    .filter(citizen -> citizen.profession()
                            .filter(Villages.MERCHANT::equals).isPresent())
                    .findFirst().orElse(null);
            if (merchant == null) {
                context.throwGameTestException("В деревне нет купца: "
                        + village.citizens().stream().map(citizen -> citizen.profession()
                                .map(Identifier::toString).orElse("без дела")).toList());
                return;
            }

            Building stall = village.buildings().stream()
                    .filter(site -> BuildingTypes.employs(site.type(), Villages.MERCHANT))
                    .findFirst().orElse(null);
            if (stall == null || stall.progress() != BuildProgress.DONE) {
                context.throwGameTestException("Ларёк не стоит: "
                        + village.buildings().stream().map(site -> site.type() + " "
                                + site.progress()).toList());
                return;
            }
            // И у купца есть где стоять: ларёк без рабочего места — сарай.
            if (Workplaces.of(village, merchant).isEmpty()) {
                context.throwGameTestException("Купцу не досталось ларька");
            }

            UUID player = UUID.randomUUID();
            Warehouse wares = Warehouse.of(world, village);
            SimpleInventory pockets = new SimpleInventory(9);

            QuestView atCounter = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.MERCHANT, wares).orElse(null);
            QuestView atElder = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.ELDER, wares).orElse(null);
            if (atCounter == null || atElder == null) {
                context.throwGameTestException("Разговор не собрался: купец=" + atCounter
                        + ", старейшина=" + atElder);
                return;
            }

            if (!atCounter.trades()) {
                context.throwGameTestException("У купца пустой прилавок");
            }
            if (!atCounter.counter()) {
                context.throwGameTestException("Разговор с купцом не считается прилавком: "
                        + "игрок увидит лишние вкладки вместо товара");
            }
            if (atElder.trades()) {
                context.throwGameTestException("Старейшина всё ещё торгует: "
                        + atElder.stalls().size() + " сделок на прилавке");
            }
            // А квесты, наоборот, остались у него.
            if (Quests.offered(village, player, Villages.ELDER).isEmpty()) {
                context.throwGameTestException("Старейшине нечего предложить игроку");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Без купца прилавок держит старейшина.
     * <p>
     * Разделение обязанностей не должно оборачиваться тупиком: купца может
     * унести набег, а нанимают нового не в тот же день. Деревня, молча
     * переставшая торговать, выглядит сломанной — это уже проходили
     * с пустой полкой при первой встрече.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests", tickLimit = 600)
    public void withoutAMerchantTheElderKeepsTheCounter(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos centre = context.getAbsolutePos(new BlockPos(0, 9, 0));
        List<BlockPos> meadow = new ArrayList<>();
        Settlement village = null;

        try {
            for (int x = -20; x <= 20; x++) {
                for (int z = -20; z <= 20; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                    meadow.add(at);
                }
            }

            village = Villages.found(world, NORMAN, centre).orElse(null);
            if (village == null) {
                context.throwGameTestException("Деревня не встала");
                return;
            }

            // Купца не стало: набег, мор, дорога — неважно.
            for (Citizen citizen : List.copyOf(village.citizens())) {
                if (citizen.profession().filter(Villages.MERCHANT::equals).isPresent()) {
                    citizen.entityUuid().map(world::getEntity).ifPresent(Entity::discard);
                    village.removeCitizen(citizen.id());
                }
            }

            if (!Villages.counterKeeper(village).equals(Villages.ELDER)) {
                context.throwGameTestException("Без купца прилавок остался за "
                        + Villages.counterKeeper(village));
            }

            QuestView atElder = QuestNet.viewOf(manager, village, UUID.randomUUID(),
                    new SimpleInventory(9), Villages.ELDER, Warehouse.of(world, village))
                    .orElse(null);
            if (atElder == null || !atElder.trades()) {
                context.throwGameTestException("Без купца торговать стало не с кем: "
                        + "деревня молча перестала быть деревней");
            }
        } finally {
            cleanUpVillage(world, manager, village, centre, meadow);
        }

        context.complete();
    }

    /**
     * Купец приходит за свой прилавок и там остаётся.
     * <p>
     * Ремесло без выработки: купец производит <b>место встречи</b>. Ларёк
     * без купца — декорация, купец без ларька — прохожий, которого игрок
     * ловит щелчками по всей деревне. Поэтому проверяется ровно то, ради
     * чего работа заведена: где он стоит, когда работает.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "trade", tickLimit = 600)
    public void merchantStandsBehindHisCounter(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic stallPlan = schematic(context, STALL_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building stall = plan(colony, anchor, STALL_TYPE, BlockRotation.NONE);

        try {
            stockFor(world, colony, stallPlan);
            if (BuildJob.advance(world, manager, colony.id(), stall.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ларёк не встал");
                return;
            }

            BlockPos counter = Workplaces.stations(stall).stream().findFirst().orElse(null);
            if (counter == null) {
                context.throwGameTestException("В ларьке нет рабочего места: торговать негде");
                return;
            }

            Citizen merchant = hireWithBody(world, colony, Villages.MERCHANT,
                    anchor.add(0, 1, 0).north(6));
            Workplaces.assign(world, colony);
            if (Workplaces.of(colony, merchant).isEmpty()) {
                context.throwGameTestException("Купцу не досталось ларька, хотя он один");
            }

            runWork(world, manager, colony, merchant, 8, Schedule.MORNING_WORK);

            CitizenEntity body = bodyOf(world, colony, merchant);
            double away = body.getBlockPos().getSquaredDistance(counter);
            if (away > WorkContext.ARRIVAL_REACH * WorkContext.ARRIVAL_REACH) {
                context.throwGameTestException("Купец не за прилавком: он на "
                        + body.getBlockPos().toShortString() + ", прилавок на "
                        + counter.toShortString());
            }
        } finally {
            demolish(world, stall, stallPlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    private static final Identifier MARKET_TYPE = new Identifier("villagepax", "norman/market");
    private static final Identifier MARKET_SCHEMATIC =
            new Identifier("villagepax", "norman/market_lvl1");

    /**
     * Ратуша растёт до четвёртого уровня, и колония становится столицей.
     * <p>
     * Ступень «столица» была в лестнице с первой недели и недостижима:
     * схемы ратуши четвёртого уровня не существовало, и на третьем
     * «Улучшить» отвечало «выше некуда». Обещание держится схемой.
     * <p>
     * И растёт она <b>вширь</b>, а не вверх, и это не украшение: третий
     * ярус пришлось укоротить на ряд стен и ярус кровли, потому что
     * до верха билдер не дотягивался. Четвёртый растёт гульбищем вокруг —
     * на восток и юг, чтобы якорь здания остался на месте.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "growth", tickLimit = 900)
    public void townHallGrowsWideIntoACapital(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic fourth = schematic(context, new Identifier("villagepax",
                "norman/town_hall_lvl4"));

        BlockPos hall = context.getAbsolutePos(new BlockPos(0, 1, 0));
        BlockPos at = context.getAbsolutePos(new BlockPos(2, 1, 2));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building seat = null;

        try {
            seat = plan(colony, at, TOWN_HALL_TYPE, BlockRotation.NONE);
            seat.setLevel(4);
            seat.restartBuilding();
            stockFor(world, colony, fourth);
            if (BuildJob.advance(world, manager, colony.id(), seat.id(), 40_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ратуша четвёртого уровня не встала: шаг "
                        + seat.nextStep() + " из " + fourth.plan().steps().size());
                return;
            }

            Levels.refresh(colony);
            if (colony.level() != SettlementLevel.CAPITAL) {
                context.throwGameTestException("Ратуша четвёртого уровня стоит, а колония "
                        + "всё ещё " + colony.level().id());
            }

            // И ступень открывает дело, а не только число: рынок был заперт,
            // а теперь его можно строить.
            com.villagepax.core.building.BuildingType market =
                    BuildingTypes.get(MARKET_TYPE).orElse(null);
            if (market == null) {
                context.throwGameTestException("Рынка нет в данных");
                return;
            }
            if (market.openTo(SettlementLevel.TOWN)) {
                context.throwGameTestException("Рынок открыт городу: тогда столица "
                        + "не открывает ничего");
            }
            if (!market.openTo(colony.level())) {
                context.throwGameTestException("Столица стоит, а рынок всё ещё заперт");
            }
        } finally {
            if (seat != null) {
                demolish(world, seat, fourth);
            }
            cleanUpVillage(world, manager, colony, hall, List.of());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * К рынку обоз приходит каждый день, а к колонии без рынка — раз в три.
     * <p>
     * Это и есть награда за столицу, и она из тех, которые видно, не
     * открывая пульта: у ворот стоит чужой обоз, и стоит он там каждое
     * утро. Ступень, дающая только предел населения, наградой
     * не ощущается — это уже проходили с первыми тремя.
     * <p>
     * Считается по дням числом, а не по игровым суткам: «раз в три дня»
     * иначе не проверить — тест не может прождать трое суток, а шесть
     * вызовов в одном тике для мода один и тот же день.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "caravan", tickLimit = 900)
    public void theMarketBringsACaravanEveryDay(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic marketPlan = schematic(context, MARKET_SCHEMATIC);

        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(20, 2, 2));
        BlockPos marketAt = context.getAbsolutePos(new BlockPos(4, 2, 5));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = null;
        Settlement village = null;
        Building market = null;

        try {
            for (int x = -2; x <= 26; x++) {
                for (int z = -4; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Торговая", villageAt);
            world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
            manager.add(village);

            colony = colonyWithBuilder(world, manager, colonyAt);

            // Деревне есть чем торговать — иначе обоз не выйдет вовсе,
            // и проверка мерила бы пустоту.
            Warehouse store = Warehouse.of(world, village);
            store.add(new ItemStack(Items.BREAD, 40));
            Coins.earn(store.coins(), 64);

            int without = countVisits(world, manager, village, colony);

            market = plan(colony, marketAt, MARKET_TYPE, BlockRotation.NONE);
            stockFor(world, colony, marketPlan);
            if (BuildJob.advance(world, manager, colony.id(), market.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Рынок не встал");
                return;
            }
            Warehouse.of(world, village).add(new ItemStack(Items.BREAD, 40));
            Coins.earn(Warehouse.of(world, village).coins(), 64);

            int with = countVisits(world, manager, village, colony);

            if (with <= without) {
                context.throwGameTestException("Рынок не позвал обозы чаще: без рынка "
                        + without + " прихода за шесть дней, с рынком " + with);
            }
            if (with < 6) {
                context.throwGameTestException("К рынку обоз приходит не каждый день: "
                        + with + " прихода за шесть дней");
            }
        } finally {
            if (market != null) {
                demolish(world, market, marketPlan);
            }
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, colonyAt, floor);
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
            world.setBlockState(colonyAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Сколько раз за шесть дней деревня послала обоз к этой колонии.
     * <p>
     * Гость отпускается сразу: обоз, оставшийся у ворот, не даёт прийти
     * следующему, и «каждый день» превратилось бы в «один раз».
     */
    private static int countVisits(ServerWorld world, SettlementManager manager,
                                   Settlement village, Settlement colony) {
        int came = 0;
        for (long day = 0; day < 6; day++) {
            Caravans.sendIfDue(world, manager, village, day);
            Settlement gates = manager.byId(colony.id()).orElseThrow();
            came += gates.visitors().size();
            for (Caravan guest : List.copyOf(gates.visitors())) {
                gates.seeOff(guest.id());
            }
        }
        return came;
    }

    /**
     * Союз заключают друзья и только с городом — и он держится дружбой.
     * <p>
     * Лестница ступеней обещала союзы с города, а доверие до сих пор
     * меняло только цены на прилавке. Проверяется вся сделка: чего не
     * хватает на каждом шагу, что дар уходит деревне и что союз
     * <b>перестаёт действовать</b>, если игрок растерял дружбу.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "peace", tickLimit = 600)
    public void anAllianceNeedsFriendshipATownAndGold(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 2, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(14, 2, 2));
        List<BlockPos> floor = new ArrayList<>();

        Settlement village = null;
        Settlement colony = null;

        try {
            for (int x = -2; x <= 18; x++) {
                for (int z = -2; z <= 8; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 1, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
            world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
            manager.add(village);

            colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
            manager.add(colony);

            SimpleInventory pockets = new SimpleInventory(9);

            // Чужак с золотом: денег мало, дружбы нет.
            Coins.earn(pockets, Alliance.PRICE);
            if (Alliance.judge(village, colony, player, pockets)
                    != Alliance.Verdict.NOT_FRIENDS) {
                context.throwGameTestException("Союз предлагают чужаку: "
                        + Alliance.judge(village, colony, player, pockets));
            }

            // Друг, но хутор.
            village.addReputation(player, Standing.FRIEND.from());
            if (Alliance.judge(village, colony, player, pockets) != Alliance.Verdict.NO_TOWN) {
                context.throwGameTestException("Союз предлагают хутору: "
                        + Alliance.judge(village, colony, player, pockets));
            }

            // Друг и город, но без золота.
            colony.setLevel(SettlementLevel.TOWN);
            SimpleInventory empty = new SimpleInventory(9);
            if (Alliance.judge(village, colony, player, empty) != Alliance.Verdict.NO_COIN) {
                context.throwGameTestException("Союз заключают даром: "
                        + Alliance.judge(village, colony, player, empty));
            }

            // И наконец всё сошлось.
            Alliance.Outcome outcome = Alliance.forge(world, manager, village, colony, player,
                    pockets, 7L, left -> { });
            if (!outcome.forged()) {
                context.throwGameTestException("Союз не заключён: " + outcome.verdict());
                return;
            }
            if (Coins.total(pockets) != 0) {
                context.throwGameTestException("Дар не ушёл: у игрока осталось "
                        + Coins.total(pockets));
            }
            if (Coins.total(Warehouse.of(world, village).coins()) != Alliance.PRICE) {
                context.throwGameTestException("Дар не дошёл до деревни: в кошеле "
                        + Coins.total(Warehouse.of(world, village).coins()));
            }
            if (!manager.byId(village.id()).orElseThrow().isAllyOf(player)) {
                context.throwGameTestException("Союз заключён, а деревня об этом не знает");
            }

            // И союз держится дружбой, а не записью.
            Settlement allied = manager.byId(village.id()).orElseThrow();
            allied.addReputation(player, -Standing.FRIEND.from());
            if (allied.isAllyOf(player)) {
                context.throwGameTestException("Союз пережил утраченную дружбу: доверие "
                        + allied.reputationOf(player));
            }
        } finally {
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, colonyAt, floor);
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Союзники приходят на набег и бьют налётчиков.
     * <p>
     * Это и есть всё содержание союза: не строка в пульте, а мечи у ворот.
     * Проверяется то, ради чего он заключается, — что подмога пришла,
     * что её не считают налётчиками свои же проверки, и что обе стороны
     * взяли друг друга на прицел.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid", tickLimit = 600)
    public void alliesComeWhenRaidersDo(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        // Своя полка по высоте: делянки проверок стоят в двенадцати блоках,
        // а этой нужна площадка шире — соседи затирали бы её.
        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 61, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(10, 61, 6));
        BlockPos friendAt = context.getAbsolutePos(new BlockPos(2, 61, 14));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        manager.add(colony);
        Settlement friend = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Верный", friendAt);
        friend.addReputation(player, Standing.FRIEND.from());
        friend.makeAlly(player, 1L);
        manager.add(friend);

        WarParty party = new WarParty(UUID.randomUUID(), UUID.randomUUID(), MAYA,
                musters, 2, 20L, 21L);

        try {
            for (int x = 2; x <= 16; x++) {
                for (int z = 0; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 60, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            manager.update(colony.id(), state -> state.besiege(party, 19L));
            Raids.watch(world, manager, 20L);

            List<CitizenEntity> raiders = Raids.bodiesOf(world, party);
            List<CitizenEntity> helpers = Allies.defendersOf(world, party);

            if (raiders.size() != 2) {
                context.throwGameTestException("Налётчиков " + raiders.size() + " вместо двух: "
                        + "союзников сочли своими");
                return;
            }
            if (helpers.size() != 2) {
                context.throwGameTestException("Союзников пришло " + helpers.size()
                        + " вместо двух");
                return;
            }
            for (CitizenEntity helper : helpers) {
                if (helper.isRaider()) {
                    context.throwGameTestException("Союзник числится налётчиком: "
                            + "его смерть засчитают деревне-обидчице");
                }
            }

            // И обе стороны видят друг в друге врага.
            //
            // Спрашивается НЕ ОДИН РАЗ, а до самого срока: ванильная цель
            // просыпается раз в десяток тиков, случайно, и требует прямой
            // видимости. Проверка, спросившая однажды, мигает — и это
            // не «иногда не работает», а «иногда не успели посмотреть».
            boolean[] met = {false};
            for (int tick = 40; tick <= 200; tick += 40) {
                boolean last = tick > 160;
                context.runAtTick(tick, () -> {
                    if (met[0]) {
                        return;
                    }
                    // Меркой служит КРОВЬ НАЛЁТЧИКА, а не то, на кого он
                    // смотрит прямо сейчас. Прицел — мгновение: бой
                    // кончается за пару секунд, и проверка, спросившая
                    // «целится ли», у победившей стороны получает «нет»
                    // ровно потому, что дело сделано. А бить налётчика
                    // в этой проверке больше некому: жителей у колонии нет,
                    // падать неоткуда.
                    List<CitizenEntity> raidersNow = Raids.bodiesOf(world, party);
                    boolean bled = raidersNow.size() < 2
                            || raidersNow.stream().anyMatch(one -> one.getHealth() < one.getMaxHealth());
                    if (bled) {
                        met[0] = true;
                        tidyUpFight(world, manager, party, friend, friendAt, colony, centre, floor);
                        context.complete();
                        return;
                    }
                    if (!last) {
                        return;
                    }
                    try {
                        context.throwGameTestException("Союзники пришли и не подрались: "
                                + "за десять секунд налётчики не потеряли ни капли крови. "
                                + "Их " + Raids.bodiesOf(world, party).size()
                                + ", союзников " + Allies.defendersOf(world, party).size());
                    } finally {
                        tidyUpFight(world, manager, party, friend, friendAt, colony, centre, floor);
                    }
                });
            }
        } catch (RuntimeException | Error trouble) {
            tidyUpFight(world, manager, party, friend, friendAt, colony, centre, floor);
            throw trouble;
        }
    }

    /** Убрать за боем: тела обеих сторон, обе деревни и площадку. */
    private static void tidyUpFight(ServerWorld world, SettlementManager manager, WarParty party,
                                    Settlement friend, BlockPos friendAt, Settlement colony,
                                    BlockPos centre, List<BlockPos> floor) {
        Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
        Allies.dismiss(world, party);
        cleanUpVillage(world, manager, friend, friendAt, List.of());
        cleanUpVillage(world, manager, colony, centre, floor);
    }


    /**
     * Пульт открывается только хозяину, и это чинит молчащее меню.
     * <p>
     * Жалоба заказчика: «не могу заказать постройку и поставить постройку,
     * не грузит призрак». Причина оказалась не в призраке. Ратуша есть
     * и у деревни народа, и щелчок по ней открывал <b>полный пульт
     * колонии</b> — со списком зданий и кнопками «Заказать». Ни одна
     * из них не работала: сервер отбрасывает намерения по чужому
     * поселению, потому что распоряжаться можно только своим. Молча.
     * <p>
     * Кнопка, которая ничего не делает и ничего не говорит, — худшее,
     * что бывает в меню: игрок не понимает, сломан мод или он сам.
     * Теперь чужая ратуша пульта не открывает вовсе и говорит, что
     * здесь можно на самом деле.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "founding")
    public void aVillageTownHallIsNotYourConsole(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 20, 2));
        BlockPos mineAt = context.getAbsolutePos(new BlockPos(14, 20, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        Settlement mine = Settlement.found(NORMAN, Owner.of(player), "Моя", mineAt);
        Settlement theirs = Settlement.found(NORMAN, Owner.of(stranger), "Чужая", mineAt);

        manager.add(village);
        manager.add(mine);

        try {
            if (TownHallConsole.yours(village, player)) {
                context.throwGameTestException("Пульт деревни народа открывается игроку: "
                        + "в нём все кнопки молчат");
            }
            if (TownHallConsole.yours(theirs, player)) {
                context.throwGameTestException("Пульт чужой колонии открывается игроку");
            }
            if (!TownHallConsole.yours(mine, player)) {
                context.throwGameTestException("Своя колония не пускает хозяина в пульт");
            }

            // И это ровно тот же признак, по которому сервер решает, чьё
            // намерение исполнять: два разных ответа на один вопрос
            // и давали молчащее меню.
            if (Founding.colonyOf(manager, player).map(Settlement::id)
                    .filter(mine.id()::equals).isEmpty()) {
                context.throwGameTestException("Колония игрока не находится по владельцу");
            }
            if (Founding.colonyOf(manager, stranger).isPresent()) {
                context.throwGameTestException("У чужака нашлась колония в этом мире");
            }
        } finally {
            manager.remove(village.id());
            manager.remove(mine.id());
        }

        context.complete();
    }


    /**
     * У каждого заказа есть призрак, и он не пустой.
     * <p>
     * Призрак — единственное, чем игрок выбирает место: нет призрака —
     * нет и постройки, и жаловаться он будет ровно теми словами, какими
     * и пожаловался: «не грузит призрак». Ломается это молча — схема
     * загрузилась, список заказов полон, а показывать нечего, — и потому
     * сверяется списком.
     * <p>
     * Заодно мерится потолок: призрак в восемь тысяч блоков клиент
     * обрежет на середине, и здание покажется недостроенным ещё
     * до стройки.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "orders")
    public void everyOrderHasAGhostToShow(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);

        try {
            List<String> mute = new ArrayList<>();
            for (Identifier schematicId : TownHallView.of(world, colony).offers()) {
                Schematic schematic = SchematicLoader.get(schematicId).orElse(null);
                if (schematic == null) {
                    mute.add(schematicId + ": схемы нет вовсе");
                    continue;
                }
                GhostPlan ghost = GhostPlan.of(schematicId, schematic);
                if (ghost.blocks().isEmpty()) {
                    mute.add(schematicId + ": призрак пуст");
                }
                if (ghost.blocks().size() >= GhostPlan.MAX_BLOCKS) {
                    mute.add(schematicId + ": призрак упёрся в потолок ("
                            + ghost.blocks().size() + ")");
                }
                if (!ghost.size().equals(schematic.size())) {
                    mute.add(schematicId + ": размер призрака " + ghost.size()
                            + " вместо " + schematic.size());
                }
            }

            if (!mute.isEmpty()) {
                context.throwGameTestException("Заказы без призрака: "
                        + String.join("; ", mute));
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Разбитый отряд деревня помнит, а ушедший — нет.
     * <p>
     * С этого дня считается право требовать дань: она берётся не с того,
     * кто слабее вообще, а с того, чьи люди <b>лежат под твоими воротами</b>.
     * Разница видна на второй половине проверки: отряд, ушедший целым,
     * дня разгрома не оставляет — его и не было.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid")
    public void aBeatenWarBandIsRemembered(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos centre = context.getAbsolutePos(new BlockPos(6, 71, 6));
        BlockPos musters = context.getAbsolutePos(new BlockPos(9, 71, 6));
        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 71, 14));
        List<BlockPos> floor = new ArrayList<>();

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", centre);
        Settlement village = Settlement.found(MAYA, Owner.AUTONOMOUS, "Коба", villageAt);
        manager.add(colony);
        manager.add(village);

        WarParty band = new WarParty(UUID.randomUUID(), village.id(), MAYA, musters, 1, 20L, 21L);

        try {
            for (int x = 2; x <= 14; x++) {
                for (int z = 2; z <= 14; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 70, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    floor.add(at);
                }
            }

            // Отряд пришёл и лёг: тела убираются до срока ухода.
            manager.update(colony.id(), state -> state.besiege(band, 19L));
            Raids.watch(world, manager, 20L);
            Raids.bodiesOf(world, band).forEach(CitizenEntity::discard);
            Raids.watch(world, manager, 22L);

            long beaten = manager.byId(village.id()).orElseThrow().beatenOn();
            if (beaten != band.leavesOn()) {
                context.throwGameTestException("День разгрома не записан: " + beaten
                        + " вместо " + band.leavesOn() + ". Дань требовать будет не с чего");
            }

            // А второй отряд уходит целым — и разгрома не случилось.
            WarParty whole = new WarParty(UUID.randomUUID(), village.id(), MAYA, musters,
                    1, 30L, 31L);
            manager.update(village.id(), state -> state.beaten(Settlement.UNSEEN_DAY));
            manager.update(colony.id(), state -> state.besiege(whole, 29L));
            Raids.watch(world, manager, 30L);
            Raids.watch(world, manager, 32L);

            if (manager.byId(village.id()).orElseThrow().beatenOn() != Settlement.UNSEEN_DAY) {
                context.throwGameTestException("Ушедший целым отряд засчитан разгромом: "
                        + "дань можно было бы требовать после любого набега");
            }
        } finally {
            Raids.bodiesOf(world, band).forEach(CitizenEntity::discard);
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, centre, floor);
        }

        context.complete();
    }


    /**
     * Дань берут с разбитых, а не с друзей — и не с хутора.
     * <p>
     * Лестница ступеней обещала городу «право требовать дань со слабых
     * соседей», и слово «слабых» тут не украшение: требовать можно только
     * у той деревни, чей отряд <b>только что</b> лёг под твоими воротами.
     * Иначе дань стала бы налогом на соседство — подрос и обложил всех,
     * ничем не рискуя.
     * <p>
     * Проверяется каждый отказ по очереди, потому что каждый из них —
     * отдельное правило, и выпади любое, дань перестанет что-то значить.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "peace")
    public void tributeIsTakenFromTheBeatenNotFromFriends(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 30, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(16, 30, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
        manager.add(village);
        manager.add(colony);

        try {
            // Хутор не требует ничего, даже у разбитых.
            manager.update(village.id(), state -> state.beaten(10L));
            if (Tribute.judge(manager.byId(village.id()).orElseThrow(), colony, player, 12L)
                    != Tribute.Verdict.NO_TOWN) {
                context.throwGameTestException("Хутор требует дань: "
                        + Tribute.judge(village, colony, player, 12L));
            }

            colony.setLevel(SettlementLevel.TOWN);

            // Неразбитая деревня не платит.
            manager.update(village.id(), state -> state.beaten(Settlement.UNSEEN_DAY));
            if (Tribute.judge(manager.byId(village.id()).orElseThrow(), colony, player, 12L)
                    != Tribute.Verdict.NOT_BEATEN) {
                context.throwGameTestException("Дань берут с деревни, которая не воевала");
            }

            // И давно разбитая тоже: страх не вечен.
            manager.update(village.id(), state -> state.beaten(10L));
            if (Tribute.judge(manager.byId(village.id()).orElseThrow(), colony, player,
                    10L + Tribute.MEMORY + 1) != Tribute.Verdict.NOT_BEATEN) {
                context.throwGameTestException("Разгром помнят дольше срока памяти");
            }

            // Друг дани не платит.
            manager.update(village.id(), state ->
                    state.addReputation(player, Standing.FRIEND.from()));
            if (Tribute.judge(manager.byId(village.id()).orElseThrow(), colony, player, 12L)
                    != Tribute.Verdict.TOO_FRIENDLY) {
                context.throwGameTestException("С друга берут дань: дружба и дань смешались");
            }

            // А с обиженного — берут, и требование разрывает союз.
            manager.update(village.id(), state -> {
                state.addReputation(player, -Standing.FRIEND.from());
                state.makeAlly(player, 11L);
            });
            Settlement beaten = manager.byId(village.id()).orElseThrow();
            Tribute.Outcome outcome = Tribute.demand(manager, beaten, colony, player, 12L);
            if (!outcome.taken()) {
                context.throwGameTestException("Дань не взята: " + outcome.verdict());
                return;
            }

            Settlement paying = manager.byId(village.id()).orElseThrow();
            if (!paying.owesTributeTo(player, 12L)) {
                context.throwGameTestException("Дань назначена, а деревня о ней не знает");
            }
            if (paying.tributeDaysLeft(12L) != Tribute.DAYS) {
                context.throwGameTestException("Срок дани " + paying.tributeDaysLeft(12L)
                        + " вместо " + Tribute.DAYS);
            }
            if (paying.isAllyOf(player)) {
                context.throwGameTestException("Союз пережил требование дани: "
                        + "деревня и вступается за игрока, и откупается от него");
            }
            if (Tribute.judge(paying, colony, player, 12L) != Tribute.Verdict.ALREADY) {
                context.throwGameTestException("Дань требуют дважды");
            }
        } finally {
            manager.remove(village.id());
            manager.remove(colony.id());
        }

        context.complete();
    }


    /**
     * Дань переезжает монетой и копит обиду.
     * <p>
     * Дань — не число в сохранении, а <b>серебро из чужого сундука</b>:
     * обобрать можно только того, у кого есть что взять, и увидеть это
     * можно, открыв его склад. Разорённая деревня не платит, и это
     * не сбой, а ответ.
     * <p>
     * И каждый платёж роняет доверие. Иначе дань была бы бесплатным
     * доходом, а она — решение: монета сегодня против отряда у ворот
     * послезавтра.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "peace")
    public void tributeMovesCoinAndBreedsResentment(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 35, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(16, 35, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
        manager.add(village);

        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
        world.setBlockState(colonyAt, ModBlocks.TOWN_HALL.getDefaultState());
        manager.add(colony);

        try {
            Coins.earn(Warehouse.of(world, village).coins(), Tribute.RATE * 3);
            manager.update(village.id(), state -> state.startTribute(player, 100L));
            int trustBefore = manager.byId(village.id()).orElseThrow().reputationOf(player);
            int mineBefore = Coins.total(Warehouse.of(world, colony).coins());

            for (long day = 1; day <= 3; day++) {
                if (Tribute.pay(world, manager, village, day) != Tribute.RATE) {
                    context.throwGameTestException("День " + day + ": дань не заплачена");
                    return;
                }
            }

            int mineAfter = Coins.total(Warehouse.of(world, colony).coins());
            if (mineAfter != mineBefore + Tribute.RATE * 3) {
                context.throwGameTestException("На склад колонии пришло "
                        + (mineAfter - mineBefore) + " вместо " + (Tribute.RATE * 3));
            }
            if (Coins.total(Warehouse.of(world, village).coins()) != 0) {
                context.throwGameTestException("У деревни осталась монета: "
                        + Coins.total(Warehouse.of(world, village).coins())
                        + " — платили не из её сундука");
            }

            Settlement paying = manager.byId(village.id()).orElseThrow();
            if (paying.reputationOf(player) != trustBefore - Tribute.RESENTMENT * 3) {
                context.throwGameTestException("Обида не копится: доверие "
                        + paying.reputationOf(player) + " вместо "
                        + (trustBefore - Tribute.RESENTMENT * 3));
            }

            // Разорённая деревня не платит, но и дани не лишается.
            if (Tribute.pay(world, manager, village, 4L) != 0) {
                context.throwGameTestException("Разорённая деревня всё равно заплатила");
            }
            if (!manager.byId(village.id()).orElseThrow().owesTributeTo(player, 4L)) {
                context.throwGameTestException("Дань кончилась от одного пустого дня");
            }

            // А срок выходит — и запись убирается сама.
            Tribute.pay(world, manager, village, 200L);
            if (manager.byId(village.id()).orElseThrow().tributeTo().isPresent()) {
                context.throwGameTestException("Срок вышел, а дань в записи осталась");
            }
        } finally {
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, colonyAt, List.of());
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
            world.setBlockState(colonyAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Карточка дани говорит сама за себя — и появляется не у всех.
     * <p>
     * У мирного соседа её нет вовсе: «сперва разбей их отряд» в разговоре
     * с деревней, которая тебе ничего не сделала, — это не цель, а подсказка
     * грабить. Зато у разбитой она есть сразу, и у платящей — тоже:
     * игрок должен видеть, сколько ему ещё несут.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "quests")
    public void theLevyCardSpeaksForItself(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        UUID player = UUID.randomUUID();

        BlockPos villageAt = context.getAbsolutePos(new BlockPos(2, 45, 2));
        BlockPos colonyAt = context.getAbsolutePos(new BlockPos(16, 45, 2));

        Settlement village = Settlement.found(NORMAN, Owner.AUTONOMOUS, "Бовуар", villageAt);
        world.setBlockState(villageAt, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(NORMAN, Owner.of(player), "Моя", colonyAt);
        colony.setLevel(SettlementLevel.TOWN);
        manager.add(village);
        manager.add(colony);

        SimpleInventory pockets = new SimpleInventory(9);
        Warehouse wares = Warehouse.of(world, village);

        try {
            // Мирный сосед: карточки нет.
            QuestView quiet = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.ELDER, wares, Optional.empty(), ItemStack.EMPTY, 12L).orElseThrow();
            if (quiet.levy().isPresent()) {
                context.throwGameTestException("Дань предлагают у мирной деревни: "
                        + "это подсказка грабить, а не цель");
            }

            // Разбитая: карточка есть и говорит «можно».
            manager.update(village.id(), state -> state.beaten(10L));
            QuestView beaten = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.ELDER, wares, Optional.empty(), ItemStack.EMPTY, 12L).orElseThrow();
            QuestView.Levy levy = beaten.levy().orElse(null);
            if (levy == null || !levy.ready() || levy.paying()) {
                context.throwGameTestException("У разбитой деревни карточка дани не готова: "
                        + levy);
            }

            // Платящая: карточка считает дни.
            manager.update(village.id(), state -> state.startTribute(player, 20L));
            QuestView paying = QuestNet.viewOf(manager, village, player, pockets,
                    Villages.ELDER, wares, Optional.empty(), ItemStack.EMPTY, 12L).orElseThrow();
            QuestView.Levy going = paying.levy().orElse(null);
            if (going == null || !going.paying() || going.days() != 8) {
                context.throwGameTestException("Карточка не считает дни дани: " + going);
            }
        } finally {
            cleanUpVillage(world, manager, village, villageAt, List.of());
            cleanUpVillage(world, manager, colony, colonyAt, List.of());
            world.setBlockState(villageAt, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    private static final Identifier TOWER_TYPE = new Identifier("villagepax", "norman/watchtower");
    private static final Identifier TOWER_SCHEMATIC =
            new Identifier("villagepax", "norman/watchtower_lvl1");

    /**
     * Башни убавляют отряд, но не отменяют набег.
     * <p>
     * Это всё, что делает укрепление, и мерится оно тем, чем игрок его
     * и почувствует: <b>к воротам пришло меньше людей</b>. Не «плюс десять
     * к обороне», которых не видно, а двое вместо четверых.
     * <p>
     * И не до нуля. Набег, который не приходит, — это выключенная механика,
     * а не победа: деревня со счётом к игроку пошлёт хотя бы одного.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid")
    public void towersThinTheWarBandButNeverStopIt(TestContext context) {
        int angry = Raids.fightersFor(-100);
        if (angry < 3) {
            context.throwGameTestException("Проверка рассчитана на полный отряд, а он "
                    + angry + ": числа набега изменились, поправь проверку");
        }

        if (Raids.fightersFor(-100, 1) != angry - 1) {
            context.throwGameTestException("Одна башня убавила " + (angry
                    - Raids.fightersFor(-100, 1)) + " мечей вместо одного");
        }
        if (Raids.fightersFor(-100, 2) != angry - 2) {
            context.throwGameTestException("Две башни убавили не двоих");
        }
        if (Raids.fightersFor(-100, 99) != 1) {
            context.throwGameTestException("Башни отменили набег совсем: пришло "
                    + Raids.fightersFor(-100, 99) + " бойцов. Выключенная механика —"
                    + " не оборона");
        }
        if (Raids.fightersFor(0, 5) != 0) {
            context.throwGameTestException("Башни зовут набег там, где его не было");
        }

        context.complete();
    }

    /**
     * Недостроенная башня мечей не убавляет, а достроенная — считается.
     * <p>
     * Обещать оборону, которой ещё нет, — худший вид обмана: игрок
     * рассчитывает на стены и встречает полный отряд.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "raid", tickLimit = 600)
    public void onlyAFinishedTowerCounts(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, TOWER_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 50, 1));
        BlockPos at = context.getAbsolutePos(new BlockPos(4, 50, 4));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building tower = null;

        try {
            tower = plan(colony, at, TOWER_TYPE, BlockRotation.NONE);
            if (Raids.towersOf(colony) != 0) {
                context.throwGameTestException("Размеченная башня уже считается обороной");
            }

            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), tower.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Башня не встала: до верха не дотянуться?");
                return;
            }

            if (Raids.towersOf(colony) != 1) {
                context.throwGameTestException("Готовая башня не сочтена: "
                        + Raids.towersOf(colony));
            }
            if (Workplaces.stations(tower).isEmpty()) {
                context.throwGameTestException("На башне нет поста: страже некуда встать");
            }
        } finally {
            if (tower != null) {
                demolish(world, tower, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Страж приходит на башню и стоит там.
     * <p>
     * «Работа должна быть видна» — правило дизайн-документа, и до башни
     * страж его нарушал: мирный обход был ходьбой к дальнему зданию,
     * то есть кругами по чужим огородам. Башню для того и строят, чтобы
     * с неё смотреть, и пустая башня рядом с бродящим по улице стражем
     * была бы насмешкой над обоими.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "guard", tickLimit = 600)
    public void theGuardStandsOnTheTower(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic plan = schematic(context, TOWER_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 55, 1));
        BlockPos at = context.getAbsolutePos(new BlockPos(4, 55, 4));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building tower = null;

        try {
            tower = plan(colony, at, TOWER_TYPE, BlockRotation.NONE);
            stockFor(world, colony, plan);
            if (BuildJob.advance(world, manager, colony.id(), tower.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Башня не встала");
                return;
            }

            BlockPos post = Workplaces.stations(tower).get(0);
            Citizen watchman = hireWithBody(world, colony, Villages.GUARD,
                    hall.add(0, 1, 0));
            runWork(world, manager, colony, watchman, 8, Schedule.MORNING_WORK);

            CitizenEntity body = bodyOf(world, colony, watchman);
            double away = body.getBlockPos().getSquaredDistance(post);
            if (away > WorkContext.ARRIVAL_REACH * WorkContext.ARRIVAL_REACH) {
                context.throwGameTestException("Страж не на башне: он на "
                        + body.getBlockPos().toShortString() + ", пост на "
                        + post.toShortString());
            }
        } finally {
            if (tower != null) {
                demolish(world, tower, plan);
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /**
     * Верёвку можно натянуть в любую сторону, и она всё так же держит вещи.
     * <p>
     * Заказчик: «сделай, чтоб верёвка могла смотреть в разные стороны».
     * До этого она шла только с запада на восток, и во дворе, вытянутом
     * поперёк, висела через проход, а не вдоль стены.
     * <p>
     * Проверяется не только поворот, но и то, что он <b>переживает</b>
     * поворот здания: схемы ставятся всеми четырьмя сторонами, и верёвка,
     * теряющая направление при повороте дома, повисла бы поперёк комнаты.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "decor")
    public void theRopeCanBeStrungAnyWay(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos at = context.getAbsolutePos(new BlockPos(2, 22, 2));

        try {
            for (Direction facing : Direction.Type.HORIZONTAL) {
                BlockState strung = ModBlocks.LAUNDRY.getDefaultState()
                        .with(LaundryBlock.FACING, facing);
                world.setBlockState(at, strung);

                if (world.getBlockState(at).get(LaundryBlock.FACING) != facing) {
                    context.throwGameTestException("Верёвка не встала на " + facing);
                    return;
                }
                if (!(world.getBlockEntity(at) instanceof RopeBlockEntity rope)) {
                    context.throwGameTestException("У повёрнутой верёвки нет блок-сущности");
                    return;
                }

                // И держит: поворот не должен отнимать у неё смысл.
                ItemStack hide = new ItemStack(Items.LEATHER, 1);
                if (!rope.hang(hide)) {
                    context.throwGameTestException("Повёрнутая на " + facing
                            + " верёвка ничего не держит");
                }
                if (rope.hung().stream().allMatch(ItemStack::isEmpty)) {
                    context.throwGameTestException("Вещь не повисла на " + facing);
                }

                // Поворот здания поворачивает и верёвку.
                BlockState turned = strung.rotate(BlockRotation.CLOCKWISE_90);
                if (turned.get(LaundryBlock.FACING) != facing.rotateYClockwise()) {
                    context.throwGameTestException("Верёвка не поворачивается вместе с домом: "
                            + facing + " превратилось в " + turned.get(LaundryBlock.FACING));
                }
            }
        } finally {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }


    /** Сколько хранилищ стоит в следе здания прямо сейчас. */
    private static int containersIn(ServerWorld world, Building building, Schematic schematic) {
        int found = 0;
        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            BlockPos where = BuildJob.worldPos(building, schematic.size(), step.pos());
            if (world.getBlockEntity(where) instanceof net.minecraft.inventory.Inventory) {
                found++;
            }
        }
        return found;
    }

    /** Убрать за набегом: свои тела, запись колонии и пол. */
    private static void cleanUpRaid(ServerWorld world, SettlementManager manager,
                                    Settlement colony, WarParty party, List<BlockPos> floor) {
        Raids.bodiesOf(world, party).forEach(CitizenEntity::discard);
        manager.remove(colony.id());
        for (BlockPos at : floor) {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
        }
        world.setBlockState(colony.center(), Blocks.AIR.getDefaultState());
    }


    /**
     * Убрать деревню за собой начисто — включая память о месте.
     * <p>
     * Забыть место обязательно: места деревень вечны, и мир игровых тестов
     * переживает прогон. Без этого второй запуск подряд не поднимал бы
     * деревню и падал — что и случилось, когда тест был написан.
     */
    private static void cleanUpVillage(ServerWorld world, SettlementManager manager,
                                       Settlement village, BlockPos centre, List<BlockPos> meadow) {
        if (village != null) {
            razeVillage(world, village);
            discardBodies(world, village);
            manager.remove(village.id());
        }
        manager.forget(centre);
        for (BlockPos at : meadow) {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
        }
    }

    /**
     * Кто мешает деревне встать здесь. Нужно в сообщении об отказе: без
     * этого «деревня не встала» не отличает занятое место от тесноты,
     * и на поиск причины уходит прогон за прогоном.
     */
    private static String whoBlocks(SettlementManager manager, BlockPos centre) {
        Settlement probe = Settlement.found(NORMAN, Owner.AUTONOMOUS, "проба", centre);
        return manager.conflictWith(probe)
                .map(clash -> clash.name() + " в " + clash.center().toShortString())
                .orElse("нет");
    }

    /** Убрать деревню за собой: игровые тесты делят один мир. */
    private static void razeVillage(ServerWorld world, Settlement village) {
        for (Building site : village.buildings()) {
            SchematicLoader.get(BuildJob.schematicId(site))
                    .ifPresent(schematic -> demolish(world, site, schematic));
        }
        world.setBlockState(village.center(), Blocks.AIR.getDefaultState());
    }

    // --- слоты декора ---

    /**
     * Слот декора заполняется, и всегда одним и тем же.
     * <p>
     * Маркер декора задумывался с самого начала, но дел не делал: становился
     * воздухом, и все дома колонии выходили близнецами. Теперь на его место
     * встаёт блок из списка культуры.
     * <p>
     * Устойчивость проверяется ремонтом, и это главное в тесте. Случайность
     * из генератора пережила бы постройку, но не ремонт: дом менял бы облик
     * всякий раз, когда билдер подлатает стену, и игрок видел бы мод, который
     * сам себя переделывает.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "decor")
    public void decorSlotsAreFilledAndStayTheSame(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic house = schematic(context, HOUSE_LVL2);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 2, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(site);

        try {
            stockFor(world, colony, house);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом не достроился");
            }

            List<BlockPos> slots = BuildJob.pointsOfInterest(site, house, MarkerKind.DECOR);
            if (slots.size() != 2) {
                context.throwGameTestException("Слотов декора " + slots.size() + ", а в схеме два");
            }

            List<Block> chosen = new ArrayList<>();
            for (BlockPos slot : slots) {
                BlockState state = world.getBlockState(slot);
                if (state.isAir()) {
                    context.throwGameTestException("Слот декора на "
                            + slot.toShortString() + " остался пустым");
                }
                if (!decorTable().contains(state.getBlock())) {
                    context.throwGameTestException("В слот встало то, чего нет в списке "
                            + "культуры: " + state.getBlock());
                }
                chosen.add(state.getBlock());
            }

            // Ремонт: план проходится заново, и декор обязан вернуться тот же.
            site.setProgress(BuildProgress.DAMAGED);
            for (BlockPos slot : slots) {
                world.setBlockState(slot, Blocks.AIR.getDefaultState());
            }
            stockFor(world, colony, house);
            if (BuildJob.advance(world, manager, colony.id(), site.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ремонт не завершился");
            }

            for (int index = 0; index < slots.size(); index++) {
                Block back = world.getBlockState(slots.get(index)).getBlock();
                if (back != chosen.get(index)) {
                    context.throwGameTestException("После ремонта декор сменился: было "
                            + chosen.get(index) + ", стало " + back);
                }
            }
        } finally {
            demolish(world, site, house);
            for (BlockPos slot : BuildJob.pointsOfInterest(site, house, MarkerKind.DECOR)) {
                world.setBlockState(slot, Blocks.AIR.getDefaultState());
            }
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Список декора норманнов — из датапака, а не из догадки теста. */
    private static List<Block> decorTable() {
        Culture norman = CultureManager.get(NORMAN);
        List<Block> table = new ArrayList<>();
        if (norman != null) {
            for (Identifier id : norman.decor()) {
                table.add(Registries.BLOCK.get(id));
            }
        }
        return table;
    }

    // --- билдер строит стоя на земле ---

    /**
     * Высокое здание билдер достраивает, <b>ни разу не встав в воздух</b>.
     * <p>
     * Жалоба игрока: «строитель продолжает тупить при постройке, пытается
     * дотянуться а не может». Прежние тесты стройки этого поймать не могли
     * и не могут: {@code runWork} телепортирует тело прямо в назначенную
     * точку, а телепортом достаётся и блок на крыше. В игре у жителя
     * телепорта нет.
     * <p>
     * Поэтому здесь {@link #runWorkOnFoot} переносит тело только туда, где
     * <b>может стоять человек</b>: твёрдое под ногами, пусто на месте и над
     * головой. Назначили точку в воздухе — житель остаётся там, где был,
     * ровно как в игре, где он до неё не дойдёт.
     * <p>
     * Проверяется на доме второго уровня: он девять блоков высотой, и труба
     * у него идёт до самого верха. Самой высокой схемой мода он был до
     * майя; теперь выше него храм совета второго уровня, и у него своя
     * проверка — {@link #mayaTempleIsBuiltWithoutStandingInMidair}.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "reach")
    public void tallHouseIsBuiltWithoutStandingInMidair(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic tall = schematic(context, HOUSE_LVL2);

        // Ратуша рядом со стройкой намеренно: дальше двенадцати блоков
        // билдер не берёт со склада сам, и стройка встала бы по нехватке
        // материалов, а проверяем мы досягаемость.
        BlockPos hall = context.getAbsolutePos(new BlockPos(8, 9, 8));
        List<BlockPos> ground = new ArrayList<>();

        try {
            // Ровная площадка под здание и вокруг него: билдеру надо где стоять.
            for (int x = -2; x <= 12; x++) {
                for (int z = -2; z <= 12; z++) {
                    BlockPos at = context.getAbsolutePos(new BlockPos(x, 8, z));
                    world.setBlockState(at, Blocks.STONE.getDefaultState());
                    ground.add(at);
                }
            }

            Settlement colony = colonyWithBuilder(world, manager, hall);
            BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 9, 0));
            Building site = new Building(UUID.randomUUID(), HOUSE_TYPE, 2, anchor,
                    BlockRotation.NONE, BuildProgress.PLANNED, List.of());
            colony.addBuilding(site);

            try {
                stockFor(world, colony, tall);
                Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER,
                        context.getAbsolutePos(new BlockPos(-1, 9, -1)));

                int stalled = runWorkOnFoot(world, manager, colony, mason, 900);

                if (!site.isOperational()) {
                    context.throwGameTestException("Дом второго уровня не достроился: билдер "
                            + "встал на шаге " + site.nextStep() + " из "
                            + tall.plan().steps().size() + ", и " + stalled
                            + " раз его посылали в точку, где человек стоять не может");
                }
                if (stalled > 0) {
                    context.throwGameTestException("Билдера " + stalled
                            + " раз посылали стоять в воздух — в игре он туда не дойдёт");
                }
            } finally {
                demolish(world, site, tall);
                discardBodies(world, colony);
                manager.remove(colony.id());
            }
        } finally {
            for (BlockPos at : ground) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Прогон стройки без телепорта по воздуху: тело переносится только туда,
     * где может стоять человек. Возвращает, сколько раз ему назвали точку,
     * до которой в игре не дойти.
     */
    private static int runWorkOnFoot(ServerWorld world, SettlementManager manager,
                                     Settlement colony, Citizen worker, int rounds) {
        CitizenEntity body = (CitizenEntity) world.getEntity(worker.entityUuid().orElseThrow());
        int impossible = 0;

        for (int round = 0; round < rounds; round++) {
            WorkTicker.decide(world, manager, colony, worker, Schedule.MORNING_WORK);

            BlockPos target = body.workTarget();
            if (target == null) {
                continue;
            }
            if (standable(world, target)) {
                body.refreshPositionAndAngles(target.getX() + 0.5, target.getY(),
                        target.getZ() + 0.5, 0f, 0f);
            } else {
                impossible++;
            }
        }
        return impossible;
    }

    /** Ноги на твёрдом, голова в пустоте — то же, что требует ванильная ходьба. */
    private static boolean standable(ServerWorld world, BlockPos spot) {
        return world.getBlockState(spot.down()).isSolidBlock(world, spot.down())
                && world.getBlockState(spot).getCollisionShape(world, spot).isEmpty()
                && world.getBlockState(spot.up()).getCollisionShape(world, spot.up()).isEmpty();
    }

    // --- вторые уровни дома и фермы ---

    private static final Identifier HOUSE_LVL2 = new Identifier("villagepax", "norman/house_lvl2");
    private static final Identifier FARM_LVL2 = new Identifier("villagepax", "norman/farm_lvl2");

    /**
     * Сколько грядок на поле второго уровня.
     * <p>
     * Поле растёт на восток и юг, якорь остаётся тем же: двадцать три
     * грядки первого уровня становятся сорока шестью. Из внутренних
     * сорока девяти клеток вычтены два колодца и тюк пугала.
     */
    private static final int BIGGER_FIELD = 46;

    /**
     * Дом второго уровня: четыре кровати и открытый дымоход.
     * <p>
     * Решение заказчика — «больше и красивее». Кровати это «больше»,
     * а очаг с трубой — то самое «красивее»: дым виден с улицы, и деревня
     * перестаёт выглядеть макетом.
     * <p>
     * Дымоход проверяется <b>по всей высоте</b>, и не зря: колонна проходит
     * через потолок и три слоя крыши, пробивается кодом, и одна пропущенная
     * дырка означает дом, который дымит внутрь. Увидеть такое можно было бы
     * только в игре, стоя рядом.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "levels")
    public void upgradedHouseSleepsFourAndVentsItsHearth(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic bigger = schematic(context, HOUSE_LVL2);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = new Building(UUID.randomUUID(), HOUSE_TYPE, 2, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(house);

        try {
            stockFor(world, colony, bigger);
            if (BuildJob.advance(world, manager, colony.id(), house.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Дом второго уровня не достроился");
            }

            // Очаг ищется в схеме, а не помнится координатой: он уже
            // переезжал — из середины комнаты в угол, после того как
            // на нём сгорел житель.
            BlockPos hearth = hearthOf(bigger, house);
            BlockState fire = hearth == null
                    ? Blocks.AIR.getDefaultState() : world.getBlockState(hearth);
            if (!fire.isOf(Blocks.CAMPFIRE) || !fire.get(CampfireBlock.LIT)) {
                context.throwGameTestException("Очага в доме нет или он потушен: "
                        + fire.getBlock());
            }

            // Дымоход идёт над очагом, где бы тот ни стоял.
            BlockPos above = hearth == null ? null
                    : BuildJob.worldPos(house, bigger.size(), new BlockPos(0, 0, 0));
            for (int y = 3; hearth != null && y < bigger.size().getY(); y++) {
                BlockPos flue = new BlockPos(hearth.getX(),
                        above.getY() + y, hearth.getZ());
                if (!world.getBlockState(flue).isAir()) {
                    context.throwGameTestException("Дымоход закрыт на высоте " + y + ": там "
                            + world.getBlockState(flue).getBlock());
                }
            }

            // Четверо под одной крышей — вдвое против первого уровня.
            for (int extra = 0; extra < 3; extra++) {
                Citizen lodger = Citizen.newborn("Жилец", String.valueOf(extra), NORMAN,
                        Gender.FEMALE);
                colony.addCitizen(lodger);
            }
            Housing.assignBeds(world, colony);

            long housed = colony.citizens().stream().filter(citizen -> !citizen.isHomeless())
                    .count();
            if (housed != 4) {
                context.throwGameTestException("Под крышей устроилось " + housed
                        + " жителей, а кроватей четыре");
            }

            // Пятому места нет: кроватей ровно столько, сколько построено.
            colony.addCitizen(Citizen.newborn("Лишний", "", NORMAN, Gender.MALE));
            Housing.assignBeds(world, colony);
            if (colony.citizens().stream().filter(citizen -> !citizen.isHomeless()).count() != 4) {
                context.throwGameTestException("Кроватей оказалось больше, чем построено");
            }
        } finally {
            demolish(world, house, bigger);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Ферма второго уровня: поле вдвое больше и по-прежнему с калиткой.
     * <p>
     * Калитка проверяется настоящим поиском пути, а не наличием блока:
     * поле без входа — это фермер, который стоит снаружи и не делает
     * ничего, и ровно так это однажды и было.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "levels")
    public void biggerFarmIsPlantedAndStillEnterable(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic bigger = schematic(context, FARM_LVL2);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building farm = new Building(UUID.randomUUID(), FARM_TYPE, 2, anchor,
                BlockRotation.NONE, BuildProgress.PLANNED, List.of());
        colony.addBuilding(farm);

        BlockPos landing = BuildJob.worldPos(farm, bigger.size(), new BlockPos(-1, 1, 3));

        try {
            stockFor(world, colony, bigger);
            if (BuildJob.advance(world, manager, colony.id(), farm.id(), 20_000)
                    != BuildJob.Outcome.FINISHED) {
                context.throwGameTestException("Ферма второго уровня не достроилась");
            }

            int planted = 0;
            for (int x = 1; x <= 7; x++) {
                for (int z = 1; z <= 7; z++) {
                    BlockPos plot = BuildJob.worldPos(farm, bigger.size(), new BlockPos(x, 2, z));
                    if (world.getBlockState(plot).getBlock() instanceof CropBlock) {
                        planted++;
                    }
                }
            }
            if (planted != BIGGER_FIELD) {
                context.throwGameTestException("Засеяно грядок " + planted + ", а ждали "
                        + BIGGER_FIELD);
            }

            // Пугало: тюк и тыква на нём. Стоит там же, где стояло
            // на первом уровне, и это половина смысла проверки:
            // улучшение надстраивает поле, а не переставляет на нём вещи.
            BlockPos straw = BuildJob.worldPos(farm, bigger.size(), new BlockPos(1, 2, 5));
            BlockPos head = BuildJob.worldPos(farm, bigger.size(), new BlockPos(1, 3, 5));
            if (!world.getBlockState(straw).isOf(Blocks.HAY_BLOCK)
                    || !world.getBlockState(head).isOf(Blocks.CARVED_PUMPKIN)) {
                context.throwGameTestException("Пугала на поле нет: "
                        + world.getBlockState(straw).getBlock() + " и "
                        + world.getBlockState(head).getBlock());
            }

            BlockPos gate = BuildJob.worldPos(farm, bigger.size(), new BlockPos(0, 2, 3));
            if (!(world.getBlockState(gate).getBlock() instanceof FenceGateBlock)) {
                context.throwGameTestException("В ограде большого поля нет калитки, стоит "
                        + world.getBlockState(gate).getBlock());
            }

            world.setBlockState(landing, Blocks.DIRT.getDefaultState());
            Citizen farmer = hireWithBody(world, colony, FarmJob.FARMER, landing.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(farmer.entityUuid().orElseThrow());
            body.setOnGround(true);

            BlockPos plot = BuildJob.worldPos(farm, bigger.size(), new BlockPos(1, 2, 3));
            Path through = body.getNavigation().findPathTo(plot, 0);
            if (through == null || !through.reachesTarget()) {
                context.throwGameTestException("Фермер не может войти на большое поле: "
                        + (through == null ? "пути нет вовсе" : "путь не доходит"));
            }
        } finally {
            demolish(world, farm, bigger);
            world.setBlockState(landing, Blocks.AIR.getDefaultState());
            discardBodies(world, colony);
            manager.remove(colony.id());
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
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "evening")
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

    // --- карта колонии ---

    /**
     * Подпись здания висит над его крышей, а не внутри дома.
     * <p>
     * Место подписи считает сервер, потому что размер здания знает схема,
     * а схем у клиента нет. Ошибись здесь — и надпись окажется в стене,
     * где её не видно, или в небе, где непонятно, чья она.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "labels")
    public void colonySignsHangOverTheRoofs(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        BlockPos anchor = context.getAbsolutePos(new BlockPos(0, 8, 0));

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, anchor, HOUSE_TYPE, BlockRotation.NONE);

        try {
            ColonyMap map = ColonyNet.mapOf(colony);

            if (!map.id().equals(colony.id()) || !map.name().equals(colony.name())) {
                context.throwGameTestException("Карта не про эту колонию: " + map.name());
            }
            if (map.radius() != colony.level().claimRadiusChunks()) {
                context.throwGameTestException("Радиус владений на карте " + map.radius()
                        + ", а у колонии " + colony.level().claimRadiusChunks());
            }
            if (map.signs().size() != 1) {
                context.throwGameTestException("Подписей " + map.signs().size()
                        + ", а здание одно");
            }

            ColonyMap.Sign sign = map.signs().get(0);
            if (sign.done()) {
                context.throwGameTestException("Размеченный дом объявлен готовым");
            }

            // Ровно на высоту схемы над якорем и по её середине.
            BlockPos expected = anchor.add(housePlan.size().getX() / 2, housePlan.size().getY(),
                    housePlan.size().getZ() / 2);
            if (!sign.at().equals(expected)) {
                context.throwGameTestException("Подпись висит на " + sign.at().toShortString()
                        + ", а крыша кончается на " + expected.toShortString());
            }
            if (sign.at().getY() <= anchor.getY()) {
                context.throwGameTestException("Подпись оказалась не выше основания дома");
            }

            // Достроили — подпись перестаёт говорить «строится».
            stockFor(world, colony, housePlan);
            BuildJob.advance(world, manager, colony.id(), house.id(), 10_000);

            if (!ColonyNet.mapOf(colony).signs().get(0).done()) {
                context.throwGameTestException("Дом готов, а подпись всё ещё про стройку");
            }
        } finally {
            demolish(world, house, housePlan);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    // --- улицы колонии ---

    /**
     * Улица от двери дома к ратуше: те самые восемь тайлов, которые кладёт
     * {@code Roads}, посчитанные по той же линии вручную.
     * <p>
     * Проверять точные места, а не «сколько-нибудь замостил», намеренно:
     * улица обязана быть <b>непрерывной</b> от двери до площади. Дорожка
     * с провалами не помогает поиску пути и выглядит хуже, чем её
     * отсутствие.
     */
    /**
     * Ожидаемый маршрут больше не выписывается координатами.
     * <p>
     * Раньше здесь стояли восемь угаданных пар, и любая правка правила
     * (например «улица начинается от порога, а не от стены») ломала тест
     * не по делу. Теперь проверяются <b>свойства</b>: маршрут спрашивается
     * у {@code Roads}, и он обязан начинаться прямо перед дверью, не
     * рваться и быть шириной в один тайл.
     */
    private static List<BlockPos> streetOf(ServerWorld world, Settlement colony, Building house) {
        return Roads.route(world, colony, house);
    }

    /** Место ратуши и якорь дома, между которыми ляжет улица. */
    private static final BlockPos STREET_HALL = new BlockPos(1, 9, 1);
    private static final BlockPos STREET_HOUSE = new BlockPos(10, 9, 3);

    /** Высота газона: улица ложится на него, дом стоит на нём же. */
    private static final int LAWN = 8;

    /**
     * Билдер сам мостит улицу от готового дома к ратуше.
     * <p>
     * Решение заказчика: деревня должна становиться деревней, а не набором
     * домов на траве. Приказа игрока на это нет — билдер берётся за улицу,
     * когда строить больше нечего.
     * <p>
     * Материала на складе нет, поэтому улица получается натоптанной тропой:
     * она ничего не стоит, ровно как удар лопатой по траве у игрока. Так
     * улицы появляются и у самой бедной колонии — иначе игрок не увидел бы
     * этой работы вовсе.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "streets")
    public void builderPavesAStreetToTheTownHall(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(STREET_HALL);
        List<BlockPos> lawn = lawn(world, context);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, context.getAbsolutePos(STREET_HOUSE), HOUSE_TYPE,
                BlockRotation.NONE);

        try {
            raiseHouse(context, world, manager, colony, house, housePlan);

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            CitizenEntity body = (CitizenEntity) world.getEntity(mason.entityUuid().orElseThrow());

            // Три решения: найти дело, дойти, положить первый тайл.
            runWork(world, manager, colony, mason, 3, Schedule.MORNING_WORK);
            if (!body.getMainHandStack().isOf(Items.IRON_SHOVEL)) {
                context.throwGameTestException("Билдер топчет тропу без лопаты в руке: "
                        + body.getMainHandStack());
            }

            runWork(world, manager, colony, mason, 20, Schedule.MORNING_WORK);

            List<BlockPos> street = streetOf(world, colony, house);
            if (street.size() < 4) {
                context.throwGameTestException("Маршрут улицы вышел длиной " + street.size()
                        + " — от дома до площади должно быть дальше");
            }

            for (BlockPos tile : street) {
                BlockState state = world.getBlockState(tile);
                if (!state.isOf(Blocks.DIRT_PATH)) {
                    context.throwGameTestException("Улица прервалась на "
                            + tile.toShortString() + ": там " + state.getBlock());
                }
                if (!state.isIn(ModTags.PREFERRED_PATH)) {
                    context.throwGameTestException("Замощённое не считается дорогой — "
                            + "поиск пути по такой улице жителей не поведёт");
                }
            }

            // Путь ведёт ОТ ДВЕРИ: первый тайл стоит прямо перед входом.
            BlockPos door = BuildJob.pointsOfInterest(house, housePlan, MarkerKind.DOOR).get(0);
            BlockPos first = street.get(0);
            int fromDoor = Math.max(Math.abs(first.getX() - door.getX()),
                    Math.abs(first.getZ() - door.getZ()));
            if (fromDoor != 1) {
                context.throwGameTestException("Улица начинается в " + fromDoor
                        + " блоках от двери " + door.toShortString() + ", а надо у порога");
            }

            // И маршрут не рвётся: соседние тайлы стоят рядом.
            for (int index = 1; index < street.size(); index++) {
                BlockPos was = street.get(index - 1);
                BlockPos now = street.get(index);
                if (Math.max(Math.abs(now.getX() - was.getX()),
                        Math.abs(now.getZ() - was.getZ())) > 1) {
                    context.throwGameTestException("Разрыв в улице между "
                            + was.toShortString() + " и " + now.toShortString());
                }
            }

            int paved = 0;
            for (BlockPos at : lawn) {
                if (world.getBlockState(at).isOf(Blocks.DIRT_PATH)) {
                    paved++;
                }
            }
            if (paved != street.size()) {
                context.throwGameTestException("Замощено тайлов " + paved + ", а в маршруте "
                        + street.size() + ": улица должна быть шириной в один");
            }
        } finally {
            clearStreet(world, house, housePlan, lawn);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Улица берёт только излишки материала.
     * <p>
     * Без этого правила дорожка молча съедала бы булыжник, отложенный
     * игроком на цоколь следующего дома, — и он не понял бы, куда девается
     * камень. Здесь гравия ровно на один тайл больше запаса: улица кладёт
     * одну плиту и переходит на бесплатную тропу.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "streets")
    public void streetTakesOnlySurplusMaterial(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(STREET_HALL);
        List<BlockPos> lawn = lawn(world, context);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, context.getAbsolutePos(STREET_HOUSE), HOUSE_TYPE,
                BlockRotation.NONE);

        try {
            raiseHouse(context, world, manager, colony, house, housePlan);

            ItemStack over = Warehouse.of(world, colony)
                    .add(new ItemStack(Items.GRAVEL, Roads.reserve() + 1));
            if (!over.isEmpty()) {
                context.throwGameTestException("Склад не принял гравий: " + over);
            }

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 24, Schedule.MORNING_WORK);

            List<BlockPos> street = streetOf(world, colony, house);
            BlockState first = world.getBlockState(street.get(0));
            if (!first.isOf(Blocks.GRAVEL)) {
                context.throwGameTestException("Излишек гравия не пошёл в мостовую: у порога "
                        + first.getBlock());
            }

            int left = Warehouse.of(world, colony).count(Items.GRAVEL);
            if (left != Roads.reserve()) {
                context.throwGameTestException("Запас тронут: гравия осталось " + left
                        + " вместо " + Roads.reserve());
            }

            for (int index = 1; index < street.size(); index++) {
                BlockState state = world.getBlockState(street.get(index));
                if (!state.isOf(Blocks.DIRT_PATH)) {
                    context.throwGameTestException("Материал кончился, а улица встала: на "
                            + street.get(index).toShortString() + " лежит " + state.getBlock());
                }
            }
        } finally {
            clearStreet(world, house, housePlan, lawn);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /**
     * Чужого улица не трогает: ни дорожки игрока, ни грядки.
     * <p>
     * То же правило, по которому фермер не считает своими посадки игрока.
     * Мостить билдер вправе только натуральный грунт: увидел кварц или
     * пашню — обошёл и пошёл дальше, а не встал и не перекопал.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "streets")
    public void streetLeavesPlayerBlocksAndFieldsAlone(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        Schematic housePlan = schematic(context, HOUSE_SCHEMATIC);

        BlockPos hall = context.getAbsolutePos(STREET_HALL);
        List<BlockPos> lawn = lawn(world, context);

        Settlement colony = colonyWithBuilder(world, manager, hall);
        Building house = plan(colony, context.getAbsolutePos(STREET_HOUSE), HOUSE_TYPE,
                BlockRotation.NONE);

        BlockPos mine;
        BlockPos field;

        try {
            raiseHouse(context, world, manager, colony, house, housePlan);

            // Прямо на будущей улице: своя дорожка игрока и его грядка.
            List<BlockPos> planned = streetOf(world, colony, house);
            if (planned.size() < 6) {
                context.throwGameTestException("Маршрут короче шести тайлов, некуда ставить чужое");
                return;
            }
            mine = planned.get(2);
            field = planned.get(4);
            world.setBlockState(mine, Blocks.QUARTZ_BLOCK.getDefaultState());
            world.setBlockState(field, Blocks.FARMLAND.getDefaultState());

            Citizen mason = hireWithBody(world, colony, BuildJob.BUILDER, hall.up());
            runWork(world, manager, colony, mason, 24, Schedule.MORNING_WORK);

            if (!world.getBlockState(mine).isOf(Blocks.QUARTZ_BLOCK)) {
                context.throwGameTestException("Улица перекопала дорожку игрока: теперь там "
                        + world.getBlockState(mine).getBlock());
            }
            if (!world.getBlockState(field).isOf(Blocks.FARMLAND)) {
                context.throwGameTestException("Улица прошла по грядке: теперь там "
                        + world.getBlockState(field).getBlock());
            }

            // А вокруг чужого улица всё-таки легла: обошла, а не встала.
            for (BlockPos tile : streetOf(world, colony, house)) {
                if (tile.equals(mine) || tile.equals(field)) {
                    continue;
                }
                BlockState state = world.getBlockState(tile);
                if (!state.isOf(Blocks.DIRT_PATH)) {
                    context.throwGameTestException("Улица встала перед чужим блоком: на "
                            + tile.toShortString() + " лежит " + state.getBlock());
                }
            }
        } finally {
            clearStreet(world, house, housePlan, lawn);
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }

        context.complete();
    }

    /** Ровный газон под колонию: улице надо по чему идти. */
    private static List<BlockPos> lawn(ServerWorld world, TestContext context) {
        List<BlockPos> laid = new ArrayList<>();

        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 4; z++) {
                BlockPos at = context.getAbsolutePos(new BlockPos(x, LAWN, z));
                world.setBlockState(at, Blocks.GRASS_BLOCK.getDefaultState());
                laid.add(at);
            }
        }
        return laid;
    }

    /** Дом строится разом: улицу мостят от <b>готового</b> здания. */
    private static void raiseHouse(TestContext context, ServerWorld world, SettlementManager manager,
                                   Settlement colony, Building house, Schematic housePlan) {
        stockFor(world, colony, housePlan);
        if (BuildJob.advance(world, manager, colony.id(), house.id(), 10_000)
                != BuildJob.Outcome.FINISHED) {
            context.throwGameTestException("Дом не достроился, мостить нечего");
        }
    }

    private static void clearStreet(ServerWorld world, Building house, Schematic housePlan,
                                    List<BlockPos> lawn) {
        demolish(world, house, housePlan);
        for (BlockPos at : lawn) {
            world.setBlockState(at, Blocks.AIR.getDefaultState());
        }
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

    /**
     * Готовая ратуша как здание колонии — то же, что делает основание.
     * <p>
     * Помощник {@code colonyWithBuilder} ставит только блок, а рост уровня
     * считается по <b>зданию</b>: без него колонии нечего улучшать.
     */
    private static Building raisedTownHall(Settlement colony, BlockPos at) {
        // Якорь считается так же, как при основании: блок ратуши — середина
        // её следа, а не угол. Иначе тест проверял бы геометрию, которой
        // в игре не бывает.
        BlockPos anchor = SchematicLoader.get(TOWN_HALL_SCHEMATIC)
                .map(schematic -> BuildOrders.centredAnchor(at, schematic, BlockRotation.NONE))
                .orElse(at);

        Building hall = new Building(UUID.randomUUID(), TOWN_HALL_TYPE, 1, anchor,
                BlockRotation.NONE, BuildProgress.DONE, List.of());
        colony.addBuilding(hall);
        return hall;
    }

    /**
     * Тело жителя, а если его больше нет — новое.
     * <p>
     * Мир игровых тестов не держит чанки вечно, и выгрузка снимает тело
     * вместе с опознавателем в записи жителя. Тест, который на это
     * не рассчитывает, падает через раз и не по своей вине: проверять надо
     * решения жителя, а не то, повезло ли чанку остаться загруженным.
     */
    private static CitizenEntity bodyOf(ServerWorld world, Settlement colony, Citizen citizen) {
        CitizenEntity body = citizen.entityUuid()
                .map(world::getEntity)
                .filter(CitizenEntity.class::isInstance)
                .map(CitizenEntity.class::cast)
                .orElse(null);

        return body != null ? body : CitizenSpawner.spawnBody(world, colony, citizen);
    }

    private static void runWork(ServerWorld world, SettlementManager manager, Settlement colony,
                                Citizen worker, int rounds, Schedule part) {
        CitizenEntity body = bodyOf(world, colony, worker);

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
        return colonyWithBuilder(world, manager, center, NORMAN);
    }

    private static Settlement colonyWithBuilder(ServerWorld world, SettlementManager manager,
                                                BlockPos center, Identifier culture) {
        world.setBlockState(center, ModBlocks.TOWN_HALL.getDefaultState());
        Settlement colony = Settlement.found(culture, Owner.of(UUID.randomUUID()), "Стройка", center);
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
        clearPorch(world, site, schematic);
    }

    /**
     * Убрать и ступени у входов.
     * <p>
     * Крыльцо кладётся <b>снаружи</b> следа здания, а снос проходит только
     * по следу — и ступени оставались в общем мире игровых тестов навсегда.
     * Следующая проверка находила на своём месте чужой булыжник и падала
     * непонятно от чего.
     * <p>
     * Убирается только <b>похожее на ступень</b> и только в столбцах
     * у входа: первая попытка вычищала объём вокруг входа и вырезала землю,
     * на которой стояли соседние проверки, — а падали от этого уже третьи.
     * Вторая считала высоту ступени наперёд, но укладка идёт по земле,
     * и одна ровная клетка сдвигала всю лесенку мимо расчёта: чужой
     * булыжник оставался, а вместо него стиралась целая клетка.
     */
    private static void clearPorch(ServerWorld world, Building site, Schematic schematic) {
        for (BlockPos spot : Access.stepSpots(site, schematic)) {
            if (Access.isTread(world.getBlockState(spot))) {
                world.setBlockState(spot, Blocks.AIR.getDefaultState(),
                        net.minecraft.block.Block.NOTIFY_LISTENERS);
            }
        }
    }
}
