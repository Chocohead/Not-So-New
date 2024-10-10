package com.chocohead.nsn;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.NavigableMap;

import com.google.common.collect.ImmutableMap;

import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap.BasicEntry;

public class Maps {
	public static <K, V> Entry<K, V> entry(K key, V value) {
		return new ImmutableNonullEntry<>(key, value);
	}

	@SuppressWarnings("unchecked")
	public static <K, V> Entry<K, V> copyOf(Entry<? extends K, ? extends V> entry) {
		return Objects.requireNonNull(entry) instanceof ImmutableNonullEntry ? (Entry<K, V>) entry : entry(entry.getKey(), entry.getValue());
	}

	public static final class ImmutableNonullEntry<K, V> implements Entry<K, V> {
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

	public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
		return ImmutableMap.<K, V>builder().put(k1, v1).put(k2, v2).put(k3, v3).put(k4, v4).put(k5, v5).put(k6, v6).build();
	}

	public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
		return ImmutableMap.<K, V>builder().put(k1, v1).put(k2, v2).put(k3, v3).put(k4, v4).put(k5, v5).put(k6, v6).put(k7, v7).build();
	}

	public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8) {
		return ImmutableMap.<K, V>builder().put(k1, v1).put(k2, v2).put(k3, v3).put(k4, v4).put(k5, v5).put(k6, v6).put(k7, v7).put(k8, v8).build();
	}

	public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
		return ImmutableMap.<K, V>builder().put(k1, v1).put(k2, v2).put(k3, v3).put(k4, v4).put(k5, v5).put(k6, v6).put(k7, v7).put(k8, v8).put(k9, v9).build();
	}

	public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
		return ImmutableMap.<K, V>builder().put(k1, v1).put(k2, v2).put(k3, v3).put(k4, v4).put(k5, v5).put(k6, v6).put(k7, v7).put(k8, v8).put(k9, v9).put(k10, v10).build();
	}

	public static <K, V> Entry<K, V> pollFirstEntry(Map<K, V> self) {
		if (self instanceof NavigableMap<?, ?>) {
			return ((NavigableMap<K, V>) self).pollFirstEntry();
		} else {
			Iterator<Entry<K, V>> it = self.entrySet().iterator();

			if (it.hasNext()) {
				Entry<K, V> entry = it.next();
				entry = new BasicEntry<>(entry.getKey(), entry.getValue());
				it.remove();
				return entry;
			} else {
				return null;
			}
		}
	}
}