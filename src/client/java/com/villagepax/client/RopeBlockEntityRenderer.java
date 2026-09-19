package com.villagepax.client;

import com.villagepax.block.LaundryBlock;
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
            //
            // Шаг и размер связаны намертво: вещь занимает ровно столько,
            // сколько ей отведено. Первая версия рисовала в размер 0.6
            // с шагом 0.19 — вещи налезали друг на друга втрое, и заказчик
            // сказал «перекрывается текстура». Четыре места на блок — это
            // четверть блока на каждое, и больше взять неоткуда.
            float step = 1.0f / RopeBlockEntity.SIZE;
            // Через одну — чуть глубже: так они не сливаются в полосу
            // и не спорят гранями, стоя в одной плоскости.
            float depth = slot % 2 == 0 ? 0.46f : 0.54f;
            float along = step / 2 + slot * step;

            // Вдоль бечевы, а не по мировой оси. Верёвку теперь можно
            // натянуть в любую сторону, и вещи обязаны висеть НА НЕЙ:
            // счёт по иксу оставил бы их болтаться поперёк, в воздухе
            // рядом с верёвкой, и это было бы хуже прежней неподвижной.
            boolean acrossX = rope.getCachedState().get(LaundryBlock.FACING)
                    .getAxis() == net.minecraft.util.math.Direction.Axis.X;
            if (acrossX) {
                matrices.translate(depth, 0.66, along);
            } else {
                matrices.translate(along, 0.66, depth);
            }

            float phase = (time + tickDelta) * PACE + (seed + slot * 37) % 628 / 100.0f;
            // И качается поперёк себя: вещь на верёвке ходит от ветра
            // вбок, а не вдоль бечевы. Ось качания поворачивается вместе
            // с верёвкой, иначе повёрнутое бельё колыхалось бы, врезаясь
            // в собственную бечеву.
            float swing = (float) Math.sin(phase) * SWING;
            matrices.multiply(acrossX
                    ? RotationAxis.POSITIVE_X.rotationDegrees(swing)
                    : RotationAxis.POSITIVE_Z.rotationDegrees(swing));
            if (acrossX) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
            }
            matrices.scale(step, step, step);

            MinecraftClient.getInstance().getItemRenderer().renderItem(stack,
                    ModelTransformationMode.FIXED, light, overlay, matrices, vertices,
                    rope.getWorld(), seed + slot);
            matrices.pop();
        }
    }
}
