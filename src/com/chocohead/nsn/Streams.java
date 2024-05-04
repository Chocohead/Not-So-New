package com.chocohead.nsn;

import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators.AbstractIntSpliterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import java.util.stream.StreamSupport;

public class Streams {
	public static <T> Stream<T> ofNullable(T thing) {
		return thing != null ? Stream.of(thing) : Stream.empty();
	}

	public static <T, R> Stream<R> mapMulti(Stream<T> self, BiConsumer<? super T, ? super Consumer<R>> mapper) {
		Objects.requireNonNull(mapper, "mapper");
		return self.flatMap(element -> {
			Builder<R> builder = Stream.builder();
			mapper.accept(element, builder);
			return builder.build();
		});
	}

	public static <T> IntStream mapMultiToInt(Stream<T> self, BiConsumer<? super T, ? super IntConsumer> mapper) {
		Objects.requireNonNull(mapper, "mapper");
		return self.flatMapToInt(element -> {
			IntStream.Builder builder = IntStream.builder();
			mapper.accept(element, builder);
			return builder.build();
		});
	}

	public static <T> LongStream mapMultiToLong(Stream<T> self, BiConsumer<? super T, ? super LongConsumer> mapper) {
		Objects.requireNonNull(mapper, "mapper");
		return self.flatMapToLong(element -> {
			LongStream.Builder builder = LongStream.builder();
			mapper.accept(element, builder);
			return builder.build();
		});
	}

	public static <T> DoubleStream mapMultiToDouble(Stream<T> self, BiConsumer<? super T, ? super DoubleConsumer> mapper) {
		Objects.requireNonNull(mapper, "mapper");
		return self.flatMapToDouble(element -> {
			DoubleStream.Builder builder = DoubleStream.builder();
			mapper.accept(element, builder);
			return builder.build();
		});
	}

	public static <T> Stream<T> takeWhile(Stream<T> self, Predicate<? super T> predicate) {
		Objects.requireNonNull(predicate, "predicate");
		return self.filter(new Predicate<T>() {
			private boolean hasFailed;

			@Override
			public boolean test(T element) {
				return !hasFailed && !(hasFailed = !predicate.test(element));
			}
		});
	}

	public static <T> Stream<T> dropWhile(Stream<T> self, Predicate<? super T> predicate) {
		Objects.requireNonNull(predicate, "predicate");
		return self.filter(new Predicate<T>() {
			private boolean hasMatched;

			@Override
			public boolean test(T element) {
				return hasMatched || (hasMatched = !predicate.test(element));
			}
		});
	}

	public static IntStream iterate(int seed, IntPredicate hasNext, IntUnaryOperator generator) {
		return StreamSupport.intStream(new AbstractIntSpliterator(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL) {
			private final OfInt stream = IntStream.iterate(seed, generator).spliterator();
			private boolean complete;

			@Override
			public boolean tryAdvance(IntConsumer action) {
				if (complete) return false;
				stream.tryAdvance((int value) -> {
					if (!hasNext.test(value)) {
						complete = true;
					} else {
						action.accept(value);
					}
				});
				return !complete;
			}
		}, false);
	}
}