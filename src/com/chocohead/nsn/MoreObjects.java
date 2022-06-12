package com.chocohead.nsn;

import java.util.Objects;
import java.util.function.Supplier;

public class MoreObjects {
	public static <T> T requireNonNullElseGet(T thing, Supplier<? extends T> supplier) {
		return thing != null ? thing : Objects.requireNonNull(Objects.requireNonNull(supplier, "supplier").get(), "supplier result");
	}

	public static int checkFromIndexSize(int fromIndex, int size, int length) {
		if ((length | fromIndex | size) < 0 || size > length - fromIndex) {
            throw new IndexOutOfBoundsException(String.format("Range [%s, %<s + %s) out of bounds for length %s", fromIndex, size, length));
		}

        return fromIndex;
	}
}