package com.villagepax.block;

import com.villagepax.VillagePax;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
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

    public static final Block TOWN_HALL = register("town_hall",
            new Block(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS)
                    .strength(4.0f, 12.0f)
                    .sounds(BlockSoundGroup.WOOD)));

    public static final Block MARKER_WORKSTATION = registerMarker("marker_workstation");
    public static final Block MARKER_BED = registerMarker("marker_bed");
    public static final Block MARKER_STORAGE = registerMarker("marker_storage");
    public static final Block MARKER_DOOR = registerMarker("marker_door");
    public static final Block MARKER_DECOR = registerMarker("marker_decor");

    private ModBlocks() {
    }

    private static Block registerMarker(String name) {
        return register(name, new Block(AbstractBlock.Settings.create()
                .strength(0.2f)
                .sounds(BlockSoundGroup.WOOL)
                .nonOpaque()));
    }

    private static Block register(String name, Block block) {
        Identifier id = new Identifier(VillagePax.MOD_ID, name);
        Block registered = Registry.register(Registries.BLOCK, id, block);
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
