package com.chocohead.nsn;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.io.MoreFiles;

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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.transformer.ext.Extensions;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import com.chocohead.mm.api.ClassTinkerers;

public class Nester {
	static void run(String mixinPackage) {
		StickyTape.tape();

		Set<String> toTransform = new HashSet<>(4096);
		SetMultimap<String, ClassReader> nests = Multimaps.newSetMultimap(new HashMap<>(128), ReferenceOpenHashSet::new);
		RecyclableDataInputStream buffer = new RecyclableDataInputStream();

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String modID = mod.getMetadata().getId();

			if (!"fabricloader".equals(modID) && !"java".equals(modID) && !"nsn".equals(modID)) {
				if ("minecraft".equals(modID)) {
					String block = FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", "net.minecraft.class_2248").replace('.', '/');

					try (FileSystem fs = FileSystems.newFileSystem(BulkRemapper.class.getResource('/' + block + ".class").toURI(), Collections.emptyMap())) {
						for (Path root : fs.getRootDirectories()) {
							walkTree(buffer, root, toTransform, nests);
						}
					} catch (URISyntaxException | FileSystemAlreadyExistsException | IOException e) {
						throw new RuntimeException("Failed to read Minecraft jar", e);
					}
				} else {
					walkTree(buffer, mod.getRootPath(), toTransform, nests);
				}
			}
		}

		assert !toTransform.isEmpty();
		mixinPackage = mixinPackage.replace('.', '/');
		generateMixin(mixinPackage.concat("SuperMixin"), toTransform);
		generateMixin(mixinPackage.concat("InterfaceMixin"), BulkRemapper.HUMBLE_INTERFACES.keySet());

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

	private static void walkTree(RecyclableDataInputStream buffer, Path jar, Set<String> toTransform, SetMultimap<String, ClassReader> nests) {
		try {
			Files.walkFileTree(jar, new FileVisitor<Path>() {
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
									toTransform.add(name);

									if (checker.inNestedSystem()) {
										nests.put(checker.isNestHost() ? name : checker.getNestHost(), reader);
									}
								} else if (Modifier.isInterface(reader.getAccess())) {
									for (String target : checker.getTargets()) {
										BulkRemapper.HUMBLE_INTERFACES.put(target, name);
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
			public final Set<String> methods = new HashSet<>();
			public final Set<String> usedMethods = new HashSet<>();
			public final Set<String> fields = new HashSet<>();
			public final Set<String> usedFields = new HashSet<>();

			Member(String name) {
				this.name = name;
			}
		}
		Map<String, Member> ownerToMembers = system.stream().map(ClassReader::getClassName).collect(Collectors.toMap(Function.identity(), Member::new));

		for (ClassReader reader : system) {
			reader.accept(new ClassVisitor(Opcodes.ASM9) {
				private Member self;

				@Override
				public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
					self = ownerToMembers.get(name);
				}

				@Override
				public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
					if (Modifier.isPrivate(access)) self.fields.add(name + '#' + descriptor);

					return null;
				}

				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if (Modifier.isPrivate(access)) self.methods.add(name.concat(descriptor));

					return new MethodVisitor(api) {
						private void visitMethod(String owner, String name, String descriptor) {
							Member member = ownerToMembers.get(owner);

							if (member != null && member != self) { //Don't log the use of our own methods
								member.usedMethods.add(name.concat(descriptor));
							}
						}

						@Override
						public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
							Member member = ownerToMembers.get(owner);

							if (member != null && member != self) { //Don't log the use of our own fields
								member.usedFields.add(name + '#' + descriptor);
							}
						}

						@Override
						public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
							visitMethod(owner, name, descriptor);
						}

						@Override
						public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
							visitMethod(bootstrapMethodHandle.getOwner(), bootstrapMethodHandle.getName(), bootstrapMethodHandle.getDesc());

							for (Object argument : bootstrapMethodArguments) {
								if (argument instanceof Handle) {
									Handle handle = (Handle) argument;
									visitMethod(handle.getOwner(), handle.getName(), handle.getDesc());
								}
							}
						}
					};
				}
			}, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
		}

		List<Runnable> tasks = new ArrayList<>(ownerToMembers.size());
		for (Member member : ownerToMembers.values()) {
			Set<String> neededMethods = member.usedMethods;
			neededMethods.retainAll(member.methods);
			Set<String> neededFields = member.usedFields;
			neededFields.retainAll(member.fields);
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
}