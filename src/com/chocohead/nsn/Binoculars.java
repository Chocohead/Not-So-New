package com.chocohead.nsn;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import com.chocohead.mm.api.ClassTinkerers;

public class Binoculars {
	public static Lookup privateLookupIn(Class<?> type, Lookup unused) throws IllegalAccessException {
		try {
			Constructor<Lookup> constructor = Lookup.class.getDeclaredConstructor(Class.class);
			constructor.setAccessible(true); //Please
			return constructor.newInstance(type);
		} catch (ReflectiveOperationException e) {
			throw (IllegalAccessException) new IllegalAccessException("Unable to make Lookup for " + type).initCause(e);
		}
	}

	public static Class<?> defineClass(Lookup context, byte[] bytes) throws IllegalAccessException {
		ClassReader reader = new ClassReader(bytes);

		String name;
		if (reader.readShort(6) > Opcodes.V1_8) {
			ClassNode node = new ClassNode();
			reader.accept(node, 0);

			BulkRemapper.transform(node);

			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			bytes = writer.toByteArray();
			name = node.name;
		} else {
			name = reader.getClassName();
		}

		if (context.lookupClass().getClassLoader() == ClassLoader.getSystemClassLoader()) {//Much hacks needed
			try {
				Method define = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
				define.setAccessible(true); //Very much please
				return (Class<?>) define.invoke(ClassLoader.getSystemClassLoader(), name.replace('/', '.'), bytes, 0, bytes.length);
			} catch (ReflectiveOperationException | ClassCastException e) {
				e.printStackTrace();
				throw (IllegalAccessException) new IllegalAccessException("Unable to define class " + name).initCause(e);
			}
		} else {//Not quite as many hacks needed
			ClassTinkerers.define(name, bytes);

			try {
				return Class.forName(name.replace('/', '.'), false, Binoculars.class.getClassLoader());
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException("Failed to define class for " + name + '?', e);
			}
		}
	}

	private static boolean isFinal(Class<?> definingClass, String name) throws NoSuchFieldException {
		return Modifier.isFinal(definingClass.getDeclaredField(name).getModifiers());
	}

	public static VarHandle findVarHandle(Lookup context, Class<?> definingClass, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
		return new VarHandle(false, context.findGetter(definingClass, name, type), isFinal(definingClass, name) ? null : context.findSetter(definingClass, name, type));
	}

	public static VarHandle findStaticVarHandle(Lookup context, Class<?> definingClass, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
		return new VarHandle(true, context.findStaticGetter(definingClass, name, type), isFinal(definingClass, name) ? null : context.findStaticSetter(definingClass, name, type));
	}

	public static VarHandle unreflectVarHandle(Lookup context, Field field) throws IllegalAccessException {
		return new VarHandle(Modifier.isStatic(field.getModifiers()), context.unreflectGetter(field), Modifier.isFinal(field.getModifiers()) ? null : context.unreflectSetter(field));
	}

	public static VarHandle arrayElementVarHandle(Class<?> arrayClass) {
		return VarHandle.forArray(MethodHandles.arrayElementGetter(arrayClass), MethodHandles.arrayElementSetter(arrayClass));
	}
}