package com.villagepax.client;

import com.villagepax.entity.CitizenEntity;
import com.villagepax.entity.Looks;
import com.villagepax.entity.Marks;
import com.villagepax.entity.Stature;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <h2>Приметы — имя кости, а не код</h2>
 * Борода гнома, уши эльфа, поля шляпы и наплечники — кости одной модели
 * с условием в имени ({@link Marks}). Отрисовщик прячет те, чьё условие
 * не совпало со словами облика, и больше о них ничего не знает.
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

    /** Кость, к которой привязан предмет у двуногого. */
    private static final String HAND = "hand_right";

    /** И у четвероногого: что возят — на спину, что чинят — в зубы. */
    private static final String PACK = "pack";
    private static final String TEETH = "muzzle";

    /**
     * Кости с условием в каждой модели — разобранные один раз.
     * <p>
     * По модели, а не по кадру: кость и её условие не меняются, пока
     * не перечитан набор ресурсов, а разбирать имена у каждого жителя
     * каждый кадр значило бы строить строки шестьдесят раз в секунду.
     */
    private static final Map<BakedGeoModel, List<Marked>> MARKED = new IdentityHashMap<>();

    /** Слова облика — тоже один раз на облик, их меньше сотни. */
    private static final Map<String, Set<String>> WORDS = new HashMap<>();

    /** Кость с условием. */
    private record Marked(GeoBone bone, Marks marks) {
    }

    public CitizenEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new CitizenGeoModel());
        this.shadowRadius = 0.5f;

        // Работа должна быть видна: строитель держит блок, который ставит,
        // лесоруб топор, курьер груз. Правило мода с первой недели, и оно
        // переезжает сюда целиком — только теперь предмет висит на кости
        // ладони и ходит вместе с рукой, а не рядом с ней.
        addRenderLayer(new BlockAndItemGeoLayer<CitizenEntity>(this,
                CitizenEntityRenderer::carried, (bone, entity) -> null) {
            @Override
            protected ModelTransformationMode getTransformTypeForStack(
                    GeoBone bone, ItemStack stack, CitizenEntity entity) {
                // Груз на спине лежит, а не держится: положение брошенной
                // вещи — единственное, в котором сноп не торчит из холки
                // ребром.
                return PACK.equals(bone.getName())
                        ? ModelTransformationMode.GROUND
                        : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
            }
        });
    }

    /**
     * Что у этого тела в руках — и где именно.
     * <p>
     * У двуногого один ответ: в руке. У коня рук нет, и правило «работа
     * видна» пришлось бы отменять — вместо этого оно переехало на спину
     * и в зубы. Делит их <b>прочность</b>: чинить можно топор и кирку,
     * их конь несёт в зубах; всё прочее — брёвна, снопы, камень — это
     * груз, и он едет на спине.
     * <p>
     * Разделение по прочности, а не по списку предметов: список пришлось
     * бы дописывать при каждом новом инструменте, включая инструменты
     * чужих модов, — а прочность у них есть и так.
     */
    private static ItemStack carried(GeoBone bone, CitizenEntity citizen) {
        ItemStack load = citizen.getMainHandStack();
        if (load.isEmpty()) {
            return ItemStack.EMPTY;
        }
        String where = bone.getName();
        if (HAND.equals(where)) {
            return load;
        }
        if (TEETH.equals(where)) {
            return load.isDamageable() ? load : ItemStack.EMPTY;
        }
        if (PACK.equals(where)) {
            return load.isDamageable() ? ItemStack.EMPTY : load;
        }
        return ItemStack.EMPTY;
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
        dress(citizen, model);

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

    /** На сколько выше подписи встаёт сказанное: строка и промежуток. */
    private static final float SPEECH_RISE = 0.3f;

    /**
     * Сказанное — строкой над подписью, в кавычках: имя и слово не спутать.
     * <p>
     * Рисуется всегда, а не только когда видна подпись: спрятавшийся ребёнок
     * без подписи всё равно кричит «Нашёл!», когда его нашли. Поправка на рост
     * — та же, что у подписи: над гномом фраза висела бы в полблока от макушки.
     */
    @Override
    public void render(CitizenEntity citizen, float yaw, float delta, MatrixStack matrices,
                       VertexConsumerProvider buffers, int light) {
        super.render(citizen, yaw, delta, matrices, buffers, light);
        citizen.speech().ifPresent(line -> {
            matrices.push();
            matrices.translate(0, SPEECH_RISE
                    + (statureOf(citizen).height() - 1.0f) * citizen.getHeight(), 0);
            super.renderLabelIfPresent(citizen, Text.translatable("villagepax.speech", line),
                    matrices, buffers, light);
            matrices.pop();
        });
    }

    /**
     * Показать приметы этого жителя и спрятать чужие.
     * <p>
     * Кости у модели общие на всех жителей одного тела, поэтому ставится
     * и «показать», и «спрятать» — каждый раз: иначе борода, показанная
     * гному, осталась бы на следующем за ним эльфе.
     */
    private static void dress(CitizenEntity citizen, BakedGeoModel model) {
        List<Marked> marked = MARKED.computeIfAbsent(model, CitizenEntityRenderer::marked);
        if (marked.isEmpty()) {
            return;
        }
        Set<String> words = WORDS.computeIfAbsent(
                citizen.look() + (citizen.isChildBody() ? "#child" : "#adult"),
                key -> wordsOf(citizen));
        for (Marked each : marked) {
            each.bone().setHidden(!each.marks().fits(words));
        }
    }

    /**
     * Слова облика и пора жизни.
     * <p>
     * Пора — не из облика: ребёнок носит ту же кожу, что взрослый его
     * народа и пола, а бороды у него быть не должно. Её тело знает само.
     */
    private static Set<String> wordsOf(CitizenEntity citizen) {
        Set<String> words = new HashSet<>(Looks.words(citizen.look()));
        words.add(citizen.isChildBody() ? "child" : "adult");
        return Set.copyOf(words);
    }

    private static List<Marked> marked(BakedGeoModel model) {
        List<Marked> found = new ArrayList<>();
        for (GeoBone bone : model.topLevelBones()) {
            collect(bone, found);
        }
        return List.copyOf(found);
    }

    private static void collect(GeoBone bone, List<Marked> found) {
        Marks.of(bone.getName()).ifPresent(marks -> found.add(new Marked(bone, marks)));
        for (GeoBone child : bone.getChildBones()) {
            collect(child, found);
        }
    }

    /**
     * Забыть разобранное: зовётся при перезагрузке набора ресурсов.
     * <p>
     * После перезагрузки у тел новые кости, и старые ссылки держали бы
     * в памяти прежние модели и прятали бы кости, которых уже не рисуют.
     */
    public static void forget() {
        MARKED.clear();
        WORDS.clear();
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
