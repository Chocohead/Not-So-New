package com.chocohead.nsn;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import org.apache.commons.lang3.reflect.FieldUtils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.FabricMixinTransformerProxy;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.MixinService;

import net.fabricmc.loader.api.FabricLoader;

public class SpecialService {
	private static Stage stage;

	private enum Stage {
		CROUCH {
			@Override
			void onSwitch() {
				try {
					Object delegate = FieldUtils.readDeclaredField(SpecialService.class.getClassLoader(), "delegate", true);
					FieldUtils.writeDeclaredField(delegate, "mixinTransformer", new FabricMixinTransformerProxy() {{
							try {
								FieldUtils.writeDeclaredStaticField(MixinEnvironment.class, "transformer", null, true);
							} catch (ReflectiveOperationException e) {
								throw new RuntimeException("Failed to reset active Mixin transformer", e);
							}
						}

						@Override
						public byte[] transformClassBytes(String name, String transformedName, byte[] basicClass) {
							if (basicClass != null && basicClass.length >= 8) {
								int version = (basicClass[6] << 8) + basicClass[7];

								if (version > Opcodes.V1_8) {
									ClassNode node = new ClassNode();
									new ClassReader(basicClass).accept(node, 0);
									BulkRemapper.transform(node);

									ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
									node.accept(writer);
									return writer.toByteArray();
								}
							}

							return basicClass;
						}
					}, true);
					FieldUtils.writeDeclaredField(delegate, "transformInitialized", true, true); 
				} catch (ReflectiveOperationException | ClassCastException e) {
					throw new RuntimeException("Failed to fake Mixin transformer", e);
				}
			}

			@Override
			void onLoad(String name) {
			}
		}, BIND {
			private final Map<String, byte[]> patches = grabPatches();

			@SuppressWarnings("unchecked")
			private Map<String, byte[]> grabPatches() {
				try {
					return (Map<String, byte[]>) FieldUtils.readDeclaredField(((net.fabricmc.loader.FabricLoader) FabricLoader.getInstance()).getGameProvider().getEntrypointTransformer(), "patchedClasses", true);
				} catch (ReflectiveOperationException | ClassCastException e) {
					throw new RuntimeException("Failed to find entrypoint patches", e);
				}
			}

			@Override
			void onSwitch() {
				try {
					Object delegate = FieldUtils.readDeclaredField(SpecialService.class.getClassLoader(), "delegate", true);
					FieldUtils.writeDeclaredField(delegate, "transformInitialized", false, true); 
				} catch (ReflectiveOperationException | ClassCastException e) {
					throw new RuntimeException("Failed to unfake Mixin transformer", e);
				}
			}

			@Override
			void onLoad(String name) {
				//System.out.println("About to load " + name);
				try (InputStream in = SpecialService.class.getResourceAsStream('/' + name.replace('.', '/') + ".class")) {
					if (in != null) {
						ClassReader reader = new ClassReader(in);

						if (reader.readShort(6) > Opcodes.V1_8) {
							//System.out.println("\tIt's too new!");
							ClassNode node = new ClassNode();
							reader.accept(node, 0);
							BulkRemapper.transform(node);

							ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
							node.accept(writer);
							patches.put(name, writer.toByteArray());
						}// else System.out.println("\tIt's fine");
					}// else System.out.println("\tDidn't find it...");
				} catch (IOException e) {
					//Class might not exist?
					//System.err.println("\tCrashed trying to find it?");
				}
			}
		}, SET {
			@Override
			void onSwitch() {
				//Could undo the Mixin service changes?
			}

			@Override
			void onLoad(String name) {
			}
		};

		abstract void onSwitch();

		abstract void onLoad(String name);
	}

	static void link() {
		IClassProvider replacement = new IClassProvider() {
			private final IClassProvider existing = MixinService.getService().getClassProvider();

			@Override
			@Deprecated
			public URL[] getClassPath() {
				return existing.getClassPath();
			}

			@Override
			public Class<?> findClass(String name) throws ClassNotFoundException {
				return findAgentClass(name, true);
			}

			@Override
			public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
				stage.onLoad(name);
				return existing.findClass(name, initialize);
			}

			@Override
			public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
				return existing.findAgentClass(name, initialize);
			}
		};
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
		}).handling("getClassProvider", IClassProvider.class, () -> replacement).make();

		try {
			MixinService instance = (MixinService) FieldUtils.readDeclaredStaticField(MixinService.class, "instance", true);
			FieldUtils.writeDeclaredField(instance, "service", service, true);
		} catch (ReflectiveOperationException | ClassCastException e) {
			throw new RuntimeException("Failed to replace Mixin service", e);
		}
	}

	static void unlink() {
		stage = Stage.SET;
		stage.onSwitch();
	}
}