package com.villagepax.client;

import com.villagepax.block.entity.RopeBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/**
 * Что висит на верёвке — то и видно.
 * <p>
 * Раньше на ней висели две нарисованные рубахи, одинаковые у всех
 * и ни на что не годные; заказчик назвал это «криво». Теперь рисуется
 * <b>содержимое</b>: кожа — кожей, шерсть — шерстью, и тканевая одежда,
 * когда она в моде появится, повиснет сама, без единой правки здесь.
 * <p>
 * И качается. Ветра в Minecraft нет, но есть время мира: угол берётся
 * синусом от него со сдвигом по месту блока, и соседние верёвки качаются
 * вразнобой, а не строем. Это дешевле любой анимации кадрами и живее её.
 */
public class RopeBlockEntityRenderer implements BlockEntityRenderer<RopeBlockEntity> {

    /** Насколько сильно ведёт вещь на ветру. */
    private static final float SWING = 7.0f;

    /** Как быстро: полный размах примерно за пять секунд. */
    private static final float PACE = 0.02f;

    public RopeBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(RopeBlockEntity rope, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertices, int light, int overlay) {
        if (rope.getWorld() == null) {
            return;
        }
        long time = rope.getWorld().getTime();
        int seed = rope.getPos().hashCode();

        for (int slot = 0; slot < RopeBlockEntity.SIZE; slot++) {
            ItemStack stack = rope.hung().get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            matrices.push();
            // Четыре места вдоль бечевы, вещь висит под ней.
            matrices.translate(0.22 + slot * 0.19, 0.62, 0.5);

            float phase = (time + tickDelta) * PACE + (seed + slot * 37) % 628 / 100.0f;
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(
                    (float) Math.sin(phase) * SWING));
            matrices.scale(0.6f, 0.6f, 0.6f);

            MinecraftClient.getInstance().getItemRenderer().renderItem(stack,
                    ModelTransformationMode.FIXED, light, overlay, matrices, vertices,
                    rope.getWorld(), seed + slot);
            matrices.pop();
        }
    }
}
