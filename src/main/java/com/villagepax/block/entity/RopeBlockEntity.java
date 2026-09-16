package com.villagepax.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/**
 * Бельевая верёвка: что на ней висит.
 * <p>
 * Решение заказчика: «пусть это будет просто верёвка, но на которую можно
 * будет вешать кожаные вещи, кожу и тканевую одежду». До этого бельё было
 * картинкой — двумя нарисованными рубахами, одинаковыми у всех и ни на что
 * не годными. Теперь на верёвке висит <b>то, что повесили</b>, и его
 * видно: это первый в моде предмет обстановки, с которым можно
 * обращаться, а не только смотреть.
 * <p>
 * Четыре места, а не сундук: верёвка — это верёвка. Складом она быть
 * не должна, иначе в неё начнут прятать алмазы, а колония получит
 * бесплатное хранилище в обход склада.
 * <p>
 * Содержимое уезжает на клиент целиком: рисовать его должен он, а
 * четыре стопки — это несколько байт на блок. Тот же приём, что у
 * ванильной витрины.
 */
public class RopeBlockEntity extends BlockEntity {

    /** Сколько вещей помещается на верёвке. */
    public static final int SIZE = 4;

    private static final String HUNG_KEY = "Hung";

    private final DefaultedList<ItemStack> hung = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

    public RopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROPE, pos, state);
    }

    public DefaultedList<ItemStack> hung() {
        return hung;
    }

    /** Свободное место, если оно есть. */
    public int freeSpot() {
        for (int slot = 0; slot < SIZE; slot++) {
            if (hung.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /** Последнее повешенное: его и снимают первым. */
    public int lastHung() {
        for (int slot = SIZE - 1; slot >= 0; slot--) {
            if (!hung.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Повесить одну штуку.
     * <p>
     * Ровно одну: вешают по вещи, а не стопкой. Иначе на верёвке висела бы
     * «кожа ×64», и весь смысл — видеть, что висит, — пропал бы.
     */
    public boolean hang(ItemStack stack) {
        int spot = freeSpot();
        if (spot < 0 || stack.isEmpty()) {
            return false;
        }
        hung.set(spot, stack.split(1));
        sync();
        return true;
    }

    /** Снять последнее. Пусто — снимать нечего. */
    public ItemStack takeDown() {
        int spot = lastHung();
        if (spot < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = hung.get(spot);
        hung.set(spot, ItemStack.EMPTY);
        sync();
        return taken;
    }

    public boolean isEmpty() {
        return lastHung() < 0;
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isClient()) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        hung.clear();
        Inventories.readNbt(nbt.getCompound(HUNG_KEY), hung);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtCompound items = new NbtCompound();
        Inventories.writeNbt(items, hung);
        nbt.put(HUNG_KEY, items);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
