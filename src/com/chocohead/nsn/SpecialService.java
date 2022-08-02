package com.chocohead.nsn;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.reflect.FieldUtils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.MixinEnvironment.Option;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.IExtensionRegistry;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.game.GameProvider;

import com.chocohead.nsn.util.Fields;

public class SpecialService {
	private static Stage stage;
	static Set<String> extraTransforms;

	private enum Stage {
		CROUCH {
			@Override
			void onSwitch() {
				try {
					Object delegate = Fields.readDeclared(SpecialService.class.getClassLoader(), "delegate");
					Fields.writeDeclared(delegate, "mixinTransformer", new IMixinTransformer() {
						@Override
						public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
							if (basicClass != null && basicClass.length >= 8) {
								int version = (basicClass[6] << 8) + basicClass[7];

								if (version > Opcodes.V1_8) {
									ClassNode node = new ClassNode();
									new ClassReader(basicClass).accept(node, 0);

									BulkRemapper.transform(node);
									BulkRemapper.toTransform.applyNestTransform(node);

									ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
									node.accept(writer);
									return writer.toByteArray();
								}
							}

							return basicClass;
						}

						@Override
						public boolean transformClass(MixinEnvironment environment, String name, ClassNode node) {
							if (node.version > Opcodes.V1_8) {
								BulkRemapper.transform(node);
								BulkRemapper.toTransform.applyNestTransform(node);

								return true;
							}

							return false;
						}

						@Override
						public byte[] transformClass(MixinEnvironment environment, String name, byte[] basicClass) {
							return transformClassBytes(name, name, basicClass);
						}

						@Override
						public List<String> reload(String mixinClass, ClassNode classNode) {
							return Collections.emptyList();
						}

						@Override
						public IExtensionRegistry getExtensions() {
							throw new UnsupportedOperationException(); //Probably won't need this
						}

						@Override
						public boolean generateClass(MixinEnvironment environment, String name, ClassNode classNode) {
							return false; //Wasn't generated
						}

						@Override
						public byte[] generateClass(MixinEnvironment environment, String name) {
							return null; //No class
						}

						@Override
						public boolean computeFramesForClass(MixinEnvironment environment, String name, ClassNode classNode) {
							throw new UnsupportedOperationException("The normal transformer doesn't either");
						}

						@Override
						public void audit(MixinEnvironment environment) {
							//All fine :)
						}
					});
					Fields.writeDeclared(delegate, "transformInitialized", true);
				} catch (ReflectiveOperationException | ClassCastException e) {
					throw new RuntimeException("Failed to fake Mixin transformer", e);
				}

				MixinEnvironment env = MixinEnvironment.getDefaultEnvironment();
				for (Option option : Option.values()) {
					env.setOption(option, env.getOption(option));
				}
			}
		}, BIND {
			@Override
			void onSwitch() {
				try {
					Object delegate = Fields.readDeclared(SpecialService.class.getClassLoader(), "delegate");
					Fields.writeDeclared(delegate, "transformInitialized", false);

					GameProvider provider = ((net.fabricmc.loader.impl.FabricLoaderImpl) FabricLoader.getInstance()).getGameProvider();
					@SuppressWarnings("unchecked") //Some would say that it is
					Map<String, byte[]> patches = (Map<String, byte[]>) Fields.readDeclared(provider.getEntrypointTransformer(), "patchedClasses");
					existingEntrypoints.addAll(patches.keySet());

					Path realms = (Path) Fields.readDeclared(provider, "realmsJar");
					@SuppressWarnings("unchecked")
					Collection<Path> loggingLibraries = (Collection<Path>) Fields.readDeclared(provider, "logJars");
					@SuppressWarnings("unchecked")
					Collection<Path> otherLibraries = (Collection<Path>) Fields.readDeclared(provider, "miscGameLibraries");
					class DynamicLoader implements Closeable {
						private final Closeable[] toClose = new Closeable[(realms != null ? 1 : 0) + loggingLibraries.size() + otherLibraries.size()];
						private final Path[] roots;

						private Iterable<Path> open(Path jar, int slot) {
							try {
								FileSystem fs = FiledSystems.newFileSystem(jar, Collections.emptyMap());
								toClose[slot] = fs;
								return fs.getRootDirectories();
							} catch (FileSystemAlreadyExistsException | IOException e) {
								throw new RuntimeException("Failed to read library jar at " + jar, e);
							}
						}

						public DynamicLoader() {
							List<Path> paths = new ArrayList<>();

							int slot = 0;
							if (realms != null) {
								for (Path root : open(realms, slot++)) paths.add(root);
							}
							for (Path loggingLibrary : loggingLibraries) {
								for (Path root : open(loggingLibrary, slot++)) paths.add(root);
							}
							for (Path otherLibrary : otherLibraries) {
								for (Path root : open(otherLibrary, slot++)) paths.add(root);
							}

							for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
								switch (mod.getMetadata().getId()) {
								case "fabricloader":
								case "java":
								case "nsn":
									break;

								default:
									paths.addAll(mod.getRootPaths());
									break;
								}
							}

							roots = paths.toArray(new Path[0]);
						}

						public InputStream getResourceAsStream(String name) throws IOException {
							for (Path root : roots) {
								Path out = root.resolve(name);
								if (Files.exists(out)) return Files.newInputStream(out);
							}

							return null;
						}

						@Override
						public void close() throws IOException {
							IOException toThrow = null;

							for (Closeable toClose : this.toClose) {
								try {
									toClose.close();
								} catch (IOException e) {
									if (toThrow == null) {
										toThrow = e;										
									} else {
										toThrow.addSuppressed(e);
									}
								}
							}

							if (toThrow != null) throw toThrow;
						}
					}
					try (DynamicLoader loader = new DynamicLoader()) {
						for (String name : BulkRemapper.toTransform.getTargets()) {
							String binaryName = name.replace('/', '.');
							if (existingEntrypoints.contains(binaryName)) continue; //Avoid replacing the real patches, shouldn't be loading those types before Mixin

							//System.out.println("About to load " + name);
							try (InputStream in = loader.getResourceAsStream('/' + name + ".class")) {
								if (in != null) {
									ClassReader reader = new ClassReader(in);

									if (reader.readShort(6) > Opcodes.V1_8) {
										//System.out.println("\tIt's too new!");
										ClassNode node = new ClassNode();
										reader.accept(node, 0);

										BulkRemapper.transform(node);
										BulkRemapper.toTransform.applyNestTransform(node);

										ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
										node.accept(writer);
										patches.put(binaryName, writer.toByteArray());
									}// else System.out.println("\tIt's fine");
								} else System.out.println("Didn't find " + name);
							} catch (IOException e) {
								//Class might not exist?
								System.err.println("Crashed trying to find " + name);
							}
						}
					} catch (IOException e) {
						System.err.println("Error cleaning up temporary loader");
						e.printStackTrace(); //Shouldn't happen, but not worth crashing over
					}

					Object storage = Fields.readDeclared(FabricLoader.getInstance(), "entrypointStorage");
					@SuppressWarnings("unchecked")
					Map<String, List<Object>> entries = (Map<String, List<Object>>) Fields.readDeclared(storage, "entryMap");
					List<Object> preLaunchers = entries.get("preLaunch");
					out: if (preLaunchers.size() > 1) {
						for (Iterator<?> it = preLaunchers.iterator(); it.hasNext();) {
							Object preLauncher = it.next();
							ModContainer mod = (ModContainer) Fields.readDeclared(preLauncher, "mod");

							if ("nsn".equals(mod.getMetadata().getId())) {
								it.remove();
								preLaunchers.add(0, preLauncher);
								break out;
							}
						}

						throw new IllegalStateException("Couldn't find the load guard?");
					}
				} catch (ReflectiveOperationException | ClassCastException e) {
					throw new RuntimeException("Failed to unfake Mixin transformer", e);
				}
			}
		}, SET {
			@Override
			void onSwitch() {
				try {
					@SuppressWarnings("unchecked") //Some would say that it is
					Map<String, byte[]> patches = (Map<String, byte[]>) FieldUtils.readDeclaredField(((net.fabricmc.loader.impl.FabricLoaderImpl) FabricLoader.getInstance()).getGameProvider().getEntrypointTransformer(), "patchedClasses", true);
					assert extraTransforms.stream().noneMatch(patches::containsKey);

					for (String name : BulkRemapper.toTransform.getMixinTargets()) {
						name = name.replace('/', '.');
						if (!existingEntrypoints.contains(name)) patches.remove(name);
					}

					IClassBytecodeProvider provider = MixinService.getService().getBytecodeProvider();
					for (String name : extraTransforms) {
						try {
							ClassNode node = provider.getClassNode(name, false);
							assert node.version > Opcodes.V1_8;

							BulkRemapper.transform(node);
							BulkRemapper.toTransform.applyNestTransform(node);

							ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
							node.accept(writer);
							patches.put(name, writer.toByteArray());
						} catch (IOException | ClassNotFoundException e) {
							throw new RuntimeException("Failed to add extra transform for " + name, e);
						}
					}

					existingEntrypoints = null; //Don't need to remember these anymore
					extraTransforms = null;
					BulkRemapper.toTransform = null;
				} catch (ReflectiveOperationException | ClassCastException e) {
					throw new RuntimeException("Failed to clear extra transformers", e);
				}
			}
		};
		static Set<String> existingEntrypoints = new HashSet<>();

		abstract void onSwitch();
	}

	static void link() {
		IMixinService service = ForwardingFactory.of(IMixinService.class, MixinService.getService()).handling("init", () -> {
			if (stage == null) {
				stage = Stage.CROUCH;
				stage.onSwitch();
			}
		}).handling("beginPhase", () -> {
			if (stage == Stage.CROUCH) {
				stage = Stage.BIND;
				stage.onSwitch();
			}
		}).handling("getClassTracker", IClassTracker.class, () -> null).make();

		try {
			MixinService instance = (MixinService) Fields.readDeclared(MixinService.class, "instance");
			Fields.writeDeclared(instance, "service", service);
		} catch (ReflectiveOperationException | ClassCastException e) {
			throw new RuntimeException("Failed to replace Mixin service", e);
		}
	}

	static void unlink(Set<String> trouble) {
		extraTransforms = trouble;
		stage = Stage.SET;
		stage.onSwitch();
	}
}