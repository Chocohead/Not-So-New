package com.chocohead.nsn;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

public class StackWalker {
	public static enum Option {
		RETAIN_CLASS_REFERENCE,
	}
	public interface StackFrame {
		StackTraceElement toStackTraceElement();

		String getFileName();

		String getClassName();

		Class<?> getDeclaringClass();

		boolean isNativeMethod();

		String getMethodName();

		int getLineNumber();

		int getByteCodeIndex();

		default MethodType getMethodType() {
			throw new UnsupportedOperationException();
		}

		default String getDescriptor() {
			throw new UnsupportedOperationException();
		}
	}

	public static StackWalker getInstance() {
		return new StackWalker();
	}

	public static StackWalker getInstance(Option option) {
		return new StackWalker();
	}

	private StackWalker() {
	}

	public <T> T walk(Function<? super Stream<StackFrame>, ? extends T> walker) {
		return walker.apply(Arrays.stream(Thread.currentThread().getStackTrace()).map(frame -> {
			return new StackFrame() {
				@Override
				public StackTraceElement toStackTraceElement() {
					return frame;
				}

				@Override
				public String getFileName() {
					return frame.getFileName();
				}

				@Override
				public String getClassName() {
					return frame.getClassName();
				}

				@Override
				public Class<?> getDeclaringClass() {
					throw new UnsupportedOperationException("TODO?");
				}

				@Override
				public boolean isNativeMethod() {
					return frame.isNativeMethod();
				}

				@Override
				public String getMethodName() {
					return frame.getMethodName();
				}

				@Override
				public int getLineNumber() {
					return frame.getLineNumber();
				}

				@Override
				public int getByteCodeIndex() {
					throw new UnsupportedOperationException("?");
				}
			};
		}));
	}
}