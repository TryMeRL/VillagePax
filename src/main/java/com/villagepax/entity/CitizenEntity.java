package com.villagepax.entity;

import com.villagepax.VillagePax;
import com.villagepax.core.config.Configs;
import com.villagepax.core.profession.ProfessionManager;
import com.villagepax.core.quest.QuestManager;
import com.villagepax.screen.FestivalNet;
import com.villagepax.screen.QuestNet;
import com.villagepax.sim.quest.Quests;
import com.villagepax.sim.trade.Trading;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import com.villagepax.core.culture.Culture;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.life.Ages;
import com.villagepax.sim.life.Natures;
import com.villagepax.sim.Hazards;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.Villages;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.diplomacy.Relations;
import com.villagepax.sim.work.Schedule;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.entity.EntityType;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.World;

import java.util.List;
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
public class CitizenEntity extends PathAwareEntity implements GeoEntity {

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

    /**
     * Обоз, которому принадлежит это тело, и поселение, где он гостит.
     * <p>
     * Не сохраняется, как и всё в теле: запись обоза лежит у принимающего
     * поселения, а тело для неё — только кукла на день. После перезахода
     * {@link com.villagepax.sim.trade.Caravans} поставит его заново.
     */
    private UUID caravanId;
    private UUID caravanHost;

    public CitizenEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        keepTools();
        avoidHarm();
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
    private void avoidHarm() {
        setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 64.0f);
        setPathfindingPenalty(PathNodeType.DANGER_FIRE, 32.0f);
        setPathfindingPenalty(PathNodeType.DAMAGE_OTHER, 64.0f);
        setPathfindingPenalty(PathNodeType.DANGER_OTHER, 32.0f);
        // Рыхлый снег выглядит сугробом, и ваниль ведёт по нему, как
        // по дороге: клетка над ним для неё ничего не стоит. Провалившийся
        // изнутри пути уже не строит и замерзает — так курьер пони погиб
        // в двух шагах от избы. По снегу житель не идёт вовсе: ни одна
        // дорога не стоит того, чтобы на ней замёрзнуть.
        setPathfindingPenalty(PathNodeType.DANGER_POWDER_SNOW, -1.0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                // Кулак. Всё остальное приносит оружие: модификатор меча
                // считается в силу удара, как у любого моба с мечом, —
                // поэтому стража с железом бьёт всерьёз, а пахарь,
                // схватившийся за вилы, почти никак.
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.0);
    }

    /**
     * Удар жителя — и удар оружия его народа.
     * <p>
     * У игрока особый удар случается сам: ваниль зовёт {@code postHit}
     * у предмета в руке. У моба — нет, он бьёт атрибутом, и секира
     * в руке стража морозила бы только в руках игрока. Поэтому здесь.
     */
    @Override
    public boolean tryAttack(net.minecraft.entity.Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && target instanceof net.minecraft.entity.LivingEntity struck) {
            com.villagepax.item.gear.GearWeapons.gearOf(getMainHandStack())
                    .ifPresent(gear -> gear.strike(struck, this));
        }
        return hit;
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
            if (target != null) {
                // Дошёл — прошлые отказы от этой точки не в счёт.
                strikes.remove(target);
            }
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

        BlockPos lost = stuckOn;
        stuckOn = null;
        stuckFor = 0;

        // Второй отказ от той же точки — последняя ступень: перенос.
        // Первый — честный отказ, пусть берётся за другое; но у билдера дело
        // одно, и без переноса он через полминуты упирался бы в ту же стену
        // снова, и так до конца мира.
        int strike = strikes.merge(lost, 1, Integer::sum);
        if (strike >= RELOCATE_ON_STRIKE && relocateNear(lost)) {
            strikes.remove(lost);
            unreachable.remove(lost);
            return;
        }
        if (strikes.size() > REMEMBER_AT_MOST) {
            strikes.clear();
        }

        if (unreachable.size() >= REMEMBER_AT_MOST) {
            unreachable.clear();
        }
        unreachable.put(lost, getWorld().getTime() + FORGET_AFTER);
    }

    /**
     * С какого отказа от одной и той же точки житель переносится к ней.
     * <p>
     * Лестница взята у MineColonies, где застрявший житель, «набравшись
     * решимости», переносится к цели: сперва перепрокладка пути (её делает
     * цель навигации сама), потом отказ и другое дело, и только потом —
     * перенос. Со второго отказа, а не с первого: первый бывает и у того,
     * кто просто выбрал неудачное дело.
     */
    private static final int RELOCATE_ON_STRIKE = 2;

    /** Дальше этого не переносят: это уже не «застрял у стены», а «потерялся». */
    private static final double RELOCATE_REACH = 64.0;

    /** Сколько раз житель отступался от каждой точки — до переноса или прихода. */
    private final Map<BlockPos, Integer> strikes = new HashMap<>();

    /**
     * Перенести тело на место рядом с точкой, где можно стоять.
     * <p>
     * С облачком на обоих концах: перенос виден и читается как «добрался»,
     * а не как сбой. Места рядом нет или оно слишком далеко — переноса нет.
     */
    private boolean relocateNear(BlockPos target) {
        if (!(getWorld() instanceof ServerWorld world)
                || squaredDistanceTo(Vec3d.ofCenter(target)) > RELOCATE_REACH * RELOCATE_REACH) {
            return false;
        }
        BlockPos spot = com.villagepax.sim.work.Standing.nextTo(world, target).orElse(null);
        if (spot == null) {
            return false;
        }
        world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF, getX(), getY() + 0.5, getZ(),
                8, 0.3, 0.4, 0.3, 0.02);
        getNavigation().stop();
        refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, getYaw(), getPitch());
        world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF, getX(), getY() + 0.5, getZ(),
                8, 0.3, 0.4, 0.3, 0.02);
        return true;
    }

    /**
     * Держаться этого места, когда делать нечего.
     * <p>
     * Ванильная привязь: её уважают прогулка и бегство, а навигация к делу —
     * нет, поэтому работе она не мешает. Ставится заново, только если
     * сменилась: зовут её каждое решение.
     */
    public void keepNear(BlockPos home, int range) {
        if (!home.equals(getPositionTarget()) || getPositionTargetRange() != range) {
            setPositionTarget(home, range);
        }
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
        // Драка выше дела: боец, у которого есть цель, бросает работу.
        // У мирного жителя цели не бывает — её ставят только тем, кто
        // воюет, — и потому эта цель для пахаря всё равно что нет её.
        goalSelector.add(1, new CitizenMeleeGoal(this));
        // А трус бежит от того, кого прочие ещё не заметили. Выше общего
        // бегства, потому что иначе оба спорили бы за ноги: побеждает
        // старший, и старшим должен быть тот, у кого шире круг. Для всех
        // остальных эта цель не начинается никогда — предикат спрашивает
        // характер.
        goalSelector.add(2, new FleeEntityGoal<>(this, CitizenEntity.class, COWARD_FLEES, 0.8, 1.0,
                who -> coward && who instanceof CitizenEntity fighter && fighter.isRaider()
                        && !isFighter()) {
            @Override
            public boolean canStart() {
                // Сперва два поля, и только потом поиск в округе:
                // предикат отсеивает найденных, а не отменяет поиск.
                return coward && besieged && super.canStart();
            }
        });
        // А мирный житель от бойца бежит. Бегство важнее работы по той же
        // причине, по которой драка важнее: и то и другое про жизнь.
        goalSelector.add(3, new FleeEntityGoal<>(this, CitizenEntity.class, FLEES, 0.7, 0.9,
                who -> who instanceof CitizenEntity fighter && fighter.isRaider()
                        && !isFighter()) {
            @Override
            public boolean canStart() {
                return besieged && !isFighter() && super.canStart();
            }
        });
        goalSelector.add(4, new CitizenWorkGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.5));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
        goalSelector.add(7, new LookAroundGoal(this));

        // Кого искать глазами. Предикаты спрашивают состояние, а не тип:
        // список целей строится один раз при появлении тела, а кем это
        // тело окажется — жителем, стражником, налётчиком — выясняется
        // потом. Один список на всех, поведение в состоянии.
        // Сдачи даёт только налётчик, и это не мелочь. Ванильная месть
        // не спрашивает, кто ты: с ней избитый пахарь получал цель, а
        // цель важнее бегства — и он оставался драться с мечником,
        // вооружённый кулаком. Поймано мерцающей проверкой: житель,
        // которого успели ударить, «полез в драку».
        //
        // Стражу месть не нужна: цель ей ставит собственное ремесло
        // каждое решение. А налётчику нужна — иначе он прошёл бы мимо
        // стрелка, обстреливающего его с холма, к первому попавшемуся
        // жителю.
        targetSelector.add(0, new RevengeGoal(this) {
            @Override
            public boolean canStart() {
                return isRaider() && super.canStart();
            }
        });
        // Врага среди жителей ищет только налётчик. Страже такая цель
        // не нужна вовсе: ей цель ставит собственное ремесло каждое
        // решение — и ставит осмысленнее, выбирая ближайшего к себе,
        // а не первого попавшегося в кольце обзора.
        targetSelector.add(1, new ActiveTargetGoal<>(this, CitizenEntity.class, 10, true, false,
                who -> isRaider() && who instanceof CitizenEntity other && isEnemyOf(other)));
        // А союзник ищет налётчика — того самого, что пришёл к колонии,
        // за которую его прислали. Своим ремеслом он это решить не может:
        // у куклы нет ни записи жителя, ни стратегии, которая ставила бы
        // ей цель. Значит, цель ставит глаз.
        targetSelector.add(1, new ActiveTargetGoal<>(this, CitizenEntity.class, 10, true, false,
                who -> isDefender() && who instanceof CitizenEntity other
                        && other.isRaider() && sameSiege(other)));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false,
                who -> isRaider() && who instanceof PlayerEntity target
                        && ownsWhatWeCameFor(target)));
    }

    /**
     * Враг ли это тело для налётчика.
     * <p>
     * Враг — всякий, кто держится осаждённой колонии: и её пахарь, и её
     * стражник. <b>Своих не бьёт никто</b>, и это не украшение: проверка,
     * которой позволили сказать «враг всегда», показала, чем это кончается,
     * — отряд взял на прицел сам себя, не дойдя до ворот.
     */
    private boolean isEnemyOf(CitizenEntity other) {
        if (other.isRaider()) {
            return false;
        }
        // Союзник, пришедший за эту колонию, — враг такой же, как её пахарь:
        // он пришёл мешать. Без этого отряд ходил бы мимо мечника,
        // рубящего его в спину, к безоружному жителю.
        if (other.isDefender()) {
            return sameSiege(other);
        }
        return other.settlementId().filter(host -> host.equals(raidHost)).isPresent();
    }

    /** Об одной ли осаде идёт речь: тот же отряд у тех же ворот. */
    private boolean sameSiege(CitizenEntity other) {
        return raidId != null && raidId.equals(other.raidId());
    }

    /** Тот ли это игрок, к чьей колонии пришли. */
    private boolean ownsWhatWeCameFor(PlayerEntity player) {
        if (raidHost == null || !(getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        return SettlementManager.get(serverWorld).byId(raidHost)
                .filter(colony -> colony.owner().isOwnedBy(player.getUuid()))
                .isPresent();
    }

    /**
     * На сколько падает доверие деревни за убитого жителя.
     * <p>
     * Тридцать — дороже ограбленного обоза (двадцать пять) и заметно
     * ближе к тому пределу, за которым деревня посылает людей. Двух
     * убитых довольно, чтобы за игроком пришли, и это соразмерно:
     * житель у деревни один из шести.
     */
    private static final int MURDER_COSTS = 30;

    /**
     * Облик: путь к текстуре, каким его назначил сервер.
     * <p>
     * Строкой, а не числом: народы приходят из датапаков, и нумеровать
     * их значило бы завести реестр, который обязан совпасть у сервера
     * и клиента. Путь совпадать не обязан — чего нет, то клиент заменит
     * известным обликом сам.
     */
    private static final TrackedData<String> LOOK =
            DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.STRING);

    /**
     * Ребёнок ли это тело.
     * <p>
     * Отслеживаемым полем по той же причине, что и облик: возраст живёт
     * в записи жителя <b>на сервере</b>, а рисует тело клиент. Считать
     * рост по чему-то своему клиент не может — у него нет ни поселения,
     * ни жителя.
     * <p>
     * И это не украшение. Ребёнок, не отличимый от взрослого, есть только
     * в списке жителей — то есть его нет: игрок не открывает список,
     * чтобы посмотреть, кто бегает по улице. Разница в росте видна
     * с другого конца деревни и не требует ни одного слова.
     */
    private static final TrackedData<Boolean> CHILD =
            DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /**
     * Дремлет на лежанке — на полу, где кровати нет.
     * <p>
     * Ванильный сон тут не годится: игра каждый тик будит того, кто спит
     * не в кровати, а стратегия через полсекунды укладывала снова. Житель
     * всю ночь подскакивал и крутил головой — заказчик так и описал:
     * «прыгают на месте и вертят головой». На лежанке он теперь стоит
     * и дремлет: та же дремота, что во сне, без укладывания.
     */
    private static final TrackedData<Boolean> DOZING =
            DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /**
     * Пляшет в хороводе на празднике.
     * <p>
     * Отслеживаемым полем по той же причине, что дремота: решает сервер —
     * он знает, что сегодня праздник и что житель в кругу, — а рисует
     * клиент, которому праздника не видно.
     */
    private static final TrackedData<Boolean> DANCING =
            DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /**
     * Борется на руках за игрой.
     * <p>
     * Отслеживаемым полем, как пляска: партию знает сервер, а рисует клиент,
     * и рука соперника должна дрожать от натуги у всех, кто смотрит.
     */
    private static final TrackedData<Boolean> WRESTLING =
            DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /**
     * Рост народа, каким его объявил датапак.
     * <p>
     * Отслеживаемым полем по той же причине, что облик и детство: культура
     * живёт в датапаке <b>сервера</b>, а рисует тело клиент. Числом,
     * а не опознавателем народа: клиенту незачем знать, кто перед ним, —
     * ему надо знать, какого этот человек роста.
     * <p>
     * <b>Число одно, а не три.</b> Ширина и голова считаются из него
     * ({@code Stature}), и это не лень, а защита: народ с крошечной
     * головой на широком туловище читается как ошибка, а не как замысел,
     * и давать автору датапака возможность так ошибиться незачем.
     */
    private static final TrackedData<Float> STATURE =
            DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /**
     * Фраза над головой: что житель сказал вслух.
     * <p>
     * Отслеживаемым полем, а не чатом: чат забивается и не показывает, кто
     * говорит, а над головой видно — вот этот сказал. Ключ с подстановками,
     * а не готовая строка: клиент покажет её на своём языке.
     */
    private static final TrackedData<Optional<Text>> SPEECH =
            DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.OPTIONAL_TEXT_COMPONENT);

    /** Сколько тиков фраза видна: три секунды. */
    public static final int SPEECH_TICKS = 60;

    /** Сколько тиков после фразы новая не встаёт: две секунды. */
    public static final int SPEECH_GAP = 40;

    /** До какого тика мира фраза видна. Только сервер; не сохраняется: это голос, а не память. */
    private long speechUntil;

    /** Когда сказана последняя фраза. */
    private long spokeAt = -SPEECH_GAP;

    // --- движения ------------------------------------------------------------
    //
    // Имена общие для всех тел: человек и конь «идут», «работают» и «дышат»
    // по-разному, но называется это одинаково — а какие кости под именем
    // поворачиваются, решает модель народа.

    /** Шаг: размах от пройденного пути, от стояния до бега без перехода. */
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    /** Стоит без дела: руки качаются, а раз в дюжину секунд он чешет затылок. */
    private static final RawAnimation REST = RawAnimation.begin().thenLoop("rest");
    /** Руки на ходу — тоже от пройденного пути, в такт ногам. */
    private static final RawAnimation STRIDE = RawAnimation.begin().thenLoop("stride");
    /** Несёт груз обеими руками: курьер с мешком, билдер с блоком. */
    private static final RawAnimation CARRY = RawAnimation.begin().thenLoop("carry");
    /** Взмах без особого орудия. */
    private static final RawAnimation WORK = RawAnimation.begin().thenLoop("work");
    /** Топор и кирка: удар из-за головы обеими руками. */
    private static final RawAnimation CHOP = RawAnimation.begin().thenLoop("chop");
    /** Мотыга и лопата: низкий замах к земле. */
    private static final RawAnimation DIG = RawAnimation.begin().thenLoop("dig");
    /** Блок в руке: положить перед собой. */
    private static final RawAnimation PLACE = RawAnimation.begin().thenLoop("place");
    /** Меч: косой удар. */
    private static final RawAnimation STRIKE = RawAnimation.begin().thenLoop("strike");
    /** Боец с целью: оружие наготове. */
    private static final RawAnimation GUARD = RawAnimation.begin().thenLoop("guard");
    /** Спит: руки вдоль тела. */
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation BREATHE = RawAnimation.begin().thenLoop("breathe");
    /** Дыхание спящего — медленнее и глубже. */
    private static final RawAnimation DOZE = RawAnimation.begin().thenLoop("doze");
    /** На бегу подаётся вперёд. */
    private static final RawAnimation LEAN = RawAnimation.begin().thenLoop("lean");
    /** Машет рукой тому, кто заговорил. Запускается сервером. */
    private static final RawAnimation GREET = RawAnimation.begin().thenPlay("greet");
    /** Пляшет в хороводе на празднике: руки вверх и в стороны, по очереди. */
    private static final RawAnimation DANCE = RawAnimation.begin().thenLoop("dance");
    /** Туловище в такт пляске — вместо спокойного дыхания. */
    private static final RawAnimation SWAY = RawAnimation.begin().thenLoop("sway");
    /** Подбрасывает мячики: затейник у своего прилавка. */
    private static final RawAnimation JUGGLE = RawAnimation.begin().thenLoop("juggle");
    /** Ликует: кончилось состязание. Запускается сервером, как взмах руки. */
    private static final RawAnimation CHEER = RawAnimation.begin().thenPlay("cheer");
    /** Бросает кости: трясёт кулак у груди и выбрасывает вперёд. Запускается сервером. */
    private static final RawAnimation THROW = RawAnimation.begin().thenPlay("throw");
    /** Борется на руках: рука вперёд и согнута, мелкая дрожь от натуги. */
    private static final RawAnimation WRESTLE = RawAnimation.begin().thenLoop("wrestle");

    /** Дорожка рук — её же зовёт сервер, чтобы помахать. */
    private static final String ARMS = "руки";

    /**
     * С какого размаха шага житель уже бежит, а не идёт.
     * <p>
     * Семь десятых: столько набирает беглец от налётчика и страж, бегущий
     * к нему, а работник по делу держит около половины.
     */
    private static final float RUNNING = 0.7f;

    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);

    /** Опознаватели набега, если это боец, а не житель. */
    private UUID raidId;
    private UUID raidHost;

    /** Стража ли это тело: перечитывается раз в секунду, см. {@link #isGuard}. */
    private boolean guard;

    /**
     * Пришли ли к его деревне: перечитывается там же и тогда же.
     * <p>
     * Заведено ради цены, и цена была измерена. Две цели бегства
     * обшаривали округу <b>каждый тик у каждого жителя</b> — и у пахаря,
     * которому бежать не от кого, тоже. На колонии в сорок один двор это
     * стоило шестьсот семьдесят микросекунд из двух тысяч двухсот, треть
     * всего тика тел.
     * <p>
     * Поиск в округе — самая дорогая вещь, какую мод может попросить
     * у сервера, и просить её надо только тогда, когда есть кого искать.
     * Налётчик приходит не сам по себе: он приходит <b>к осаждённой
     * деревне</b>, и пока осады нет, бежать не от кого никому.
     * <p>
     * Секунда задержки на то, чтобы это заметить, ничего не стоит:
     * осада объявляется за день, а идут налётчики через полдеревни.
     */
    private boolean besieged;

    /**
     * Стоит ли это тело <b>за</b> колонию, а не против неё.
     * <p>
     * Куклой приходят обе стороны, и отряд у них общий; разводит их
     * это поле. Оно же бережёт деревню от чужого траура: павший союзник
     * её людей не убавляет.
     */
    private boolean defender;

    /** Как часто тело перечитывает своё ремесло, в тиках. */
    private static final int ROLE_EVERY = 20;

    /** Точка, к которой житель идёт по работе, или {@code null}. */
    public BlockPos workTarget() {
        return workTarget;
    }

    public void setWorkTarget(BlockPos workTarget) {
        this.workTarget = workTarget == null ? null : workTarget.toImmutable();
    }

    /**
     * На что житель смотрит, стоя у цели: грядка, ствол, прилавок.
     * <p>
     * Цель — это где встать, а дело лежит рядом с ней: фермера ставят у
     * грядки, а не в грядку. Не сохраняется: это взгляд, а не состояние.
     */
    private BlockPos workFocus;

    public BlockPos workFocus() {
        return workFocus;
    }

    public void setWorkFocus(BlockPos workFocus) {
        this.workFocus = workFocus;
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

    /**
     * Привязать тело к набегу: оно кукла, и воюет за пославшую деревню.
     *
     * @param colony осаждаемая колония: её жителей он и пришёл бить
     * @param party  отряд, чтобы отряд мог узнать о его смерти
     */
    public void linkRaid(UUID colony, UUID party) {
        this.raidHost = colony;
        this.raidId = party;
        this.defender = false;
    }

    /**
     * Привязать тело к той же осаде, но <b>на другой стороне</b>: союзник
     * пришёл за колонию.
     * <p>
     * Отряд тот же — по нему союзник и уйдёт, когда осада кончится, —
     * а сторона другая, и различает их одно поле. Иначе пришлось бы
     * заводить союзникам собственный отряд, который ничем, кроме знака,
     * от осадного не отличается.
     */
    public void linkDefence(UUID colony, UUID party) {
        this.raidHost = colony;
        this.raidId = party;
        this.defender = true;
    }

    public UUID raidId() {
        return raidId;
    }

    public Optional<UUID> raidHost() {
        return Optional.ofNullable(raidHost);
    }

    /** Налётчик ли это. */
    public boolean isRaider() {
        return raidId != null && !defender;
    }

    /** Союзник ли это: пришёл к той же осаде, но за колонию. */
    public boolean isDefender() {
        return raidId != null && defender;
    }

    /**
     * Пришли ли к деревне этого тела.
     * <p>
     * Спрашивается двумя целями бегства — и только ими. Поле, а не поиск
     * в округе: поиск стоил трети всего тика тел, а ответ у поля тот же.
     */
    public boolean isBesieged() {
        return besieged;
    }

    /**
     * Воюет ли это тело вообще: налётчик или стража.
     * <p>
     * Спрашивается предикатами целей. Мирный житель не ищет врага
     * и не бьёт: у него не бывает цели, и потому боевая цель для него
     * всё равно что не добавлена.
     */
    public boolean isFighter() {
        return isRaider() || isGuard() || isDefender();
    }

    /**
     * Стража ли это.
     * <p>
     * Из <b>записи</b> жителя, но не каждый раз: ремесло меняет игрок
     * в пульте колонии, и знать об этом надо телу, — но спрашивают это
     * поле предикаты боевых целей, а они срабатывают каждый тик и на
     * каждого соседа. Обход поселения ради каждого такого вопроса — это
     * та самая мелочь, на которой моды с работниками и садятся.
     * <p>
     * Поэтому раз в секунду, в {@link #tick}. Отставание в двадцать тиков
     * незаметно и не опаснее отставания подписи над головой, которая
     * обновляется реже.
     */
    public boolean isGuard() {
        return guard;
    }

    /** Перечитать ремесло с записи: зовётся из тика, не из предикатов. */
    private void refreshRole() {
        if (settlementId == null || citizenId == null
                || !(getWorld() instanceof ServerWorld serverWorld)) {
            guard = false;
            besieged = false;
            return;
        }
        // Осада спрашивается у поселения, а не у округи: поле дешевле
        // поиска в тысячи раз, а ответ тот же.
        besieged = SettlementManager.get(serverWorld).byId(settlementId)
                .flatMap(Settlement::siege).isPresent();
        Citizen citizen = data(serverWorld).orElse(null);
        if (citizen == null) {
            guard = false;
            return;
        }
        guard = citizen.profession().filter(Villages.GUARD::equals).isPresent();
        // И облик заодно: ремесло игрок меняет на ходу, и человек должен
        // переодеться при жизни, а не в следующей. Тем же вызовом —
        // и рост: ребёнок однажды просто оказывается взрослым, и тело
        // обязано это заметить без отдельного дня взросления.
        setLook(Looks.of(citizen));
        setChild(Ages.isChild(citizen));
    }

    /**
     * Какой текстурой рисовать это тело.
     * <p>
     * Отслеживаемым полем, а не расчётом на клиенте: культура и ремесло
     * живут в датапаке <b>сервера</b>, и клиент про них не знает ничего.
     */
    public void setLook(Identifier look) {
        String value = look.toString();
        if (!value.equals(look())) {
            dataTracker.set(LOOK, value);
        }
        // Рост ставится здесь же и только здесь.
        //
        // Раньше он ставился отдельным вызовом рядом, и это оказалось
        // приглашением забыть: куклы — налётчик, союзник и возница обоза —
        // облик получали, а рост нет. Гномий налётчик выходил на голову
        // выше гномов, которых пришёл грабить.
        //
        // Облик уже несёт имя народа, и других источников роста нет.
        // Одна дверь вместо двух: забыть теперь негде.
        Looks.cultureOf(value)
                .map(people -> new Identifier(look.getNamespace(), people))
                .ifPresent(this::setStature);
    }

    /**
     * Путь к текстуре этого тела — и никогда не пусто.
     * <p>
     * Пустоту отдаёт трекер в те кадры, пока клиент ещё не получил
     * значение с сервера, и одного такого кадра довольно, чтобы отрисовка
     * упала на разборе пустого пути. Известный облик в этот кадр —
     * единственный честный ответ.
     */
    public String look() {
        String value = dataTracker.get(LOOK);
        return value == null || value.isEmpty() ? Looks.UNKNOWN.toString() : value;
    }

    /**
     * Ростом ли это дитя.
     * <p>
     * Ставится сервером при каждом суточном ходе — вместе с обликом,
     * тем же вызовом. Отдельного дня взросления нет: пора жизни выводится
     * из прожитых дней, и тело просто однажды оказывается взрослым.
     */
    public void setChild(boolean child) {
        if (dataTracker.get(CHILD) != child) {
            dataTracker.set(CHILD, child);
            calculateDimensions();
        }
    }

    /**
     * Запомнить рост народа этого тела.
     * <p>
     * Народа, которого нет в датапаке, рисуем человеческим ростом:
     * перечитанный датапак — обычное дело, и исчезать из-за него тело
     * не должно.
     */
    public void setStature(Identifier culture) {
        Culture known = CultureManager.get(culture);
        float stature = known == null ? Culture.PLAIN_STATURE : known.stature();
        if (dataTracker.get(STATURE) != stature) {
            dataTracker.set(STATURE, stature);
        }
    }

    /** Какого роста этот народ: единица — человеческий. */
    public float stature() {
        Float value = dataTracker.get(STATURE);
        return value == null || value <= 0 ? Culture.PLAIN_STATURE : value;
    }

    public boolean isChildBody() {
        return dataTracker.get(CHILD);
    }

    /** Дремлет ли на лежанке: см. {@link #DOZING}. */
    public boolean isDozing() {
        return dataTracker.get(DOZING);
    }

    public void setDozing(boolean dozing) {
        if (dozing != isDozing()) {
            dataTracker.set(DOZING, dozing);
            if (dozing) {
                getNavigation().stop();
            }
        }
    }

    /**
     * Ребёнок ниже и уже взрослого.
     * <p>
     * Множитель, а не свои размеры: ваниль так же уменьшает детёнышей,
     * и след тела обязан совпасть с тем, что видит игрок, — иначе
     * ребёнок застрянет в дверном проёме, в который пролез его взгляд.
     */
    @Override
    public net.minecraft.entity.EntityDimensions getDimensions(
            net.minecraft.entity.EntityPose pose) {
        net.minecraft.entity.EntityDimensions grown = super.getDimensions(pose);
        return isChildBody() ? grown.scaled(CHILD_SCALE) : grown;
    }

    /** Во сколько раз ребёнок меньше взрослого. */
    public static final float CHILD_SCALE = 0.6f;

    /**
     * Забытая кукла уходит сама.
     * <p>
     * Тело набега живёт ровно столько, сколько поселение помнит его отряд,
     * и обычно уводит их сам набег. Но запись об осаде может исчезнуть
     * помимо него — старое сохранение, выкупленный мир, чужая правка
     * данных, — и тогда вооружённые куклы остались бы стоять у колонии
     * навсегда, а единственным способом от них избавиться было бы убить
     * их всех. Проверка дешёвая (раз в секунду и только у тел набега),
     * а чинит целый класс бед.
     */
    private void dropIfForgotten() {
        if (raidId == null || raidHost == null
                || !(getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        boolean remembered = SettlementManager.get(serverWorld).byId(raidHost)
                .flatMap(colony -> colony.siege())
                .filter(party -> raidId.equals(party.id()))
                .isPresent();
        if (!remembered) {
            discard();
        }
    }

    /** Обоз этого тела, если это торговец, а не житель. */
    public UUID caravanId() {
        return caravanId;
    }

    public Optional<UUID> caravanHost() {
        return Optional.ofNullable(caravanHost);
    }

    public void linkCaravan(UUID host, UUID caravan) {
        this.caravanHost = host;
        this.caravanId = caravan;
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
     * Отвечает тот, кому есть что сказать, — выдающий квесты, стоящий за
     * столом торга своего народа или сидящий вечером за игорным столом.
     * Все остальные пропускают нажатие дальше, чтобы не съедать игроку
     * действие предметом в руке: житель, глотающий удар кайлом, раздражал бы.
     * Пустой рукой житель говорит, почему не сядет играть, — это ответ,
     * а не съеденное действие.
     */
    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getWorld().isClient() || !(player instanceof ServerPlayerEntity server)
                || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        ServerWorld world = (ServerWorld) getWorld();

        // Торговец обоза — не житель: у него нет ни записи, ни поселения,
        // и разговор у него свой.
        if (caravanId != null) {
            QuestNet.openCaravan(server, world, caravanHost, caravanId);
            return ActionResult.SUCCESS;
        }

        Citizen citizen = data(world).orElse(null);
        if (citizen == null) {
            return ActionResult.PASS;
        }
        SettlementManager manager = SettlementManager.get(world);
        Settlement village = settlementId().flatMap(manager::byId).orElse(null);
        if (village == null) {
            return ActionResult.PASS;
        }

        // Кому есть что сказать по щелчку своим делом.
        //
        // Затейник отвечает всегда — и в своей колонии, и в чужой деревне:
        // праздник для всех, и лавка его — праздничный товар, а не торг
        // с самим собой.
        //
        // Торг — вторая причина заговорить, и её нельзя было забыть: народ
        // с прилавком, но без квестов, молчал бы на щелчок, и прилавок
        // остался бы недостижимым. Но торгует не всякий, а купец: иначе
        // нажатие съедал бы каждый житель деревни, и удар кайлом по пахарю
        // открывал бы прилавок. Именно купец, а не старейшина, — это
        // «разделим обязанности» заказчика. Пока купца в деревне нет,
        // за прилавком по-прежнему старейшина: спрашивается об этом одно
        // место на весь мод, иначе тело и экран разошлись бы во мнениях,
        // и игрок щёлкал бы по человеку, который открывает пустоту.
        //
        // И торгуют с игроком только чужие: свой купец за своим прилавком
        // продавал бы игроку его же зерно за его же монету. Колонии ларёк
        // всё равно нужен — у него останавливается обоз, — но разговор
        // с самим собой не разговор.
        Optional<Identifier> craft = citizen.profession();
        boolean hosts = craft.filter(Villages.ENTERTAINER::equals).isPresent();
        boolean gives = craft.filter(id -> QuestManager.all().values().stream()
                .anyMatch(quest -> quest.giver().equals(id))).isPresent();
        boolean trades = craft.filter(id -> id.equals(Villages.counterKeeper(village))).isPresent()
                && village.owner().isAutonomous()
                && Trading.tableOf(village).isPresent();

        // Игра — раньше дела: у стола в сумерках сидит и старейшина, и купец.
        // Но у них своё дело по щелчку, и обычный щелчок остаётся делу,
        // а сыграть с ними — щелчок с Shift. Взрослый без ремесла тоже
        // играет, поэтому проверка ремесла — после игры.
        long time = world.getTimeOfDay();
        com.villagepax.sim.games.Games.Answer answer = com.villagepax.sim.games.Games.answer(world,
                server, village, citizen, hosts || gives || trades, Schedule.dayOf(time), time);
        if (answer == com.villagepax.sim.games.Games.Answer.WINDOW) {
            com.villagepax.screen.GamesNet.open(server, village, citizen);
            return ActionResult.SUCCESS;
        }
        if (answer != com.villagepax.sim.games.Games.Answer.PASS) {
            return ActionResult.SUCCESS;
        }
        if (hosts) {
            greet();
            FestivalNet.open(server, world, village, citizen);
            return ActionResult.SUCCESS;
        }
        if (!gives && !trades) {
            return ActionResult.PASS;
        }

        // Экран, а не только чат — решение заказчика. В чате видно
        // только сказанное сейчас, а игроку нужно видеть доверие, сколько
        // до следующей ступени и сколько из просимого уже в сумке.
        // Чат при этом остаётся: старейшина говорит, а экран показывает.
        Quests.greet(server, citizen);
        greet();

        // И сам скажет, куда идти за товаром. Игрок, который помнит
        // прилавок у старейшины, иначе решит, что торговлю сломали:
        // молча исчезнувшая возможность выглядит поломкой, даже когда
        // она просто переехала.
        if (craft.filter(Villages.ELDER::equals).isPresent() && !trades
                && village.owner().isAutonomous()
                && Trading.tableOf(village).isPresent()) {
            server.sendMessage(Text.translatable("villagepax.trade.at_the_stall"), true);
        }
        citizen.profession()
                .flatMap(giver -> QuestNet.viewOf(manager, village, server.getUuid(),
                        server.getInventory(), giver, Warehouse.of(world, village),
                        Optional.empty(), server.getMainHandStack(),
                        Schedule.dayOf(world.getTimeOfDay())))
                .ifPresent(view -> QuestNet.send(server, view));
        return ActionResult.SUCCESS;
    }

    /**
     * Трус ли это.
     * <p>
     * Полем, а не спросом у записи каждый тик: цель бегства спрашивает
     * предикат по многу раз в секунду, а запись жителя лежит в другом
     * слое и достаётся через управляющего поселениями. Ровно так же
     * и по той же причине здесь живёт {@code guard}.
     */
    private boolean coward;

    public void applyFrom(Citizen citizen) {
        label(citizen, Configs.get().citizenLabels());
        // Характер — тоже часть облика тела, и ставится он здесь по той же
        // причине, что и ремесло: у только что появившегося труса иначе
        // была бы секунда, в которую он считает себя храбрецом.
        coward = Natures.isCoward(citizen);
        setLook(Looks.of(citizen));
        setChild(Ages.isChild(citizen));
        setHealth(citizen.health());
        // И ремесло сразу, раз запись всё равно в руках: иначе у только
        // что появившегося стража была бы секунда, в которую он считает
        // себя мирным и бежит от налётчика вместо того, чтобы выйти
        // ему навстречу.
        guard = citizen.profession().filter(Villages.GUARD::equals).isPresent();
    }

    /** Насколько далеко житель замечает налётчика и пускается бежать. */
    private static final float FLEES = 10.0f;

    /**
     * И насколько далеко — трус.
     * <p>
     * Вдвое: меньше было бы не видно вовсе, больше — и трус убегал бы
     * из колонии от отряда, которого в ней ещё нет.
     */
    private static final float COWARD_FLEES = FLEES * 2.0f;

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

        Text name = titleKeyOf(citizen)
                .map(title -> (Text) Text.translatable("villagepax.citizen.label", citizen.fullName(),
                        Text.translatable(title)))
                .orElse(Text.literal(citizen.fullName()));

        setCustomName(atWork(citizen).orElse(name));
        setCustomNameVisible(true);
    }

    /**
     * Ключ имени ремесла — народного, если народ его назвал: у норманнов
     * затейник — жонглёр, у северян — скальд.
     * <p>
     * Одно место на весь мод: подпись над головой и окно игры за столом
     * называют соперника одинаково.
     *
     * @return пусто, если ремесла нет
     */
    public static Optional<String> titleKeyOf(Citizen citizen) {
        Culture people = CultureManager.get(citizen.culture());
        return citizen.profession().flatMap(id -> ProfessionManager.get(id).map(profession ->
                people == null ? profession.displayName()
                        : people.titleOf(id).orElse(profession.displayName())));
    }

    /**
     * Чем занят прямо сейчас — вместо ремесла.
     * <p>
     * Жалоба заказчика: «пусть строитель в чужой деревне показывает,
     * что он сейчас строит». В своей колонии это видно в пульте —
     * очередь стройки со ступенями и полосой готовности; в чужой деревне
     * пульта нет, и билдер, снующий с блоком в руках, выглядел просто
     * человеком по имени «Строитель». Теперь над ним написано, что
     * именно растёт.
     * <p>
     * Только у того, кто и вправду взялся: занятое здание живёт
     * в состоянии работы, и без него подпись остаётся обычной. Написать
     * «строит» тому, кто стоит без дела, значило бы соврать.
     */
    private Optional<Text> atWork(Citizen citizen) {
        if (settlementId == null || !(getWorld() instanceof ServerWorld serverWorld)) {
            return Optional.empty();
        }
        UUID site = citizen.jobState().building().orElse(null);
        if (site == null || citizen.profession()
                .filter(com.villagepax.sim.build.BuildJob.BUILDER::equals).isEmpty()) {
            return Optional.empty();
        }
        return SettlementManager.get(serverWorld).byId(settlementId)
                .flatMap(settlement -> settlement.building(site))
                .filter(com.villagepax.sim.build.BuildJob::isUnderConstruction)
                .map(what -> Text.translatable("villagepax.citizen.building",
                        citizen.fullName(),
                        Text.translatable(com.villagepax.core.building.BuildingTypes
                                .displayName(what.type()))));
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

    /** Высота голоса — по облику: см. {@link Voice}. */
    @Override
    public float getSoundPitch() {
        return super.getSoundPitch() * Voice.pitch(Looks.words(look()), isChildBody());
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
            if (caravanId != null) {
                // Торговец обоза — кукла: возвращать в данные нечего.
                // А вот убитого надо разграбить: в этом весь смысл.
                if (reason == RemovalReason.KILLED) {
                    robCaravan(serverWorld, getRecentDamageSource());
                }
            } else if (isRaider()) {
                // Боец набега — тоже кукла: в данные возвращать нечего,
                // но отряд обязан узнать, что его стало меньше.
                if (reason == RemovalReason.KILLED) {
                    com.villagepax.sim.war.Raids.fell(serverWorld, this);
                }
            } else if (raidId != null) {
                // Павший союзник — тоже кукла, и её смерть не траур
                // осаждающей деревне: она своих не теряла.
            } else if (reason == RemovalReason.KILLED) {
                buryCitizen(serverWorld);
            } else {
                writeBackTo(serverWorld);
            }
        }
        super.remove(reason);
    }

    /**
     * Торговца убили: товар рассыпается, деревня запоминает.
     * <p>
     * Это «перехватить» из плана про караваны: грабёж возможен и наказуем.
     */
    private void robCaravan(ServerWorld world, DamageSource cause) {
        UUID killer = cause != null && cause.getAttacker() instanceof PlayerEntity thief
                ? thief.getUuid() : null;
        com.villagepax.sim.trade.Caravans.robbed(world, this, killer);
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
        SettlementManager manager = SettlementManager.get(world);
        manager.update(settlementId, settlement ->
                settlement.citizen(citizenId).ifPresent(citizen -> {
                    // Горе считается до удаления: после него спрашивать,
                    // кто помнил убитого, уже не у кого.
                    com.villagepax.sim.life.Bonds.mourn(world, settlement, citizen);
                    settlement.removeCitizen(citizenId);
                    VillagePax.LOGGER.info("Житель {} из поселения {} погиб от {}",
                            citizen.fullName(), settlement.name(), lastCause());
                    mourn(world, settlement, citizen);
                }));
        answerFor(world, manager);
    }

    /**
     * Убийство жителя деревни стоит игроку доверия — и стоит дорого.
     * <p>
     * До этого убить человека в деревне было <b>бесплатно</b>: старейшина
     * говорил с убийцей так же приветливо, как и до, а разбойник платил
     * только за ограбленный обоз. Это ровно та «агрессия игрока», которую
     * дизайн-документ называет причиной войны, — и первая, которую мод
     * теперь считает.
     * <p>
     * Через {@link Relations#deed}: об убитом узнают и свои деревни,
     * и её соседи. Считается только у деревни народа — у колонии игрока
     * мнения о хозяине нет, и «убил своего» наказывается иначе и само:
     * колония теряет работника, которого нанимала днями.
     */
    private void answerFor(ServerWorld world, SettlementManager manager) {
        DamageSource cause = getRecentDamageSource();
        if (cause == null || !(cause.getAttacker() instanceof PlayerEntity killer)) {
            return;
        }
        Settlement settlement = manager.byId(settlementId).orElse(null);
        if (settlement == null || !settlement.owner().isAutonomous()) {
            return;
        }

        List<Relations.Shift> shifts =
                Relations.deed(manager, settlement, killer.getUuid(), -MURDER_COSTS);
        if (killer instanceof ServerPlayerEntity server) {
            server.sendMessage(Text.translatable("villagepax.citizen.murder_costs",
                            Text.literal(settlement.name()),
                            Text.literal(String.valueOf(MURDER_COSTS)))
                    .formatted(Formatting.RED), false);
            Relations.tell(server, shifts);
        }
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

    /** На каких высотах от ног ищется, куда перенести жителя из беды. */
    private static final int[] SAFETY_HEIGHTS = {0, -1, 1};

    /**
     * Три дорожки движения, и у каждой свои кости.
     * <p>
     * Ноги шагают, руки работают, туловище дышит — и всё это одновременно.
     * Одной дорожкой так не выйдет: билдер, который идёт к стене и машет
     * молотом, должен делать и то и другое, а не выбирать. Дорожки
     * не спорят между собой потому, что <b>ни одна кость не встречается
     * в двух из них</b>: шаг трогает только ноги, работа только руки
     * (у коня — шею, голову, уши и хвост), дыхание только туловище.
     * <p>
     * Решает всё то, что клиент и так знает, — ни одного нового поля
     * в сети: пройденный путь и размах шага, взмах руки (о нём сервер
     * шлёт пакет сам), предмет в руке, боевая стойка и поза сна.
     * <p>
     * Плавность перехода — четыре тика у ног и три у рук: взмах длится
     * шесть тиков, и переход в четыре съел бы его почти целиком.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar dancers) {
        // Нога одна на все случаи: размах шага — выражение от пройденного
        // пути, и у стоящего он ноль. Стояние и бег — одна и та же дорожка.
        dancers.add(new AnimationController<>(this, "ноги", 4, state ->
                state.setAndContinue(WALK)));
        dancers.add(new AnimationController<>(this, ARMS, 3, state ->
                state.setAndContinue(ARM_TRACKS.get(armsTrack(isSleeping() || isDozing(),
                        isDancing(), isWrestling(), handSwinging, isAttacking(), getMainHandStack(),
                        state.isMoving()))))
                .triggerableAnim("greet", GREET)
                .triggerableAnim("cheer", CHEER)
                .triggerableAnim("throw", THROW));
        // Борющийся подаётся вперёд, как бегущий: всем весом на руку.
        dancers.add(new AnimationController<>(this, "дыхание", 8, state ->
                state.setAndContinue(BODY_TRACKS.get(bodyTrack(isSleeping() || isDozing(),
                        isDancing(), limbAnimator.getSpeed() > RUNNING || isWrestling())))));
    }

    private static final Map<String, RawAnimation> ARM_TRACKS = Map.ofEntries(
            Map.entry("sleep", SLEEP), Map.entry("dance", DANCE), Map.entry("chop", CHOP),
            Map.entry("dig", DIG), Map.entry("strike", STRIKE), Map.entry("place", PLACE),
            Map.entry("work", WORK), Map.entry("guard", GUARD), Map.entry("juggle", JUGGLE),
            Map.entry("carry", CARRY), Map.entry("stride", STRIDE), Map.entry("rest", REST),
            Map.entry("wrestle", WRESTLE));

    private static final Map<String, RawAnimation> BODY_TRACKS = Map.of(
            "doze", DOZE, "sway", SWAY, "lean", LEAN, "breathe", BREATHE);

    /**
     * Чем заняты руки — по тому, что видно глазом.
     * <p>
     * Порядок — от сильного к слабому: спящий не машет, пляшущий пляшет,
     * даже держа топор, взмах важнее стойки, стойка важнее ноши. Какое
     * движение у взмаха, решает <b>орудие в руке</b>, а не ремесло: клиент
     * ремесла не знает, а лесоруб с мотыгой и должен рыхлить, а не рубить.
     * Мячики подбрасывают стоя — на ходу затейник их просто несёт.
     * <p>
     * Чистая и открытая: рисует по ней клиент, а сверяет проверка, —
     * глазом на клиенте порядок не проверить.
     *
     * @return имя дорожки рук
     */
    public static String armsTrack(boolean resting, boolean dancing, boolean swinging,
                                   boolean guarding, ItemStack held, boolean moving) {
        return armsTrack(resting, dancing, false, swinging, guarding, held, moving);
    }

    /**
     * То же, и с борьбой на руках: борющийся не машет орудием — рука занята
     * рукой соперника. Спящий и пляшущий не борются, и порядок это держит.
     *
     * @return имя дорожки рук
     */
    public static String armsTrack(boolean resting, boolean dancing, boolean wrestling,
                                   boolean swinging, boolean guarding, ItemStack held,
                                   boolean moving) {
        if (resting) {
            return "sleep";
        }
        if (dancing) {
            return "dance";
        }
        if (wrestling) {
            return "wrestle";
        }
        if (swinging) {
            Item tool = held.getItem();
            if (tool instanceof AxeItem || tool instanceof PickaxeItem) {
                return "chop";
            }
            if (tool instanceof HoeItem || tool instanceof ShovelItem) {
                return "dig";
            }
            if (tool instanceof SwordItem) {
                return "strike";
            }
            if (tool instanceof BlockItem) {
                return "place";
            }
            return "work";
        }
        if (guarding) {
            return "guard";
        }
        if (held.isOf(com.villagepax.item.festival.ModFestivalItems.JUGGLING_BALLS)) {
            return moving ? "stride" : "juggle";
        }
        // Ноша — всё, что не орудие: у орудия есть прочность, у мешка нет.
        // То же деление, по которому конь несёт вещь на спине, а не в зубах.
        if (!held.isEmpty() && !held.isDamageable()) {
            return "carry";
        }
        return moving ? "stride" : "rest";
    }

    /**
     * Чем занято туловище: дремлет, пляшет, подаётся вперёд на бегу или дышит.
     *
     * @return имя дорожки дыхания
     */
    public static String bodyTrack(boolean resting, boolean dancing, boolean running) {
        if (resting) {
            return "doze";
        }
        if (dancing) {
            return "sway";
        }
        return running ? "lean" : "breathe";
    }

    /**
     * Помахать тому, кто заговорил.
     * <p>
     * Сервер шлёт это сам, потому что только он знает, что разговор
     * начался: щелчок по жителю, у которого есть что сказать. Ответ
     * без жеста выглядит так, будто экран открыл сундук, а не человек.
     */
    public void greet() {
        triggerAnim(ARMS, "greet");
    }

    /** Как близко должен подойти игрок в шапке, чтобы ему помахали. */
    private static final double HAT_SEEN = 6;

    /** Машут не чаще раза в полминуты: иначе деревня махала бы без передышки. */
    private static final long WAVE_EVERY = 600;

    /** Когда житель махал игроку в шапке своего народа. Не сохраняется: это жест, а не память. */
    private long lastWave = -WAVE_EVERY;

    /**
     * Помахать игроку в шапке народа этого жителя — не чаще раза в полминуты.
     * <p>
     * Народ — по поселению, а не по облику: колонисты-норманны машут венку
     * так же, как деревня норманнов. Шапка чужого народа — просто шапка.
     *
     * @return помахал ли
     */
    public boolean wave(PlayerEntity player) {
        if (!(getWorld() instanceof ServerWorld world) || settlementId == null || player.isSpectator()) {
            return false;
        }
        long now = world.getTime();
        if (now - lastWave < WAVE_EVERY || squaredDistanceTo(player) > HAT_SEEN * HAT_SEEN) {
            return false;
        }
        Identifier culture = SettlementManager.get(world).byId(settlementId).map(Settlement::culture)
                .orElse(null);
        Item hat = culture == null ? null
                : com.villagepax.item.festival.ModFestivalItems.hatOf(culture).orElse(null);
        if (hat == null || !player.getEquippedStack(EquipmentSlot.HEAD).isOf(hat)) {
            return false;
        }
        lastWave = now;
        getLookControl().lookAt(player, 30.0f, 30.0f);
        greet();
        return true;
    }

    public long lastWave() {
        return lastWave;
    }

    /** Кто рядом в шапке народа: ближнему игроку — взмах. */
    private void noticeHats() {
        if (getWorld() instanceof ServerWorld world) {
            PlayerEntity near = world.getClosestPlayer(getX(), getY(), getZ(), HAT_SEEN,
                    player -> !player.isSpectator());
            if (near != null) {
                wave(near);
            }
        }
    }

    /**
     * Ликовать: кончилось состязание, кто-то победил.
     * <p>
     * Сервер шлёт это сам, как взмах руки: только он знает, что состязание
     * кончилось. Зрители, стоящие молча после победы, выглядели бы так,
     * будто победы не было.
     */
    public void cheer() {
        triggerAnim(ARMS, "cheer");
    }

    /**
     * Бросить кости: взмах рукой над столом.
     * <p>
     * Сервер шлёт это сам, как взмах приветствия: только он знает, что
     * бросок был, — а без движения фраза «Шесть!» висела бы над стоящим
     * столбом.
     */
    public void throwDice() {
        triggerAnim(ARMS, "throw");
    }

    public boolean isWrestling() {
        return dataTracker.get(WRESTLING);
    }

    public void setWrestling(boolean wrestling) {
        if (dataTracker.get(WRESTLING) != wrestling) {
            dataTracker.set(WRESTLING, wrestling);
        }
    }

    public boolean isDancing() {
        return dataTracker.get(DANCING);
    }

    public void setDancing(boolean dancing) {
        if (dataTracker.get(DANCING) != dancing) {
            dataTracker.set(DANCING, dancing);
        }
    }

    /**
     * Сказать вслух — над головой, на три секунды.
     * <p>
     * Чаще раза в две секунды житель не говорит: компания у стола, где
     * каждый отвечает каждому, иначе тараторила бы, и ни одну фразу
     * не успеть бы прочесть.
     *
     * @return встала ли фраза
     */
    public boolean say(Text line) {
        return say(line, false);
    }

    /**
     * Сказать — и, если {@code urgent}, поверх недавней фразы.
     * <p>
     * Итог партии ждут все за столом: «Перебор!» не должно пропасть
     * оттого, что полсекунды назад житель сказал «Шесть!».
     */
    public boolean say(Text line, boolean urgent) {
        long now = getWorld().getTime();
        if (!urgent && now - spokeAt < SPEECH_GAP) {
            return false;
        }
        spokeAt = now;
        speechUntil = now + SPEECH_TICKS;
        dataTracker.set(SPEECH, Optional.of(line));
        return true;
    }

    /** Что житель сейчас говорит. */
    public Optional<Text> speech() {
        return dataTracker.get(SPEECH);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(LOOK, Looks.UNKNOWN.toString());
        dataTracker.startTracking(CHILD, false);
        dataTracker.startTracking(DOZING, false);
        dataTracker.startTracking(DANCING, false);
        dataTracker.startTracking(WRESTLING, false);
        dataTracker.startTracking(STATURE, Culture.PLAIN_STATURE);
        dataTracker.startTracking(SPEECH, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();

        if (speechUntil != 0 && !getWorld().isClient() && getWorld().getTime() >= speechUntil) {
            speechUntil = 0;
            dataTracker.set(SPEECH, Optional.empty());
        }

        if (!getWorld().isClient() && age % OFF_FENCE_EVERY == 0) {
            stepOutOfTrouble();
        }
        if (!getWorld().isClient() && age % ROLE_EVERY == 0) {
            refreshRole();
            dropIfForgotten();
            noticeHats();
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

                    // Своя высота, на блок ниже и на блок выше: с забора
                    // сходят вниз — его верх и есть та лишняя половина
                    // блока, из-за которой житель там стоял, — а из рыхлого
                    // снега выходят вверх: утонувшему твёрдый край ямы
                    // приходится на блок выше ног.
                    for (int dy : SAFETY_HEIGHTS) {
                        BlockPos spot = getBlockPos().add(dx, dy, dz);
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
