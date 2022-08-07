package com.chocohead.nsn;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class Optionals {
	public static boolean isEmpty(Optional<?> self) {
		return !self.isPresent();
	}

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

	public static boolean isEmpty(OptionalInt self) {
		return !self.isPresent();
	}

	public static void ifPresentOrElse(OptionalInt self, IntConsumer action, Runnable emptyAction) {
		if (self.isPresent()) {
			action.accept(self.getAsInt());
		} else {
			emptyAction.run();
		}
	}

	public static IntStream stream(OptionalInt self) {
		return self.isPresent() ? IntStream.of(self.getAsInt()) : IntStream.empty();
	}

	public static boolean isEmpty(OptionalLong self) {
		return !self.isPresent();
	}

	public static void ifPresentOrElse(OptionalLong self, LongConsumer action, Runnable emptyAction) {
		if (self.isPresent()) {
			action.accept(self.getAsLong());
		} else {
			emptyAction.run();
		}
	}

	public static LongStream stream(OptionalLong self) {
		return self.isPresent() ? LongStream.of(self.getAsLong()) : LongStream.empty();
	}

	public static boolean isEmpty(OptionalDouble self) {
		return !self.isPresent();
	}

	public static void ifPresentOrElse(OptionalDouble self, DoubleConsumer action, Runnable emptyAction) {
		if (self.isPresent()) {
			action.accept(self.getAsDouble());
		} else {
			emptyAction.run();
		}
	}

	public static DoubleStream stream(OptionalDouble self) {
		return self.isPresent() ? DoubleStream.of(self.getAsDouble()) : DoubleStream.empty();
	}
}