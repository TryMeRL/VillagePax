package com.villagepax.sim.festival;

import com.villagepax.sim.Building;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Ярмарка и её места в мире.
 *
 * @param building ярмарка как здание поселения
 * @param heart    сердце праздника: нижний блок столба, барабан, костёр, горн, деревце
 * @param pen      клетки загона, по которым бегают зверьки ловли
 * @param shooting где встаёт стрелок
 * @param targets  мишени стрелища
 * @param tables   столы под пироги — пирог ставится над столом
 * @param counter  место затейника у прилавка
 * @param area     след ярмарки
 */
public record Fair(Building building, BlockPos heart, List<BlockPos> pen, List<BlockPos> shooting,
                   List<BlockPos> targets, List<BlockPos> tables, BlockPos counter, Box area) {

    /** Высота, на которой стоят: над полом ярмарки. */
    public int standingY() {
        return building.anchor().getY() + 1;
    }
}
