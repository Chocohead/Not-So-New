package com.chocohead.nsn;

public class Maths {
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