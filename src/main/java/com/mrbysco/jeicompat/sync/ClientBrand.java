package com.mrbysco.jeicompat.sync;

import java.util.Locale;

public enum ClientBrand {
	FABRIC,
	NEOFORGE,
	OTHER;

	public static ClientBrand fromBrand(String brand) {
		if (brand == null) {
			return OTHER;
		}

		String normalized = brand.toLowerCase(Locale.ROOT);
		if (normalized.contains("fabric")) {
			return FABRIC;
		}
		if (normalized.contains("neoforge")) {
			return NEOFORGE;
		}
		return OTHER;
	}

	public boolean isSupported() {
		return this == FABRIC || this == NEOFORGE;
	}
}
