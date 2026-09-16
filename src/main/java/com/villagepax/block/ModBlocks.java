package com.villagepax.block;

import com.villagepax.VillagePax;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
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
            new PillarBlock(AbstractBlock.Settings.copy(Blocks.OAK_LOG)));

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

    /** Мешок снеди. Стоит у склада и на ферме — знак, что колония кормится. */
    public static final Block GRAIN_SACK = register("grain_sack",
            new Block(AbstractBlock.Settings.create()
                    .strength(0.6f)
                    .sounds(BlockSoundGroup.WOOL)));

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
