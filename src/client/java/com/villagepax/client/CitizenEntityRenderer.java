package com.villagepax.client;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.Stature;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Облик жителя: своя модель, свои движения и своё сложение у каждого народа.
 * <p>
 * Раньше здесь стояла ванильная двуногая заготовка. Она честно махала
 * руками и ногами, но давала всем один силуэт: гном, эльф и норманн
 * отличались только раскраской, и со спины народ было не узнать вовсе.
 * Заказчик сказал прямо: «гномы обычного роста не вкатывает».
 *
 * <h2>Что даёт GeckoLib</h2>
 * Три вещи, которых у ванильной модели нет. <b>Движения по дорожкам</b>:
 * ноги шагают, руки работают, туловище дышит — одновременно и не мешая
 * друг другу (см. {@code CitizenEntity#registerControllers}). <b>Кости
 * поимённо</b>: голову можно увеличить, не трогая остального. И <b>кость
 * ладони</b>, к которой предмет привязан по-настоящему, а не подложен
 * под руку матрицей.
 *
 * <h2>Сложение — данные, а не код</h2>
 * Рост объявляет культура в датапаке, сервер присылает число телом,
 * а ширину и голову считает {@link Stature}. Ни в модели, ни здесь нет
 * слова «гном»: народ, дописанный чужим датапаком завтра, получит свой
 * силуэт той же строкой.
 */
public class CitizenEntityRenderer extends GeoEntityRenderer<CitizenEntity> {

    /**
     * Насколько мельче ребёнок.
     * <p>
     * Три четверти взрослого своего народа, а не человека: гномий ребёнок
     * должен быть ниже гнома, а не ниже норманна. Умножением, а не своим
     * числом — иначе у низкого народа дети оказались бы выше взрослых
     * какого-нибудь высокого.
     */
    private static final float CHILD_SHARE = 0.75f;

    /** Кость, к которой привязан предмет в руке. */
    private static final String HAND = "hand_right";

    public CitizenEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new CitizenGeoModel());
        this.shadowRadius = 0.5f;

        // Работа должна быть видна: строитель держит блок, который ставит,
        // лесоруб топор, курьер груз. Правило мода с первой недели, и оно
        // переезжает сюда целиком — только теперь предмет висит на кости
        // ладони и ходит вместе с рукой, а не рядом с ней.
        addRenderLayer(new BlockAndItemGeoLayer<>(this,
                (bone, entity) -> HAND.equals(bone.getName())
                        ? entity.getMainHandStack() : ItemStack.EMPTY,
                (bone, entity) -> null));
    }

    /**
     * Сложение народа применяется до всякой отрисовки — и один раз.
     * <p>
     * Матрица берёт рост и ширину, кость головы — свою прибавку. Голова
     * при этом остаётся такой же приплюснутой, как всё остальное тело,
     * и это нарочно: у приземистого народа приплюснуто всё, а голова,
     * выправленная обратно в куб, торчала бы на нём чужой.
     */
    @Override
    public void preRender(MatrixStack matrices, CitizenEntity citizen, BakedGeoModel model,
                          VertexConsumerProvider buffers, VertexConsumer buffer,
                          boolean reRender, float delta, int light, int overlay,
                          float red, float green, float blue, float alpha) {
        Stature build = statureOf(citizen);
        matrices.scale(build.girth(), build.height(), build.girth());
        model.getBone("head").ifPresent(head -> setScale(head, build.head()));

        super.preRender(matrices, citizen, model, buffers, buffer, reRender, delta, light,
                overlay, red, green, blue, alpha);
    }

    /**
     * Имя висит над головой, а не там, где кончается невидимый ящик тела.
     * <p>
     * Размер ящика один на всех — его знает поиск пути, и менять его
     * по народу значило бы, что гном не проходит там, где проходит эльф.
     * Но подпись рисуется по ящику, и над гномом она висела бы в полблока
     * от макушки. Смещаем ровно на ту долю, на которую он ниже ящика.
     */
    @Override
    protected void renderLabelIfPresent(CitizenEntity citizen, Text name, MatrixStack matrices,
                                        VertexConsumerProvider buffers, int light) {
        matrices.push();
        matrices.translate(0, (statureOf(citizen).height() - 1.0f) * citizen.getHeight(), 0);
        super.renderLabelIfPresent(citizen, name, matrices, buffers, light);
        matrices.pop();
    }

    /** Сложение этого тела: народное, а у ребёнка — уменьшенное народное. */
    private static Stature statureOf(CitizenEntity citizen) {
        float stature = citizen.stature();
        return Stature.of(citizen.isChildBody() ? stature * CHILD_SHARE : stature);
    }

    private static void setScale(GeoBone bone, float scale) {
        bone.setScaleX(scale);
        bone.setScaleY(scale);
        bone.setScaleZ(scale);
    }

    @Override
    public int getPackedOverlay(CitizenEntity citizen, float u, float delta) {
        return OverlayTexture.getUv(OverlayTexture.getU(u),
                citizen.hurtTime > 0 || citizen.deathTime > 0);
    }
}
