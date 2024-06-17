package com.chocohead.nsn;

import java.util.Random;

public class Randoms {
	public static float nextFloat(Random self, float rangeEnd) {
		return nextFloat(self, 0, rangeEnd);
	}

	public static float nextFloat(Random self, float rangeStart, float rangeEnd) {
		//Invert the if condition to account for rangeStart or rangeEnd being Float.NaN
		if (!(rangeStart < rangeEnd && rangeEnd - rangeStart < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("Range start must be less than or equal to range end");
        }

		float out = rangeStart + (rangeEnd - rangeStart) * self.nextFloat();
        if (out >= rangeEnd) out = Float.intBitsToFloat(Float.floatToIntBits(rangeEnd) - 1); //Naughty float rounding
        return out;
	}
}