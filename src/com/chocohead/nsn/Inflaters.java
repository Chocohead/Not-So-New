package com.chocohead.nsn;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.apache.commons.lang3.ArrayUtils;

import com.google.common.primitives.Ints;

public class Inflaters {
	private static final Map<Inflater, ByteBuffer> WATCHING_INFLATERS = new WeakHashMap<>(4);

	public static void setInput(Inflater self, ByteBuffer input)  {
		WATCHING_INFLATERS.put(self, input);
	}

	public static int getRemaining(Inflater self) {
		ByteBuffer buffer = WATCHING_INFLATERS.get(self);
		return buffer != null ? buffer.remaining() : self.getRemaining();
	}

	public static boolean needsInput(Inflater self) {
		ByteBuffer buffer = WATCHING_INFLATERS.get(self);
		return buffer != null ? !buffer.hasRemaining() : self.needsInput();
	}

	public static int inflate(Inflater self, byte[] output) throws DataFormatException {
		return inflate(self, output, 0, output.length);
	}

	public static int inflate(Inflater self, byte[] output, int offset, int length) throws DataFormatException {
		ByteBuffer buffer = WATCHING_INFLATERS.get(self);
		if (buffer == null) return self.inflate(output, offset, length); //Nothing special

		int out = 0;
		while (buffer.hasRemaining() && offset < length) {
			if (buffer.hasArray()) {
				int position = buffer.position();
				self.setInput(buffer.array(), buffer.arrayOffset() + position, Ints.constrainToRange(buffer.remaining(), 0, 2 << 15));
				buffer.position(position + self.getRemaining()); //Move the position on
			} else {
				byte[] input = new byte[Ints.constrainToRange(buffer.remaining(), 0, 2 << 15)];
				buffer.get(input);
				self.setInput(input);
			}

			int inflated = self.inflate(output, offset, length);
			out += inflated;
			offset += inflated;
		}

		if (!self.needsInput()) {//Clear any data left in the input back into the buffer
			buffer.position(buffer.position() - self.getRemaining());
			self.setInput(ArrayUtils.EMPTY_BYTE_ARRAY);
		}

		return out;
	}

	public static int inflate(Inflater self, ByteBuffer buffer) throws DataFormatException {
		int out = 0;
		while (buffer.hasRemaining() && !needsInput(self)) {
			if (buffer.hasArray()) {
				int position = buffer.position();
				out = inflate(self, buffer.array(), buffer.arrayOffset() + position, Ints.constrainToRange(buffer.remaining(), 0, 2 << 15));
				buffer.position(position + out);
			} else {
				byte[] output = new byte[Ints.constrainToRange(buffer.remaining(), 0, 2 << 15)];
				out = inflate(self, output);
				buffer.put(output, 0, out);
			}
		}
		return out;
	}

	public static void clearWatch(Inflater inflater) {
		WATCHING_INFLATERS.remove(inflater);
	}
}