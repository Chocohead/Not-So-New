package com.chocohead.nsn;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import org.lwjgl.system.MemoryUtil;

public class Buffers {
	public static ByteBuffer get(ByteBuffer self, int index, byte[] dst) {
		return get(self, index, dst, 0, dst.length);
	}

	public static ByteBuffer get(ByteBuffer self, int index, byte[] dst, int offset, int length) {
		MoreObjects.checkFromIndexSize(index, length, self.limit());
		MoreObjects.checkFromIndexSize(offset, length, dst.length);

		if (index > 0) {
			int position = self.position();
			self.position(position + index);
			self.get(dst, offset, length);
			self.position(position);
		} else {
			self.get(dst, offset, length);
		}

		return self;
	}

	public static ByteBuffer put(ByteBuffer self, int index, ByteBuffer src, int offset, int length) {
		MoreObjects.checkFromIndexSize(index, length, self.limit());
		MoreObjects.checkFromIndexSize(offset, length, src.limit());
		if (self.isReadOnly()) throw new ReadOnlyBufferException();

		//Equivalent to for (final int end = offset + length; offset < end; offset++, index++) self.put(index, src.get(offset))
		long from = MemoryUtil.memAddress(src, offset);
		long to = MemoryUtil.memAddress(self, index);
		MemoryUtil.memCopy(from, to, length);

		return self;
	}
}