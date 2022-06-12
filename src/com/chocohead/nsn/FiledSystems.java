package com.chocohead.nsn;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

public class FiledSystems {
	public static FileSystem newFileSystem(Path path) throws IOException {
		return FileSystems.newFileSystem(path, null);
	}

	public static FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
		return newFileSystem(path, env, null);
	}

	public static FileSystem newFileSystem(Path path, Map<String, ?> env, ClassLoader loader) throws IOException {
		Objects.requireNonNull(path, "path");

		for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
			try {
				return provider.newFileSystem(path, env);
			} catch (UnsupportedOperationException e) {
			}
		}

		if (loader != null) {
			for (FileSystemProvider provider : ServiceLoader.load(FileSystemProvider.class, loader)) {
				try {
					return provider.newFileSystem(path, env);
				} catch (UnsupportedOperationException e) {
				}
			}
		}

		throw new ProviderNotFoundException("Provider not found");
	}
}