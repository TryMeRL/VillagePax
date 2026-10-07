package com.villagepax.sim;

import com.villagepax.core.ModTags;
import com.villagepax.core.config.Configs;
import com.villagepax.entity.CitizenEntity;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.BlockState;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;
import java.util.UUID;

/**
 * Земля колонии не для чужих рук.
 * <p>
 * Дизайн-документ записал это в правах с первого дня: хозяин, доверенные,
 * гости — и защита земли от постороннего разрушения, которую можно
 * выключить. До сих пор её не было вовсе, и на общем сервере любой
 * прохожий мог разобрать чужую ратушу, выгрести склад и поджечь поле.
 * В одиночной игре это незаметно — хозяин там единственный игрок, —
 * а в сетевой это первое, о чём спрашивают про любой мод колоний.
 * <p>
 * Гость на земле колонии <b>ходит, смотрит и говорит</b>: открывает двери
 * и калитки, жмёт кнопки, звонит в колокол, заговаривает с жителями
 * и открывает пульт ратуши — тот сам решает, что гостю в нём можно.
 * Всё, что лежит в теге {@code villagepax:guest_usable}, — тегом, чтобы
 * датапак сервера мог открыть гостям и свои блоки. Ломать, ставить,
 * лить из ведра, открывать сундуки и бить жителей гость не может.
 * <p>
 * Деревни народов не защищаются: их берут набегом и походом, и это
 * часть игры, а не порча.
 * <p>
 * Решает только сервер. Клиент, который поставил блок наперёд, получает
 * от сервера настоящее состояние клетки, и призрачный блок исчезает сам.
 */
public final class Protection {

    private Protection() {
    }

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(world instanceof ServerWorld server) || allowed(server, player, pos));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(world instanceof ServerWorld server)) {
                return ActionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState clicked = world.getBlockState(pos);
            boolean holding = !player.getStackInHand(hand).isEmpty();
            // С блоком в руке и присев — это не «открыть дверь», а «поставить
            // рядом с дверью»: ставить гостю нельзя, даже у двери.
            boolean placing = player.isSneaking() && holding;
            if (!placing && clicked.isIn(ModTags.GUEST_USABLE)) {
                if (mayTouch(server, player, pos)) {
                    return ActionResult.PASS;
                }
                // Гостю — только сам блок. Железная дверь, люк, колокол сбоку
                // на щелчок не отвечают, и ваниль тогда пускала в ход то, что
                // в руке: булыжник, огниво, динамит вставали на землю колонии.
                ActionResult used = clicked.onUse(world, player, hand, hit);
                return used.isAccepted() ? used : ActionResult.FAIL;
            }
            if (!allowed(server, player, pos)) {
                return ActionResult.FAIL;
            }
            // Блок из руки встаёт не в щёлкнутую клетку, а рядом, — и та
            // может быть уже за границей: щелчок по внешней грани соседа
            // ставил блок (или огонь) на клетку внутрь колонии.
            return holding && !allowed(server, player, pos.offset(hit.getSide()))
                    ? ActionResult.FAIL : ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack held = player.getStackInHand(hand);
            if (!(world instanceof ServerWorld server) || !(held.getItem() instanceof BucketItem)) {
                return TypedActionResult.pass(held);
            }
            // Ведро льёт не под ноги, а туда, куда смотрят: стоя у границы,
            // гость прежде заливал лавой колонию на пять блоков вглубь.
            if (!allowed(server, player, player.getBlockPos())) {
                return TypedActionResult.fail(held);
            }
            if (player.raycast(REACH, 0f, true) instanceof BlockHitResult aim
                    && aim.getType() == HitResult.Type.BLOCK
                    && (!allowed(server, player, aim.getBlockPos())
                    || !allowed(server, player, aim.getBlockPos().offset(aim.getSide())))) {
                return TypedActionResult.fail(held);
            }
            return TypedActionResult.pass(held);
        });

        // Жителя колонии защищает не земля, а то, чей он: лесоруб за границей
        // в лесу — всё тот же житель, и бить его гостю нельзя и там.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(world instanceof ServerWorld server) || !(entity instanceof CitizenEntity body)) {
                return ActionResult.PASS;
            }
            Optional<Settlement> home = shieldOf(server, player, body);
            home.ifPresent(colony -> deny(player, colony));
            return home.isPresent() ? ActionResult.FAIL : ActionResult.PASS;
        });
    }

    /** Как далеко достаёт ведро: столько же, сколько рука игрока. */
    private static final double REACH = 5.0;

    /**
     * Чья колония заслоняет этого жителя от этого игрока, если заслоняет.
     * <p>
     * Спрашивает и удар рукой, и всякий урон с игроком-виновником: стрела,
     * трезубец, зелье. Прежде охранялся только удар в упор, и гость
     * расстреливал колонистов из лука без последствий.
     */
    public static Optional<Settlement> shieldOf(ServerWorld world, PlayerEntity player,
                                                CitizenEntity body) {
        if (!Configs.get().protectColonies() || player.hasPermissionLevel(2)) {
            return Optional.empty();
        }
        return body.settlementId()
                .flatMap(SettlementManager.get(world)::byId)
                .filter(colony -> !colony.owner().isAutonomous())
                .filter(colony -> !colony.owner().mayBuild(player.getUuid()));
    }

    /** То же, что {@link #allowed}, но молча: гостю, открывшему дверь, отказ не говорят. */
    private static boolean mayTouch(ServerWorld world, PlayerEntity player, BlockPos pos) {
        return !Configs.get().protectColonies() || player.hasPermissionLevel(2)
                || guardedAgainst(SettlementManager.get(world), player.getUuid(), pos).isEmpty();
    }

    /**
     * Можно ли этому игроку трогать эту клетку — и сказать ему, если нельзя.
     * <p>
     * Операторы сервера проходят всегда: чинить чужую колонию по просьбе
     * хозяина — их работа, и защита не должна мешать ей.
     */
    private static boolean allowed(ServerWorld world, PlayerEntity player, BlockPos pos) {
        if (!Configs.get().protectColonies() || player.hasPermissionLevel(2)) {
            return true;
        }
        Optional<Settlement> guarded = guardedAgainst(SettlementManager.get(world),
                player.getUuid(), pos);
        guarded.ifPresent(colony -> deny(player, colony));
        return guarded.isEmpty();
    }

    /**
     * Сказать, почему нет, — в полосу над панелью, а не в чат: щелчков
     * правой кнопкой много, и чат от них захлебнулся бы.
     */
    private static void deny(PlayerEntity player, Settlement colony) {
        player.sendMessage(Text.translatable("villagepax.protection.denied", colony.name()), true);
    }

    /**
     * Чья земля не пускает этого игрока — если чья-то не пускает.
     * <p>
     * Без мира и без событий: правило проверяется модульно, а события
     * только спрашивают его и говорят игроку ответ.
     */
    public static Optional<Settlement> guardedAgainst(SettlementManager manager, UUID player,
                                                     BlockPos pos) {
        return manager.at(pos)
                .filter(colony -> !colony.owner().isAutonomous())
                .filter(colony -> !colony.owner().mayBuild(player));
    }
}
