package com.clockworktown.campboard.compat.jei;

import com.clockworktown.campboard.CampBoardMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class CampBoardJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return CampBoardMod.id("jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(
                new ItemStack(CampBoardMod.CAMP_BOARD_ITEM),
                Component.literal("A physical project board for shared builds."),
                Component.literal("Each placed Camp Board has its own projects, suggestions, tasks, leaders, locations, and virtual material storage."),
                Component.literal("Right-click a placed Camp Board to open its project board.")
        );
    }

    @Override
    public void registerIngredientAliases(IIngredientAliasRegistration registration) {
        ItemStack stack = new ItemStack(CampBoardMod.CAMP_BOARD_ITEM);
        registration.addAlias(stack, "project board");
        registration.addAlias(stack, "camp projects");
        registration.addAlias(stack, "tasks");
        registration.addAlias(stack, "materials");
        registration.addAlias(stack, "suggestions");
    }
}
