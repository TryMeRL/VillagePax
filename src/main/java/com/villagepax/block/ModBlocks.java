package com.villagepax.block;

import com.villagepax.VillagePax;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.WallBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
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
    public static final Block TIMBER_FRAME = register("timber_frame", plain());

    /** Та же штукатурка без балок — для простых стен и для внутренностей. */
    public static final Block PLASTER = register("plaster", plain());

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
    public static final Block OCHRE_PLASTER = register("ochre_plaster", plain());

    /**
     * Резной камень майя: ступенчатая пирамида с нефритом на вершине.
     * <p>
     * То же место в их архитектуре, какое у норманнов занимает фахверк, —
     * приметный блок, по которому читается стена. Ставится в углах и
     * на видных местах, а не сплошь: рельеф на каждом блоке был бы шумом.
     */
    public static final Block CARVED_STONE = register("carved_stone", plain());

    /**
     * Пальмовая кровля.
     * <p>
     * Своя, а не ванильный тюк сена: тюк лежит в декоре у норманнов, и
     * кровля из него читалась бы как сеновал. Держится как трава и звучит
     * как трава — по ней сразу понятно, что это не камень.
     */
    public static final Block THATCH = register("thatch",
            new Block(AbstractBlock.Settings.create()
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

    public static final Block MARKER_WORKSTATION = registerMarker("marker_workstation");
    public static final Block MARKER_BED = registerMarker("marker_bed");
    public static final Block MARKER_STORAGE = registerMarker("marker_storage");
    public static final Block MARKER_DOOR = registerMarker("marker_door");
    public static final Block MARKER_DECOR = registerMarker("marker_decor");

    private ModBlocks() {
    }

    /** Штукатурка и фахверк держатся как камень, но звучат как камень же. */
    private static Block plain() {
        return new Block(AbstractBlock.Settings.create()
                .strength(1.5f, 4.0f)
                .sounds(BlockSoundGroup.STONE));
    }

    private static Block registerMarker(String name) {
        return register(name, new Block(AbstractBlock.Settings.create()
                .strength(0.2f)
                .sounds(BlockSoundGroup.WOOL)
                .nonOpaque()));
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
