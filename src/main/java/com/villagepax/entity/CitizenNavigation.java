package com.villagepax.entity;

import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;

/**
 * Навигация жителя: та же ванильная, но с предпочтением дороги.
 * <p>
 * Своя навигация, а не примесь в ванильную: примесь меняла бы путь всем
 * мобам мира, включая зомби и коров, — а дорогу предпочитают жители,
 * и только они.
 */
public class CitizenNavigation extends MobNavigation {

    public CitizenNavigation(MobEntity entity, World world) {
        super(entity, world);
    }

    @Override
    protected PathNodeNavigator createPathNodeNavigator(int range) {
        this.nodeMaker = new CitizenPathNodeMaker();
        this.nodeMaker.setCanEnterOpenDoors(true);
        return new PathNodeNavigator(this.nodeMaker, range);
    }
}
