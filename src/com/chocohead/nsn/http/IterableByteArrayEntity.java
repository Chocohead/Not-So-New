package com.chocohead.nsn.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.util.Objects;

import com.google.common.collect.Iterators;

import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;

public class IterableByteArrayEntity extends AbstractHttpEntity {
	protected final Iterable<byte[]> factory;

	public IterableByteArrayEntity(Iterable<byte[]> factory) {
		this(factory, null);
	}

	public IterableByteArrayEntity(Iterable<byte[]> factory, ContentType contentType) {
		this.factory = Objects.requireNonNull(factory);
		if (contentType != null) {
			setContentType(contentType.toString());
		}
	}

	@Override
	public boolean isRepeatable() {
		return true;
	}

	@Override
	public long getContentLength() {
		long length = 0;
		for (byte[] content : factory) {
			length = Math.addExact(length, content.length);
		}
		return length;
	}

	@Override
	public InputStream getContent() {
		return new SequenceInputStream(Iterators.asEnumeration(Iterators.transform(factory.iterator(), ByteArrayInputStream::new)));
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		for (byte[] content : factory) {
			out.write(content);
		}
		out.flush();
	}

	@Override
	public boolean isStreaming() {
		return false;
	}
}