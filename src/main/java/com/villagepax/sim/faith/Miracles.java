package com.villagepax.sim.faith;

import com.villagepax.core.faith.Domain;
import com.villagepax.core.faith.Gods;
import com.villagepax.core.war.WarParty;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Building;
import com.villagepax.sim.BuildProgress;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.Materials;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.war.Raids;
import com.villagepax.sim.work.Needs;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

import java.util.Optional;

/**
 * Чудо: разовый крупный эффект, который стоит втрое дороже благословения.
 * <p>
 * Благословение — это помощь, растянутая на дни; чудо — то, что игрок
 * зовёт в тот единственный вечер, когда всё плохо. Поэтому оно не
 * повторяется и не откладывается: поле сгорело, стена не готова, у ворот
 * стоят четверо. Отсюда же и отказ «просить нечего»: чудо, потраченное
 * впустую, — сто пятьдесят дней впустую, и мод обязан об этом сказать
 * <b>до</b> списания.
 */
public final class Miracles {

    /** Чем кончилась просьба о чуде. */
    public enum Verdict {

        DONE("done"),
        NO_GOD("no_god"),

        /** Бог ещё не услышал: чудеса просят с третьей ступени. */
        NOT_HEARD("not_heard"),

        /**
         * Просить нечего: дождь уже идёт, стройки нет, у ворот пусто.
         * <p>
         * Отказ до списания, а не после. Чудо стоит трёх благословений,
         * и «нажал, а ничего не случилось» тут не мелкая досада,
         * а полсотни игровых дней.
         */
        NOTHING_TO_DO("nothing_to_do"),

        NOT_YOURS("not_yours");

        private final String id;

        Verdict(String id) {
            this.id = id;
        }

        public String key() {
            return "villagepax.faith.miracle." + id;
        }

        public boolean isDone() {
            return this == DONE;
        }
    }

    /** Сколько блоков кладёт чудо камня за раз. */
    public static final int STONE_BLOCKS = 256;

    /** Сколько урона получает каждый налётчик от гнева. */
    public static final float WRATH_DAMAGE = 12.0f;

    /** На сколько тиков чудо урожая проливает дождь: игровые сутки. */
    public static final int RAIN_TICKS = 24_000;

    private Miracles() {
    }

    /**
     * Есть ли о чём просить — и по карману ли.
     * <p>
     * Мир здесь спрашивается (идёт ли дождь, есть ли стройка, стоит ли
     * отряд), и потому функция не чистая. Но решение всё равно отделено
     * от действия: она ничего не меняет, и проверка может спросить её
     * дважды подряд.
     */
    public static Verdict judge(ServerWorld world, Settlement colony, Domain domain, long today) {
        Identifier god = Gods.inDomain(colony.culture(), domain).orElse(null);
        if (god == null) {
            return Verdict.NO_GOD;
        }
        // Цена чуда равна порогу ступени «Услышаны», поэтому отдельного
        // «не хватает» здесь нет: дошёл — хватает. См. Faith.MIRACLE_COST.
        if (!Faith.tierOf(colony, god).reached(Faith.Tier.HEARD)) {
            return Verdict.NOT_HEARD;
        }

        return switch (domain) {
            case HARVEST -> world.isRaining() && !anyCropGrowing(world, colony)
                    ? Verdict.NOTHING_TO_DO : Verdict.DONE;
            case STONE -> underConstruction(colony).isPresent()
                    ? Verdict.DONE : Verdict.NOTHING_TO_DO;
            case WATCH -> colony.siege().isPresent() ? Verdict.DONE : Verdict.NOTHING_TO_DO;
        };
    }

    /**
     * Позвать чудо.
     *
     * @return вердикт; при {@link Verdict#DONE} очки уже списаны
     */
    public static Verdict call(ServerWorld world, SettlementManager manager, Settlement colony,
                               Domain domain, long today) {
        Verdict verdict = judge(world, colony, domain, today);
        if (!verdict.isDone()) {
            return verdict;
        }

        Identifier god = Gods.inDomain(colony.culture(), domain).orElseThrow();
        manager.update(colony.id(), state -> state.addFavour(god, -Faith.MIRACLE_COST));

        switch (domain) {
            case HARVEST -> rain(world, manager, colony);
            case STONE -> raise(world, manager, colony);
            case WATCH -> wrath(world, colony);
        }

        world.playSound(null, colony.center(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.WEATHER, 1.0f, 1.0f);
        return Verdict.DONE;
    }

    /**
     * Дождь: небо кормит.
     * <p>
     * Три вещи разом, и это одна мысль, а не три: пошёл дождь, посевы
     * поднялись, людям вернулась сытость. Чудо урожая просят в голод,
     * а голод в этом моде — это пустой склад и жители, собравшиеся уходить;
     * один только дождь их бы не удержал.
     */
    private static void rain(ServerWorld world, SettlementManager manager, Settlement colony) {
        if (world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
            world.setWeather(0, RAIN_TICKS, true, false);
        }

        for (Building building : colony.buildings()) {
            if (!building.isOperational()) {
                continue;
            }
            for (BlockPos plot : com.villagepax.sim.work.FarmJob.plots(building)) {
                if (!world.isChunkLoaded(plot)) {
                    continue;
                }
                BlockState state = world.getBlockState(plot);
                if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
                    world.setBlockState(plot, crop.withAge(crop.getMaxAge()));
                }
            }
        }

        manager.update(colony.id(), state -> {
            for (Citizen citizen : state.citizens()) {
                citizen.setSaturation(Needs.MAX_SATURATION);
            }
        });
    }

    /**
     * Твердыня: стройка встаёт разом.
     * <p>
     * Бог посылает <b>материал</b>, а кладёт его по-прежнему билдер — просто
     * за один вздох. Это не полумера: у мода уже есть проверенный путь
     * «выдать сырьё в саму стройку», по которому колонии дарят первый дом,
     * и второй, обходящий билдера с материалами разом, означал бы две
     * разные стройки в одном моде. Заодно чудо остаётся честным: камень
     * прилетает с неба, стена растёт руками.
     * <p>
     * Кладётся не всё бесконечно, а двести пятьдесят шесть блоков: ратуша
     * четвёртого уровня больше, и достраивать её целиком одним нажатием
     * было бы уже не чудом, а кнопкой «победить».
     */
    private static void raise(ServerWorld world, SettlementManager manager, Settlement colony) {
        Building site = underConstruction(colony).orElse(null);
        if (site == null) {
            return;
        }
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(site)).orElse(null);
        if (schematic == null) {
            return;
        }

        // Ровно то, чего не хватает впереди, а не вся смета: излишек лёг бы
        // в запас стройки мёртвым грузом и вернулся бы игроку только сносом.
        Materials.shortfall(schematic, site, STONE_BLOCKS).forEach((item, count) ->
                site.stock().add(Registries.ITEM.getId(item), count));

        BuildJob.advance(world, manager, colony.id(), site.id(), STONE_BLOCKS);
    }

    /**
     * Гнев: по тем, кто стоит у ворот.
     * <p>
     * Бьёт <b>только отряд</b>, а не всё живое вокруг. Молния по площади
     * спалила бы и своих жителей, и дом, у которого они стоят, — а чудо,
     * от которого надо убегать, игрок позовёт один раз и больше никогда.
     */
    private static void wrath(ServerWorld world, Settlement colony) {
        WarParty party = colony.siege().orElse(null);
        if (party == null) {
            return;
        }

        DamageSource source = world.getDamageSources().magic();
        for (CitizenEntity raider : Raids.bodiesOf(world, party)) {
            // Молния косметическая: настоящая жжёт траву, плавит песок
            // и зажигает дом, у которого стоит налётчик. Урон наносится
            // отдельно и ровно тому, в кого целились, — иначе чудо
            // защиты сожгло бы то, что защищает.
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(raider.getX(), raider.getY(), raider.getZ());
                bolt.setCosmetic(true);
                world.spawnEntity(bolt);
            }
            raider.damage(source, WRATH_DAMAGE);
        }
    }

    /** Стройка колонии, которая идёт прямо сейчас. */
    public static Optional<Building> underConstruction(Settlement colony) {
        return colony.buildings().stream()
                .filter(building -> building.progress() == BuildProgress.BUILDING
                        || building.progress() == BuildProgress.PLANNED)
                .findFirst();
    }

    /** Есть ли на полях колонии хоть один недоросший посев. */
    private static boolean anyCropGrowing(ServerWorld world, Settlement colony) {
        for (Building building : colony.buildings()) {
            if (!building.isOperational()) {
                continue;
            }
            for (BlockPos plot : com.villagepax.sim.work.FarmJob.plots(building)) {
                if (!world.isChunkLoaded(plot)) {
                    continue;
                }
                BlockState state = world.getBlockState(plot);
                if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
                    return true;
                }
            }
        }
        return false;
    }
}
