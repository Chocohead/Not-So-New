package com.chocohead.nsn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.MixinEnvironment.Option;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.refmap.IClassReferenceMapper;
import org.spongepowered.asm.mixin.refmap.IReferenceMapper;
import org.spongepowered.asm.mixin.refmap.ReferenceMapper;
import org.spongepowered.asm.mixin.refmap.RemappingReferenceMapper;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;

import com.chocohead.nsn.util.Fields;

public class MixinChecker extends ClassVisitor {
	private static class References {
		final List<Type> usedTypes;
		final List<String> usedMethods;

		References(Set<Type> usedTypes, Set<String> usedMethods) {
			this.usedTypes = copy(usedTypes);
			this.usedMethods = copy(usedMethods);
		}

		private static <T> List<T> copy(Set<T> set) {
			switch (set.size()) {
			case 0:
				return Collections.emptyList();

			case 1:
				return Collections.singletonList(set.iterator().next());

			default:
				return new ArrayList<>(set);
			}
		}
	}
	private static final String TARGET = Type.getDescriptor(Mixin.class);
	private static final String PLUGIN = Type.getInternalName(IMixinConfigPlugin.class);
	private static final String EXTENSION = Type.getInternalName(IExtension.class);
	private final AnnotationVisitor mixinVisitor = new AnnotationVisitor(api) {
		private final Map<IMixinConfig, IReferenceMapper> remappers = new IdentityHashMap<>();

		@Override
		public AnnotationVisitor visitArray(String name) {
			return "value".equals(name) || "targets".equals(name) ? this : null;
		}

		@Override
		public void visit(String name, Object value) {
			if (name == null) {
				if (value instanceof String) {
					String mixinName = MixinChecker.this.name;
					targets.add(() -> {
						String binaryName = mixinName.replace('/', '.');
						String target = (String) value;

						List<IMixinConfig> configs = Mixins.getConfigs().stream().map(Config::getConfig)
																			.filter(config -> binaryName.startsWith(config.getMixinPackage())).collect(Collectors.toList());
						if (configs.isEmpty()) {
							throw new UnsupportedOperationException("Unable to find Mixin config for " + mixinName + " targetting " + target.replace('.', '/'));
						}

						for (IMixinConfig config : configs) {
							IReferenceMapper remapper = remappers.computeIfAbsent(config, k -> {
								String refmap;
								try {
									refmap = (String) Fields.readDeclared(k, "refMapperConfig");
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
								remap = ((IClassReferenceMapper) remapper).remapClassName(mixinName, target);
							} else {
								remap = remapper.remap(mixinName, target);
							}

							//System.out.println(config.getName() + " remapped " + target + " to " + remap + " for " + MixinChecker.this.name);
							if (!target.equals(remap)) return remap.replace('.', '/');
						}

						return target.replace('.', '/'); //Nothing says a string target has to change
					});
				} else if (value instanceof Type) {
					targets.add(() -> ((Type) value).getInternalName());
				} else {
					System.out.println("Unexpected array type: " + value + " in " + MixinChecker.this.name);
				}
			}
		}
	};
	private final MethodVisitor pluginVisitor = new TypeSpectator() {
		private final Set<Type> seenTypes = new HashSet<>();
		private final Set<String> usedMethods = new HashSet<>();

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

			if (MixinChecker.this.name.equals(owner)) usedMethods.add(name.concat(descriptor));
		}

		@Override
		protected void visitType(Type type) {
			if (type.getSort() != Type.OBJECT) throw new IllegalArgumentException("Raw non-object type " + type + " in " + name);

			//Java types we can't transform no matter what so no point keeping those 
			if (!type.getInternalName().startsWith("java/")) seenTypes.add(type);
		}

		@Override
		public void visitEnd() {
			methodReferences.put(visitedMethod, new References(seenTypes, usedMethods));
			seenTypes.clear();
			usedMethods.clear();
			visitedMethod = null;
		}
	};
	private String name;
	private final List<Supplier<String>> targets = new ArrayList<>();
	private boolean isMixin, isPlugin, isExtension;
	private String nestHost;
	private final Set<String> nestMates = new HashSet<>();
	private String visitedMethod;
	private final Map<String, References> methodReferences = new HashMap<>(8);
	private final Set<Type> pluginTypes = new HashSet<>();

	public MixinChecker() {
		super(Opcodes.ASM9);
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.name = name;

		for (String interfaceName : interfaces) {
			if (PLUGIN.equals(interfaceName)) {
				isPlugin = true;
				if (isExtension) break;
			} else if (EXTENSION.equals(interfaceName)) {
				isExtension = true;
				if (isPlugin) break;
			}
		}
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

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if (!isPlugin) return null;
		visitedMethod = name.concat(descriptor);
		return pluginVisitor;
	}

	@Override
	public void visitEnd() {
		if (!isPlugin) return;
		Queue<String> usedMethods = new ArrayDeque<>();
		usedMethods.add("preApply(Ljava/lang/String;Lorg/objectweb/asm/tree/ClassNode;Ljava/lang/String;Lorg/spongepowered/asm/mixin/extensibility/IMixinInfo;)V");
		usedMethods.add("postApply(Ljava/lang/String;Lorg/objectweb/asm/tree/ClassNode;Ljava/lang/String;Lorg/spongepowered/asm/mixin/extensibility/IMixinInfo;)V");
		if (isExtension) {
			usedMethods.add("preApply(Lorg/spongepowered/asm/mixin/transformer/ext/ITargetClassContext;)V");
			usedMethods.add("postApply(Lorg/spongepowered/asm/mixin/transformer/ext/ITargetClassContext;)V");
			usedMethods.add("export(Lorg/spongepowered/asm/mixin/MixinEnvironment;Ljava/lang/String;ZLorg/objectweb/asm/tree/ClassNode;)V");
		}

		String method;
		for (Set<String> checkedMethods = new HashSet<>(); (method = usedMethods.poll()) != null && checkedMethods.add(method);) {
			References references = methodReferences.get(method);
			pluginTypes.addAll(references.usedTypes);
			usedMethods.addAll(references.usedMethods);
		}
	}

	public boolean isMixin() {
		return isMixin;
	}

	public Set<String> getTargets() {
		return Collections.unmodifiableSet(targets.stream().map(Supplier::get).collect(Collectors.toSet()));
	}

	Collection<Supplier<String>> getLazyTargets() {
		return Collections.unmodifiableCollection(targets);
	}

	public boolean isMixinPlugin() {
		return isPlugin;
	}

	public Set<String> getPluginClasses() {
		return Collections.unmodifiableSet(pluginTypes.stream().map(Type::getInternalName).collect(Collectors.toSet()));
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
		isPlugin = false;
		targets.clear();
		nestHost = null;
		nestMates.clear();
		isExtension = false;
		methodReferences.clear();
		pluginTypes.clear();
	}
}