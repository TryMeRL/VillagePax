package com.villagepax.client.hologram;

import com.villagepax.VillagePax;
import com.villagepax.screen.GhostPlan;
import com.villagepax.sim.build.BuildSite;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

/**
 * Призрак здания в мире.
 * <p>
 * Рисуется блоками, а не рамкой: по рамке не видно ни крыши, ни того, куда
 * смотрит дверь, — а именно это игрок и выбирает. План схемы уже посчитан
 * сервером, и его шаги — готовый список «что где стоит».
 * <p>
 * Рамка следа рисуется <b>тоже</b>, и не только для красоты: если призрак
 * из блоков по какой-то причине не появится, по рамке всё равно видно, куда
 * встанет здание и можно ли здесь строить.
 * <p>
 * Цвет — от приговора сервера: зелёный можно, красный нельзя. Проверку
 * делает сервер, потому что правила «где можно строить» должны быть описаны
 * один раз.
 * <p>
 * <b>Событие выбрано не любое.</b> Приёмники вершин мира существуют только
 * между {@code BEFORE_ENTITIES} и {@code BEFORE_DEBUG_RENDER} — так сказано
 * в документации Fabric API, — а после этого {@code consumers()} равен
 * {@code null}. Первая версия рисовала на {@code AFTER_TRANSLUCENT}, то есть
 * <b>позже</b> этого окна: проверка на {@code null} молча выходила, и
 * призрака не было видно вовсе. Отсюда и предупреждение в логе ниже:
 * если событие снова выберут неверно, это должно быть слышно, а не тихо.
 */
public final class HologramRenderer {

    /** Насколько призрак прозрачен. */
    private static final int GHOST_ALPHA = 130;

    /** Сколько блоков рисовать за кадр. Схема ратуши — двести девяносто четыре. */
    private static final int MAX_DRAWN = 4096;

    /** Об отсутствии приёмников вершин говорится один раз, а не каждый кадр. */
    private static boolean warnedAboutConsumers;

    private HologramRenderer() {
    }

    public static void render(WorldRenderContext context) {
        Placement placement = Placement.active();
        if (placement == null || placement.plan() == null || placement.anchor() == null) {
            return;
        }
        if (context.consumers() == null) {
            if (!warnedAboutConsumers) {
                warnedAboutConsumers = true;
                VillagePax.LOGGER.warn("Голограмма не рисуется: приёмники вершин недоступны "
                        + "в этом событии отрисовки мира. Нужно событие между BEFORE_ENTITIES "
                        + "и BEFORE_DEBUG_RENDER.");
            }
            return;
        }

        GhostPlan plan = placement.plan();
        BlockPos anchor = placement.anchor();
        Vec3i size = BuildSite.rotatedSize(plan.size(), placement.rotation());
        Vec3d camera = context.camera().getPos();

        boolean allowed = placement.allowed();
        float red = allowed ? 0.55f : 1.0f;
        float green = allowed ? 1.0f : 0.4f;
        float blue = allowed ? 0.6f : 0.4f;

        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        outline(matrices, context.consumers(), anchor, size, red, green, blue);
        ghost(matrices, context.consumers(), plan, placement, red, green, blue);

        matrices.pop();

        // Своя геометрия обязана быть слита сама: мир уже нарисован, и
        // ждать, что кто-то другой сбросит буфер, нельзя. Сливаются ровно
        // два своих слоя, а не всё подряд: общий слив мог бы вытолкнуть
        // недособранную ванильную геометрию раньше времени.
        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(RenderLayer.getTranslucent());
            immediate.draw(RenderLayer.getLines());
        }
    }

    private static void outline(MatrixStack matrices, VertexConsumerProvider consumers,
                                BlockPos anchor, Vec3i size, float red, float green, float blue) {
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        Box footprint = new Box(anchor.getX(), anchor.getY(), anchor.getZ(),
                anchor.getX() + size.getX(), anchor.getY() + size.getY(), anchor.getZ() + size.getZ());

        WorldRenderer.drawBox(matrices, lines, footprint, red, green, blue, 1.0f);
    }

    private static void ghost(MatrixStack matrices, VertexConsumerProvider consumers,
                              GhostPlan plan, Placement placement,
                              float red, float green, float blue) {
        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider tinted = layer -> new GhostVertexConsumer(
                consumers.getBuffer(RenderLayer.getTranslucent()), red, green, blue, GHOST_ALPHA);

        int drawn = 0;
        for (GhostPlan.Ghost block : plan.blocks()) {
            if (drawn++ >= MAX_DRAWN) {
                return;
            }

            BlockPos at = BuildSite.toWorld(placement.anchor(), plan.size(),
                    placement.rotation(), block.pos());

            matrices.push();
            matrices.translate(at.getX(), at.getY(), at.getZ());
            client.getBlockRenderManager().renderBlockAsEntity(
                    block.state().rotate(placement.rotation()), matrices, tinted,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
    }
}
