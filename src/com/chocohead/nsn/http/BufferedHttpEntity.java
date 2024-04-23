package com.chocohead.nsn.http;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

public class BufferedHttpEntity extends HttpEntityWrapper {
	protected final int size;

	public BufferedHttpEntity(HttpEntity entity, int size) {
		super(entity);

		this.size = size;
	}

	@Override
	public InputStream getContent() throws IOException {
		InputStream content = super.getContent();
		return content != null ? new BufferedInputStream(content, size) : null;
	}
}