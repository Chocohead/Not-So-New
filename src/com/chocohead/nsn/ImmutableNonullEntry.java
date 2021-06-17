package com.chocohead.nsn;

import java.util.Map.Entry;
import java.util.Objects;

public final class ImmutableNonullEntry<K, V> implements Entry<K, V> {
	public static <K, V> Entry<K, V> entry(K key, V value) {
		return new ImmutableNonullEntry<>(key, value);
	}

	private final K key;
	private final V value;

	private ImmutableNonullEntry(K key, V value) {
		this.key = Objects.requireNonNull(key);
		this.value = Objects.requireNonNull(value);
	}

	@Override
	public K getKey() {
		return key;
	}

	@Override
	public V getValue() {
		return value;
	}

	@Override
	public V setValue(V value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Entry)) return false;

		Entry<?, ?> that = (Entry<?, ?>) other;
		return key.equals(that.getKey()) && value.equals(that.getValue());
	}

	@Override
	public int hashCode() {
		return key.hashCode() ^ value.hashCode();
	}

	@Override
	public String toString() {
		return key + "=" + value;
	}
}