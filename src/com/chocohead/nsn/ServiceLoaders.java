package com.chocohead.nsn;

import java.util.Iterator;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ServiceLoaders {
	public interface Provider<T> extends Supplier<T> {
		Class<? extends T> type();

        @Override
        T get();
	}

	public static <T> Optional<T> findFirst(ServiceLoader<T> self) {
        Iterator<T> iterator = self.iterator();

        if (iterator.hasNext()) {
            return Optional.of(iterator.next());
        } else {
            return Optional.empty();
        }
    }

	public static <T> Stream<Provider<T>> stream(ServiceLoader<T> self) {
		return StreamSupport.stream(new Spliterator<Provider<T>>() {
			private final Iterator<T> it = self.iterator();

			@Override
			public long estimateSize() {
				return Long.MAX_VALUE; //Unknown size
			}

			@Override
			public int characteristics() {
				return ORDERED;
			}

			@Override
			public boolean tryAdvance(Consumer<? super Provider<T>> action) {
				while (true) {
					try {
						if (!it.hasNext()) break;
						action.accept(new Provider<T>() {
							private final T provider = it.next();

							@Override
							@SuppressWarnings("unchecked") //As provider is T, provider#getClass is... ? extends Object
							public Class<? extends T> type() {
								return (Class<? extends T>) provider.getClass();
							}

							@Override
							public T get() {
								return provider;
							}
						});
						return true;
					} catch (ServiceConfigurationError e) {
						//Have to abandon this provider and try the next one
					}
				}

				return false;
			}

			@Override
			public Spliterator<Provider<T>> trySplit() {
				return null; //No splitting
			}
		}, false);
	}
}