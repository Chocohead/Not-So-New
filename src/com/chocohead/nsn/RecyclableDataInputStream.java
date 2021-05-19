package com.chocohead.nsn;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RecyclableDataInputStream extends DataInputStream {
	private class RecyclableBuffer extends BufferedInputStream {
		private final byte[] buffer = buf;

		public RecyclableBuffer() {
			super(null, 16);
		}

		public InputStream open(InputStream in) {
			if (buf == null) buf = buffer;
			this.in = in;
			return this;
		}
	}
	private final RecyclableBuffer buffer = new RecyclableBuffer();

	public RecyclableDataInputStream() {
		super(null);
	}

	public DataInputStream open(InputStream in) {
		this.in = buffer.open(in);
		return this;
	}

	@Override
	public void close() throws IOException {
		try {
			super.close();
		} finally {
			in = null;
		}
	}
}