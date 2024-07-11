package com.chocohead.nsn;

public class Classes {
	public static String getPackageName(Class<?> self) {
		while (self.isArray()) self = self.getComponentType();

		if (self.isPrimitive()) {
    		return "java.lang";
    	} else {
    		String name = self.getName();
	    	int index = name.lastIndexOf('.');
	    	return index >= 0 ? name.substring(0, index) : "";
    	}
	}

	public static boolean isHidden(Class<?> self) {
		return Binoculars.isHiddenClass(self);
	}
}