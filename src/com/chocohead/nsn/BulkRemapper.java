package com.chocohead.nsn;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableList;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.util.Bytecode;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;

import com.chocohead.mm.api.ClassTinkerers;
import com.chocohead.nsn.Nester.ScanResult;

public class BulkRemapper implements IMixinConfigPlugin {
	static final Map<String, Set<String>> HUMBLE_INTERFACES = new HashMap<>(64);
	private static final Map<String, List<Consumer<ClassNode>>> EXTRA_ACCESS = new HashMap<>(4);
	static ScanResult toTransform = Nester.run();

	@Override
	public void onLoad(String mixinPackage) {
		Persuasion.flip(); //We've done the persuading now
		StickyTape.tape();

		for (Entry<String, List<Supplier<String>>> entry : toTransform.getInterfaceTargets().entrySet()) {
			for (Supplier<String> target : entry.getValue()) {
				HUMBLE_INTERFACES.computeIfAbsent(target.get(), k -> new HashSet<>(4)).add(entry.getKey());
			}
		}
		for (Entry<List<Supplier<String>>, Consumer<ClassNode>> entry : toTransform.getMixinNestTransforms()) {
			Consumer<ClassNode> transformer = entry.getValue();
			for (Supplier<String> target : entry.getKey()) {
				EXTRA_ACCESS.computeIfAbsent(target.get(), k -> new ArrayList<>(1)).add(transformer);
			}
		}

		mixinPackage = mixinPackage.replace('.', '/');
		generateMixin(mixinPackage.concat("SuperMixin"), toTransform.getMixinTargets());
		generateMixin(mixinPackage.concat("InterfaceMixin"), HUMBLE_INTERFACES.keySet());
		generateMixin(mixinPackage.concat("MixinAccessMixin"), EXTRA_ACCESS.keySet());

		for (Entry<String, Consumer<ClassNode>> entry : toTransform.getNestTransforms().entrySet()) {
			ClassTinkerers.addTransformation(entry.getKey(), entry.getValue());
		}
		ClassTinkerers.addTransformation(FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", "net.minecraft.class_6611"), new Consumer<ClassNode>() {
			private void assertMethod(String owner, String name, String desc, AbstractInsnNode insn) {
				if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
					MethodInsnNode min = (MethodInsnNode) insn;

					if (owner.equals(min.owner) && name.equals(min.name) && desc.equals(min.desc)) {
						return;
					}
				}

				throw new IllegalStateException("Expected " + owner + '#' + name + desc + " call but found " + Bytecode.describeNode(insn, false));
			}

			@Override
			public void accept(ClassNode node) {
				for (MethodNode method : node.methods) {
					if ("<clinit>".equals(method.name)) {
						int stage = -1;

						out: for (ListIterator<AbstractInsnNode> it = method.instructions.iterator(); it.hasNext();) {
							AbstractInsnNode insn = it.next();

							switch (stage) {
							case -1:
								if (insn.getType() == AbstractInsnNode.LDC_INSN) {
									LdcInsnNode lin = (LdcInsnNode) insn;

									if (lin.cst instanceof Type && "Ljava/lang/Runtime;".equals(((Type) lin.cst).getDescriptor())) {
										stage = 1;
										it.remove();
									}
								}
								break;

							case 1:
								assertMethod("java/lang/Class", "getModule", "()Ljava/lang/Module;", insn);
								stage++;
								it.remove();
								break;

							case 2:
								assertMethod("java/lang/Module", "getLayer", "()Ljava/lang/ModuleLayer;", insn);
								stage++;
								it.remove();
								break;

							case 3:
								if (insn.getType() == AbstractInsnNode.LDC_INSN) {
									LdcInsnNode lin = (LdcInsnNode) insn;

									if ("jdk.jfr".equals(lin.cst)) {
										stage++;
										it.remove();
										continue;
									}
								}
								throw new IllegalStateException("Expected jdk.jfr load but found " + Bytecode.describeNode(insn, false));

							case 4:
								assertMethod("java/lang/ModuleLayer", "findModule", "(Ljava/lang/String;)Ljava/util/Optional;", insn);
								stage++;
								it.remove();
								break;

							case 5:
								assertMethod("java/util/Optional", "isPresent", "()Z", insn);
								stage++;
								it.set(new InsnNode(Opcodes.ICONST_0));
								break out;
							}
						}
						if (stage != 6) throw new IllegalStateException("Failed to find injection: " + stage);
						break;
					}
				}
			}
		});
		ClassTinkerers.addTransformation(FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", "net.minecraft.class_7668"), node -> {
			MethodVisitor method = node.visitMethod(Opcodes.ACC_PUBLIC, "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;", null, null);
			method.visitCode();
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "getFileSystem", "()Ljava/nio/file/FileSystem;", true);
			method.visitVarInsn(Opcodes.ALOAD, 1);
			method.visitInsn(Opcodes.ICONST_0);
			method.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
			method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/nio/file/FileSystem", "getPath", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;", false);
			method.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/nio/file/Path", "resolve", "(Ljava/nio/file/Path;)Ljava/nio/file/Path;", true);
			method.visitInsn(Opcodes.ARETURN);
			method.visitMaxs(4, 2);
			method.visitEnd();

			method = node.visitMethod(Opcodes.ACC_PUBLIC, "iterator", "()Ljava/util/Iterator;", "()Ljava/util/Iterator<Ljava/nio/file/Path;>;", null);
			method.visitCode();
			method.visitTypeInsn(Opcodes.NEW, "com/chocohead/nsn/PathIterator");
			method.visitInsn(Opcodes.DUP);
			method.visitVarInsn(Opcodes.ALOAD, 0);
			method.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/chocohead/nsn/PathIterator", "<init>", "(Ljava/nio/file/Path;)V", false);
			method.visitInsn(Opcodes.ARETURN);
			method.visitMaxs(2, 2);
			method.visitEnd();
		});

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
		for (String target : targets) targetAnnotation.visit(null, Type.getObjectType(target));
		targetAnnotation.visitEnd();
		mixinAnnotation.visitEnd();

		cw.visitEnd();
		ClassTinkerers.define(name, cw.toByteArray());
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return !"com.chocohead.nsn.mixins.MainMixin".equals(mixinClassName) || !FabricLoader.getInstance().getModContainer("minecraft").filter(mod -> {
			try {
				return SemanticVersion.parse("1.19.1").compareTo(mod.getMetadata().getVersion()) <= 0;
			} catch (VersionParsingException e) {
				throw new IllegalStateException("Failed to create valid SemVer?", e);
			}
		}).isPresent();
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return ImmutableList.of("SuperMixin", "InterfaceMixin", "MixinAccessMixin");
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
		else if (mixinClassName.endsWith("MixinAccessMixin")) {
			for (Consumer<ClassNode> transformer : EXTRA_ACCESS.get(targetClassName.replace('.', '/'))) {
				transformer.accept(node);
			}
		}
	}

	static void transform(ClassNode node) {
		node.version = Opcodes.V1_8;

		if ("java/lang/Record".equals(node.superName)) {
			node.access &= ~Opcodes.ACC_RECORD;
			node.superName = "java/lang/Object"; //Record only defines some abstract methods
		}
		boolean isInterface = Modifier.isInterface(node.access);

		Set<String> privateMethods;
		if (isInterface) {
			privateMethods = new HashSet<>();

			for (MethodNode method : node.methods) {
				if (Modifier.isPrivate(method.access)) {
					privateMethods.add(method.name.concat(method.desc));
				}
			}
		} else {
			privateMethods = Collections.emptySet();
		}

		List<MethodNode> extraMethods = new ArrayList<>();
		for (MethodNode method : node.methods) {
			if (method.desc.contains("Ljava/lang/Record;")) {
				method.desc = method.desc.replace("Ljava/lang/Record;", "Ljava/lang/Object;");
			}
			if (method.desc.contains("Ljava/util/ServiceLoader$Provider;")) {
				method.desc = method.desc.replace("Ljava/util/ServiceLoader$Provider;", "Lcom/chocohead/nsn/ServiceLoaders$Provider;");
			}

			for (ListIterator<AbstractInsnNode> it = method.instructions.iterator(); it.hasNext();) {
				AbstractInsnNode insn = it.next();

				switch (insn.getType()) {
				case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
					InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) insn;

					switch (idin.bsm.getOwner()) {
					case "java/lang/invoke/StringConcatFactory": {
						switch (idin.bsm.getName()) {
						case "makeConcat": {
							MethodNode concat = Stringy.makeConcat(Type.getArgumentTypes(idin.desc));
							//System.out.println("Transforming " + idin.name + idin.desc + " to " + concat.name + concat.desc);
							it.set(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, concat.name, concat.desc, isInterface));
							extraMethods.add(concat);
							break;
						}

						case "makeConcatWithConstants": {
							MethodNode concat = Stringy.makeConcat((String) idin.bsmArgs[0], Type.getArgumentTypes(idin.desc), Arrays.copyOfRange(idin.bsmArgs, 1, idin.bsmArgs.length));
							//System.out.println("Transforming " + idin.name + idin.desc + " to " + concat.name + concat.desc);
							it.set(new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, concat.name, concat.desc, isInterface));
							extraMethods.add(concat);
							break;
						}
						}
						break;
					}

					case "java/lang/invoke/LambdaMetafactory": {
						if (!privateMethods.isEmpty() && idin.bsmArgs[1] instanceof Handle) {
							Handle lambda = (Handle) idin.bsmArgs[1];

							if (node.name.equals(lambda.getOwner()) && lambda.getTag() == Opcodes.H_INVOKEINTERFACE) {
								if (privateMethods.contains(lambda.getName().concat(lambda.getDesc()))) {
									idin.bsmArgs[1] = new Handle(Opcodes.H_INVOKESPECIAL, lambda.getOwner(), lambda.getName(), lambda.getDesc(), lambda.isInterface());
								}
							}
						}
						break;
					}

					case "java/lang/runtime/ObjectMethods": {
						if ("bootstrap".equals(idin.bsm.getName())) {
							Handle[] fields = Arrays.copyOfRange(idin.bsmArgs, 2, idin.bsmArgs.length, Handle[].class);

							if (idin.bsmArgs[1] != null) {//Is allowed to be null when idin.name is equals or hashCode
								String template = (String) idin.bsmArgs[1];

								if (template.isEmpty()) {
									if (fields.length != 0) throw new AssertionError(String.format("Expected no getters but received %s", Arrays.toString(fields)));
								} else if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
									String[] names = Arrays.stream(fields).map(Handle::getName).toArray(String[]::new);
									if (!Arrays.equals(template.split(";"), names)) throw new AssertionError(String.format("Expected %s == %s", template, Arrays.toString(names)));
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
							if (isInterface) throw new AssertionError(String.format("%s has instance method %s generated but is an interface?", node.name, idin.name));
							it.set(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, implementation.name, implementation.desc, false));
							extraMethods.add(implementation);
						}
						break;
					}
					}

					for (int i = 0, end = idin.bsmArgs.length; i < end; i++) {
						if (idin.bsmArgs[i] instanceof Handle) {
							Handle handle = (Handle) idin.bsmArgs[i];

							if (handle.getDesc().contains("Ljava/lang/Record;") || handle.getDesc().contains("Ljava/util/ServiceLoader$Provider;")) {
								idin.bsmArgs[i] = handle = new Handle(handle.getTag(), handle.getOwner(), handle.getName(),
										handle.getDesc().replace("Ljava/lang/Record;", "Ljava/lang/Object;").replace("Ljava/util/ServiceLoader$Provider;", "Lcom/chocohead/nsn/ServiceLoaders$Provider;"), handle.isInterface());
							}

							switch (handle.getOwner()) {
							case "java/util/Optional": {
								switch (handle.getName().concat(handle.getDesc())) {
								case "ifPresentOrElse(Ljava/util/function/Consumer;Ljava/lang/Runnable;)V":
								case "or(Ljava/util/function/Supplier;)Ljava/util/Optional;":
								case "stream()Ljava/util/stream/Stream;":
									idin.bsmArgs[i] = new Handle(Opcodes.H_INVOKESTATIC, "com/chocohead/nsn/Optionals", handle.getName(), "(Ljava/util/Optional;".concat(handle.getDesc().substring(1)), false);
									break;

								case "orElseThrow()Ljava/lang/Object;":
									idin.bsmArgs[i] = new Handle(handle.getTag(), handle.getOwner(), "get", handle.getDesc(), handle.isInterface());
									break;
								}
								break;
							}

							case "java/util/List": {
								switch (handle.getName().concat(handle.getDesc())) {
								case "of()Ljava/util/List;":
									idin.bsmArgs[i] = new Handle(Opcodes.H_INVOKESTATIC, "java/util/Collections", "emptyList", handle.getDesc(), false);
									break;

								case "of(Ljava/lang/Object;)Ljava/util/List;":
									idin.bsmArgs[i] = new Handle(Opcodes.H_INVOKESTATIC, "java/util/Collections", "singletonList", handle.getDesc(), false);
									break;

								case "of([Ljava/lang/Object;)Ljava/util/List;":
									handle = new Handle(handle.getTag(), handle.getOwner(), "copyOf", handle.getDesc(), handle.isInterface());
								case "copyOf(Ljava/util/Collection;)Ljava/util/List;":
									idin.bsmArgs[i] = new Handle(Opcodes.H_INVOKESTATIC, "com/google/common/collect/ImmutableList", handle.getName(),
											handle.getDesc().substring(0, handle.getDesc().length() - 15).concat("com/google/common/collect/ImmutableList;"), false);
									break;
								}
								break;
							}

							case "java/util/Set": {
								switch (handle.getName().concat(handle.getDesc())) {
								case "of()Ljava/util/Set;":
									idin.bsmArgs[i] = new Handle(Opcodes.H_INVOKESTATIC, "java/util/Collections", "emptySet", handle.getDesc(), false);
									break;
								}
								break;
							}

							case "java/lang/Character": {
								switch (handle.getName().concat(handle.getDesc())) {
								case "toString(I)Ljava/lang/String;":
									idin.bsmArgs[i] = new Handle(Opcodes.H_INVOKESTATIC, "com/chocohead/nsn/Characters", handle.getName(), handle.getDesc(), false);
									break;
								}
								break;
							}
							}
						} else if (idin.bsmArgs[i] instanceof Type) {
							Type type = (Type) idin.bsmArgs[i];

							switch (type.getSort()) {
							case Type.OBJECT:
							case Type.ARRAY:
								if (type.getDescriptor().contains("Ljava/lang/Record;") || type.getDescriptor().contains("Ljava/util/ServiceLoader$Provider;")) {
									idin.bsmArgs[i] = Type.getType(type.getDescriptor().replace("Ljava/lang/Record;", "Ljava/lang/Object;").replace("Ljava/util/ServiceLoader$Provider;", "Lcom/chocohead/nsn/ServiceLoaders$Provider;"));
								}
								break;

							case Type.METHOD: {//Normally would only expect this
								Type[] args = type.getArgumentTypes();
								Type returnType = type.getReturnType();
								boolean madeChange = false;

								for (int j = 0; j < args.length; j++) {
									String desc = args[j].getDescriptor();
									if (desc.contains("Ljava/lang/Record;") || desc.contains("Ljava/util/ServiceLoader$Provider;")) {
										args[j] = Type.getType(desc.replace("Ljava/lang/Record;", "Ljava/lang/Object;").replace("Ljava/util/ServiceLoader$Provider;", "Lcom/chocohead/nsn/ServiceLoaders$Provider;"));
										madeChange = true;
									}
								}
								if (returnType.getDescriptor().contains("Ljava/lang/Record;") || returnType.getDescriptor().contains("Ljava/util/ServiceLoader$Provider;")) {
									returnType = Type.getType(returnType.getDescriptor().replace("Ljava/lang/Record;", "Ljava/lang/Object;").replace("Ljava/util/ServiceLoader$Provider;", "Lcom/chocohead/nsn/ServiceLoaders$Provider;"));
									madeChange = true;
								}

								if (madeChange) idin.bsmArgs[i] = Type.getMethodType(returnType, args);
								break;
							}
							}
						}
					}
					break;
				}

				case AbstractInsnNode.METHOD_INSN: {
					MethodInsnNode min = (MethodInsnNode) insn;

					if (min.getOpcode() == Opcodes.INVOKEINTERFACE && node.name.equals(min.owner) && privateMethods.contains(min.name.concat(min.desc))) {
						min.setOpcode(Opcodes.INVOKESPECIAL);
					}

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

						case "slice()Ljava/nio/ByteBuffer;":
						case "slice(II)Ljava/nio/ByteBuffer;":
						case "duplicate()Ljava/nio/ByteBuffer;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "org/lwjgl/system/MemoryUtil";
					        min.name = prependMem(min.name);
							min.desc = "(Ljava/nio/ByteBuffer;".concat(min.desc.substring(1));
							break;

						case "put(ILjava/nio/ByteBuffer;II)Ljava/nio/ByteBuffer;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/Buffers";
							min.desc = "(Ljava/nio/ByteBuffer;".concat(min.desc.substring(1));
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

						case "slice()Ljava/nio/FloatBuffer;":
						case "slice(II)Ljava/nio/FloatBuffer;":
						case "duplicate()Ljava/nio/FloatBuffer;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "org/lwjgl/system/MemoryUtil";
					        min.name = prependMem(min.name);
							min.desc = "(Ljava/nio/FloatBuffer;".concat(min.desc.substring(1));
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

						case "slice()Ljava/nio/IntBuffer;":
						case "slice(II)Ljava/nio/IntBuffer;":
						case "duplicate()Ljava/nio/IntBuffer;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "org/lwjgl/system/MemoryUtil";
					        min.name = prependMem(min.name);
							min.desc = "(Ljava/nio/IntBuffer;".concat(min.desc.substring(1));
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
						switch (min.name.concat(min.desc)) {
						case "isEmpty()Z":
						case "ifPresentOrElse(Ljava/util/function/Consumer;Ljava/lang/Runnable;)V":
						case "or(Ljava/util/function/Supplier;)Ljava/util/Optional;":
						case "stream()Ljava/util/stream/Stream;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/Optionals";
							min.desc = "(Ljava/util/Optional;".concat(min.desc.substring(1));
							break;

						case "orElseThrow()Ljava/lang/Object;":
							min.name = "get";
							break;
						}
						break;
					}

					case "java/util/OptionalInt": {
						switch (min.name.concat(min.desc)) {
						case "isEmpty()Z":
						case "ifPresentOrElse(Ljava/util/function/IntConsumer;Ljava/lang/Runnable;)V":
						case "stream()Ljava/util/stream/IntStream;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/Optionals";
							min.desc = "(Ljava/util/OptionalInt;".concat(min.desc.substring(1));
							break;

						case "orElseThrow()I":
							min.name = "getAsInt";
							break;
						}
						break;
					}

					case "java/util/OptionalLong": {
						switch (min.name.concat(min.desc)) {
						case "isEmpty()Z":
						case "ifPresentOrElse(Ljava/util/function/LongConsumer;Ljava/lang/Runnable;)V":
						case "stream()Ljava/util/stream/LongStream;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/Optionals";
							min.desc = "(Ljava/util/OptionalLong;".concat(min.desc.substring(1));
							break;

						case "orElseThrow()J":
							min.name = "getAsLong";
							break;
						}
						break;
					}

					case "java/util/OptionalDouble": {
						switch (min.name.concat(min.desc)) {
						case "isEmpty()Z":
						case "ifPresentOrElse(Ljava/util/function/DoubleConsumer;Ljava/lang/Runnable;)V":
						case "stream()Ljava/util/stream/DoubleStream;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/Optionals";
							min.desc = "(Ljava/util/OptionalDouble;".concat(min.desc.substring(1));
							break;

						case "orElseThrow()D":
							min.name = "getAsInt";
							break;
						}
						break;
					}

					case "java/util/function/Predicate": {
						if ("not".equals(min.name) && "(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;".equals(min.desc)) {
							min.setOpcode(Opcodes.INVOKEINTERFACE);
							min.name = "negate";
							min.desc = "()Ljava/util/function/Predicate;";
						}
						break;
					}

					case "java/util/Collection": {
						if ("toArray".equals(min.name) && "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;".equals(min.desc)) {
							doToArray(it, min);
						}
						break;
					}

					case "java/util/List": {
						switch (min.name.concat(min.desc)) {
						case "of()Ljava/util/List;":
							min.owner = "java/util/Collections";
							min.name = "emptyList";
							min.itf = false;
							break;

						case "of(Ljava/lang/Object;)Ljava/util/List;":
							min.owner = "java/util/Collections";
							min.name = "singletonList";
							min.itf = false;
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
							min.itf = false;
							break;

						case "toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;":
							doToArray(it, min);
							break;
						}
						break;
					}

					case "java/util/Set": {
						switch (min.name.concat(min.desc)) {
						case "of()Ljava/util/Set;":
							min.owner = "java/util/Collections";
							min.name = "emptySet";
							min.itf = false;
							break;

						case "of(Ljava/lang/Object;)Ljava/util/Set;":
							min.owner = "java/util/Collections";
							min.name = "singleton";
							min.itf = false;
							break;

						case "of([Ljava/lang/Object;)Ljava/util/Set;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;":
							min.owner = "com/chocohead/nsn/Sets";
							min.itf = false;
							break;

						case "copyOf(Ljava/util/Collection;)Ljava/util/Set;":
							min.owner = "com/google/common/collect/ImmutableSet";
							min.desc = "(Ljava/util/Collection;)Lcom/google/common/collect/ImmutableSet;";
							min.itf = false;
							break;

						case "toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;":
							doToArray(it, min);
							break;
						}
						break;
					}

					case "com/google/common/collect/ImmutableList": {
						if ("toArray".equals(min.name) && "(Ljava/util/function/IntFunction;)[Ljava/lang/Object;".equals(min.desc)) {
							doToArray(it, min);
						}
						break;
					}

					case "java/lang/String": {
						switch (min.name.concat(min.desc)) {
						case "strip()Ljava/lang/String;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "org/apache/commons/lang3/StringUtils";
							min.desc = "(Ljava/lang/String;)Ljava/lang/String;";
							break;

						case "stripLeading()Ljava/lang/String;":
							it.previous();
							it.add(new InsnNode(Opcodes.ACONST_NULL));
							it.next();
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "org/apache/commons/lang3/StringUtils";
							min.name = "stripStart";
							min.desc = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
							break;

						case "stripTrailing()Ljava/lang/String;":
							it.previous();
							it.add(new InsnNode(Opcodes.ACONST_NULL));
							it.next();
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "org/apache/commons/lang3/StringUtils";
							min.name = "stripEnd";
							min.desc = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
							break;

						case "isBlank()Z":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "org/apache/commons/lang3/StringUtils";
							min.desc = "(Ljava/lang/CharSequence;)Z";
							break;

						case "lines()Ljava/util/stream/Stream;":
						case "indent(I)Ljava/lang/String;":
						case "stripIndent()Ljava/lang/String;":
						case "translateEscapes()Ljava/lang/String;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/Strings";
							min.desc = "(Ljava/lang/String;".concat(min.desc.substring(1));
							break;

						case "transform(Ljava/util/function/Function;)Ljava/lang/Object;":
							it.previous();
							it.add(new InsnNode(Opcodes.SWAP));
							it.next();
							min.setOpcode(Opcodes.INVOKEINTERFACE);
							min.owner = "java/util/function/Function";
							min.name = "apply";
							min.desc = "(Ljava/lang/Object;)Ljava/lang/Object;";
							min.itf = true;
							break;

						case "chars()Ljava/util/stream/IntStream;":
						case "codePoints()Ljava/util/stream/IntStream;":
							break; //Although String overrides were added in J9, CharSequence added these in J8 

						case "formatted([Ljava/lang/Object;)Ljava/lang/String;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.name = "format";
							min.desc = "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;";
							break;

						case "repeat(I)Ljava/lang/String;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/google/common/base/Strings"; //Guava's implementation is closer than Apache's StringUtils
							min.desc = "(Ljava/lang/String;I)Ljava/lang/String;";
							break;

						case "describeConstable()Ljava/util/Optional;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "java/util/Optional";
							min.name = "of";
							min.desc = "(Ljava/lang/Object;)Ljava/util/Optional;";
							break;

						case "resolveConstantDesc(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/String;":
							it.set(new InsnNode(Opcodes.POP));
							break;
						}
						break;
					}

					case "java/lang/System": {
						if ("getLogger".equals(min.name) && "(Ljava/lang/String;)Ljava/lang/System$Logger;".equals(min.desc)) {
							min.owner = "com/chocohead/nsn/SystemLogger";
							min.desc = "(Ljava/lang/String;)Lcom/chocohead/nsn/SystemLogger;";
							min.itf = true;
						}
						break;
					}

					case "java/lang/System$Logger": {
						min.owner = "com/chocohead/nsn/SystemLogger";
						min.desc = min.desc.replace("java/lang/System$Logger", "com/chocohead/nsn/SystemLogger");
						break;
					}

					case "java/lang/System$Logger$Level": {
						min.owner = "com/chocohead/nsn/SystemLogger$Level";
						break;
					}

					case "java/lang/Record": {
						min.owner = "java/lang/Object";
						break;
					}

					case "java/util/stream/Collectors": {
						switch (min.name.concat(min.desc)) {
						case "toUnmodifiableList()Ljava/util/stream/Collector;":
							min.owner = "com/google/common/collect/ImmutableList";
							min.name = "toImmutableList";
							break;

						case "toUnmodifiableSet()Ljava/util/stream/Collector;":
							min.owner = "com/google/common/collect/ImmutableSet";
							min.name = "toImmutableSet";
							break;

						case "toUnmodifiableMap(Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/util/stream/Collector;":
						case "toUnmodifiableMap(Ljava/util/function/Function;Ljava/util/function/Function;Ljava/util/function/BinaryOperator;)Ljava/util/stream/Collector;":
							min.owner = "com/google/common/collect/ImmutableMap";
							min.name = "toImmutableMap";
							break;
						}
						break;
					}

					case "java/util/Map": {
						switch (min.name.concat(min.desc)) {
						case "of()Ljava/util/Map;":
							min.owner = "java/util/Collections";
							min.name = "emptyMap";
							min.itf = false;
							break;

						case "of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
							min.owner = "java/util/Collections";
							min.name = "singletonMap";
							min.itf = false;
							break;

						case "copyOf(Ljava/util/Map;)Ljava/util/Map;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
							min.owner = "com/google/common/collect/ImmutableMap";
							min.desc = min.desc.substring(0, min.desc.length() - 14).concat("com/google/common/collect/ImmutableMap;");
							min.itf = false;
							break;

						case "entry(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map$Entry;": //A non-null and non-Serialisable Entry is a bit weird
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
						case "of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;":
							min.owner = "com/chocohead/nsn/Maps";
							min.itf = false;
							break;

						case "ofEntries([Ljava/util/Map$Entry;)Ljava/util/Map;":
							it.previous();
							it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false));
							it.next();
							min.owner = "com/google/common/collect/ImmutableMap";
							min.name = "copyOf";
							min.desc = "(Ljava/lang/Iterable;)Lcom/google/common/collect/ImmutableMap;";
							min.itf = false;
							break;
						}
						break;
					}

					case "java/util/Map$Entry": {
						if ("copyOf".equals(min.name) && "(Ljava/util/Map$Entry;)Ljava/util/Map$Entry;".equals(min.desc)) {
							min.owner = "com/chocohead/nsn/Maps";
							min.itf = false;
						}
						break;
					}

					case "java/util/stream/Stream": {
						switch (min.name.concat(min.desc)) {
						case "toList()Ljava/util/List;":
							min.name = "toArray";
							min.desc = "()[Ljava/lang/Object;";
							it.add(new InsnNode(Opcodes.DUP));
							it.add(new InsnNode(Opcodes.ARRAYLENGTH));
							it.add(new LdcInsnNode(Type.getType("[Ljava/lang/Object;")));
							it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf", "([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;", false));
							it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false));
							it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Collections", "unmodifiableList", "(Ljava/util/List;)Ljava/util/List;", false));
							break;

						case "mapMulti(Ljava/util/function/BiConsumer;)Ljava/util/stream/Stream;":
						case "mapMultiToInt(Ljava/util/function/BiConsumer;)Ljava/util/stream/IntStream;":
						case "mapMultiToLong(Ljava/util/function/BiConsumer;)Ljava/util/stream/LongStream;":
						case "mapMultiToDouble(Ljava/util/function/BiConsumer;)Ljava/util/stream/DoubleStream;":
						case "takeWhile(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;":
						case "dropWhile(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;":
							min.desc = "(Ljava/util/stream/Stream;".concat(min.desc.substring(1));
							min.setOpcode(Opcodes.INVOKESTATIC);
						case "ofNullable(Ljava/lang/Object;)Ljava/util/stream/Stream;":
							min.owner = "com/chocohead/nsn/Streams";
							min.itf = false;
							break;
						}
						break;
					}

					case "java/util/concurrent/atomic/AtomicReference": {
						if ("setPlain".equals(min.name) && "(Ljava/lang/Object;)V".equals(min.desc)) {
							min.name = "set";
						}
						break;
					}

					case "java/lang/Class": {
						if ("arrayType".equals(min.name) && "()Ljava/lang/Class;".equals(min.desc)) {
							it.previous();
							it.add(new InsnNode(Opcodes.ICONST_0));
							it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/reflect/Array", "newInstance", "(Ljava/lang/Class;I)Ljava/lang/Object;", false));
							it.next();
							min.owner = "java/lang/Object";
							min.name = "getClass";
						}
						break;
					}

					case "java/util/concurrent/CompletableFuture": {
						switch (min.name.concat(min.desc)) {
						case "exceptionallyAsync(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;":
						case "exceptionallyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;":
						case "exceptionallyCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;":
						case "exceptionallyComposeAsync(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;":
						case "exceptionallyComposeAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/CompletableFutures";
							min.desc = "(Ljava/util/concurrent/CompletableFuture;".concat(min.desc.substring(1));
							break;
						}
						break;
					}

					case "java/lang/invoke/MethodHandles": {
						if ("privateLookupIn".equals(min.name) && "(Ljava/lang/Class;Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/invoke/MethodHandles$Lookup;".equals(min.desc)) {
							min.owner = "com/chocohead/nsn/Binoculars";
						}
						break;
					}

					case "java/lang/invoke/MethodHandles$Lookup": {
						if ("defineClass".equals(min.name) && "([B)Ljava/lang/Class;".equals(min.desc)) {
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/Binoculars";
							min.desc = "(Ljava/lang/invoke/MethodHandles$Lookup;[B)Ljava/lang/Class;";
						}
						break;
					}

					case "java/util/Objects": {
						switch (min.name.concat(min.desc)) {
						case "requireNonNullElse(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;":
							min.owner = "com/google/common/base/MoreObjects";
							min.name = "firstNonNull";
							break;

						case "requireNonNullElseGet(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;":
						case "checkFromIndexSize(III)I":
							min.owner = "com/chocohead/nsn/MoreObjects";
							break;
						}
						break;
					}

					case "java/io/InputStream": {
						switch (min.name.concat(min.desc)) {
						case "nullInputStream()Ljava/io/InputStream;": //An inexact replicate in terms of close behaviour, but good enough
							it.previous();
							it.add(new TypeInsnNode(Opcodes.NEW, "org/apache/commons/io/input/NullInputStream"));
							it.add(new InsnNode(Opcodes.LCONST_0));
							it.add(new InsnNode(Opcodes.ICONST_0));
							it.add(new InsnNode(Opcodes.ICONST_0));
							it.next();
							min.setOpcode(Opcodes.INVOKESPECIAL);
							min.owner = "org/apache/commons/io/input/NullInputStream";
							min.name = "<init>";
							min.desc = "(JZZ)V";
							break;

						case "readNBytes(I)[B":
							it.previous();
							it.add(new InsnNode(Opcodes.I2L)); //IOUtils#readAllBytes(InputStream, int) will throw an IOException if the result reads short 
							it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/google/common/io/ByteStreams", "limit", "(Ljava/io/InputStream;J)Ljava/io/InputStream;", false));
							it.next();
						case "readAllBytes()[B":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "org/apache/commons/io/IOUtils";
							min.name = "toByteArray";
							min.desc = "(Ljava/io/InputStream;)[B";
							break;
						}
						break;
					}

					case "java/io/OutputStream": {
						if ("nullOutputStream".equals(min.name) && "()Ljava/io/OutputStream;".equals(min.desc)) {
							min.owner = "com/google/common/io/ByteStreams"; //Commons IO also has a NullOutputStream type
						}
						break;
					}

					case "java/io/Reader": {
						if ("nullReader".equals(min.name) && "()Ljava/io/Reader;".equals(min.desc)) {
							it.previous();
							it.add(new TypeInsnNode(Opcodes.NEW, "org/apache/commons/io/input/NullReader"));
							it.add(new InsnNode(Opcodes.LCONST_0));
							it.add(new InsnNode(Opcodes.ICONST_0));
							it.add(new InsnNode(Opcodes.ICONST_0));
							it.next();
							min.setOpcode(Opcodes.INVOKESPECIAL);
							min.owner = "org/apache/commons/io/input/NullReader";
							min.name = "<init>";
							min.desc = "(JZZ)V";
						}
						break;
					}

					case "java/io/Writer": {
						if ("nullWriter".equals(min.name) && "()Ljava/io/Writer;".equals(min.desc)) {
							it.previous();
							it.add(new TypeInsnNode(Opcodes.NEW, "org/apache/commons/io/input/NullWriter"));
							it.next();
							min.setOpcode(Opcodes.INVOKESPECIAL);
							min.owner = "org/apache/commons/io/input/NullWriter";
							min.name = "<init>";
							min.desc = "()V";
						}
						break;
					}

					case "java/nio/file/Files": {
						switch (min.name.concat(min.desc)) {
						case "writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;":
						case "writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;Ljava/nio/charset/Charset;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;":
						case "readString(Ljava/nio/file/Path;)Ljava/lang/String;":
						case "readString(Ljava/nio/file/Path;Ljava/nio/charset/Charset;)Ljava/lang/String;":
							min.owner = "com/chocohead/nsn/MoreFiles";
							break;
						}
						break;
					}

					case "java/nio/file/FileSystems": {
						switch (min.name.concat(min.desc)) {
						case "newFileSystem(Ljava/nio/file/Path;)Ljava/nio/file/FileSystem;":
						case "newFileSystem(Ljava/nio/file/Path;Ljava/util/Map;)Ljava/nio/file/FileSystem;":
						case "newFileSystem(Ljava/nio/file/Path;Ljava/util/Map;Ljava/lang/ClassLoader;)Ljava/nio/file/FileSystem;":
							min.owner = "com/chocohead/nsn/FiledSystems";
							break;
						}
						break;
					}

					case "java/util/regex/Matcher": {
						switch (min.name.concat(min.desc)) {
						case "appendReplacement(Ljava/lang/StringBuilder;Ljava/lang/String;)Ljava/util/regex/Matcher;":
						case "appendTail(Ljava/lang/StringBuilder;)Ljava/lang/StringBuilder;":
						case "replaceAll(Ljava/util/function/Function;)Ljava/lang/String;":
						case "results()Ljava/util/stream/Stream;":
						case "replaceFirst(Ljava/util/function/Function;)Ljava/lang/String;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/Matchy";
							min.desc = "(Ljava/util/regex/Matcher;".concat(min.desc.substring(1));
							break;
						}
						break;
					}

					case "java/nio/file/Path": {
						if ("of".equals(min.name) && "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;".equals(min.desc)) {
							min.owner = "java/nio/file/Paths";
							min.name = "get";
							min.itf = false;
						}
						break;
					}

					case "java/lang/StackWalker": {
						if ("getCallerClass".equals(min.name) && "()Ljava/lang/Class;".equals(min.desc)) {
							it.previous();
							it.add(new InsnNode(Opcodes.POP));
							it.add(new InsnNode(Opcodes.ICONST_2));
							it.next();
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "sun/reflect/Reflection";
							min.desc = "(I)Ljava/lang/Class;";
						} else {
							min.owner = "com/chocohead/nsn/StackWalker";
							min.desc = min.desc.replace("java/lang/StackWalker", "com/chocohead/nsn/StackWalker");
						}
						break;
					}

					case "java/util/ServiceLoader": {
						switch (min.name.concat(min.desc)) {
						case "findFirst()Ljava/util/Optional;":
						case "stream()Ljava/util/stream/Stream;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/ServiceLoaders";
							min.desc = "(Ljava/util/ServiceLoader;".concat(min.desc.substring(1).replace("java/util/ServiceLoader$Provider", "com/chocohead/nsn/ServiceLoaders$Provider"));
							break;
						}
						break;
					}

					case "java/util/ServiceLoader$Provider": {
						min.owner = "com/chocohead/nsn/ServiceLoaders$Provider";
						break;
					}

					case "java/util/concurrent/TimeUnit": {
						if ("convert".equals(min.name) && "(Ljava/time/Duration;)J".equals(min.desc)) {
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "com/chocohead/nsn/TimeUnits";
							min.desc = "(Ljava/util/concurrent/TimeUnit;".concat(min.desc.substring(1));
						}
						break;
					}
					}
					break;
				}

				case AbstractInsnNode.FIELD_INSN: {
					FieldInsnNode fin = (FieldInsnNode) insn;

					fin.desc = fin.desc.replace("java/lang/StackWalker", "com/chocohead/nsn/StackWalker").replace("java/lang/System$Logger", "com/chocohead/nsn/SystemLogger");

					if ("java/lang/StackWalker$Option".equals(fin.owner)) {
						fin.owner = "com/chocohead/nsn/StackWalker$Option";						
					} else if (fin.owner.startsWith("java/lang/System$Logger")) {
						fin.owner = fin.owner.replace("java/lang/System$Logger", "com/chocohead/nsn/SystemLogger");
					}
					break;
				}

				case AbstractInsnNode.TYPE_INSN: {
					TypeInsnNode tin = (TypeInsnNode) insn;

					switch (tin.desc) {
					case "java/lang/Record":
						tin.desc = "java/lang/Object";
						break;
					case "java/util/ServiceLoader$Provider": {
						tin.desc = "com/chocohead/nsn/ServiceLoaders$Provider";
						break;
					}
					}
					break;
				}
				}
			}
		}
		node.methods.addAll(extraMethods);

		for (FieldNode field : node.fields) {
			field.desc = field.desc.replace("java/lang/StackWalker", "com/chocohead/nsn/StackWalker").replace("java/lang/System$Logger", "com/chocohead/nsn/SystemLogger");
		}

		for (Iterator<InnerClassNode> it = node.innerClasses.iterator(); it.hasNext();) {
			InnerClassNode innerClass = it.next();

			switch (innerClass.name) {
			case "java/lang/System$Logger": {
				it.remove();
				break;
			}

			case "java/lang/System$Logger$Level": {
				innerClass.name = "com/chocohead/nsn/SystemLogger$Level";
				innerClass.outerName = "com/chocohead/nsn/SystemLogger";
				break;
			}

			case "java/util/ServiceLoader$Provider": {
				innerClass.name = "com/chocohead/nsn/ServiceLoaders$Provider";
				innerClass.outerName = "com/chocohead/nsn/ServiceLoaders";
				break;
			}
			}
		}
	}

	private static String prependMem(String to) {
		int len = to.length();
		char[] out = new char[len + 3];

        out[0] = out[2] = 'm';
        out[1] = 'e';
        out[3] = Character.toTitleCase(to.charAt(0));
        to.getChars(1, len, out, 4);

        return String.valueOf(out);		
	}

	private static void doToArray(ListIterator<AbstractInsnNode> it, MethodInsnNode min) {
		it.previous();
		it.add(new InsnNode(Opcodes.ICONST_0));
		it.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/function/IntFunction", "apply", "(I)Ljava/lang/Object;", true));
		it.add(new TypeInsnNode(Opcodes.CHECKCAST, "[Ljava/lang/Object;"));
		it.next();
		min.desc = "([Ljava/lang/Object;)[Ljava/lang/Object;";
	}
}