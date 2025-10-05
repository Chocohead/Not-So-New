package com.chocohead.nsn;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

public class StickyTape {
	static void tape() {
		List<ClassNode> nodes = new ArrayList<>(8); {
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
			nodes.add(node);
		}

		FabricLoader.getInstance().getModContainer("fabricloader").ifPresent(mod -> {
			try {
				if (Version.parse("0.17.0").compareTo(mod.getMetadata().getVersion()) < 0) {
					nodes.add(createEmptyObject("java/lang/MatchException", "java/lang/RuntimeException"));
					nodes.add(createEmptyObject("java/lang/Record", "java/lang/Object"));
					nodes.add(createEmptyObject("java/lang/StackWalker", "java/lang/Object"));
					nodes.add(createEmptyObject("java/lang/runtime/SwitchBootstraps", "java/lang/Object"));
					nodes.add(createEmptyInterface("java/util/SequencedSet", "java/util/Set"));
					nodes.add(createEmptyInterface("java/util/SequencedMap", "java/util/Map"));
				}
			} catch (VersionParsingException e) {
				throw new AssertionError("Version isn't valid?", e);
			}
		});

		try {
			Method method = ClassInfo.class.getDeclaredMethod("fromClassNode", ClassNode.class);
			method.setAccessible(true);
			for (ClassNode node : nodes) {
				method.invoke(null, node);
			}
		} catch (ReflectiveOperationException e) {
			System.err.println("Failed to add ClassInfo!");
			e.printStackTrace(); //Probably not good
		}
	}

	private static ClassNode createEmptyInterface(String name, String parent) {
		ClassNode out = new ClassNode();
		out.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_SUPER, name, null, parent, null);
		out.visitEnd();
		return out;
	}

	private static ClassNode createEmptyObject(String name, String parent) {
		ClassNode out = new ClassNode();
		out.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, parent, null);
		out.visitEnd();
		return out;
	}

	private static void addMethod(ClassNode node, int access, String name, String desc, Consumer<MethodNode> method) {
		method.accept((MethodNode) node.visitMethod(access, name, desc, null, null));
	}

	public static <T> T grabTransformer(Class<T> type, String name) throws ReflectiveOperationException {
		Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
		if (transformer == null) throw new IllegalStateException("Not running with a transformer?");

		for (Field f : transformer.getClass().getDeclaredFields()) {
			if (f.getType() == type) {
				f.setAccessible(true); //Knock knock, we need this
				return type.cast(f.get(transformer));
			}
		}

		String foundFields = Arrays.stream(transformer.getClass().getDeclaredFields()).map(f -> f.getType() + " " + f.getName()).collect(Collectors.joining(", "));
		throw new NoSuchFieldError("Unable to find " + name + " field, only found " + foundFields);
	}
}