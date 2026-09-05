package com.villagepax.item;

import com.villagepax.sim.ColonyFounder;
import com.villagepax.sim.FoundingOutcome;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Чертёж ратуши: основной путь в мод.
 * <p>
 * Игрок получает его наградой за стартовую цепочку квестов у чужой деревни —
 * так он сперва полчаса смотрит на работающее поселение, а потом строит своё
 * и уже знает, к чему идёт. Крафт существует только как подстраховка: на
 * неудачном сиде рядом может не оказаться ни одной деревни, и без чертежа
 * мод было бы не начать вовсе.
 * <p>
 * Народ, записанный в предмет, определяет культуру будущей колонии. Сама
 * операция живёт в {@link ColonyFounder}, здесь остаются только сообщения
 * игроку и трата стака.
 */
public class TownHallBlueprintItem extends Item {

    public static final String CULTURE_KEY = "Culture";
    public static final Identifier DEFAULT_CULTURE = new Identifier("villagepax", "norman");
    public static final String KEY_SUCCESS = "villagepax.found.success";

    public TownHallBlueprintItem(Settings settings) {
        super(settings);
    }

    public static ItemStack forCulture(Identifier culture) {
        ItemStack stack = new ItemStack(ModItems.TOWN_HALL_BLUEPRINT);
        stack.getOrCreateNbt().putString(CULTURE_KEY, culture.toString());
        return stack;
    }

    /** Чертёж без записанного народа считается норманнским: это единственная культура в 0.1. */
    public static Identifier cultureOf(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(CULTURE_KEY)) {
            Identifier parsed = Identifier.tryParse(nbt.getString(CULTURE_KEY));
            if (parsed != null) {
                return parsed;
            }
        }
        return DEFAULT_CULTURE;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) {
            // Клиент только проигрывает взмах: решение принимает сервер.
            return ActionResult.SUCCESS;
        }

        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }

        ItemStack stack = context.getStack();
        BlockPos target = context.getBlockPos().offset(context.getSide());

        FoundingOutcome outcome = ColonyFounder.foundAt(
                serverWorld, player.getUuid(), cultureOf(stack), target);

        if (outcome instanceof FoundingOutcome.Refused refused) {
            player.sendMessage(Text.translatable(refused.translationKey(), refused.arguments()), false);
            return ActionResult.FAIL;
        }

        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        serverWorld.playSound(null, target, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        player.sendMessage(Text.translatable(KEY_SUCCESS,
                ((FoundingOutcome.Founded) outcome).settlement().name()), false);
        return ActionResult.CONSUME;
    }
}
