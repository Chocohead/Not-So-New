package com.chocohead.nsn;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.common.base.Verify;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;

import com.chocohead.mm.api.ClassTinkerers;
import com.chocohead.nsn.Nester.ScanResult;

public class BulkRemapper implements IMixinConfigPlugin {
	static final SetMultimap<String, String> HUMBLE_INTERFACES = HashMultimap.create(64, 4);
	static ScanResult toTransform = Nester.run();

	@Override
	public void onLoad(String mixinPackage) {
		Persuasion.flip(); //We've done the persuading now
		StickyTape.tape();

		for (Entry<String, Supplier<String>> entry : toTransform.getInterfaceTargets().entries()) {
			HUMBLE_INTERFACES.put(entry.getValue().get(), entry.getKey());
		}

		mixinPackage = mixinPackage.replace('.', '/');
		generateMixin(mixinPackage.concat("SuperMixin"), toTransform.getMixinTargets());
		generateMixin(mixinPackage.concat("InterfaceMixin"), HUMBLE_INTERFACES.keySet());

		for (Entry<String, Consumer<ClassNode>> entry : toTransform.getNestTransforms().entrySet()) {
			ClassTinkerers.addTransformation(entry.getKey(), entry.getValue());
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
		boolean isInterface = Modifier.isInterface(node.access);

		Object2IntMap<String> nameToAccess;
		if (isInterface) {
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
							Verify.verify(!isInterface, "%s has instance method %s generated but is an interface?", node.name, idin.name);
							it.set(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, implementation.name, implementation.desc, false));
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

						case "slice()Ljava/nio/ByteBuffer;":
						case "slice(II)Ljava/nio/ByteBuffer;":
						case "duplicate()Ljava/nio/ByteBuffer;":
							min.setOpcode(Opcodes.INVOKESTATIC);
							min.owner = "org/lwjgl/system/MemoryUtil";
					        min.name = prependMem(min.name);
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
						case "isEmpty()Z": {
							min.name = "isPresent";
							LabelNode present = new LabelNode();
							it.add(new JumpInsnNode(Opcodes.IFEQ, present));
							it.add(new InsnNode(Opcodes.ICONST_0));
							LabelNode next = new LabelNode();
							it.add(new JumpInsnNode(Opcodes.GOTO, next));
							it.add(present);
							it.add(new InsnNode(Opcodes.ICONST_1));
							it.add(next);
							break;
						}

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

					case "java/lang/Record": {
						min.owner = "java/lang/Object";
						break;
					}

					case "java/util/stream/Collectors": {
						if ("toUnmodifiableList".equals(min.name) && "()Ljava/util/stream/Collector;".equals(min.desc)) {
							min.owner = "com/google/common/collect/ImmutableList";
							min.name = "toImmutableList";
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
						if ("toList".equals(min.name) && "()Ljava/util/List;".equals(min.desc)) {
							min.name = "toArray";
							min.desc = "()[Ljava/lang/Object;";
							it.add(new InsnNode(Opcodes.DUP));
							it.add(new InsnNode(Opcodes.ARRAYLENGTH));
							it.add(new LdcInsnNode(Type.getType("[Ljava/lang/Object;")));
							it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "copyOf", "([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;", false));
							it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false));
							it.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Collections", "unmodifiableList", "(Ljava/util/List;)Ljava/util/List;", false));
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
							min.owner = "com/chocohead/nsn/MoreObjects";
							break;
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
					}
					break;
				}
				}
			}
		}
		node.methods.addAll(extraMethods);
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