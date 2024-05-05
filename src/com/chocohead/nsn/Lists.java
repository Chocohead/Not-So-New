package com.chocohead.nsn;

import java.util.List;
import java.util.NoSuchElementException;

public class Lists {
	public static <T> T getFirst(List<T> list) {
		if (list.isEmpty()) throw new NoSuchElementException();
		return list.get(0);
	}

	public static <T> T getLast(List<T> list) {
		int index = list.size() - 1;
		if (index < 0) throw new NoSuchElementException();
		return list.get(index);
	}

	public static <T> T removeFirst(List<T> list) {
		if (list.isEmpty()) throw new NoSuchElementException();
		return list.remove(0);
	}

	public static <T> T	removeLast(List<T> list) {
		int index = list.size() - 1;
		if (index < 0) throw new NoSuchElementException();
		return list.remove(index);
	}
}