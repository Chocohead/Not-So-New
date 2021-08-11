package com.chocohead.nsn;

import java.util.Objects;
import java.util.function.Supplier;

public class MoreObjects {
	public static <T> T requireNonNullElseGet(T thing, Supplier<? extends T> supplier) {
		return thing != null ? thing : Objects.requireNonNull(Objects.requireNonNull(supplier, "supplier").get(), "supplier result");
	}
}