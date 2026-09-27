package com.villagepax.sim.festival;

import com.villagepax.block.ModBlocks;
import com.villagepax.block.festival.FestivalTokenBlock;
import com.villagepax.core.festival.Festival;
import com.villagepax.core.festival.TokenKind;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.sim.Citizen;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Поиск: за минуту найти вещицы, спрятанные вокруг ярмарки.
 * <p>
 * Вещицы ставятся на старте и записываются в память праздника поселения:
 * упади сервер посреди поиска — после запуска их уберут. Находка — щелчок
 * участника, и только пока идёт игра: во время отсчёта вещица ждёт.
 * <p>
 * Соперник замечает вещицу в шести блоках и идёт к ней; дошёл — она его.
 * Не видит ни одной — бродит по округе ярмарки и раз в три секунды
 * выбирает, куда пойти. Так житель ищет, как ищет человек: глазами,
 * а не по списку, и игрок, знающий деревню, его обгоняет.
 */
public final class Hunt extends Match {

    /** Меньше мест нашлось — поиск недоступен: из четырёх вещиц игры не выйдет. */
    public static final int FEWEST = 5;

    /** Соперник видит вещицу в этих блоках. */
    public static final double SIGHT = 6;

    /** Дошёл до вещицы на столько — она его. */
    public static final double REACH = 1.5;

    /** Как далеко от сердца бродит соперник, не видя вещиц. */
    private static final int STROLL = 16;

    /** Раз в сколько тиков бродящий выбирает новую точку: три секунды. */
    private static final long STROLL_EVERY = 60;

    public static final Identifier TOKEN = Registries.BLOCK.getId(ModBlocks.FESTIVAL_TOKEN);

    private final List<BlockPos> tokens = new ArrayList<>();
    private final Map<UUID, BlockPos> strolls = new HashMap<>();
    private final Map<UUID, Long> strolledAt = new HashMap<>();

    Hunt(ServerWorld world, UUID id, Settlement settlement, Fair fair, Festival festival, int index,
         long day) {
        super(world, id, settlement, fair, festival, index, day);
    }

    /** Ещё не найденные вещицы. */
    public List<BlockPos> tokens() {
        return List.copyOf(tokens);
    }

    boolean hides(BlockPos token) {
        return tokens.contains(token);
    }

    @Override
    protected boolean prepare(ServerWorld world, Random random) {
        TokenKind kind = contest().token().orElse(null);
        SettlementManager manager = SettlementManager.get(world);
        Settlement home = manager.byId(settlement()).orElse(null);
        if (kind == null || home == null) {
            return false;
        }
        List<BlockPos> spots = HidingPlaces.find(world, home, fair, contest().pieces(), random);
        if (spots.size() < FEWEST) {
            return false;
        }
        // Память праздника — сегодняшняя: стол накрыт, вчерашнее убрано.
        // Иначе вещицы записались бы поверх вчерашних пирогов, и те
        // считались бы сегодняшними.
        Feast.tend(world, manager, home, day());
        BlockState token = ModBlocks.FESTIVAL_TOKEN.getDefaultState().with(FestivalTokenBlock.KIND, kind);
        for (BlockPos spot : spots) {
            world.setBlockState(spot, token, Block.NOTIFY_ALL);
            manager.recordFestive(home.id(), day(), spot, TOKEN);
            tokens.add(spot.toImmutable());
        }
        return true;
    }

    /** Щелчок игрока по вещице этого поиска. */
    void collect(ServerWorld world, BlockPos token, PlayerEntity player) {
        if (!isPlayer(player.getUuid())) {
            player.sendMessage(Text.translatable("villagepax.contest.hunt.not_yours"), true);
            return;
        }
        if (!isRunning()) {
            player.sendMessage(Text.translatable("villagepax.contest.hunt.wait"), true);
            return;
        }
        take(world, token, player.getUuid());
    }

    @Override
    protected void steer(ServerWorld world, CitizenEntity body, Citizen rival) {
        prune(world);
        BlockPos seen = null;
        double nearest = SIGHT * SIGHT;
        for (BlockPos token : tokens) {
            double distance = body.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(token));
            if (distance <= nearest) {
                nearest = distance;
                seen = token;
            }
        }
        if (seen != null) {
            strolls.remove(rival.id());
            if (nearest <= REACH * REACH) {
                take(world, seen, rival.id());
                return;
            }
            body.setWorkTarget(seen);
            body.setWorkFocus(seen);
            return;
        }
        long now = world.getTime();
        BlockPos stroll = strolls.get(rival.id());
        if (stroll == null || now - strolledAt.getOrDefault(rival.id(), 0L) >= STROLL_EVERY) {
            stroll = somewhere(world, world.getRandom());
            strolls.put(rival.id(), stroll);
            strolledAt.put(rival.id(), now);
        }
        body.setWorkTarget(stroll);
        body.setWorkFocus(null);
    }

    @Override
    protected boolean exhausted(ServerWorld world) {
        prune(world);
        return tokens.isEmpty();
    }

    @Override
    protected void clear(ServerWorld world) {
        SettlementManager manager = SettlementManager.get(world);
        for (BlockPos token : tokens) {
            Feast.takeBack(world, manager, settlement(), new SettlementManager.Placed(token.asLong(), TOKEN));
        }
        tokens.clear();
        strolls.clear();
        strolledAt.clear();
    }

    /** Находка: очко, вещица с блеском уходит, память забывает её. */
    private void take(ServerWorld world, BlockPos token, UUID who) {
        if (!tokens.remove(token)) {
            return;
        }
        SettlementManager.get(world).forgetFestive(settlement(), token);
        if (world.getBlockState(token).isOf(ModBlocks.FESTIVAL_TOKEN)) {
            score(who, 1);
            FestivalTokenBlock.pickUp(world, token);
        }
    }

    /**
     * Забыть вещицы, которых уже нет: сломанную киркой не находят,
     * и поиск кончается, когда кончились вещицы, а не список.
     */
    private void prune(ServerWorld world) {
        tokens.removeIf(token -> world.isChunkLoaded(token)
                && !world.getBlockState(token).isOf(ModBlocks.FESTIVAL_TOKEN));
    }

    /** Куда пойти бродящему: стоячее место в округе ярмарки, а не найдётся — прилавок. */
    private BlockPos somewhere(ServerWorld world, Random random) {
        Settlement home = SettlementManager.get(world).byId(settlement()).orElse(null);
        for (int tries = 0; home != null && tries < 8; tries++) {
            int x = fair.heart().getX() + random.nextInt(2 * STROLL + 1) - STROLL;
            int z = fair.heart().getZ() + random.nextInt(2 * STROLL + 1) - STROLL;
            BlockPos feet = HidingPlaces.spotAt(world, home, fair, x, z);
            if (feet != null) {
                return feet;
            }
        }
        return fair.counter();
    }
}
