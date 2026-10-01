package local.voicerouter;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UpdateChecker {
    static final long CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private static final String RELEASE_API =
            "https://api.github.com/repos/kalinkin-sys/voice-search-router/releases/latest";
    private static final String APK_NAME = "VoiceSearchRouter.apk";
    private static final String PREFS = "updates";
    private static final String KEY_AUTOMATIC = "automatic";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_VERSION = "available_version";
    private static final String KEY_URL = "available_url";
    private static final String KEY_DIGEST = "available_digest";
    private static final String KEY_NOTES = "available_notes";
    private static final String KEY_NOTIFIED = "notified_version";
    private static final String KEY_NOTIFICATION_PROMPTED = "notification_prompted";

    private UpdateChecker() {}

    static final class UpdateInfo {
        final String version;
        final String downloadUrl;
        final String sha256;
        final String notes;

        UpdateInfo(String version, String downloadUrl, String sha256, String notes) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
            this.notes = notes;
        }
    }

    static final class Result {
        final UpdateInfo update;
        final boolean upToDate;
        final String error;

        private Result(UpdateInfo update, boolean upToDate, String error) {
            this.update = update;
            this.upToDate = upToDate;
            this.error = error;
        }

        static Result available(UpdateInfo info) {
            return new Result(info, false, null);
        }

        static Result current() {
            return new Result(null, true, null);
        }

        static Result failed(String error) {
            return new Result(null, false, error);
        }
    }

    static Result check(Context context) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent",
                    "VoiceSearchRouter/" + currentVersion(context));

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                return Result.failed("GitHub HTTP " + status);
            }

            JSONObject release = new JSONObject(readAll(connection.getInputStream()));
            String version = normalizeVersion(release.optString("tag_name", ""));
            if (version.length() == 0) return Result.failed("Release has no version");

            JSONArray assets = release.optJSONArray("assets");
            JSONObject apk = null;
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject candidate = assets.optJSONObject(i);
                    if (candidate != null && APK_NAME.equals(candidate.optString("name"))) {
                        apk = candidate;
                        break;
                    }
                }
            }
            if (apk == null) return Result.failed(APK_NAME + " is missing from the release");

            String downloadUrl = apk.optString("browser_download_url", "");
            if (!downloadUrl.startsWith("https://")) {
                return Result.failed("Release download URL is invalid");
            }
            String digest = apk.optString("digest", "");
            if (digest.startsWith("sha256:")) digest = digest.substring(7);
            if (!digest.matches("[0-9a-fA-F]{64}")) digest = "";

            String notes = release.optString("body", "");
            if (notes.length() > 4000) notes = notes.substring(0, 4000);
            UpdateInfo info = new UpdateInfo(version, downloadUrl,
                    digest.toLowerCase(), notes);

            preferences(context).edit().putLong(KEY_LAST_CHECK,
                    System.currentTimeMillis()).apply();
            if (compareVersions(version, currentVersion(context)) > 0) {
                saveAvailable(context, info);
                return Result.available(info);
            }

            clearAvailable(context);
            return Result.current();
        } catch (Exception error) {
            return Result.failed(error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String currentVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            return info.versionName == null ? "0" : info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "0";
        }
    }

    static boolean automatic(Context context) {
        return preferences(context).getBoolean(KEY_AUTOMATIC, true);
    }

    static void setAutomatic(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_AUTOMATIC, enabled).apply();
    }

    static long lastCheck(Context context) {
        return preferences(context).getLong(KEY_LAST_CHECK, 0L);
    }

    static UpdateInfo savedUpdate(Context context) {
        SharedPreferences prefs = preferences(context);
        String version = prefs.getString(KEY_VERSION, "");
        String url = prefs.getString(KEY_URL, "");
        if (version == null || url == null || version.length() == 0
                || url.length() == 0
                || compareVersions(version, currentVersion(context)) <= 0) {
            return null;
        }
        return new UpdateInfo(version, url,
                value(prefs.getString(KEY_DIGEST, "")),
                value(prefs.getString(KEY_NOTES, "")));
    }

    static String notifiedVersion(Context context) {
        return value(preferences(context).getString(KEY_NOTIFIED, ""));
    }

    static void setNotifiedVersion(Context context, String version) {
        preferences(context).edit().putString(KEY_NOTIFIED, version).apply();
    }

    static boolean notificationPrompted(Context context) {
        return preferences(context).getBoolean(KEY_NOTIFICATION_PROMPTED, false);
    }

    static void setNotificationPrompted(Context context) {
        preferences(context).edit().putBoolean(KEY_NOTIFICATION_PROMPTED, true).apply();
    }

    static int compareVersions(String left, String right) {
        int[] a = numericParts(normalizeVersion(left));
        int[] b = numericParts(normalizeVersion(right));
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return 0;
    }

    private static void saveAvailable(Context context, UpdateInfo info) {
        SharedPreferences prefs = preferences(context);
        String previous = value(prefs.getString(KEY_VERSION, ""));
        if (!info.version.equals(previous)) UpdateFileProvider.file(context).delete();
        prefs.edit()
                .putString(KEY_VERSION, info.version)
                .putString(KEY_URL, info.downloadUrl)
                .putString(KEY_DIGEST, info.sha256)
                .putString(KEY_NOTES, info.notes)
                .apply();
    }

    private static void clearAvailable(Context context) {
        UpdateFileProvider.file(context).delete();
        preferences(context).edit()
                .remove(KEY_VERSION)
                .remove(KEY_URL)
                .remove(KEY_DIGEST)
                .remove(KEY_NOTES)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalizeVersion(String version) {
        if (version == null) return "";
        version = version.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        return version;
    }

    private static int[] numericParts(String version) {
        String[] raw = version.split("\\.");
        int[] result = new int[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String part = raw[i].replaceFirst("[^0-9].*$", "");
            try {
                result[i] = part.length() == 0 ? 0 : Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }

    private static String readAll(InputStream input) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) result.append(line).append('\n');
        return result.toString();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
