package com.chocohead.nsn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.TinyTree;

import com.chocohead.mm.api.ClassTinkerers;

public class BulkRemapper implements Runnable {
	private void transform(ClassNode node) {
		node.version = Opcodes.V1_8;

		List<MethodNode> extraMethods = new ArrayList<>();
		for (MethodNode method : node.methods) {
			for (ListIterator<AbstractInsnNode> it = method.instructions.iterator(); it.hasNext();) {
				AbstractInsnNode insn = it.next();

				if (insn.getType() == AbstractInsnNode.INVOKE_DYNAMIC_INSN) {
					Handle bootstrap = ((InvokeDynamicInsnNode) insn).bsm;

					if ("java/lang/invoke/StringConcatFactory".equals(bootstrap.getOwner())) {
						InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) insn;

						switch (bootstrap.getName()) {
						case "makeConcat": {
							MethodNode concat = Stringy.makeConcat(Type.getArgumentTypes(idin.desc));
							//System.out.println("Transforming " + idin.name + idin.desc + " to " + concat.name + concat.desc);
							it.set(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, concat.name, concat.desc));
							extraMethods.add(concat);
							break;
						}

						case "makeConcatWithConstants": {
							MethodNode concat = Stringy.makeConcat((String) idin.bsmArgs[0], Type.getArgumentTypes(idin.desc), Arrays.copyOfRange(idin.bsmArgs, 1, idin.bsmArgs.length));
							//System.out.println("Transforming " + idin.name + idin.desc + " to " + concat.name + concat.desc);
							it.set(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, concat.name, concat.desc));
							extraMethods.add(concat);
							break;
						}
						}
					}
					
				}
			}
		}
		node.methods.addAll(extraMethods);
	}

	@Override
	public void run() {
		TinyTree mappings = FabricLauncherBase.getLauncher().getMappingConfiguration().getMappings();

		String activeNamespace = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
		for (ClassDef clazz : mappings.getClasses()) {
			ClassTinkerers.addTransformation(clazz.getName(activeNamespace), this::transform);
		}
		for (String clazz : Arrays.asList("net/minecraft/client/main/Main$2", "com/mojang/blaze3d/systems/RenderSystem", "com/mojang/blaze3d/platform/GlStateManager", "com/mojang/blaze3d/platform/GLX", "com/mojang/blaze3d/platform/TextureUtil", "com/mojang/blaze3d/platform/GlConst")) {
			ClassTinkerers.addTransformation(clazz, this::transform);
		}
	}
}