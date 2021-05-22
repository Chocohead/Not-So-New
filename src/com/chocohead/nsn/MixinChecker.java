package com.chocohead.nsn;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.transformer.MixinProcessor;

public class MixinChecker extends ClassVisitor {
	private static final String TARGET = Type.getDescriptor(Mixin.class);
	private final AnnotationVisitor mixinVisitor = new AnnotationVisitor(api) {
		@Override
		public AnnotationVisitor visitArray(String name) {
			return "value".equals(name) || "targets".equals(name) ? this : null;
		}

		@Override
		@SuppressWarnings("unchecked") //Just a little
		public void visit(String name, Object value) {
			if (name == null) {
				if (value instanceof String) {
					targets.add(() -> {
						Collection<IMixinConfig> pendingConfigs;
						try {
							MixinProcessor processor = StickyTape.grabTransformer(MixinProcessor.class, "processor");

							pendingConfigs = (Collection<IMixinConfig>) FieldUtils.readDeclaredField(processor, "pendingConfigs", true);
						} catch (ReflectiveOperationException | ClassCastException e) {
							throw new IllegalStateException("Running with a transformer that doesn't have a processor?", e);
						}
						String binaryName = MixinChecker.this.name.replace('/', '.');
						String target = (String) value;

						List<IMixinConfig> configs = Stream.concat(Mixins.getConfigs().stream().map(Config::getConfig), pendingConfigs.stream())
																			.filter(config -> binaryName.startsWith(config.getMixinPackage())).collect(Collectors.toList());
						if (configs.isEmpty()) {
							throw new UnsupportedOperationException("Unable to find Mixin config for " + MixinChecker.this.name + " targetting " + target.replace('.', '/'));
						}

						for (IMixinConfig config : configs) {
							try {
								String remap = (String) MethodUtils.invokeMethod(config, true, "remapClassName", MixinChecker.this.name, target);
								//System.out.println(config.getName() + " remapped " + target + " to " + remap + " for " + MixinChecker.this.name);
								if (!target.equals(remap)) return remap.replace('.', '/');
							} catch (ReflectiveOperationException | ClassCastException e) {
								throw new RuntimeException("Error remapping mixin target " + target.replace('.', '/'), e);
							}
						}

						return target.replace('.', '/'); //Nothing says a string target has to change
					});
				} else if (value instanceof Type) {
					targets.add(() -> ((Type) value).getInternalName());
				} else {
					System.out.println("Unexpected array type: " + value);
				}
			}
		}
	};
	private String name;
	private final Set<Supplier<String>> targets = new ObjectOpenHashSet<>();
	private boolean isMixin;
	private String nestHost;
	private final Set<String> nestMates = new ObjectOpenHashSet<>();

	public MixinChecker() {
		super(Opcodes.ASM9);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.name = name;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (TARGET.equals(descriptor)) {
			isMixin = true;
			return mixinVisitor;
		}

		return null;
	}

	@Override
	public void visitNestHost(String nestHost) {
		this.nestHost = nestHost;
	}

	@Override
	public void visitNestMember(String nestMember) {
		nestMates.add(nestMember);
	}

	public boolean isMixin() {
		return isMixin;
	}

	public Set<String> getTargets() {
		return Collections.unmodifiableSet(targets.stream().map(Supplier::get).collect(Collectors.toSet()));
	}

	public boolean inNestedSystem() {
		return nestHost != null || !nestMates.isEmpty();
	}

	public boolean isNestHost() {
		return inNestedSystem() && nestHost == null;
	}

	public String getNestHost() {
		return nestHost;
	}

	public Set<String> getNestMates() {
		return Collections.unmodifiableSet(nestMates);
	}

	public void reset() {
		name = null;
		isMixin = false;
		targets.clear();
		nestHost = null;
		nestMates.clear();
	}
}