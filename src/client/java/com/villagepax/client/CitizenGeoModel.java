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

/**
 * Модель жителя: одни кости на всех, разные текстуры и разное сложение.
 * <p>
 * Модель <b>одна</b> на пять народов, и это решение, а не экономия. Кости
 * у людей одинаковые; народ виден ростом, шириной и одеждой, а не тем,
 * что у гнома другое число рук. Пять моделей пришлось бы править впятеро
 * при каждой правке анимации — и они бы разошлись.
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

    public CitizenGeoModel() {
        // Второй довод — «голова поворачивается за взглядом». Житель,
        // который смотрит прямо перед собой, пока игрок ходит вокруг,
        // выглядит куклой; поворот головы даёт живость дешевле любой
        // анимации, и GeckoLib считает его сам.
        super(new Identifier(VillagePax.MOD_ID, "citizen"), true);
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
