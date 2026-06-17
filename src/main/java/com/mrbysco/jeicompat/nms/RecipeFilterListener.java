package com.mrbysco.jeicompat.nms;

@FunctionalInterface
public interface RecipeFilterListener {
	void onFiltered(String recipeId, String reason);
}
