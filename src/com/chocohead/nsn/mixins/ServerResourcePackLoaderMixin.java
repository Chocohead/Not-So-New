package com.chocohead.nsn.mixins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipError;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;

import com.google.common.hash.Funnels;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.resource.ClientBuiltinResourcePackProvider;

@Mixin(ClientBuiltinResourcePackProvider.class)
abstract class ServerResourcePackLoaderMixin {
	@Unique
	private static String hashFile(File file) {
		Hasher hasher = Hashing.murmur3_128().newHasher();

		try (InputStream in = new FileInputStream(file)) {
			IOUtils.copy(in, Funnels.asOutputStream(hasher));
		} catch (IOException e) {
			throw new UncheckedIOException("Error calculating hash for " + file, e);
		}

		return hasher.hash().toString();
	}

	@Group(name = "fixDodgyZips", min = 1, max = 1)
	@ModifyVariable(method = "loadServerPack", at = @At("HEAD"), argsOnly = true)
	private File fixDodgyZips(File packZip) {
		boolean suspicious = false;

		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(packZip)) {
			for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
				ZipEntry entry = it.nextElement();

				if (entry.getCompressedSize() > 50 * 1024 * 1024) {//A compressed entry bigger than 50MB is suspicious
					suspicious = true;
					break;
				}
			}
		} catch (IOException | ZipError e) {
			//Never mind...
		}

		if (!suspicious) return packZip; //Nothing wrong with it
		File repackedZip = new File(packZip.getParentFile(), "repacked-" + packZip.getName());
		String hash = hashFile(packZip);

		if (repackedZip.exists()) {
			try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(repackedZip)) {
				if (hash.equals(zip.getComment())) return repackedZip; //All fine
			} catch (IOException | ZipError e) {
				repackedZip.delete(); //Must be something wrong with it
			}
		}

		try (ZipFile zip = new ZipFile(packZip);
				ZipOutputStream out = new ZipOutputStream(new FileOutputStream(repackedZip))) {
			out.setComment(hash);

			for (Enumeration<? extends ZipArchiveEntry> it = zip.getEntries(); it.hasMoreElements();) {
				ZipArchiveEntry entry = it.nextElement();

				out.putNextEntry(new ZipEntry(entry.getName())); //Don't copy the dodgy sizes
				IOUtils.copy(zip.getInputStream(entry), out);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Error repacking server resource pack in " + packZip, e);
		}

		return repackedZip;
	}

	@Group(name = "fixDodgyZips", min = 1, max = 1)
	@ModifyVariable(method = "method_55519", at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/class_9041$class_9043;comp_2155()Ljava/nio/file/Path;"))
	private Path fixDodgyZips(Path packZip) {
		return fixDodgyZips(packZip.toFile()).toPath();
	}
}