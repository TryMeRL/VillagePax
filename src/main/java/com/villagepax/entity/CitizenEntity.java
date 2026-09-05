package com.villagepax.entity;

import com.villagepax.VillagePax;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;

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

    public CitizenEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initGoals() {
        // Заглушка на время: настоящее расписание и работа появятся вместе
        // с профессиями. Пока житель просто ходит и смотрит по сторонам,
        // чтобы поселение не выглядело мёртвым.
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new WanderAroundFarGoal(this, 0.5));
        goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
        goalSelector.add(3, new LookAroundGoal(this));
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

    public void applyFrom(Citizen citizen) {
        setCustomName(Text.literal(citizen.fullName()));
        setCustomNameVisible(true);
        setHealth(citizen.health());
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
