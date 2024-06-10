package com.chocohead.nsn;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class UsedTypeVisitor extends ClassVisitor {
	private final MethodVisitor methodVisitor = new TypeSpectator() {
		@Override
		protected void visitType(Type type) {
			if (type.getSort() != Type.OBJECT) throw new IllegalArgumentException("Raw non-object type " + type + " in " + name);

			//Java types we can't transform no matter what so no point keeping those 
			if (!type.getInternalName().startsWith("java/")) usedTypes.add(type);
		}
	};
	private String name;
	private final Set<Type> usedTypes = new HashSet<>();

	public UsedTypeVisitor() {
		super(Opcodes.ASM9);
	}

	public UsedTypeVisitor(ClassVisitor next) {
		super(Opcodes.ASM9, next);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);

		this.name = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		return methodVisitor;
	}

	public Set<String> getUsedClasses() {
		return Collections.unmodifiableSet(usedTypes.stream().map(Type::getInternalName).collect(Collectors.toSet()));
	}

	public void reset() {
		name = null;
		usedTypes.clear();
	}
}