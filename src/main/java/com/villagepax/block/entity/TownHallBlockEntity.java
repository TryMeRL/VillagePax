package com.villagepax.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

/**
 * Ратуша: связь блока с поселением и стартовое хранилище колонии.
 * <p>
 * Хранилище здесь потому, что иначе получается курица и яйцо: чтобы построить
 * склад, нужен склад. Игрок кладёт материалы в ратушу рукой, и билдеру уже
 * есть из чего строить. Экран — ванильный сундучный, поэтому своего
 * интерфейса и клиентского кода не нужно вовсе.
 * <p>
 * Поселение ищется по идентификатору, а не по координатам: границы соседних
 * поселений могут почти касаться, а ратуша станет ещё и точкой входа
 * в интерфейс колонии, где ошибиться нельзя.
 */
public class TownHallBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

    /** Три ряда, как у сундука: столько же, сколько показывает ванильный экран. */
    public static final int SIZE = 27;

    private static final String SETTLEMENT_KEY = "Settlement";

    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

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
        stacks.clear();
        Inventories.readNbt(nbt, stacks);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (settlementId != null) {
            nbt.putUuid(SETTLEMENT_KEY, settlementId);
        }
        Inventories.writeNbt(nbt, stacks);
    }

    // --- хранилище ---

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return stacks.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack removed = Inventories.splitStack(stacks, slot, amount);
        if (!removed.isEmpty()) {
            markDirty();
        }
        return removed;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(stacks, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        stacks.clear();
        markDirty();
    }

    // --- экран ---

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.villagepax.town_hall");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
    }
}
