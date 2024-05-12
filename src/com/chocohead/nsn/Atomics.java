package com.chocohead.nsn;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class Atomics {
	public static <T> T compareAndExchange(AtomicReference<T> self, T expect, T update) {
		T out;
		do {
			out = self.get();
		} while (out == expect && !self.compareAndSet(out, update));

		return out;
	}

	public static <T> T compareAndExchange(AtomicReferenceArray<T> self, int index, T expect, T update) {
		T out;
		do {
			out = self.get(index);
		} while (out == expect && !self.compareAndSet(index, out, update));

		return out;
	}
}