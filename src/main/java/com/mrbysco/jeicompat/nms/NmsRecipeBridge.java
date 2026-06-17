package com.mrbysco.jeicompat.nms;

import com.mrbysco.jeicompat.JEIRecipeBridgePlugin;
import com.mrbysco.jeicompat.config.PluginConfig;
import com.mrbysco.jeicompat.util.Reflect;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NmsRecipeBridge implements RecipeBridge {
	private static final String FABRIC_CHANNEL = "fabric:recipe_sync";
	private static final String FABRIC_FINISHED_CHANNEL = "fabric:recipe_sync_finished";
	private static final String NEOFORGE_CHANNEL = "neoforge:recipe_content";

	private final Plugin plugin;
	private final boolean available;

	private Object minecraftServer;
	private Object registryAccess;
	private Object serializerRegistry;
	private Method registryGetKey;
	private Method getRecipes;
	private Method holderId;
	private Method holderValue;
	private Method recipeGetSerializer;
	private Method serializerStreamCodec;
	private CodecHelper codecHelper;
	private Method bufWriteVarInt;
	private Method bufWriteResourceLocation;
	private Method resourceKeyLocation;
	private Constructor<?> registryBufCtor;
	private Object recipesCache;
	private int recipeCount;

	private Object recipeTypeRegistry;
	private Method recipeGetType;
	private Method recipeTypeRegistryGetId;
	private Object recipeHolderStreamCodec;
	private Object serverRegistries;
	private Method serializeTagsToNetwork;
	private Constructor<?> updateTagsPacketCtor;

	private Method getHandle;
	private Method connectionSend;
	private Constructor<?> customPayloadPacketCtor;
	private Constructor<?> discardedPayloadCtor;
	private boolean discardedPayloadTakesByteBuf;
	private Object fabricPayloadId;
	private Object fabricFinishedPayloadId;
	private Object neoForgePayloadId;

	private ReflectionRecipeValidator validator;

	public NmsRecipeBridge(Plugin plugin) {
		this.plugin = plugin;
		boolean bridgeAvailable = false;
		ReflectionRecipeValidator resolvedValidator = null;
		try {
			resolveHandles();
			resolvedValidator = new ReflectionRecipeValidator(
					recipeGetSerializer,
					serializerStreamCodec,
					codecHelper,
					registryBufCtor
			);
			bridgeAvailable = true;
		} catch (RuntimeException exception) {
			plugin.getLogger().warning(
					"Could not resolve server internals; recipe sync disabled. " + exception.getMessage()
			);
		}
		this.validator = resolvedValidator;
		this.available = bridgeAvailable;
	}

	private void resolveHandles() {
		Object craftServer = Bukkit.getServer();
		Method getServer = Reflect.method(craftServer.getClass(), "getServer");
		this.minecraftServer = Reflect.call(getServer, craftServer);

		Method registryAccessMethod = Reflect.method(minecraftServer.getClass(), "registryAccess");
		this.registryAccess = Reflect.call(registryAccessMethod, minecraftServer);

		Method getRecipeManager = Reflect.method(minecraftServer.getClass(), "getRecipeManager");
		Object recipeManager = Reflect.call(getRecipeManager, minecraftServer);
		this.getRecipes = Reflect.method(recipeManager.getClass(), "getRecipes");
		Object recipes = Reflect.call(getRecipes, recipeManager);
		this.recipesCache = recipes;
		this.recipeCount = countRecipes(recipes);

		Class<?> builtInRegistries = Reflect.clazz("net.minecraft.core.registries.BuiltInRegistries");
		this.serializerRegistry = Reflect.staticField(builtInRegistries, "RECIPE_SERIALIZER");
		this.registryGetKey = Reflect.methodAny(serializerRegistry.getClass(), new String[] {"getKey", "getId"}, Object.class);

		Class<?> recipeHolder = Reflect.clazz("net.minecraft.world.item.crafting.RecipeHolder");
		this.holderId = Reflect.method(recipeHolder, "id");
		this.holderValue = Reflect.method(recipeHolder, "value");

		Class<?> recipeClass = Reflect.clazz("net.minecraft.world.item.crafting.Recipe");
		this.recipeGetSerializer = Reflect.method(recipeClass, "getSerializer");
		Class<?> recipeSerializerClass = Reflect.clazz("net.minecraft.world.item.crafting.RecipeSerializer");
		this.serializerStreamCodec = Reflect.methodAny(recipeSerializerClass, new String[] {"streamCodec", "packetCodec"});
		this.codecHelper = new CodecHelper(serializerStreamCodec);

		this.recipeTypeRegistry = Reflect.staticField(builtInRegistries, "RECIPE_TYPE");
		this.recipeTypeRegistryGetId = Reflect.methodAny(recipeTypeRegistry.getClass(), new String[] {"getId", "getKey"}, Object.class);
		this.recipeGetType = Reflect.method(recipeClass, "getType");
		this.recipeHolderStreamCodec = Reflect.staticField(recipeHolder, "STREAM_CODEC");

		Method serverRegistriesMethod = Reflect.methodOptional(minecraftServer.getClass(), "registries");
		Class<?> tagNetworkSerialization = Reflect.clazz("net.minecraft.tags.TagNetworkSerialization");
		Class<?> layeredRegistryAccess = Reflect.clazz("net.minecraft.core.LayeredRegistryAccess");
		Class<?> registryAccessClass = Reflect.clazz("net.minecraft.core.RegistryAccess");
		if (serverRegistriesMethod != null) {
			this.serverRegistries = Reflect.call(serverRegistriesMethod, minecraftServer);
			this.serializeTagsToNetwork = Reflect.method(tagNetworkSerialization, "serializeTagsToNetwork", layeredRegistryAccess);
		} else {
			this.serverRegistries = this.registryAccess;
			this.serializeTagsToNetwork = Reflect.method(tagNetworkSerialization, "serializeTagsToNetwork", registryAccessClass);
		}
		Class<?> updateTagsPacket = Reflect.clazz("net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket");
		this.updateTagsPacketCtor = Reflect.ctor(updateTagsPacket, Map.class);

		Class<?> friendlyByteBuf = Reflect.clazz("net.minecraft.network.FriendlyByteBuf");
		this.bufWriteVarInt = Reflect.method(friendlyByteBuf, "writeVarInt", int.class);
		Class<?> resourceLocation = Reflect.clazzAny(
				"net.minecraft.resources.ResourceLocation",
				"net.minecraft.resources.Identifier");
		this.bufWriteResourceLocation = Reflect.methodAny(
				friendlyByteBuf,
				new String[] {"writeResourceLocation", "writeIdentifier"},
				resourceLocation
		);
		Class<?> resourceKey = Reflect.clazz("net.minecraft.resources.ResourceKey");
		this.resourceKeyLocation = Reflect.methodAny(resourceKey, new String[] {"location", "identifier"});

		Class<?> registryBuf = Reflect.clazz("net.minecraft.network.RegistryFriendlyByteBuf");
		this.registryBufCtor = Reflect.ctor(registryBuf, ByteBuf.class, registryAccessClass);

		Class<?> customPayloadPacket = Reflect.clazz("net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket");
		Class<?> customPacketPayload = Reflect.clazz("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
		this.customPayloadPacketCtor = Reflect.ctor(customPayloadPacket, customPacketPayload);
		Class<?> discardedPayload = Reflect.clazz("net.minecraft.network.protocol.common.custom.DiscardedPayload");
		this.discardedPayloadCtor = Reflect.ctorAny(
				discardedPayload,
				new Class<?>[] {resourceLocation, byte[].class},
				new Class<?>[] {resourceLocation, ByteBuf.class}
		);
		this.discardedPayloadTakesByteBuf = discardedPayloadCtor.getParameterTypes()[1] == ByteBuf.class;
		this.fabricPayloadId = newResourceLocation(resourceLocation, FABRIC_CHANNEL);
		this.fabricFinishedPayloadId = newResourceLocation(resourceLocation, FABRIC_FINISHED_CHANNEL);
		this.neoForgePayloadId = newResourceLocation(resourceLocation, NEOFORGE_CHANNEL);

		Class<?> craftPlayer = Reflect.clazz("org.bukkit.craftbukkit.entity.CraftPlayer");
		this.getHandle = Reflect.method(craftPlayer, "getHandle");
	}

	private void refreshRecipes() {
		Method getRecipeManager = Reflect.method(minecraftServer.getClass(), "getRecipeManager");
		Object recipeManager = Reflect.call(getRecipeManager, minecraftServer);
		Object recipes = Reflect.call(getRecipes, recipeManager);
		this.recipesCache = recipes;
		this.recipeCount = countRecipes(recipes);
	}

	private static int countRecipes(Object recipes) {
		if (recipes instanceof Collection<?> collection) {
			return collection.size();
		}

		int count = 0;
		for (Object ignored : Reflect.asIterable(recipes)) {
			count++;
		}
		return count;
	}

	private Object newResourceLocation(Class<?> resourceLocation, String id) {
		try {
			Method parse = resourceLocation.getMethod("parse", String.class);
			return parse.invoke(null, id);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot build resource location " + id, exception);
		}
	}

	private Object newRegistryBuf() {
		try {
			return registryBufCtor.newInstance(Unpooled.buffer(), registryAccess);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot create RegistryFriendlyByteBuf", exception);
		}
	}

	private static byte[] toBytes(Object friendlyByteBuf) {
		ByteBuf buffer = (ByteBuf) friendlyByteBuf;
		byte[] out = new byte[buffer.readableBytes()];
		buffer.getBytes(buffer.readerIndex(), out);
		return out;
	}

	@Override
	public boolean isAvailable() {
		return available;
	}

	@Override
	public int recipeCount() {
		return recipeCount;
	}

	@Override
	public byte[] buildFabricPayload(PluginConfig config, RecipeFilterListener listener) {
		refreshRecipes();
		List<Object> syncableHolders = collectSyncableHolders(config, listener);

		Map<Object, List<Object>> bySerializer = new LinkedHashMap<>();
		for (Object holder : syncableHolders) {
			Object recipe = Reflect.call(holderValue, holder);
			Object serializer = Reflect.call(recipeGetSerializer, recipe);
			bySerializer.computeIfAbsent(serializer, ignored -> new ArrayList<>()).add(holder);
		}

		Object buffer = newRegistryBuf();
		try {
			Reflect.call(bufWriteVarInt, buffer, bySerializer.size());
			for (Map.Entry<Object, List<Object>> entry : bySerializer.entrySet()) {
				Object serializer = entry.getKey();
				List<Object> holders = entry.getValue();
				Object serializerId = Reflect.call(registryGetKey, serializerRegistry, serializer);
				Reflect.call(bufWriteResourceLocation, buffer, serializerId);
				Reflect.call(bufWriteVarInt, buffer, holders.size());
				Object codec = Reflect.call(serializerStreamCodec, serializer);
				for (Object holder : holders) {
					Object id = Reflect.call(holderId, holder);
					Object location = Reflect.call(resourceKeyLocation, id);
					Reflect.call(bufWriteResourceLocation, buffer, location);
					Object recipe = Reflect.call(holderValue, holder);
					codecHelper.encode(codec, buffer, recipe);
				}
			}
			return toBytes(buffer);
		} finally {
			((ByteBuf) buffer).release();
		}
	}

	@Override
	public NeoForgePayload buildNeoForgePayload(PluginConfig config, RecipeFilterListener listener) {
		refreshRecipes();
		List<Object> syncableHolders = collectSyncableHolders(config, listener);

		LinkedHashSet<Object> types = new LinkedHashSet<>();
		for (Object holder : syncableHolders) {
			Object recipe = Reflect.call(holderValue, holder);
			types.add(Reflect.call(recipeGetType, recipe));
		}

		Object buffer = newRegistryBuf();
		try {
			Reflect.call(bufWriteVarInt, buffer, types.size());
			for (Object type : types) {
				int id = (int) Reflect.call(recipeTypeRegistryGetId, recipeTypeRegistry, type);
				Reflect.call(bufWriteVarInt, buffer, id);
			}
			Reflect.call(bufWriteVarInt, buffer, syncableHolders.size());
			for (Object holder : syncableHolders) {
				codecHelper.encode(recipeHolderStreamCodec, buffer, holder);
			}
			return new NeoForgePayload(toBytes(buffer), buildTagsPacket());
		} finally {
			((ByteBuf) buffer).release();
		}
	}

	@Override
	public void sendFabric(Player player, byte[] payload) {
		send(player, fabricPayloadId, payload);
	}

	@Override
	public void sendFabricSyncFinished(Player player) {
		send(player, fabricFinishedPayloadId, new byte[0]);
	}

	@Override
	public void sendNeoForge(Player player, NeoForgePayload payload) {
		send(player, neoForgePayloadId, payload.recipeBytes());
		sendPacket(player, payload.tagPacket());
	}

	private List<Object> collectSyncableHolders(PluginConfig config, RecipeFilterListener listener) {
		Set<String> blacklist = config.recipeBlacklist();
		List<Object> holders = new ArrayList<>();

		for (Object holder : Reflect.asIterable(recipesCache)) {
			String recipeId = recipeId(holder);
			if (isBlacklisted(recipeId, blacklist)) {
				listener.onFiltered(recipeId, "recipe is blacklisted");
				continue;
			}

			if (config.filterInvalidRecipes()) {
				Object recipe = Reflect.call(holderValue, holder);
				if (!validator.isSafeForClientSync(recipe)) {
					listener.onFiltered(recipeId, "ingredient contains air or is otherwise invalid");
					continue;
				}
				if (!validator.canEncode(recipe, registryAccess)) {
					listener.onFiltered(recipeId, "failed server-side encode validation");
					continue;
				}
			}

			holders.add(holder);
		}

		return holders;
	}

	private String recipeId(Object holder) {
		Object id = Reflect.call(holderId, holder);
		Object location = Reflect.call(resourceKeyLocation, id);
		return String.valueOf(location);
	}

	private static boolean isBlacklisted(String recipeId, Set<String> blacklist) {
		return !blacklist.isEmpty() && blacklist.contains(recipeId);
	}

	private Object buildTagsPacket() {
		try {
			Object tags = Reflect.call(serializeTagsToNetwork, null, serverRegistries);
			return updateTagsPacketCtor.newInstance(tags);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot build ClientboundUpdateTagsPacket", exception);
		}
	}

	private void send(Player player, Object payloadId, byte[] payload) {
		try {
			Object serverPlayer = Reflect.call(getHandle, player);
			Object connection = Reflect.getField(serverPlayer, "connection");
			Object data = discardedPayloadTakesByteBuf ? Unpooled.wrappedBuffer(payload) : payload;
			Object discarded = discardedPayloadCtor.newInstance(payloadId, data);
			Object packet = customPayloadPacketCtor.newInstance(discarded);
			sendPacket(connection, packet);
		} catch (ReflectiveOperationException | RuntimeException exception) {
			logSendFailure(player, exception);
		}
	}

	private void sendPacket(Player player, Object packet) {
		try {
			Object serverPlayer = Reflect.call(getHandle, player);
			Object connection = Reflect.getField(serverPlayer, "connection");
			sendPacket(connection, packet);
		} catch (RuntimeException exception) {
			logSendFailure(player, exception);
		}
	}

	private void sendPacket(Object connection, Object packet) {
		if (connectionSend == null) {
			connectionSend = Reflect.method(
					connection.getClass(),
					"send",
					Reflect.clazz("net.minecraft.network.protocol.Packet")
			);
		}
		Reflect.call(connectionSend, connection, packet);
	}

	private void logSendFailure(Player player, Exception exception) {
		plugin.getLogger().warning("Failed to send recipe payload to " + player.getName() + ": " + exception.getMessage());
		if (plugin instanceof JEIRecipeBridgePlugin bridgePlugin && bridgePlugin.getPluginConfig().debug()) {
			exception.printStackTrace();
		}
	}
}
