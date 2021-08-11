package com.chocohead.nsn;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Objects;

public class MoreFiles {
	public static Path writeString(Path path, CharSequence value, OpenOption... options) throws IOException {
		return writeString(path, value, StandardCharsets.UTF_8, options);
	}

	public static Path writeString(Path path, CharSequence value, Charset encoding, OpenOption... options) throws IOException {
		byte[] contents = Objects.requireNonNull(value, "string").toString().getBytes(Objects.requireNonNull(encoding, "encoding"));
		Files.write(Objects.requireNonNull(path, "path"), contents, options);
		return path;
	}

	public static String readString(Path path) throws IOException {
		return readString(path, StandardCharsets.UTF_8);
	}

	public static String readString(Path path, Charset encoding) throws IOException {
		byte[] contents = Files.readAllBytes(Objects.requireNonNull(path, "path"));
		return new String(contents, Objects.requireNonNull(encoding, "encoding"));
	}
}