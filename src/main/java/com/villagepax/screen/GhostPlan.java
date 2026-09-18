package com.villagepax.screen;

import com.villagepax.sim.build.BuildStep;
import com.villagepax.sim.build.Schematic;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Схема в том виде, в каком её рисует голограмма.
 * <p>
 * Отдельная запись, а не сама {@code Schematic}, потому что клиент схем
 * <b>не видит</b>: они лежат в датапаке сервера, а датапак игроку не едет.
 * В одиночной игре это незаметно — сервер и клиент в одном процессе, — а на
 * выделенном сервере призрак был бы пустым. Поэтому план приезжает по сети
 * один раз на выбранное здание.
 * <p>
 * Блоки едут числовым состоянием ({@code Block.getRawIdFromState}) — тем же
 * способом, каким ваниль отправляет блоки в пакетах чанков. Схема ратуши
 * в двести девяносто четыре блока укладывается в пару килобайт.
 * <p>
 * Расчистка в план призрака не входит: игрок выбирает, как встанет здание,
 * а не что будет снесено.
 */
public record GhostPlan(Identifier schematic, Vec3i size, List<Ghost> blocks) {

    /** Один блок призрака: место в схеме и что там стоит. */
    public record Ghost(BlockPos pos, BlockState state) {
    }

    /** Сколько блоков схемы вообще имеет смысл гнать по сети. */
    public static final int MAX_BLOCKS = 8192;

    public GhostPlan {
        blocks = List.copyOf(blocks);
    }

    /**
     * Собрать призрак схемы.
     * <p>
     * Живёт здесь, а не в сетевом слое, ровно по той же причине, что и
     * проверки заказа в {@code BuildOrders}: пакет — только способ
     * доставки, а <b>что именно видит игрок</b> — свойство схемы. Заодно
     * это можно спросить без игрока и без сети, то есть проверить.
     * <p>
     * Расчистка в призрак не входит: игрок выбирает, как встанет здание,
     * а не что будет снесено. С расчисткой подхода это стало особенно
     * важно — иначе призрак торчал бы из здания прозрачными клетками
     * там, где билдер всего лишь срубит дерево.
     */
    public static GhostPlan of(Identifier schematicId, Schematic schematic) {
        List<Ghost> blocks = new ArrayList<>();
        for (BuildStep step : schematic.plan().steps()) {
            if (step.placesBlock() && blocks.size() < MAX_BLOCKS) {
                blocks.add(new Ghost(step.pos(), schematic.blockAt(step.paletteIndex())));
            }
        }
        return new GhostPlan(schematicId, schematic.size(), blocks);
    }

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(schematic);
        buf.writeVarInt(size.getX());
        buf.writeVarInt(size.getY());
        buf.writeVarInt(size.getZ());

        buf.writeVarInt(blocks.size());
        for (Ghost ghost : blocks) {
            buf.writeBlockPos(ghost.pos());
            buf.writeVarInt(Block.getRawIdFromState(ghost.state()));
        }
    }

    public static GhostPlan read(PacketByteBuf buf) {
        Identifier schematic = buf.readIdentifier();
        Vec3i size = new Vec3i(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());

        int count = Math.min(buf.readVarInt(), MAX_BLOCKS);
        List<Ghost> blocks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            BlockPos pos = buf.readBlockPos();
            blocks.add(new Ghost(pos, Block.getStateFromRawId(buf.readVarInt())));
        }
        return new GhostPlan(schematic, size, blocks);
    }
}
