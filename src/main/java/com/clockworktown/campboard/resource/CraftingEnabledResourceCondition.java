package com.clockworktown.campboard.resource;

import com.clockworktown.campboard.CampBoardMod;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.minecraft.resources.RegistryOps;

public class CraftingEnabledResourceCondition implements ResourceCondition {
    public static final CraftingEnabledResourceCondition INSTANCE = new CraftingEnabledResourceCondition();
    public static final MapCodec<CraftingEnabledResourceCondition> CODEC = MapCodec.unit(() -> INSTANCE);
    public static final ResourceConditionType<CraftingEnabledResourceCondition> TYPE =
            ResourceConditionType.create(CampBoardMod.id("crafting_enabled"), CODEC);

    private CraftingEnabledResourceCondition() {
    }

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(RegistryOps.RegistryInfoLookup registryInfoLookup) {
        return CampBoardMod.config().craftingEnabled();
    }
}
