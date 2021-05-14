package com.chocohead.nsn;

import java.lang.reflect.Modifier;
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
import org.objectweb.asm.tree.TypeInsnNode;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.TinyTree;

import com.chocohead.mm.api.ClassTinkerers;

public class BulkRemapper implements Runnable {
	private void transform(ClassNode node) {
		node.version = Opcodes.V1_8;

		Object2IntMap<String> nameToAccess;
		if (Modifier.isInterface(node.access)) {
			nameToAccess = new Object2IntOpenHashMap<>();

			for (MethodNode method : node.methods) {
				nameToAccess.put(method.name.concat(method.desc), method.access);
			}
		} else {
			nameToAccess = Object2IntMaps.emptyMap();
		}

		List<MethodNode> extraMethods = new ArrayList<>();
		for (MethodNode method : node.methods) {
			for (ListIterator<AbstractInsnNode> it = method.instructions.iterator(); it.hasNext();) {
				AbstractInsnNode insn = it.next();

				switch (insn.getType()) {
				case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
					Handle bootstrap = ((InvokeDynamicInsnNode) insn).bsm;

					switch (bootstrap.getOwner()) {
					case "java/lang/invoke/StringConcatFactory": {
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
						break;
					}

					case "java/lang/invoke/LambdaMetafactory": {
						InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) insn;

						if (!nameToAccess.isEmpty() && idin.bsmArgs[1] instanceof Handle) {
							Handle lambda = (Handle) idin.bsmArgs[1];

							if (node.name.equals(lambda.getOwner()) && lambda.getTag() == Opcodes.H_INVOKEINTERFACE) {
								int access = nameToAccess.getInt(lambda.getName().concat(lambda.getDesc()));

								if (access != nameToAccess.defaultReturnValue() && Modifier.isPrivate(access)) {
									idin.bsmArgs[1] = new Handle(Opcodes.H_INVOKESPECIAL, lambda.getOwner(), lambda.getName(), lambda.getDesc(), lambda.isInterface());
								}
							}
						}
						break;
					}
					}
					break;
				}

				case AbstractInsnNode.METHOD_INSN: {
					MethodInsnNode min = (MethodInsnNode) insn;

					switch (min.owner) {
					case "java/nio/ByteBuffer": {
						switch (min.name.concat(min.desc)) {
						case "position(I)Ljava/nio/ByteBuffer;":
						case "limit(I)Ljava/nio/ByteBuffer;":
					    case "flip()Ljava/nio/ByteBuffer;":
					    case "clear()Ljava/nio/ByteBuffer;":
					    case "mark()Ljava/nio/ByteBuffer;":
					    case "reset()Ljava/nio/ByteBuffer;":
					    case "rewind()Ljava/nio/ByteBuffer;":
					    	min.desc = min.desc.substring(0, min.desc.length() - 11).concat("Buffer;");
					    	it.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/nio/ByteBuffer"));
					    	break;
						}
						break;
					}

					case "java/nio/FloatBuffer": {
						switch (min.name.concat(min.desc)) {
						case "position(I)Ljava/nio/FloatBuffer;":
						case "limit(I)Ljava/nio/FloatBuffer;":
					    case "flip()Ljava/nio/FloatBuffer;":
					    case "clear()Ljava/nio/FloatBuffer;":
					    case "mark()Ljava/nio/FloatBuffer;":
					    case "reset()Ljava/nio/FloatBuffer;":
					    case "rewind()Ljava/nio/FloatBuffer;":
					    	min.desc = min.desc.substring(0, min.desc.length() - 12).concat("Buffer;");
					    	it.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/nio/FloatBuffer"));
					    	break;
						}
						break;
					}

					case "java/nio/IntBuffer": {
						switch (min.name.concat(min.desc)) {
						case "position(I)Ljava/nio/IntBuffer;":
						case "limit(I)Ljava/nio/IntBuffer;":
					    case "flip()Ljava/nio/IntBuffer;":
					    case "clear()Ljava/nio/IntBuffer;":
					    case "mark()Ljava/nio/IntBuffer;":
					    case "reset()Ljava/nio/IntBuffer;":
					    case "rewind()Ljava/nio/IntBuffer;":
					    	min.desc = min.desc.substring(0, min.desc.length() - 10).concat("Buffer;");
					    	it.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/nio/IntBuffer"));
					    	break;
						}
						break;
					}

					case "java/lang/Math": {
						if ("floorMod".equals(min.name) && "(JI)I".equals(min.desc)) {
							min.owner = "com/chocohead/nsn/Maths";
						}
						break;
					}
					}
					break;
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
		for (String clazz : Arrays.asList("net/minecraft/client/main/Main$2", "com/mojang/blaze3d/systems/RenderSystem", "com/mojang/blaze3d/platform/GlStateManager", "com/mojang/blaze3d/platform/GLX",
				"com/mojang/blaze3d/platform/TextureUtil", "com/mojang/blaze3d/platform/GlConst", "net/minecraft/world/level/ColorResolver")) {
			ClassTinkerers.addTransformation(clazz, this::transform);
		}
	}
}