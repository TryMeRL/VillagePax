package com.villagepax.sim;

import com.villagepax.block.FurnitureBlock;
import com.villagepax.block.ModBlocks;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.sim.build.Access;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Footing;
import com.villagepax.sim.build.Grading;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import com.villagepax.sim.games.GameSpot;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Убранство улиц деревни народа: колодец на площади, фонари у домов, цветы.
 * <p>
 * Заказчик: «улучши восприятие деревень, чтоб прям хотелось жить в них».
 * Дома у деревни были, а деревни не было: коробки на склоне, между ними
 * трава. Живое место отличается от стройплощадки мелочами, которых никто
 * не заказывает, — колодцем, у которого собираются вечером, фонарём
 * у крыльца, цветами под окном, табличкой с названием. Их деревня народа
 * и ставит сама — она «старше игрока», и убранство у неё уже есть.
 * <p>
 * Колонии игрока этого не делают: там решает игрок, и фонарь у его двери,
 * которого он не ставил, был бы чужой рукой в его доме. Народам под землёй
 * и в кронах улиц нет — у них свой уклад, и фонарь на столбе там не к месту.
 * <p>
 * Всё поставленное помнит {@link SettlementManager}: снести деревню — значит
 * убрать и его, а не оставить на лугу одинокие фонари.
 */
public final class Streetscape {

    /**
     * Чем убирает улицы народ.
     *
     * @param wall    кладка колодца
     * @param fence   столб фонаря и стойки навеса
     * @param slab    навес колодца
     * @param lamp    фонарь
     * @param sign    табличка с названием
     * @param flowers цветы у домов
     */
    record Palette(Block wall, Block fence, Block slab, Block lamp, Block sign,
                   List<Block> flowers, Canopy canopy) {
    }

    /**
     * Чем накрыт колодец. Колодец — первое, что видит пришедший на площадь,
     * и у каждого народа он свой: «пусть каждый народ будет уникальным».
     */
    enum Canopy {
        /** Навес-плита на четырёх стойках — норманнский двор. */
        SLAB,
        /** Без кровли: резные столбы с фонарями — открытый сенот майя. */
        PILLARS,
        /** Радуга из шерсти над водой — пони. */
        RAINBOW,
        /** Двускатный навес с рогами на коньке — северяне. */
        GABLE,
        /** Черепичный шатёр с загнутыми углами — ямато. */
        PAGODA
    }

    /** Цвета радуги по кругу навеса, с запада на север и дальше посолонь. */
    private static final List<Block> RAINBOW = List.of(Blocks.RED_WOOL, Blocks.ORANGE_WOOL,
            Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.BLUE_WOOL,
            Blocks.PURPLE_WOOL, Blocks.MAGENTA_WOOL);

    private static final Palette NORMAN = new Palette(Blocks.COBBLESTONE, Blocks.DARK_OAK_FENCE,
            Blocks.DARK_OAK_SLAB, Blocks.LANTERN, Blocks.DARK_OAK_SIGN,
            List.of(Blocks.POPPY, Blocks.DANDELION, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER),
            Canopy.SLAB);

    private static final Palette MAYA = new Palette(Blocks.MOSSY_COBBLESTONE, Blocks.JUNGLE_FENCE,
            Blocks.JUNGLE_SLAB, ModBlocks.PAPER_LANTERN, Blocks.JUNGLE_SIGN,
            List.of(Blocks.ORANGE_TULIP, Blocks.RED_TULIP, Blocks.ALLIUM, Blocks.POPPY),
            Canopy.PILLARS);

    private static final Palette PONY = new Palette(Blocks.SMOOTH_SANDSTONE, Blocks.ACACIA_FENCE,
            Blocks.ACACIA_SLAB, ModBlocks.PAPER_LANTERN, Blocks.ACACIA_SIGN,
            List.of(Blocks.PINK_TULIP, Blocks.AZURE_BLUET, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY,
                    Blocks.ALLIUM),
            Canopy.RAINBOW);

    private static final Palette YAMATO = new Palette(Blocks.MOSSY_STONE_BRICKS,
            Blocks.CHERRY_FENCE, Blocks.DEEPSLATE_TILE_SLAB, ModBlocks.PAPER_LANTERN,
            Blocks.CHERRY_SIGN, List.of(Blocks.PINK_TULIP, Blocks.WHITE_TULIP, Blocks.AZURE_BLUET,
                    Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM),
            Canopy.PAGODA);

    private static final Palette NORD = new Palette(Blocks.COBBLESTONE, Blocks.SPRUCE_FENCE,
            Blocks.SPRUCE_SLAB, Blocks.LANTERN, Blocks.SPRUCE_SIGN,
            List.of(Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.OXEYE_DAISY,
                    Blocks.BLUE_ORCHID),
            Canopy.GABLE);

    /** Колодец — в этом кольце вокруг ратуши: на площади, но не у её дверей. */
    private static final int WELL_NEAR = 6;
    private static final int WELL_FAR = 11;

    /**
     * Площадь, которую деревня держит открытой: на столько блоков от ратуши
     * в каждую сторону дикий лес вырублен. Деревня в джунглях иначе стояла
     * под пологом, и ни дома, ни колодца не было видно даже с холма.
     */
    private static final int PLAZA = 14;

    /** На сколько вокруг дома лес не подходит к стенам. */
    private static final int AROUND = 2;

    /** До какой высоты над землёй вырубается полог. */
    private static final int CANOPY_HEIGHT = 16;

    /** Сколько цветов пробуют посадить у дома: взойдут те, кому есть где. */
    private static final int FLOWER_TRIES = 6;

    private Streetscape() {
    }

    /**
     * Убрать деревню: колодец, если его ещё нет, и дома, достроенные с
     * прошлого раза. Каждое здание убирается однажды — даже если места
     * под фонарь у него не нашлось: убранство не должно прирастать каждый
     * день, как сорняк.
     */
    public static void dress(ServerWorld world, SettlementManager manager, Settlement village) {
        if (!village.owner().isAutonomous() || !Footing.of(village).levelsTheGround()) {
            return;
        }
        Palette palette = paletteOf(village.culture());
        if (!manager.isDressed(village.id())) {
            BlockPos centre = village.center();
            clearWild(world, village, centre.add(-PLAZA, -2, -PLAZA),
                    centre.add(PLAZA, CANOPY_HEIGHT, PLAZA));
            well(world, manager, village, palette);
            manager.markDressed(village.id());
        }
        for (Building building : village.buildings()) {
            if (building.progress() != BuildProgress.DONE || manager.isDressed(building.id())
                    || BuildingTypes.isTownHall(building.type())) {
                continue;
            }
            Schematic plan = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (plan != null) {
                Vec3i around = BuildSite.rotatedSize(plan.size(), building.rotation());
                // Убирают только видимое: дом на краю прогрузки подождёт
                // следующего рассвета, а не затянет свои чанки в память
                // посреди тика. Отметки «убран» у него тоже нет — до тех пор.
                if (!world.isRegionLoaded(building.anchor().add(-AROUND - 1, 0, -AROUND - 1),
                        building.anchor().add(around.getX() + AROUND + 1, 0, around.getZ() + AROUND + 1))) {
                    continue;
                }
            }
            SchematicLoader.get(BuildJob.schematicId(building)).ifPresent(schematic -> {
                // Дикий лес над крышей и у стен вырубается: дерево, нависшее
                // над домом, прячет его от улицы, а у двери — загораживает вход.
                Vec3i footprint = BuildSite.rotatedSize(schematic.size(), building.rotation());
                int felled = clearWild(world, village, building.anchor().add(-AROUND, 0, -AROUND),
                        building.anchor().add(footprint.getX() + AROUND,
                                schematic.size().getY() + CANOPY_HEIGHT,
                                footprint.getZ() + AROUND));
                if (felled > 0) {
                    // Срубленный ствол мог стоять вместо земли — у калитки,
                    // вровень с насыпью, — и на его месте осталась яма.
                    // Откос его обошёл, крыльцо на нём стояло; после вырубки
                    // и то и другое меряется заново, по настоящей земле.
                    com.villagepax.sim.build.Grading.grade(world, village, building, schematic);
                    com.villagepax.sim.build.Access.porch(world, building, schematic);
                }
                dressBuilding(world, manager, village, building, schematic, palette);
            });
            manager.markDressed(building.id());
        }
        // Окна — отдельно от крыльца и на каждом уровне здания: надстроенный
        // этаж получает свои ставни, а деревни, убранные до ставен, получают
        // их на ближайшем рассвете.
        for (Building building : village.buildings()) {
            UUID marker = windowsMarker(building);
            if (building.progress() != BuildProgress.DONE || manager.isDressed(marker)) {
                continue;
            }
            Schematic plan = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (plan == null) {
                continue;
            }
            Vec3i around = BuildSite.rotatedSize(plan.size(), building.rotation());
            if (!world.isRegionLoaded(building.anchor().add(-2, 0, -2),
                    building.anchor().add(around.getX() + 2, 0, around.getZ() + 2))) {
                continue;
            }
            windows(world, manager, village, building, plan);
            manager.markDressed(marker);
        }
        gameTable(world, manager, village);
        // Ступень видна на улице, а не только в чате: у деревни вдоль улиц
        // встают фонари, у города мостится площадь у ратуши.
        if (village.level().ordinal() >= SettlementLevel.VILLAGE.ordinal()) {
            streetLamps(world, manager, village, palette);
        }
        if (village.level().ordinal() >= SettlementLevel.TOWN.ordinal()) {
            square(world, manager, village, palette);
            wall(world, manager, village);
        }
    }

    // --- городская стена ---

    /** Из чего у народа стена: тело, зубец (или навершие), камень ворот. */
    record Rampart(Block body, Block alternate, Block cap, boolean capEveryColumn, Block footing) {
    }

    static Rampart rampartOf(Identifier culture) {
        return switch (culture.getPath()) {
            // Майя: бут с мхом, зубцы через один — стена террас, а не замка.
            case "maya" -> new Rampart(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
                    Blocks.MOSSY_COBBLESTONE, false, Blocks.COBBLESTONE);
            // Пони и северяне — частокол: брёвна стоймя, заострённые жердью.
            case "pony" -> new Rampart(Blocks.ACACIA_LOG, Blocks.ACACIA_LOG, Blocks.ACACIA_FENCE, true,
                    Blocks.ACACIA_LOG);
            case "nord" -> new Rampart(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LOG, Blocks.SPRUCE_FENCE, true,
                    Blocks.COBBLESTONE);
            // Ямато: белёная стена на каменном цоколе под черепичным гребнем.
            case "yamato" -> new Rampart(ModBlocks.PLASTER, ModBlocks.PLASTER, Blocks.DEEPSLATE_TILE_SLAB, true,
                    Blocks.STONE_BRICKS);
            // Норманны: тёсаный камень с замшелыми вставками, зубцы через один.
            default -> new Rampart(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, false,
                    Blocks.STONE_BRICKS);
        };
    }

    /** Стена — не ближе стольких блоков к крайнему зданию. */
    static final int WALL_CLEARANCE = 6;

    /** И не ближе стольких к ратуше: городу нужен простор. */
    static final int WALL_LEAST = 40;

    /** Высота стены над землёй, без зубцов. */
    static final int WALL_HEIGHT = 3;

    /** Ширина ворот. */
    static final int GATE = 3;

    static UUID wallMarker(Settlement village) {
        return UUID.nameUUIDFromBytes(("villagepax:wall/" + village.id() + "/" + village.level().id())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Ключ, под которым лежат блоки стены: чтобы при росте снять старое кольцо целиком. */
    static UUID wallKey(Settlement village) {
        return wallKey(village.id());
    }

    public static UUID wallKey(UUID village) {
        return SettlementManager.wallKey(village);
    }

    /** Полуширина кольца стены — для проверок. */
    public static int wallHalfOf(Settlement village) {
        return wallHalf(village);
    }

    /** Полуширина кольца стены от ратуши: за крайним зданием, внутри границы. */
    static int wallHalf(Settlement village) {
        BlockPos c = village.center();
        int extent = 0;
        for (Building building : village.buildings()) {
            Schematic plan = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (plan == null) {
                continue;
            }
            Vec3i size = BuildSite.rotatedSize(plan.size(), building.rotation());
            BlockPos a = building.anchor();
            extent = Math.max(extent, Math.max(
                    Math.max(Math.abs(a.getX() - c.getX()), Math.abs(a.getX() + size.getX() - c.getX())),
                    Math.max(Math.abs(a.getZ() - c.getZ()), Math.abs(a.getZ() + size.getZ() - c.getZ()))));
        }
        int edge = village.level().claimRadiusChunks() * 16 - 2;
        return Math.min(edge, Math.max(WALL_LEAST, extent + WALL_CLEARANCE));
    }

    /**
     * Стена города: кольцо по краю застройки, с воротами на каждой стороне
     * и там, где её пересекает дорога или тропа.
     * <p>
     * Город отличается от деревни ещё и тем, что у него есть граница, которую
     * видно: входишь — через ворота. Стена идёт по земле, повторяя рельеф,
     * обходит здания и воду, а деревья на своей линии рубит. При росте
     * до столицы старое кольцо разбирается, и встаёт новое, шире: стена
     * поперёк улиц внутри города была бы помехой, а не защитой.
     * <p>
     * Гномам и эльфам стена не нужна: их чертог в толще горы, а палата в кронах.
     */
    static void wall(ServerWorld world, SettlementManager manager, Settlement village) {
        UUID marker = wallMarker(village);
        if (manager.isDressed(marker)) {
            return;
        }
        BlockPos c = village.center();
        int half = wallHalf(village);
        if (!world.isRegionLoaded(c.add(-half - 1, 0, -half - 1), c.add(half + 1, 0, half + 1))) {
            return;
        }
        Rampart rampart = rampartOf(village.culture());
        Palette palette = paletteOf(village.culture());
        UUID key = wallKey(village);

        // Прежнее кольцо — долой: город перерос его.
        for (BlockPos at : manager.decorOf(key)) {
            BlockState state = world.getBlockState(at);
            if (state.isOf(rampart.body()) || state.isOf(rampart.alternate()) || state.isOf(rampart.cap())
                    || state.isOf(rampart.footing()) || state.isOf(palette.lamp())) {
                world.setBlockState(at, Blocks.AIR.getDefaultState());
            }
        }
        manager.forgetDecor(key);

        List<BlockPos> ring = new ArrayList<>();
        for (int d = -half; d <= half; d++) {
            ring.add(new BlockPos(c.getX() + d, 0, c.getZ() - half));
            ring.add(new BlockPos(c.getX() + half, 0, c.getZ() + d));
            ring.add(new BlockPos(c.getX() - d, 0, c.getZ() + half));
            ring.add(new BlockPos(c.getX() - half, 0, c.getZ() - d));
        }
        Set<Long> gates = gatesOf(world, village, c, half, ring);

        int placed = 0;
        for (BlockPos column : ring) {
            long flat = BlockPos.asLong(column.getX(), 0, column.getZ());
            if (gates.contains(flat) || covered(village, column)) {
                continue;
            }
            clearWild(world, village, new BlockPos(column.getX(), c.getY() - 8, column.getZ()),
                    new BlockPos(column.getX(), c.getY() + 24, column.getZ()));
            BlockPos ground = Ground.buildableAt(world, column.getX(), column.getZ()).orElse(null);
            if (ground == null || !world.getFluidState(ground.down()).isEmpty()
                    || !world.getBlockState(ground.down()).isSolidBlock(world, ground.down())) {
                continue;
            }
            int height = 0;
            for (int dy = 0; dy < WALL_HEIGHT; dy++) {
                BlockPos at = ground.up(dy);
                if (!world.getBlockState(at).isReplaceable()) {
                    break;
                }
                Block block = dy == 0 ? rampart.footing()
                        : Math.floorMod(at.getX() * 31 + at.getZ() * 17 + dy, 6) == 0 ? rampart.alternate()
                        : rampart.body();
                wallPut(world, manager, village, key, at, block.getDefaultState());
                height++;
                placed++;
            }
            boolean crenel = rampart.capEveryColumn()
                    || Math.floorMod(column.getX() + column.getZ(), 2) == 0;
            BlockPos top = ground.up(height);
            if (height == WALL_HEIGHT && crenel && world.getBlockState(top).isReplaceable()) {
                wallPut(world, manager, village, key, top, rampart.cap().getDefaultState());
            }
        }

        // Столбы ворот — на ступень выше стены, с фонарём наверху.
        for (BlockPos post : gatePosts(ring, gates)) {
            BlockPos ground = Ground.buildableAt(world, post.getX(), post.getZ()).orElse(null);
            if (ground == null || covered(village, post)) {
                continue;
            }
            for (int dy = 0; dy <= WALL_HEIGHT; dy++) {
                BlockPos at = ground.up(dy);
                if (world.getBlockState(at).isReplaceable() || world.getBlockState(at).isOf(rampart.cap())) {
                    wallPut(world, manager, village, key, at,
                            (dy == 0 ? rampart.footing() : rampart.body()).getDefaultState());
                }
            }
            BlockPos lamp = ground.up(WALL_HEIGHT + 1);
            if (world.getBlockState(lamp).isReplaceable()) {
                wallPut(world, manager, village, key, lamp, lamp(palette, false));
            }
        }
        // От главных ворот — мостовая к площади: ворота, к которым не ведёт
        // улица, никуда не ведут.
        Block road = cobbleOf(village, palette);
        for (BlockPos gate : List.of(c.add(0, 0, -half + 1), c.add(0, 0, half - 1),
                c.add(-half + 1, 0, 0), c.add(half - 1, 0, 0))) {
            com.villagepax.sim.build.Roads.layRoute(world, village,
                    com.villagepax.sim.build.Roads.routeFrom(world, village, gate), road);
        }
        com.villagepax.VillagePax.LOGGER.info("У города {} встала стена: {} блоков, ворот {}",
                village.name(), placed, gates.size() / GATE);
        manager.markDressed(marker);
    }

    /**
     * Где в стене ворота: посередине каждой стороны и везде, где кольцо
     * пересекает дорогу, тропу или проход к двери. Ворота — {@link #GATE}
     * клеток в ширину.
     */
    private static Set<Long> gatesOf(ServerWorld world, Settlement village, BlockPos c, int half,
                                     List<BlockPos> ring) {
        Set<Long> gates = new HashSet<>();
        java.util.function.BiConsumer<Integer, Integer> open = (x, z) -> {
            boolean alongX = Math.abs(z - c.getZ()) == half;
            for (int d = -(GATE / 2); d <= GATE / 2; d++) {
                gates.add(alongX ? BlockPos.asLong(x + d, 0, z) : BlockPos.asLong(x, 0, z + d));
            }
        };
        open.accept(c.getX(), c.getZ() - half);
        open.accept(c.getX(), c.getZ() + half);
        open.accept(c.getX() - half, c.getZ());
        open.accept(c.getX() + half, c.getZ());
        Set<Long> passages = passages(village);
        Block road = cobbleOf(village, paletteOf(village.culture()));
        for (BlockPos column : ring) {
            long flat = BlockPos.asLong(column.getX(), 0, column.getZ());
            if (passages.contains(flat)) {
                open.accept(column.getX(), column.getZ());
                continue;
            }
            Ground.buildableAt(world, column.getX(), column.getZ()).ifPresent(ground -> {
                BlockState under = world.getBlockState(ground.down());
                if (under.isOf(Blocks.DIRT_PATH) || under.isOf(road) || under.isOf(Blocks.GRAVEL)) {
                    open.accept(column.getX(), column.getZ());
                }
            });
        }
        return gates;
    }

    /** Столбы — по обе стороны каждого проёма. */
    private static List<BlockPos> gatePosts(List<BlockPos> ring, Set<Long> gates) {
        List<BlockPos> posts = new ArrayList<>();
        Set<Long> onRing = new HashSet<>();
        ring.forEach(column -> onRing.add(BlockPos.asLong(column.getX(), 0, column.getZ())));
        for (BlockPos column : ring) {
            long flat = BlockPos.asLong(column.getX(), 0, column.getZ());
            if (gates.contains(flat)) {
                continue;
            }
            for (Direction side : Direction.Type.HORIZONTAL) {
                BlockPos next = column.offset(side);
                long beside = BlockPos.asLong(next.getX(), 0, next.getZ());
                if (onRing.contains(beside) && gates.contains(beside)) {
                    posts.add(column);
                    break;
                }
            }
        }
        return posts;
    }

    /** Блок стены: в убранство деревни и в список своего кольца. */
    private static void wallPut(ServerWorld world, SettlementManager manager, Settlement village, UUID key,
                                BlockPos at, BlockState state) {
        put(world, manager, village, at, state);
        manager.recordDecor(key, at);
    }

    // --- окна: ставни и цветочные ящики ---

    /** Отметка «окна этого здания на этом уровне убраны». */
    static UUID windowsMarker(Building building) {
        return UUID.nameUUIDFromBytes(("villagepax:windows/" + building.id() + "/" + building.level())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Чем народ закрывает окна снаружи. У ямато окна — бумажные сёдзи,
     * и деревянные ставни на них были бы чужой вещью.
     */
    static Optional<Block> shutterOf(Identifier culture) {
        return switch (culture.getPath()) {
            case "norman" -> Optional.of(Blocks.DARK_OAK_TRAPDOOR);
            case "pony" -> Optional.of(Blocks.ACACIA_TRAPDOOR);
            case "nord" -> Optional.of(Blocks.SPRUCE_TRAPDOOR);
            case "maya" -> Optional.of(Blocks.JUNGLE_TRAPDOOR);
            default -> Optional.empty();
        };
    }

    /**
     * Ставни по бокам окна и цветочный ящик под ним — снаружи стены.
     * <p>
     * Дом из коробки становится домом, в котором живут, от мелочей у окна:
     * ставня, распахнутая к стене, и герань в ящике. Всё — за следом
     * здания, на клетку от стены, поэтому ни схеме, ни ремонту, ни ходу
     * внутри это не мешает. Мимо дверей и проходов — ничего: перед входом
     * ящик был бы порогом, о который спотыкаются.
     */
    static void windows(ServerWorld world, SettlementManager manager, Settlement village,
                        Building building, Schematic schematic) {
        Block shutter = shutterOf(village.culture()).orElse(null);
        Vec3i size = schematic.size();
        Set<Long> passages = passages(village);
        for (com.villagepax.sim.build.BuildStep step : schematic.plan().steps()) {
            if (!step.placesBlock() || !isWindow(schematic.blockAt(step.paletteIndex()))) {
                continue;
            }
            BlockPos local = step.pos();
            int dx = local.getX() == 0 ? -1 : local.getX() == size.getX() - 1 ? 1 : 0;
            int dz = local.getZ() == 0 ? -1 : local.getZ() == size.getZ() - 1 ? 1 : 0;
            if ((dx == 0) == (dz == 0) || local.getY() < 1) {
                continue;
            }
            BlockPos pane = BuildSite.toWorld(building.anchor(), size, building.rotation(), local);
            BlockPos front = BuildSite.toWorld(building.anchor(), size, building.rotation(),
                    local.add(dx, 0, dz));
            Direction out = Direction.getFacing(front.getX() - pane.getX(), 0, front.getZ() - pane.getZ());
            if (!world.getBlockState(pane).isOf(schematic.blockAt(step.paletteIndex()).getBlock())) {
                continue;
            }

            BlockPos box = front.down();
            if (!passages.contains(BlockPos.asLong(box.getX(), 0, box.getZ()))
                    && world.getBlockState(box).isAir()
                    && natural(world.getBlockState(box.down()))) {
                put(world, manager, village, box, ModBlocks.FLOWER_BOX.getDefaultState()
                        .with(FurnitureBlock.FACING, out));
            }

            if (shutter == null) {
                continue;
            }
            for (Direction side : List.of(out.rotateYClockwise(), out.rotateYCounterclockwise())) {
                BlockPos beside = front.offset(side);
                if (isWindow(world.getBlockState(pane.offset(side)))
                        || passages.contains(BlockPos.asLong(beside.getX(), 0, beside.getZ()))
                        || !world.getBlockState(beside).isAir()
                        || !world.getBlockState(pane.offset(side)).isSolidBlock(world, pane.offset(side))) {
                    continue;
                }
                put(world, manager, village, beside, shutter.getDefaultState()
                        .with(net.minecraft.block.TrapdoorBlock.FACING, out)
                        .with(net.minecraft.block.TrapdoorBlock.OPEN, true)
                        .with(net.minecraft.block.TrapdoorBlock.HALF,
                                net.minecraft.block.enums.BlockHalf.TOP));
            }
        }
    }

    /** Окно — стекло или стеклянная панель; решётка гномов окном не считается. */
    private static boolean isWindow(BlockState state) {
        return !state.isOf(Blocks.IRON_BARS)
                && (state.getBlock() instanceof net.minecraft.block.PaneBlock
                || state.getBlock() instanceof net.minecraft.block.AbstractGlassBlock);
    }

    // --- улицы деревни и площадь города ---

    /** Фонари на улице — не чаще, чем через столько блоков. */
    static final int LAMP_SPACING = 8;

    /**
     * Фонари вдоль улиц — у деревни, выросшей из хутора.
     * <p>
     * У хутора фонарь только у крыльца; деревня зажигает улицу: столб
     * сбоку от мостовой через каждые несколько шагов, по обе стороны
     * не мешая ни проходу, ни двери. Ночью по огням видно, где улицы
     * и куда они ведут, — и нечисть на них не родится.
     */
    static void streetLamps(ServerWorld world, SettlementManager manager, Settlement village,
                            Palette palette) {
        List<List<BlockPos>> routes = new ArrayList<>();
        Set<Long> streets = new HashSet<>();
        for (Building building : village.buildings()) {
            if (building.progress() != BuildProgress.DONE) {
                continue;
            }
            List<BlockPos> route = com.villagepax.sim.build.Roads.route(world, village, building);
            routes.add(route);
            route.forEach(tile -> streets.add(BlockPos.asLong(tile.getX(), 0, tile.getZ())));
        }
        List<BlockPos> lamps = new ArrayList<>();
        for (BlockPos at : manager.decorOf(village.id())) {
            if (world.isChunkLoaded(at) && world.getBlockState(at).isOf(palette.lamp())) {
                lamps.add(at);
            }
        }
        Set<Long> passages = passages(village);

        for (List<BlockPos> route : routes) {
            for (int i = 1; i + 1 < route.size(); i++) {
                BlockPos tile = route.get(i);
                if (near(lamps, tile, LAMP_SPACING)) {
                    continue;
                }
                BlockPos before = route.get(i - 1);
                BlockPos after = route.get(i + 1);
                Direction along = Direction.getFacing(after.getX() - before.getX(), 0,
                        after.getZ() - before.getZ());
                for (Direction side : List.of(along.rotateYClockwise(), along.rotateYCounterclockwise())) {
                    BlockPos column = tile.offset(side);
                    long key = BlockPos.asLong(column.getX(), 0, column.getZ());
                    if (streets.contains(key) || passages.contains(key)) {
                        continue;
                    }
                    BlockPos at = Ground.buildableAt(world, column.getX(), column.getZ())
                            .filter(spot -> Math.abs(spot.getY() - tile.up().getY()) <= 1)
                            .filter(spot -> freeFor(world, village, spot, 3))
                            .orElse(null);
                    if (at != null) {
                        put(world, manager, village, at, palette.fence().getDefaultState());
                        put(world, manager, village, at.up(), palette.fence().getDefaultState());
                        put(world, manager, village, at.up(2), lamp(palette, false));
                        lamps.add(at.up(2));
                        break;
                    }
                }
            }
        }
    }

    private static boolean near(List<BlockPos> lamps, BlockPos tile, int reach) {
        for (BlockPos lamp : lamps) {
            int dx = lamp.getX() - tile.getX();
            int dz = lamp.getZ() - tile.getZ();
            if (dx * dx + dz * dz < reach * reach) {
                return true;
            }
        }
        return false;
    }

    /** На сколько клеток площадь выходит за след ратуши: у города и у столицы. */
    static int squareMargin(SettlementLevel level) {
        return level.ordinal() >= SettlementLevel.CAPITAL.ordinal() ? 5 : 3;
    }

    /** Отметка «площадь этой ступени уже вымощена». */
    static UUID squareMarker(UUID village, SettlementLevel level) {
        return UUID.nameUUIDFromBytes(("villagepax:square/" + village + "/" + level.id())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Мощёная площадь у ратуши — у города.
     * <p>
     * Город отличается от деревни тем, что у него есть середина, по которой
     * не топчут траву: камень вокруг ратуши, фонари по углам. Мостится
     * только природная земля вровень с порогом ратуши — колодец, доска,
     * игорный стол и чужое остаются как стояли. У столицы площадь шире.
     */
    static void square(ServerWorld world, SettlementManager manager, Settlement village,
                       Palette palette) {
        UUID marker = squareMarker(village.id(), village.level());
        if (manager.isDressed(marker)) {
            return;
        }
        Building hall = village.buildings().stream()
                .filter(building -> BuildingTypes.isTownHall(building.type()))
                .findFirst().orElse(null);
        Schematic plan = hall == null ? null : SchematicLoader.get(BuildJob.schematicId(hall)).orElse(null);
        if (plan == null) {
            return;
        }
        Vec3i size = BuildSite.rotatedSize(plan.size(), hall.rotation());
        int margin = squareMargin(village.level());
        BlockPos from = hall.anchor().add(-margin, 0, -margin);
        BlockPos to = hall.anchor().add(size.getX() + margin - 1, 0, size.getZ() + margin - 1);
        if (!world.isRegionLoaded(from, to)) {
            return;
        }
        BlockState stone = cobbleOf(village, palette).getDefaultState();
        int floor = hall.anchor().getY() - 1;
        for (int x = from.getX(); x <= to.getX(); x++) {
            for (int z = from.getZ(); z <= to.getZ(); z++) {
                BlockPos column = new BlockPos(x, floor, z);
                if (BuildSite.covers(hall.anchor(), plan.size(), hall.rotation(),
                        new BlockPos(x, hall.anchor().getY(), z))) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos ground = column.up(dy);
                    BlockState state = world.getBlockState(ground);
                    if ((natural(state) || state.isOf(Blocks.DIRT_PATH))
                            && world.getBlockState(ground.up()).isReplaceable()) {
                        world.setBlockState(ground, stone);
                        break;
                    }
                }
            }
        }
        // Фонари — по четырём углам площади.
        for (BlockPos corner : List.of(from, new BlockPos(to.getX(), floor, from.getZ()),
                new BlockPos(from.getX(), floor, to.getZ()), to)) {
            Ground.buildableAt(world, corner.getX(), corner.getZ())
                    .filter(at -> Math.abs(at.getY() - hall.anchor().getY()) <= 1)
                    .filter(at -> !covered(village, at)
                            && world.getBlockState(at).isReplaceable()
                            && world.getBlockState(at.up()).isReplaceable()
                            && world.getBlockState(at.up(2)).isReplaceable())
                    .ifPresent(at -> {
                        put(world, manager, village, at, palette.fence().getDefaultState());
                        put(world, manager, village, at.up(), palette.fence().getDefaultState());
                        put(world, manager, village, at.up(2), lamp(palette, false));
                    });
        }
        manager.markDressed(marker);
    }

    /**
     * Камень мостовой народа: первое из его списка, что кладётся целым
     * блоком, — натоптанной тропой площадь не мостят.
     */
    static Block cobbleOf(Settlement village, Palette palette) {
        com.villagepax.core.culture.Culture culture =
                com.villagepax.core.culture.CultureManager.get(village.culture());
        if (culture != null) {
            for (Identifier id : culture.road()) {
                Block block = net.minecraft.registry.Registries.BLOCK.get(id);
                if (block != Blocks.AIR && block != Blocks.DIRT_PATH
                        && block.getDefaultState().isFullCube(net.minecraft.world.EmptyBlockView.INSTANCE,
                        BlockPos.ORIGIN)) {
                    return block;
                }
            }
        }
        return palette.wall();
    }

    static Palette paletteOf(Identifier culture) {
        return switch (culture.getPath()) {
            case "maya" -> MAYA;
            case "pony" -> PONY;
            case "nord" -> NORD;
            case "yamato" -> YAMATO;
            default -> NORMAN;
        };
    }

    // --- колодец ---

    /**
     * Колодец на площади: сруб в кольцо, вода, навес на четырёх стойках,
     * фонарь под навесом и табличка с названием деревни.
     * <p>
     * У колодца собираются вечером — туда и так ведёт вечерний сбор, —
     * и название на табличке впервые говорит пришедшему, куда он пришёл,
     * без команды и без экрана.
     */
    private static void well(ServerWorld world, SettlementManager manager, Settlement village,
                             Palette palette) {
        BlockPos centre = wellSpot(world, village).orElse(null);
        if (centre == null) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos ring = centre.add(dx, 0, dz);
                if (dx == 0 && dz == 0) {
                    put(world, manager, village, ring.down(), Blocks.WATER.getDefaultState());
                    put(world, manager, village, ring, Blocks.WATER.getDefaultState());
                } else {
                    put(world, manager, village, ring, palette.wall().getDefaultState());
                }
                canopy(world, manager, village, palette, ring, dx, dz);
            }
        }
        if (palette.canopy() != Canopy.PILLARS) {
            put(world, manager, village, centre.up(2), lamp(palette, true));
        }

        // Табличка — со стороны ратуши, лицом к ней: её читают с площади.
        Direction toward = Direction.getFacing(village.center().getX() - centre.getX(), 0,
                village.center().getZ() - centre.getZ());
        BlockPos at = centre.offset(toward, 2);
        if (world.getBlockState(at).isAir() && world.getBlockState(at.down()).isSolidBlock(world, at.down())) {
            put(world, manager, village, at, palette.sign().getDefaultState()
                    .with(SignBlock.ROTATION, RotationPropertyHelper.fromDirection(toward)));
            if (world.getBlockEntity(at) instanceof SignBlockEntity sign) {
                sign.setText(sign.getText(true).withMessage(1, Text.literal(village.name())), true);
                sign.markDirty();
                world.updateListeners(at, world.getBlockState(at), world.getBlockState(at), 3);
            }
        }
        hitch(world, manager, village, palette, centre, toward);
    }

    /**
     * Переписать табличку у колодца: поселение переименовали.
     * <p>
     * Табличку ставят один раз, когда мостят площадь, и название в ней
     * прежде так и оставалось первым — деревня звалась на карте одним
     * именем, а у колодца другим.
     */
    public static void relabel(ServerWorld world, Settlement village) {
        BlockPos centre = wellSpot(world, village).orElse(null);
        if (centre == null) {
            return;
        }
        Direction toward = Direction.getFacing(village.center().getX() - centre.getX(), 0,
                village.center().getZ() - centre.getZ());
        BlockPos at = centre.offset(toward, 2);
        if (world.isChunkLoaded(at) && world.getBlockEntity(at) instanceof SignBlockEntity sign) {
            sign.setText(sign.getText(true).withMessage(1, Text.literal(village.name())), true);
            sign.markDirty();
            world.updateListeners(at, world.getBlockState(at), world.getBlockState(at), 3);
        }
    }

    /**
     * Какие животные у кого стоят у коновязи. Деревня без скотины — декорация:
     * у пони у столба кони, у норманнов овцы, у майя попугаи-ара, у северян
     * ездовые волки, у ямато — лисы, посланницы Инари. Гномы и эльфы живут
     * не на площади под небом, их улицы не убираются, и коновязи у них нет.
     */
    private static final java.util.Map<String, EntityType<? extends MobEntity>> HERDS =
            java.util.Map.of("norman", EntityType.SHEEP, "maya", EntityType.PARROT,
                    "pony", EntityType.HORSE, "nord", EntityType.WOLF,
                    "yamato", EntityType.FOX);

    /** Сколько животных у одного столба: пара, чтобы было не одиноко. */
    private static final int HERD = 2;

    /**
     * Коновязь у колодца: столб и животные народа на привязи.
     * <p>
     * На привязи нарочно: вольная скотина к утру разбредается по лесу,
     * и площадь снова пуста. Привязанное животное не пропадает само и не
     * уходит — оно и есть та жизнь, которую видно с улицы.
     */
    private static void hitch(ServerWorld world, SettlementManager manager, Settlement village,
                              Palette palette, BlockPos well, Direction away) {
        EntityType<? extends MobEntity> kind = HERDS.get(village.culture().getPath());
        if (kind == null) {
            return;
        }
        // Место ищется в нескольких шагах, а не в одном: столб на ровно
        // четвёртом блоке от сруба не находил места, стоило дому или проходу
        // к его двери лечь рядом, — и деревня оставалась без скотины.
        BlockPos post = null;
        for (int reach : new int[]{4, 5, 3, 6}) {
            for (Direction side : new Direction[]{away.getOpposite(), away.rotateYClockwise(),
                    away.rotateYCounterclockwise()}) {
                for (int up = 1; up >= -1 && post == null; up--) {
                    BlockPos at = well.offset(side, reach).up(up);
                    if (freeFor(world, village, at, 3)
                            && world.getBlockState(at.down()).isSolidBlock(world, at.down())) {
                        post = at;
                    }
                }
                if (post != null) {
                    break;
                }
            }
            if (post != null) {
                break;
            }
        }
        if (post == null) {
            return;
        }
        put(world, manager, village, post, palette.fence().getDefaultState());
        net.minecraft.entity.decoration.LeashKnotEntity knot =
                net.minecraft.entity.decoration.LeashKnotEntity.getOrCreate(world, post);
        for (int i = 0; i < HERD; i++) {
            MobEntity animal = kind.create(world);
            if (animal == null) {
                continue;
            }
            double dx = i == 0 ? 1.5 : -1.5;
            animal.refreshPositionAndAngles(post.getX() + 0.5 + dx, post.getY(),
                    post.getZ() + 0.5, world.getRandom().nextFloat() * 360f, 0f);
            animal.initialize(world, world.getLocalDifficulty(post),
                    net.minecraft.entity.SpawnReason.STRUCTURE, null, null);
            animal.setPersistent();
            world.spawnEntity(animal);
            animal.attachLeash(knot, true);
        }
    }

    /** Одна клетка навеса над колодцем, по обычаю народа. */
    private static void canopy(ServerWorld world, SettlementManager manager, Settlement village,
                               Palette palette, BlockPos ring, int dx, int dz) {
        boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
        if (palette.canopy() == Canopy.PILLARS) {
            if (corner) {
                put(world, manager, village, ring.up(), ModBlocks.CARVED_STONE.getDefaultState());
                put(world, manager, village, ring.up(2), lamp(palette, false));
            }
            return;
        }
        if (corner) {
            put(world, manager, village, ring.up(), palette.fence().getDefaultState());
            put(world, manager, village, ring.up(2), palette.fence().getDefaultState());
        }
        BlockState roof = switch (palette.canopy()) {
            case RAINBOW -> dx == 0 && dz == 0 ? Blocks.PINK_WOOL.getDefaultState()
                    : RAINBOW.get(rainbowIndex(dx, dz)).getDefaultState();
            case PAGODA -> dx == 0 && dz == 0 ? Blocks.DEEPSLATE_TILES.getDefaultState()
                    : Blocks.DEEPSLATE_TILE_STAIRS.getDefaultState().with(StairsBlock.FACING,
                    dz < 0 ? Direction.SOUTH : dz > 0 ? Direction.NORTH
                            : dx < 0 ? Direction.EAST : Direction.WEST);
            case GABLE -> dz == 0 ? palette.slab().getDefaultState()
                    : Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING,
                    dz < 0 ? Direction.SOUTH : Direction.NORTH);
            default -> palette.slab().getDefaultState();
        };
        put(world, manager, village, ring.up(3), roof);
        if (palette.canopy() == Canopy.GABLE && dz == 0 && dx != 0) {
            put(world, manager, village, ring.up(4), palette.fence().getDefaultState());
        }
        // Углы шатра задраны вверх плитой — та же линия, что у кровель ямато;
        // над серединой — плита-навершие.
        if (palette.canopy() == Canopy.PAGODA && (corner || (dx == 0 && dz == 0))) {
            put(world, manager, village, ring.up(4), palette.slab().getDefaultState());
        }
    }

    /** Место клетки в кольце восьми соседей, посолонь от северо-запада. */
    static int rainbowIndex(int dx, int dz) {
        int[][] order = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int i = 0; i < order.length; i++) {
            if (order[i][0] == dx && order[i][1] == dz) {
                return i;
            }
        }
        throw new IllegalArgumentException("не сосед: " + dx + ", " + dz);
    }

    /**
     * Где встанет колодец: ровный клочок пять на пять вокруг будущего
     * сруба, в кольце площади, вдали от чужих следов.
     */
    static Optional<BlockPos> wellSpot(ServerWorld world, Settlement village) {
        BlockPos centre = village.center();
        for (int radius = WELL_NEAR; radius <= WELL_FAR; radius++) {
            for (int step = 0; step < 8; step++) {
                double angle = step * Math.PI / 4 + Math.PI / 8;
                int x = centre.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = centre.getZ() + (int) Math.round(Math.sin(angle) * radius);
                Optional<BlockPos> ground = Ground.buildableAt(world, x, z);
                if (ground.isPresent() && flatAndFree(world, village, ground.get())) {
                    return ground;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean flatAndFree(ServerWorld world, Settlement village, BlockPos middle) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos at = middle.add(dx, 0, dz);
                if (covered(village, at)
                        || !world.getBlockState(at.down()).isSolidBlock(world, at.down())
                        || !natural(world.getBlockState(at.down()))) {
                    return false;
                }
                for (int up = 0; up < 5; up++) {
                    if (!world.getBlockState(at.up(up)).isReplaceable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // --- у домов ---

    /**
     * Фонарь у крыльца и цветы вокруг.
     * <p>
     * Фонарь — наискось от двери, на шаг вперёд и на два вбок: прямо
     * перед дверью идёт тропа к площади, и столб на ней был бы шлагбаумом.
     */
    private static void dressBuilding(ServerWorld world, SettlementManager manager,
                                      Settlement village, Building building, Schematic schematic,
                                      Palette palette) {
        Vec3i size = schematic.size();
        List<Schematic.Entrance> doors = schematic.entrances();
        if (!doors.isEmpty()) {
            Schematic.Entrance door = doors.get(0);
            BlockPos inside = BuildSite.toWorld(building.anchor(), size, building.rotation(),
                    door.pos());
            BlockPos outside = BuildSite.toWorld(building.anchor(), size, building.rotation(),
                    door.pos().offset(door.wayOut()));
            Direction out = Direction.getFacing(outside.getX() - inside.getX(), 0,
                    outside.getZ() - inside.getZ());
            BlockPos column = outside.offset(out).offset(out.rotateYClockwise(), 2);
            Ground.buildableAt(world, column.getX(), column.getZ())
                    .filter(at -> Math.abs(at.getY() - outside.getY()) <= 1)
                    .filter(at -> freeFor(world, village, at, 3))
                    .ifPresent(at -> {
                        put(world, manager, village, at, palette.fence().getDefaultState());
                        put(world, manager, village, at.up(), palette.fence().getDefaultState());
                        put(world, manager, village, at.up(2), lamp(palette, false));
                    });
        }

        Random random = new Random(building.id().getMostSignificantBits());
        Vec3i footprint = BuildSite.rotatedSize(size, building.rotation());
        for (int attempt = 0; attempt < FLOWER_TRIES; attempt++) {
            int side = random.nextInt(4);
            int along = random.nextInt(side % 2 == 0 ? footprint.getX() : footprint.getZ());
            int off = 1 + random.nextInt(2);
            BlockPos a = building.anchor();
            BlockPos column = switch (side) {
                case 0 -> a.add(along, 0, -off);
                case 1 -> a.add(footprint.getX() - 1 + off, 0, along);
                case 2 -> a.add(along, 0, footprint.getZ() - 1 + off);
                default -> a.add(-off, 0, along);
            };
            Block flower = palette.flowers().get(random.nextInt(palette.flowers().size()));
            Ground.buildableAt(world, column.getX(), column.getZ())
                    .filter(at -> world.getBlockState(at.down()).isIn(BlockTags.DIRT))
                    .filter(at -> freeFor(world, village, at, 1))
                    .ifPresent(at -> put(world, manager, village, at, flower.getDefaultState()));
        }
    }

    /**
     * Вырубить дикий лес в коробке: брёвна и природную листву.
     * <p>
     * Только дикий: листва, посаженная рукой, помечена «постоянной» и не
     * трогается, а брёвна внутри следа здания — это стены и балки, их
     * не трогает никто. Роща лесоруба — тоже след здания, и её саженцы
     * растут где росли.
     */
    static int clearWild(ServerWorld world, Settlement village, BlockPos from, BlockPos to) {
        int cleared = 0;
        for (BlockPos at : BlockPos.iterate(from, to)) {
            BlockState state = world.getBlockState(at);
            boolean wildLeaves = state.isIn(BlockTags.LEAVES)
                    && state.contains(net.minecraft.block.LeavesBlock.PERSISTENT)
                    && !state.get(net.minecraft.block.LeavesBlock.PERSISTENT);
            boolean log = state.isIn(BlockTags.LOGS) && !inFootprint(village, at);
            if (wildLeaves || log) {
                world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                cleared++;
            }
        }
        return cleared;
    }

    /** Лежит ли клетка в самом следе какого-нибудь здания — без зазора. */
    private static boolean inFootprint(Settlement village, BlockPos at) {
        for (Building building : village.buildings()) {
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic != null && BuildSite.covers(building.anchor(), schematic.size(),
                    building.rotation(), at)) {
                return true;
            }
        }
        return false;
    }

    // --- игорный стол ---

    /**
     * Игорный стол с лавками по бокам — у двери пивной, а где её нет, у ратуши.
     * <p>
     * За ним по вечерам собирается компания: кости, кружки и две лавки
     * говорят «здесь играют» раньше, чем кто-нибудь бросит кость. Ставится
     * раз и навсегда; не нашлось места — пробуется при следующем обходе:
     * старая деревня получает стол на первом же, а та, что застроила площадь
     * вплотную, — когда место найдётся.
     * <p>
     * Не на пути ни к одной двери: стол посреди прохода — баррикада, и через
     * него житель не прошёл бы домой. Лицом к площади, лавки — поперёк:
     * игроки сидят друг против друга, и с площади видно обоих.
     *
     * @return где встал стол; пусто — не встал или уже стоит
     */
    static Optional<BlockPos> gameTable(ServerWorld world, SettlementManager manager,
                                        Settlement village) {
        UUID marker = GameSpot.tableMarker(village.id());
        if (manager.isDressed(marker)) {
            return Optional.empty();
        }
        Optional<BlockPos> door = GameSpot.breweryDoor(village);
        BlockPos level = door.orElse(village.center());
        Set<Long> passages = passages(village);
        for (int reach = GameSpot.TABLE_FROM; reach <= GameSpot.TABLE_TO; reach++) {
            for (BlockPos column : tableRing(village, door, reach)) {
                BlockPos table = Ground.buildableAt(world, column.getX(), column.getZ())
                        .filter(at -> Math.abs(at.getY() - level.getY()) <= 2)
                        .orElse(null);
                if (table == null || !seatable(world, village, passages, table)) {
                    continue;
                }
                Direction face = Direction.getFacing(village.center().getX() - table.getX(), 0,
                        village.center().getZ() - table.getZ());
                List<Direction> sides = List.of(face.rotateYClockwise(), face.rotateYCounterclockwise());
                boolean benches = sides.stream().allMatch(side -> {
                    BlockPos bench = table.offset(side);
                    return Ground.buildableAt(world, bench.getX(), bench.getZ())
                            .filter(bench::equals).isPresent()
                            && seatable(world, village, passages, bench);
                });
                if (!benches) {
                    continue;
                }
                put(world, manager, village, table, ModBlocks.GAME_TABLE.getDefaultState()
                        .with(FurnitureBlock.FACING, face));
                for (Direction side : sides) {
                    put(world, manager, village, table.offset(side), ModBlocks.BENCH.getDefaultState()
                            .with(FurnitureBlock.FACING, side.getOpposite()));
                }
                manager.markDressed(marker);
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    // --- доска заданий ---

    /** Доска — не ближе стольких шагов от стены ратуши и не дальше. */
    static final int BOARD_FROM = 2;
    static final int BOARD_TO = 6;

    /** Метка «доска поставлена» в памяти убранства — раз и навсегда, как стол. */
    public static UUID boardMarker(UUID village) {
        return UUID.nameUUIDFromBytes(("villagepax:notice_board/" + village)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Доска заданий у ратуши — лицом к площади.
     * <p>
     * У народа с земли — снаружи, на природной земле в двух–шести шагах
     * от стены ратуши, не на проходе к двери и так, чтобы перед ней было
     * где встать. У народа из горы и из крон снаружи земли нет — тогда
     * на полу зала ратуши, не вплотную к блоку ратуши. Ставится раз
     * и навсегда; не нашлось места — пробуется при следующем обходе.
     *
     * @return где встала доска; пусто — не встала или уже стоит
     */
    public static Optional<BlockPos> noticeBoard(ServerWorld world, SettlementManager manager,
                                                 Settlement village) {
        UUID marker = boardMarker(village.id());
        if (!village.owner().isAutonomous() || manager.isDressed(marker)
                || !world.isChunkLoaded(village.center())) {
            return Optional.empty();
        }
        Set<Long> passages = passages(village);
        BlockPos centre = village.center();
        Optional<BlockPos> spot = Footing.of(village).levelsTheGround()
                ? outsideTheHall(world, village, passages)
                : insideTheHall(world, village, passages);
        spot.ifPresent(at -> {
            Direction face = Direction.getFacing(centre.getX() - at.getX(), 0, centre.getZ() - at.getZ());
            put(world, manager, village, at, ModBlocks.NOTICE_BOARD.getDefaultState()
                    .with(FurnitureBlock.FACING, face));
            manager.markDressed(marker);
        });
        return spot;
    }

    private static Optional<BlockPos> outsideTheHall(ServerWorld world, Settlement village,
                                                     Set<Long> passages) {
        BlockPos centre = village.center();
        for (int reach = BOARD_FROM; reach <= BOARD_TO; reach++) {
            for (BlockPos column : tableRing(village, Optional.empty(), reach)) {
                if (!world.isChunkLoaded(column)) {
                    continue;
                }
                BlockPos at = Ground.buildableAt(world, column.getX(), column.getZ())
                        .filter(found -> Math.abs(found.getY() - centre.getY()) <= 2)
                        .orElse(null);
                if (at == null || !seatable(world, village, passages, at)) {
                    continue;
                }
                Direction face = Direction.getFacing(centre.getX() - at.getX(), 0,
                        centre.getZ() - at.getZ());
                BlockPos front = at.offset(face);
                // Перед доской должно быть где встать — иначе листки читать неоткуда.
                if (!com.villagepax.sim.work.Standing.canStandAt(world, front)
                        || passages.contains(BlockPos.asLong(at.getX(), 0, at.getZ()))) {
                    continue;
                }
                return Optional.of(at);
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> insideTheHall(ServerWorld world, Settlement village,
                                                    Set<Long> passages) {
        int[] box = GameSpot.hallBox(village);
        BlockPos centre = village.center();
        BlockPos best = null;
        double bestAway = Double.MAX_VALUE;
        for (int x = box[0] + 1; x <= box[2] - 1; x++) {
            for (int z = box[1] + 1; z <= box[3] - 1; z++) {
                if (Math.max(Math.abs(x - centre.getX()), Math.abs(z - centre.getZ())) < 2
                        || passages.contains(BlockPos.asLong(x, 0, z))) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos at = new BlockPos(x, centre.getY() + dy, z);
                    if (!world.isChunkLoaded(at) || !com.villagepax.sim.work.Standing.canStandAt(world, at)) {
                        continue;
                    }
                    Direction face = Direction.getFacing(centre.getX() - x, 0, centre.getZ() - z);
                    if (!com.villagepax.sim.work.Standing.canStandAt(world, at.offset(face))) {
                        continue;
                    }
                    // Ближе к ратуше — виднее; дальше трёх шагов — уже угол зала.
                    double away = at.getSquaredDistance(centre);
                    if (away < bestAway) {
                        bestAway = away;
                        best = at;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Колонны в этом шаге: вокруг клетки перед пивной или вокруг стен ратуши. */
    private static List<BlockPos> tableRing(Settlement village, Optional<BlockPos> door, int reach) {
        int[] box = door.map(at -> new int[]{at.getX(), at.getZ(), at.getX(), at.getZ()})
                .orElseGet(() -> GameSpot.hallBox(village));
        List<BlockPos> ring = new ArrayList<>();
        for (int x = box[0] - reach; x <= box[2] + reach; x++) {
            for (int z = box[1] - reach; z <= box[3] + reach; z++) {
                boolean edge = x == box[0] - reach || x == box[2] + reach
                        || z == box[1] - reach || z == box[3] + reach;
                if (edge) {
                    ring.add(new BlockPos(x, 0, z));
                }
            }
        }
        return ring;
    }

    /** Под стол и лавку: природная земля вне следов, воздух в рост и не проход к двери. */
    private static boolean seatable(ServerWorld world, Settlement village, Set<Long> passages,
                                    BlockPos at) {
        return freeFor(world, village, at, 2)
                && !passages.contains(BlockPos.asLong(at.getX(), 0, at.getZ()));
    }

    /** Проходы к дверям всех зданий: колонны, где убранству не место. */
    private static Set<Long> passages(Settlement village) {
        Set<Long> cells = new HashSet<>();
        for (Building building : village.buildings()) {
            SchematicLoader.get(BuildJob.schematicId(building)).ifPresent(schematic -> cells.addAll(
                    Access.doorway(building, schematic, Grading.APPROACH, 1)));
        }
        return cells;
    }

    // --- общее ---

    private static BlockState lamp(Palette palette, boolean hanging) {
        return palette.lamp().getDefaultState().with(LanternBlock.HANGING, hanging);
    }

    /** Клетка свободна под убранство: не в следе здания, воздух в рост. */
    private static boolean freeFor(ServerWorld world, Settlement village, BlockPos at, int height) {
        if (covered(village, at) || !natural(world.getBlockState(at.down()))) {
            return false;
        }
        for (int up = 0; up < height; up++) {
            if (!world.getBlockState(at.up(up)).isReplaceable()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Земля под убранством — природная: трава, земля, песок. На мостовую
     * и тропу ничего не ставится, иначе фонарь встал бы посреди улицы.
     */
    private static boolean natural(BlockState ground) {
        return ground.isIn(BlockTags.DIRT) || ground.isIn(BlockTags.SAND)
                || ground.isOf(Blocks.GRASS_BLOCK);
    }

    private static boolean covered(Settlement village, BlockPos at) {
        for (Building building : village.buildings()) {
            Schematic schematic = SchematicLoader.get(BuildJob.schematicId(building)).orElse(null);
            if (schematic == null) {
                continue;
            }
            Vec3i footprint = BuildSite.rotatedSize(schematic.size(), building.rotation());
            BlockPos a = building.anchor();
            if (at.getX() >= a.getX() - 1 && at.getX() <= a.getX() + footprint.getX()
                    && at.getZ() >= a.getZ() - 1 && at.getZ() <= a.getZ() + footprint.getZ()) {
                return true;
            }
        }
        return false;
    }

    private static void put(ServerWorld world, SettlementManager manager, Settlement village,
                            BlockPos at, BlockState state) {
        world.setBlockState(at, state);
        manager.recordDecor(village.id(), at);
    }
}
