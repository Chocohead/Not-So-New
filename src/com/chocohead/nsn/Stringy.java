package com.chocohead.nsn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.MethodNode;

public class Stringy {
	private interface RecipePart {
		char ARGUMENT = '\1';
		char CONSTANT = '\2';

		void nullCheck(InstructionAdapter visitor);

		int size(InstructionAdapter visitor);

		String append(InstructionAdapter visitor);

		static String convert(Type type) {
			switch (type.getSort()) {
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				return "(I)Ljava/lang/StringBuilder;";

			case Type.BOOLEAN:
				return "(Z)Ljava/lang/StringBuilder;";

			case Type.CHAR:
				 return "(C)Ljava/lang/StringBuilder;";

			case Type.FLOAT:
				return "(F)Ljava/lang/StringBuilder;";

			case Type.DOUBLE:
				return "(D)Ljava/lang/StringBuilder;";

			case Type.LONG:
				return "(J)Ljava/lang/StringBuilder;";

			case Type.OBJECT:
				switch (type.getInternalName()) {
				case "java/lang/String":
					return "(Ljava/lang/String;)Ljava/lang/StringBuilder;";

				case "java/lang/CharSequence":
					return "(Ljava/lang/CharSequence;)Ljava/lang/StringBuilder;";
				}
			case Type.ARRAY:
				return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";

			case Type.METHOD:
				throw new UnsupportedOperationException("Unexpected constant method: " + type);

			default:
				throw new IllegalArgumentException("Unexpected type: " + type + " (sort " + type.getSort() + ')');
			}
		}
	}

	private static class ConstantPart implements RecipePart {
		private final Object value;
		private final String type;

		public ConstantPart(Object value) {
			this.value = Objects.requireNonNull(value);

			if (value instanceof Integer) {
				type = "(I)Ljava/lang/StringBuilder;";
			} else if (value instanceof Float) {
				type = "(F)Ljava/lang/StringBuilder;";
			} else if (value instanceof Long) {
				type = "(J)Ljava/lang/StringBuilder;";
			} else if (value instanceof Double) {
				type = "(D)Ljava/lang/StringBuilder;";
			} else if (value instanceof String) {
				type = "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
			} else if (value instanceof Type) {
				type = RecipePart.convert((Type) value);
			} else if (value instanceof Handle || value instanceof ConstantDynamic) {
				throw new UnsupportedOperationException("Unexpected dynamic constant: " + value);
			} else {
				throw new IllegalArgumentException("Unexpected constant type: " + value);
			}
		}

		@Override
		public void nullCheck(InstructionAdapter visitor) {
			//Always non-null
		}

		@Override
		public int size(InstructionAdapter visitor) {
			return value.toString().length();
		}

		@Override
		public String append(InstructionAdapter visitor) {
			visitor.visitLdcInsn(value);
			return type;
		}
	}

	private static class ArgumentPart implements RecipePart {
		private final Type type;
		private final int arg;

		public ArgumentPart(Type type, int arg) {
			this.type = type;
			this.arg = arg;
		}

		@Override
		public void nullCheck(InstructionAdapter visitor) {
			if ("java/lang/String".equals(type.getInternalName())) {
				visitor.load(arg, type);
				Label skip = new Label();
				visitor.ifnonnull(skip);
				visitor.aconst("null");
				visitor.store(arg, type);
				visitor.mark(skip);
				visitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			}
		}

		@Override
		public int size(InstructionAdapter visitor) {
			switch (type.getSort()) {
			case Type.BOOLEAN:
				return 5;

			case Type.BYTE:
				return 4;

			case Type.CHAR:
				return 1;

			case Type.SHORT:
				return 6;

			case Type.INT:
				return 11;

			case Type.LONG:
				return 20;

			case Type.FLOAT:
			case Type.DOUBLE:
				return 26;

			case Type.OBJECT:
				switch (type.getInternalName()) {
				case "java/lang/String":
					visitor.load(arg, type);
					visitor.invokevirtual("java/lang/String", "length", "()I", false);
					visitor.add(Type.INT_TYPE);
					break;

				case "java/lang/CharSequence":
					visitor.load(arg, type);
					visitor.invokeinterface("java/lang/CharSequence", "length", "()I");
					visitor.add(Type.INT_TYPE);
					break;
				}
			default:
				return 0;
			}
		}

		@Override
		public String append(InstructionAdapter visitor) {
			visitor.load(arg, type);
			return RecipePart.convert(type);
		}
	}

	public static MethodNode makeConcat(Type[] args) {
		List<RecipePart> recipe = new ArrayList<>(args.length);

		int slot = 0;
		for (Type arg : args) {
			recipe.add(new ArgumentPart(arg, slot));
			slot += arg.getSize();
		}

		return makeConcat(args, recipe);
	}

	public static MethodNode makeConcat(String recipe, Type[] args, Object... constants) {
		List<RecipePart> recipeParts = new ArrayList<>(args.length + constants.length);

		int textFrom = -1;
		for (int i = 0, textIn = 0, arg = 0, slot = 0, constant = 0; i < recipe.length(); i++) {
			switch (recipe.charAt(i)) {
			case RecipePart.CONSTANT:
				if (textFrom >= 0) {
					recipeParts.add(new ConstantPart(recipe.substring(textFrom, textFrom + textIn)));
					textIn = 0;
					textFrom = -1;
				}
				recipeParts.add(new ConstantPart(constants[constant++]));
				break;

			case RecipePart.ARGUMENT:
				if (textFrom >= 0) {
					recipeParts.add(new ConstantPart(recipe.substring(textFrom, textFrom + textIn)));
					textIn = 0;
					textFrom = -1;
				}
				recipeParts.add(new ArgumentPart(args[arg], slot));
				slot += args[arg++].getSize();
				break;

			default:
				if (textFrom < 0) textFrom = i;
				textIn++;
				break;
			}
		}
		if (textFrom >= 0) {
			recipeParts.add(new ConstantPart(recipe.substring(textFrom)));
		}

		return makeConcat(args, recipeParts);
	}

	private static final AtomicInteger CONCAT_COUNTER = new AtomicInteger();
	private static MethodNode makeConcat(Type[] args, List<RecipePart> recipe) {
		MethodNode out = new MethodNode(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "concat£" + CONCAT_COUNTER.getAndIncrement(), Type.getMethodDescriptor(Type.getObjectType("java/lang/String"), args), null, null);
		InstructionAdapter method = new InstructionAdapter(out);

		for (RecipePart part : recipe) {
			part.nullCheck(method);
		}

		method.anew(Type.getObjectType("java/lang/StringBuilder"));
		method.dup();

		method.iconst(0);
		int knownSize = 0;
		for (RecipePart part : recipe) {
			knownSize += part.size(method);
		}
		if (knownSize > 0) {
			method.iconst(knownSize);
			method.add(Type.INT_TYPE);
		}
		method.invokespecial("java/lang/StringBuilder", "<init>", "(I)V", false);

		for (RecipePart part : recipe) {
			String desc = part.append(method);
			method.invokevirtual("java/lang/StringBuilder", "append", desc, false);
		}

		method.invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
		method.areturn(Type.getObjectType("java/lang/String"));

		return out;
	}
}