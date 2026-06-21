package com.chocohead.nsn;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ReadOnlyBufferException;

import org.lwjgl.system.MemoryUtil;

public class Buffers {
	public static ByteBuffer slice(ByteBuffer self, int offset, int length) {
		assert offset >= 0: String.format("Offset %d < 0", offset);
		assert self.limit() >= offset: String.format("Limit %d < Offset %d (Aiming for length %d)", self.limit(), offset, length);
		assert length >= 0: String.format("Length %d < 0", length);
		assert self.capacity() - offset >= length: String.format("Overflow: Offset %d => Capacity %d < Length %d", offset, self.capacity(), length);
		if (self.isDirect()) {
			if (self.position() <= offset) {
				return MemoryUtil.memSlice(self, offset - self.position(), length);
			} else {//Slicing from behind the current position
				int position = self.position();
				try {
					self.position(offset);
					return MemoryUtil.memSlice(self, 0, length);
				} finally {
					self.position(position);
				}
			}
		} else {
			int position = self.position();
			int limit = self.limit();
			try {//Could do self.duplicate() if temporary position & limit changes are a problem
				return ((ByteBuffer) self.position(offset).limit(length)).slice();
			} finally {
				self.position(position).limit(limit);
			}
		}
	}

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

		if (self.isDirect() && src.isDirect()) {
			if (self.isReadOnly()) throw new ReadOnlyBufferException();
			assert self.order() == src.order(); //Would this a problem?
			//Equivalent to for (final int end = offset + length; offset < end; offset++, index++) self.put(index, src.get(offset))
			long from = MemoryUtil.memAddress(src, offset);
			long to = MemoryUtil.memAddress(self, index);
			MemoryUtil.memCopy(from, to, length);
		} else {
			int position = self.position();
			if (position != index) self.position(index);
			try {
				self.put(slice(src, offset, length).order(src.order()));
			} finally {
				src.position(position);
			}
		}

		return self;
	}

	public static CharBuffer slice(CharBuffer self, int offset, int length) {
		assert offset >= 0: String.format("Offset %d < 0", offset);
		assert self.limit() >= offset: String.format("Limit %d < Offset %d (Aiming for length %d)", self.limit(), offset, length);
		assert length >= 0: String.format("Length %d < 0", length);
		assert self.capacity() - offset >= length: String.format("Overflow: Offset %d => Capacity %d < Length %d", offset, self.capacity(), length);
		if (self.isDirect()) {
			if (self.position() <= offset) {
				return MemoryUtil.memSlice(self, offset - self.position(), length);
			} else {//Slicing from behind the current position
				int position = self.position();
				try {
					self.position(offset);
					return MemoryUtil.memSlice(self, 0, length);
				} finally {
					self.position(position);
				}
			}
		} else {
			int position = self.position();
			int limit = self.limit();
			try {//Could do self.duplicate() if temporary position & limit changes are a problem
				return ((CharBuffer) self.position(offset).limit(length)).slice();
			} finally {
				self.position(position).limit(limit);
			}
		}
	}

	public static IntBuffer slice(IntBuffer self, int offset, int length) {
		assert offset >= 0: String.format("Offset %d < 0", offset);
		assert self.limit() >= offset: String.format("Limit %d < Offset %d (Aiming for length %d)", self.limit(), offset, length);
		assert length >= 0: String.format("Length %d < 0", length);
		assert self.capacity() - offset >= length: String.format("Overflow: Offset %d => Capacity %d < Length %d", offset, self.capacity(), length);
		if (self.isDirect()) {
			if (self.position() <= offset) {
				return MemoryUtil.memSlice(self, offset - self.position(), length);
			} else {//Slicing from behind the current position
				int position = self.position();
				try {
					self.position(offset);
					return MemoryUtil.memSlice(self, 0, length);
				} finally {
					self.position(position);
				}
			}
		} else {
			int position = self.position();
			int limit = self.limit();
			try {//Could do self.duplicate() if temporary position & limit changes are a problem
				return ((IntBuffer) self.position(offset).limit(length)).slice();
			} finally {
				self.position(position).limit(limit);
			}
		}
	}

	public static FloatBuffer slice(FloatBuffer self, int offset, int length) {
		assert offset >= 0: String.format("Offset %d < 0", offset);
		assert self.limit() >= offset: String.format("Limit %d < Offset %d (Aiming for length %d)", self.limit(), offset, length);
		assert length >= 0: String.format("Length %d < 0", length);
		assert self.capacity() - offset >= length: String.format("Overflow: Offset %d => Capacity %d < Length %d", offset, self.capacity(), length);
		if (self.isDirect()) {
			if (self.position() <= offset) {
				return MemoryUtil.memSlice(self, offset - self.position(), length);
			} else {//Slicing from behind the current position
				int position = self.position();
				try {
					self.position(offset);
					return MemoryUtil.memSlice(self, 0, length);
				} finally {
					self.position(position);
				}
			}
		} else {
			int position = self.position();
			int limit = self.limit();
			try {//Could do self.duplicate() if temporary position & limit changes are a problem
				return ((FloatBuffer) self.position(offset).limit(length)).slice();
			} finally {
				self.position(position).limit(limit);
			}
		}
	}
}