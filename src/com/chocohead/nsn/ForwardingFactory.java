package com.chocohead.nsn;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import net.fabricmc.loader.api.FabricLoader;

public class ForwardingFactory extends ClassLoader {
	private static class Handler {
		public final Method method;
		public final Class<?> type;
		public final Object handler;
		public final BiConsumer<GeneratorAdapter, Runnable> writer;

		<T> Handler(String name, String desc, BiConsumer<GeneratorAdapter, Runnable> writer, Class<T> type, T method) {
			this.method = new Method(name, desc);
			this.type = type;
			this.handler = method;
			this.writer = writer;
		}

		@Override
		public int hashCode() {
			return method.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Handler && method.equals(((Handler) obj).method);
		}
	}

	public static class Builder<T> {
		private final Class<T> type;
		private final T instance;
		private final Set<Handler> handlers = new HashSet<>();

		Builder(Class<T> type, T instance) {
			this.type = type;
			this.instance = instance;
		}

		private void addHandler(Handler handler) {
			if (!handlers.add(handler)) {
				throw new IllegalArgumentException("Already have handler for " + type + '#' + handler.method);
			}
		}

		public Builder<T> handling(String name, Runnable handler) {
			addHandler(new Handler(name, "()V", (method, handlerLoader) -> {
				handlerLoader.run();
				method.invokeInterface(Type.getType(Runnable.class), new Method("run", "()V"));
				method.returnValue();
			}, Runnable.class, handler));

			return this;
		}

		private void castIfNecessary(GeneratorAdapter method, Class<?> type) {
			Type to = Type.getType(type);

			Method convert;
			switch (to.getSort()) {
			case Type.VOID:
				throw new AssertionError("A method is loading primitive void?");

			case Type.BOOLEAN: {
				to = Type.getObjectType("java/lang/Boolean");
				convert = new Method("booleanValue", "()Z");
				break;
			}

			case Type.BYTE: {
				to = Type.getObjectType("java/lang/Byte");
				convert = new Method("byteValue", "()B");
				break;
			}

			case Type.CHAR: {
				to = Type.getObjectType("java/lang/Character");
				convert = new Method("charValue", "()C");
				break;
			}

			case Type.SHORT: {
				to = Type.getObjectType("java/lang/Short");
				convert = new Method("shortValue", "()S");
				break;
			}

			case Type.INT: {
				to = Type.getObjectType("java/lang/Integer");
				convert = new Method("intValue", "()I");
				break;
			}

			case Type.LONG: {
				to = Type.getObjectType("java/lang/Long");
				convert = new Method("longValue", "()J");
				break;
			}

			case Type.FLOAT: {
				to = Type.getObjectType("java/lang/Float");
				convert = new Method("floatValue", "()F");
				break;
			}

			case Type.DOUBLE: {
				to = Type.getObjectType("java/lang/Double");
				convert = new Method("doubleValue", "()D");
				break;
			}

			case Type.OBJECT:
				if (type == Object.class) return;
			case Type.ARRAY:
				method.checkCast(to);
				return;

			case Type.METHOD:
				throw new IllegalArgumentException("Tried to use method Type as a constructor argument");

			default:
				throw new IllegalStateException("Unexpected type sort: " + to.getSort() + " (" + type + ')');
			}

			method.checkCast(to);
			method.invokeVirtual(to, convert);
		}

		public <R> Builder<T> handling(String name, Class<R> returnType, Supplier<R> handler) {
			addHandler(new Handler(name, "()".concat(Type.getDescriptor(returnType)), (method, handlerLoader) -> {
				handlerLoader.run();
				method.invokeInterface(Type.getType(Supplier.class), new Method("get", "()Ljava/lang/Object;"));
				castIfNecessary(method, returnType);
				method.returnValue();
			}, Supplier.class, handler));

			return this;
		}

		public <P> Builder<T> handling(String name, Class<P> arg, Consumer<P> handler) {
			addHandler(new Handler(name, '(' + Type.getDescriptor(arg) + ")V", (method, handlerLoader) -> {
				handlerLoader.run();
				method.loadArg(0);
				method.invokeInterface(Type.getType(Consumer.class), new Method("accept", "(Ljava/lang/Object;)V"));
				method.returnValue();
			}, Consumer.class, handler));

			return this;
		}

		public <P, R> Builder<T> handling(String name, Class<P> arg, Class<R> returnType, Function<P, R> handler) {
			addHandler(new Handler(name, '(' + Type.getDescriptor(arg) + ')' + Type.getDescriptor(returnType), (method, handlerLoader) -> {
				handlerLoader.run();
				method.loadArg(0);
				method.invokeInterface(Type.getType(Function.class), new Method("apply", "(Ljava/lang/Object;)Ljava/lang/Object;"));
				castIfNecessary(method, returnType);
				method.returnValue();
			}, Function.class, handler));

			return this;
		}

		public T make() {
			return ForwardingFactory.make(type, instance, handlers);
		}
	}

	public static <T> Builder<T> of(Class<T> type, T instance) {
		if (!type.isInterface()) throw new IllegalArgumentException("Type is not interface: " + type);
		return new Builder<>(type, instance);
	}

	static {
		registerAsParallelCapable();
	}
	private static final ForwardingFactory INSTANCE = new ForwardingFactory();
	private static final AtomicInteger PROXY = new AtomicInteger(1);

	private ForwardingFactory() {
		super(ForwardingFactory.class.getClassLoader());
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		return super.findClass(name);
	}

	private Class<?> spawnClass(String name, byte... bytes) {
		return defineClass(name, bytes, 0, bytes.length);
	}

	static <T> T make(Class<T> type, T instance, Set<Handler> handlers) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		Type thisType = Type.getObjectType("com/chocohead/proxy/" + Type.getInternalName(type) + PROXY.getAndIncrement());
		Type interfaceType = Type.getType(type);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL, thisType.getInternalName(), null, "java/lang/Object", new String[] {interfaceType.getInternalName()});

		GeneratorAdapter constructor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, new Method("<init>", '(' + interfaceType.getDescriptor() + Strings.repeat("Ljava/lang/Object;", handlers.size()) + ")V"), null, null, writer);
		constructor.visitCode();
		constructor.loadThis();
		constructor.invokeConstructor(Type.getObjectType("java/lang/Object"), new Method("<init>", "()V"));

		Set<Method> forwardMethods = Arrays.stream(type.getMethods()).map(Method::getMethod).collect(Collectors.toSet());
		forwardMethods.removeAll(Collections2.transform(handlers, handler -> handler.method));

		Object[] args = new Object[handlers.size() + 1];
		args[0] = instance;
		if (!forwardMethods.isEmpty()) {
			writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "real", interfaceType.getDescriptor(), null, null).visitEnd();

			constructor.loadThis();
			constructor.loadArg(0);
			constructor.putField(thisType, "real", interfaceType);
		}

		for (Method forward : forwardMethods) {
			GeneratorAdapter method = new GeneratorAdapter(Opcodes.ACC_PUBLIC, forward, null, null, writer);
			method.visitCode();
			method.loadThis();
			method.getField(thisType, "real", interfaceType);
			method.loadArgs();
			method.invokeInterface(interfaceType, forward);
			method.returnValue();
			method.endMethod();
		}

		int index = 0;
		for (Handler handler : handlers) {
			String fieldName = "handler" + index++;
			Type fieldType = Type.getType(handler.type);

			writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, fieldName, fieldType.getDescriptor(), null, null).visitEnd();
			GeneratorAdapter method = new GeneratorAdapter(Opcodes.ACC_PUBLIC, handler.method, null, null, writer);
			method.visitCode();
			handler.writer.accept(method, () -> {
				method.loadThis();
				method.getField(thisType, fieldName, fieldType);
			});
			method.endMethod();

			constructor.loadThis();
			constructor.loadArg(index);
			constructor.checkCast(fieldType);
			constructor.putField(thisType, fieldName, fieldType);
			args[index] = handler.handler;
		}

		constructor.returnValue();
		constructor.endMethod();

		Class<?> made = INSTANCE.spawnClass(thisType.getClassName(), writer.toByteArray());
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			try {
				Files.write(FabricLoader.getInstance().getGameDir().resolve(thisType.getClassName() + ".class"), writer.toByteArray());
			} catch (IOException e) {
				System.err.println("Failed to write " + thisType.getClassName());
				e.printStackTrace();
			}
		}

		try {
			Class<?>[] argTypes = new Class<?>[args.length];
			argTypes[0] = type;
			Arrays.fill(argTypes, 1, argTypes.length, Object.class);
			Object out = made.getConstructor(argTypes).newInstance(args);

			return type.cast(out);
		} catch (ReflectiveOperationException | ClassCastException e) {
			throw new RuntimeException("Failed to make forwarded " + made + " of " + instance, e);
		}
	}
}