package com.villagepax.client;

import com.villagepax.entity.CitizenEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.VillagerResemblingModel;
import net.minecraft.util.Identifier;

/**
 * Временный облик жителя: ванильная модель селянина.
 * <p>
 * Свои модели и анимации на GeckoLib запланированы на фазу 5, когда появятся
 * гномы и пони. До тех пор жителей отличает не внешность, а имя над головой —
 * его показывает {@code CitizenEntity}, и этого достаточно, чтобы не путать
 * своих с ванильными селянами.
 */
public class CitizenEntityRenderer
        extends MobEntityRenderer<CitizenEntity, VillagerResemblingModel<CitizenEntity>> {

    private static final Identifier TEXTURE =
            new Identifier("minecraft", "textures/entity/villager/villager.png");

    public CitizenEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new VillagerResemblingModel<>(context.getPart(EntityModelLayers.VILLAGER)), 0.5f);
    }

    @Override
    public Identifier getTexture(CitizenEntity entity) {
        return TEXTURE;
    }
}
