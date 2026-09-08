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
import net.minecraft.world.RaycastContext;

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

    /** Насколько далеко призрак стоит от игрока по умолчанию. */
    public static final int DEFAULT_RANGE = 8;

    /**
     * Ближе двух блоков призрак упирается в самого игрока, дальше сорока
     * восьми сервер откажет по расстоянию — там же и предел заказа.
     */
    public static final int MIN_RANGE = 2;
    public static final int MAX_RANGE = 48;

    /**
     * Насколько призрак можно поднять или опустить.
     * <p>
     * Опустить нужнее, чем поднять: место под перекрестием — это блок
     * <b>над</b> поверхностью, а фундамент чаще хочется вкопать в землю,
     * а не поставить на траву. Без этого приходилось ломать блок,
     * чтобы прицелиться в получившуюся ямку.
     */
    public static final int MAX_LIFT = 16;

    /** Раз в секунду место переспрашивается даже без движения. */
    private static final int REPROBE_TICKS = 20;

    private static Placement active;

    private final Identifier schematic;
    private GhostPlan plan;

    private BlockPos anchor;
    private BlockPos aimed;
    private BlockRotation rotation = BlockRotation.NONE;

    private int range = DEFAULT_RANGE;
    private int lift;
    private boolean pinned;

    private boolean allowed;
    private String reason = "villagepax.hologram.checking";
    private BlockPos probed;
    private BlockRotation probedRotation;
    private int sinceProbe;

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

    public int range() {
        return range;
    }

    public int lift() {
        return lift;
    }

    public boolean pinned() {
        return pinned;
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

    /** Отодвинуть или придвинуть призрак, не двигаясь самому. */
    public void pushAway(int blocks) {
        range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, range + blocks));
    }

    /**
     * Поднять или опустить призрак.
     * <p>
     * Это и есть «удобное проектирование»: место под перекрестием — блок
     * над поверхностью, и без сдвига вниз фундамент встаёт на траву,
     * а не в землю.
     */
    public void raise(int blocks) {
        lift = Math.max(-MAX_LIFT, Math.min(MAX_LIFT, lift + blocks));
    }

    /**
     * Закрепить призрак на месте — или отпустить.
     * <p>
     * Закреплённый не идёт за взглядом, и вокруг него можно обойти, посмотреть
     * с другой стороны и только потом подтвердить. Поворот и высота при этом
     * по-прежнему меняются: закрепляется место, а не вид.
     */
    public void togglePin() {
        pinned = !pinned;
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
        if (!pinned) {
            aimed = aim(client);
        }
        if (aimed == null || plan == null) {
            // Без плана неизвестен размер, а значит и якорь: примерка ушла бы
            // не на то место, и призрак мигнул бы неверным приговором.
            return;
        }
        anchor = centre(aimed).up(lift);

        // Переспрашивать надо и без движения: закреплённый призрак стоит
        // на месте, а игрок от него отходит — и «слишком далеко» иначе
        // никогда не появится. Заодно так виден чужой дом, размеченный
        // тем же местом секунду назад.
        boolean moved = !anchor.equals(probed) || rotation != probedRotation;
        if (moved || ++sinceProbe >= REPROBE_TICKS) {
            probed = anchor;
            probedRotation = rotation;
            sinceProbe = 0;
            if (moved) {
                reason = "villagepax.hologram.checking";
            }

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeIdentifier(schematic);
            buf.writeBlockPos(anchor);
            buf.writeString(com.villagepax.screen.BuildOrders.nameOf(rotation));
            ClientPlayNetworking.send(TownHallNet.PROBE, buf);
        }
    }

    /**
     * Куда смотрит игрок — свой луч, а не ванильное перекрестие.
     * <p>
     * Ванильное перекрестие видит блоки в пяти шагах: этого хватает, чтобы
     * ударить кайлом, и совсем не хватает, чтобы поставить дом на пригорке
     * напротив. Свой луч бьёт настолько далеко, насколько игрок сам отодвинул
     * призрак, и потому дальность — управляемая величина, а не постоянная.
     * <p>
     * Если луч ни во что не попал (игрок смотрит в небо), призрак встаёт
     * на своей дальности на высоте ног игрока: без этого он исчезал бы,
     * стоило поднять голову.
     */
    private BlockPos aim(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }

        Vec3d eyes = client.player.getCameraPosVec(1.0f);
        Vec3d far = eyes.add(client.player.getRotationVec(1.0f).multiply(range));

        BlockHitResult hit = client.world.raycast(new RaycastContext(eyes, far,
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE,
                client.player));

        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getBlockPos().offset(hit.getSide());
        }
        return BlockPos.ofFloored(far.x, client.player.getY(), far.z);
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
