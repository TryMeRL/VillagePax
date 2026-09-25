package com.villagepax.block;

import com.villagepax.block.wonder.Wonders;

import com.villagepax.VillagePax;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.WallBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.enums.Instrument;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Блоки мода.
 * <p>
 * Маркеры — служебные блоки, которые ставятся внутри схемы здания и при постройке
 * заменяются билдером на воздух, оставляя после себя точку интереса. Благодаря им
 * одна схема описывает и геометрию здания, и его логику: где рабочее место,
 * где кровать, где сундук, где вход.
 */
public final class ModBlocks {

    /** Порядок важен: в этом же порядке блоки попадают в творческую вкладку. */
    private static final Map<Identifier, Block> REGISTERED = new LinkedHashMap<>();

    public static final TownHallBlock TOWN_HALL = register("town_hall",
            new TownHallBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)
                    .strength(4.0f, 12.0f)
                    .sounds(BlockSoundGroup.WOOD)));

    /**
     * Стена норманнов: белая штукатурка по тёмным балкам.
     * <p>
     * Своя, а не крашеная глина, которой она была раньше. Народу нужен
     * <b>свой материал</b>: по нему деревню узнают издалека, а глина —
     * это заимствование, которое в любом чужом моде выглядит иначе.
     */
    public static final Block TIMBER_FRAME = register("timber_frame",
            plain(MapColor.TERRACOTTA_WHITE));

    /** Та же штукатурка без балок — для простых стен и для внутренностей. */
    public static final Block PLASTER = register("plaster", plain(MapColor.OFF_WHITE));

    /**
     * Поленница. Дрова у дома — самая короткая примета того, что здесь живут:
     * их видно с улицы, и они ничего не делают, кроме этого.
     */
    public static final Block FIREWOOD = register("firewood",
            // nonOpaque обязателен: модель больше не полный куб, а поленница
            // из брёвен со щелями. Без него ваниль срезает грани соседей
            // и сквозь поленницу видно пустоту.
            new PillarBlock(AbstractBlock.Settings.copy(Blocks.OAK_LOG).nonOpaque()));

    /**
     * Бельё на верёвке. Просьба заказчика — та самая «мелкая жизнь».
     * <p>
     * Сквозь него ходят: верёвка с простынями не должна останавливать ни
     * жителя, ни игрока. Ломается мгновенно, как ткань, а не как стена.
     */
    public static final Block LAUNDRY = register("laundry",
            new LaundryBlock(AbstractBlock.Settings.create()
                    .noCollision()
                    .breakInstantly()
                    .sounds(BlockSoundGroup.WOOL)
                    .nonOpaque()));

    /**
     * Печная труба. Единственный блок мода, который сам по себе ничего
     * не делает и всё же нужен: дым над крышей виден оттуда, откуда
     * ни жителей, ни их дел ещё не разглядеть.
     */
    public static final Block CHIMNEY = register("chimney",
            new ChimneyBlock(AbstractBlock.Settings.copy(Blocks.BRICKS)));

    /** Мешок снеди. Стоит у склада и на ферме — знак, что колония кормится. */
    public static final Block GRAIN_SACK = register("grain_sack",
            new Block(AbstractBlock.Settings.create()
                    .mapColor(MapColor.OAK_TAN)
                    .strength(0.6f)
                    .sounds(BlockSoundGroup.WOOL)
                    // Мешок уже клетки и ниже её: полным кубом он не был
                    // никогда, а теперь это видно и модели.
                    .nonOpaque()));

    /**
     * Стена майя: охра по извести.
     * <p>
     * Красная, и это не вольность. Норманнская штукатурка кремовая, и
     * первый набросок известковой стены майя от неё почти не отличался —
     * а народ обязан узнаваться с первого взгляда, иначе своя архитектура
     * не имеет смысла. Охрой по извести майя красили в действительности.
     */
    public static final Block OCHRE_PLASTER = register("ochre_plaster",
            plain(MapColor.TERRACOTTA_ORANGE));

    /**
     * Резной камень майя: ступенчатая пирамида с нефритом на вершине.
     * <p>
     * То же место в их архитектуре, какое у норманнов занимает фахверк, —
     * приметный блок, по которому читается стена. Ставится в углах и
     * на видных местах, а не сплошь: рельеф на каждом блоке был бы шумом.
     */
    public static final Block CARVED_STONE = register("carved_stone",
            plain(MapColor.PALE_YELLOW));

    /**
     * Пальмовая кровля.
     * <p>
     * Своя, а не ванильный тюк сена: тюк лежит в декоре у норманнов, и
     * кровля из него читалась бы как сеновал. Держится как трава и звучит
     * как трава — по ней сразу понятно, что это не камень.
     */
    public static final Block THATCH = register("thatch",
            new Block(AbstractBlock.Settings.create()
                    .mapColor(MapColor.YELLOW)
                    .strength(0.5f)
                    .sounds(BlockSoundGroup.GRASS)));


    /**
     * Строительный набор: ступени, плиты и стена из наших же материалов.
     * <p>
     * Заказчик попросил веселья — и первое веселье строителя не в новых
     * механиках, а в том, что <b>из материала можно строить</b>. Куб,
     * у которого нет ступени и плиты, в доме годится на стену и больше
     * ни на что: ни на скат кровли, ни на карниз, ни на крыльцо.
     * <p>
     * И это же — дружба с чужими модами. Ванильная форма ступени понимают
     * все: кто умеет ставить дубовые ступени, тот без единой правки умеет
     * и наши, потому что это тот же блок с той же моделью и тем же тегом.
     * <p>
     * Анонимные наследники здесь не украшение: у ванильных {@code StairsBlock}
     * и {@code WallBlock} защищённые конструкторы, и наследник — законный
     * способ их позвать, не трогая чужой класс.
     */
    public static final Block TIMBER_FRAME_STAIRS = register("timber_frame_stairs",
            stairsOf(TIMBER_FRAME));
    public static final Block TIMBER_FRAME_SLAB = register("timber_frame_slab",
            slabOf(TIMBER_FRAME));

    public static final Block PLASTER_STAIRS = register("plaster_stairs", stairsOf(PLASTER));
    public static final Block PLASTER_SLAB = register("plaster_slab", slabOf(PLASTER));

    public static final Block OCHRE_PLASTER_STAIRS = register("ochre_plaster_stairs",
            stairsOf(OCHRE_PLASTER));
    public static final Block OCHRE_PLASTER_SLAB = register("ochre_plaster_slab",
            slabOf(OCHRE_PLASTER));

    public static final Block CARVED_STONE_STAIRS = register("carved_stone_stairs",
            stairsOf(CARVED_STONE));
    public static final Block CARVED_STONE_SLAB = register("carved_stone_slab",
            slabOf(CARVED_STONE));
    public static final Block CARVED_STONE_WALL = register("carved_stone_wall",
            wallOf(CARVED_STONE));

    public static final Block THATCH_STAIRS = register("thatch_stairs", stairsOf(THATCH));
    public static final Block THATCH_SLAB = register("thatch_slab", slabOf(THATCH));


    /**
     * Мебель: скамья, стол и полка.
     * <p>
     * Заказчик: «добавь мебель». Она не механика и не должна ею быть —
     * дом без стола и лавки выглядит складом, в котором ночуют, и это
     * первое, что бросается в глаза, когда заходишь внутрь.
     * <p>
     * Вся она в теге {@code villagepax:build_decor}: билдер ставит её
     * <b>последней</b>, когда коробка дома готова. Мебель, поставленная
     * посреди стройки, мешала бы ему самому — это уже проверено ковром
     * и котлом.
     * <p>
     * Ставится по взгляду, как сундук: у стола и полки есть лицо, и
     * поворачивать их руками игрок не должен.
     */
    public static final Block BENCH = register("bench", furniture(
            Block.createCuboidShape(0, 0, 4, 16, 16, 12)));
    public static final Block TABLE = register("table", furniture(
            Block.createCuboidShape(0, 0, 0, 16, 16, 16)));
    public static final Block SHELF = register("shelf", furniture(
            Block.createCuboidShape(0, 0, 11, 16, 16, 16)));

    /**
     * Алтарь: единственное место в моде, где говорят с небом.
     * <p>
     * Светится слабо (пять из пятнадцати) и намеренно слабо: алтарь
     * не должен освещать храм — он должен быть в нём заметен. Ровный
     * тёплый отсвет в полумраке читается как «здесь что-то есть»
     * куда вернее, чем полный свет, в котором блок теряется.
     * <p>
     * Прочность как у камня и без взрывоустойчивости сверх обычной:
     * набег ломает стены и ломает алтарь тоже. Неразрушимый блок
     * в моде, где война разоряет здания, выглядел бы читерством —
     * а благосклонность лежит в поселении и сноса не боится.
     */
    public static final Block ALTAR = register("altar",
            new AltarBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.STONE_GRAY)
                    .instrument(Instrument.BASEDRUM)
                    .strength(2.5f, 6.0f)
                    .sounds(BlockSoundGroup.STONE)
                    .luminance(state -> 5)
                    .nonOpaque()));

    /**
     * Бумажный фонарик: праздничный свет — красная бумага на бамбуковых
     * рёбрах. Висит под балкой или стоит на столе, как ванильный фонарь,
     * но бумажный: ломается рукой и гаснет под поршнем.
     */
    public static final Block PAPER_LANTERN = register("paper_lantern",
            new LanternBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.RED)
                    .strength(0.5f)
                    .sounds(BlockSoundGroup.WOOL)
                    .luminance(state -> 15)
                    .nonOpaque()
                    .pistonBehavior(PistonBehavior.DESTROY)));

    /** Лунник: эльфийский цветок, светится и искрит ночью. */
    public static final Block MOONFLOWER = register("moonflower",
            new MoonflowerBlock(AbstractBlock.Settings.copy(Blocks.DANDELION)
                    .luminance(state -> 7)));

    /**
     * Цветочный ящик под окно: доска, земля и три цветка. Ставится
     * лицом к игроку и прижимается к дальней стороне клетки — к стене.
     */
    public static final Block FLOWER_BOX = register("flower_box", furniture(
            Block.createCuboidShape(0, 0, 9, 16, 8, 16)));

    /**
     * Верстовой столб: щелчок — и видно, где ближайшая деревня каждого
     * народа и в какой она стороне. Дерево, как у мебели.
     */
    public static final Block SIGNPOST = register("signpost", new SignpostBlock(
            Block.createCuboidShape(6, 0, 6, 10, 16, 10), AbstractBlock.Settings.create()
                    .mapColor(MapColor.SPRUCE_BROWN)
                    .instrument(Instrument.BASS)
                    .burnable()
                    .strength(1.5f, 3.0f)
                    .sounds(BlockSoundGroup.WOOD)
                    .nonOpaque()));

    // --- диковинки народов (tools/make-wonders.py) ------------------------------
    //
    // Заказчик: «создавай свои блоки, не стесняйся, всю фантазию». У каждой
    // диковинки своё дело — см. Wonders.

    /** Торо — каменный фонарь сада ямато. */
    public static final Block TORO_LANTERN = register("toro_lantern", new Wonders.Toro(
            Block.createCuboidShape(3, 0, 3, 13, 16, 13), stone(MapColor.STONE_GRAY)
                    .luminance(state -> 15)));

    /** Фурин — звенит на ветру; сквозь него проходят. */
    public static final Block WIND_CHIME = register("wind_chime", new Wonders.WindChime(
            AbstractBlock.Settings.create().mapColor(MapColor.WHITE).strength(0.3f)
                    .sounds(BlockSoundGroup.GLASS).noCollision().nonOpaque()
                    .pistonBehavior(PistonBehavior.DESTROY)));

    public static final Block BONSAI = register("bonsai", new FurnitureBlock(
            Block.createCuboidShape(3, 0, 3, 13, 15, 13), AbstractBlock.Settings.create()
                    .mapColor(MapColor.PINK).strength(0.3f).sounds(BlockSoundGroup.CHERRY_WOOD)
                    .nonOpaque().pistonBehavior(PistonBehavior.DESTROY)));

    /** Рунный камень северян: каждый день новая строка саги. */
    public static final Block RUNE_STONE = register("rune_stone", new Wonders.RuneStone(
            Block.createCuboidShape(3, 0, 6, 13, 16, 10), stone(MapColor.STONE_GRAY)
                    .luminance(state -> 4)));

    public static final Block WAR_DRUM = register("war_drum", new Wonders.WarDrum(
            Block.createCuboidShape(1.5, 0, 1.5, 14.5, 12.5, 14.5), AbstractBlock.Settings.create()
                    .mapColor(MapColor.BROWN).instrument(Instrument.BASEDRUM).strength(1.5f)
                    .sounds(BlockSoundGroup.WOOD).burnable().nonOpaque()));

    public static final Block JAGUAR_IDOL = register("jaguar_idol", new Wonders.JaguarIdol(
            Block.createCuboidShape(2, 0, 2, 14, 15, 13), stone(MapColor.PALE_YELLOW)
                    .luminance(state -> 5)));

    public static final Block RAINBOW_FOUNTAIN = register("rainbow_fountain",
            new Wonders.RainbowFountain(Block.createCuboidShape(1, 0, 1, 15, 10.5, 15),
                    stone(MapColor.WHITE)));

    public static final Block CRYSTAL_LAMP = register("crystal_lamp", new Wonders.CrystalLamp(
            Block.createCuboidShape(3, 0, 3, 13, 12, 13), AbstractBlock.Settings.create()
                    .mapColor(MapColor.CYAN).strength(1.5f).sounds(BlockSoundGroup.AMETHYST_CLUSTER)
                    .luminance(state -> 15).nonOpaque()));

    public static final Block FIREFLY_JAR = register("firefly_jar", new Wonders.FireflyJar(
            Block.createCuboidShape(4, 0, 4, 12, 12, 12), AbstractBlock.Settings.create()
                    .mapColor(MapColor.LIME).strength(0.3f).sounds(BlockSoundGroup.GLASS)
                    .luminance(state -> 10).nonOpaque().pistonBehavior(PistonBehavior.DESTROY)));

    public static final Block INCENSE_BURNER = register("incense_burner",
            new Wonders.IncenseBurner(Block.createCuboidShape(4, 0, 4, 12, 10, 12),
                    AbstractBlock.Settings.create().mapColor(MapColor.TERRACOTTA_ORANGE)
                            .strength(2.0f).sounds(BlockSoundGroup.COPPER)
                            .luminance(state -> 3).nonOpaque()));

    public static final Block WEATHERVANE = register("weathervane", new Wonders.Weathervane(
            Block.createCuboidShape(2, 0, 2, 14, 16, 14), AbstractBlock.Settings.create()
                    .mapColor(MapColor.GOLD).strength(2.0f).sounds(BlockSoundGroup.METAL)
                    .nonOpaque()));

    public static final Block MAYA_CALENDAR = register("maya_calendar", new Wonders.MayaCalendar(
            Block.createCuboidShape(1, 0, 5, 15, 16, 11), stone(MapColor.PALE_YELLOW)));

    public static final Block SCARECROW = register("scarecrow", new Wonders.Scarecrow(
            Block.createCuboidShape(1, 0, 5, 15, 16, 11), AbstractBlock.Settings.create()
                    .mapColor(MapColor.YELLOW).strength(0.8f).sounds(BlockSoundGroup.GRASS)
                    .burnable().nonOpaque()));

    public static final Block MARKER_WORKSTATION = registerMarker("marker_workstation");
    public static final Block MARKER_BED = registerMarker("marker_bed");
    public static final Block MARKER_STORAGE = registerMarker("marker_storage");
    public static final Block MARKER_DOOR = registerMarker("marker_door");
    public static final Block MARKER_DECOR = registerMarker("marker_decor");

    private ModBlocks() {
    }

    /**
     * Штукатурка и фахверк держатся как камень, но звучат как камень же.
     * <p>
     * Цвет на карте — обязательный довод, а не украшение. Без него
     * {@code Settings.create()} даёт «прозрачный», и деревня на ванильной
     * карте была дырой: видны тропинки и поля, а домов нет вовсе. Цвет
     * взят ближайший ванильный к самому материалу, чтобы на карте
     * норманнская деревня белела, а майяская краснела — как и вживую.
     */
    private static Block plain(MapColor colour) {
        return new Block(AbstractBlock.Settings.create()
                .mapColor(colour)
                .instrument(Instrument.BASEDRUM)
                .strength(1.5f, 4.0f)
                .sounds(BlockSoundGroup.STONE));
    }

    private static Block registerMarker(String name) {
        return register(name, new Block(AbstractBlock.Settings.create()
                .strength(0.2f)
                .sounds(BlockSoundGroup.WOOL)
                .nonOpaque()));
    }

    /** Каменная диковинка: ломается киркой, звучит камнем, пропускает свет. */
    private static AbstractBlock.Settings stone(MapColor colour) {
        return AbstractBlock.Settings.create()
                .mapColor(colour)
                .instrument(Instrument.BASEDRUM)
                .strength(1.5f, 6.0f)
                .sounds(BlockSoundGroup.STONE)
                .nonOpaque();
    }

    /** Ступени из того же материала: форма ванильная, чтобы её понимали все. */
    private static Block stairsOf(Block base) {
        return new StairsBlock(base.getDefaultState(), AbstractBlock.Settings.copy(base)) {
        };
    }

    private static Block slabOf(Block base) {
        return new SlabBlock(AbstractBlock.Settings.copy(base));
    }

    private static Block wallOf(Block base) {
        return new WallBlock(AbstractBlock.Settings.copy(base)) {
        };
    }

    /**
     * Мебель: дерево, поворот по взгляду и свой след.
     * <p>
     * Не полный куб, поэтому {@code nonOpaque}: иначе игра сочтёт клетку
     * глухой и потушит в комнате свет, а соседние грани перестанут
     * рисоваться.
     */
    private static Block furniture(VoxelShape shape) {
        return new FurnitureBlock(shape, AbstractBlock.Settings.create()
                .mapColor(MapColor.OAK_TAN)
                .instrument(Instrument.BASS)
                .burnable()
                .strength(1.5f, 3.0f)
                .sounds(BlockSoundGroup.WOOD)
                .nonOpaque());
    }

    private static <T extends Block> T register(String name, T block) {
        Identifier id = new Identifier(VillagePax.MOD_ID, name);
        T registered = Registry.register(Registries.BLOCK, id, block);
        REGISTERED.put(id, registered);
        return registered;
    }

    public static Map<Identifier, Block> registered() {
        return Collections.unmodifiableMap(REGISTERED);
    }

    /** Обращение к классу, чтобы сработала статическая инициализация. */
    public static void init() {
        VillagePax.LOGGER.debug("Зарегистрировано блоков: {}", REGISTERED.size());
    }
}
