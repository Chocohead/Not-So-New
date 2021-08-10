package com.chocohead.nsn;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

public class Sets {
	private static <T> Set<T> checkDuplicates(Set<T> set, int expectedSize) {
		if (set.size() != expectedSize) {
			throw new IllegalArgumentException(expectedSize - set.size() + " duplicated element(s)");
		}

		return set;
	}

	public static <T> Set<T> of(T e1, T e2) {
		return checkDuplicates(ImmutableSet.of(e1, e2), 2);
	}

	public static <T> Set<T> of(T e1, T e2, T e3) {
		return checkDuplicates(ImmutableSet.of(e1, e2, e3), 3);
	}

	public static <T> Set<T> of(T e1, T e2, T e3, T e4) {
		return checkDuplicates(ImmutableSet.of(e1, e2, e3, e4), 4);
	}

	public static <T> Set<T> of(T e1, T e2, T e3, T e4, T e5) {
		return checkDuplicates(ImmutableSet.of(e1, e2, e3, e4, e5), 5);
	}

	public static <T> Set<T> of(T e1, T e2, T e3, T e4, T e5, T e6) {
		return checkDuplicates(ImmutableSet.of(e1, e2, e3, e4, e5, e6), 6);
	}

	public static <T> Set<T> of(T e1, T e2, T e3, T e4, T e5, T e6, T e7) {
		return checkDuplicates(ImmutableSet.of(e1, e2, e3, e4, e5, e6, e7), 7);
	}

	public static <T> Set<T> of(T e1, T e2, T e3, T e4, T e5, T e6, T e7, T e8) {
		return checkDuplicates(ImmutableSet.of(e1, e2, e3, e4, e5, e6, e7, e8), 8);
	}

	public static <T> Set<T> of(T e1, T e2, T e3, T e4, T e5, T e6, T e7, T e8, T e9) {
		return checkDuplicates(ImmutableSet.of(e1, e2, e3, e4, e5, e6, e7, e8, e9), 9);
	}

	public static <T> Set<T> of(T e1, T e2, T e3, T e4, T e5, T e6, T e7, T e8, T e9, T e10) {
		return checkDuplicates(ImmutableSet.of(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10), 10);
	}

	@SafeVarargs
	public static <T> Set<T> of(T... elements) {
		return checkDuplicates(ImmutableSet.copyOf(elements), elements.length);
	}
}