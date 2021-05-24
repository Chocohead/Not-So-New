package com.chocohead.nsn;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.MethodNode;

public class Recordy {
	public static boolean equals(boolean a, boolean b) {
		return a == b;
	}

	public static boolean equals(byte a, byte b) {
		return a == b;
	}

	public static boolean equals(short a, short b) {
		return a == b;
	}

	public static boolean equals(char a, char b) {
		return a == b;
	}

	public static boolean equals(int a, int b) {
		return a == b;
	}

	public static boolean equals(long a, long b) {
		return a == b;
	}

	public static boolean equals(float a, float b) {
		return Float.compare(a, b) == 0;
	}

	public static boolean equals(double a, double b) {
		return Double.compare(a, b) == 0;
	}

	private static void equals(InstructionAdapter method, Handle field) {
		Type type = Type.getType(field.getDesc());

		String owner;
		switch (type.getSort()) {
		case Type.BOOLEAN:
		case Type.BYTE:
		case Type.SHORT:
		case Type.CHAR:
		case Type.INT:
		case Type.LONG:
		case Type.FLOAT:
		case Type.DOUBLE:
			owner = "com/chocohead/nsn/Recordy";
			break;

		case Type.ARRAY:
		case Type.OBJECT:
			owner = "java/lang/Objects";
			break;

		case Type.VOID:
		case Type.METHOD:
		default:
			throw new IllegalArgumentException("Unexpected field type: " + field.getDesc() + " (sort " + Type.getType(field.getDesc()).getSort() + ')');
		}

		method.invokestatic(owner, "equals", '(' + type.getDescriptor() + type.getDescriptor() + ")Z", false);
	}

	public static MethodNode makeEquals(String type, Handle... fields) {
		return makeMethod("hashCode", "(Ljava/lang/Object;)Z", method -> {
			Type thisType = Type.getObjectType(type);

			method.load(1, InstructionAdapter.OBJECT_TYPE);
			method.load(0, thisType);
			Label notIdenticallyEqual = new Label();
			method.ifacmpne(notIdenticallyEqual);
			method.iconst(1);
			method.areturn(Type.BOOLEAN_TYPE);
			method.mark(notIdenticallyEqual);

			method.load(1, InstructionAdapter.OBJECT_TYPE);
			method.instanceOf(thisType);
			Label isInstance = new Label();
			method.ifne(isInstance);
			method.iconst(0);
			method.areturn(Type.BOOLEAN_TYPE);
			method.mark(isInstance);

			if (fields.length > 0) {
				method.load(1, InstructionAdapter.OBJECT_TYPE);
				method.checkcast(thisType);
				method.store(2, thisType);

				for (Handle field : fields) {
					method.load(0, thisType);
					method.getfield(type, field.getName(), field.getDesc());
					method.load(2, thisType);
					method.getfield(type, field.getName(), field.getDesc());
					equals(method, field);
					Label equal = new Label();
					method.ifne(equal);
					method.iconst(0);
					method.areturn(Type.BOOLEAN_TYPE);
					method.mark(equal);
				}
			}

			method.iconst(1);
			method.areturn(Type.BOOLEAN_TYPE);
		});
	}

	private static void hashCode(InstructionAdapter method, Handle field) {
		String owner, desc;
		switch (Type.getType(field.getDesc()).getSort()) {
		case Type.BOOLEAN:
			owner = "java/lang/Boolean";
			desc = "(Z)I";
			break;

		case Type.BYTE:
			owner = "java/lang/Byte";
			desc = "(B)I";
			break;

		case Type.SHORT:
			owner = "java/lang/Short";
			desc = "(S)I";
			break;

		case Type.CHAR:
			owner = "java/lang/Character";
			desc = "(C)I";
			break;

		case Type.INT:
			owner = "java/lang/Integer";
			desc = "(I)I";
			break;

		case Type.LONG:
			owner = "java/lang/Long";
			desc = "(J)I";
			break;

		case Type.FLOAT:
			owner = "java/lang/Float";
			desc = "(F)I";
			break;

		case Type.DOUBLE:
			owner = "java/lang/Double";
			desc = "(D)I";
			break;

		case Type.ARRAY:
		case Type.OBJECT:
			owner = "java/lang/Objects";
			desc = "(Ljava/lang/Object;)I";
			break;

		case Type.VOID:
		case Type.METHOD:
		default:
			throw new IllegalArgumentException("Unexpected field type: " + field.getDesc() + " (sort " + Type.getType(field.getDesc()).getSort() + ')');
		}

		method.invokestatic(owner, "hashCode", desc, false);
	}

	public static MethodNode makeHashCode(String type, Handle... fields) {
		return makeMethod("hashCode", "()I", method -> {
			if (fields.length > 0) {
				Type thisType = Type.getObjectType(type);
				Handle field = fields[0];
				method.load(0, thisType);
				method.getfield(type, field.getName(), field.getDesc());
				hashCode(method, field);

				for (int i = 1, end = fields.length; i < end; i++) {
					field = fields[i];

					method.iconst(31);
					method.mul(Type.INT_TYPE);

					method.load(0, thisType);
					method.getfield(type, field.getName(), field.getDesc());
					hashCode(method, field);	
					method.add(Type.INT_TYPE);
				}
			} else {
				method.iconst(0);
			}

			method.areturn(Type.INT_TYPE);
		});
	}

	private static void toString(InstructionAdapter method, Handle field) {
		String owner, name, desc;
		switch (Type.getType(field.getDesc()).getSort()) {
		case Type.BOOLEAN:
			owner = "java/lang/String";
			name = "valueOf";
			desc = "(Z)Ljava/lang/String;";
			break;

		case Type.BYTE:
			owner = "java/lang/Byte";
			name = "toString";
			desc = "(B)Ljava/lang/String;";
			break;

		case Type.SHORT:
			owner = "java/lang/Short";
			name = "toString";
			desc = "(S)Ljava/lang/String;";
			break;

		case Type.CHAR:
			owner = "java/lang/String";
			name = "valueOf";
			desc = "(C)Ljava/lang/String;";
			break;

		case Type.INT:
			owner = "java/lang/Integer";
			name = "toString";
			desc = "(I)Ljava/lang/String;";
			break;

		case Type.LONG:
			owner = "java/lang/Long";
			name = "toString";
			desc = "(J)Ljava/lang/String;";
			break;

		case Type.FLOAT:
			owner = "java/lang/Float";
			name = "toString";
			desc = "(F)Ljava/lang/String;";
			break;

		case Type.DOUBLE:
			owner = "java/lang/Double";
			name = "toString";
			desc = "(D)Ljava/lang/String;";
			break;

		case Type.ARRAY:
		case Type.OBJECT:
			owner = "java/lang/String";
			name = "valueOf";
			desc = "(Ljava/lang/Object;)Ljava/lang/String;";
			break;

		case Type.VOID:
		case Type.METHOD:
		default:
			throw new IllegalArgumentException("Unexpected field type: " + field.getDesc() + " (sort " + Type.getType(field.getDesc()).getSort() + ')');
		}

		method.invokestatic(owner, name, desc, false);
	}

	public static MethodNode makeToString(String type, Handle... fields) {
		return makeMethod("toString", "()Ljava/lang/String;", method -> {
			if (fields.length > 0) {
				method.anew(Type.getObjectType("java/lang/StringBuilder"));
				method.dup();
				method.invokespecial("java/lang/StringBuilder", "<init>", "()V", false);

				Type thisType = Type.getObjectType(type);
				Handle field = fields[0];
				method.aconst(type + '[' + field.getName() + '=');
				method.invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
				method.load(0, thisType);
				method.getfield(type, field.getName(), field.getDesc());
				toString(method, field);
				method.invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

				for (int i = 1, end = fields.length; i < end; i++) {
					field = fields[i];

					method.aconst(", " + field.getName() + '=');
					method.invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
					method.load(0, thisType);
					method.getfield(type, field.getName(), field.getDesc());
					toString(method, field);
					method.invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);	
				}

				method.iconst(']');
				method.invokevirtual("java/lang/StringBuilder", "append", "(C)Ljava/lang/StringBuilder;", false);
				method.invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
			} else {
				method.aconst(type.concat("[]"));
			}

			method.areturn(Type.getObjectType("java/lang/String"));
		});
	}

	private static final AtomicInteger METHOD_COUNTER = new AtomicInteger();
	private static MethodNode makeMethod(String name, String desc, Consumer<InstructionAdapter> filler) {
		MethodNode out = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, name + '£' + METHOD_COUNTER.getAndIncrement(), desc, null, null);

		filler.accept(new InstructionAdapter(out));

		return out;
	}
}