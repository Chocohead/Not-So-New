package com.chocohead.nsn;

import java.util.Collections;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import org.spongepowered.asm.mixin.Mixin;

public class MixinChecker extends ClassVisitor {
	private static final String TARGET = Type.getDescriptor(Mixin.class);
	private final AnnotationVisitor mixinVisitor = new AnnotationVisitor(api) {
		@Override
		public AnnotationVisitor visitArray(String name) {
			return "value".equals(name) || "targets".equals(name) ? this : null;
		}

		@Override
		public void visit(String name, Object value) {
			if (name == null) {
				if (value instanceof String) {
					targets.add(((String) value).replace('.', '/'));
				} else if (value instanceof Type) {
					targets.add(((Type) value).getInternalName());
				} else {
					System.out.println("Unexpected array type: " + value);
				}
			}
		}
	};
	final Set<String> targets = new ObjectOpenHashSet<>();
	private boolean isMixin;

	public MixinChecker() {
		super(Opcodes.ASM9);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (TARGET.equals(descriptor)) {
			isMixin = true;
			return mixinVisitor;
		}

		return null;
	}

	public boolean isMixin() {
		return isMixin;
	}

	public Set<String> getTargets() {
		return Collections.unmodifiableSet(targets);
	}

	public void reset() {
		isMixin = false;
		targets.clear();
	}
}