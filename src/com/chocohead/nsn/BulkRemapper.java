package com.chocohead.nsn;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.google.common.base.Verify;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.io.MoreFiles;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;

import com.chocohead.mm.api.ClassTinkerers;

public class BulkRemapper implements IMixinConfigPlugin {
	static final SetMultimap<String, String> HUMBLE_INTERFACES = HashMultimap.create(64, 4);

	@Override
	public void onLoad(String mixinPackage) {
		Persuasion.flip(); //We've done the persuading now
		StickyTape.tape();

		Set<String> toTransform = new HashSet<>(4096);
		SetMultimap<String, ClassReader> nests = Multimaps.newSetMultimap(new HashMap<>(128), ReferenceOpenHashSet::new);
		@SuppressWarnings("resource") //So long as we're careful we're not leaking anything
		RecyclableDataInputStream buffer = new RecyclableDataInputStream();

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			ModMetadata metadata = mod.getMetadata();

			if (!"fabricloader".equals(metadata.getId()) && !"java".equals(metadata.getId()) && !"nsn".equals(metadata.getId())) {
				try {
					Files.walkFileTree(mod.getRootPath(), new FileVisitor<Path>() {
						private final UnaryOperator<String> remapper = "minecraft".equals(metadata.getId()) ? new UnaryOperator<String>() {
							private final MappingResolver remapper = FabricLoader.getInstance().getMappingResolver();

							@Override
							public String apply(String clazz) {
								return remapper.mapClassName("official", clazz.replace('/', '.'));
							}
						} : UnaryOperator.identity();
						private final MixinChecker checker = new MixinChecker();

						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
							if ("class".equalsIgnoreCase(MoreFiles.getFileExtension(file))) {
								try (DataInputStream in = buffer.open(Files.newInputStream(file))) {
									in.mark(16);

								    int magic = in.readInt();
								    if (magic != 0xCAFEBABE) {
								    	System.err.printf("Expected magic in %s but got %X%n", file, magic);
								    	return FileVisitResult.CONTINUE; //Not a class?
								    }

								    in.readUnsignedShort(); //Minor version
								    if (in.readUnsignedShort() > Opcodes.V1_8) {
								    	in.reset();

								    	ClassReader reader = new ClassReader(in); 
								    	String name = reader.getClassName();
								    	//System.out.println("Planning to transform ".concat(name));
								    	reader.accept(checker, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

								    	if (!checker.isMixin()) {
								    		toTransform.add(remapper.apply(name));

								    		if (name.equals(remapper.apply(name)) && checker.inNestedSystem()) {
									    		nests.put(checker.isNestHost() ? name : checker.getNestHost(), reader);
									    	}								    		
								    	} else if (Modifier.isInterface(reader.getAccess())) {
								    		for (String target : checker.getTargets()) {
								    			HUMBLE_INTERFACES.put(target, name);
								    		}
								    	}

								    	checker.reset();
								    }// else System.out.printf("Not transforming %s as its version is %d%n", MoreFiles.getNameWithoutExtension(file), version);
								} catch (IOException e) {
									System.err.println("Broke visiting " + file); //Oops
									e.printStackTrace();
								}
							}

							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFileFailed(Path file, IOException e) {
							System.err.println("Broke trying to visit " + file); //Oops?
							e.printStackTrace();
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult postVisitDirectory(Path dir, IOException e) {
							if (e != null) {//Oops?
								System.err.println("Broke having visited " + dir);
								e.printStackTrace();
							};
							return FileVisitResult.CONTINUE;
						}
					});
				} catch (IOException e) {//This can only be thrown from the file visitor doing so
					throw new AssertionError("Unexpected exception", e);
				}
			}
		}

		assert !toTransform.isEmpty();
		mixinPackage = mixinPackage.replace('.', '/');
		generateMixin(mixinPackage.concat("SuperMixin"), toTransform);
		generateMixin(mixinPackage.concat("InterfaceMixin"), HUMBLE_INTERFACES.keySet());

		if (!nests.isEmpty()) {
			try {
				resolveNestSystem(Collections.singleton(new ClassReader(Object.class.getName())));
			} catch (IOException e) {
				//Only warming up for class loading
			}
			List<CompletableFuture<List<Runnable>>> tasks = new ArrayList<>(nests.asMap().size());

			for (Collection<ClassReader> system : nests.asMap().values()) {
				System.out.println(system.stream().map(ClassReader::getClassName).collect(Collectors.joining(", ", "Analysing: [", "]")));
				tasks.add(CompletableFuture.supplyAsync(() -> resolveNestSystem(system)));
			}

			for (CompletableFuture<List<Runnable>> future : tasks) {
				for (Runnable task : future.join()) {
					task.run(); //Avoid causing CMEs trying to transform multiple classes at the same time
				}
			}
		}

		try {
			Extensions extensions = StickyTape.grabTransformer(Extensions.class, "extensions");

			extensions.add(new Extension(mixinPackage));
		} catch (ReflectiveOperationException | ClassCastException e) {
			throw new IllegalStateException("Running with a transformer that doesn't have extensions?", e);
		}
	}

	private static void generateMixin(String name, Iterable<String> targets) {
		ClassWriter cw = new ClassWriter(0);
		cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE, name, null, "java/lang/Object", null);

		AnnotationVisitor mixinAnnotation = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false);
		AnnotationVisitor targetAnnotation = mixinAnnotation.visitArray("value");
		for (String target : targets) targetAnnotation.visit(null, Type.getType('L' + target + ';'));
		targetAnnotation.visitEnd();
		mixinAnnotation.visitEnd();

		cw.visitEnd();
		ClassTinkerers.define(name, cw.toByteArray());
	}

	private static List<Runnable> resolveNestSystem(Collection<ClassReader> system) {
		class Member {
			public final String name;
			public final Set<String> members;
			public final Set<String> wantedMethods = new HashSet<>();
			public final Set<String> wantedFields = new HashSet<>();
			public final Map<String, Set<String>> usedMethods;
			public final Map<String, Set<String>> usedFields;

			Member(String name, Set<String> members, Map<String, Set<String>> usedMethods, Map<String, Set<String>> usedFields) {
				this.name = name;
				this.members = Collections.unmodifiableSet(members);
				this.usedMethods = Collections.unmodifiableMap(usedMethods);
				this.usedFields = Collections.unmodifiableMap(usedFields);
			}
		}
		Map<String, Member> ownerToMembers = new HashMap<>(system.size());

		for (ClassReader reader : system) {
			Set<String> members = new HashSet<>();
			Map<String, Set<String>> usedMethods = new HashMap<>();
			Map<String, Set<String>> usedFields = new HashMap<>();

			reader.accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
					if (Modifier.isPrivate(access)) members.add(name + '#' + descriptor);

					return null;
				}

				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if (Modifier.isPrivate(access)) members.add(name.concat(descriptor));

					return new MethodVisitor(api) {
						@Override
						public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
							usedFields.computeIfAbsent(owner, k -> new HashSet<>()).add(name + '#' + descriptor);
						}

						@Override
						public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
							usedMethods.computeIfAbsent(owner, k -> new HashSet<>()).add(name.concat(descriptor));
						}

						@Override
						public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
							//Maybe
						}
					};
				}
			}, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

			String name = reader.getClassName();
			ownerToMembers.put(name, new Member(name, members, usedMethods, usedFields));
		}

		for (Member member : ownerToMembers.values()) {
			for (Entry<String, Set<String>> entry : member.usedFields.entrySet()) {
				Member owner = ownerToMembers.get(entry.getKey());

				if (owner != null) {
					for (String field : entry.getValue()) {
						if (owner.members.contains(field)) {
							owner.wantedFields.add(field);
						}
					}
				}
			}
			for (Entry<String, Set<String>> entry : member.usedMethods.entrySet()) {
				Member owner = ownerToMembers.get(entry.getKey());

				if (owner != null) {
					for (String method : entry.getValue()) {
						if (owner.members.contains(method)) {
							owner.wantedMethods.add(method);
						}
					}
				}
			}
		}

		List<Runnable> tasks = new ArrayList<>(ownerToMembers.size());
		for (Member member : ownerToMembers.values()) {
			Set<String> neededMethods = member.wantedMethods;
			Set<String> neededFields = member.wantedFields;
			if (neededFields.isEmpty() && neededMethods.isEmpty()) continue;

			tasks.add(() -> {
				ClassTinkerers.addTransformation(member.name, node -> {
					if (!neededMethods.isEmpty()) {
						Map<String, MethodNode> methodNodes = node.methods.stream().collect(Collectors.toMap(method -> method.name.concat(method.desc), Function.identity()));

						for (String method : neededMethods) {
							MethodNode m = methodNodes.get(method);
							m.access = (m.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
						}
					}

					if (!neededFields.isEmpty()) {
						Map<String, FieldNode> fieldNodes = node.fields.stream().collect(Collectors.toMap(field -> field.name + '#' + field.desc, Function.identity()));

						for (String field : neededFields) {
							FieldNode f = fieldNodes.get(field);
							f.access = (f.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
						}
					}							
				});
			});
		}

		return tasks;
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return ImmutableList.of("SuperMixin", "InterfaceMixin");
	}

	@Override
	public void preApply(String targetClassName, ClassNode node, String mixinClassName, IMixinInfo mixinInfo) {
		if (mixinClassName.endsWith(".InterfaceMixin") && HUMBLE_INTERFACES.containsKey(node.name)) {
			MethodNode method = new MethodNode(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "k££makeSomeMagic££", "()V", null, null);
			method.instructions.add(new InsnNode(Opcodes.RETURN));
			node.methods.add(method);
		}
	}

	@Override
	public void postApply(String targetClassName, ClassNode node, String mixinClassName, IMixinInfo mixinInfo) {
		node.interfaces.remove(mixinClassName);
		if (mixinClassName.endsWith(".SuperMixin")) transform(node);
	}

	static void transform(ClassNode node) {
		node.version = Opcodes.V1_8;

		if ((node.access & Opcodes.ACC_RECORD) != 0) {
			node.access &= ~Opcodes.ACC_RECORD;
			node.superName = "java/lang/Object"; //Record only defines some abstract methods
		}

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

					case "java/lang/runtime/ObjectMethods": {
						InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) insn;

						if ("bootstrap".equals(bootstrap.getName())) {
							Handle[] fields = Arrays.copyOfRange(idin.bsmArgs, 2, idin.bsmArgs.length, Handle[].class);

							if (idin.bsmArgs[1] != null) {//Is allowed to be null when idin.name is equals or hashCode
								String template = (String) idin.bsmArgs[1];

								if (template.isEmpty()) {
									Verify.verify(fields.length == 0, "Expected no getters but received %s", Arrays.toString(fields));
								} else {
									String[] names = Arrays.stream(fields).map(Handle::getName).toArray(String[]::new);
									Verify.verify(Arrays.equals(template.split(";"), names), "Expected %s == %s", template, Arrays.toString(names));
								}
							}

							MethodNode implementation;
							switch (idin.name) {
							case "equals":
								implementation = Recordy.makeEquals(node.name, fields);
								break;

							case "hashCode":
								implementation = Recordy.makeHashCode(node.name, fields);
								break;

							case "toString":
								implementation = Recordy.makeToString(node.name, fields);
								break;

							default:
								throw new IllegalArgumentException("Unexpected object method name: " + idin.name);
							}

							//System.out.println("Transforming " + idin.name + idin.desc + " to " + concat.name + concat.desc);
							it.set(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, implementation.name, implementation.desc));
							extraMethods.add(implementation);
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

					case "java/util/Optional": {
						if ("isEmpty".equals(min.name) && "()Z".equals(min.desc)) {
							min.name = "isPresent";
							LabelNode present = new LabelNode();
							it.add(new JumpInsnNode(Opcodes.IFEQ, present));
							it.add(new InsnNode(Opcodes.ICONST_0));
							LabelNode next = new LabelNode();
							it.add(new JumpInsnNode(Opcodes.GOTO, next));
							it.add(present);
							it.add(new InsnNode(Opcodes.ICONST_1));
							it.add(next);
						}
						break;
					}

					case "java/util/List": {
						switch (min.name.concat(min.desc)) {
						case "of()Ljava/util/List;":
							min.owner = "java/util/Collections";
							min.name = "emptyList";
							break;

						case "of(Ljava/lang/Object;)Ljava/util/List;":
							min.owner = "java/util/Collections";
							min.name = "singletonList";
							break;

						case "of([Ljava/lang/Object;)Ljava/util/List;":
							min.name = "copyOf";
						case "of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;":
						case "copyOf(Ljava/util/Collection;)Ljava/util/List;":
							min.owner = "com/google/common/collect/ImmutableList";
							min.desc = min.desc.substring(0, min.desc.length() - 15).concat("com/google/common/collect/ImmutableList;");
							break;
						}
						break;
					}

					case "java/lang/String": {
						if ("repeat".equals(min.name) && "(I)Ljava/lang/String;".equals(min.desc)) {
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/google/common/base/Strings"; //Guava's implementation is closer than Apache's StringUtils
							min.desc = "(Ljava/lang/String;I)Ljava/lang/String;";
						}
						break;
					}

					case "java/lang/Record": {
						min.owner = "java/lang/Object";
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
}