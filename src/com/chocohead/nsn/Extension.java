package com.chocohead.nsn;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

public class Extension implements IExtension {
	private final String mixinPackage;

	Extension(String mixinPackage) {
		this.mixinPackage = mixinPackage;
	}

	@Override
	public boolean checkActive(MixinEnvironment environment) {
		return true;
	}

	@Override
	public void preApply(ITargetClassContext context) {
		ClassNode node = context.getClassNode();

		if ("java/lang/Record".equals(node.superName)) {//ClassInfo already has Object as super class
			for (MethodNode method : node.methods) {
				if (!"<init>".equals(method.name)) continue;

				for (AbstractInsnNode insn : method.instructions) {
					if (insn.getType() == AbstractInsnNode.METHOD_INSN && insn.getOpcode() == Opcodes.INVOKESPECIAL) {
						MethodInsnNode minsn = (MethodInsnNode) insn;

						if ("<init>".equals(minsn.name) && "java/lang/Record".equals(minsn.owner)) {
							minsn.owner = "java/lang/Object";
							break; //Should only be one constructor call, Record itself is abstract
						}
					} 
				}
			}
		}
	}

	@Override
	public void postApply(ITargetClassContext context) {
		context.getClassNode().signature.replaceAll('L' + mixinPackage + "[^;]+?;", "");
	}

	@Override
	public void export(MixinEnvironment env, String name, boolean force, ClassNode node) {
		//System.out.println("Watching " + name + " go past (version " + node.version + ')');
		if (node.version > Opcodes.V1_8) BulkRemapper.transform(node);

		node.interfaces.removeIf(interfaceName -> {
			//This breaks Java's detection of annotation classes (as they only extend a single type)
			return interfaceName.startsWith(mixinPackage);
		});
	}
}