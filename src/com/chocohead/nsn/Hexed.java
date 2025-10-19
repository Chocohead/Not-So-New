package com.chocohead.nsn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.RoundingMode;
import java.nio.CharBuffer;
import java.util.Objects;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import com.google.common.math.IntMath;
import com.google.common.primitives.Chars;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

public class Hexed {
	private final BaseEncoding encoder;
	private final String prefix, delimiter, suffix;

	public static Hexed of() {
		return new Hexed("", "", "");
	}

	public static Hexed ofDelimiter(String delimiter) {
		return new Hexed("", Objects.requireNonNull(delimiter, "delimiter"), "");
	}

	private Hexed(String prefix, String delimiter, String suffix) {
		this(BaseEncoding.base16().lowerCase(), prefix, delimiter, suffix);
	}

	private Hexed(BaseEncoding encoder, String prefix, String delimiter, String suffix) {
		this.encoder = encoder;
		this.prefix = prefix;
		this.delimiter = delimiter;
		this.suffix = suffix;
	}

	public Hexed withUpperCase() {
		return new Hexed(encoder.upperCase(), prefix, delimiter, suffix);
	}

	public Hexed withLowerCase() {
		return new Hexed(encoder.lowerCase(), prefix, delimiter, suffix);
	}

	public Hexed withPrefix(String prefix) {
		return new Hexed(encoder, Objects.requireNonNull(prefix, "prefix"), delimiter, suffix);
	}

	public Hexed withDelimiter(String delimiter) {
		return new Hexed(encoder, prefix, Objects.requireNonNull(delimiter, "delimiter"), suffix);
	}

	public Hexed withSuffix(String suffix) {
		return new Hexed(encoder, prefix, delimiter, Objects.requireNonNull(suffix, "suffix"));
	}

	public String delimiter() {
		return delimiter;
	}

	public String prefix() {
		return prefix;
	}

	public String suffix() {
		return suffix;
	}

	public boolean isUpperCase() {
		return encoder == encoder.upperCase();
	}

	public String formatHex(byte[] bytes) {
		return formatHex(bytes, 0, Objects.requireNonNull(bytes, "bytes").length);
	}

	public String formatHex(byte[] bytes, int from, int to) {
		String out = encoder.encode(Objects.requireNonNull(bytes, "bytes"), from, to - from);

		if (!out.isEmpty() && (!prefix.isEmpty() || !delimiter.isEmpty() || !suffix.isEmpty())) {
			out = formatHex(new StringBuilder(), out).toString();
		}

		return out;
	}

	public <T extends Appendable> T formatHex(T out, byte[] bytes) {
		return formatHex(out, bytes, 0, Objects.requireNonNull(bytes, "bytes").length);
	}

	public <T extends Appendable> T formatHex(T out, byte[] bytes, int from, int to) {
		String hex = encoder.encode(Objects.requireNonNull(bytes, "bytes"), from, to - from);

		if (!hex.isEmpty()) {
			if (!prefix.isEmpty() || !delimiter.isEmpty() || !suffix.isEmpty()) {
				formatHex(out, hex);
			} else {
				try {
					out.append(hex);
				} catch (IOException e) {
					throw new UncheckedIOException(e.getMessage(), e);
				}
			}
		}

		return out;
	}

	private <T extends Appendable> T formatHex(T out, String hex) {
		assert hex != null && !hex.isEmpty() && hex.length() % 2 == 0 : hex;
		assert !prefix.isEmpty() || !delimiter.isEmpty() || !suffix.isEmpty();

		try {
			out.append(prefix);
			out.append(hex, 0, 2);
			for (int i = 2; i < hex.length(); i += 2) {
				out.append(suffix).append(delimiter).append(prefix);
				out.append(hex, i, i + 2);
			}
			out.append(suffix);
		} catch (IOException e) {
			throw new UncheckedIOException(e.getMessage(), e);
		}

		return out;
	}

	public byte[] parseHex(CharSequence string) {
		if (string.length() == 0) return new byte[0];

		if (!prefix.isEmpty() || !delimiter.isEmpty() || !suffix.isEmpty()) {
			int valueLength = prefix.length() + 2 + suffix.length();
			int strideLength = valueLength + delimiter.length();
			if ((string.length() - valueLength) % strideLength != 0) {
				throw new IllegalArgumentException("String did not match expected hex format");
			}

			int expectedValues = 1 + ((string.length() - valueLength) / strideLength);
			StringBuilder parsed = new StringBuilder(expectedValues * 2);
			int offset = assertExpected(prefix, string, 0);
			String stride = suffix + delimiter + prefix;
			for (int i = expectedValues - 1; i > 0; i--) {
				parsed.append(string, offset, offset += 2);
				offset = assertExpected(stride, string, offset);	
			}
			parsed.append(string, offset, offset += 2);
			assertExpected(suffix, string, offset);
			string = parsed;
		}

		return encoder.decode(string);
	}

	private static int assertExpected(String expected, CharSequence in, int offset) {
		if (!expected.contentEquals(in.subSequence(offset, offset += expected.length()))) {
			throw new IllegalArgumentException("Expected \"" + expected + "\" at " + (offset -= expected.length()) + " but had \"" + in.subSequence(offset, offset + expected.length()) + '"');
		}

		return offset;
	}

	public byte[] parseHex(CharSequence string, int from, int to) {
		return parseHex(from != 0 || to != string.length() ? string.subSequence(from, to) : string);
	}

	public byte[] parseHex(char[] chars, int from, int to) {
		return parseHex(CharBuffer.wrap(chars, from, to - from));
	}

	public char toLowHexDigit(int value) {
		return toHexDigits((byte) value).charAt(1);
	}

	public char toHighHexDigit(int value) {
		return toHexDigits((byte) value).charAt(0);
	}

	public String toHexDigits(long value, int digits) {
		if (digits < 0 || digits > 16) throw new IllegalArgumentException("Invalid number of digits: " + digits);
		int bytes = IntMath.divide(digits, 2, RoundingMode.CEILING);
		String out = encoder.encode(Longs.toByteArray(value), 8 - bytes, bytes);
		return digits % 2 == 1 ? out.substring(1) : out;
	}

	public String toHexDigits(long value) {
		return encoder.encode(Longs.toByteArray(value));
	}

	public String toHexDigits(int value) {
		return encoder.encode(Ints.toByteArray(value));
	}

	public String toHexDigits(short value) {
		return encoder.encode(Shorts.toByteArray(value));
	}

	public String toHexDigits(char value) {
		return encoder.encode(Chars.toByteArray(value));
	}

	public String toHexDigits(byte value) {
		return encoder.encode(new byte[] {value});
	}

	public <A extends Appendable> A toHexDigits(A out, byte value) {
		try {
			out.append(toHexDigits(value));
		} catch (IOException e) {
			throw new UncheckedIOException(e.getMessage(), e);
		}

		return out;
	}

	public static boolean isHexDigit(int letter) {
		switch (letter) {
		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
		case 'A': case 'a':
		case 'B': case 'b':
		case 'C': case 'c':
		case 'D': case 'd':
		case 'E': case 'e':
		case 'F': case 'f':
			return true;
		default:
			return false;
		}
	}

	public static int fromHexDigit(int letter) {
		switch (letter) {
		case '0':
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9':
			return letter - '0';
		case 'A':
		case 'B':
		case 'C':
		case 'D':
		case 'E':
		case 'F':
			return letter - 'A' + 10;
		case 'a':
		case 'b':
		case 'c':
		case 'd':
		case 'e':
		case 'f':
			return letter - 'a' + 10;
		default:
			throw new NumberFormatException("Not a hexadecimal digit: " + (char) letter + " (" + letter + ')');
		}
	}

	public static int fromHexDigits(CharSequence digits) {
		return fromHexDigits(digits, 0, Objects.requireNonNull(digits, "digits").length());
	}

	public static int fromHexDigits(CharSequence digits, int from, int to) {
		Preconditions.checkPositionIndexes(from, to, Objects.requireNonNull(digits, "digits").length());
		if (to - from > 8) throw new IllegalArgumentException("Too many digits in " + digits + " (allowed up to 8, has " + digits.length() + ')');

		int out = 0;
		for (int i = from; i < to; i++) {
			out = (out << 4) + fromHexDigit(digits.charAt(i));
		}
		return out;
	}

	public static long fromHexDigitsToLong(CharSequence digits) {
		return fromHexDigitsToLong(digits, 0, Objects.requireNonNull(digits, "digits").length());
	}

	public static long fromHexDigitsToLong(CharSequence digits, int from, int to) {
		Preconditions.checkPositionIndexes(from, to, Objects.requireNonNull(digits, "digits").length());
		if (to - from > 16) throw new IllegalArgumentException("Too many digits in " + digits + " (allowed up to 16, has " + digits.length() + ')');

		long out = 0;
		for (int i = from; i < to; i++) {
			out = (out << 4) + fromHexDigit(digits.charAt(i));
		}
		return out;
	}

	@Override
	public boolean equals(Object that) {
		if (this == that) return true;
		if (that == null || getClass() != that.getClass()) return false;
		Hexed other = (Hexed) that;
		return encoder.equals(other.encoder) && prefix.equals(other.prefix) && delimiter.equals(other.delimiter) && suffix.equals(other.suffix);
	}

	@Override
	public int hashCode() {
		return Objects.hash(encoder, prefix, delimiter, suffix);
	}
}