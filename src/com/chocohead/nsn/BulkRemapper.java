package com.chocohead.nsn;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import net.fabricmc.mapping.tree.ClassDef;
import net.fabricmc.mapping.tree.TinyTree;

import com.chocohead.mm.api.ClassTinkerers;

public class BulkRemapper implements Runnable {
	private void transform(ClassNode node) {
		node.version = Opcodes.V1_8;
	}

	@Override
	public void run() {
		TinyTree mappings = FabricLauncherBase.getLauncher().getMappingConfiguration().getMappings();

		String activeNamespace = FabricLoader.getInstance().getMappingResolver().getCurrentRuntimeNamespace();
		for (ClassDef clazz : mappings.getClasses()) {
			ClassTinkerers.addTransformation(clazz.getName(activeNamespace), this::transform);
		}
		ClassTinkerers.addTransformation("net/minecraft/client/main/Main$2", this::transform);
	}
}