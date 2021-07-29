package com.chocohead.nsn;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.lang3.reflect.FieldUtils;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.util.Constants.ManifestAttributes;
import org.spongepowered.asm.util.JavaVersion;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

public class Persuasion implements LanguageAdapter {
	private static double real;

	public Persuasion() {
		real = JavaVersion.current();
		set(16);

		MixinBootstrap.getPlatform().addContainer(new IContainerHandle() {			
			@Override
			public Collection<IContainerHandle> getNestedContainers() {
				return Collections.emptySet();
			}

			@Override
			public String getAttribute(String name) {
				switch (name) {
				case ManifestAttributes.MIXINCONFIGS:
					return "nsn.mixins.json";

				default:
					return null;
				}
			}
		});
	}

	static void flip() {
		set(real);
	}

	private static void set(double version) {
		try {
			Field field = FieldUtils.getDeclaredField(JavaVersion.class, "current", true);
			if (field != null) field.setDouble(null, version);
		} catch (ReflectiveOperationException e) {
			//Oh no
		}
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		throw new UnsupportedOperationException(mod.getMetadata().getName() + " tried to make us load " + value);
	}
}