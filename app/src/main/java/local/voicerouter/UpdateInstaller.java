package local.voicerouter;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.provider.Settings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

final class UpdateInstaller {
    interface Callback {
        void onReady(File file);
        void onFailure(String reason);
    }

    private UpdateInstaller() {}

    static void download(final Context context,
                         final UpdateChecker.UpdateInfo update,
                         final Callback callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                File file = UpdateFileProvider.file(context);
                try {
                    File directory = file.getParentFile();
                    if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) {
                        throw new Exception("Cannot create update directory");
                    }
                    if (file.exists() && !file.delete()) {
                        throw new Exception("Cannot replace old update");
                    }

                    HttpURLConnection connection = (HttpURLConnection)
                            new URL(update.downloadUrl).openConnection();
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try {
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(30000);
                        connection.setInstanceFollowRedirects(true);
                        connection.setRequestProperty("User-Agent",
                                "VoiceSearchRouter/" + UpdateChecker.currentVersion(context));
                        int status = connection.getResponseCode();
                        if (status < 200 || status >= 300) {
                            throw new Exception("Download HTTP " + status);
                        }

                        try (BufferedInputStream input = new BufferedInputStream(
                                     connection.getInputStream());
                             BufferedOutputStream output = new BufferedOutputStream(
                                     new FileOutputStream(file))) {
                            byte[] buffer = new byte[32768];
                            int count;
                            long total = 0L;
                            while ((count = input.read(buffer)) != -1) {
                                total += count;
                                if (total > 50L * 1024L * 1024L) {
                                    throw new Exception("APK is unexpectedly large");
                                }
                                output.write(buffer, 0, count);
                                digest.update(buffer, 0, count);
                            }
                        }
                    } finally {
                        connection.disconnect();
                    }

                    String actualDigest = hex(digest.digest());
                    if (update.sha256.length() > 0
                            && !update.sha256.equalsIgnoreCase(actualDigest)) {
                        file.delete();
                        throw new Exception("SHA-256 mismatch");
                    }
                    validatePackage(context, file);
                    callback.onReady(file);
                } catch (Exception error) {
                    file.delete();
                    callback.onFailure(error.getClass().getSimpleName()
                            + ": " + error.getMessage());
                }
            }
        }, "update-download").start();
    }

    static boolean isDownloaded(Context context) {
        return UpdateFileProvider.file(context).isFile();
    }

    static boolean install(Activity activity) {
        File file = UpdateFileProvider.file(activity);
        if (!file.isFile()) return false;

        PackageManager packages = activity.getPackageManager();
        if (!packages.canRequestPackageInstalls()) {
            Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            try {
                activity.startActivity(permission);
                return true;
            } catch (ActivityNotFoundException ignored) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    return true;
                } catch (ActivityNotFoundException unavailable) {
                    return false;
                }
            }
        }

        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(UpdateFileProvider.uri(activity),
                "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(install);
            return true;
        } catch (ActivityNotFoundException ignored) {
            return false;
        }
    }

    private static void validatePackage(Context context, File file) throws Exception {
        PackageManager packages = context.getPackageManager();
        PackageInfo archive = packages.getPackageArchiveInfo(file.getAbsolutePath(),
                PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed = packages.getPackageInfo(context.getPackageName(),
                PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null || !context.getPackageName().equals(archive.packageName)) {
            throw new Exception("Downloaded APK has the wrong package name");
        }
        if (archive.getLongVersionCode() <= installed.getLongVersionCode()) {
            throw new Exception("Downloaded APK is not newer");
        }

        Signature[] archiveSigners = archive.signingInfo == null ? null
                : archive.signingInfo.getApkContentsSigners();
        Signature[] installedSigners = installed.signingInfo == null ? null
                : installed.signingInfo.getApkContentsSigners();
        if (archiveSigners == null || installedSigners == null
                || archiveSigners.length != installedSigners.length) {
            throw new Exception("APK signature is unavailable");
        }
        for (Signature installedSigner : installedSigners) {
            boolean found = false;
            for (Signature archiveSigner : archiveSigners) {
                if (installedSigner.equals(archiveSigner)) {
                    found = true;
                    break;
                }
            }
            if (!found) throw new Exception("APK signature does not match");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }
}
