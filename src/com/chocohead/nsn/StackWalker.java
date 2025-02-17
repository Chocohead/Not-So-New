package com.chocohead.nsn;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.lang3.ClassUtils;

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
	private final boolean offerDeclaringClasses;

	public static StackWalker getInstance() {
		return new StackWalker(false);
	}

	public static StackWalker getInstance(Option option) {
		return new StackWalker(option == Option.RETAIN_CLASS_REFERENCE);
	}

	public static StackWalker getInstance(Set<Option> options) {
		return new StackWalker(options.contains(Option.RETAIN_CLASS_REFERENCE));
	}

	public static StackWalker getInstance(Set<Option> options, int expectedDepth) {
		return getInstance(options);
	}

	private StackWalker(boolean offerDeclaringClasses) {
		this.offerDeclaringClasses = offerDeclaringClasses;
	}

	public <T> T walk(Function<? super Stream<StackFrame>, ? extends T> walker) {
		return walker.apply(Arrays.stream(Thread.currentThread().getStackTrace()).map(frame -> {
			return new StackFrame() {
				private Class<?> declaringClass;

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
					if (!offerDeclaringClasses) throw new UnsupportedOperationException("StackWalker was not created with RETAIN_CLASS_REFERENCE");
					if (declaringClass == null) {
						try {
							declaringClass = ClassUtils.getClass(getClassName(), false);
						} catch (ClassNotFoundException e) {
							throw new UnsupportedOperationException("Can't find declaring class for stack frame", e);
						}
					}

					return declaringClass;
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