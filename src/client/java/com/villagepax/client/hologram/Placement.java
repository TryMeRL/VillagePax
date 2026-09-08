package com.villagepax.client.hologram;

import com.villagepax.screen.GhostPlan;
import com.villagepax.screen.TownHallNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

/**
 * Режим установки: игрок водит призраком здания по земле.
 * <p>
 * Состояние клиентское и одиночное — экран установки у игрока один, как
 * и сам игрок. Запрет на изменяемое статическое состояние в этом моде
 * касается симуляции: там оно порождает расхождение между сервером и
 * сохранением. Здесь ни того, ни другого нет — это то, что нарисовано
 * на стекле перед глазами, и живёт оно до подтверждения.
 * <p>
 * Проверку места делает сервер: клиент спрашивает при каждой смене места
 * или поворота и красит призрак по ответу. Дублировать правила «где можно
 * строить» на клиенте нельзя — второе описание разошлось бы с первым.
 */
public final class Placement {

    /** Как далеко перед игроком встаёт призрак, если тот смотрит в небо. */
    private static final double AHEAD = 6.0;

    private static Placement active;

    private final Identifier schematic;
    private GhostPlan plan;

    private BlockPos anchor;
    private BlockRotation rotation = BlockRotation.NONE;

    private boolean allowed;
    private String reason = "villagepax.hologram.checking";
    private BlockPos probed;
    private BlockRotation probedRotation;

    private Placement(Identifier schematic) {
        this.schematic = schematic;
    }

    // --- жизнь режима ---

    /** Начать установку: план придёт с сервера отдельным пакетом. */
    public static void begin(Identifier schematic) {
        active = new Placement(schematic);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeIdentifier(schematic);
        ClientPlayNetworking.send(TownHallNet.PLAN_REQUEST, buf);
    }

    public static void cancel() {
        active = null;
    }

    public static Placement active() {
        return active;
    }

    public static boolean isActive() {
        return active != null;
    }

    /** План приехал. Если игрок успел отменить — пакет уже не нужен. */
    public static void acceptPlan(GhostPlan plan) {
        if (active != null && active.schematic.equals(plan.schematic())) {
            active.plan = plan;
        }
    }

    public static void acceptVerdict(boolean allowed, String reason) {
        if (active != null) {
            active.allowed = allowed;
            active.reason = reason;
        }
    }

    // --- что показывать ---

    public GhostPlan plan() {
        return plan;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public BlockRotation rotation() {
        return rotation;
    }

    public boolean allowed() {
        return allowed;
    }

    public String reason() {
        return reason;
    }

    public Identifier schematic() {
        return schematic;
    }

    // --- управление ---

    public void rotate() {
        rotation = switch (rotation) {
            case NONE -> BlockRotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> BlockRotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    /** Подтвердить: заказ уходит на сервер ровно туда, где стоял призрак. */
    public void confirm() {
        if (anchor == null || !allowed) {
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeIdentifier(schematic);
        buf.writeBlockPos(anchor);
        buf.writeString(com.villagepax.screen.BuildOrders.nameOf(rotation));
        ClientPlayNetworking.send(TownHallNet.ORDER, buf);

        cancel();
    }

    /**
     * Каждый тик: призрак идёт за взглядом, и при смене места сервер
     * спрашивается заново.
     * <p>
     * Запрос уходит <b>только при смене</b> места или поворота, а не каждый
     * кадр: игрок сдвигается на блок пару раз в секунду, и этого хватает.
     */
    public void follow(MinecraftClient client) {
        BlockPos aim = aim(client);
        if (aim == null || plan == null) {
            // Без плана неизвестен размер, а значит и якорь: примерка ушла бы
            // не на то место, и призрак мигнул бы неверным приговором.
            return;
        }
        anchor = centre(aim);

        if (!anchor.equals(probed) || rotation != probedRotation) {
            probed = anchor;
            probedRotation = rotation;
            reason = "villagepax.hologram.checking";

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeIdentifier(schematic);
            buf.writeBlockPos(anchor);
            buf.writeString(com.villagepax.screen.BuildOrders.nameOf(rotation));
            ClientPlayNetworking.send(TownHallNet.PROBE, buf);
        }
    }

    /**
     * Куда смотрит игрок. По блоку под перекрестием — так место выбирается
     * глазами; если перекрестие в небе, призрак встаёт перед игроком.
     */
    private BlockPos aim(MinecraftClient client) {
        if (client.player == null) {
            return null;
        }
        if (client.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos().offset(hit.getSide());
        }

        Vec3d ahead = client.player.getPos()
                .add(client.player.getRotationVec(1.0f).multiply(AHEAD));
        return BlockPos.ofFloored(ahead.x, client.player.getY(), ahead.z);
    }

    /**
     * Призрак центрируется на месте, куда смотрит игрок, а якорь схемы —
     * её минимальный угол. Без сдвига здание уезжало бы вперёд и вправо
     * от перекрестия, и попасть им куда надо было бы нельзя.
     */
    private BlockPos centre(BlockPos aim) {
        if (plan == null) {
            return aim;
        }

        Vec3i size = com.villagepax.sim.build.BuildSite.rotatedSize(plan.size(), rotation);
        return aim.add(-size.getX() / 2, 0, -size.getZ() / 2);
    }
}
