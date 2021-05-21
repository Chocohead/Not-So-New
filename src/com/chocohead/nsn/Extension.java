package com.chocohead.nsn;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

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
	}

	@Override
	public void postApply(ITargetClassContext context) {
		context.getClassNode().signature.replaceAll('L' + mixinPackage + "[^;]+?;", "");
	}

	@Override
	public void export(MixinEnvironment env, String name, boolean force, ClassNode node) {
		//System.out.println("Watching " + name + " go past (version " + node.version + ')');
		if (node.version > Opcodes.V1_8) BulkRemapper.transform(node);
	}
}