package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import androidx.core.content.pm.PackageInfoCompat;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.net.Download;
import com.github.catvod.utils.Crypto;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public record UpdatePackage(String version, String url, long size, String digest) {

    private static final Pattern SHA256 = Pattern.compile("sha256:[0-9a-fA-F]{64}");

    public UpdatePackage {
        if (version == null || version.isEmpty() || url == null || url.isEmpty() || size <= 0) throw new IllegalArgumentException("Invalid update asset");
        if (digest == null) digest = "";
        if (!digest.isEmpty() && !SHA256.matcher(digest).matches()) throw new IllegalArgumentException("Invalid update digest");
    }

    public void verifyFile(File file) throws IOException {
        if (!file.isFile() || file.length() != size) throw new IOException("Update size mismatch");
        if (digest.isEmpty()) throw new IOException("Update digest missing");
        byte[] expected = new byte[32];
        for (int i = 0; i < expected.length; i++) {
            int offset = 7 + i * 2;
            expected[i] = (byte) (Character.digit(digest.charAt(offset), 16) * 16 + Character.digit(digest.charAt(offset + 1), 16));
        }
        if (!MessageDigest.isEqual(expected, Crypto.sha256(file))) throw new IOException("Update checksum mismatch");
    }

    public File file(File directory) throws IOException {
        Files.createDirectories(directory.toPath());
        File target = new File(directory, "update-" + Crypto.sha256(url + "\n" + version + "\n" + size + "\n" + digest) + ".apk");
        File[] expired = directory.listFiles(file -> ApkCache.APP.matcher(file.getName()).matches() && Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS));
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        if (expired != null) for (File file : expired) if (!file.equals(target) && file.lastModified() < cutoff) Download.clearCache(file);
        return target;
    }

    public void verifyApk(Context context, File file) throws IOException {
        verifyFile(file);
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (info == null) throw new IOException("Invalid update APK");
        verifyPackage(info.packageName, info.versionName, PackageInfoCompat.getLongVersionCode(info));
        verifySignature(context, info);
    }

    private static void verifySignature(Context context, PackageInfo archive) throws IOException {
        try {
            PackageInfo host = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] expected = signers(host);
            Signature[] actual = signers(archive);
            if (expected.length == 0 || actual.length == 0) throw new IOException("Update signature missing");
            for (Signature signature : actual) if (!contains(expected, signature)) throw new IOException("Update signature mismatch");
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("Unable to read host signature", e);
        }
    }

    private static Signature[] signers(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return info.signingInfo == null ? new Signature[0] : info.signingInfo.getApkContentsSigners();
        return info.signatures == null ? new Signature[0] : info.signatures;
    }

    private static boolean contains(Signature[] list, Signature target) {
        for (Signature item : list) if (item.equals(target)) return true;
        return false;
    }

    public void verifyPackage(String packageName, String versionName, long versionCode) throws IOException {
        if (!BuildConfig.APPLICATION_ID.equals(packageName)) throw new IOException("Update package mismatch");
        if (!version.equals(versionName) || versionCode <= BuildConfig.VERSION_CODE) throw new IOException("Update version mismatch");
    }
}
