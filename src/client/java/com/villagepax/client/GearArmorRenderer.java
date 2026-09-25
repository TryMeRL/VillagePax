package com.villagepax.client;

import com.villagepax.VillagePax;
import com.villagepax.item.gear.Gear;
import com.villagepax.item.gear.GearArmorItem;
import com.villagepax.item.gear.GearRendering;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Объёмная броня народов: одна модель и одна развёртка на набор.
 * <p>
 * Какие кости видны, решает сам GeoArmorRenderer по слоту: шлем — голова,
 * нагрудник — тело и руки, поножи — ноги, сапоги — ступни. Поэтому все
 * четыре части набора делят одну модель, и рог шлема не появится на том,
 * кто надел одни сапоги.
 */
public final class GearArmorRenderer extends GeoArmorRenderer<GearArmorItem> {

    private static final Identifier ANIMATIONS =
            new Identifier(VillagePax.MOD_ID, "animations/armor/gear.animation.json");

    public GearArmorRenderer(Gear gear) {
        super(new GeoModel<>() {
            private final Identifier model =
                    new Identifier(VillagePax.MOD_ID, "geo/armor/" + gear.id() + ".geo.json");
            private final Identifier texture =
                    new Identifier(VillagePax.MOD_ID, "textures/armor/" + gear.id() + ".png");

            @Override
            public Identifier getModelResource(GearArmorItem animatable) {
                return model;
            }

            @Override
            public Identifier getTextureResource(GearArmorItem animatable) {
                return texture;
            }

            @Override
            public Identifier getAnimationResource(GearArmorItem animatable) {
                return ANIMATIONS;
            }
        });
    }

    /** Поставить фабрику отрисовщиков: по одному на набор, на все его части. */
    public static void install() {
        Map<Gear, GearArmorRenderer> renderers = new EnumMap<>(Gear.class);
        GearRendering.install(item -> new RenderProvider() {
            @Override
            public BipedEntityModel<LivingEntity> getHumanoidArmorModel(LivingEntity entity,
                    ItemStack stack, EquipmentSlot slot, BipedEntityModel<LivingEntity> original) {
                GearArmorRenderer renderer = renderers.computeIfAbsent(item.gear(),
                        GearArmorRenderer::new);
                renderer.prepForRender(entity, stack, slot, original);
                return renderer;
            }
        });
    }
}
