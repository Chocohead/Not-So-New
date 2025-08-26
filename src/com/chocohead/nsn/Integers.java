package com.chocohead.nsn;

import java.util.Objects;

public class Integers {
	public static int parseInt(CharSequence text, int from, int to, int radix) throws NumberFormatException {
		return Integer.parseInt(Objects.requireNonNull(text, "text").subSequence(from, to).toString(), radix);
	}

	public static int parseUnsignedInt(CharSequence text, int from, int to, int radix) throws NumberFormatException {
		return Integer.parseUnsignedInt(Objects.requireNonNull(text, "text").subSequence(from, to).toString(), radix);
	}
}