package com.chocohead.nsn;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.MethodNode;

public class Switchy {
	public static MethodNode typeSwitch(Type type, Object[] labels) {
		Type[] args = type.getArgumentTypes();
		if (args.length != 2 || args[1] != Type.INT_TYPE || type.getReturnType() != Type.INT_TYPE) {
			throw new IllegalArgumentException("Strange call descriptor: " + type.getDescriptor());
		}

		for (Object label : labels) {
			if (label == null) throw new IllegalArgumentException("null label");

			Class<?> labelClass = label.getClass();
			if (labelClass != Type.class && labelClass != String.class && labelClass != Integer.class) {
				throw new IllegalArgumentException("Unexpected label type: " + labelClass);
			}
		}

		return makeSwitcher(args[0], type, labels);
	}

	public static MethodNode enumSwitch(Type type, Object[] labels) {
		Type[] args = type.getArgumentTypes();
		if (args.length != 2 || args[0].getSort() != Type.OBJECT || args[1] != Type.INT_TYPE || type.getReturnType() != Type.INT_TYPE) {
			throw new IllegalArgumentException("Strange call descriptor: " + type.getDescriptor());
		}

		//If there's no cases the switch is just a nullness test
		if (labels.length == 0) return makeSwitcher(args[0], type, labels);

		for (Object label : labels) {
			if (label == null) throw new IllegalArgumentException("null label");

			Class<?> labelClass = label.getClass();
			if (labelClass == Type.class) {
				if (!args[0].equals(label)) throw new IllegalArgumentException("Expected switched enum label (" + args[0] + ") but have " + label);

				throw new UnsupportedOperationException("TODO");
			} else if (labelClass != String.class) {
				throw new IllegalArgumentException("Unexpected label type: " + labelClass);
			}
		}

		return null; //We'll do everything at link time
	}

	@SuppressWarnings("unchecked")
	public static CallSite enumSwitch(Lookup lookup, String name, MethodType type, String... labels) throws ReflectiveOperationException {
		Enum<?>[] jumpMap = new Enum[labels.length];
		@SuppressWarnings("rawtypes") //Not much can be done about that
		Class<? extends Enum> enumType = type.parameterType(0).asSubclass(Enum.class);

		for (int i = 0; i < labels.length; i++) {
			jumpMap[i] = Enum.valueOf(enumType, labels[i]);
		}

		return new ConstantCallSite(lookup.findStatic(Switchy.class, "enumSwitch", type.changeParameterType(0, Enum.class).insertParameterTypes(0, Enum[].class)).bindTo(jumpMap).asType(type));
	}

	public static <E extends Enum<E>> int enumSwitch(E[] jumpMap, E value, int from) {
		if (value == null) return -1;
		int end = jumpMap.length;

		for (int i = from; i < end; i++) {
			if (jumpMap[i] == value) {
				return i;
			}
		}

		return end;
	}

	private static final AtomicInteger SWITCH_COUNTER = new AtomicInteger();
	private static MethodNode makeSwitcher(Type switched, Type type, Object[] labels) {
		MethodNode out = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "switch£" + SWITCH_COUNTER.getAndIncrement(), type.getDescriptor(), null, null);
		InstructionAdapter method = new InstructionAdapter(out);

		assert switched.equals(type.getArgumentTypes()[0]);
		method.load(0, switched);
		Label notNull = new Label();
		method.ifnonnull(notNull);
		method.iconst(-1);
		method.areturn(Type.INT_TYPE);
		method.visitLabel(notNull);
		method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

		if (labels.length == 0) {
			method.iconst(0);
			method.areturn(Type.INT_TYPE);
		} else {
			Label noMatch = new Label();
			Label[] cases = new Label[labels.length];
			Label[] next = new Label[labels.length];
			cases[labels.length - 1] = new Label();
			next[labels.length - 1] = noMatch;
			for (int i = labels.length - 2; i >= 0; i--) {
				cases[i] = new Label();
				next[i] = (labels[i] == labels[i + 1] ? next : cases)[i + 1];
			}

			method.load(1, Type.INT_TYPE);
			method.tableswitch(0, labels.length - 1, noMatch, cases);
			for (int i = 0; i < labels.length; i++) {
				method.visitLabel(cases[i]);
				method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
				Object label = labels[i];
				if (label instanceof Type) {
					method.load(0, switched);
					method.instanceOf((Type) label);
					method.ifeq(next[i]);
				} else if (label instanceof String) {
					method.aconst(label);
					method.load(0, switched);
					method.invokevirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
					method.ifeq(next[i]);
				} else if (label instanceof Integer) {
					method.load(0, switched);
					Type number = Type.getType(Number.class);
					method.instanceOf(number);
					Label notNumber = new Label();
					method.ifeq(notNumber);
					method.load(0, switched);
					method.checkcast(number);
					method.invokevirtual(number.getInternalName(), "intValue", "()I", false);
					Label haveNumber = new Label();
					method.goTo(haveNumber);
					method.visitLabel(notNumber);
					method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
					method.load(0, switched);
					Type character = Type.getType(Character.class);
					method.instanceOf(character);
					method.ifeq(next[i]);
					method.load(0, switched);
					method.checkcast(character);
					method.invokevirtual(character.getInternalName(), "charValue", "()C", false);
					method.visitLabel(haveNumber);
					method.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
					method.iconst((Integer) label);
					method.ificmpne(next[i]);
				} else if (label instanceof ConstantDynamic) {
					throw new UnsupportedOperationException("TODO"); //An enum constant
				} else {
					throw new AssertionError("Unexpected label type: " + label);
				}
				method.iconst(i);
				method.areturn(Type.INT_TYPE);
			}
			method.visitLabel(noMatch);
			method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			method.iconst(labels.length);
			method.areturn(Type.INT_TYPE);
		}

		return out;
	}
}