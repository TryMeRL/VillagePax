package com.villagepax.sim;

import com.villagepax.block.ModBlocks;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Footing;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
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

import java.util.List;
import java.util.Optional;
import java.util.Random;

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
            SchematicLoader.get(BuildJob.schematicId(building)).ifPresent(schematic -> {
                // Дикий лес над крышей и у стен вырубается: дерево, нависшее
                // над домом, прячет его от улицы, а у двери — загораживает вход.
                Vec3i footprint = BuildSite.rotatedSize(schematic.size(), building.rotation());
                clearWild(world, village, building.anchor().add(-AROUND, 0, -AROUND),
                        building.anchor().add(footprint.getX() + AROUND,
                                schematic.size().getY() + CANOPY_HEIGHT,
                                footprint.getZ() + AROUND));
                dressBuilding(world, manager, village, building, schematic, palette);
            });
            manager.markDressed(building.id());
        }
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
