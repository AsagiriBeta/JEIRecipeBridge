package com.mrbysco.jeicompat.nms;

import com.mrbysco.jeicompat.util.Reflect;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

final class ReflectionRecipeValidator {
	private final Object airItem;
	private final Class<?> smithingRecipeClass;
	private final Class<?> singleItemRecipeClass;
	private final Method recipeGetSerializer;
	private final CodecHelper codecHelper;
	private final Constructor<?> registryBufCtor;
	private final Method ingredientIsEmpty;
	private final Method ingredientItemStacks;
	private final Method ingredientItems;
	private final Method holderValue;
	private final Method placementInfoIngredients;
	private final Method placementInfoIsImpossibleToPlace;
	private final Method recipePlacementInfo;
	private final Method smithingTemplateIngredient;
	private final Method smithingBaseIngredient;
	private final Method smithingAdditionIngredient;
	private final Method singleItemInput;
	private final Method itemStackIsEmpty;
	private final Method itemStackIs;

	ReflectionRecipeValidator(
			Method recipeGetSerializer,
			Method serializerStreamCodec,
			CodecHelper codecHelper,
			Constructor<?> registryBufCtor) {
		Class<?> itemsClass = Reflect.clazz("net.minecraft.world.item.Items");
		this.airItem = Reflect.staticField(itemsClass, "AIR");

		this.smithingRecipeClass = Reflect.clazz("net.minecraft.world.item.crafting.SmithingRecipe");
		this.singleItemRecipeClass = Reflect.clazz("net.minecraft.world.item.crafting.SingleItemRecipe");
		this.recipeGetSerializer = recipeGetSerializer;
		this.codecHelper = codecHelper;
		this.registryBufCtor = registryBufCtor;

		Class<?> ingredientClass = Reflect.clazz("net.minecraft.world.item.crafting.Ingredient");
		this.ingredientIsEmpty = Reflect.method(ingredientClass, "isEmpty");
		this.ingredientItemStacks = Reflect.methodAny(ingredientClass, new String[] {"itemStacks", "getItems"});
		this.ingredientItems = Reflect.method(ingredientClass, "items");

		Class<?> holderClass = Reflect.clazz("net.minecraft.core.Holder");
		this.holderValue = Reflect.method(holderClass, "value");

		Class<?> placementInfoClass = Reflect.clazz("net.minecraft.world.item.crafting.PlacementInfo");
		this.placementInfoIngredients = Reflect.method(placementInfoClass, "ingredients");
		this.placementInfoIsImpossibleToPlace = Reflect.method(placementInfoClass, "isImpossibleToPlace");

		Class<?> recipeClass = Reflect.clazz("net.minecraft.world.item.crafting.Recipe");
		this.recipePlacementInfo = Reflect.method(recipeClass, "placementInfo");

		this.smithingTemplateIngredient = Reflect.method(smithingRecipeClass, "templateIngredient");
		this.smithingBaseIngredient = Reflect.method(smithingRecipeClass, "baseIngredient");
		this.smithingAdditionIngredient = Reflect.method(smithingRecipeClass, "additionIngredient");
		this.singleItemInput = Reflect.method(singleItemRecipeClass, "input");

		Class<?> itemStackClass = Reflect.clazz("net.minecraft.world.item.ItemStack");
		Class<?> itemClass = Reflect.clazz("net.minecraft.world.item.Item");
		this.itemStackIsEmpty = Reflect.method(itemStackClass, "isEmpty");
		this.itemStackIs = Reflect.method(itemStackClass, "is", itemClass);
	}

	boolean isSafeForClientSync(Object recipe) {
		try {
			if (smithingRecipeClass.isInstance(recipe)) {
				if (optionalIngredientContainsAir(Reflect.call(smithingTemplateIngredient, recipe))) {
					return false;
				}
				if (ingredientContainsAir(Reflect.call(smithingBaseIngredient, recipe))) {
					return false;
				}
				if (optionalIngredientContainsAir(Reflect.call(smithingAdditionIngredient, recipe))) {
					return false;
				}
			}

			if (singleItemRecipeClass.isInstance(recipe)) {
				if (ingredientContainsAir(Reflect.call(singleItemInput, recipe))) {
					return false;
				}
			}

			Object placementInfo = Reflect.call(recipePlacementInfo, recipe);
			if (!(boolean) Reflect.call(placementInfoIsImpossibleToPlace, placementInfo)) {
				for (Object ingredient : (Collection<?>) Reflect.call(placementInfoIngredients, placementInfo)) {
					if (ingredientContainsAir(ingredient)) {
						return false;
					}
				}
			}

			return true;
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	boolean canEncode(Object recipe, Object registryAccess) {
		if (Reflect.call(recipeGetSerializer, recipe) == null) {
			return false;
		}

		Object buffer = null;
		try {
			buffer = registryBufCtor.newInstance(Unpooled.buffer(), registryAccess);
			codecHelper.encodeRecipe(Reflect.call(recipeGetSerializer, recipe), buffer, recipe);
			return true;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return false;
		} finally {
			if (buffer instanceof ByteBuf byteBuf) {
				byteBuf.release();
			}
		}
	}

	private boolean optionalIngredientContainsAir(Object optional) {
		if (!(optional instanceof Optional<?> value)) {
			return false;
		}
		return value.isPresent() && ingredientContainsAir(value.get());
	}

	private boolean ingredientContainsAir(Object ingredient) {
		if (ingredient == null || (boolean) Reflect.call(ingredientIsEmpty, ingredient)) {
			return false;
		}

		for (Object stack : (Iterable<?>) Reflect.call(ingredientItemStacks, ingredient)) {
			if (!(boolean) Reflect.call(itemStackIsEmpty, stack) && (boolean) Reflect.call(itemStackIs, stack, airItem)) {
				return true;
			}
		}

		Object holders = Reflect.call(ingredientItems, ingredient);
		if (holders instanceof java.util.stream.Stream<?> stream) {
			try (stream) {
				var iterator = stream.iterator();
				while (iterator.hasNext()) {
					if (isAirHolder(iterator.next())) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private boolean isAirHolder(Object holder) {
		try {
			return Reflect.call(holderValue, holder) == airItem;
		} catch (RuntimeException ignored) {
			return false;
		}
	}
}
