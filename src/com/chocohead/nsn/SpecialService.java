package com.chocohead.nsn;

import java.io.InputStream;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang3.reflect.FieldUtils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.FabricMixinTransformerProxy;
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

									BulkRemapper.earlyLoaded.add(node.name);
									BulkRemapper.transform(node);
									BulkRemapper.toTransform.applyNestTransform(node);

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
		}, BIND {
			@Override
			void onSwitch() {
				try {
					Object delegate = FieldUtils.readDeclaredField(SpecialService.class.getClassLoader(), "delegate", true);
					FieldUtils.writeDeclaredField(delegate, "transformInitialized", false, true);

					@SuppressWarnings("unchecked") //Some would say that it is
					Map<String, byte[]> patches = (Map<String, byte[]>) FieldUtils.readDeclaredField(((net.fabricmc.loader.FabricLoader) FabricLoader.getInstance()).getGameProvider().getEntrypointTransformer(), "patchedClasses", true);

					for (String name : BulkRemapper.toTransform.getTargets()) {
						//System.out.println("About to load " + name);
						try (InputStream in = SpecialService.class.getResourceAsStream('/' + name + ".class")) {
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
									patches.put(name.replace('/', '.'), writer.toByteArray());
								}// else System.out.println("\tIt's fine");
							}// else System.out.println("\tDidn't find it...");
						} catch (IOException e) {
							//Class might not exist?
							//System.err.println("\tCrashed trying to find it?");
						}
					}
				} catch (ReflectiveOperationException | ClassCastException e) {
					throw new RuntimeException("Failed to unfake Mixin transformer", e);
				}
			}
		}, SET {
			@Override
			void onSwitch() {
				try {
					Map<?, ?> patches = (Map<?, ?>) FieldUtils.readDeclaredField(((net.fabricmc.loader.FabricLoader) FabricLoader.getInstance()).getGameProvider().getEntrypointTransformer(), "patchedClasses", true);

					for (String name : BulkRemapper.toTransform.getTargets()) {
						patches.remove(name);
					}
					BulkRemapper.toTransform = null; //Don't need to remember these anymore
				} catch (ReflectiveOperationException | ClassCastException e) {
					throw new RuntimeException("Failed to clear extra transformers", e);
				}

				//Could undo the Mixin service changes?
			}
		};

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
		}).make();

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