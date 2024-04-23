package com.chocohead.nsn.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;

import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;

public class RepeatableInputStreamEntity extends AbstractHttpEntity {
	protected final Supplier<? extends InputStream> factory;

	public RepeatableInputStreamEntity(Supplier<? extends InputStream> factory) {
		this(factory, null);
	}

	public RepeatableInputStreamEntity(Supplier<? extends InputStream> factory, ContentType contentType) {
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
		return -1;
	}

	@Override
	public InputStream getContent() throws IOException {
		return factory.get();
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		try (InputStream in = getContent()) {
    		IOUtils.copyLarge(in, out);
    	}
	}

	@Override
	public boolean isStreaming() {
		return true;
	}
}