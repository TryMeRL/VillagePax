package com.villagepax.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

/**
 * Связывает блок ратуши с конкретным поселением.
 * <p>
 * Искать поселение по координатам было бы неточно: границы соседних поселений
 * могут почти касаться, а позже ратуша станет ещё и точкой входа в интерфейс
 * колонии, где ошибиться нельзя.
 */
public class TownHallBlockEntity extends BlockEntity {

    private static final String SETTLEMENT_KEY = "Settlement";

    private UUID settlementId;

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOWN_HALL, pos, state);
    }

    public Optional<UUID> settlementId() {
        return Optional.ofNullable(settlementId);
    }

    public void setSettlementId(UUID settlementId) {
        this.settlementId = settlementId;
        markDirty();
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        settlementId = nbt.containsUuid(SETTLEMENT_KEY) ? nbt.getUuid(SETTLEMENT_KEY) : null;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (settlementId != null) {
            nbt.putUuid(SETTLEMENT_KEY, settlementId);
        }
    }
}
