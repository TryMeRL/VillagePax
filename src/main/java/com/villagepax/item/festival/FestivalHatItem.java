package com.villagepax.item.festival;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Праздничная шапка народа: венок, корона из перьев, маска кицунэ…
 * <p>
 * Надевается на голову, как тыква, и рисуется своей объёмной моделью
 * предмета — брони в ней нет, она не защищает ни от чего. Зато жители
 * её народа узнают своего: машут вслед тому, кто в ней. Это единственное
 * её дело, и его хватает — шапку берут в лавке за ленты, а ленты за победы.
 */
public class FestivalHatItem extends Item implements Equipment {

    private final Identifier culture;

    public FestivalHatItem(Identifier culture, Settings settings) {
        super(settings);
        this.culture = culture;
    }

    /** Чей это праздник и чья шапка: её народ и машет. */
    public Identifier culture() {
        return culture;
    }

    @Override
    public EquipmentSlot getSlotType() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return equipAndSwap(this, world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        tooltip.add(Text.translatable("villagepax.festival_hat.tooltip",
                Text.translatable("villagepax.culture." + culture.getPath()))
                .formatted(Formatting.GRAY));
    }
}
