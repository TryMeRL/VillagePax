package com.villagepax.client;

import com.villagepax.VillagePax;
import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.Looks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.molang.LazyVariable;
import software.bernie.geckolib.core.molang.MolangParser;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

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

    /**
     * Сколько прошли ноги: путь шага, по которому качаются ноги и руки.
     * <p>
     * Шаг в движениях жителя — не часы, а molang-выражение от пройденного
     * пути, как у ванильного моба: {@code math.cos(query.villagepax_stride * 38.17)}.
     * Движение по часам скользит ступнями — медленный житель перебирает
     * ногами как бегун, быстрый семенит, — а подгонять скорость дорожки
     * под ход нельзя: GeckoLib умножает на неё всё прошедшее время,
     * и каждая перемена скорости дёргала бы ноги в новую фазу.
     */
    public static final String STRIDE = "query.villagepax_stride";

    /**
     * Насколько широк шаг: от нуля у стоящего до единицы у бегущего.
     * <p>
     * Одна дорожка «идёт» покрывает и стояние, и бег: у стоящего размах
     * ноль, и ноги прямые без всякого перехода между «стоит» и «идёт».
     */
    public static final String STRIDE_AMOUNT = "query.villagepax_stride_amount";

    public CitizenGeoModel() {
        // Голова поворачивается за взглядом — но не силами GeckoLib:
        // она поворот головы ставит, затирая позу, а у коня голова
        // в покое наклонена и сама кивает на ходу. Поворот прибавляется
        // в setCustomAnimations.
        super(new Identifier(VillagePax.MOD_ID, "citizen"));
    }

    /**
     * Завести переменные шага до первой загрузки движений.
     * <p>
     * Разбор выражения ищет переменные по имени в миг загрузки файла,
     * и неизвестное имя стало бы нулём навсегда. Поэтому — при запуске
     * клиента, раньше, чем прочитается первый набор ресурсов.
     */
    public static void registerVariables() {
        MolangParser.INSTANCE.register(new LazyVariable(STRIDE, 0));
        MolangParser.INSTANCE.register(new LazyVariable(STRIDE_AMOUNT, 0));
    }

    @Override
    public void applyMolangQueries(CitizenEntity citizen, double animTime) {
        super.applyMolangQueries(citizen, animTime);
        // С долей тика, как у ванильной модели: без неё шаг шёл бы
        // рывками двадцать раз в секунду при сотне кадров.
        float delta = MinecraftClient.getInstance().getTickDelta();
        MolangParser parser = MolangParser.INSTANCE;
        parser.setMemoizedValue(STRIDE, () -> citizen.limbAnimator.getPos(delta));
        parser.setMemoizedValue(STRIDE_AMOUNT,
                () -> Math.min(1.0f, citizen.limbAnimator.getSpeed(delta)));
    }

    /**
     * Голова смотрит за взглядом — поверх позы, а не вместо неё.
     * <p>
     * Житель, который смотрит прямо перед собой, пока игрок ходит вокруг,
     * выглядит куклой; поворот головы даёт живость дешевле любой анимации.
     * <p>
     * <b>Прибавка обязана не копиться</b>, и сама GeckoLib этого не
     * гарантирует. Всякая установка поворота помечает кость «изменённой»,
     * а изменённую кость процессор на следующем кадре <b>не сбрасывает</b>
     * к позе: считает, что её двигает движение. Кость же общая на всех
     * жителей одного тела. Без снятия пометки голова второго жителя
     * начинала с поворота первого, а своя прибавка ложилась сверху, —
     * и головы дёргались туда-сюда по кругу; поймал заказчик глазами
     * в первом же запуске. Поэтому пометка снимается сразу: следующий
     * кадр честно ставит голову заново из позы и движения.
     */
    @Override
    public void setCustomAnimations(CitizenEntity citizen, long instanceId,
                                    AnimationState<CitizenEntity> state) {
        CoreGeoBone head = getAnimationProcessor().getBone("head");
        if (head == null) {
            return;
        }
        EntityModelData look = state.getData(DataTickets.ENTITY_MODEL_DATA);
        head.setRotX(head.getRotX() + look.headPitch() * MathHelper.RADIANS_PER_DEGREE);
        head.setRotY(head.getRotY() + look.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
        head.resetStateChanges();
    }

    /**
     * Забыть всё, что спрошено у хранилища ресурсов.
     * <p>
     * Зовётся при перезагрузке набора ресурсов. Без этого оба словаря
     * живут до перезапуска игры, и набор, добавивший народу своё тело,
     * не действует вовсе; а набор, у которого тело отобрали, оставляет
     * клиент просить каждый кадр файл, которого больше нет.
     */
    public static void forget() {
        KNOWN.clear();
        OWN_BODY.clear();
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
