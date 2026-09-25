package com.villagepax.block;

import com.villagepax.block.entity.TownHallBlockEntity;
import com.villagepax.screen.TownHallConsole;
import com.villagepax.screen.VillageNeeds;
import com.villagepax.sim.Settlement;
import com.villagepax.sim.SettlementManager;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Ратуша — центр поселения и точка входа в его интерфейс.
 */
public class TownHallBlock extends BlockWithEntity {

    public TownHallBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TownHallBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        // BlockWithEntity по умолчанию невидим — это нужно только тем блокам,
        // которые целиком рисуются рендерером блок-энтити.
        return BlockRenderType.MODEL;
    }

    /**
     * Обычный щелчок открывает пульт колонии, Shift — сундук ратуши.
     * <p>
     * Пульт важнее: это единственное окно во всё, что происходит в колонии.
     * Сундук остаётся начальным хранилищем и тем местом, куда игрок кладёт
     * материалы, — вкладка склада прямо об этом и говорит.
     * <p>
     * Без колонии пульту показывать нечего, и тогда щелчок открывает сундук:
     * ратуша, поставленная не чертежом, — просто ящик.
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof TownHallBlockEntity hall)) {
            return ActionResult.PASS;
        }

        if (world instanceof ServerWorld serverWorld) {
            SettlementManager manager = SettlementManager.get(serverWorld);
            Settlement colony = hall.settlementId().flatMap(manager::byId).orElse(null);
            if (colony != null && TownHallConsole.yours(colony, player.getUuid())) {
                if (!player.isSneaking()) {
                    player.openHandledScreen(new TownHallConsole(serverWorld, colony, pos));
                    return ActionResult.CONSUME;
                }
            } else if (colony != null && colony.owner().isAutonomous()) {
                // Ратуша деревни народа — не сундук для прохожего. Прежде
                // присевший игрок открывал её склад и выносил что хотел:
                // «не моя деревня, но брать блоки могу оттуда». Теперь она
                // говорит, что деревне нужно, и принимает помощь.
                if (!VillageNeeds.donate(serverWorld, manager, colony, player,
                        player.getStackInHand(hand))) {
                    VillageNeeds.tell(serverWorld, colony, player);
                }
                return ActionResult.CONSUME;
            } else if (colony != null && !colony.owner().mayBuild(player.getUuid())) {
                // Чужая колония: пульт не откроется, склад — тем более. Склад
                // открыт хозяину и тем, кому он доверил свою землю.
                player.sendMessage(Text.translatable("villagepax.town_hall.not_yours",
                        Text.literal(colony.name())), false);
                return ActionResult.CONSUME;
            }
        }

        player.openHandledScreen(hall);
        return ActionResult.CONSUME;
    }

    /** Содержимое хранилища при сносе высыпается, а не исчезает. */
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof TownHallBlockEntity hall) {
            ItemScatterer.spawn(world, pos, hall);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    /**
     * Снос ратуши <b>не</b> распускает колонию.
     * <p>
     * Это то же правило, что и для войны: здания повреждаются и восстанавливаются,
     * но двести часов работы не должны исчезать от одного неверного клика.
     * Поселение остаётся, ратушу нужно отстроить заново.
     */
    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (world instanceof ServerWorld serverWorld) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof TownHallBlockEntity townHall) {
                townHall.settlementId()
                        .flatMap(id -> SettlementManager.get(serverWorld).byId(id))
                        .ifPresent(settlement -> player.sendMessage(
                                Text.translatable("villagepax.town_hall.destroyed", settlement.name()), false));
            }
        }
        super.onBreak(world, pos, state, player);
    }
}
