package com.chocohead.nsn;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.transformer.ClassInfo;

public class StickyTape {
	static void tape() {
		ClassNode node = new ClassNode();
		node.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "java/lang/invoke/StringConcatFactory", null, "java/lang/Object", null);
		addMethod(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "makeConcat", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", method -> {
			method.visitCode();
			method.visitInsn(Opcodes.ACONST_NULL);
			method.visitInsn(Opcodes.ARETURN);
		});
		addMethod(node, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VARARGS, "makeConcatWithConstants",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", method -> {
			method.visitCode();
			method.visitInsn(Opcodes.ACONST_NULL);
			method.visitInsn(Opcodes.ARETURN);
		});
		node.visitEnd();

		try {
			Method method = ClassInfo.class.getDeclaredMethod("fromClassNode", ClassNode.class);
			method.setAccessible(true);
			method.invoke(null, node);
		} catch (ReflectiveOperationException e) {
			System.err.println("Failed to add ClassInfo!");
			e.printStackTrace(); //Probably not good
		}
	}

	private static void addMethod(ClassNode node, int access, String name, String desc, Consumer<MethodNode> method) {
		method.accept((MethodNode) node.visitMethod(access, name, desc, null, null));
	}
}