package com.mrbysco.jeicompat.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Reflect {
	private Reflect() {
	}

	public static Class<?> clazz(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException exception) {
			throw new IllegalStateException("Missing class " + name, exception);
		}
	}

	public static Class<?> clazzAny(String... names) {
		for (String name : names) {
			try {
				return Class.forName(name);
			} catch (ClassNotFoundException ignored) {
				// try next
			}
		}
		throw new IllegalStateException("Missing class (tried " + String.join(", ", names) + ")");
	}

	public static Constructor<?> ctorAny(Class<?> owner, Class<?>[]... paramSets) {
		for (Class<?>[] params : paramSets) {
			try {
				Constructor<?> constructor = owner.getDeclaredConstructor(params);
				constructor.setAccessible(true);
				return constructor;
			} catch (NoSuchMethodException ignored) {
				// try next
			}
		}
		throw new IllegalStateException("Missing constructor " + owner.getName() + "(...)");
	}

	public static Constructor<?> ctor(Class<?> owner, Class<?>... params) {
		try {
			Constructor<?> constructor = owner.getDeclaredConstructor(params);
			constructor.setAccessible(true);
			return constructor;
		} catch (NoSuchMethodException exception) {
			throw new IllegalStateException("Missing constructor " + owner.getName() + "(...)", exception);
		}
	}

	public static Method method(Class<?> owner, String name, Class<?>... params) {
		try {
			Method method = owner.getMethod(name, params);
			method.setAccessible(true);
			return method;
		} catch (NoSuchMethodException ignored) {
			for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
				try {
					Method method = current.getDeclaredMethod(name, params);
					method.setAccessible(true);
					return method;
				} catch (NoSuchMethodException ignored2) {
					// keep walking
				}
			}
			throw new IllegalStateException("Missing method " + owner.getName() + "#" + name);
		}
	}

	public static Method methodAny(Class<?> owner, String[] names, Class<?>... params) {
		for (String name : names) {
			try {
				return method(owner, name, params);
			} catch (IllegalStateException ignored) {
				// try next
			}
		}
		throw new IllegalStateException("Missing method " + owner.getName() + "#" + String.join("|", names));
	}

	public static Method methodOptional(Class<?> owner, String name, Class<?>... params) {
		try {
			return method(owner, name, params);
		} catch (IllegalStateException ignored) {
			return null;
		}
	}

	public static Iterable<Object> asIterable(Object recipes) {
		if (recipes instanceof Iterable<?> iterable) {
			@SuppressWarnings("unchecked")
			Iterable<Object> cast = (Iterable<Object>) iterable;
			return cast;
		}
		throw new IllegalStateException("Recipe manager returned unsupported type: " + recipes.getClass().getName());
	}

	public static Object call(Method method, Object target, Object... args) {
		try {
			return method.invoke(target, args);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Invoke failed: " + method, exception);
		}
	}

	public static Object getField(Object target, String name) {
		for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
			try {
				Field field = current.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(target);
			} catch (NoSuchFieldException ignored) {
				// keep walking
			} catch (IllegalAccessException exception) {
				throw new IllegalStateException(exception);
			}
		}
		throw new IllegalStateException("Missing field " + name + " on " + target.getClass());
	}

	public static Object staticField(Class<?> owner, String name) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(null);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Missing static field " + owner.getName() + "#" + name, exception);
		}
	}
}
