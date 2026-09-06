package com.chocohead.nsn;

import java.math.RoundingMode;

import com.google.common.math.IntMath;

public class Maths {
	public static int absExact(int value) {
		if (value == Integer.MIN_VALUE) {
			throw new ArithmeticException("Overflow of Integer.MAX_VALUE + 1");
		}

		return Math.abs(value);
	}

	public static int floorDivExact(int x, int y) {
		if (x == Integer.MIN_VALUE && y == -1) {
			throw new ArithmeticException("Overflow of Integer.MAX_VALUE + 1");
		}

		return IntMath.divide(x, y, RoundingMode.FLOOR);
	}

	public static int floorMod(long x, int y) {
		return (int) Math.floorMod(x, (long) y);
	}

	public static strictfp float fma(float a, float b, float c) {
		return (float) fma((double) a, (double) b, (double) c);
	}

	public static strictfp double fma(double a, double b, double c) {
		return a * b + c; //This isn't strictly right as it lacks the infinite precision of true FMA
	}
}