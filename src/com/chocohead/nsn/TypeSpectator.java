package com.chocohead.nsn;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public abstract class TypeSpectator extends MethodVisitor {
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
	}

	@Override
	public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
		checkedVisitType(Type.getObjectType(owner));
		checkedVisitType(Type.getType(descriptor));
	}

	@Override
	public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
		checkedVisitType(Type.getObjectType(owner));
		for (Type arg : Type.getArgumentTypes(descriptor)) checkedVisitType(arg);
		checkedVisitType(Type.getReturnType(descriptor));
	}

	@Override
	public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
		for (Type arg : Type.getArgumentTypes(descriptor)) checkedVisitType(arg);
		checkedVisitType(Type.getReturnType(descriptor));
		visitHandle(bootstrapMethodHandle);
		for (Object arg : bootstrapMethodArguments) visitLdcInsn(arg);
	}

	@Override
	public void visitLdcInsn(Object value) {
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
	}
}