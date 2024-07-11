package com.chocohead.nsn;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.primitives.Primitives;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import com.chocohead.mm.api.ClassTinkerers;

public class Binoculars {
	private static final AtomicInteger HIDDEN_CLASS_COUNT = new AtomicInteger(1);
	private static final Map<Class<?>, Object> HIDDEN_CLASSES = new IdentityHashMap<>();

	public static Lookup privateLookupIn(Class<?> type, Lookup unused) throws IllegalAccessException {
		try {
			Constructor<Lookup> constructor = Lookup.class.getDeclaredConstructor(Class.class);
			constructor.setAccessible(true); //Please
			return constructor.newInstance(type);
		} catch (ReflectiveOperationException e) {
			throw (IllegalAccessException) new IllegalAccessException("Unable to make Lookup for " + type).initCause(e);
		}
	}

	public static <T> T classData(Lookup caller, String name, Class<T> type) {
		if (!"_".equals(name)) {
			throw new IllegalArgumentException("name must be \"_\" but is: " + name);
		}

		Object data = HIDDEN_CLASSES.get(Objects.requireNonNull(caller, "caller").lookupClass());
		return data == null ? null : castWideningPrimitives(data, type);
	}

	public static <T> T classDataAt(Lookup caller, String name, Class<T> type, int index) {
		List<?> data = classData(caller, name, List.class);
		return data == null ? null : castWideningPrimitives(data.get(index), type);
	}

	@SuppressWarnings("unchecked")
	private static <T> T castWideningPrimitives(Object thing, Class<T> target) {
		//If the target type is not primitive, the object can be cast to it directly
		if (!Objects.requireNonNull(target, "target").isPrimitive()) return target.cast(thing);
		//The target type is primitive, but casting might not be needed if we already have the right boxed form
		if (Objects.requireNonNull(thing).getClass() == Primitives.wrap(target)) return (T) thing;
		//Need to widen the object in some way, so long as it is numberish to start with
		Number number;
		if (thing instanceof Number) {
			number = (Number) thing;
		} else if (thing instanceof Character) {
			number = (int) (Character) thing;
		} else {
			throw new ClassCastException(thing + " is not instance of " + target.getName());
		}
		switch (Type.getType(target).getSort()) {
		case Type.BYTE:
			return (T) (Byte) number.byteValue();
		case Type.SHORT:
			return (T) (Short) number.shortValue();
		case Type.CHAR:
			return (T) Character.valueOf((char) number.intValue());
		case Type.INT:
			return (T) (Integer) number.intValue();
		case Type.LONG:
			return (T) (Long) number.longValue();
		case Type.FLOAT:
			return (T) (Float) number.floatValue();
		case Type.DOUBLE:
			return (T) (Double) number.doubleValue();
		case Type.BOOLEAN:
			throw new ClassCastException("Cannot cast " + thing + " to a boolean");
		case Type.VOID:
			throw new ClassCastException("Cannot cast " + thing + " as a void");
		case Type.ARRAY:
		case Type.OBJECT:
		case Type.METHOD:
		default:
			throw new IllegalStateException("Expected primitive sort but got " + Type.getType(target));
		}
	}

	public static Class<?> defineClass(Lookup context, byte[] bytes) throws IllegalAccessException {
		return defineClass(context, bytes, false);
	}

	private static Class<?> defineClass(Lookup context, byte[] bytes, boolean hidden) throws IllegalAccessException {
		ClassReader reader = new ClassReader(bytes);

		String name;
		if (reader.readShort(6) > Opcodes.V1_8) {
			ClassNode node = new ClassNode();
			reader.accept(node, 0);

			if (hidden) ClassNodeRemapper.remap(node, new Remapper() {
				private final String originalName = node.name;
				private final String newName = node.name + "*" + HIDDEN_CLASS_COUNT.getAndIncrement();

				@Override
				public String map(String internalName) {
					return originalName.equals(internalName) ? newName : internalName;
				}
			});
			BulkRemapper.transform(node);

			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			bytes = writer.toByteArray();
			name = node.name;
		} else if (hidden) {
			name = reader.getClassName() + '*' + HIDDEN_CLASS_COUNT.getAndIncrement();
			ClassWriter writer = new ClassWriter(reader, 0);
			reader.accept(new ClassRemapper(writer, new Remapper() {
				private final String originalName = reader.getClassName();

				@Override
				public String map(String internalName) {
					return originalName.equals(internalName) ? name : internalName;
				}
			}), 0);
			bytes = writer.toByteArray();
			assert name.equals(new ClassReader(bytes).getClassName()): name;
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

	public enum ClassOption {
        NESTMATE,
        STRONG;
	}

	public static Lookup defineHiddenClass(Lookup context, byte[] bytes, boolean initialise, ClassOption... options) throws IllegalAccessException {
		Objects.requireNonNull(options, "options");
		return defineHiddenClass(context, bytes, null, initialise);
	}

	public static Lookup defineHiddenClassWithClassData(Lookup context, byte[] bytes, Object classData, boolean initialise, ClassOption... options) throws IllegalAccessException {
		Objects.requireNonNull(options, "options");
		return defineHiddenClass(context, bytes, Objects.requireNonNull(classData, "classData"), initialise);
	}

	private static Lookup defineHiddenClass(Lookup context, byte[] bytes, Object classData, boolean initialise) throws IllegalAccessException {
		Class<?> out = defineClass(context, bytes, true);
		HIDDEN_CLASSES.put(out, classData);
		if (initialise) {
			try {
				Class.forName(out.getName(), true, out.getClassLoader());
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException("Failed to find hidden class " + out.getName() + '?', e);
			}
		}
		return privateLookupIn(out, context);
	}

	static boolean isHiddenClass(Class<?> type) {
		return HIDDEN_CLASSES.containsKey(type);
	}

	public static Class<?> findClass(Lookup context, String targetName) throws ClassNotFoundException, IllegalAccessException {
		return Class.forName(targetName, false, context.lookupClass().getClassLoader()); //Should check the access relative to the lookup class...
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