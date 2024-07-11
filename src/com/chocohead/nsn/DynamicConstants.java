package com.chocohead.nsn;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

public class DynamicConstants {
	public static CallSite link(Lookup lookup, String name, MethodType type, MethodHandle constant, Object... args) throws Throwable {
		return new ConstantCallSite(MethodHandles.constant(type.returnType(), constant.asSpreader(Object[].class, args.length).invoke(lookup, name, type.returnType(), args)));
	}
}