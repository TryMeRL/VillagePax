package com.villagepax.entity;

import com.villagepax.VillagePax;
import com.villagepax.core.config.Configs;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.screen.QuestNet;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.trade.Trading;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Hazards;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import com.villagepax.sim.Warehouse;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.World;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;

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
        avoidFire();
    }

    /**
     * Огонь для жителя дороже, чем для ванильного моба.
     * <p>
     * У ванили костёр стоит шестнадцать шагов пути — «дорого, но пройти
     * можно», — и моб идёт прямо через очаг, если обход длиннее. Для
     * жителя это смертельно: в костре он теряет здоровье, а пути из
     * огненного узла ваниль почти не строит. Шестьдесят четыре означают
     * «обойди, даже если крюк через всю деревню».
     * <p>
     * Но не запрет: запрет отнял бы у попавшего в огонь последнюю
     * возможность выйти самому, а рефлекс {@link #stepOutOfTrouble}
     * срабатывает раз в полсекунды и может не успеть.
     */
    private void avoidFire() {
        setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 64.0f);
        setPathfindingPenalty(PathNodeType.DANGER_FIRE, 32.0f);
        setPathfindingPenalty(PathNodeType.DAMAGE_OTHER, 64.0f);
        setPathfindingPenalty(PathNodeType.DANGER_OTHER, 32.0f);
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

    /**
     * Сколько решений подряд житель простоял без материалов.
     * <p>
     * Не сохраняется, и не должно: терпение — состояние минуты, а не мира.
     * После перезахода билдер начнёт ждать заново, и это верно — курьер
     * тоже начнёт ходить заново.
     */
    private int waitedForMaterials;

    private final Map<BlockPos, Long> unreachable = new HashMap<>();
    private BlockPos stuckOn;
    private int stuckFor;

    /**
     * Отметить ещё одно решение без материалов и сказать, сколько их подряд.
     * <p>
     * По этому счёту билдер решает, идти ли за материалами самому. Решение
     * заказчика: помогать, <b>если курьер не справляется</b>, — а не только
     * когда курьера нет вовсе. Курьер может спать, застрять, не дойти или
     * просто не успевать, и стройка не должна из-за этого стоять насмерть.
     */
    public int noteMaterialWait() {
        return ++waitedForMaterials;
    }

    /**
     * Сколько решений подряд простоял — <b>не отмечая</b> ещё одно.
     * <p>
     * Разведено с {@link #noteMaterialWait} намеренно: то отмечает и
     * возвращает, и позвать его дважды за одно решение значит удвоить
     * счёт. Я на этом уже споткнулся — билдер начинал носить материалы
     * сам вдвое раньше срока.
     */
    public int materialWait() {
        return waitedForMaterials;
    }

    /** Материалы появились: терпение отсчитывается заново. */
    public void materialsArrived() {
        waitedForMaterials = 0;
    }

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
     * Отвечает только тот, кому есть что сказать, — выдающий квесты или
     * стоящий за столом торга своего народа. Все остальные пропускают
     * нажатие дальше, чтобы не съедать игроку действие предметом в руке:
     * житель, глотающий удар кайлом, раздражал бы.
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
        SettlementManager manager = SettlementManager.get(world);
        Settlement village = settlementId().flatMap(manager::byId).orElse(null);
        if (village == null) {
            return ActionResult.PASS;
        }
        // Торг — вторая причина заговорить, и её нельзя было забыть: народ
        // с прилавком, но без квестов, молчал бы на щелчок, и прилавок
        // остался бы недостижимым.
        //
        // Но торгует не всякий, а старейшина: иначе нажатие съедал бы
        // каждый житель деревни, и удар кайлом по пахарю открывал бы
        // прилавок. Тот же, с кем игрок и так разговаривает.
        Identifier profession = citizen.profession().get();
        boolean gives = QuestManager.all().values().stream()
                .anyMatch(quest -> quest.giver().equals(profession));
        boolean trades = profession.equals(Villages.ELDER)
                && Trading.tableOf(village).isPresent();
        if (!gives && !trades) {
            return ActionResult.PASS;
        }

        // Экран, а не только чат — решение заказчика. В чате видно
        // только сказанное сейчас, а игроку нужно видеть доверие, сколько
        // до следующей ступени и сколько из просимого уже в сумке.
        // Чат при этом остаётся: старейшина говорит, а экран показывает.
        Quests.greet(server, citizen);
        citizen.profession()
                .flatMap(giver -> QuestNet.viewOf(village, server.getUuid(),
                        server.getInventory(), giver, Warehouse.of(world, village)))
                .ifPresent(view -> QuestNet.send(server, view));
        return ActionResult.SUCCESS;
    }

    public void applyFrom(Citizen citizen) {
        label(citizen, Configs.get().citizenLabels());
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
    public void label(Citizen citizen, boolean visible) {
        if (!visible) {
            // Настройкой выключено: подпись снимается, а не просто
            // не обновляется — иначе она осталась бы висеть до перезахода.
            setCustomName(null);
            setCustomNameVisible(false);
            return;
        }

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
                    VillagePax.LOGGER.info("Житель {} из поселения {} погиб от {}",
                            citizen.fullName(), settlement.name(), lastCause());
                    mourn(world, settlement, citizen);
                }));
    }

    /**
     * Сказать хозяину колонии, что житель погиб и от чего.
     * <p>
     * Написано после настоящей смерти: строитель сгорел на очаге, а игрок
     * узнал об этом <b>из файла лога</b>, разбирая, почему стройка встала.
     * Потеря жителя — самое дорогое, что может случиться с колонией:
     * нанимается он днями, а гибнет за секунды. Молчать о таком нельзя,
     * и <b>причину</b> назвать обязательно: «сгорел» и «утонул» лечатся
     * по-разному, а без причины игрок не поймёт, что чинить.
     */
    private void mourn(ServerWorld world, Settlement settlement, Citizen citizen) {
        Text notice = Text.translatable("villagepax.citizen.died",
                Text.literal(citizen.fullName()), Text.literal(settlement.name()),
                lastCause());

        settlement.owner().player().ifPresentOrElse(
                owner -> {
                    ServerPlayerEntity player = world.getServer().getPlayerManager()
                            .getPlayer(owner);
                    if (player != null) {
                        player.sendMessage(notice, false);
                    }
                },
                // У деревни народа хозяина нет, и сообщать некому. Но если
                // игрок стоит рядом — он это видел, и молчать странно.
                () -> world.getPlayers(near -> near.squaredDistanceTo(this) <= 64 * 64)
                        .forEach(near -> near.sendMessage(notice, false)));
    }

    /**
     * От чего погиб. Ванильное описание смерти без имени жертвы: «сгорел
     * в огне», «утонул», «убит зомби».
     */
    private Text lastCause() {
        DamageSource source = getRecentDamageSource();
        return source == null
                ? Text.translatable("villagepax.citizen.died.unknown")
                : Text.translatable("death.attack." + source.getName());
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

    /** Как часто проверяется, не стоит ли житель на заборе. */
    private static final int OFF_FENCE_EVERY = 10;

    /**
     * Докуда ищется твёрдая земля, чтобы сойти с забора.
     * <p>
     * Два блока: забор житель пересекает по ширине за один шаг, и дальше
     * искать незачем — если и там нет земли, значит он на заборе среди
     * пустоты, и переносить его было бы уже не помощью.
     */
    private static final int OFF_FENCE_REACH = 2;

    @Override
    public void tick() {
        super.tick();

        if (!getWorld().isClient() && age % OFF_FENCE_EVERY == 0) {
            stepOutOfTrouble();
        }
    }

    /**
     * Выбраться из беды: с забора и из огня.
     * <p>
     * Две беды, а рефлекс один, потому что ловушка у них одна и та же:
     * <b>из такой точки не строится путь</b>. У забора коробка
     * столкновений в полтора блока — узла пути на его верхушке нет;
     * у костра узел непроходим по самой ванильной таблице. И в том, и
     * в другом случае навигация не может даже начать маршрут, житель
     * стоит на месте, а решение раз за разом гонит его к цели.
     * <p>
     * С забора он от этого ходит кругами — на это жаловался игрок.
     * В костре — <b>сгорает заживо</b>: ровно так и погиб строитель,
     * перестраивавший дом. Ни то, ни другое не лечится поиском пути:
     * лечится тем, что жителя оттуда снимают.
     * <p>
     * Один-два блока в сторону — на землю, с которой путь снова
     * считается и на которой не жжётся. Возвращает истину, если сняли:
     * так это и проверяется тестом, без прогона тиков.
     */
    public boolean stepOutOfTrouble() {
        BlockPos under = getBlockPos().down();
        BlockState support = getWorld().getBlockState(under);

        boolean onFence = support.isIn(BlockTags.FENCES)
                || support.isIn(BlockTags.WALLS)
                || support.isIn(BlockTags.FENCE_GATES);
        boolean inTrouble = Hazards.standingHurts(getWorld(), getBlockPos());

        if (!onFence && !inTrouble) {
            return false;
        }

        return moveToSafety(null);
    }

    /**
     * Отойти с клетки, которую сейчас займёт блок.
     * <p>
     * Зовётся стройкой перед установкой: житель, оставшийся в клетке,
     * оказался бы внутри блока, а если блок ещё и жжётся — внутри огня,
     * откуда ваниль не строит пути. Отходит только тот, кто действительно
     * стоит в этой клетке; прочих трогать незачем.
     */
    public boolean stepAsideFrom(BlockPos taken) {
        if (!getBoundingBox().intersects(new Box(taken))) {
            return false;
        }
        return moveToSafety(taken);
    }

    /**
     * Ближайшее место, где можно стоять, — и перенос туда.
     * <p>
     * Перенос, а не «пойди туда»: житель в беде как раз и не может никуда
     * пойти — из непроходимого узла пути не строится. Один-два блока
     * в сторону выглядят как шаг, а не как телепорт, и это честно:
     * ровно столько он и прошёл бы сам, если бы мог.
     */
    private boolean moveToSafety(BlockPos avoid) {
        for (int radius = 1; radius <= OFF_FENCE_REACH; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    // Своя высота и на блок ниже: с забора сходят вниз —
                    // его верх и есть та лишняя половина блока, из-за
                    // которой житель там стоял.
                    for (int down = 0; down <= 1; down++) {
                        BlockPos spot = getBlockPos().add(dx, -down, dz);
                        if (spot.equals(avoid) || !isSafeFooting(spot)) {
                            continue;
                        }
                        getNavigation().stop();
                        refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(),
                                spot.getZ() + 0.5, getYaw(), getPitch());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Ноги на твёрдом и не на заборе, голова в пустоте. */
    private boolean isSafeFooting(BlockPos spot) {
        BlockPos below = spot.down();
        BlockState ground = getWorld().getBlockState(below);

        if (ground.isIn(BlockTags.FENCES) || ground.isIn(BlockTags.WALLS)
                || ground.isIn(BlockTags.FENCE_GATES)) {
            return false;
        }
        if (Hazards.standingHurts(getWorld(), spot)) {
            // Из огня да в полымя: снимать жителя в соседний костёр
            // было бы не помощью.
            return false;
        }
        return ground.isSolidBlock(getWorld(), below)
                && getWorld().getBlockState(spot).getCollisionShape(getWorld(), spot).isEmpty()
                && getWorld().getBlockState(spot.up())
                        .getCollisionShape(getWorld(), spot.up()).isEmpty();
    }
}
