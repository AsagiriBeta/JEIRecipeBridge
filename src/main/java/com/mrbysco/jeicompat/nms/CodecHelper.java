package com.mrbysco.jeicompat.nms;

import com.mrbysco.jeicompat.util.Reflect;

import java.lang.reflect.Method;

final class CodecHelper {
	private final Method serializerCodecMethod;

	CodecHelper(Method serializerCodecMethod) {
		this.serializerCodecMethod = serializerCodecMethod;
	}

	void encodeRecipe(Object serializer, Object buffer, Object recipe) {
		Object codec = Reflect.call(serializerCodecMethod, serializer);
		encode(codec, buffer, recipe);
	}

	void encode(Object codec, Object buffer, Object value) {
		Method encode = Reflect.methodAny(codec.getClass(), new String[] {"encode"}, Object.class, Object.class);
		Reflect.call(encode, codec, buffer, value);
	}
}
