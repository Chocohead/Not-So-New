package com.chocohead.nsn;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import sun.reflect.Reflection;

@SuppressWarnings("restriction")
public class AccessibleObjects {
	@SuppressWarnings("deprecation")
	public static boolean canAccess(AccessibleObject self, Object from) {
		if (!(self instanceof Member)) return self.isAccessible(); //Strange home-brew AccessibleObject?

        Class<?> declaringClass = ((Member) self).getDeclaringClass();
        int modifiers = ((Member) self).getModifiers();
        if (!Modifier.isStatic(modifiers) && (self instanceof Method || self instanceof Field)) {
            if (from == null) {
                throw new IllegalArgumentException("Non-static " + self + " given null context");
            }
            if (!declaringClass.isAssignableFrom(from.getClass())) {
                throw new IllegalArgumentException("Cannot cast context as " + declaringClass.getName() + " for " + self);
            }
        } else if (from != null) {
            throw new IllegalArgumentException("Static " + self + " given context");
        }

        if (self.isAccessible() || Reflection.quickCheckMemberAccess(declaringClass, modifiers)) return true;
        Class<?> targetClass;
        if (self instanceof Constructor) {
            targetClass = declaringClass;
        } else {
            targetClass = Modifier.isStatic(modifiers) ? null : from.getClass();
        }
        return Reflection.verifyMemberAccess(Reflection.getCallerClass(2), declaringClass, targetClass, modifiers);
	}
}