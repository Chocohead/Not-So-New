package com.chocohead.nsn;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.MethodNode;

public enum CollectionMethod {
	addFirst("(Ljava/lang/Object;)V") {
		@Override
		protected void makeMethod(InstructionAdapter method) {
			method.load(0, InstructionAdapter.OBJECT_TYPE);
			method.iconst(0);
			method.load(1, InstructionAdapter.OBJECT_TYPE);
			method.invokeinterface("java/util/List", "add", "(ILjava/lang/Object;)V");
			method.areturn(Type.VOID_TYPE);
		}
	},
	addLast("(Ljava/lang/Object;)V") {
		@Override
		protected void makeMethod(InstructionAdapter method) {
			method.load(0, InstructionAdapter.OBJECT_TYPE);
			method.load(1, InstructionAdapter.OBJECT_TYPE);
			method.invokeinterface("java/util/List", "add", "(Ljava/lang/Object;)V");
			method.areturn(Type.VOID_TYPE);
		}
	},
	getFirst("()Ljava/lang/Object;") {
		@Override
		protected void makeMethod(InstructionAdapter method) {
			method.load(0, InstructionAdapter.OBJECT_TYPE);
			method.invokestatic("com/chocohead/nsn/Lists", "getFirst", "(Ljava/util/List;)Ljava/lang/Object;", false);
			method.areturn(InstructionAdapter.OBJECT_TYPE);
		}
	},
	getLast("()Ljava/lang/Object;") {
		@Override
		protected void makeMethod(InstructionAdapter method) {
			method.load(0, InstructionAdapter.OBJECT_TYPE);
			method.invokestatic("com/chocohead/nsn/Lists", "getLast", "(Ljava/util/List;)Ljava/lang/Object;", false);
			method.areturn(InstructionAdapter.OBJECT_TYPE);
		}
	},
	removeFirst("()Ljava/lang/Object;") {
		@Override
		protected void makeMethod(InstructionAdapter method) {
			method.load(0, InstructionAdapter.OBJECT_TYPE);
			method.invokestatic("com/chocohead/nsn/Lists", "removeFirst", "(Ljava/util/List;)Ljava/lang/Object;", false);
			method.areturn(InstructionAdapter.OBJECT_TYPE);
		}
	},
	removeLast("()Ljava/lang/Object;") {
		@Override
		protected void makeMethod(InstructionAdapter method) {
			method.load(0, InstructionAdapter.OBJECT_TYPE);
			method.invokestatic("com/chocohead/nsn/Lists", "removeLast", "(Ljava/util/List;)Ljava/lang/Object;", false);
			method.areturn(InstructionAdapter.OBJECT_TYPE);
		}
	},
	reversed("()Ljava/util/List;") {
		@Override
		protected void makeMethod(InstructionAdapter method) {
			method.load(0, InstructionAdapter.OBJECT_TYPE);
			method.invokestatic("com/google/common/collect/Lists", "reverse", "(Ljava/util/List;)Ljava/util/List;", false);
			method.areturn(InstructionAdapter.OBJECT_TYPE);
		}
	};
	public final String desc;

	private CollectionMethod(String desc) {
		this.desc = desc;
	}

	public MethodNode makeMethod() {
		MethodNode out = new MethodNode(Opcodes.ACC_PUBLIC, name(), desc, null, null);
		makeMethod(new InstructionAdapter(out));
		return out;
	}

	protected abstract void makeMethod(InstructionAdapter method);

	public static CollectionMethod forDesc(String name, String desc) {
		return MAP.get(name.concat(desc));
	}

	private static final Map<String, CollectionMethod> MAP;
	static {
		Map<String, CollectionMethod> map = new HashMap<>();

		for (CollectionMethod method : values()) {
			map.put(method.name().concat(method.desc), method);
		}

		MAP = Collections.unmodifiableMap(map);
	}
}