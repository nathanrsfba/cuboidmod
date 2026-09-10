package com.cuboiddroid.cuboidmod.modules.collapser.recipe;

import net.minecraft.resources.ResourceLocation;

public class CollapsingRecipeData {
    public final ResourceLocation recipeInput;
    public final ResourceLocation recipeInputTag;
    public final int recipeCount;

    public final boolean usesTag;

    public CollapsingRecipeData(String identifier, int recipeCount) {
        this.recipeInput = ResourceLocation.tryParse(identifier); // Returns null if it starts with #
        this.recipeInputTag = ResourceLocation.tryParse(identifier.replaceFirst("^#", ""));

        // this.recipeInput = ForgeRegistries.ITEMS.getValue(itemIdentifier);
        // this.recipeInputTag = TagKey.create(Registries.ITEM, tagIdentifier);
        this.recipeCount = recipeCount;

        this.usesTag = (this.recipeInput == null);
    }
}
