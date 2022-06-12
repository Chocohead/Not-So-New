package com.chocohead.nsn.util;

import java.lang.reflect.Field;

public class Fields {
	public static Object readDeclared(Object target, String name) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	public static Object readDeclared(Class<?> target, String name) throws ReflectiveOperationException {
		Field field = target.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(null);
	}

	public static void writeDeclared(Object target, String name, Object value) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	public static void writeDeclared(Object target, String name, boolean value) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.setBoolean(target, value);
	}
}