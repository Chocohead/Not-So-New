package com.chocohead.nsn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;

public class Strings {
	public static Stream<String> lines(String from) {
		return Streams.stream(iterLines(from));
	}

	public static void main(String[] args) {
		System.out.println(lines("thing\nvery\rworking\r\ngreat\n").collect(Collectors.joining(", ")));
		System.out.println(lines("thing\nvery\rworking\r\ngreat\r\ntrailing").collect(Collectors.joining(", ")));
		System.out.println(lines("\r\n").collect(Collectors.joining(", ", "start: [", "] end")));
		System.out.println(stripIndent("\n"+
			      "          &lt;html&gt;\n"+
			      "              &lt;body&gt; \n"+
			      "                  &lt;p&gt;Hello, world&lt;/p&gt;\n"+
			      "              &lt;/body&gt;    \n"+
			      "          &lt;/html&gt;\n"+
			      "          "));
	}

	private static Iterable<String> iterLines(String from) {
		return new Iterable<String>() {
			@Override
			public Iterator<String> iterator() {
				return new Iterator<String>() {
					private final int limit = from.length();
					private int position;
					private String next;

					private String readLine() {
						if (position < limit) {
							int startPos = position;

							for (; position < limit; position++) {
								char lastChar = from.charAt(position);

								if (lastChar == '\n' || lastChar == '\r') {
									String out = from.substring(startPos, position++);

									if (lastChar == '\r' && position < limit && from.charAt(position) == '\n') {
										position++;
									}

									return out;
								}
							}

							return from.substring(startPos);
						}

						return null;
					}

					@Override
					public boolean hasNext() {
						if (next == null) next = readLine();
						return next != null;
					}

					@Override
					public String next() {
						String out = next;
						if (out == null) throw new NoSuchElementException();
						next = null;
						return out;
					}
				};
			}

			@Override
			public Spliterator<String> spliterator() {
				return Spliterators.spliteratorUnknownSize(iterator(), Spliterator.NONNULL | Spliterator.IMMUTABLE);
			}
		};
	}

	public static String ident(String to, int spaces) {
		if (to.isEmpty()) return to;

		if (spaces > 0) {
			String pad = StringUtils.repeat(' ', spaces);
			StringBuilder out = new StringBuilder(to.length() + spaces * 5); //Guess how many line breaks there are

			for (String line : iterLines(to)) {
				out.append(pad).append(line).append('\n');
			}

			return out.substring(0, out.length() - 1);
		} else if (spaces < 0) {
			StringBuilder out = new StringBuilder(to.length());

			for (String line : iterLines(to)) {
				int i = 0;

				for (int end = Math.min(line.length(), -spaces); i < end; i++) {
					if (!Character.isWhitespace(to.charAt(i))) break;
				}

				out.append(line, i, line.length()).append('\n');
			}

			return out.substring(0, out.length() - 1);
		} else {
			assert spaces == 0;
			return to.indexOf('\r') < 0 ? to : StringUtils.join(iterLines(to), '\n');
		}
	}

	private static int nonWhitespaceStart(CharSequence in) {
		int out = 0;

		for (int end = in.length(); out < end; out++) {
			if (!Character.isWhitespace(in.charAt(out))) {
				return out;
			}
		}

		assert StringUtils.isBlank(in);
		return out; //It's all blank
	}

	private static int nonWhitespaceEnd(CharSequence in) {
		for (int i = in.length() - 1; i >= 0; i--) {
			if (!Character.isWhitespace(in.charAt(i))) {
				return i;
			}
		}

		assert StringUtils.isBlank(in);
		return -1; //It's all blank
	}

	public static String stripIndent(String from) {
		if (from.isEmpty()) return from;
		char last = from.charAt(from.length() - 1);
		boolean trailingLine = last == '\r' || last == '\n';

		List<String> lines = new ArrayList<>();
		int outdent;
		if (!trailingLine) {
			outdent = Integer.MAX_VALUE;

			for (String line : iterLines(from)) {
				lines.add(line);
				int start = nonWhitespaceStart(line);

				if (start != line.length()) {
					outdent = Math.min(outdent, start);
				}
			}

			if (outdent > 0) {
				String lastLine = Iterables.getLast(lines);
		        if (StringUtils.isBlank(lastLine)) {
		            outdent = Math.min(outdent, lastLine.length());
		        }
			}
		} else {
			Iterables.addAll(lines, iterLines(from));
			outdent = 0;
		}

		if (lines.isEmpty()) return trailingLine ? "\n" : "";
		StringBuilder out = new StringBuilder(from.length());

		for (String line : lines) {
			int start = outdent > 0 ? Math.min(outdent, nonWhitespaceStart(line)) : 0;
			int end = nonWhitespaceEnd(line) + 1;

			System.out.println(line + " has " + start + ", " + end);
			(start >= end ? out : out.append(line, start, end)).append('\n');
		}

		return !trailingLine ? out.substring(0, out.length() - 1) : out.toString();
	}

	public static String translateEscapes(String from) {
		if (from.isEmpty()) return from;

		char[] chars = from.toCharArray();
		int to = 0;
		for (int i = 0, end = chars.length; i < end;) {
			char c = chars[i++];

			if (c == '\\') {
				if (i >= end) throw new IllegalArgumentException("Trailing backslash");
				c = chars[i++];

				switch (c) {
				case 'b': //Backspace
					c = '\b';
					break;

				case 't': //Horizontal tab
					c = '\t';
					break;

				case 'n': //New line
					c = '\n';
					break;

				case 'f': //Form feed
					c = '\f';
					break;

				case 'r': //Carriage return
					c = '\r';
					break;

				case 's': //Space
					c = ' ';
					break;

				case '"': //Double quote
				case '\'': //Single quote
				case '\\': //Backslash
					break;

				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7': {//Octal escape codes (up to 377)
					int code = c - '0';

					for (int more = Math.min(i + (c < '4' ? 2 : 1), end); i < more; i++, code = (code << 3) | (c - '0')) {
						c = chars[i];

						if (c < '0' || '7' < c) {
							break;
						}
					}

					c = (char) code;
					break;
				}

				case '\r': //Continuation
					if (i < end && chars[i] == '\n') {
						i++;
					}
				case '\n':
					continue;

				default:
					throw new IllegalArgumentException(String.format("Unexpected escape code: \\%c (\\u%04X)", c, (int) c));
				}
			}

			chars[to++] = c;
		}

		return new String(chars, 0, to);
	}
}