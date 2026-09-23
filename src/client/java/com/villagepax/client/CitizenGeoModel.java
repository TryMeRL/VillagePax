package com.villagepax.client;

import com.villagepax.VillagePax;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.Looks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Модель жителя: одни кости на всех, разные текстуры и разное сложение.
 * <p>
 * Модель <b>одна на всех, у кого не объявлено своей</b>. Кости у людей
 * одинаковые, и народ виден ростом, шириной и одеждой; писать пять
 * одинаковых моделей значило бы править их впятеро при каждой правке
 * анимации, а потом обнаружить, что они разошлись.
 * <p>
 * Но пони не люди. Заказчик сказал прямо: «сделай чтоб пони выглядели
 * как пони», и они стали четвероногими — с бочкой, гривой, хвостом
 * и четырьмя ногами. Поэтому у народа может быть <b>своя</b> модель,
 * и берётся она по имени: положил рядом {@code citizen_<народ>.geo.json}
 * и такие же движения — получил своё тело, ни строчки кода. Чужой датапак
 * получит то же самое даром.
 * <p>
 * Что общего у человека и коня, так это <b>имена движений</b>: стоит,
 * идёт, работает, дышит. Дорожки в теле жителя названы раз и навсегда,
 * а какие кости под ними поворачиваются — дело модели. Конь «работает»
 * опущенной головой там, где человек машет рукой.
 * <p>
 * Текстуру называет сервер (см. {@link Looks}), а клиент только проверяет,
 * что она есть. Проверка нужна ради чужих датапаков: народ, объявивший
 * культуру, но не нарисовавший людей, получит норманнский облик вместо
 * розово-чёрной клетки «текстура не найдена».
 */
public class CitizenGeoModel extends DefaultedEntityGeoModel<CitizenEntity> {

    /**
     * Что из названного сервером у клиента и вправду есть.
     * <p>
     * Ответ помнится: спрашивать хранилище ресурсов каждый кадр у каждого
     * жителя — это поход в файловую систему шестьдесят раз в секунду.
     */
    private static final Map<String, Identifier> KNOWN = new HashMap<>();

    /** У какого народа есть своё тело — спрошено у клиента однажды. */
    private static final Map<String, Boolean> OWN_BODY = new HashMap<>();

    public CitizenGeoModel() {
        // Второй довод — «голова поворачивается за взглядом». Житель,
        // который смотрит прямо перед собой, пока игрок ходит вокруг,
        // выглядит куклой; поворот головы даёт живость дешевле любой
        // анимации, и GeckoLib считает его сам.
        super(new Identifier(VillagePax.MOD_ID, "citizen"), true);
    }

    @Override
    public Identifier getModelResource(CitizenEntity entity) {
        return ownBody(entity)
                .map(people -> new Identifier(VillagePax.MOD_ID,
                        "geo/entity/citizen_" + people + ".geo.json"))
                .orElseGet(() -> super.getModelResource(entity));
    }

    @Override
    public Identifier getAnimationResource(CitizenEntity entity) {
        return ownBody(entity)
                .map(people -> new Identifier(VillagePax.MOD_ID,
                        "animations/entity/citizen_" + people + ".animation.json"))
                .orElseGet(() -> super.getAnimationResource(entity));
    }

    /**
     * Народ этого тела, если у него есть своя модель.
     * <p>
     * Спрашивается у хранилища ресурсов один раз на народ: ходить
     * в файловую систему каждый кадр за каждым жителем нельзя.
     */
    private static Optional<String> ownBody(CitizenEntity entity) {
        return Looks.cultureOf(entity.look()).filter(people ->
                OWN_BODY.computeIfAbsent(people, name ->
                        MinecraftClient.getInstance().getResourceManager()
                                .getResource(new Identifier(VillagePax.MOD_ID,
                                        "geo/entity/citizen_" + name + ".geo.json"))
                                .isPresent()));
    }

    @Override
    public Identifier getTextureResource(CitizenEntity entity) {
        return KNOWN.computeIfAbsent(entity.look(), CitizenGeoModel::resolve);
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
