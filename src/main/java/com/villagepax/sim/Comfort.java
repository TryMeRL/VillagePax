package com.villagepax.sim;

import com.villagepax.core.ModTags;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.BuildSite;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

/**
 * Уют: за что житель любит свой дом.
 * <p>
 * Убранство в моде было с самого начала и <b>не значило ничего</b>:
 * фонарь, ковёр, бельё на верёвке и поленница ставились билдером,
 * стояли и никак не влияли на жизнь. Заказчик сказал об этом прямо:
 * пусть декор что-то делает.
 * <p>
 * Делает он ровно то, чего от него ждут: дом, в котором есть чем
 * порадовать глаз, поднимает жителю настроение. Это та же мысль, на
 * которой стоит MineColonies — обжитая колония работает лучше голой, —
 * и она здесь единственная, ради которой стоит заводить правило: игроку
 * даётся причина обустраивать дома руками, а не только ставить коробки.
 * <p>
 * Считается <b>по следу дома</b> и по тегу убранства: что автор датапака
 * счёл убранством, то и греет. Отдельного списка в коде нет намеренно —
 * он бы разошёлся с тегом в первый же день.
 */
public final class Comfort {

    /** Сколько блоков убранства считаются за одно очко настроения. */
    public static final int PER_POINT = 3;

    /** Выше этого дом не греет: иначе комната в фонарях решала бы всё. */
    public static final int MOST = 3;

    private Comfort() {
    }

    /** Насколько уютен дом этого жителя. Без дома уюта нет. */
    public static int of(ServerWorld world, Settlement colony, Citizen citizen) {
        Building home = citizen.home().flatMap(colony::building).orElse(null);
        if (home == null || !home.isOperational()) {
            return 0;
        }
        return of(world, home);
    }

    /**
     * Сколько очков уюта даёт это здание.
     * <p>
     * Только загруженные чанки: спрашивать блоки в выгруженном мире
     * нельзя — это правило мода, и уют не повод его нарушать. Дом,
     * которого не видно, просто не считается: жителя в нём всё равно нет.
     */
    public static int of(ServerWorld world, Building home) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(home)).orElse(null);
        if (schematic == null || !world.isChunkLoaded(home.anchor())) {
            return 0;
        }

        Vec3i size = BuildSite.rotatedSize(schematic.size(), home.rotation());
        int found = 0;
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dy = 0; dy < size.getY(); dy++) {
                for (int dz = 0; dz < size.getZ(); dz++) {
                    BlockPos at = home.anchor().add(dx, dy, dz);
                    if (world.getBlockState(at).isIn(ModTags.BUILD_DECOR)) {
                        found++;
                    }
                }
            }
        }
        return Math.min(MOST, found / PER_POINT);
    }
}
