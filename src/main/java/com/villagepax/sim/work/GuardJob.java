package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

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
 */
public class GuardJob implements Job {

    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "guard");

    /** Насколько далеко страж чует налётчиков: полный клейм малой колонии. */
    private static final int WATCH = 48;

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        context.hold(new ItemStack(Items.IRON_SWORD));

        // Страж всегда «в простое», и это не небрежность: фаза работы
        // означает привязку к зданию, а у стражи здания нет — есть
        // колония целиком. Заодно так возвращается на склад груз того,
        // кто был курьером до того, как ему дали меч.
        if (context.state().phase() != JobState.Phase.IDLE) {
            context.goIdle();
        }

        CitizenEntity enemy = nearestRaider(context);
        if (enemy != null) {
            // Цель — телу, а не работе: драться умеет тактика, и умеет
            // хорошо. Стратегии остаётся сказать, на кого идти, и вести
            // туда же ноги, чтобы страж не терял врага из вида.
            context.body().setTarget(enemy);
            return Optional.of(enemy.getBlockPos());
        }

        // Врага нет — и цель надо снять руками: иначе страж будет гнаться
        // за тем, кого уже нет, до конца боевой цели.
        if (context.body().getTarget() instanceof CitizenEntity gone && gone.isRaider()) {
            context.body().setTarget(null);
        }
        return farthestCorner(context);
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
