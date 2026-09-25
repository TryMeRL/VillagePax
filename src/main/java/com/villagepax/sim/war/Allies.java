package com.villagepax.sim.war;

import com.villagepax.VillagePax;
import com.villagepax.core.war.WarParty;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.entity.Looks;
import com.villagepax.sim.Ground;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.diplomacy.Alliance;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Подмога: союзная деревня шлёт своих бойцов на чужой набег.
 * <p>
 * Это и есть всё содержание союза. Скидку, подарки и доброе слово мод
 * умел и раньше — их даёт лестница доверия; союз даёт единственное, чего
 * за доверие не купишь: <b>людей, готовых умереть за твою колонию</b>.
 * <p>
 * Устроено тем же способом, что и сам набег, и по той же причине: между
 * союзной деревней и колонией лежат сотни незагруженных чанков, и вести
 * по ним живых мобов нельзя ни дёшево, ни честно. Союзники — такие же
 * куклы, как налётчики, и приходят к тому же отряду, только с другой
 * стороны.
 * <p>
 * <b>Их меньше, чем налётчиков.</b> Помощь не должна отменять набег:
 * игрок, у которого союзники дерутся вместо него, перестаёт быть
 * участником своей войны. Двое на четверых — это плечо, а не замена.
 * <p>
 * <b>Их смерть ничего не стоит деревне-обидчице</b>: траур считают
 * по своим павшим, а союзник ей не свой. Зато союзная деревня теряет
 * людей по-настоящему — их тела не возвращаются.
 */
public final class Allies {

    private Allies() {
    }

    /**
     * Позвать союзников к воротам осаждённой колонии.
     * <p>
     * Зовётся оттуда же, откуда встают телами налётчики, — когда отряд
     * дошёл и чанк загружен. Раньше звать бессмысленно: ставить тела
     * в выгруженный чанк нельзя, а держать их в данных — значит завести
     * второй отряд, который ничем, кроме знака, от осадного не отличается.
     */
    public static void callUp(ServerWorld world, SettlementManager manager, Settlement colony,
                              WarParty party) {
        UUID owner = colony.owner().player().orElse(null);
        if (owner == null) {
            return;
        }

        List<Settlement> allies = new ArrayList<>();
        for (Settlement other : manager.all()) {
            if (other.owner().isAutonomous() && other.isAllyOf(owner)
                    && !other.id().equals(party.home())) {
                // Своих против своих не посылают: деревня, приславшая
                // отряд, союзником быть и не может — доверие у неё давно
                // ниже дружбы, — но проверить дешевле, чем однажды
                // получить бойца, дерущегося сам с собой.
                allies.add(other);
            }
        }
        if (allies.isEmpty()) {
            return;
        }

        int already = defendersOf(world, party).size();
        int want = Math.min(allies.size() * Alliance.FIGHTERS, party.fighters());
        if (already >= want) {
            return;
        }

        for (int number = already; number < want; number++) {
            Settlement from = allies.get(number % allies.size());
            // Рядом, но не вплотную: союзники встают плечом к плечу
            // с обороняющимися, а не внутри отряда налётчиков.
            BlockPos where = Ground.spotNear(world, party.musters(), 2, 4);
            CitizenEntity fighter = CitizenSpawner.spawnPuppet(world,
                    where == null ? party.musters() : where);
            if (fighter == null) {
                return;
            }

            fighter.linkDefence(colony.id(), party.id());
            fighter.setLook(Looks.puppet(from.culture(), "guard"));
            fighter.setCustomName(Text.translatable("villagepax.ally.fighter", from.name()));
            fighter.setCustomNameVisible(true);
            fighter.equipStack(EquipmentSlot.MAINHAND,
                    com.villagepax.item.gear.ModGear.armsFor(from.culture()));
        }

        if (already == 0) {
            tell(world, colony, Text.translatable("villagepax.ally.here",
                    allies.get(0).name()).formatted(Formatting.GREEN));
            VillagePax.LOGGER.info("Союзники {} пришли к {}: {} бойцов",
                    allies.get(0).name(), colony.name(), want);
        }
    }

    /** Союзники этого отряда, какие есть в мире. */
    public static List<CitizenEntity> defendersOf(ServerWorld world, WarParty party) {
        Box around = new Box(party.musters()).expand(64);
        return new ArrayList<>(world.getEntitiesByClass(CitizenEntity.class, around,
                alive -> alive.isDefender() && party.id().equals(alive.raidId())));
    }

    /** Осада кончилась — союзники расходятся по домам. */
    public static void dismiss(ServerWorld world, WarParty party) {
        defendersOf(world, party).forEach(CitizenEntity::discard);
    }

    private static void tell(ServerWorld world, Settlement colony, Text message) {
        colony.owner().player()
                .map(owner -> world.getServer().getPlayerManager().getPlayer(owner))
                .ifPresent(player -> player.sendMessage(message, false));
    }
}
