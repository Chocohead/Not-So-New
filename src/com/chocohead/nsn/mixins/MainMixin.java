package com.chocohead.nsn.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.main.Main;

@Mixin(Main.class)
abstract class MainMixin {
	@ModifyArg(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/crash/CrashReport;create(Ljava/lang/Throwable;Ljava/lang/String;)Lnet/minecraft/util/crash/CrashReport;"))
	private static Throwable onBang(Throwable t) {
		t.printStackTrace();
		return t;
	}
}