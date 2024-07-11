package com.chocohead.nsn;

import java.lang.invoke.MethodHandle;

public final class VarHandle {
	private final boolean isStatic, isArray;
	private final MethodHandle getter;
	private final MethodHandle setter;

	static VarHandle forArray(MethodHandle getter, MethodHandle setter) {
		return new VarHandle(false, true, getter, setter);
	}

	VarHandle(boolean isStatic, MethodHandle getter, MethodHandle setter) {
		this(isStatic, false, getter, setter);
	}

	private VarHandle(boolean isStatic, boolean isArray, MethodHandle getter, MethodHandle setter) {
		assert !isArray || !isStatic;
		this.isStatic = isStatic;
		this.isArray = isArray;
		this.getter = getter;
		this.setter = setter;
	}

	public Object get() throws Throwable {
		if (!isStatic) throw new UnsupportedOperationException();
		return getter.invoke();
	}

	public Object get(Object instance) throws Throwable {
		if (isStatic || isArray) throw new UnsupportedOperationException();
		return getter.invoke(instance);
	}

	public Object get(Object array, int index) throws Throwable {
		if (!isArray) throw new UnsupportedOperationException();
		return getter.invoke(array, index);
	}

	public void set(Object value) throws Throwable {
		if (setter == null) throw new UnsupportedOperationException();
		if (!isStatic) throw new UnsupportedOperationException();
		setter.invoke(value);
	}

	public void set(Object instance, Object value) throws Throwable {
		if (setter == null) throw new UnsupportedOperationException();
		if (isStatic || isArray) throw new UnsupportedOperationException();
		setter.invoke(instance, value);
	}

	public void set(Object array, int index, Object value) throws Throwable {
		if (setter == null) throw new UnsupportedOperationException();
		if (!isArray) throw new UnsupportedOperationException();
		setter.invoke(array, index, value);
	}

	public Object getVolatile() throws Throwable {
		return get(); //Should probably do this via Unsafe...
	}

	public Object getVolatile(Object instance) throws Throwable {
		return get(instance); //Should probably do this via Unsafe...
	}

	public Object getVolatile(Object array, int index) throws Throwable {
		return get(array, index); //Should probably do this via Unsafe...
	}

	public void setRelease(Object value) throws Throwable {
		set(value); //Not sureShould probably do this via Unsafe (as ordered put)...
	}

	public void setRelease(Object instance, Object value) throws Throwable {
		set(instance, value); //Should probably do this via Unsafe (as ordered put)...
	}

	public void setRelease(Object array, int index, Object value) throws Throwable {
		set(array, index, value); //Should probably do this via Unsafe (as ordered put)...
	}

	public boolean compareAndSet(Object expected, Object value) throws Throwable {
		if (setter == null) throw new UnsupportedOperationException();
		if (!isStatic) throw new UnsupportedOperationException();
		if (get() == expected) {//Should probably do this via Unsafe...
			set(value);
			return true;
		} else {
			return false;
		}
	}

	public boolean compareAndSet(Object instance, Object expected, Object value) throws Throwable {
		if (setter == null) throw new UnsupportedOperationException();
		if (isStatic || isArray) throw new UnsupportedOperationException();
		if (get(instance) == expected) {//Should probably do this via Unsafe...
			set(instance, value);
			return true;
		} else {
			return false;
		}
	}
}