package com.chocohead.nsn;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.stream.Stream;

public class Matchy {
	public static Matcher appendReplacement(Matcher matcher, StringBuilder builder, String replacement) {
		StringBuffer buffer = new StringBuffer();
		matcher.appendReplacement(buffer, replacement);
		builder.append(buffer);

		return matcher;
	}

	public static StringBuilder appendTail(Matcher matcher, StringBuilder builder) {
		StringBuffer buffer = new StringBuffer();
		matcher.appendTail(buffer);
		builder.append(buffer);

		return builder;
	}
	
	public static String replaceAll(Matcher matcher, Function<MatchResult, String> replacer) {
		Objects.requireNonNull(replacer, "replacer");
		matcher.reset();
		StringBuffer buffer = new StringBuffer();

		while (matcher.find()) {
			String replacement = replacer.apply(matcher);
			matcher.appendReplacement(buffer, replacement);
		}

		matcher.appendTail(buffer);
		return buffer.toString();
	}

	public static Stream<MatchResult> results(Matcher matcher) {
		throw new UnsupportedOperationException("TODO"); //A bit more complicated
	}

	public static String replaceFirst(Matcher matcher, Function<MatchResult, String> replacer) {
		matcher.reset();
		StringBuffer buffer = new StringBuffer();

		if (matcher.find()) {
			String replacement = Objects.requireNonNull(replacer, "replacer").apply(matcher);
			matcher.appendReplacement(buffer, replacement);
		}

		matcher.appendTail(buffer);
		return buffer.toString();
}
}