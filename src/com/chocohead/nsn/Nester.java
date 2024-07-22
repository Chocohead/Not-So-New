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
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.reflect.FieldUtils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo.Field;
import org.spongepowered.asm.mixin.transformer.ClassInfo.Method;
import org.spongepowered.asm.util.Bytecode;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin.Kind;
import net.fabricmc.loader.impl.FabricLoaderImpl;

import com.chocohead.nsn.util.Fields;

public class Nester {
	public static class ScanResult {
		final Set<String> toTransform = new HashSet<>(4096);
		final Set<String> pluginClasses = new HashSet<>(32);
		final Map<String, List<Supplier<String>>> interfaceTargets = new HashMap<>(64);
		final Map<String, Set<CollectionMethod>> newLists = new HashMap<>(8);
		Map<String, List<Member>> nests = new HashMap<>(128);
		Map<String, List<MixinMember>> mixinNests = new HashMap<>(8);
		CompletableFuture<Map<String, Consumer<ClassNode>>> nestTransforms;
		private CompletableFuture<List<Entry<List<Supplier<String>>, Consumer<ClassNode>>>> mixinNestTransforms;

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

		public Map<String, Set<CollectionMethod>> getNewLists() {
			return Collections.unmodifiableMap(newLists);
		}

		private static <M extends Member, T, R> CompletableFuture<R> scheduleNesting(Collection<? extends Collection<M>> systems, Function<Collection<M>, T> nester, Function<CompletableFuture<T>[], R> then) {
			@SuppressWarnings("unchecked") //We'll be careful
			CompletableFuture<T>[] tasks = new CompletableFuture[systems.size()];

			int i = 0;
			for (Collection<M> system : systems) {
				//System.out.println(system.stream().map(Member::getName).collect(Collectors.joining(", ", "Analysing: [", "]")));
				tasks[i++] = CompletableFuture.<T>supplyAsync(() -> nester.apply(system));
			}

			return CompletableFuture.allOf(tasks).thenApply(empty -> then.apply(tasks));
		}

		void calculateNests() {
			try {
				resolveNestSystem(Collections.singleton(new Member(new ClassReader(Object.class.getName()))), MemberInclusionFilter.DEFAULT);
			} catch (IOException e) {
				//Only warming up for class loading
			}
			for (Entry<String, List<MixinMember>> entry : mixinNests.entrySet()) {
				List<Member> members = nests.remove(entry.getKey());
				if (members == null) continue; //No non-mixin inner types to rescue
				List<MixinMember> mixins = entry.getValue();

				for (Member member : members) {
					mixins.add(new MixinMember(member));
				}
			}
			nestTransforms = scheduleNesting(nests.values(), Nester::resolveNestTransformers, tasks -> {
				Map<String, Consumer<ClassNode>> out = new HashMap<>();

				for (CompletableFuture<Map<String, Consumer<ClassNode>>> task : tasks) {
					out.putAll(task.join());
				}

				return out;
			});
			mixinNestTransforms = scheduleNesting(mixinNests.values(), Nester::resolveNestedMixins, tasks -> {
				List<Entry<List<Supplier<String>>, Consumer<ClassNode>>> out = new ArrayList<>();

				for (CompletableFuture<List<Entry<List<Supplier<String>>, Consumer<ClassNode>>>> task : tasks) {
					out.addAll(task.join());
				}

				return out;
			});
			mixinNests = null;
			searchPlugins(nests.values());
			nests = null;
		}

		private void searchPlugins(Collection<? extends Collection<Member>> systems) {
			Set<String> names = new HashSet<>(8);

			for (Collection<Member> system : systems) {
				boolean pluginUsed = false;

				for (Member member : system) {
					if (pluginUsed) {
						pluginClasses.add(member.name);
					} else if (pluginClasses.contains(member.name)) {
						pluginUsed = true;

						pluginClasses.addAll(names);
					} else {
						names.add(member.name);
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

		public List<Entry<List<Supplier<String>>, Consumer<ClassNode>>> getMixinNestTransforms() {
			return mixinNestTransforms.join();
		}
	}
	private static final BiPredicate<String, String> IS_MIXIN_LOADED = new BiPredicate<String, String>() {
		private List<IMixinConfig> configs;

		@SuppressWarnings("unchecked")
		private void getConfigs() {
			try {
				Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
				if (transformer == null) throw new IllegalStateException("No active transformer?");

				Object processor = FieldUtils.readDeclaredField(transformer, "processor", true);
				assert processor != null; //Shouldn't manage to get it null

				Object configs = FieldUtils.readDeclaredField(processor, "configs", true);
				assert configs != null; //Shouldn't manage to be null either

				this.configs = (List<IMixinConfig>) configs;
			} catch (ReflectiveOperationException | ClassCastException e) {
				throw new RuntimeException("Failed to get mixin configs", e);
			}
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean test(String mixinName, String target) {
			assert mixinName.indexOf('.') < 0;
			assert target.indexOf('.') < 0;
			if (configs == null) getConfigs();

			for (IMixinConfig config : configs) {
				if (mixinName.startsWith(config.getMixinPackage())) {
					List<IMixinInfo> mixins;
					try {
						mixins = (List<IMixinInfo>) FieldUtils.readDeclaredField(config, "mixins", true);
					} catch (ReflectiveOperationException | ClassCastException e) {
						throw new RuntimeException("Failed to get mixins from config " + config, e);
					}

					for (IMixinInfo mixin : mixins) {
						if (mixinName.equals(mixin.getClassRef())) {
							assert mixin.getTargetClasses().stream().allMatch(name -> name.indexOf('.') < 0);
							return mixin.getTargetClasses().contains(target);
						}
					}
				}
			}

			return false; //No config owns the mixin, so it won't ever be loaded
		}
	};

	static ScanResult run() {
		ScanResult out = new ScanResult();
		Set<Path> seen = new HashSet<>(64);
		Map<String, Path> classRecall = new HashMap<>(512);
		RecyclableDataInputStream buffer = new RecyclableDataInputStream();

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			switch (mod.getMetadata().getId()) {
			case "fabricloader":
			case "java":
			case "nsn":
				break;

			default:
				if (mod.getOrigin().getKind() == Kind.PATH) {
					seen.addAll(mod.getOrigin().getPaths());
				}
				for (Path root : mod.getRootPaths()) {
					walkTree(buffer, root, classRecall, out);
				}
				break;
			}
		}
		try {
			@SuppressWarnings("unchecked")
			Set<Path> logLibraries = (Set<Path>) Fields.readDeclared(((FabricLoaderImpl) FabricLoader.getInstance()).getGameProvider(), "logJars");
			for (Path library : logLibraries) {
				if (!seen.add(library)) continue;
				walkLibrary(buffer, library, classRecall, out);
			}

			@SuppressWarnings("unchecked")
			List<Path> libraries = (List<Path>) Fields.readDeclared(((FabricLoaderImpl) FabricLoader.getInstance()).getGameProvider(), "miscGameLibraries");
			for (Path library : libraries) {
				if (!seen.add(library)) continue;
				walkLibrary(buffer, library, classRecall, out);
			}
		} catch (ReflectiveOperationException e) {
			System.err.println("Failed to read GameProvider libraries");
			e.printStackTrace();
		}

		out.calculateNests();
		return out;
	}

	static ScanResult run(Path... paths) {
		ScanResult out = new ScanResult();
		Map<String, Path> classRecall = new HashMap<>(512);
		RecyclableDataInputStream buffer = new RecyclableDataInputStream();

		for (Path path : paths) {
			walkLibrary(buffer, path, classRecall, out);
		}

		out.calculateNests();
		return out;
	}

	private static void walkLibrary(RecyclableDataInputStream buffer, Path jar, Map<String, Path> classRecall, ScanResult result) {
		try (FileSystem fs = FiledSystems.newFileSystem(jar, Collections.emptyMap())) {
			for (Path root : fs.getRootDirectories()) {
				walkTree(buffer, root, classRecall, result);
			}
		} catch (FileSystemAlreadyExistsException | IOException e) {
			throw new RuntimeException("Failed to read library jar at " + jar, e);
		}
	}

	private static void walkTree(RecyclableDataInputStream buffer, Path jar, Map<String, Path> classRecall, ScanResult result) {
		Map<String, Boolean> pluginClasses = new HashMap<>();

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
								classRecall.put(name, file);
								//System.out.println("Planning to transform ".concat(name));
								if (pluginClasses.containsKey(name)) {
									UsedTypeVisitor visitor = new UsedTypeVisitor(checker);
									reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

									for (String usedClass : visitor.getUsedClasses()) {
										pluginClasses.merge(usedClass, Boolean.FALSE, (seen, neverSeen) -> seen);
									}
									pluginClasses.put(name, Boolean.TRUE);
								} else {
									reader.accept(checker, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
								}

								if (!checker.isMixin()) {
									result.toTransform.add(name);

									if (checker.isCollection()) {
										result.newLists.put(name, checker.getCollectionMethods());
									}

									if (checker.isMixinPlugin()) {
										for (String pluginClass : checker.getPluginClasses()) {
											pluginClasses.merge(pluginClass, Boolean.FALSE, (seen, neverSeen) -> seen);
										}
										pluginClasses.put(name, Boolean.TRUE);
									}

									if (checker.inNestedSystem()) {
										result.nests.computeIfAbsent(checker.isNestHost() ? name : checker.getNestHost(), k -> new ArrayList<>()).add(new Member(reader));
									}
								} else {
									if (Modifier.isInterface(reader.getAccess())) {
										result.interfaceTargets.computeIfAbsent(name, k -> new ArrayList<>(4)).addAll(checker.getLazyTargets());
									}

									if (checker.inNestedSystem()) {
										result.mixinNests.computeIfAbsent(checker.isNestHost() ? name : checker.getNestHost(), k -> new ArrayList<>()).add(new MixinMember(reader, checker));
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

		if (!pluginClasses.isEmpty()) {
			Deque<String> toCheck = pluginClasses.entrySet().stream().filter(entry -> entry.getValue() == Boolean.FALSE).map(Entry::getKey).collect(Collectors.toCollection(ArrayDeque::new));
			UsedTypeVisitor checker = new UsedTypeVisitor();

			String checkedClass;
			while ((checkedClass = toCheck.poll()) != null) {
				/*Path file = jar.resolve(checkedClass.concat(".class"));
				if (!Files.exists(file)) continue; //Might not be a class from this mod*/
				Path file = classRecall.get(checkedClass);
				if (file == null) continue; //Might not be a class from this mod

				try (DataInputStream in = buffer.open(Files.newInputStream(file))) {
					in.mark(16);

					int magic = in.readInt();
					if (magic != 0xCAFEBABE) {
						System.err.printf("Expected magic in %s but got %X%n", file, magic);
						continue; //Not a class?
					}

					in.readUnsignedShort(); //Minor version
					if (in.readUnsignedShort() > Opcodes.V1_8) {
						in.reset();

						ClassReader reader = new ClassReader(in);
						reader.accept(checker, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

						for (String pluginClass : checker.getUsedClasses()) {
							if (pluginClasses.putIfAbsent(pluginClass, Boolean.FALSE) == null) {
								toCheck.addLast(pluginClass); //Only note classes we haven't seen yet
							}
						}

						checker.reset();
					}
				} catch (IOException e) {
					System.err.println("Broke visiting " + file); //Oops
					e.printStackTrace();
				}
			}

			result.pluginClasses.addAll(pluginClasses.keySet());
		}

		classRecall.clear();
	}

	private static class Member {
		public final ClassReader reader;
		public final String name;
		public final Set<String> methods = new HashSet<>();
		public final Set<String> usedMethods = new HashSet<>();
		public final Set<String> fields = new HashSet<>();
		public final Set<String> usedFields = new HashSet<>();

		Member(ClassReader reader) {
			this.reader = reader;
			this.name = reader.getClassName();
		}

		Member(Member member) {
			this.reader = member.reader;
			this.name = member.name;
			methods.addAll(member.methods);
			usedMethods.addAll(member.usedMethods);
			fields.addAll(member.fields);
			usedFields.addAll(member.usedFields);
		}

		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return getClass().getName() + '[' + name + ']';
		}
	}

	private static class MixinMember extends Member {
		public final List<Supplier<String>> targets;
		public final boolean isMixin;

		MixinMember(ClassReader reader, MixinChecker checker) {
			super(reader);

			isMixin = true;
			targets = new ArrayList<>(checker.getLazyTargets());
		}

		MixinMember(Member member) {
			super(member);

			isMixin = false;
			targets = Collections.emptyList();
		}
	}

	private interface MemberInclusionFilter<M extends Member> {
		MemberInclusionFilter<Member> DEFAULT = (member, access) -> Modifier.isPrivate(access);

		boolean test(M member, int access);
	}

	private static <M extends Member> void resolveNestSystem(Collection<M> system, MemberInclusionFilter<? super M> inclusionFilter) {
		Map<String, M> ownerToMembers = system.stream().collect(Collectors.toMap(Member::getName, Function.identity()));

		for (M member : system) {
			member.reader.accept(new ClassVisitor(Opcodes.ASM9) {
				private final M self = member;

				@Override
				public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
					if (inclusionFilter.test(self, access)) self.fields.add(name + '#' + descriptor);

					return null;
				}

				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					if (inclusionFilter.test(self, access)) self.methods.add(name.concat(descriptor));

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
	}

	private static Map<String, Consumer<ClassNode>> resolveNestTransformers(Collection<Member> system) {
		resolveNestSystem(system, MemberInclusionFilter.DEFAULT);

		Map<String, Consumer<ClassNode>> tasks = new HashMap<>(system.size());
		for (Member member : system) {
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

	private static List<Entry<List<Supplier<String>>, Consumer<ClassNode>>> resolveNestedMixins(Collection<MixinMember> system) {
		resolveNestSystem(system, (member, access) -> Modifier.isPrivate(access) || member.isMixin);

		List<Entry<List<Supplier<String>>, Consumer<ClassNode>>> out = new ArrayList<>();
		for (MixinMember member : system) {
			Set<String> neededMethods = member.usedMethods;
			neededMethods.retainAll(member.methods);
			Set<String> neededFields = member.usedFields;
			neededFields.retainAll(member.fields);
			if (neededFields.isEmpty() && neededMethods.isEmpty()) continue;
 
			if (member.isMixin) {
				String name = member.name; //Capture just the name rather than all of member
				out.add(new SimpleImmutableEntry<List<Supplier<String>>, Consumer<ClassNode>>(member.targets, node -> {
					ClassInfo mixin = ClassInfo.forName(name);

					if (!neededMethods.isEmpty()) {
						for (String neededMethod : neededMethods) {
							int split = neededMethod.indexOf('(');
							Method method = mixin.findMethod(neededMethod.substring(0, split), neededMethod.substring(split), ClassInfo.INCLUDE_ALL | ClassInfo.INCLUDE_INITIALISERS);
							if (method == null) throw new RuntimeException("Unable to find " + neededMethod + " in " + name);

							assert method.getOriginalName().regionMatches(0, neededMethod, 0, split);
							MethodNode realMethod = Bytecode.findMethod(node, method.getName(), method.getDesc());
							if (realMethod == null) {
								if (!IS_MIXIN_LOADED.test(mixin.getName(), node.name)) break;
								throw new RuntimeException("Unable to find " + method + " in " + name);
							}
							if (Modifier.isPrivate(realMethod.access)) {//If the method is shadowed, the real method might be accessible all along
								realMethod.access = (realMethod.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED;
							}
						}
					}

					if (!neededFields.isEmpty()) {
						on: for (String neededField : neededFields) {
							int split = neededField.indexOf('#');
							Field field = mixin.findField(neededField.substring(0, split), neededField.substring(split + 1), ClassInfo.INCLUDE_ALL);
							if (field == null) throw new RuntimeException("Unable to find " + neededField + " in " + name);

							assert field.getOriginalName().regionMatches(0, neededField, 0, split);
							for (FieldNode realField : node.fields) {
								if (realField.name.equals(field.getName()) && realField.desc.equals(field.getDesc())) {
									if (Modifier.isPrivate(realField.access)) {//Ditto for shadowed fields
										realField.access = (realField.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PROTECTED;
									}
									continue on;
								}
							}
							if (!IS_MIXIN_LOADED.test(mixin.getName(), node.name)) break;
							throw new RuntimeException("Unable to find " + field + " in " + name);
						}
					}
				}));
			} else {//Finding the mangled inner class names is inconvenient, cross the bridge when come to
				throw new UnsupportedOperationException("Private access in " + member.name + ", fields: " + neededFields + ", methods: " + neededMethods);
			}
		}

		return out;
	}
}