package com.mrbysco.jeicompat.util;

import java.util.function.Supplier;

public final class Lazy<T> {
	private final Supplier<T> supplier;
	private volatile T value;

	public Lazy(Supplier<T> supplier) {
		this.supplier = supplier;
	}

	public T get() {
		T current = value;
		if (current == null) {
			synchronized (this) {
				current = value;
				if (current == null) {
					current = supplier.get();
					value = current;
				}
			}
		}
		return current;
	}

	public void invalidate() {
		value = null;
	}
}
