package com.chocohead.nsn;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class PathIterator implements Iterator<Path> {
	private final Path path;
	private int name = 0;

	public PathIterator(Path path) {
		this.path = path;
	}

	@Override
	public boolean hasNext() {
		return name < path.getNameCount();
	}

	@Override
	public Path next() {
		if (!hasNext()) throw new NoSuchElementException();
		return path.getName(name++);
	}
}