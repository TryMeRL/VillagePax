package com.villagepax.gametest;

import com.villagepax.core.building.BuildingTypes;
import com.villagepax.core.culture.CultureManager;
import com.villagepax.sim.Building;
import com.villagepax.sim.festival.Fair;
import com.villagepax.sim.festival.Fairs;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Праздник на ярмарке: места праздника, затейник, день праздника,
 * состязания и призы.
 */
public class FestivalTests extends GameTestSupport {

    /**
     * У каждого народа есть ярмарка, и у ярмарки — все места праздника.
     * <p>
     * Места спрашиваются у схемы, а не у мира, поэтому проверка обходится
     * без единого блока: {@link Fairs} читает сердце, загон, черту, мишени
     * и столы по плану стройки. Числа — решение по игре: загон не меньше
     * пяти на пять (трём зверькам есть где удирать), ровно три мишени
     * (ближняя, средняя, дальняя), хоть одна черта и один стол.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "festival")
    public void everyPeopleHasAFairWithAllItsPlaces(TestContext context) {
        List<String> complaints = new ArrayList<>();
        for (Identifier culture : CultureManager.all().keySet()) {
            Identifier type = new Identifier(culture.getNamespace(), culture.getPath() + "/fairground");
            if (BuildingTypes.get(type).isEmpty()) {
                complaints.add(culture + ": нет ярмарки");
                continue;
            }
            Building site = Building.planned(type, BlockPos.ORIGIN, BlockRotation.NONE);
            Fair fair = Fairs.of(site).orElse(null);
            if (fair == null) {
                complaints.add(culture + ": у ярмарки нет сердца или прилавка");
                continue;
            }
            if (fair.pen().size() < 25) {
                complaints.add(culture + ": загон меньше 5x5 — клеток " + fair.pen().size());
            }
            if (fair.targets().size() != 3) {
                complaints.add(culture + ": мишеней " + fair.targets().size());
            }
            if (fair.shooting().isEmpty()) {
                complaints.add(culture + ": негде встать стрелку");
            }
            if (fair.tables().isEmpty()) {
                complaints.add(culture + ": некуда ставить пироги");
            }
            if (!fair.area().contains(fair.heart().toCenterPos())) {
                complaints.add(culture + ": сердце праздника вне ярмарки");
            }
        }
        if (!complaints.isEmpty()) {
            context.throwGameTestException("Ярмарки народов:\n  " + String.join("\n  ", complaints));
        }
        context.complete();
    }
}
