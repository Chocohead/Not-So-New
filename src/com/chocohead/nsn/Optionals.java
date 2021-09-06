package com.chocohead.nsn;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Optionals {
	public static <T> void ifPresentOrElse(Optional<T> self, Consumer<? super T> action, Runnable emptyAction) {
		if (self.isPresent()) {
			action.accept(self.get());
		} else {
			emptyAction.run();
		}
	}

	public static <T> Optional<? extends T> or(Optional<T> self, Supplier<Optional<? extends T>> supplier) {
		if (self.isPresent()) {
			return self;
		} else {
			return Objects.requireNonNull(Objects.requireNonNull(supplier.get(), "supplier"), "supplier result");
		}
	}

	public static <T> Stream<T> stream(Optional<T> self) {
		return self.isPresent() ? Stream.of(self.get()) : Stream.empty();
	}
}