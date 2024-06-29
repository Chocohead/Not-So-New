package com.chocohead.nsn;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class CompletableFutures {
	private static class Timer {
		private static final ScheduledThreadPoolExecutor POOL = new ScheduledThreadPoolExecutor(1, task -> {
			Thread out = new Thread(task, "CompletableFuturesTimerPool");
			out.setDaemon(true);
			return out;
		});
		static {
			POOL.setRemoveOnCancelPolicy(true);
		}

		static ScheduledFuture<?> start(Runnable command, long time, TimeUnit unit) {
			return POOL.schedule(command, time, unit);
		}
	}

	public static <T> CompletableFuture<T> failedFuture(Throwable e) {
		CompletableFuture<T> out = new CompletableFuture<>();
		out.completeExceptionally(e);
		return out;
	}

	public static <T> CompletableFuture<T> orTimeout(CompletableFuture<T> self, long timeout, TimeUnit unit) {
		return raceTimer(self, () -> {
			if (!self.isDone()) self.completeExceptionally(new TimeoutException());
		}, timeout, unit);
	}

	public static <T> CompletableFuture<T> completeOnTimeout(CompletableFuture<T> self, T value, long timeout, TimeUnit unit) {
		return raceTimer(self, () -> self.complete(value), timeout, unit);
	}

	private static <T> CompletableFuture<T> raceTimer(CompletableFuture<T> self, Runnable timedAction, long timeout, TimeUnit unit) {
		if (!self.isDone()) {
			ScheduledFuture<?> timer = Timer.start(timedAction, timeout, unit);
			self.whenComplete((result, e) -> {
				if (e == null && !timer.isDone()) timer.cancel(false);
			});
		}

		return self;
	}

	public static Executor delayedExecutor(long delay, TimeUnit unit, Executor executor) {
		return delayedExecutor(task -> CompletableFuture.runAsync(task, executor), delay, unit);
	}

	public static Executor delayedExecutor(long delay, TimeUnit unit) {
		return delayedExecutor(CompletableFuture::runAsync, delay, unit);
	}

	private static Executor delayedExecutor(Function<Runnable, CompletableFuture<Void>> taskConsumer, long delay, TimeUnit unit) {
		return task -> Timer.start(() -> taskConsumer.apply(task), delay, unit);
	}

	public static <T> CompletableFuture<T> exceptionallyAsync(CompletableFuture<T> self, Function<Throwable, ? extends T> action) {
		return self.handle((result, e) -> e == null ? self : self.<T>handleAsync((innerResult, innerE) -> action.apply(innerE))).thenCompose(Function.identity());
	}

	public static <T> CompletableFuture<T> exceptionallyAsync(CompletableFuture<T> self, Function<Throwable, ? extends T> action, Executor executor) {
		return self.handle((result, e) -> e == null ? self : self.<T>handleAsync((innerResult, innerE) -> action.apply(innerE), executor)).thenCompose(Function.identity());
	}

	public static <T> CompletableFuture<T> exceptionallyCompose(CompletableFuture<T> self, Function<Throwable, ? extends CompletionStage<T>> action) {
		return self.handle((result, e) -> e == null ? self : action.apply(e)).thenCompose(Function.identity());
	}

	public static <T> CompletableFuture<T> exceptionallyComposeAsync(CompletableFuture<T> self, Function<Throwable, ? extends CompletionStage<T>> action) {
		return self.handle((result, e) -> e == null ? self : self.handleAsync((innerResult, innerE) -> action.apply(innerE)).thenCompose(Function.identity())).thenCompose(Function.identity());
	}

	public static <T> CompletableFuture<T> exceptionallyComposeAsync(CompletableFuture<T> self, Function<Throwable, ? extends CompletionStage<T>> action, Executor executor) {
		return self.handle((result, e) -> e == null ? self : self.handleAsync((innerResult, innerE) -> action.apply(innerE), executor).thenCompose(Function.identity())).thenCompose(Function.identity());
	}
}