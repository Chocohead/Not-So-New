package com.chocohead.nsn;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;
import com.google.common.io.MoreFiles;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import org.spongepowered.asm.mixin.MixinEnvironment;
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

		Extensions extensions = null;
		try {
			Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
			if (transformer == null) throw new IllegalStateException("Not running with a transformer?");

			for (Field f : transformer.getClass().getDeclaredFields()) {
				if (f.getType() == Extensions.class) {
					f.setAccessible(true); //Knock knock, we need this
					extensions = (Extensions) f.get(transformer);
					break;
				}
			}

			if (extensions == null) {
				String foundFields = Arrays.stream(transformer.getClass().getDeclaredFields()).map(f -> f.getType() + " " + f.getName()).collect(Collectors.joining(", "));
				throw new NoSuchFieldError("Unable to find extensions field, only found " + foundFields);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Running with a transformer that doesn't have extensions?", e);
		}

		extensions.add(new Extension(mixinPackage));
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
					}
					break;
				}
				}
			}
		}
		node.methods.addAll(extraMethods);
	}
}