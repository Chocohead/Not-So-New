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
		generateMixin(mixinPackage.concat("SuperMixin"), toTransform.getTargets());
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

					case "java/util/stream/Collectors": {
						if ("toUnmodifiableList".equals(min.name) && "()Ljava/util/stream/Collector;".equals(min.desc)) {
							min.owner = "com/google/common/collect/ImmutableList";
							min.name = "toImmutableList";
						}
						break;
					}

					case "java/util/Map": {
						if ("entry".equals(min.name) && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map$Entry;".equals(min.desc)) {
							min.owner = "com/chocohead/nsn/ImmutableNonullEntry"; //A non-null and non-Serialisable Entry is a bit weird
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
					}
					break;
				}
				}
			}
		}
		node.methods.addAll(extraMethods);
	}
}