package com.chocohead.nsn;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public class ScopedValue<T> {
	@FunctionalInterface
	public interface FailableCallable<T, E extends Throwable> {
		T call() throws E;
	}

	public static class Carrier {
		static final ThreadLocal<Carrier> POOL = ThreadLocal.withInitial(Carrier::new);
		private final Map<ScopedValue<?>, Object> map;

		Carrier() {
			map = new IdentityHashMap<>();
		}

		private Carrier(Map<ScopedValue<?>, Object> existing) {
			map = new IdentityHashMap<>(existing);
		}

		public <T> Carrier with(ScopedValue<T> key, T value) {
			map.put(key, value);
			return this;
		}

		public <T> Carrier where(ScopedValue<T> key, T value) {
			return new Carrier(map).with(key, value);
		}

		boolean isBound(ScopedValue<?> key) {
			return map.containsKey(key);
		}

		public <T> T get(ScopedValue<T> key) {
			@SuppressWarnings("unchecked")
			T out = (T) map.get(key);
			if (out != null || map.containsKey(key)) {
				return out;
			} else {
				throw new NoSuchElementException("No value for key");
			}
		}

		<T> T orElse(ScopedValue<T> key, T thing) {
			@SuppressWarnings("unchecked")
			T out = (T) map.get(key);
			if (out != null || map.containsKey(key)) {
				return out;
			} else {
				return thing;
			}
		}

		<T, E extends Throwable> T orElseThrow(ScopedValue<T> key, Supplier<? extends E> thrower) throws E {
			@SuppressWarnings("unchecked")
			T out = (T) map.get(key);
			if (out != null || map.containsKey(key)) {
				return out;
			} else {
				throw thrower.get();
			}
		}

		private Carrier merge(Carrier with) {
			Carrier out = new Carrier(with.map);
			out.map.putAll(map);
			return out;
		}

		public <T, E extends Throwable> T call(FailableCallable<? extends T, E> action) throws E {
			Carrier existing = POOL.get();
			try {
				POOL.set(merge(existing));
				return action.call();
			} finally {
				POOL.set(existing);
			}
		}

		public void run(Runnable action) {
			Carrier existing = POOL.get();
			try {
				POOL.set(merge(existing));
				action.run();
			} finally {
				POOL.set(existing);
			}
		}
	}
	private final int hash = ThreadLocalRandom.current().nextInt();

	private ScopedValue() {
	}

	@Override
	public int hashCode() {
		return hash;
	}

	public static <T> Carrier where(ScopedValue<T> key, T value) {
		return new Carrier().with(key, value);
	}

	public static <T> ScopedValue<T> newInstance() {
		return new ScopedValue<>();
	}

	public boolean isBound() {
		return Carrier.POOL.get().isBound(this);
	}

	public T get() {
		return Carrier.POOL.get().get(this);
	}

	public T orElse(T thing) {
		return Carrier.POOL.get().orElse(this, thing);
	}

	public <E extends Throwable> T orElseThrow(Supplier<? extends E> thrower) throws E {
		return Carrier.POOL.get().orElseThrow(this, thrower);
	}
}