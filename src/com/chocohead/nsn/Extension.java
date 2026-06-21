package com.chocohead.nsn;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.mixin.transformer.ext.extensions.ExtensionCheckClass;
import org.spongepowered.asm.util.Annotations;

public class Extension extends ExtensionCheckClass {
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

		if (checkSugars(context.getClassNode())) {
			for (MethodNode method : context.getClassNode().methods) {
				for (AbstractInsnNode insn : method.instructions) {
					if (insn.getType() == AbstractInsnNode.TYPE_INSN) {
						TypeInsnNode tin = (TypeInsnNode) insn;

						switch (tin.desc) {
						case "java/util/SequencedSet":
							tin.desc = "java/util/Set";
							break;
						case "java/util/SequencedMap":
							tin.desc = "java/util/Map";
							break;
						}
					}
				}
			}
		}
	}

	private static boolean checkSugars(ClassNode node) {
		for (MethodNode method : node.methods) {
			if (Annotations.get(method.invisibleAnnotations, "Lcom/llamalad7/mixinextras/sugar/SugarBridge;") != null) {
				return true; //Most of the useful metadata is stripped by SugarInjector
			}
		}

		return false;
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