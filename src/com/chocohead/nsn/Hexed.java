package com.chocohead.nsn;

import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Chars;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public class Hexed {
	private final BaseEncoding encoder;
	private final String prefix;

	public static Hexed of() {
		return new Hexed(BaseEncoding.base16().lowerCase(), null);
	}

	private Hexed(BaseEncoding encoder, String prefix) {
		this.encoder = encoder;
		this.prefix = prefix;
	}

	public Hexed withUpperCase() {
		return new Hexed(encoder.upperCase(), prefix);
	}

	public Hexed withPrefix(String prefix) {
		return new Hexed(encoder, Objects.requireNonNull(prefix, "prefix"));
	}

	public String toHexDigits(long value) {
		return toHexDigits(Longs.toByteArray(value));
	}

	public String toHexDigits(int value) {
		return toHexDigits(Ints.toByteArray(value));
	}

	private String toHexDigits(byte[] value) {
		String out = encoder.encode(value);
		return prefix != null ? Joiner.on(prefix).join(Chars.asList(out.toCharArray())) : out;
	} 
}