package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.sim.Building;
import com.villagepax.sim.Sounds;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Фермер: сеет, жнёт и сдаёт урожай на склад.
 * <p>
 * Решение заказчика — работает только на <b>построенной ферме</b>. Грядки,
 * вода и ограда приходят из схемы, а не вскапываются жителем где попало:
 * колония выглядит опрятно, а ферма становится нужным зданием.
 * <p>
 * Что сеять, тоже сказано схемой: посаженная в ней культура и есть указание.
 * То же решение, что с рощей лесоруба и кроватями в домах — план схемы уже
 * посчитан, и грядки берутся из него даром, без нового состояния в данных.
 * <p>
 * Норманнское поле растит <b>морковь</b>, а не пшеницу. Причина не в культуре,
 * а в том, что житель ест морковь прямо с грядки: пшенице нужны мельница
 * и пекарь, которых в моде ещё нет, и поле пшеницы кормило бы только склад.
 * Когда цепочки ремёсел появятся, поле пшеницы станет просто второй схемой —
 * культура читается из схемы, и кода это не потребует.
 * <p>
 * С этой профессии колония начинает кормить себя сама: до неё еду в ратушу
 * носил игрок, и голод из задачи 1.8 упирался в него.
 */
public final class FarmJob implements Job {

    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "farm");
    public static final Identifier FARMER = new Identifier(VillagePax.MOD_ID, "farmer");

    /** Докуда фермер дотягивается с того места, где стоит. */
    private static final double REACH = 4.5;

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        Building farm = Workplaces.of(context.settlement(), context.citizen()).orElse(null);
        if (farm == null) {
            // Без построенной фермы фермеру негде работать — по замыслу.
            context.goIdle();
            return Optional.empty();
        }

        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(farm)).orElse(null);
        if (schematic == null) {
            context.goIdle();
            return Optional.empty();
        }

        BlockPos plot = plotNeedingWork(context, farm, schematic).orElse(null);
        if (plot == null) {
            // Всё посеяно и ничего не поспело: подождём до следующего решения.
            context.goIdle();
            return Optional.empty();
        }

        if (context.state().isIdle()) {
            context.setState(JobState.startAt(farm.id(), JobState.Phase.TO_SITE));
        }

        // Мотыга в руке: по ней и видно, что житель идёт работать в поле.
        context.hold(hoe());

        if (context.position().squaredDistanceTo(Vec3d.ofCenter(plot)) <= REACH * REACH) {
            context.swing();
            work(context, schematic, plot);
        }
        return Optional.of(plot);
    }

    /** Грядки фермы — позиции, где в схеме посеяно. */
    public static List<BlockPos> plots(Building farm) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(farm)).orElse(null);
        return schematic == null ? List.of() : plots(farm, schematic);
    }

    private static List<BlockPos> plots(Building farm, Schematic schematic) {
        List<BlockPos> plots = new ArrayList<>();

        for (BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock()) {
                continue;
            }
            if (schematic.blockAt(step.paletteIndex()).isIn(BlockTags.CROPS)) {
                plots.add(BuildJob.worldPos(farm, schematic.size(), step.pos()));
            }
        }
        return plots;
    }

    /**
     * Грядка, требующая руки: поспевшая — жать, вытоптанная — вскопать,
     * пустая — сеять.
     * <p>
     * Растущие пропускаются: торопить рост фермер не умеет, а стоять над
     * зелёным ростком ему незачем.
     */
    private static Optional<BlockPos> plotNeedingWork(WorkContext context, Building farm,
                                                      Schematic schematic) {
        ServerWorld world = context.world();
        BlockState crop = cropOf(schematic).orElse(null);
        BlockPos sowable = null;

        for (BlockPos plot : plots(farm, schematic)) {
            if (context.body().isUnreachable(plot)) {
                // От этой грядки фермер уже отступился: не дойти. Через
                // полминуты попробует снова — мир мог измениться.
                continue;
            }

            BlockState state = world.getBlockState(plot);
            if (isRipe(state) || isTrampled(world, plot, state)) {
                return Optional.of(plot);
            }
            // Пустая грядка — только та, которую и правда можно засеять:
            // есть семя и всходам там жить. Прежде фермер вставал над первой
            // пустой и стоял вечно — семян нет или грядка в тени, — а всё
            // поле за ней поспевало и гнило несжатым. И сеять — после жатвы:
            // жатва и приносит семена.
            if (sowable == null && crop != null && isBareBed(world, plot, state)
                    && crop.canPlaceAt(world, plot)) {
                Item seed = seedOf(world, plot, crop);
                if (seed != Items.AIR && context.warehouse().has(seed, 1)) {
                    sowable = plot;
                }
            }
        }
        return Optional.ofNullable(sowable);
    }

    private static boolean isRipe(BlockState state) {
        return state.getBlock() instanceof CropBlock crop && crop.isMature(state);
    }

    private static boolean isBareBed(ServerWorld world, BlockPos plot, BlockState state) {
        return state.isAir() && world.getBlockState(plot.down()).isOf(Blocks.FARMLAND);
    }

    /**
     * Грядка, с которой сбили землю: под ней снова дёрн.
     * <p>
     * Это не редкость, а обычный ход дела: любой прыгнувший на поле —
     * житель, корова, сам игрок — превращает грядку в землю, и посев с неё
     * слетает. Без починки поле год за годом вытаптывается в пустырь,
     * а игрок видит ферму, которая перестала работать без причины.
     * <p>
     * Чужое на грядке фермер не трогает: если игрок поставил там сундук,
     * землю под ним никто копать не станет.
     */
    private static boolean isTrampled(ServerWorld world, BlockPos plot, BlockState state) {
        if (!state.isAir() && !state.isIn(BlockTags.CROPS)) {
            return false;
        }
        BlockState soil = world.getBlockState(plot.down());
        return soil.isIn(BlockTags.DIRT) && !soil.isOf(Blocks.FARMLAND);
    }

    private static void work(WorkContext context, Schematic schematic, BlockPos plot) {
        ServerWorld world = context.world();
        BlockState state = world.getBlockState(plot);

        if (isRipe(state)) {
            harvest(world, context.warehouse(), plot, state,
                    com.villagepax.sim.life.Happenings.harvestTimes(context.settlement(),
                            Schedule.dayOf(world.getTimeOfDay())));
            return;
        }
        if (isTrampled(world, plot, state)) {
            till(world, plot);
            return;
        }
        if (isBareBed(world, plot, state)) {
            sow(world, context.warehouse(), schematic, plot);
        }
    }

    /** Снять спелое; {@code times} — во сколько раз щедрее обычного (щедрое поле). */
    private static void harvest(ServerWorld world, Warehouse warehouse, BlockPos plot,
                                BlockState ripe, int times) {
        for (int i = 0; i < times; i++) {
            for (ItemStack drop : Block.getDroppedStacks(ripe, world, plot, null, null, hoe())) {
                if (!drop.isEmpty()) {
                    warehouse.addOrScatter(world, plot, drop);
                }
            }
        }
        if (times > 1) {
            world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                    plot.getX() + 0.5, plot.getY() + 0.6, plot.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.0);
        }
        Sounds.broke(world, plot, ripe);
        world.setBlockState(plot, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    /**
     * Вскопать грядку заново — тем же, чем это делает игрок мотыгой.
     * <p>
     * Влажность не выставляется: её нагонит вода из колодца, который стоит
     * в середине поля по схеме. Ставить «сразу мокрую» землю значило бы
     * делать колодец украшением.
     */
    private static void till(ServerWorld world, BlockPos plot) {
        world.setBlockState(plot.down(), Blocks.FARMLAND.getDefaultState(), Block.NOTIFY_ALL);
        Sounds.tilled(world, plot.down());
    }

    /**
     * Посеять то, что растёт на этой ферме по схеме.
     * <p>
     * Семена берутся со склада, куда сам же фермер сдал урожай: круг
     * замкнут, и подарков колонии никто не делает. Первый посев приходит
     * вместе с постройкой — схема ставит поле уже засеянным.
     */
    private static void sow(ServerWorld world, Warehouse warehouse, Schematic schematic,
                            BlockPos plot) {
        BlockState crop = cropOf(schematic).orElse(null);
        if (crop == null) {
            return;
        }

        // Семя не тратится там, где всходам не жить: в тени под навесом
        // ванильная грядка сбрасывает посев первым же обновлением, и склад
        // пустел бы молча.
        if (!crop.canPlaceAt(world, plot)) {
            return;
        }

        Item seed = seedOf(world, plot, crop);
        if (seed == Items.AIR || !warehouse.take(seed, 1)) {
            return;
        }
        world.setBlockState(plot, crop, Block.NOTIFY_ALL);
        Sounds.sown(world, plot);
    }

    /** Какая культура растёт на этой ферме — из схемы, а не из догадки. */
    public static Optional<BlockState> cropOf(Schematic schematic) {
        for (BlockState state : schematic.palette()) {
            if (state.isIn(BlockTags.CROPS)) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }

    /**
     * Чем сеётся эта ферма. Он же и есть её урожай, если культура съедобна:
     * морковь и сажают, и едят.
     * <p>
     * Позиция всходам безразлична, но ванильная подпись её требует.
     */
    public static Optional<Item> seedOf(ServerWorld world, Schematic schematic) {
        return cropOf(schematic)
                .map(crop -> seedOf(world, BlockPos.ORIGIN, crop))
                .filter(item -> item != Items.AIR);
    }

    /**
     * Чем сеять. У всходов нет своего предмета — у пшеницы это семена, —
     * поэтому спрашиваем блок тем же способом, каким игрок узнаёт предмет
     * средней кнопкой мыши.
     */
    private static Item seedOf(ServerWorld world, BlockPos plot, BlockState crop) {
        return crop.getBlock().getPickStack(world, plot, crop).getItem();
    }

    /** Мотыга нужна для правильной добычи с грядки. */
    private static ItemStack hoe() {
        return new ItemStack(Items.IRON_HOE);
    }
}
