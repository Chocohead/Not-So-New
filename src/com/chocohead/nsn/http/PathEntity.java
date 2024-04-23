package com.chocohead.nsn.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;

public class PathEntity extends AbstractHttpEntity {
	protected final Path path;

	public PathEntity(Path path) {
        this(path, null);
    }

    public PathEntity(Path path, ContentType contentType) {
        this.path = Objects.requireNonNull(path);
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
    	try {
			return Files.size(path);
		} catch (IOException e) {
			return -1;
		}
	}

    @Override
	public InputStream getContent() throws IOException {
    	return Files.newInputStream(path);
	}

    @Override
	public void writeTo(final OutputStream out) throws IOException {
    	try (InputStream in = getContent()) {
    		IOUtils.copyLarge(in, out);
    	}
	}

    @Override
	public boolean isStreaming() {
		return false;
	}
}