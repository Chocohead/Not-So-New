package com.chocohead.nsn;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.reflect.FieldUtils;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.MixinEnvironment.Option;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.refmap.IClassReferenceMapper;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.refmap.ReferenceMapper;
import org.spongepowered.asm.mixin.refmap.RemappingReferenceMapper;
import org.spongepowered.asm.mixin.transformer.Config;

public class MixinChecker extends ClassVisitor {
	private static final String TARGET = Type.getDescriptor(Mixin.class);
	private final Map<IMixinConfig, IReferenceMapper> remappers = new IdentityHashMap<>();
	private final AnnotationVisitor mixinVisitor = new AnnotationVisitor(api) {
		@Override
		public AnnotationVisitor visitArray(String name) {
			return "value".equals(name) || "targets".equals(name) ? this : null;
		}

		@Override
		public void visit(String name, Object value) {
			if (name == null) {
				if (value instanceof String) {
					targets.add(() -> {
						String binaryName = MixinChecker.this.name.replace('/', '.');
						String target = (String) value;

						List<IMixinConfig> configs = Mixins.getConfigs().stream().map(Config::getConfig)
																			.filter(config -> binaryName.startsWith(config.getMixinPackage())).collect(Collectors.toList());
						if (configs.isEmpty()) {
							throw new UnsupportedOperationException("Unable to find Mixin config for " + MixinChecker.this.name + " targetting " + target.replace('.', '/'));
						}

						for (IMixinConfig config : configs) {
							IReferenceMapper remapper = remappers.computeIfAbsent(config, k -> {
								String refmap;
								try {
									refmap = (String) FieldUtils.readDeclaredField(k, "refMapperConfig", true);
								} catch (ReflectiveOperationException | ClassCastException e) {
									throw new RuntimeException("Error getting refmap for " + k.getName(), e);
								}

								IReferenceMapper out = refmap != null ? ReferenceMapper.read(refmap) : ReferenceMapper.DEFAULT_MAPPER;
								if (MixinEnvironment.getDefaultEnvironment().getOption(Option.REFMAP_REMAP)) {
						            out = RemappingReferenceMapper.of(MixinEnvironment.getDefaultEnvironment(), out);
						        }

								return out;
							});

							String remap;
							if (remapper instanceof IClassReferenceMapper) {
					            remap = ((IClassReferenceMapper) remapper).remapClassName(MixinChecker.this.name, target);
					        } else {
					            remap = remapper.remap(MixinChecker.this.name, target);
					        }

							//System.out.println(config.getName() + " remapped " + target + " to " + remap + " for " + MixinChecker.this.name);
							if (!target.equals(remap)) return remap.replace('.', '/');
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