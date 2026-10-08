package com.villagepax.sim;

import com.villagepax.core.building.BuildingType;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Деревня строит то, чего ей не хватает, — а не лавку за лавкой.
 */
class VillagePlannerTest {

    private static final Identifier MAYA = new Identifier("villagepax", "maya");
    private static final Identifier HALL = type("town_hall");
    private static final Identifier STALL = type("market_stall");
    private static final Identifier SHRINE = type("shrine");
    private static final Identifier HOUSE = type("house");
    private static final Identifier FARM = type("farm");
    private static final Identifier COTTAGE = type("cottage");
    private static final Identifier TOWNHOUSE = type("townhouse");
    private static final List<Identifier> LIST = List.of(HALL, STALL, SHRINE, HOUSE, FARM);

    private static final Map<Identifier, BuildingType> TYPES = Map.of(
            HALL, kind(BuildingType.Role.TOWN_HALL, SettlementLevel.HAMLET),
            STALL, kind(BuildingType.Role.WORKPLACE, SettlementLevel.HAMLET),
            SHRINE, kind(BuildingType.Role.PLAIN, SettlementLevel.VILLAGE),
            HOUSE, kind(BuildingType.Role.HOME, SettlementLevel.HAMLET),
            FARM, kind(BuildingType.Role.WORKPLACE, SettlementLevel.HAMLET),
            COTTAGE, kind(BuildingType.Role.HOME, SettlementLevel.HAMLET),
            TOWNHOUSE, kind(BuildingType.Role.HOME, SettlementLevel.TOWN));

    private static Identifier type(String name) {
        return new Identifier("villagepax", "maya/" + name);
    }

    private static BuildingType kind(BuildingType.Role role, SettlementLevel level) {
        return new BuildingType("", role, Optional.empty(), false, level, List.of());
    }

    private static Settlement village(int people, Identifier... buildings) {
        Settlement village = Settlement.found(MAYA, Owner.AUTONOMOUS, "Коба", BlockPos.ORIGIN);
        for (Identifier type : buildings) {
            village.addBuilding(new Building(UUID.randomUUID(), type, 1, BlockPos.ORIGIN,
                    BlockRotation.NONE, BuildProgress.DONE, List.of()));
        }
        for (int i = 0; i < people; i++) {
            village.addCitizen(Citizen.newborn("Имя" + i, "", MAYA, Gender.MALE));
        }
        return village;
    }

    private static List<Identifier> wishes(Settlement village, int beds) {
        return VillagePlanner.wishes(village, LIST, beds,
                type -> Optional.ofNullable(TYPES.get(type)), FARM::equals);
    }

    @Test
    void aVillageWithNowhereToSleepBuildsAHouseFirst() {
        Settlement village = village(3, HALL, STALL, FARM);
        assertEquals(HOUSE, wishes(village, 2).get(0));
    }

    /** Прежняя беда: лавка первой в списке, и деревня ставила лавку за лавкой. */
    @Test
    void aSecondStallIsNeverWished() {
        Settlement village = village(2, HALL, STALL, HOUSE, FARM);
        List<Identifier> wishes = wishes(village, 8);
        assertFalse(wishes.contains(STALL), "вторая лавка: " + wishes);
        assertFalse(wishes.contains(HALL), "вторая ратуша: " + wishes);
    }

    @Test
    void aGrowingVillageSowsMoreFields() {
        Settlement village = village(6, HALL, STALL, HOUSE, HOUSE, HOUSE, HOUSE, FARM);
        assertEquals(FARM, wishes(village, 10).get(0));
    }

    @Test
    void theShrineWaitsForItsLevel() {
        Settlement village = village(2, HALL, STALL, HOUSE, FARM);
        assertFalse(wishes(village, 8).contains(SHRINE));
        village.setLevel(SettlementLevel.VILLAGE);
        assertTrue(wishes(village, 8).contains(SHRINE));
    }

    /** Прежняя беда: деревня без притока ставила дом за домом, пока была земля. */
    @Test
    void spareHousesStopAtTheVillageLimit() {
        Settlement village = village(2, HALL, STALL, HOUSE, FARM);
        int limit = village.maxCitizens() + VillagePlanner.SPARE_BEDS;
        assertTrue(wishes(village, limit - 1).contains(HOUSE));
        assertFalse(wishes(village, limit).contains(HOUSE), "дом сверх предела");
    }

    /** Двух видов дома — строится тот, которого меньше: улица не из одинаковых коробок. */
    @Test
    void homesTakeTurns() {
        List<Identifier> list = List.of(HALL, HOUSE, COTTAGE, FARM);
        Settlement village = village(2, HALL, HOUSE, HOUSE, FARM);
        List<Identifier> wishes = VillagePlanner.wishes(village, list, 1,
                type -> Optional.ofNullable(TYPES.get(type)), FARM::equals);
        assertEquals(COTTAGE, wishes.get(0), "второй вид дома не дождался очереди: " + wishes);
    }

    /** В городе первыми — городские дома; в хуторе их нет вовсе. */
    @Test
    void aTownBuildsTownhousesFirst() {
        List<Identifier> list = List.of(HALL, HOUSE, TOWNHOUSE, FARM);
        Settlement village = village(2, HALL, FARM);
        assertFalse(VillagePlanner.wishes(village, list, 1,
                type -> Optional.ofNullable(TYPES.get(type)), FARM::equals).contains(TOWNHOUSE));
        village.setLevel(SettlementLevel.TOWN);
        assertEquals(TOWNHOUSE, VillagePlanner.wishes(village, list, 1,
                type -> Optional.ofNullable(TYPES.get(type)), FARM::equals).get(0));
    }

    /** Ратуша растёт, когда деревне тесно: жителей под предел, всем есть где спать и что есть. */
    @Test
    void aCrowdedVillageRaisesItsHall() {
        Settlement village = village(6, HALL, STALL, HOUSE, HOUSE, FARM, FARM);
        assertTrue(VillagePlanner.readyToGrow(village, 6, 2));
        assertFalse(VillagePlanner.readyToGrow(village, 5, 2), "растёт, а спать негде");
        assertFalse(VillagePlanner.readyToGrow(village, 6, 1), "растёт, а есть нечего");
        assertFalse(VillagePlanner.readyToGrow(village(5, HALL, FARM), 6, 1),
                "растёт одними основателями");
        Settlement capital = village(63, HALL, FARM);
        capital.setLevel(SettlementLevel.CAPITAL);
        assertFalse(VillagePlanner.readyToGrow(capital, 80, 20), "выше столицы");
    }

    /**
     * Призвание: деревня стражей ставит башню раньше пивоварни, деревня
     * мастеров — наоборот; и у всякой деревни оно своё и навсегда.
     */
    @Test
    void eachVillageFollowsItsCalling() {
        Identifier tower = type("watchtower");
        Identifier brewery = type("brewery");
        Map<Identifier, BuildingType> kinds = Map.of(
                tower, new BuildingType("", BuildingType.Role.WORKPLACE,
                        Optional.of(new Identifier("villagepax", "guard")), false, SettlementLevel.HAMLET, List.of()),
                brewery, new BuildingType("", BuildingType.Role.WORKPLACE,
                        Optional.of(new Identifier("villagepax", "brewer")), false, SettlementLevel.HAMLET, List.of()));
        java.util.Set<Calling> seen = java.util.EnumSet.noneOf(Calling.class);
        for (int i = 0; i < 200; i++) {
            Settlement village = village(3);
            Calling calling = Calling.of(village.id());
            seen.add(calling);
            assertEquals(calling, Calling.of(village.id()), "призвание сменилось");
            List<Identifier> wishes = VillagePlanner.wishes(village, List.of(brewery, tower), 99,
                    type -> Optional.ofNullable(kinds.get(type)), type -> false);
            if (calling == Calling.WARDENS) {
                assertEquals(tower, wishes.get(0), "стражи ставят башню первой: " + wishes);
            }
            if (calling == Calling.CRAFTERS) {
                assertEquals(brewery, wishes.get(0), "мастера ставят пивоварню первой: " + wishes);
            }
        }
        assertEquals(java.util.EnumSet.allOf(Calling.class), seen, "не все призвания встречаются");
    }
}
