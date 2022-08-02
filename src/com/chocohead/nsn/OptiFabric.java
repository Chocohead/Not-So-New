package com.chocohead.nsn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Enumeration;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipError;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.chocohead.nsn.Nester.ScanResult;

public class OptiFabric implements UnaryOperator<File> {
	@Override
	public File apply(File jar) {
		ScanResult result = Nester.run(jar.toPath());
		Set<String> toTransform = result.getMixinTargets();
		if (toTransform.isEmpty()) return jar; //Nothing to do

		File transformed;
		try (ZipFile in = new ZipFile(jar); //Sneaky way of catching an IOException from File#createTempFile in the main catch
				ZipOutputStream out = new ZipOutputStream(new FileOutputStream(transformed = File.createTempFile("not-so-optifabric", ".jar")))) {
			for (Enumeration<? extends ZipEntry> it = in.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();

				try (InputStream data = in.getInputStream(entry)) {
					if (toTransform.contains(FilenameUtils.removeExtension(entry.getName()))) {
						ClassReader reader = new ClassReader(data);
						ClassNode node = new ClassNode();
						reader.accept(node, 0);

						BulkRemapper.transform(node);
						result.applyNestTransform(node);

						ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
						node.accept(writer);
						out.putNextEntry(new ZipEntry(entry.getName()));
						IOUtils.write(writer.toByteArray(), out);
					} else {
						out.putNextEntry(new ZipEntry(entry));
						IOUtils.copy(data, out);
					}
				}
			}
		} catch (ZipException | ZipError e) {
			throw new IllegalArgumentException("Crashed trying to transform " + jar, e);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to transform " + jar, e);
		}

		return transformed;
	}
}