package com.villagepax.sim.work;

import com.villagepax.core.config.Configs;
import com.villagepax.sim.Building;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Optional;

/**
 * Переноска материалов: общая часть курьера и билдера.
 * <p>
 * Вынесена сюда потому, что носить умеют двое, и по разным причинам.
 * Курьер носит <b>потому что это его дело</b>. Билдер носит <b>когда носить
 * больше некому</b> — и это ответ на вопрос игрока, с которого всё началось:
 * как поднять колонию с нуля, если курьера ещё нет, а курьеру нужен дом,
 * а дому нужны материалы у стройки. Замкнутый круг разрывается тем, что
 * первый житель делает всё сам, только медленно.
 * <p>
 * <b>Носят несколько слотов.</b> Слот — это вид груза, а не стопка: за одну
 * ходку житель берёт со склада и брёвна, и стекло, и кровать. Раньше слот
 * был один, и дом из семнадцати видов блоков требовал семнадцати ходок
 * через полдеревни — работа ради работы, которую игрок и видел.
 */
public final class Hauling {

    /** Сколько одного вида груза житель берёт за ходку. */
    public static final int PER_SLOT = 32;

    /**
     * На сколько шагов плана вперёд смотрит заявка.
     * <p>
     * Считать нужду до конца схемы — это обход трёхсот шагов на каждое
     * решение. Окна хватает: носильщик принесёт то, что понадобится скоро,
     * и вернётся снова.
     */
    public static final int LOOKAHEAD = 64;

    private Hauling() {
    }

    /** Сколько видов груза житель унесёт за раз. Из настроек. */
    public static int slots() {
        return Configs.get().carrySlots();
    }

    /** Что и сколько нести. */
    public record Request(Item item, int count) {
    }

    /** Сколько этого вида уже в руках. */
    private static int carriedCount(WorkContext context, Item item) {
        for (JobState.Load load : context.state().carried()) {
            if (load.item().equals(Registries.ITEM.getId(item))) {
                return load.count();
            }
        }
        return 0;
    }

    /**
     * Сколько решений билдер терпит нехватку материалов, прежде чем пойти
     * за ними сам.
     * <p>
     * Двадцать решений — это около десяти секунд. Столько курьеру хватает
     * на ходку по деревне, и столько игрок готов смотреть на стоящую
     * стройку, не считая её поломкой.
     */
    public static final int PATIENCE = 20;

    /**
     * Идти ли билдеру за материалами самому.
     * <p>
     * Решение заказчика: <b>помогать, если курьер не справляется</b>. Значит
     * два случая. Первый — носильщика в поселении нет вовсе: тогда идти
     * сразу, иначе колонию с нуля не поднять. Второй — носильщик есть, но
     * материалы не появляются уже {@link #PATIENCE} решений: он спит,
     * застрял, не дошёл или не успевает, и стройка стоять из-за этого
     * не должна.
     * <p>
     * Терпение важно оставить: без него билдер подменял бы курьера всегда,
     * и решение «стройка под боком у склада идёт сама, вынесенная за околицу
     * требует людей» перестало бы что-либо значить.
     */
    public static boolean shouldFetchItself(WorkContext context) {
        if (nobodyElseWillCarry(context.settlement())) {
            return true;
        }
        return context.body().noteMaterialWait() >= PATIENCE;
    }

    /**
     * Есть ли в поселении хоть один носильщик.
     */
    public static boolean nobodyElseWillCarry(Settlement settlement) {
        for (Citizen citizen : settlement.citizens()) {
            if (Jobs.forProfession(citizen.profession())
                    .filter(job -> job.logic().equals(HaulJob.LOGIC)).isPresent()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Взять со склада всё, что поместится в слоты.
     * <p>
     * Возвращает ложь, если брать нечего или носильщик ещё не дошёл до
     * сундука. Берётся не больше, чем площадке нужно: остатки вернутся при
     * сдаче здания, но лишняя ходка туда-обратно — работа ради работы.
     */
    public static boolean fillUp(WorkContext context, Building site) {
        Warehouse warehouse = context.warehouse();
        Request first = wanted(context, site).orElse(null);
        if (first == null) {
            return false;
        }

        Warehouse.Container source = warehouse
                .nearestWith(context.body().getBlockPos(), first.item(), 1)
                .orElse(null);
        if (source == null || !context.hasArrivedAt(source.pos())) {
            return false;
        }

        int taken = 0;
        for (Map.Entry<Item, Integer> want : shortfall(context, site).entrySet()) {
            if (context.state().usedSlots() >= slots()) {
                break;
            }

            // Из заявки вычитается то, что носильщик уже несёт. Иначе он
            // добирал бы то же самое каждый ход и приносил на площадку
            // в разы больше нужного.
            int already = carriedCount(context, want.getKey());
            int need = want.getValue() - already;
            if (need <= 0) {
                continue;
            }

            int got = Warehouse.takeFrom(source, want.getKey(), Math.min(PER_SLOT, need));
            if (got > 0) {
                context.setState(context.state()
                        .carrying(Registries.ITEM.getId(want.getKey()), got));
                taken += got;
            }
        }
        return taken > 0;
    }

    /**
     * Сложить принесённое на площадку.
     * <p>
     * Возвращает ложь, если носильщик ещё не дошёл. Складывается всё сразу:
     * материалы на площадке — счётчик, а не сундук, и раскладывать их
     * по одному незачем.
     */
    public static boolean unload(WorkContext context, Building site) {
        if (!context.state().isCarrying()) {
            return false;
        }
        if (!context.hasArrivedAt(site.anchor())) {
            return false;
        }

        for (JobState.Load load : context.state().carried()) {
            site.stock().add(load.item(), load.count());
        }
        context.setState(context.state().emptyHanded());
        return true;
    }

    /**
     * Вернуть на склад груз, который нести уже некуда.
     * <p>
     * Иначе материалы навсегда остаются в руках жителя, и игрок не поймёт,
     * куда девались тридцать брёвен.
     */
    public static void returnLoad(WorkContext context) {
        Warehouse warehouse = context.warehouse();
        BlockPos where = context.body().getBlockPos();

        Warehouse.Container target = warehouse.nearest(where).orElse(null);
        if (target != null && !context.hasArrivedAt(target.pos())) {
            return;
        }

        for (JobState.Load load : context.state().carried()) {
            Item item = Registries.ITEM.get(load.item());
            int left = load.count();
            while (left > 0) {
                int chunk = Math.min(left, item.getMaxCount());
                // Хранилищ нет вовсе — груз ляжет под ноги, а не пропадёт.
                warehouse.addOrScatter(context.world(), where, new ItemStack(item, chunk));
                left -= chunk;
            }
        }
        context.setState(JobState.IDLE);
    }

    /** Куда идти за грузом: к ближайшему сундуку с нужным. */
    public static Optional<BlockPos> whereToFetch(WorkContext context, Building site) {
        return wanted(context, site)
                .flatMap(request -> context.warehouse()
                        .nearestWith(context.body().getBlockPos(), request.item(), 1))
                .map(Warehouse.Container::pos);
    }

    /** Что житель держит на виду: первый из грузов. */
    public static void showLoad(WorkContext context) {
        context.hold(context.state().firstLoad()
                .map(load -> new ItemStack(Registries.ITEM.get(load.item()), load.count()))
                .orElse(ItemStack.EMPTY));
    }

    /** Что нести: первое из нужного площадке, чего на складе действительно есть. */
    public static Optional<Request> wanted(WorkContext context, Building site) {
        for (Map.Entry<Item, Integer> entry : shortfall(context, site).entrySet()) {
            if (context.warehouse().has(entry.getKey(), 1)) {
                return Optional.of(new Request(entry.getKey(), entry.getValue()));
            }
        }
        return Optional.empty();
    }

    /**
     * Чего площадке не хватает.
     * <p>
     * Порядок обхода — порядок плана, поэтому выбор устойчив: иначе
     * носильщик метался бы между двумя видами блоков от решения к решению.
     */
    private static Map<Item, Integer> shortfall(WorkContext context, Building site) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(site)).orElse(null);
        return schematic == null
                ? Map.of()
                : Materials.shortfall(schematic, site, LOOKAHEAD);
    }
}
