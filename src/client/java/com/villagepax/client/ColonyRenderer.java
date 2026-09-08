package com.villagepax.client;

import com.villagepax.client.hologram.Placement;
import com.villagepax.item.ModItems;
import com.villagepax.screen.ColonyMap;
import com.villagepax.screen.ColonyNet;
import com.villagepax.screen.TownHallNet;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Колония в мире: подписи над зданиями и граница владений.
 * <p>
 * Просьба заказчика — из четырёх направлений «живости» это два. Дома
 * подписаны, и по деревне видно, где что; граница показывает, где своя
 * земля кончается.
 * <p>
 * <b>Граница показывается не всегда.</b> Постоянная рамка на пол-деревни
 * была бы не жизнью, а помехой: смотреть на неё пришлось бы всё время,
 * а нужна она в двух случаях — когда игрок выбирает место для новой
 * колонии и когда ставит здание. Ровно в этих случаях она и появляется:
 * чертёж в руке или включённая голограмма.
 * <p>
 * Карта приходит снимком раз в две секунды и <b>стареет сама</b>: снимок,
 * которому больше {@link #FORGET_AFTER} тиков, забывается. Поэтому ни на
 * уход игрока, ни на роспуск колонии отдельного «сотри» не нужно —
 * а значит, и забыть его негде.
 */
public final class ColonyRenderer {

    /** Дальше подписи не читаются всё равно, а рисовать их — работа зря. */
    private static final double LABEL_RANGE = 48.0;

    /** Через сколько тиков снимок считается устаревшим. Приходит он раз в 40. */
    private static final long FORGET_AFTER = ColonyNet.EVERY * 3L;

    /** Ванильный размер подписи над мобом: знакомый глазу. */
    private static final float LABEL_SCALE = 0.025f;

    /** Высота столбов границы. Низкая рамка не заслоняет деревню. */
    private static final int BORDER_HEIGHT = 4;

    private static final Map<UUID, ColonyMap> known = new HashMap<>();
    private static final Map<UUID, Long> seen = new HashMap<>();

    private ColonyRenderer() {
    }

    /** Свежий снимок с сервера. */
    public static void accept(ColonyMap map, long now) {
        known.put(map.id(), map);
        seen.put(map.id(), now);
    }

    /** Выход из мира: чужая колония в новом мире показываться не должна. */
    public static void forget() {
        known.clear();
        seen.clear();
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || context.consumers() == null) {
            return;
        }

        expire(client.world.getTime());
        if (known.isEmpty()) {
            return;
        }

        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        boolean border = showBorder(client);
        for (ColonyMap map : known.values()) {
            if (border) {
                border(matrices, context.consumers(), map);
            }
            for (ColonyMap.Sign sign : map.signs()) {
                label(client, matrices, context.consumers(), context, sign, camera);
            }
        }

        matrices.pop();

        if (context.consumers() instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(RenderLayer.getLines());
        }
    }

    private static void expire(long now) {
        Iterator<Map.Entry<UUID, Long>> stale = seen.entrySet().iterator();
        while (stale.hasNext()) {
            Map.Entry<UUID, Long> entry = stale.next();
            if (now - entry.getValue() > FORGET_AFTER) {
                known.remove(entry.getKey());
                stale.remove();
            }
        }
    }

    /**
     * Граница нужна, когда игрок выбирает место: с чертежом в руке или
     * с включённой голограммой. В остальное время рамка только мешает.
     */
    private static boolean showBorder(MinecraftClient client) {
        if (Placement.active() != null) {
            return true;
        }
        return client.player.getMainHandStack().isOf(ModItems.TOWN_HALL_BLUEPRINT)
                || client.player.getOffHandStack().isOf(ModItems.TOWN_HALL_BLUEPRINT);
    }

    /**
     * Граница чертится по <b>границам чанков</b>, а не кругом от ратуши:
     * владения считаются чанками, и рамка обязана показывать ту же землю,
     * которую сервер считает своей. Круг обманывал бы по углам.
     */
    private static void border(MatrixStack matrices, VertexConsumerProvider consumers,
                               ColonyMap map) {
        ChunkPos origin = new ChunkPos(map.centre());
        int radius = map.radius();

        double minX = (origin.x - radius) * 16.0;
        double minZ = (origin.z - radius) * 16.0;
        double maxX = (origin.x + radius) * 16.0 + 16.0;
        double maxZ = (origin.z + radius) * 16.0 + 16.0;
        double bottom = map.centre().getY();

        WorldRenderer.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()),
                new Box(minX, bottom, minZ, maxX, bottom + BORDER_HEIGHT, maxZ),
                1.0f, 0.82f, 0.35f, 0.85f);
    }

    /**
     * Подпись над зданием. Слой обычный, а не сквозной: подпись ведёт себя
     * как вещь в мире и прячется за холмом, а не висит поверх всего. Иначе
     * деревня из десяти домов превратилась бы в мешанину надписей, видных
     * сквозь землю.
     */
    private static void label(MinecraftClient client, MatrixStack matrices,
                              VertexConsumerProvider consumers, WorldRenderContext context,
                              ColonyMap.Sign sign, Vec3d camera) {
        Vec3d at = Vec3d.ofCenter(sign.at());
        if (at.squaredDistanceTo(camera) > LABEL_RANGE * LABEL_RANGE) {
            return;
        }

        Text name = Text.translatable(TownHallNet.buildingKey(sign.type()));
        Text text = sign.done()
                ? (sign.level() > 1
                        ? Text.translatable("villagepax.sign.level", name,
                                Text.literal(String.valueOf(sign.level())))
                        : name)
                : Text.translatable("villagepax.sign.building", name);

        TextRenderer fonts = client.textRenderer;
        int background = (int) (client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;

        matrices.push();
        matrices.translate(at.x, at.y, at.z);
        matrices.multiply(context.camera().getRotation());
        matrices.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        Matrix4f position = matrices.peek().getPositionMatrix();
        fonts.draw(text, -fonts.getWidth(text) / 2.0f, 0.0f, 0xFFFFFFFF, false, position,
                consumers, TextRenderer.TextLayerType.NORMAL, background, 0xF000F0);

        matrices.pop();
    }
}
