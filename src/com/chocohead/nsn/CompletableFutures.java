package com.chocohead.nsn;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class CompletableFutures {
	public static <T> CompletableFuture<T> failedFuture(Throwable e) {
		CompletableFuture<T> out = new CompletableFuture<>();
		out.completeExceptionally(e);
		return out;
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