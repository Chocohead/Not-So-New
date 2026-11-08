package com.chocohead.nsn.mixins;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.minecraft.util.monomorphizer.MonomorphizableDelegate")
abstract class MonomorphizableDelegateMixin {
	@Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"), remap = false)
	private static Object skipTemplateCreation(ConcurrentMap<?, ?> templates, Object delegateClass, Function<?, ?> creator) {
		//Creating the template needs the Class-File API (JDK 24+), just leave the delegate template unset.
		//The only reader of the field is makeMonomorphic below, which always takes the polymorphic fallback.
		return null;
	}

	@Redirect(method = "makeMonomorphic", at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;DEBUG_DISABLE_RUNTIME_MONOMORPHIZATION:Z"), remap = false)
	private static boolean alwaysDisableMonomorphization() {
		//The monomorphic delegates built by MonomorphicDelegateTemplate can't exist pre-JDK 22 (Class-File API),
		//so force the debug escape hatch Mojang already provides for the polymorphic delegate instead.
		return true;
	}
}
