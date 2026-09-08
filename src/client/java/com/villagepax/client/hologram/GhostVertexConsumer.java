package com.villagepax.client.hologram;

import net.minecraft.client.render.VertexConsumer;

/**
 * Приёмник вершин, который делает блок призраком.
 * <p>
 * Ванильный вызов «нарисовать блок как сущность» цветом и прозрачностью
 * не управляет: он берёт их из модели. Обёртка подменяет цвет каждой
 * вершины — этим и достигается и прозрачность, и красный оттенок отказа.
 * <p>
 * Оттенок домножается, а не заменяет цвет: у листвы и травы он свой,
 * и полная замена превратила бы дом в однотонную заливку, по которой
 * не разобрать ни стен, ни крыши.
 */
public class GhostVertexConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final float red;
    private final float green;
    private final float blue;
    private final int alpha;

    public GhostVertexConsumer(VertexConsumer delegate, float red, float green, float blue,
                               int alpha) {
        this.delegate = delegate;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        delegate.color((int) (r * red), (int) (g * green), (int) (b * blue), alpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void next() {
        delegate.next();
    }

    @Override
    public void fixedColor(int r, int g, int b, int a) {
        delegate.fixedColor(r, g, b, a);
    }

    @Override
    public void unfixColor() {
        delegate.unfixColor();
    }
}
