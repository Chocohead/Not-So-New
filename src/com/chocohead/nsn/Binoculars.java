package com.chocohead.nsn;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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
}