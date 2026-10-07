package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import com.villagepax.sim.Villages;
import com.villagepax.sim.Settlement;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Стража: ходит по деревне, а на набег идёт с мечом.
 * <p>
 * Вторая половина обещания фазы 3 — «стража и укрепления». Укреплений
 * пока нет, а страж есть, и есть от кого: отряд, посланный обиженной
 * деревней, приходит убивать людей, и без стражи защищать колонию было бы
 * некому, кроме самого игрока. Игрок при этом спать тоже хочет.
 * <p>
 * <b>Работа стражи должна быть видна.</b> Это правило дизайн-документа,
 * и здесь оно решает всё: страж, стоящий у ратуши, ничем не отличается
 * от безработного. Поэтому мирный обход — не «стоять на посту», а ходьба
 * <b>к самой дальней постройке колонии</b>: дойдя, страж оказывается
 * дальше всего от другого края, и следующим решением идёт обратно.
 * Патруль получается сам, без памяти о том, куда он шёл, — а память
 * о маршруте пришлось бы хранить в данных и чинить после перезахода.
 * <p>
 * <b>Меч выдаётся даром</b>, как и топор лесорубу: в этом моде снаряжение
 * работника — вид его дела, а не расход. Так было с первого ремесла, и
 * менять правило ради одного из них значило бы завести два правила.
 * Настоящая цена стражи в другом: это житель, который не пашет и не
 * строит, но ест, — и в колонии на шесть человек лишний рот заметен.
 * <p>
 * Стража бывает трёх выучек ({@link GuardKind}): мечник идёт на врага,
 * лучник бьёт издали (сам выстрел — у тела, {@code CitizenBowGoal}),
 * лекарь не дерётся, а перевязывает раненых.
 */
public class GuardJob implements Job {

    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "guard");

    /** Насколько далеко страж чует налётчиков: полный клейм малой колонии. */
    private static final int WATCH = 48;

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    /** Сколько здоровья возвращает одна перевязка лекаря. */
    public static final float CARE = 4.0f;

    /** Пауза между перевязками, в тиках. */
    public static final int CARE_EVERY = 40;

    /** С какого расстояния лекарь перевязывает: надо подойти вплотную. */
    private static final double CARE_REACH = 3.0;

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        GuardKind kind = context.manager().guardKindOf(context.settlement(), context.citizen().id());
        context.hold(toolOf(kind, context.settlement().culture()));

        // Страж всегда «в простое», и это не небрежность: фаза работы
        // означает привязку к зданию, а у стражи здания нет — есть
        // колония целиком. Заодно так возвращается на склад груз того,
        // кто был курьером до того, как ему дали меч.
        if (context.state().phase() != JobState.Phase.IDLE) {
            context.goIdle();
        }

        if (kind == GuardKind.MEDIC) {
            return care(context);
        }

        CitizenEntity enemy = nearestRaider(context);
        if (enemy != null) {
            // Цель — телу, а не работе: драться умеет тактика, и умеет
            // хорошо. Стратегии остаётся сказать, на кого идти, и вести
            // туда же ноги, чтобы страж не терял врага из вида.
            context.body().setTarget(enemy);
            return Optional.of(enemy.getBlockPos());
        }

        // Налётчиков нет — есть нечисть: зомби у околицы, скелет на крыше.
        // Прежде стража видела только набег, а мертвецы ходили по деревне,
        // как у себя дома.
        HostileEntity monster = nearestMonster(context);
        if (monster != null) {
            context.body().setTarget(monster);
            return Optional.of(monster.getBlockPos());
        }

        // Врага нет — и цель надо снять руками: иначе страж будет гнаться
        // за тем, кого уже нет, до конца боевой цели.
        if (context.body().getTarget() instanceof CitizenEntity gone && gone.isRaider()
                || context.body().getTarget() instanceof HostileEntity) {
            context.body().setTarget(null);
        }
        BlockPos post = towerPost(context);
        return post != null ? Optional.of(post) : farthestCorner(context);
    }

    /** Что у стражника в руке: оружие народа, лук или целебное зелье. */
    public static ItemStack toolOf(GuardKind kind, Identifier culture) {
        return switch (kind) {
            case SWORD -> com.villagepax.item.gear.ModGear.armsFor(culture);
            case BOW -> new ItemStack(Items.BOW);
            case MEDIC -> PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
        };
    }

    /**
     * Чему учит вещь, данная стражнику в руки: меч или топор — мечник,
     * лук или арбалет — лучник, целебное — лекарь.
     */
    public static Optional<GuardKind> taughtBy(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem) {
            return Optional.of(GuardKind.BOW);
        }
        if (item instanceof SwordItem || item instanceof AxeItem
                || com.villagepax.item.gear.GearWeapons.gearOf(stack).isPresent()) {
            return Optional.of(GuardKind.SWORD);
        }
        if (item == Items.GOLDEN_APPLE || item == Items.GLISTERING_MELON_SLICE
                || item == Items.POTION || item == Items.SPLASH_POTION) {
            return Optional.of(GuardKind.MEDIC);
        }
        return Optional.empty();
    }

    /**
     * Дело лекаря: найти раненого, подойти и перевязать.
     * <p>
     * Раненые — свои жители и тот, за кого поселение держится: хозяин
     * колонии и союзники деревни. Без раненых лекарь стоит у ратуши —
     * там его и ищут, — а не ходит в обход: драться он не станет.
     */
    private static Optional<BlockPos> care(WorkContext context) {
        if (context.body().getTarget() != null) {
            context.body().setTarget(null);
        }
        LivingEntity patient = nearestWounded(context);
        if (patient == null) {
            return Optional.of(context.settlement().center());
        }
        if (patient.squaredDistanceTo(context.body()) <= CARE_REACH * CARE_REACH) {
            context.body().tend(patient);
        }
        return Optional.of(patient.getBlockPos());
    }

    /** Ближайший раненый, о ком лекарь заботится. */
    public static LivingEntity nearestWounded(WorkContext context) {
        Settlement settlement = context.settlement();
        Box around = context.body().getBoundingBox().expand(WATCH, 12, WATCH);
        LivingEntity best = null;
        double bestAway = Double.MAX_VALUE;
        List<LivingEntity> wounded = new ArrayList<>();
        wounded.addAll(context.world().getEntitiesByClass(CitizenEntity.class, around,
                one -> one.isAlive() && !one.isRaider() && one.caravanId() == null
                        && one.settlementId().filter(settlement.id()::equals).isPresent()
                        && one.getHealth() < one.getMaxHealth() - 1.0f));
        wounded.addAll(context.world().getEntitiesByClass(PlayerEntity.class, around,
                one -> one.isAlive() && !one.isSpectator() && one.getHealth() < one.getMaxHealth() - 1.0f
                        && (settlement.owner().isOwnedBy(one.getUuid()) || settlement.isAllyOf(one.getUuid()))));
        for (LivingEntity candidate : wounded) {
            double away = candidate.squaredDistanceTo(context.body());
            if (away < bestAway) {
                bestAway = away;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Пост на башне, если она в колонии есть.
     * <p>
     * До башни мирный обход стража был ходьбой к дальнему зданию — лучше,
     * чем стоять у ратуши, но всё же выдумка: страж ходил кругами
     * по чужим огородам. Башню для того и строят, чтобы с неё смотреть,
     * и пустая башня рядом с ходящим по улице стражем выглядела бы
     * насмешкой над обоими.
     * <p>
     * Пустое значение значит «башни нет» — и тогда прежний обход.
     */
    private static BlockPos towerPost(WorkContext context) {
        for (Building building : context.settlement().buildings()) {
            if (!building.isOperational()
                    || !BuildingTypes.employs(building.type(), Villages.GUARD)) {
                continue;
            }
            List<BlockPos> posts = Workplaces.stations(building);
            if (!posts.isEmpty()) {
                return posts.get(0);
            }
        }
        return null;
    }

    /**
     * Ближайший налётчик в пределах слышимости.
     * <p>
     * Ищется <b>от стража</b>, а не от середины колонии: страж идёт
     * на того, кто рядом с ним, и в этом весь смысл двух стражей —
     * они разойдутся по своим краям, а не сойдутся на одном враге.
     */
    private static CitizenEntity nearestRaider(WorkContext context) {
        Box around = context.body().getBoundingBox().expand(WATCH);
        CitizenEntity best = null;
        double bestAway = Double.MAX_VALUE;

        // Живого, а не всякого: тело убитого налётчика исчезает из мира
        // не в тот же миг — смерть моба идёт через анимацию, — и страж,
        // не спросивший о жизни, полсекунды гонялся бы за покойником.
        for (CitizenEntity candidate : context.world().getEntitiesByClass(CitizenEntity.class,
                around, alive -> alive.isRaider() && alive.isAlive())) {
            double away = candidate.squaredDistanceTo(context.body());
            if (away < bestAway) {
                bestAway = away;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Ближайшая нечисть на земле поселения.
     * <p>
     * Только в его границах: страж не уходит охотиться в лес. И не крипер:
     * мечом его не взять, не дав взорваться, — от крипера бегут все.
     */
    public static HostileEntity nearestMonster(WorkContext context) {
        Box around = context.body().getBoundingBox().expand(WATCH, 12, WATCH);
        HostileEntity best = null;
        double bestAway = Double.MAX_VALUE;
        for (HostileEntity candidate : context.world().getEntitiesByClass(HostileEntity.class, around,
                monster -> CitizenEntity.isThreat(monster) && !(monster instanceof CreeperEntity)
                        && context.settlement().claims(monster.getBlockPos()))) {
            double away = candidate.squaredDistanceTo(context.body());
            if (away < bestAway) {
                bestAway = away;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Самая дальняя постройка колонии — она же следующий конец обхода.
     * <p>
     * Пусто у колонии, где ещё ничего не стоит: там страж просто бродит,
     * как и всякий житель без дела, и это честнее, чем гонять его к
     * пустому месту.
     */
    private static Optional<BlockPos> farthestCorner(WorkContext context) {
        BlockPos from = context.body().getBlockPos();
        BlockPos best = null;
        double bestAway = -1;

        for (Building building : context.settlement().buildings()) {
            double away = building.anchor().getSquaredDistance(from);
            if (away > bestAway) {
                bestAway = away;
                best = building.anchor();
            }
        }
        // Совсем рядом стоящее здание целью не считается: страж, дошедший
        // до цели, иначе выбирал бы её же снова и топтался на месте.
        return bestAway < WorkContext.ARRIVAL_REACH * WorkContext.ARRIVAL_REACH
                ? Optional.empty() : Optional.ofNullable(best);
    }

}
