package com.villagepax.entity;

import com.villagepax.VillagePax;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.sim.quest.Quests;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.ai.pathing.EntityNavigation;

/**
 * Тело жителя.
 * <p>
 * Существует только пока загружен чанк и <b>никогда не сохраняется</b>: тип
 * сущности объявлен с {@code disableSaving}. Источник правды — запись
 * {@link Citizen} внутри поселения, а тело лишь показывает её в мире и при
 * исчезновении возвращает в неё то, что успело измениться.
 * <p>
 * Это ключевое решение по производительности всего мода. Существующие моды
 * такого класса держат состояние в сущности, и сотни сущностей душат сервер;
 * здесь незагруженное поселение не стоит ничего.
 */
public class CitizenEntity extends PathAwareEntity {

    private static final String SETTLEMENT_KEY = "Settlement";
    private static final String CITIZEN_KEY = "Citizen";

    private UUID settlementId;
    private UUID citizenId;

    /**
     * Куда житель идёт по работе. Кладёт сюда стратегия, читает цель
     * навигации — так цель не пересчитывает точку каждый тик.
     * <p>
     * Не сохраняется, и не должно: тело не пишется в чанк, а стратегия
     * назовёт точку заново на первом же своём шаге.
     */
    private BlockPos workTarget;

    public CitizenEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        keepTools();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    /**
     * Своя навигация: житель предпочитает идти по дороге.
     * <p>
     * Разница видна не сразу, а когда деревня замощена: без неё все
     * работники ходят одной линией напрямик через газон и толкаются на ней.
     */
    @Override
    protected EntityNavigation createNavigation(World world) {
        return new CitizenNavigation(this, world);
    }

    /**
     * Сколько решений подряд житель топчется у одной точки, прежде чем
     * признать её недостижимой.
     * <p>
     * Восемь решений — это около четырёх секунд: столько нужно, чтобы обойти
     * дом, и заметно меньше, чем терпение игрока, который смотрит на
     * замершего работника.
     */
    private static final int GIVE_UP_AFTER = 8;

    /**
     * И насколько забывается отказ. Мир меняется: курьер подвёз материалы,
     * билдер достроил ступени, игрок сломал забор — точка, недостижимая
     * полминуты назад, может стать достижимой.
     */
    private static final int FORGET_AFTER = 600;

    /** Столько отказов помнится; дальше список чистится целиком. */
    private static final int REMEMBER_AT_MOST = 64;

    /** Ближе этого считается «дошёл»: точность тут не нужна. */
    private static final double CLOSE_ENOUGH = 3.0;

    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private BlockPos stuckOn;
    private int stuckFor;

    /**
     * Отметить попытку дойти. Зовётся раз в решение стратегии.
     * <p>
     * Это и есть лечение зависания, о котором сообщал игрок: работа
     * выбирается как первая подходящая, и если до неё нельзя дойти —
     * житель выбирал её снова и снова, а всё остальное дело стояло.
     * Теперь он отступается и берётся за следующее.
     */
    public void noteReachAttempt(BlockPos target) {
        if (target == null || squaredDistanceTo(Vec3d.ofCenter(target))
                <= CLOSE_ENOUGH * CLOSE_ENOUGH) {
            stuckOn = null;
            stuckFor = 0;
            return;
        }

        if (!target.equals(stuckOn)) {
            stuckOn = target.toImmutable();
            stuckFor = 1;
            return;
        }
        if (++stuckFor < GIVE_UP_AFTER) {
            return;
        }

        if (unreachable.size() >= REMEMBER_AT_MOST) {
            unreachable.clear();
        }
        unreachable.put(stuckOn, getWorld().getTime() + FORGET_AFTER);
        stuckOn = null;
        stuckFor = 0;
    }

    /** Отступился ли житель от этой точки — и не пора ли забыть отказ. */
    public boolean isUnreachable(BlockPos pos) {
        Long until = unreachable.get(pos);
        if (until == null) {
            return false;
        }
        if (getWorld().getTime() > until) {
            unreachable.remove(pos);
            return false;
        }
        return true;
    }

    /**
     * Инструмент в руке — часть облика, а не добыча.
     * <p>
     * Ванильный моб роняет снаряжение при смерти, и топор лесоруба
     * превратился бы в бесконечный источник железа: житель погиб, топор
     * упал, наняли нового — и снова топор.
     */
    private void keepTools() {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setEquipmentDropChance(slot, 0.0f);
        }
    }

    @Override
    protected void initGoals() {
        // Порядок значим: работа и прогулка обе просят управление движением,
        // и работа обязана быть выше — иначе житель уходил бы бродить
        // посреди дела.
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new CitizenWorkGoal(this));
        goalSelector.add(2, new WanderAroundFarGoal(this, 0.5));
        goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
        goalSelector.add(4, new LookAroundGoal(this));
    }

    /** Точка, к которой житель идёт по работе, или {@code null}. */
    public BlockPos workTarget() {
        return workTarget;
    }

    public void setWorkTarget(BlockPos workTarget) {
        this.workTarget = workTarget == null ? null : workTarget.toImmutable();
    }

    public void link(UUID settlementId, UUID citizenId) {
        this.settlementId = settlementId;
        this.citizenId = citizenId;
    }

    public Optional<UUID> settlementId() {
        return Optional.ofNullable(settlementId);
    }

    public Optional<UUID> citizenId() {
        return Optional.ofNullable(citizenId);
    }

    /** Запись жителя, к которой привязано это тело. */
    public Optional<Citizen> data(ServerWorld world) {
        if (settlementId == null || citizenId == null) {
            return Optional.empty();
        }
        return SettlementManager.get(world).byId(settlementId).flatMap(s -> s.citizen(citizenId));
    }

    /**
     * Щелчок по жителю: разговор.
     * <p>
     * Отвечает только тот, кому есть что сказать, — выдающий квесты. Все
     * остальные пропускают нажатие дальше, чтобы не съедать игроку действие
     * предметом в руке: житель, глотающий удар кайлом, раздражал бы.
     */
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getWorld().isClient() || !(player instanceof ServerPlayerEntity server)
                || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        ServerWorld world = (ServerWorld) getWorld();
        Citizen citizen = data(world).orElse(null);
        if (citizen == null || citizen.profession().isEmpty()) {
            return ActionResult.PASS;
        }
        if (QuestManager.all().values().stream()
                .noneMatch(quest -> quest.giver().equals(citizen.profession().get()))) {
            return ActionResult.PASS;
        }

        SettlementManager manager = SettlementManager.get(world);
        Settlement village = settlementId().flatMap(manager::byId).orElse(null);
        if (village == null) {
            return ActionResult.PASS;
        }

        Quests.talk(manager, village, server, citizen);
        return ActionResult.SUCCESS;
    }

    public void applyFrom(Citizen citizen) {
        label(citizen);
        setHealth(citizen.health());
    }

    /**
     * Подпись над жителем: имя и ремесло.
     * <p>
     * Ремесло рядом с именем не украшение: игрок раздаёт работу и хочет
     * видеть, кто перед ним, не открывая пульта. Имя без ремесла остаётся
     * у того, кому его ещё не дали.
     * <p>
     * Обновляется каждое решение стратегии, а не по событию смены
     * профессии: событий этих три — основание, автораздача и приказ
     * игрока, — и забыть одно значило бы показывать устаревшую подпись.
     * Повторная установка того же текста ничего не стоит: отслеживаемые
     * данные сравнивают значения, и в сеть уходят только изменения.
     */
    public void label(Citizen citizen) {
        Text name = citizen.profession()
                .flatMap(ProfessionManager::get)
                .map(profession -> (Text) Text.translatable("villagepax.citizen.label",
                        citizen.fullName(), Text.translatable(profession.displayName())))
                .orElse(Text.literal(citizen.fullName()));

        setCustomName(name);
        setCustomNameVisible(true);
    }

    /**
     * Голос жителя.
     * <p>
     * Взят у деревенского намеренно: узнаваемое «хм» — уже язык, которому
     * игрока учить не надо, и он ровно про то, что перед ним мирный
     * житель. Своих записей у мода нет, и заводить их незачем.
     */
    @Override
    protected SoundEvent getAmbientSound() {
        // Спящий молчит: бормотание из дома ночью — это не жизнь, а помеха.
        return isSleeping() ? null : SoundEvents.ENTITY_VILLAGER_AMBIENT;
    }

    /**
     * Голоса реже, чем у ванильных мобов.
     * <p>
     * В колонии их десяток, и они стоят кучей у стройки: ванильные четыре
     * секунды превратили бы деревню в непрерывный гул. Двадцать — это
     * голоса на площади, а не гудение.
     */
    @Override
    public int getMinAmbientSoundDelay() {
        return 400;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VILLAGER_DEATH;
    }

    /**
     * Возвращает изменившееся состояние в данные. Вызывается перед исчезновением
     * тела — при выгрузке чанка, смерти или остановке сервера.
     */
    public void writeBackTo(ServerWorld world) {
        if (settlementId == null || citizenId == null) {
            return;
        }
        SettlementManager.get(world).update(settlementId, settlement -> settlement.citizen(citizenId)
                .ifPresent(citizen -> {
                    citizen.setPosition(getPos());
                    citizen.setHealth(getHealth());
                    citizen.setEntityUuid(null);
                }));
    }

    /**
     * Единственная надёжная точка возврата состояния.
     * <p>
     * Раньше это делалось из обработчика выгрузки чанка, и это было ошибкой:
     * начиная с 1.17 сущности живут в отдельном индексе и выгружаются
     * независимо от чанков, а смерть и остановка сервера туда вообще
     * не попадали. Здесь же перехватываются все пути исчезновения тела.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (getWorld() instanceof ServerWorld serverWorld) {
            if (reason == RemovalReason.KILLED) {
                buryCitizen(serverWorld);
            } else {
                writeBackTo(serverWorld);
            }
        }
        super.remove(reason);
    }

    /**
     * Погибший житель уходит из поселения совсем. Возвращать в данные его
     * позицию было бы хуже, чем ничего: на следующей загрузке чанка он
     * возродился бы целым, и смерть перестала бы что-то значить.
     */
    private void buryCitizen(ServerWorld world) {
        if (settlementId == null || citizenId == null) {
            return;
        }
        SettlementManager.get(world).update(settlementId, settlement ->
                settlement.citizen(citizenId).ifPresent(citizen -> {
                    settlement.removeCitizen(citizenId);
                    VillagePax.LOGGER.info("Житель {} из поселения {} погиб",
                            citizen.fullName(), settlement.name());
                }));
    }

    /**
     * Тело не должно попадать в сохранение чанка: иначе после перезахода в мир
     * рядом с данными жителя окажется ещё и его старая копия.
     */
    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    /** Идентификаторы пишутся для отладки командой data, в сохранение они не попадают. */
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (settlementId != null) {
            nbt.putUuid(SETTLEMENT_KEY, settlementId);
        }
        if (citizenId != null) {
            nbt.putUuid(CITIZEN_KEY, citizenId);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        settlementId = nbt.containsUuid(SETTLEMENT_KEY) ? nbt.getUuid(SETTLEMENT_KEY) : null;
        citizenId = nbt.containsUuid(CITIZEN_KEY) ? nbt.getUuid(CITIZEN_KEY) : null;
    }

    public Optional<Settlement> settlement(ServerWorld world) {
        return settlementId == null ? Optional.empty() : SettlementManager.get(world).byId(settlementId);
    }
}
