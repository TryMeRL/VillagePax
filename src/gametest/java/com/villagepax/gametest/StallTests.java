package com.villagepax.gametest;

import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.Festivals;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.item.festival.ModFestivalItems;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import com.villagepax.sim.festival.PrizeStall;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

/**
 * Лавка затейника: ленты на праздничный товар — и шапка народа, в которой
 * тебе машут.
 */
public class StallTests extends GameTestSupport {

    private static final Identifier YAMATO = new Identifier("villagepax", "yamato");

    /** Номер товара в лавке народа. */
    private static int prizeIndex(Identifier culture, Item item) {
        Festival festival = Festivals.of(culture).orElseThrow();
        Identifier wanted = Registries.ITEM.getId(item);
        for (int i = 0; i < festival.prizes().size(); i++) {
            if (festival.prizes().get(i).item().equals(wanted)) {
                return i;
            }
        }
        throw new IllegalStateException("в лавке " + culture + " нет " + wanted);
    }

    private static int ribbons(PlayerEntity player) {
        return player.getInventory().count(ModFestivalItems.FESTIVAL_RIBBON);
    }

    /**
     * Восемь лент — шапка народа, и лент не осталось; семи мало — ничего
     * не меняется; в будни лавка закрыта.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "stall")
    public void theStallTradesRibbonsForAHat(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            Item wreath = ModFestivalItems.hatOf(NORMAN).orElseThrow();
            int hat = prizeIndex(NORMAN, wreath);
            PlayerEntity player = playerAt(context, ground.place().counter());

            player.getInventory().insertStack(new ItemStack(ModFestivalItems.FESTIVAL_RIBBON, 7));
            PrizeStall.Verdict poor = PrizeStall.buy(world, player, ground.village(), hat, FAIR_DAY);
            if (poor != PrizeStall.Verdict.POOR || ribbons(player) != 7
                    || player.getInventory().count(wreath) != 0) {
                context.throwGameTestException("Семь лент за шапку в восемь: " + poor + ", лент "
                        + ribbons(player));
            }

            player.getInventory().insertStack(new ItemStack(ModFestivalItems.FESTIVAL_RIBBON, 1));
            PrizeStall.Verdict bought = PrizeStall.buy(world, player, ground.village(), hat, FAIR_DAY);
            if (bought != PrizeStall.Verdict.YES || ribbons(player) != 0
                    || player.getInventory().count(wreath) != 1) {
                context.throwGameTestException("Восемь лент — не шапка: " + bought + ", лент "
                        + ribbons(player) + ", шапок " + player.getInventory().count(wreath));
            }

            player.getInventory().insertStack(new ItemStack(ModFestivalItems.FESTIVAL_RIBBON, 8));
            PrizeStall.Verdict weekday = PrizeStall.buy(world, player, ground.village(), hat, FAIR_DAY + 1);
            if (weekday != PrizeStall.Verdict.CLOSED || ribbons(player) != 8) {
                context.throwGameTestException("В будни лавка торгует: " + weekday);
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /** Ракеты из лавки — в цветах народа и повыше ванильных: полёт два. */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "stall")
    public void rocketsFromTheStallCarryThePeoplesColours(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        FairGround ground = fairGround(context, world, manager);
        try {
            PlayerEntity player = playerAt(context, ground.place().counter());
            player.getInventory().insertStack(new ItemStack(ModFestivalItems.FESTIVAL_RIBBON, 1));
            PrizeStall.Verdict verdict = PrizeStall.buy(world, player, ground.village(),
                    prizeIndex(NORMAN, Items.FIREWORK_ROCKET), FAIR_DAY);
            ItemStack rockets = ItemStack.EMPTY;
            for (int slot = 0; slot < player.getInventory().size(); slot++) {
                if (player.getInventory().getStack(slot).isOf(Items.FIREWORK_ROCKET)) {
                    rockets = player.getInventory().getStack(slot);
                }
            }
            NbtCompound fireworks = rockets.getSubNbt("Fireworks");
            if (verdict != PrizeStall.Verdict.YES || rockets.getCount() != 3 || fireworks == null
                    || fireworks.getByte("Flight") != 2
                    || !Arrays.equals(fireworks.getList("Explosions", NbtElement.COMPOUND_TYPE)
                    .getCompound(0).getIntArray("Colors"), new int[]{0xC0392B, 0xE0B040})) {
                context.throwGameTestException("Ракеты из лавки не норманнские: " + verdict + ", "
                        + rockets.getCount() + " шт., " + fireworks);
            }
        } finally {
            clearFairGround(context, world, manager, ground);
        }
        context.complete();
    }

    /**
     * Житель-норманн машет игроку в венке — и не машет маске кицунэ:
     * шапка своего народа, а не всякая праздничная.
     */
    @GameTest(templateName = WIDE_STRUCTURE, batchId = "stall")
    public void aHatMakesItsPeopleWave(TestContext context) {
        ServerWorld world = context.getWorld();
        SettlementManager manager = SettlementManager.get(world);
        BlockPos hall = context.getAbsolutePos(new BlockPos(1, 1, 1));
        Settlement colony = colonyWithBuilder(world, manager, hall);
        try {
            BlockPos spot = context.getAbsolutePos(new BlockPos(6, 2, 6));
            Citizen norman = hireWithBody(world, colony, new Identifier("villagepax", "farmer"), spot);
            Citizen other = hireWithBody(world, colony, new Identifier("villagepax", "farmer"), spot.east(2));
            CitizenEntity waver = bodyOf(world, colony, norman);
            CitizenEntity stranger = bodyOf(world, colony, other);

            PlayerEntity inWreath = context.createMockSurvivalPlayer();
            inWreath.setPosition(Vec3d.ofBottomCenter(spot.south(3)));
            inWreath.equipStack(EquipmentSlot.HEAD, new ItemStack(ModFestivalItems.hatOf(NORMAN).orElseThrow()));
            long before = waver.lastWave();
            if (!waver.wave(inWreath) || waver.lastWave() == before) {
                context.throwGameTestException("Норманн не помахал игроку в венке");
            }

            PlayerEntity inMask = context.createMockSurvivalPlayer();
            inMask.setPosition(Vec3d.ofBottomCenter(spot.east(2).south(3)));
            inMask.equipStack(EquipmentSlot.HEAD, new ItemStack(ModFestivalItems.hatOf(YAMATO).orElseThrow()));
            long quiet = stranger.lastWave();
            if (stranger.wave(inMask) || stranger.lastWave() != quiet) {
                context.throwGameTestException("Норманн машет маске кицунэ — шапке чужого народа");
            }
        } finally {
            discardBodies(world, colony);
            manager.remove(colony.id());
            world.setBlockState(hall, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }
}
