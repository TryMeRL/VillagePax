package com.villagepax.block.wonder;

import com.villagepax.effect.ModEffects;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.work.Schedule;
import net.minecraft.block.BlockState;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.level.ServerWorldProperties;
import org.joml.Vector3f;

import java.util.List;

/**
 * Диковинки народов — у каждой своё дело.
 * <p>
 * Собраны в одном файле нарочно: каждая — десяток строк поведения поверх
 * общего основания, и искать их по двенадцати файлам было бы дольше,
 * чем читать. Модели и текстуры пишет {@code tools/make-wonders.py}.
 */
public final class Wonders {

    private Wonders() {
    }

    /**
     * Ночь ли сейчас — по часам суток, а не по темноте неба.
     * <p>
     * Ванильная {@code isNight} смотрит на затемнение неба, а оно
     * пересчитывается раз в тик: переведи часы — и мир ещё тик думает,
     * что светло. Диковинкам нужна ночь по времени, как у календаря.
     */
    public static boolean isNight(World world) {
        long time = Math.floorMod(world.getTimeOfDay(), 24_000L);
        return time >= 13_000L && time < 23_000L;
    }

    /** Торо: каменный фонарь сада, в окошке пляшет огонёк. */
    public static class Toro extends WonderBlock {
        public Toro(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (random.nextInt(4) == 0) {
                world.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5,
                        pos.getY() + 0.6, pos.getZ() + 0.5, 0, 0.004, 0);
            }
        }
    }

    /**
     * Фурин: стеклянный колокольчик с бумажным язычком. Звенит сам, чаще
     * в дождь, — как ветер у веранды. Сквозь него проходят: он висит над
     * головой и не должен ни застревать в пути, ни загораживать вход.
     */
    public static class WindChime extends LanternBlock {
        private static final VoxelShape HANGING = createCuboidShape(5, 0, 5, 11, 16, 11);
        private static final VoxelShape STANDING = createCuboidShape(2, 0, 5, 14, 16, 11);

        public WindChime(Settings settings) {
            super(settings);
        }

        @Override
        public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                          ShapeContext context) {
            return state.get(LanternBlock.HANGING) ? HANGING : STANDING;
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            int chance = world.isRaining() ? 6 : 24;
            if (random.nextInt(chance) == 0) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS,
                        0.55f, 1.4f + random.nextFloat() * 0.5f, false);
            }
        }
    }

    /** Рунный камень: каждый день на нём проступает другая строка саги. */
    public static class RuneStone extends com.villagepax.block.FurnitureBlock {
        static final int VERSES = 8;

        public RuneStone(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        /** Какая строка сегодня: своя у каждого камня и своя у каждого дня. */
        public static int verse(BlockPos pos, long day) {
            return Math.floorMod(pos.hashCode() + (int) day, VERSES);
        }

        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                  Hand hand, BlockHitResult hit) {
            if (!(world instanceof ServerWorld server)) {
                return ActionResult.SUCCESS;
            }
            int line = verse(pos, Schedule.dayOf(world.getTimeOfDay()));
            player.sendMessage(Text.translatable("villagepax.rune_stone.saga." + line)
                    .formatted(Formatting.AQUA, Formatting.ITALIC), false);
            server.spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2,
                    pos.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.4);
            world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                    SoundCategory.BLOCKS, 0.8f, 0.8f);
            return ActionResult.CONSUME;
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (world.isNight() && random.nextInt(3) == 0) {
                world.addParticle(new DustParticleEffect(new Vector3f(0.4f, 0.7f, 1.0f), 0.7f),
                        pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.3
                                + random.nextDouble() * 0.6, pos.getZ() + 0.5, 0, 0.01, 0);
            }
        }
    }

    /**
     * Боевой барабан: удар поднимает всех рядом на марш — быстрее и сильнее.
     * Потом барабан «гудит» минуту и снова силы не даёт: иначе марш был бы
     * вечным, а он — на один бой. Бьют рукой или сигналом красного камня.
     */
    public static class WarDrum extends WonderBlock {
        public static final BooleanProperty RESTING = BooleanProperty.of("resting");
        public static final BooleanProperty POWERED = Properties.POWERED;
        public static final int MARCH_TICKS = 20 * 20;
        public static final int REST_TICKS = 20 * 60;
        public static final double REACH = 12.0;

        public WarDrum(VoxelShape shape, Settings settings) {
            super(shape, settings);
            setDefaultState(getDefaultState().with(RESTING, false).with(POWERED, false));
        }

        @Override
        protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
            builder.add(RESTING, POWERED);
        }

        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                  Hand hand, BlockHitResult hit) {
            if (world instanceof ServerWorld server) {
                boolean marched = beat(server, pos, state);
                player.sendMessage(Text.translatable(marched ? "villagepax.war_drum.march"
                        : "villagepax.war_drum.resting"), true);
            }
            return ActionResult.success(world.isClient);
        }

        @Override
        public void neighborUpdate(BlockState state, World world, BlockPos pos,
                                   net.minecraft.block.Block source, BlockPos from, boolean notify) {
            boolean powered = world.isReceivingRedstonePower(pos);
            if (powered != state.get(POWERED)) {
                world.setBlockState(pos, state.with(POWERED, powered), 3);
                if (powered && world instanceof ServerWorld server) {
                    beat(server, pos, state.with(POWERED, true));
                }
            }
        }

        /**
         * Удар. Звучит всегда; силу даёт, только если барабан отдохнул.
         *
         * @return поднял ли удар на марш
         */
        public static boolean beat(ServerWorld world, BlockPos pos, BlockState state) {
            for (int i = 0; i < 3; i++) {
                world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(),
                        SoundCategory.BLOCKS, 3.0f, 0.55f + i * 0.05f);
            }
            world.spawnParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.1,
                    pos.getZ() + 0.5, 6, 0.4, 0.2, 0.4, 1.0);
            if (state.get(RESTING)) {
                return false;
            }
            Box around = new Box(pos).expand(REACH);
            for (LivingEntity who : world.getEntitiesByClass(LivingEntity.class, around,
                    entity -> entity instanceof PlayerEntity || entity instanceof CitizenEntity)) {
                who.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, MARCH_TICKS, 0));
                who.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, MARCH_TICKS, 0));
            }
            world.setBlockState(pos, state.with(RESTING, true), 3);
            world.scheduleBlockTick(pos, state.getBlock(), REST_TICKS);
            return true;
        }

        @Override
        public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
            if (state.get(RESTING)) {
                world.setBlockState(pos, state.with(RESTING, false), 3);
            }
        }
    }

    /**
     * Идол ягуара: ночью его глаза видят врага — всякую нечисть вокруг
     * деревни подсвечивает, и её видно сквозь стены. Стража майя ставит
     * такие у входа, и ночь у них не страшнее дня.
     */
    public static class JaguarIdol extends com.villagepax.block.FurnitureBlock {
        public static final int WATCH_EVERY = 100;
        public static final double SIGHT = 24.0;

        public JaguarIdol(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState old,
                                 boolean notify) {
            if (!old.isOf(this)) {
                world.scheduleBlockTick(pos, this, WATCH_EVERY);
            }
        }

        @Override
        public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
            watch(world, pos);
            world.scheduleBlockTick(pos, this, WATCH_EVERY);
        }

        /** Подсветить нечисть вокруг, если ночь. @return скольких увидел */
        public static int watch(ServerWorld world, BlockPos pos) {
            if (!isNight(world)) {
                return 0;
            }
            List<HostileEntity> foes = world.getEntitiesByClass(HostileEntity.class,
                    new Box(pos).expand(SIGHT), LivingEntity::isAlive);
            for (HostileEntity foe : foes) {
                foe.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,
                        WATCH_EVERY + 40, 0, true, false));
            }
            return foes.size();
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (world.isNight() && random.nextInt(4) == 0) {
                Direction face = state.get(FACING);
                world.addParticle(new DustParticleEffect(new Vector3f(0.2f, 0.9f, 0.6f), 0.6f),
                        pos.getX() + 0.5 + face.getOffsetX() * 0.55, pos.getY() + 0.6,
                        pos.getZ() + 0.5 + face.getOffsetZ() * 0.55, 0, 0.005, 0);
            }
        }
    }

    /**
     * Радужный фонтанчик пони: из него черпают воду ведром и бутылкой —
     * неиссякаемый родник посреди степи, — а кто стоит рядом, тому весело.
     */
    public static class RainbowFountain extends WonderBlock {
        public static final int JOY_EVERY = 40;

        public RainbowFountain(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                  Hand hand, BlockHitResult hit) {
            ItemStack held = player.getStackInHand(hand);
            ItemStack filled = null;
            if (held.isOf(Items.BUCKET)) {
                filled = new ItemStack(Items.WATER_BUCKET);
            } else if (held.isOf(Items.GLASS_BOTTLE)) {
                filled = PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.WATER);
            }
            if (filled == null) {
                return ActionResult.PASS;
            }
            if (!world.isClient) {
                if (!player.getAbilities().creativeMode) {
                    held.decrement(1);
                }
                if (!player.getInventory().insertStack(filled)) {
                    player.dropItem(filled, false);
                }
                world.playSound(null, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS,
                        1.0f, 1.2f);
            }
            return ActionResult.success(world.isClient);
        }

        @Override
        public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState old,
                                 boolean notify) {
            if (!old.isOf(this)) {
                world.scheduleBlockTick(pos, this, JOY_EVERY);
            }
        }

        @Override
        public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
            for (PlayerEntity player : world.getEntitiesByClass(PlayerEntity.class,
                    new Box(pos).expand(3), PlayerEntity::isAlive)) {
                player.addStatusEffect(new StatusEffectInstance(ModEffects.RAINBOW_DASH, 100, 0,
                        true, true));
            }
            world.scheduleBlockTick(pos, this, JOY_EVERY);
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            world.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.7,
                    pos.getZ() + 0.5, 0, 0.2, 0);
            float[][] rainbow = {{0.91f, 0.25f, 0.24f}, {0.95f, 0.6f, 0.17f}, {0.96f, 0.85f, 0.23f},
                    {0.36f, 0.78f, 0.35f}, {0.23f, 0.63f, 0.91f}, {0.55f, 0.35f, 0.85f}};
            float[] colour = rainbow[random.nextInt(rainbow.length)];
            world.addParticle(new DustParticleEffect(new Vector3f(colour[0], colour[1], colour[2]),
                            1.0f), pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.8,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.05, 0);
            if (random.nextInt(30) == 0) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS, 0.3f, 1.4f, false);
            }
        }
    }

    /** Искры над кристаллами гномьей лампы. */
    public static class CrystalLamp extends WonderBlock {
        public CrystalLamp(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (random.nextInt(3) == 0) {
                world.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                        pos.getY() + 0.8, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
            }
        }
    }

    /** Светлячки: ночью вылетают погулять вокруг банки. */
    public static class FireflyJar extends WonderBlock {
        public FireflyJar(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            if (world.isNight() && random.nextInt(2) == 0) {
                world.addParticle(new DustParticleEffect(new Vector3f(0.85f, 1.0f, 0.3f), 0.5f),
                        pos.getX() + random.nextDouble() * 1.4 - 0.2,
                        pos.getY() + random.nextDouble() * 1.2,
                        pos.getZ() + random.nextDouble() * 1.4 - 0.2, 0, 0.01, 0);
            }
        }
    }

    /**
     * Кадильница: у алтаря с дымом благовоний боги слышат лучше — жертва
     * весит вполовину больше (см. Offering). Над чашей всегда тянется дымок.
     */
    public static class IncenseBurner extends WonderBlock {
        public IncenseBurner(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
            world.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.45 + random.nextDouble() * 0.1,
                    pos.getY() + 0.7, pos.getZ() + 0.45 + random.nextDouble() * 0.1, 0, 0.02, 0);
        }
    }

    /** Флюгер: щёлкни — скажет, когда дождь. Погода мира ему видна. */
    public static class Weathervane extends com.villagepax.block.FurnitureBlock {
        public Weathervane(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                  Hand hand, BlockHitResult hit) {
            if (world instanceof ServerWorld server) {
                player.sendMessage(forecast(server), false);
                world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.BLOCKS, 0.8f,
                        1.4f);
            }
            return ActionResult.success(world.isClient);
        }

        /** Прогноз: что сейчас и сколько минут до перемены. */
        public static Text forecast(ServerWorld world) {
            ServerWorldProperties weather = (ServerWorldProperties) world.getLevelProperties();
            int clear = weather.getClearWeatherTime();
            if (clear > 0) {
                return Text.translatable("villagepax.weathervane.clear", minutes(clear));
            }
            if (weather.isThundering()) {
                return Text.translatable("villagepax.weathervane.storm",
                        minutes(weather.getThunderTime()));
            }
            if (weather.isRaining()) {
                return Text.translatable("villagepax.weathervane.rain_ends",
                        minutes(weather.getRainTime()));
            }
            return Text.translatable("villagepax.weathervane.rain_comes",
                    minutes(weather.getRainTime()));
        }

        private static int minutes(int ticks) {
            return Math.max(1, Math.round(ticks / 1200.0f));
        }
    }

    /** Календарный камень майя: день, луна и сколько до полнолуния. */
    public static class MayaCalendar extends com.villagepax.block.FurnitureBlock {
        public MayaCalendar(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                                  Hand hand, BlockHitResult hit) {
            if (world instanceof ServerWorld server) {
                for (Text line : reading(server)) {
                    player.sendMessage(line, false);
                }
                world.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.8f,
                        0.7f);
            }
            return ActionResult.success(world.isClient);
        }

        /** Что говорит камень: день, фаза луны, дни до полной луны и до рассвета. */
        public static List<Text> reading(ServerWorld world) {
            long time = world.getTimeOfDay();
            long day = time / 24_000L + 1;
            int phase = world.getMoonPhase();
            int toFull = Math.floorMod(8 - phase, 8);
            long ofDay = Math.floorMod(time, 24_000L);
            long toDawn = Math.floorMod(24_000L - ofDay, 24_000L);
            return List.of(
                    Text.translatable("villagepax.maya_calendar.day", day).formatted(Formatting.GOLD),
                    Text.translatable("villagepax.maya_calendar.moon",
                            Text.translatable("villagepax.maya_calendar.moon." + phase)),
                    toFull == 0 ? Text.translatable("villagepax.maya_calendar.full_now")
                            : Text.translatable("villagepax.maya_calendar.to_full", toFull),
                    Text.translatable("villagepax.maya_calendar.to_dawn",
                            Math.max(1, Math.round(toDawn / 1200.0f))));
        }
    }

    /**
     * Пугало: кролики, что пришли за морковью, убегают прочь. Поле фермера
     * кормит колонию, и кролик у грядок — это голодный месяц.
     */
    public static class Scarecrow extends com.villagepax.block.FurnitureBlock {
        public static final int SCARE_EVERY = 40;
        public static final double SCARE_RADIUS = 12.0;

        public Scarecrow(VoxelShape shape, Settings settings) {
            super(shape, settings);
        }

        @Override
        public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState old,
                                 boolean notify) {
            if (!old.isOf(this)) {
                world.scheduleBlockTick(pos, this, SCARE_EVERY);
            }
        }

        @Override
        public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
            scare(world, pos);
            world.scheduleBlockTick(pos, this, SCARE_EVERY);
        }

        /** Прогнать кроликов прочь от пугала. @return скольких прогнал */
        public static int scare(ServerWorld world, BlockPos pos) {
            List<RabbitEntity> rabbits = world.getEntitiesByClass(RabbitEntity.class,
                    new Box(pos).expand(SCARE_RADIUS), RabbitEntity::isAlive);
            Vec3d centre = Vec3d.ofCenter(pos);
            for (RabbitEntity rabbit : rabbits) {
                Vec3d away = rabbit.getPos().subtract(centre).multiply(1, 0, 1);
                if (away.lengthSquared() < 0.01) {
                    away = new Vec3d(1, 0, 0);
                }
                away = away.normalize();
                rabbit.setVelocity(away.x * 0.7, 0.4, away.z * 0.7);
                rabbit.velocityModified = true;
                Vec3d flee = rabbit.getPos().add(away.multiply(SCARE_RADIUS + 4));
                rabbit.getNavigation().startMovingTo(flee.x, flee.y, flee.z, 1.6);
            }
            return rabbits.size();
        }
    }

    /** Кадильница рядом с алтарём? Нужна жертве: см. Offering. */
    public static boolean incenseNear(WorldView world, BlockPos altar, int radius) {
        for (BlockPos at : BlockPos.iterate(altar.add(-radius, -2, -radius),
                altar.add(radius, 2, radius))) {
            if (world.getBlockState(at).getBlock() instanceof IncenseBurner) {
                return true;
            }
        }
        return false;
    }
}
