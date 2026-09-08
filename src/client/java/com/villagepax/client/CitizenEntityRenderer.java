package com.villagepax.client;

import com.villagepax.VillagePax;
import com.villagepax.entity.CitizenEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;

/**
 * Облик жителя: двуногая модель с руками.
 * <p>
 * Раньше стояла ванильная модель селянина. Она опрятна, но руки у неё
 * сложены на груди навсегда: житель не машет ими при ходьбе и не может
 * ничего держать. А работа должна быть <b>видна</b> — строитель с блоком
 * в руке, лесоруб с топором, курьер с брёвнами. Двуногая модель даёт и то
 * и другое даром: размах рук и ног ваниль считает сама, а держатель
 * предмета — это одна надстройка.
 * <p>
 * Своя модель и анимации на GeckoLib по-прежнему запланированы на фазу 5,
 * когда появятся гномы и пони. До тех пор жителю хватает ванильной
 * двуногой.
 */
public class CitizenEntityRenderer
        extends MobEntityRenderer<CitizenEntity, BipedEntityModel<CitizenEntity>> {

    /**
     * Свой слой модели, а не ванильный слой зомби или игрока: у первого
     * своя текстура, у второго тонкие руки и накидка. Части те же, что
     * у любого двуногого, поэтому раскладка текстуры — классическая:
     * левая рука и нога зеркалят правые.
     */
    public static final EntityModelLayer LAYER =
            new EntityModelLayer(new Identifier(VillagePax.MOD_ID, "citizen"), "main");

    private static final Identifier TEXTURE =
            new Identifier(VillagePax.MOD_ID, "textures/entity/citizen/norman.png");

    public CitizenEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(LAYER)), 0.5f);

        addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(CitizenEntity entity) {
        return TEXTURE;
    }
}
