package com.chocohead.nsn;

public class StackWalker {
	public static enum Option {
		RETAIN_CLASS_REFERENCE,
	}

	public static StackWalker getInstance(Option option) {
		return new StackWalker();
	}

	private StackWalker() {
	}
}