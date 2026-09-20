package com.villagepax.sim.faith;

import com.villagepax.core.Named;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.faith.Domain;
import com.villagepax.core.faith.God;
import com.villagepax.core.faith.Gods;
import com.villagepax.sim.Building;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.work.FarmJob;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

/**
 * Вера: лестница благосклонности и то, что с неё капает даром.
 * <p>
 * Ступени — не украшение над числом. Число «сто двадцать» игроку ничего
 * не говорит; «Услышан» говорит всё, и именно ступенями открываются права:
 * благословение, чудо, артефакт. Ровно так же в моде устроено доверие
 * деревни, и по той же причине.
 * <p>
 * <b>Почему так медленно.</b> Решение заказчика от 2026-09-10: «чтобы
 * пантеон не выродился в чек-лист, благосклонность набирается медленно —
 * за прохождение реально успеть поднять одного-двух. Выбор остаётся,
 * но за счёт нехватки времени, а не наказания». Отсюда и лестница до
 * четырёхсот пятидесяти при девяти очках за лучшую жертву в день:
 * поднять одного бога до верха — это полсотни игровых дней, а поднять
 * всех шестерых за одно прохождение нельзя.
 */
public final class Faith {

    /**
     * Ступень благосклонности.
     * <p>
     * Порог — <b>нижняя</b> граница, как у доверия. Порядок объявления
     * снизу вверх, и на него опирается сравнение: {@code ordinal()} здесь
     * несущий, а не случайный.
     */
    public enum Tier implements Named {

        /** Бог о поселении не знает. */
        UNKNOWN("unknown", 0),

        /** Замечен: можно просить благословение. */
        NOTICED("noticed", 40),

        /** Услышан: можно просить чудо. */
        HEARD("heard", 120),

        /** Хранимый: бог вручает свой артефакт. */
        KEPT("kept", 250),

        /**
         * Избранник: благословение домена держится само, пока держится
         * благосклонность.
         * <p>
         * Это и есть причина <b>не тратить</b>. Благословение стоит очков,
         * и игрок, который зовёт его каждые три дня, не доберётся сюда
         * никогда; тот, кто копил, получает его насовсем. Выбор между
         * «помощь сегодня» и «помощь всегда» — то единственное, ради чего
         * у благосклонности вообще есть цена.
         */
        CHOSEN("chosen", 450);

        private final String id;
        private final int from;

        Tier(String id, int from) {
            this.id = id;
            this.from = from;
        }

        @Override
        public String id() {
            return id;
        }

        /** С какого числа очков начинается ступень. */
        public int from() {
            return from;
        }

        /** Ключ перевода названия ступени. */
        public String key() {
            return "villagepax.faith.tier." + id;
        }

        /** Достигнута ли эта ступень или выше. */
        public boolean reached(Tier other) {
            return ordinal() >= other.ordinal();
        }

        /** Следующая ступень, если она есть. */
        public Optional<Tier> next() {
            return ordinal() + 1 < values().length
                    ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
        }
    }

    /**
     * Цена благословения — ровно та ступень, с которой его разрешают.
     * <p>
     * Это не совпадение и не лень посчитать. Сперва цена была выше ступени
     * (пятьдесят против сорока), и модульная проверка тут же назвала беду:
     * право открывается, а воспользоваться им нельзя. Кнопка есть, ответ
     * всегда «не хватает» — ровно та же ложь, за которую мод уже платил
     * ступенью колонии, обещавшей ратушу, которой в нём нет.
     * <p>
     * Отсюда правило, простое настолько, что его не надо объяснять
     * в интерфейсе: <b>дошёл до ступени — можешь позвать один раз</b>.
     * И сразу же падаешь обратно, потому что заплатил всем, что набрал.
     * В этом и весь выбор: помощь сегодня или ступень выше завтра.
     */
    public static final int BLESSING_COST = Tier.NOTICED.from();

    /** На сколько дней ложится благословение. */
    public static final int BLESSING_DAYS = 3;

    /** Чудо — разовое и дорогое: втрое дороже благословения, и то же правило. */
    public static final int MIRACLE_COST = Tier.HEARD.from();

    /** Выше скольких очков настроение колонии больше не растёт. */
    public static final int MOST_SOLACE = 3;

    /**
     * Сколько грядок доращивает благословение урожая за утро.
     * <p>
     * Не всё поле: благословение — помощь, а не отмена фермера. Поле
     * норманнов — двадцать три грядки, и восемь за утро означают, что
     * за три дня благословения оно проходит поле примерно раз. Фермер
     * по-прежнему нужен: жать и сеять бог не станет.
     */
    public static final int BLESSED_GROWTH = 8;

    private Faith() {
    }

    /** На какой ступени поселение у этого бога. */
    public static Tier tierOf(Settlement settlement, Identifier god) {
        return tierOf(settlement.favourOf(god));
    }

    /** На какой ступени столько очков. */
    public static Tier tierOf(int favour) {
        Tier found = Tier.UNKNOWN;
        for (Tier tier : Tier.values()) {
            if (favour >= tier.from()) {
                found = tier;
            }
        }
        return found;
    }

    /**
     * Держится ли сейчас благословение этого домена.
     * <p>
     * Два способа, и оба честные: за него заплачено днями или бог считает
     * поселение своим. Спрашивать надо здесь, а не у поселения напрямую, —
     * иначе избранничество пришлось бы проверять в каждой врезке заново,
     * и первая же забытая сделала бы верхнюю ступень пустым титулом.
     */
    public static boolean blessed(Settlement settlement, Domain domain, long today) {
        if (settlement.isBlessed(domain.id(), today)) {
            return true;
        }
        return Gods.inDomain(settlement.culture(), domain)
                .map(god -> tierOf(settlement, god).reached(Tier.CHOSEN))
                .orElse(false);
    }

    /**
     * Насколько вера греет жителей.
     * <p>
     * Складывается с уютом дома в суточном подсчёте, и это не случайная
     * прибавка. Храм стоит дорого, а окупается долго: без этого правила
     * игрок строил бы его только ради алтаря, и первые двадцать дней
     * здание просто занимало бы место. Здесь же у него есть смысл с
     * первого дня — жителям есть куда пойти.
     * <p>
     * Считается по услышанным богам, а не по числу очков: «нас слышат»
     * — это ступень, а не арифметика.
     */
    public static int solace(ServerWorld world, Settlement settlement) {
        int found = hasTemple(settlement) ? 1 : 0;
        for (Identifier god : Gods.of(settlement.culture())) {
            if (tierOf(settlement, god).reached(Tier.HEARD)) {
                found++;
            }
        }
        return Math.min(MOST_SOLACE, found);
    }

    /**
     * Есть ли в поселении достроенный храм.
     * <p>
     * По <b>алтарю в схеме</b>, а не по имени типа: народ вправе назвать
     * своё святилище как угодно, и узнавать его мод обязан по тому, что
     * внутри. Это то же правило, по которому ратуша узнаётся по роли,
     * а не по окончанию пути.
     */
    public static boolean hasTemple(Settlement settlement) {
        return settlement.buildings().stream().anyMatch(Faith::isTemple);
    }

    /** Достроенное здание с алтарём. */
    public static boolean isTemple(Building building) {
        return building.isOperational() && Altars.has(BuildJob.schematicId(building));
    }

    /**
     * Утро под благословением урожая: посевы подрастают сами.
     * <p>
     * Зовётся из суточного переката вместе с голодом и приходом, и только
     * под присмотром: спрашивать блоки в выгруженном чанке моду нельзя —
     * это правило, заработанное на голоде, который считался везде, а
     * поесть житель мог только рядом с игроком.
     * <p>
     * Растит <b>по одной стадии</b>, а не до спелости: поле, поспевшее
     * за ночь целиком, отняло бы у фермера работу, а вместе с ней и вид
     * работы, ради которого в моде вообще есть жители.
     */
    public static int growCrops(ServerWorld world, Settlement colony, long today) {
        if (!blessed(colony, Domain.HARVEST, today)) {
            return 0;
        }

        int grown = 0;
        for (Building building : colony.buildings()) {
            if (!building.isOperational()) {
                continue;
            }
            for (BlockPos plot : FarmJob.plots(building)) {
                if (grown >= BLESSED_GROWTH) {
                    return grown;
                }
                if (!world.isChunkLoaded(plot)) {
                    continue;
                }
                BlockState state = world.getBlockState(plot);
                if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
                    world.setBlockState(plot, crop.withAge(crop.getAge(state) + 1));
                    grown++;
                }
            }
        }
        return grown;
    }

    /**
     * Пантеон народа этого поселения.
     * <p>
     * Отдельный метод, потому что спрашивают об этом из четырёх мест,
     * и везде — «кому здесь молятся», а не «какие боги бывают».
     */
    public static List<Identifier> pantheonOf(Settlement settlement) {
        return Gods.of(settlement.culture());
    }

    /** Бог по опознавателю, если он загружен. */
    public static Optional<God> god(Identifier id) {
        return Gods.get(id);
    }

    /**
     * Тип здания с алтарём у этого народа, если он объявлен.
     * <p>
     * Нужен совету в пульте: «построй храм» — бесполезная подсказка, если
     * мод не может назвать, какой именно.
     */
    public static Optional<Identifier> templeType(Identifier culture) {
        return BuildingTypes.all().keySet().stream()
                .filter(type -> type.getPath().startsWith(culture.getPath() + "/"))
                .filter(Altars::typeHasAltar)
                .findFirst();
    }
}
