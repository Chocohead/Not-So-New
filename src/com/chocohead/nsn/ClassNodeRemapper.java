package com.chocohead.nsn;

import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.function.UnaryOperator;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.commons.ModuleHashesAttribute;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ModuleExportNode;
import org.objectweb.asm.tree.ModuleNode;
import org.objectweb.asm.tree.ModuleOpenNode;
import org.objectweb.asm.tree.ModuleProvideNode;
import org.objectweb.asm.tree.ModuleRequireNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.RecordComponentNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class ClassNodeRemapper {
	public static void remap(ClassNode node, Remapper remapper) {
		String originalName = node.name;

		//Class details
		node.name = remapper.mapType(node.name);
		node.signature = remapper.mapSignature(node.signature, false);
		node.superName = remapper.mapType(node.superName);
		mapTypes(node.interfaces, remapper);

		//Modules
		if (node.module != null) {
			ModuleNode module = node.module;
			module.name = remapper.mapModuleName(module.name);
			module.mainClass = remapper.mapType(module.mainClass);
			mapList(module.packages, remapper::mapPackageName);
			if (module.requires != null) {
				for (ModuleRequireNode require : module.requires) {
					require.module = remapper.mapModuleName(require.module);
				}
			}
			if (module.exports != null) {
				for (ModuleExportNode export : module.exports) {
					export.packaze = remapper.mapPackageName(export.packaze);
					mapList(export.modules, remapper::mapModuleName);
				}
			}
			if (module.opens != null) {
				for (ModuleOpenNode open : module.opens) {
					open.packaze = remapper.mapPackageName(open.packaze);
					mapList(open.modules, remapper::mapModuleName);
				}
			}
			mapTypes(module.uses, remapper);
			if (module.provides != null) {
				for (ModuleProvideNode provide : module.provides) {
					provide.service = remapper.mapType(provide.service);
					mapTypes(provide.providers, remapper);
				}
			}
		}

		//Class annotations
		remapAnnotations(node.visibleAnnotations, remapper);
		remapAnnotations(node.invisibleAnnotations, remapper);

		//Type annotations
		remapAnnotations(node.visibleTypeAnnotations, remapper);
		remapAnnotations(node.invisibleTypeAnnotations, remapper);

		//Attributes
		if (node.attrs != null) {
			for (Attribute attribute : node.attrs) {
				if (attribute instanceof ModuleHashesAttribute) {
					mapList(((ModuleHashesAttribute) attribute).modules, remapper::mapModuleName);
				}
			}
		}

		//Record components
		if (node.recordComponents != null) {
			for (RecordComponentNode component : node.recordComponents) {
				//Record component details
				component.name = remapper.mapRecordComponentName(originalName, component.name, component.descriptor);
				component.descriptor = remapper.mapDesc(component.descriptor);
				component.signature = remapper.mapSignature(component.signature, true);

				//Record component annotations
				remapAnnotations(component.visibleAnnotations, remapper);
				remapAnnotations(component.invisibleAnnotations, remapper);

				//Type annotations
				remapAnnotations(component.visibleTypeAnnotations, remapper);
				remapAnnotations(component.invisibleTypeAnnotations, remapper);
			}
		}

		//Fields
		for (FieldNode field : node.fields) {
			//Field details
			field.name = remapper.mapFieldName(originalName, field.name, field.desc);
			field.desc = remapper.mapDesc(field.desc);
			field.signature = remapper.mapSignature(field.signature, true);
			if (field.value != null) field.value = remapper.mapValue(field.value);

			//Field annotations
			remapAnnotations(field.visibleAnnotations, remapper);
			remapAnnotations(field.invisibleAnnotations, remapper);

			//Type annotations
			remapAnnotations(field.visibleTypeAnnotations, remapper);
			remapAnnotations(field.invisibleTypeAnnotations, remapper);
		}

		//Methods
		for (MethodNode method : node.methods) {
			//Method details
			method.name = remapper.mapMethodName(originalName, method.name, method.desc);
			method.desc = remapper.mapMethodDesc(method.desc);
			method.signature = remapper.mapSignature(method.signature, false);
			mapTypes(method.exceptions, remapper);

			method.annotationDefault = remapAnnotation(method.annotationDefault, remapper);

			//Method annotations
			remapAnnotations(method.visibleAnnotations, remapper);
			remapAnnotations(method.invisibleAnnotations, remapper);

			//Type annotations
			remapAnnotations(method.visibleTypeAnnotations, remapper);
			remapAnnotations(method.invisibleTypeAnnotations, remapper);

			//Parameter annotations
			remapAnnotations(method.visibleParameterAnnotations, remapper);
			remapAnnotations(method.invisibleParameterAnnotations, remapper);

			//Instructions
			for (AbstractInsnNode insn : method.instructions) {
				switch (insn.getType()) {
				case AbstractInsnNode.FRAME: {
					FrameNode frame = (FrameNode) insn;
					remapFrame(frame.local, remapper);
					remapFrame(frame.stack, remapper);
					break;
				}

				case AbstractInsnNode.FIELD_INSN: {
					FieldInsnNode field = (FieldInsnNode) insn;
					field.name = remapper.mapFieldName(field.owner, field.name, field.desc);
					field.desc = remapper.mapDesc(field.desc);
					field.owner = remapper.mapType(field.owner);
					break;
				}

				case AbstractInsnNode.METHOD_INSN: {
					MethodInsnNode minsn = (MethodInsnNode) insn;
					minsn.name = remapper.mapMethodName(minsn.owner, minsn.name, minsn.desc);
					minsn.desc = remapper.mapMethodDesc(minsn.desc);
					minsn.owner = remapper.mapType(minsn.owner);
					break;
				}

				case AbstractInsnNode.INVOKE_DYNAMIC_INSN: {
					InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) insn;
					idin.name = remapper.mapInvokeDynamicMethodName(idin.name, idin.desc);
					idin.desc = remapper.mapMethodDesc(idin.desc);
					idin.bsm = (Handle) remapper.mapValue(idin.bsm);
					for (int i = 0, end = idin.bsmArgs.length; i < end; i++) {
						idin.bsmArgs[i] = remapper.mapValue(idin.bsmArgs[i]);
					}
					break;
				}

				case AbstractInsnNode.TYPE_INSN: {
					TypeInsnNode tin = (TypeInsnNode) insn;
					tin.desc = remapper.mapType(tin.desc);
					break;
				}

				case AbstractInsnNode.LDC_INSN: {
					LdcInsnNode ldc = (LdcInsnNode) insn;
					ldc.cst = remapper.mapValue(ldc.cst);
					break;
				}

				case AbstractInsnNode.MULTIANEWARRAY_INSN: {
					MultiANewArrayInsnNode mnain = (MultiANewArrayInsnNode) insn;
					mnain.desc = remapper.mapDesc(mnain.desc);
					break;
				}
				}

				remapAnnotations(insn.visibleTypeAnnotations, remapper);
				remapAnnotations(insn.invisibleTypeAnnotations, remapper);
			}

			//Try-catches
			for (TryCatchBlockNode tryBlock : method.tryCatchBlocks) {
				tryBlock.type = remapper.mapType(tryBlock.type);

				remapAnnotations(tryBlock.visibleTypeAnnotations, remapper);
				remapAnnotations(tryBlock.invisibleTypeAnnotations, remapper);
			}

			//Local variables
			if (method.localVariables != null) {
				for (LocalVariableNode local : method.localVariables) {
					local.desc = remapper.mapDesc(local.desc);
					local.signature = remapper.mapSignature(local.signature, true);
				}
			}
			remapAnnotations(method.visibleLocalVariableAnnotations, remapper);
			remapAnnotations(method.invisibleLocalVariableAnnotations, remapper);
		}

		//Inner & outer classes
		for (InnerClassNode innerClass : node.innerClasses) {
			if (innerClass.innerName != null) {
				innerClass.innerName = remapper.mapInnerClassName(innerClass.name, innerClass.outerName, innerClass.innerName);
			}
			innerClass.name = remapper.mapType(innerClass.name);
			innerClass.outerName = remapper.mapType(innerClass.outerName);
		}

		//Nest host & members
		node.nestHostClass = remapper.mapType(node.nestHostClass);
		mapTypes(node.nestMembers, remapper);
		mapTypes(node.permittedSubclasses, remapper);
	}

	private static void mapTypes(List<String> types, Remapper remapper) {
		mapList(types, remapper::mapType);
	}

	private static <T> void mapList(List<T> things, UnaryOperator<T> remapper) {
		if (things == null) return;

		for (ListIterator<T> it = things.listIterator(); it.hasNext();) {
			it.set(remapper.apply(it.next()));
		}
	}

	private static <T extends AnnotationNode> void remapAnnotations(List<T>[] annotationLists, Remapper remapper) {
		if (annotationLists == null) return;

		for (List<T> annotations : annotationLists) {
			remapAnnotations(annotations, remapper);
		}
	}

	private static <T extends AnnotationNode> void remapAnnotations(List<T> annotations, Remapper remapper) {
		if (annotations == null) return;

		for (T annotation : annotations) {
			String originalDesc = annotation.desc;
			annotation.desc = remapper.mapDesc(annotation.desc);

			if (annotation.values != null) {
				for (ListIterator<Object> it = annotation.values.listIterator(); it.hasNext();) {
					String name = (String) it.next();
					if (originalDesc != null) {
						it.set(remapper.mapAnnotationAttributeName(originalDesc, name));
					}
					it.set(remapAnnotation(it.next(), remapper));
				}
			}
		}
	}

	private static Object remapAnnotation(Object value, Remapper remapper) {
		if (value instanceof String[]) {
			String[] enumPair = (String[]) value;
			enumPair[0] = remapper.mapDesc(enumPair[0]);
			return enumPair;
		} else if (value instanceof AnnotationNode) {
			AnnotationNode annotation = (AnnotationNode) value;
			remapAnnotations(Collections.singletonList(annotation), remapper);
			return annotation;
		} else if (value instanceof List<?>) {
			@SuppressWarnings("unchecked")
			List<Object> valueList = (List<Object>) value;
			mapList(valueList, listValue -> remapAnnotation(listValue, remapper));
			return valueList;
		} else {
			return remapper.mapValue(value);
		}
	}

	private static void remapFrame(List<Object> types, Remapper remapper) {
		if (types == null) return;

		for (ListIterator<Object> it = types.listIterator(); it.hasNext();) {
			Object type = it.next();
			if (type instanceof String) it.set(remapper.mapType((String) type));
		}
	}
}