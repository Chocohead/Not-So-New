package com.chocohead.nsn;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Iterables;

import org.apache.commons.lang3.reflect.FieldUtils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.util.Annotations;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public class LoadGuard implements PreLaunchEntrypoint {
	private static void clearInterface(String target, String name) {
		Set<String> interfaces = BulkRemapper.HUMBLE_INTERFACES.get(target);
		if (interfaces == null) return; //Never considered

		interfaces.remove(name);
		if (interfaces.isEmpty()) BulkRemapper.HUMBLE_INTERFACES.remove(target);
	}

	@Override
	@SuppressWarnings("unchecked") //Maybe a little
	public void onPreLaunch() {
		Set<String> trouble = new HashSet<>();

		Map<String, IMixinInfo> accessors;
		out: try {
			Class<?> processorType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinProcessor");
			Object processor = StickyTape.grabTransformer(processorType, "processor");

			List<?> processors = (List<?>) FieldUtils.readDeclaredField(processor, "coprocessors", true);
			Class<?> accessorProcessor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinCoprocessorAccessor");

			for (Object thing : processors) {
				if (thing.getClass() == accessorProcessor) {
					accessors = (Map<String, IMixinInfo>) FieldUtils.readDeclaredField(thing, "accessorMixins", true);
					break out;
				}
			}

			throw new IllegalStateException("Unable to find accessor coprocessor in " + processors);
		} catch (ReflectiveOperationException | ClassCastException e) {
			throw new IllegalStateException("Running with a transformer that doesn't have a processor?", e);
		}

		on: for (IMixinInfo accessor : accessors.values()) {
			if ("nsn.mixins.json".equals(accessor.getConfig().getName()) || "mixins.mm.json".equals(accessor.getConfig().getName())) continue;
			ClassNode node = accessor.getClassNode(0);
			if (node.version <= Opcodes.V1_8) continue; //Nothing needs to be done

			for (MethodNode method : node.methods) {
				if (Modifier.isStatic(method.access) && (Annotations.getVisible(method, Accessor.class) != null || Annotations.getVisible(method, Invoker.class) != null)) {
					String target = Iterables.getOnlyElement(accessor.getTargetClasses()); //If it has an accessor or invoker Mixin mandates there only be one target
					clearInterface(target.replace('.', '/'), node.name);
					continue on;
				}
			}

			if (accessor.getTargetClasses().size() > 1) {
				for (String target : accessor.getTargetClasses()) {
					clearInterface(target.replace('.', '/'), node.name);
				}
				trouble.add(accessor.getClassName());
				continue;
			}

			try {
				Object state = FieldUtils.readDeclaredField(accessor, "state", true);
				ClassNode realNode = (ClassNode) FieldUtils.readDeclaredField(state, "classNode", true);
				ClassInfo info = (ClassInfo) FieldUtils.readDeclaredField(accessor, "info", true);

				MethodNode method = (MethodNode) attachMethod(realNode);
				((Set<Object>) FieldUtils.readDeclaredField(info, "methods", true)).add(info.new Method(method, true));
			} catch (ReflectiveOperationException | ClassCastException e) {
				System.err.println("Wonderful fixes have encountered a less wonderful moment");
				e.printStackTrace();
				break on;
			}
		}

		SpecialService.unlink(trouble);
	}

	private static MethodVisitor attachMethod(ClassVisitor visitor) {
		MethodVisitor method = visitor.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "invokeK££makeSomeMagic££", "()V", null, null);
		AnnotationVisitor invoker = method.visitAnnotation(Type.getDescriptor(Invoker.class), true);
		invoker.visit("remap", Boolean.FALSE);
		invoker.visitEnd();

		method.visitCode();
		method.visitInsn(Opcodes.RETURN);
		method.visitEnd();
		return method;
	}
}