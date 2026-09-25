package com.villagepax.gametest;

import com.villagepax.block.ModBlocks;
import com.villagepax.block.wonder.Wonders;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.CitizenSpawner;
import com.villagepax.item.charm.Charm;
import com.villagepax.item.charm.Charms;
import com.villagepax.effect.ModEffects;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Диковинки и обереги делают то, что обещают на ярлыке.
 * <p>
 * Каждая проверка — про дело, а не про вид: вид проверяет просмотрщик
 * моделей, а игрок заметит, если барабан не поднимает на марш, а флюгер
 * врёт о дожде.
 */
public class WonderTests extends GameTestSupport {

    /**
     * Свой оберег у каждого народа — и получить его можно, дружа с ним:
     * он лежит на прилавке для друзей и в награде за последнюю просьбу.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void everyPeopleGivesItsCharmToFriends(TestContext context) {
        java.util.List<String> complaints = new java.util.ArrayList<>();
        com.villagepax.core.culture.CultureManager.all().keySet().forEach(people -> {
            Charm charm = Charm.ofPeople(people.getPath());
            if (charm == null) {
                complaints.add(people + ": своего оберега нет");
                return;
            }
            net.minecraft.item.Item item = Charms.itemOf(charm);
            boolean sold = com.villagepax.core.trade.TradeTables.of(people)
                    .map(table -> table.sells().stream().anyMatch(deal -> deal.item() == item))
                    .orElse(false);
            if (!sold) {
                complaints.add(people + ": оберег не продают друзьям");
            }
        });
        if (!complaints.isEmpty()) {
            context.throwGameTestException("Обереги народов:\n  " + String.join("\n  ", complaints));
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theWarDrumRaisesAMarchAndThenRests(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos drum = context.getAbsolutePos(new BlockPos(2, 2, 2));
        world.setBlockState(drum, ModBlocks.WAR_DRUM.getDefaultState());
        CitizenEntity soldier = CitizenSpawner.spawnPuppet(world, drum.east(2));
        try {
            if (soldier == null) {
                context.throwGameTestException("Боец не появился");
                return;
            }
            BlockState state = world.getBlockState(drum);
            if (!Wonders.WarDrum.beat(world, drum, state)) {
                context.throwGameTestException("Первый удар не поднял на марш");
            }
            if (!soldier.hasStatusEffect(StatusEffects.SPEED)
                    || !soldier.hasStatusEffect(StatusEffects.STRENGTH)) {
                context.throwGameTestException("Боец рядом с барабаном не пошёл маршем");
            }
            soldier.clearStatusEffects();
            if (Wonders.WarDrum.beat(world, drum, world.getBlockState(drum))
                    || soldier.hasStatusEffect(StatusEffects.SPEED)) {
                context.throwGameTestException("Барабан не отдыхает: марш вышел вечным");
            }
        } finally {
            if (soldier != null) {
                soldier.discard();
            }
            world.setBlockState(drum, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theJaguarIdolRevealsFoesAtNightOnly(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos idol = context.getAbsolutePos(new BlockPos(2, 2, 2));
        ZombieEntity foe = context.spawnMob(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        long was = world.getTimeOfDay();
        try {
            world.setTimeOfDay(6_000L);
            if (Wonders.JaguarIdol.watch(world, idol) != 0 || foe.hasStatusEffect(StatusEffects.GLOWING)) {
                context.throwGameTestException("Идол подсветил врага средь бела дня");
            }
            world.setTimeOfDay(18_000L);
            if (Wonders.JaguarIdol.watch(world, idol) < 1 || !foe.hasStatusEffect(StatusEffects.GLOWING)) {
                context.throwGameTestException("Ночью идол ягуара не увидел врага");
            }
        } finally {
            world.setTimeOfDay(was);
            foe.discard();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theRainbowFountainFillsABucket(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos fountain = new BlockPos(2, 2, 2);
        context.setBlockState(fountain, ModBlocks.RAINBOW_FOUNTAIN.getDefaultState());
        PlayerEntity player = context.createMockSurvivalPlayer();
        try {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
            context.useBlock(fountain, player);
            boolean filled = player.getInventory().contains(new ItemStack(Items.WATER_BUCKET));
            if (!filled) {
                context.throwGameTestException("Из фонтана не зачерпнуть воды ведром");
            }
        } finally {
            context.setBlockState(fountain, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theWeathervaneTellsTheWeatherTruly(TestContext context) {
        ServerWorld world = context.getWorld();
        try {
            world.setWeather(6_000, 0, false, false);
            String clear = Wonders.Weathervane.forecast(world).getString();
            world.setWeather(0, 2_400, true, false);
            String rain = Wonders.Weathervane.forecast(world).getString();
            if (clear.equals(rain)) {
                context.throwGameTestException("Флюгер говорит одно и то же в ясный день и в дождь: "
                        + clear);
            }
            if (!clear.contains("5")) {
                context.throwGameTestException("Ясно на пять минут, а флюгер говорит: " + clear);
            }
        } finally {
            world.setWeather(0, 0, false, false);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theCalendarAndTheRuneStoneSpeak(TestContext context) {
        ServerWorld world = context.getWorld();
        if (Wonders.MayaCalendar.reading(world).size() < 4) {
            context.throwGameTestException("Календарный камень молчит о дне, луне и рассвете");
        }
        BlockPos stone = context.getAbsolutePos(new BlockPos(1, 2, 1));
        int today = Wonders.RuneStone.verse(stone, 10);
        int tomorrow = Wonders.RuneStone.verse(stone, 11);
        if (today == tomorrow || today < 0 || today >= 8) {
            context.throwGameTestException("Рунный камень не меняет строку саги от дня к дню");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theScarecrowChasesRabbitsAway(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos scarecrow = context.getAbsolutePos(new BlockPos(2, 2, 2));
        RabbitEntity rabbit = context.spawnMob(EntityType.RABBIT, new BlockPos(4, 2, 2));
        try {
            if (Wonders.Scarecrow.scare(world, scarecrow) < 1) {
                context.throwGameTestException("Пугало не заметило кролика у грядок");
            }
            Vec3d away = rabbit.getPos().subtract(Vec3d.ofCenter(scarecrow));
            if (rabbit.getVelocity().dotProduct(away) <= 0) {
                context.throwGameTestException("Кролик бежит не прочь от пугала, а к нему");
            }
        } finally {
            rabbit.discard();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void incenseIsSeenNearTheAltar(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos altar = context.getAbsolutePos(new BlockPos(2, 2, 2));
        if (Wonders.incenseNear(world, altar, 4)) {
            context.throwGameTestException("Кадильница нашлась там, где её нет");
        }
        BlockPos burner = altar.east(3);
        world.setBlockState(burner, ModBlocks.INCENSE_BURNER.getDefaultState());
        try {
            if (!Wonders.incenseNear(world, altar, 4)) {
                context.throwGameTestException("Кадильницу в трёх шагах от алтаря не видно");
            }
        } finally {
            world.setBlockState(burner, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void charmsWorkFromTheOffHand(TestContext context) {
        ZombieEntity wearer = context.spawnMob(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        try {
            wearer.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Charms.itemOf(Charm.JADE_JAGUAR)));
            Charms.empower(wearer);
            if (!wearer.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                context.throwGameTestException("Нефритовый ягуар не дал видеть в темноте");
            }
            wearer.clearStatusEffects();
            wearer.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Charms.itemOf(Charm.LUCKY_HORSESHOE)));
            Charms.empower(wearer);
            if (!wearer.hasStatusEffect(StatusEffects.LUCK)) {
                context.throwGameTestException("Подкова не принесла удачи");
            }
            wearer.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Charms.itemOf(Charm.MOON_PENDANT)));
            Charms.empower(wearer);
            if (!wearer.hasStatusEffect(ModEffects.LIGHTNESS)) {
                context.throwGameTestException("Лунный кулон не дал лёгкости");
            }
            wearer.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Charms.itemOf(Charm.KITSUNE_CHARM)));
            wearer.setSneaking(true);
            Charms.empower(wearer);
            if (!wearer.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                context.throwGameTestException("Лисий оберег не спрятал крадущегося");
            }
            // Оберег в правой руке — не надет: сила только у того, что в левой.
            wearer.clearStatusEffects();
            wearer.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            wearer.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Charms.itemOf(Charm.JADE_JAGUAR)));
            Charms.empower(wearer);
            if (wearer.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                context.throwGameTestException("Оберег сработал из правой руки");
            }
        } finally {
            wearer.discard();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theReliquaryGivesASecondWindOnce(TestContext context) {
        PlayerEntity pilgrim = context.createMockSurvivalPlayer();
        pilgrim.setStackInHand(Hand.OFF_HAND, new ItemStack(Charms.itemOf(Charm.PILGRIM_RELIQUARY)));
        pilgrim.setHealth(8.0f);
        if (!Charms.secondWind(pilgrim, 3.0f) || !pilgrim.hasStatusEffect(StatusEffects.ABSORPTION)) {
            context.throwGameTestException("Ладанка не дала второго дыхания на краю");
        }
        pilgrim.clearStatusEffects();
        if (Charms.secondWind(pilgrim, 3.0f)) {
            context.throwGameTestException("Ладанка сработала дважды подряд: отдых забыт");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theMagnetPullsLooseItems(TestContext context) {
        ServerWorld world = context.getWorld();
        PlayerEntity player = context.createMockSurvivalPlayer();
        BlockPos at = context.getAbsolutePos(new BlockPos(1, 2, 1));
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ItemEntity loose = new ItemEntity(world, at.getX() + 5.5, at.getY() + 0.5, at.getZ() + 0.5,
                new ItemStack(Items.COBBLESTONE));
        world.spawnEntity(loose);
        try {
            if (Charms.pull(player) < 1) {
                context.throwGameTestException("Магнит не потянул вещь в пяти шагах");
            }
            if (loose.getVelocity().x >= 0) {
                context.throwGameTestException("Вещь полетела не к хозяину магнита");
            }
        } finally {
            loose.discard();
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theThunderRuneCallsLightningWhereYouLook(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos floor = context.getAbsolutePos(new BlockPos(2, 1, 2));
        world.setBlockState(floor, Blocks.STONE.getDefaultState());
        PlayerEntity caster = context.createMockSurvivalPlayer();
        caster.refreshPositionAndAngles(floor.getX() + 0.5, floor.getY() + 4, floor.getZ() + 0.5, 0, 90);
        try {
            if (!Charms.thunder(caster)) {
                context.throwGameTestException("Руна не нашла цели прямо под ногами");
            }
            boolean struck = !world.getEntitiesByClass(LightningEntity.class,
                    new Box(floor).expand(3), bolt -> true).isEmpty();
            if (!struck) {
                context.throwGameTestException("Молния не ударила туда, куда смотрел заклинатель");
            }
        } finally {
            world.getEntitiesByClass(LightningEntity.class, new Box(floor).expand(3), bolt -> true)
                    .forEach(net.minecraft.entity.Entity::discard);
            world.setBlockState(floor, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "wonders")
    public void theWindCharmDashesForward(TestContext context) {
        PlayerEntity runner = context.createMockSurvivalPlayer();
        runner.refreshPositionAndAngles(runner.getX(), runner.getY(), runner.getZ(), 0, 0);
        Charms.dash(runner);
        Vec3d push = runner.getVelocity();
        if (push.horizontalLength() < 1.0 || !runner.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            context.throwGameTestException("Ветер не бросил вперёд или не смягчил падение: " + push);
        }
        context.complete();
    }
}
