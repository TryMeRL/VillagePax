package com.villagepax.block.festival;

import com.villagepax.block.entity.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Надпись на кубке: что выиграно, где, когда и кем.
 * <p>
 * Хранится ключами словаря и простыми строками, а не готовым текстом:
 * кубок, выигранный на русском сервере, читается по-английски у того,
 * кто играет по-английски, — надпись собирается на языке смотрящего.
 * Имя деревни и победителя не переводятся, потому что это имена.
 */
public class TrophyBlockEntity extends BlockEntity {

    /** Имя поля — и в блок-сущности, и в предмете, и в таблице добычи. */
    public static final String ENGRAVING = "Engraving";

    private NbtCompound engraving = new NbtCompound();

    public TrophyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TROPHY, pos, state);
    }

    public NbtCompound engraving() {
        return engraving.copy();
    }

    public void engrave(NbtCompound engraving) {
        this.engraving = engraving.copy();
        markDirty();
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        engraving = nbt.getCompound(ENGRAVING).copy();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put(ENGRAVING, engraving.copy());
    }

    /**
     * Надпись.
     *
     * @param contest  ключ названия состязания
     * @param festival ключ названия праздника
     * @param village  имя деревни
     * @param day      игровой день
     * @param winner   имя победителя
     */
    public static NbtCompound engraving(String contest, String festival, String village, long day,
                                        String winner) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("contest", contest);
        nbt.putString("festival", festival);
        nbt.putString("village", village);
        nbt.putLong("day", day);
        nbt.putString("winner", winner);
        return nbt;
    }

    /** Строки надписи. Пустая надпись — так и сказано: кубок без надписи. */
    public static List<Text> lines(NbtCompound engraving) {
        List<Text> lines = new ArrayList<>();
        if (!engraving.contains("contest")) {
            lines.add(Text.translatable("villagepax.trophy.blank").formatted(Formatting.GRAY));
            return lines;
        }
        lines.add(Text.translatable("villagepax.trophy.place",
                Text.translatable(engraving.getString("contest"))).formatted(Formatting.GOLD));
        lines.add(Text.translatable("villagepax.trophy.where",
                Text.translatable(engraving.getString("festival")), engraving.getString("village"),
                engraving.getLong("day") + 1).formatted(Formatting.GRAY));
        lines.add(Text.translatable("villagepax.trophy.winner", engraving.getString("winner"))
                .formatted(Formatting.GRAY));
        return lines;
    }
}
