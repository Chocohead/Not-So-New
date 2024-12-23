package com.chocohead.nsn;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public abstract class TypeSpectator extends MethodVisitor {
	private String lastString;

	public TypeSpectator() {
		super(Opcodes.ASM9);
	}

	protected abstract void visitType(Type type);

	private void checkedVisitType(Type type) {
		switch (type.getSort()) {
		case Type.ARRAY:
			checkedVisitType(type.getElementType());
			break;

		case Type.OBJECT:
			visitType(type);
			break;

		case Type.METHOD:
			throw new IllegalArgumentException("Tried to visit a method? " + type);
		}
	}

	@Override
	public void visitTypeInsn(int opcode, String type) {
		checkedVisitType(Type.getObjectType(type));
		lastString = null;
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
		checkedVisitType(Type.getObjectType(owner));
		checkedVisitType(Type.getType(descriptor));
		lastString = null;
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
		checkedVisitType(Type.getObjectType(owner));
		for (Type arg : Type.getArgumentTypes(descriptor)) checkedVisitType(arg);
		checkedVisitType(Type.getReturnType(descriptor));

		if (lastString != null) {
			if ("java/lang/Class".equals(owner) && "forName".equals(name)) {
				checkedVisitType(Type.getObjectType(lastString.replace('.', '/')));
			}
			lastString = null;
		}
	}

	@Override
	public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
		for (Type arg : Type.getArgumentTypes(descriptor)) checkedVisitType(arg);
		checkedVisitType(Type.getReturnType(descriptor));
		visitHandle(bootstrapMethodHandle);
		for (Object arg : bootstrapMethodArguments) visitLdcInsn(arg);
		lastString = null;
	}

	@Override
	public void visitLdcInsn(Object value) {
		lastString = null;
		if (value instanceof Type) {
			Type type = (Type) value;

			switch (type.getSort()) {
			case Type.ARRAY:
				checkedVisitType(type.getElementType());
				break;

			case Type.OBJECT:
				visitType(type);
				break;

			case Type.METHOD:
				for (Type arg : type.getArgumentTypes()) checkedVisitType(arg);
				checkedVisitType(type.getReturnType());
				break;
			}
		} else if (value instanceof Handle) {
			visitHandle((Handle) value);
		} else if (value instanceof ConstantDynamic) {
			ConstantDynamic constant = (ConstantDynamic) value;

			checkedVisitType(Type.getType(constant.getDescriptor()));
			visitHandle(constant.getBootstrapMethod());
			for (int i = 0, end = constant.getBootstrapMethodArgumentCount(); i < end; i++) visitLdcInsn(constant.getBootstrapMethodArgument(i));
		} else if (value instanceof String) {
			lastString = (String) value;
		}
	}

	private void visitHandle(Handle handle) {
		switch (handle.getTag()) {
		case Opcodes.H_INVOKEVIRTUAL:
		case Opcodes.H_INVOKESTATIC:
		case Opcodes.H_INVOKEINTERFACE:
		case Opcodes.H_INVOKESPECIAL:
		case Opcodes.H_NEWINVOKESPECIAL: //Method handle
			checkedVisitType(Type.getObjectType(handle.getOwner()));
			for (Type arg : Type.getArgumentTypes(handle.getDesc())) checkedVisitType(arg);
			checkedVisitType(Type.getReturnType(handle.getDesc()));
			break;

		case Opcodes.H_GETFIELD:
		case Opcodes.H_GETSTATIC:
		case Opcodes.H_PUTFIELD:
		case Opcodes.H_PUTSTATIC: //Field handle
			checkedVisitType(Type.getObjectType(handle.getOwner()));
			checkedVisitType(Type.getType(handle.getDesc()));
			break;

		default: //Something else handle?
			throw new IllegalArgumentException("Unexpected handle tag " + handle.getTag() + " for " + handle);
		}
	}

	@Override
	public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
		checkedVisitType(Type.getType(descriptor).getElementType());
		lastString = null;
	}

	@Override
	public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
		if (type != Opcodes.F_SAME1) {
			lastString = null;
		}
	}

	@Override
	public void visitInsn(int opcode) {
		if (opcode != Opcodes.NOP) {
			lastString = null;
		}
	}

	@Override
	public void visitIntInsn(int opcode, int operand) {
		lastString = null;
	}

	@Override
	public void visitVarInsn(int opcode, int varIndex) {
		lastString = null;
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		lastString = null;
	}

	@Override
	public void visitIincInsn(int varIndex, int increment) {
		lastString = null;
	}

	@Override
	public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
		lastString = null;
	}

	@Override
	public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
		lastString = null;
	}
}