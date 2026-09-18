package com.villagepax.sim.work;

import com.villagepax.VillagePax;
import com.villagepax.core.building.BuildingType;
import com.villagepax.core.building.BuildingTypes;
import com.villagepax.sim.Building;
import com.villagepax.sim.Sounds;
import com.villagepax.sim.Warehouse;
import com.villagepax.sim.build.BuildJob;
import com.villagepax.sim.build.MarkerKind;
import com.villagepax.sim.build.Schematic;
import com.villagepax.sim.build.SchematicLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ремесленник: превращает припасы склада в то, чего киркой не добыть.
 * <p>
 * Ради этой работы всё и затевалось. До неё колония умела кормить себя
 * и строить дома — то есть давала игроку ровно то, что он и сам добудет
 * руками, только медленнее. Заказчик сказал об этом прямо: играть скучно,
 * зацепиться не за что. <b>Цепляет то, чего иначе не получить.</b>
 * <p>
 * Что именно делают, решает <b>мастерская</b>, а не ремесло: пивовар
 * норманнов варит эль, знахарь майя толчёт какао, а работа у них одна
 * и та же — стоять у котла. Рецепты лежат в типе здания, в датапаке,
 * и новый народ добавляет своё зелье, не трогая ни строчки кода.
 * <p>
 * Работа идёт <b>у станка и из склада</b>: житель берёт припасы со склада
 * колонии, идёт к своему котлу и кладёт готовое обратно. Ни то ни другое
 * не мелочь: без склада ремесло сделалось бы бесплатным, а без станка —
 * невидимым, а работа должна быть видна.
 */
public final class CraftJob implements Job {

    public static final Identifier LOGIC = new Identifier(VillagePax.MOD_ID, "craft");

    /** Ремесло пивовара: его даёт датапак, но искать мастерскую надо по имени. */
    public static final Identifier BREWER = new Identifier(VillagePax.MOD_ID, "brewer");

    /** У станка — значит рядом: та же мерка, что у грядки фермера. */
    private static final double REACH = 3.0;

    @Override
    public Identifier logic() {
        return LOGIC;
    }

    @Override
    public Optional<BlockPos> tick(WorkContext context) {
        Building shop = Workplaces.of(context.settlement(), context.citizen()).orElse(null);
        if (shop == null) {
            context.goIdle();
            return Optional.empty();
        }

        BuildingType type = BuildingTypes.all().get(shop.type());
        if (type == null || type.crafts().isEmpty()) {
            // Мастерская без рецептов — это просто дом: работать не над чем.
            context.goIdle();
            return Optional.empty();
        }

        BuildingType.Craft craft = affordable(type.crafts(), context.warehouse()).orElse(null);
        if (craft == null) {
            // Припасов нет. Это не сбой, а ответ: принеси зерна — будет эль.
            context.goIdle();
            return Optional.empty();
        }

        BlockPos bench = benchOf(shop).orElse(shop.anchor());
        if (context.state().isIdle()) {
            context.setState(JobState.startAt(shop.id(), JobState.Phase.TO_SITE));
        }
        context.hold(new ItemStack(Items.BOWL));

        if (context.position().squaredDistanceTo(Vec3d.ofCenter(bench)) <= REACH * REACH) {
            context.swing();
            work(context, craft);
        }
        return Optional.of(bench);
    }

    /**
     * Первый рецепт, на который хватает припасов.
     * <p>
     * Первый, а не выгоднейший: порядок в датапаке и есть порядок
     * предпочтения, и автор списка вправе им распоряжаться. Выбирать
     * за него «самое дорогое» значило бы, что порядок в файле ничего
     * не значит.
     */
    public static Optional<BuildingType.Craft> affordable(List<BuildingType.Craft> crafts,
                                                          Warehouse warehouse) {
        for (BuildingType.Craft craft : crafts) {
            if (hasAll(craft, warehouse)) {
                return Optional.of(craft);
            }
        }
        return Optional.empty();
    }

    private static boolean hasAll(BuildingType.Craft craft, Warehouse warehouse) {
        for (Map.Entry<Identifier, Integer> need : craft.from().entrySet()) {
            Item item = Registries.ITEM.get(need.getKey());
            if (item == Items.AIR || warehouse.count(item) < need.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Одна работа: припасы со склада, готовое на склад.
     * <p>
     * Сперва проверка, потом расход: житель, у которого забрали зерно
     * и не дали эля, — это пропавшие припасы, и заметит их игрок не скоро.
     */
    private static void work(WorkContext context, BuildingType.Craft craft) {
        Warehouse warehouse = context.warehouse();
        Item made = Registries.ITEM.get(craft.to());
        if (made == Items.AIR || !hasAll(craft, warehouse)) {
            return;
        }
        if (!warehouse.room(made, craft.count())) {
            // Склад полон: забирать припасы было бы грабежом.
            return;
        }

        for (Map.Entry<Identifier, Integer> need : craft.from().entrySet()) {
            warehouse.take(Registries.ITEM.get(need.getKey()), need.getValue());
        }
        warehouse.add(new ItemStack(made, craft.count()));

        // Слышно и видно. Работа, о которой знает только счётчик на складе,
        // с точки зрения игрока не происходит вовсе — это несущее правило
        // мода, и пар над котлом стоит ровно столько же, сколько звук.
        BlockPos bench = context.body().getBlockPos();
        Sounds.crafted(context.world(), bench);
        context.world().spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                bench.getX() + 0.5, bench.getY() + 1.1, bench.getZ() + 0.5,
                6, 0.2, 0.1, 0.2, 0.01);
        context.world().spawnParticles(ParticleTypes.SPLASH,
                bench.getX() + 0.5, bench.getY() + 1.0, bench.getZ() + 0.5,
                8, 0.25, 0.05, 0.25, 0.0);
    }

    /** Где в мастерской стоит станок: по метке схемы, иначе — якорь здания. */
    private static Optional<BlockPos> benchOf(Building shop) {
        Schematic schematic = SchematicLoader.get(BuildJob.schematicId(shop)).orElse(null);
        if (schematic == null) {
            return Optional.empty();
        }
        List<BlockPos> benches = BuildJob.pointsOfInterest(shop, schematic, MarkerKind.WORKSTATION);
        return benches.isEmpty() ? Optional.empty() : Optional.of(benches.get(0));
    }
}
