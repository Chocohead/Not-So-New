package com.chocohead.nsn;

import org.apache.commons.lang3.Validate;

import com.ibm.icu.lang.UCharacter;

public class Characters {
	public static String toString(int codePoint) {
		Validate.inclusiveBetween(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT, codePoint, codePoint + " is not a valid Unicode code point");
		return new String(Character.toChars(codePoint));
	}

	public static int codePointOf(String name) {
		int out = UCharacter.getCharFromName(name);
		if (out != -1) return out;

		int split = name.lastIndexOf(' ');
		if (split >= 0) {
			try {
				out = Integer.parseInt(name.substring(split + 1), 16);

				if (Character.isValidCodePoint(out) && name.equalsIgnoreCase(Character.getName(out))) {
					return out;
				}
			} catch (NumberFormatException e) {
			}
		}

		throw new IllegalArgumentException("Unrecognised character name: " + name);
	}
}