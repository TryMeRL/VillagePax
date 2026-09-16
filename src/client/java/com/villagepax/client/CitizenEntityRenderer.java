package com.villagepax.client;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.Looks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;

import java.util.HashMap;
import java.util.Map;

/**
 * Облик жителя: двуногая модель с руками и своя текстура на каждого.
 * <p>
 * Раньше стояла ванильная модель селянина. Она опрятна, но руки у неё
 * сложены на груди навсегда: житель не машет ими при ходьбе и не может
 * ничего держать. А работа должна быть <b>видна</b> — строитель с блоком
 * в руке, лесоруб с топором, курьер с брёвнами. Двуногая модель даёт и то
 * и другое даром: размах рук и ног ваниль считает сама, а держатель
 * предмета — это одна надстройка.
 * <p>
 * Текстуру называет сервер (см. {@link Looks}), а клиент только проверяет,
 * что она есть. Проверка нужна ради чужих датапаков: народ, объявивший
 * культуру, но не нарисовавший людей, получит норманнский облик вместо
 * розово-чёрной клетки «текстура не найдена».
 * <p>
 * Своя модель и анимации на GeckoLib по-прежнему запланированы на фазу 5,
 * когда появятся гномы и пони.
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
            new EntityModelLayer(new Identifier(com.villagepax.VillagePax.MOD_ID, "citizen"), "main");

    /**
     * Что из названного сервером у клиента и вправду есть.
     * <p>
     * Ответ помнится: спрашивать хранилище ресурсов каждый кадр у каждого
     * жителя — это поход в файловую систему шестьдесят раз в секунду.
     */
    private static final Map<String, Identifier> KNOWN = new HashMap<>();

    public CitizenEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(LAYER)), 0.5f);

        addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    @Override
    public Identifier getTexture(CitizenEntity entity) {
        return KNOWN.computeIfAbsent(entity.look(), CitizenEntityRenderer::resolve);
    }

    private static Identifier resolve(String look) {
        try {
            Identifier asked = new Identifier(look);
            if (MinecraftClient.getInstance().getResourceManager().getResource(asked).isPresent()) {
                return asked;
            }
            // Ремесла такого мы не рисовали — но народ и пол знаем: пусть
            // человек будет хотя бы своим, пусть и в будничной одежде.
            String path = asked.getPath();
            int craft = path.lastIndexOf('_');
            if (craft > 0) {
                Identifier plain = new Identifier(asked.getNamespace(),
                        path.substring(0, craft) + ".png");
                if (MinecraftClient.getInstance().getResourceManager()
                        .getResource(plain).isPresent()) {
                    return plain;
                }
            }
        } catch (InvalidIdentifierException broken) {
            // Путь пришёл негодный: показать известного человека честнее,
            // чем уронить отрисовку всего мира.
        }
        return Looks.UNKNOWN;
    }
}
