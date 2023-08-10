package com.chocohead.nsn;

import org.apache.commons.lang3.Validate;

public class Characters {
	public static String toString(int codePoint) {
		Validate.inclusiveBetween(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT, codePoint, codePoint + " is not a valid Unicode code point");
		return new String(Character.toChars(codePoint));
	}
}