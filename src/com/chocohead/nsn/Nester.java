package com.chocohead.nsn;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.FabricLoaderImpl;

import com.chocohead.nsn.util.Fields;

public class Nester {
	public static class ScanResult {
		final Set<String> toTransform = new HashSet<>(4096);
		final Set<String> pluginClasses = new HashSet<>(32);
		final Map<String, List<Supplier<String>>> interfaceTargets = new HashMap<>(64);
		Map<String, List<ClassReader>> nests = new HashMap<>(128);
		CompletableFuture<Map<String, Consumer<ClassNode>>> nestTransforms;

		ScanResult() {
		}

		public Set<String> getTargets() {
			return Collections.unmodifiableSet(toTransform);
		}

		public Set<String> getMixinTargets() {
			return Sets.difference(toTransform, pluginClasses);
		}

		public Map<String, List<Supplier<String>>> getInterfaceTargets() {
			return Collections.unmodifiableMap(interfaceTargets);
		}

		void calculateNests() {
			try {
				resolveNestSystem(Collections.singleton(new ClassReader(Object.class.getName())));
			} catch (IOException e) {
				//Only warming up for class loading
			}
			@SuppressWarnings("unchecked") //We'll be careful
			CompletableFuture<Map<String, Consumer<ClassNode>>>[] tasks = new CompletableFuture[nests.size()];

			int i = 0;
			for (Collection<ClassReader> system : nests.values()) {
				//System.out.println(system.stream().map(ClassReader::getClassName).collect(Collectors.joining(", ", "Analysing: [", "]")));
				tasks[i++] = CompletableFuture.supplyAsync(() -> resolveNestSystem(system));
			}

			nestTransforms = CompletableFuture.allOf(tasks).thenApply(empty -> {
				Map<String, Consumer<ClassNode>> out = new HashMap<>();

				for (CompletableFuture<Map<String, Consumer<ClassNode>>> task : tasks) {
					out.putAll(task.join());
				}

				return out;
			});
			searchPlugins(nests.values());
			nests = null;
		}

		private void searchPlugins(Collection<? extends Collection<ClassReader>> systems) {
			Set<String> names = new HashSet<>(8);

			for (Collection<ClassReader> system : systems) {
				boolean pluginUsed = false;

				for (ClassReader reader : system) {
					String name = reader.getClassName();

					if (pluginUsed) {
						pluginClasses.add(name);
					} else if (pluginClasses.contains(name)) {
						pluginUsed = true;

						pluginClasses.addAll(names);
					} else {
						names.add(name);
					}
				}

				names.clear();
			}
		}

		public Map<String, Consumer<ClassNode>> getNestTransforms() {
			return Maps.filterKeys(nestTransforms.join(), Predicates.not(pluginClasses::contains));
		}

		public boolean applyNestTransform(ClassNode node) {
			Consumer<ClassNode> out = nestTransforms.join().get(node.name);

			if (out != null) {
				out.accept(node);
				return true;
			} else {
				return false;
			}
		}
	}

	static ScanResult run() {
		ScanResult out = new ScanResult();
		RecyclableDataInputStream buffer = new RecyclableDataInputStream();

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			switch (mod.getMetadata().getId()) {
			case "fabricloader":
			case "java":
			case "nsn":
				break;

			default:
				for (Path root : mod.getRootPaths()) {
					walkTree(buffer, root, out);
				}
				break;
			}
		}
		try {
			@SuppressWarnings("unchecked")
			Set<Path> logLibraries = (Set<Path>) Fields.readDeclared(((FabricLoaderImpl) FabricLoader.getInstance()).getGameProvider(), "logJars");
			for (Path library : logLibraries) {
				walkLibrary(buffer, library, out);
			}

			@SuppressWarnings("unchecked")
			List<Path> libraries = (List<Path>) Fields.readDeclared(((FabricLoaderImpl) FabricLoader.getInstance()).getGameProvider(), "miscGameLibraries");
			for (Path library : libraries) {
				walkLibrary(buffer, library, out);
			}
		} catch (ReflectiveOperationException e) {
			System.err.println("Failed to read GameProvider libraries");
			e.printStackTrace();
		}

		out.calculateNests();
		return out;
	}

	private static void walkLibrary(RecyclableDataInputStream buffer, Path jar, ScanResult result) {
		try (FileSystem fs = FiledSystems.newFileSystem(jar, Collections.emptyMap())) {
			for (Path root : fs.getRootDirectories()) {
				walkTree(buffer, root, result);
			}
		} catch (FileSystemAlreadyExistsException | IOException e) {
			throw new RuntimeException("Failed to read library jar at " + jar, e);
		}
	}

	private static void walkTree(RecyclableDataInputStream buffer, Path jar, ScanResult result) {
		try {
			Files.walkFileTree(jar, new FileVisitor<Path>() {
				private final MixinChecker checker = new MixinChecker();

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					return FileVisitResult.CONTINUE;
				}

				private String getFileExtension(Path path) {
					Path name = path.getFileName();

					if (name == null) {
						return "";
					}

					String fileName = name.toString();
					int dotIndex = fileName.lastIndexOf('.');
					return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1);
				}

				private boolean skipNewer(Path file, String name) {
					int packages = 0;
					for (int pos = name.indexOf('/') + 1; pos > 0; pos = name.indexOf('/', pos) + 1) {
						packages++;
					}

					int pathNames = file.getNameCount();
					int packageOffset = pathNames - packages - 1;
					if (packageOffset >= 3 && "META-INF".equals(file.getName(packageOffset - 3).toString())
							&& "versions".equals(file.getName(packageOffset - 2).toString())
							&& file.subpath(packageOffset, pathNames).toString().replace('\\', '/').startsWith(name)) {
						try {
							return Integer.parseInt(file.getName(packageOffset - 1).toString()) > 8;
						} catch (NumberFormatException e) {
						}
					}

					return false;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if ("class".equalsIgnoreCase(getFileExtension(file))) {
						try (DataInputStream in = buffer.open(Files.newInputStream(file))) {
							in.mark(16);

							int magic = in.readInt();
							if (magic != 0xCAFEBABE) {
								System.err.printf("Expected magic in %s but got %X%n", file, magic);
								return FileVisitResult.CONTINUE; //Not a class?
							}

							in.readUnsignedShort(); //Minor version
							out: if (in.readUnsignedShort() > Opcodes.V1_8) {
								in.reset();

								ClassReader reader = new ClassReader(in);
								String name = reader.getClassName();
								if (skipNewer(file, name)) break out;
								//System.out.println("Planning to transform ".concat(name));
								reader.accept(checker, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

								if (!checker.isMixin()) {
									result.toTransform.add(name);

									if (checker.isMixinPlugin()) {
										result.pluginClasses.addAll(checker.getPluginClasses());
									}

									if (checker.inNestedSystem()) {
										result.nests.computeIfAbsent(checker.isNestHost() ? name : checker.getNestHost(), k -> new ArrayList<>()).add(reader);
									}
								} else if (Modifier.isInterface(reader.getAccess())) {
									result.interfaceTargets.computeIfAbsent(name, k -> new ArrayList<>(4)).addAll(checker.getLazyTargets());
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

	private static Map<String, Consumer<ClassNode>> resolveNestSystem(Collection<ClassReader> system) {
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

		Map<String, Consumer<ClassNode>> tasks = new HashMap<>(ownerToMembers.size());
		for (Member member : ownerToMembers.values()) {
			Set<String> neededMethods = member.usedMethods;
			neededMethods.retainAll(member.methods);
			Set<String> neededFields = member.usedFields;
			neededFields.retainAll(member.fields);
			if (neededFields.isEmpty() && neededMethods.isEmpty()) continue;

			tasks.put(member.name, node -> {
				if (!neededMethods.isEmpty()) {
					for (MethodNode method : node.methods) {
						if (neededMethods.contains(method.name.concat(method.desc))) {
							method.access = (method.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
						}
					}
				}

				if (!neededFields.isEmpty()) {
					for (FieldNode field : node.fields) {
						if (neededFields.contains(field.name + '#' + field.desc)) {
							field.access = (field.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
						}
					}
				}
			});
		}

		return tasks;
	}
}