package com.chocohead.nsn.http;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.message.HeaderGroup;

public final class HttpHeaders {
	private final HeaderGroup headers;

	HttpHeaders(HeaderGroup headers) {
		this.headers = headers;
	}

	public Optional<String> firstValue(String name) {
		Header header = headers.getFirstHeader(name);
		return header != null ? Optional.ofNullable(header.getValue()) : Optional.empty();
	}

	public OptionalLong firstValueAsLong(String name) {
		Header header = headers.getFirstHeader(name);
		String value = header != null ? header.getValue() : null;
		return value != null ? OptionalLong.of(Long.parseLong(value)) : OptionalLong.empty();
	}

	public List<String> allValues(String name) {
		return Lists.transform(Arrays.asList(headers.getHeaders(name)), Header::getValue);
	}

	public Map<String, List<String>> map() {
		return Arrays.stream(headers.getAllHeaders()).collect(Collectors.groupingBy(Header::getName, Collectors.mapping(Header::getValue, Collectors.toList())));
	}

	@Override
	public final boolean equals(Object obj) {
		if (!(obj instanceof HttpHeaders)) return false;
		HttpHeaders that = (HttpHeaders) obj;
		return map().equals(that.map());
	}

	@Override
	public final int hashCode() {
		int h = 0;
		for (HeaderIterator it = headers.iterator(); it.hasNext();) {
			Header header = it.nextHeader();
			h += header.getName().toLowerCase(Locale.ROOT).hashCode() ^ header.getValue().hashCode();
		}
		return h;
	}

	@Override
	public String toString() {
		return "HttpHeaders " + map();
	}
}