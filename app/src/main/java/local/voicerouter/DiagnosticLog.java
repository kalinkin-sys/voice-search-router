package local.voicerouter;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class DiagnosticLog {
    private static final String PREFS = "diagnostic_log";
    private static final String KEY_EVENTS = "events";
    private static final int MAX_CHARS = 24_000;

    private DiagnosticLog() {}

    static synchronized void add(Context context, String event) {
        String line = timestamp() + "  " + clean(event) + "\n";
        String previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_EVENTS, "");
        String combined = previous + line;
        if (combined.length() > MAX_CHARS) {
            int start = combined.length() - MAX_CHARS;
            int nextLine = combined.indexOf('\n', start);
            combined = combined.substring(nextLine >= 0 ? nextLine + 1 : start);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_EVENTS, combined).apply();
    }

    static String report(Context context) {
        StringBuilder out = new StringBuilder();
        out.append("Voice Search Router diagnostics\n");
        out.append("Created: ").append(timestamp()).append("\n\n");
        out.append("Router: ").append(packageVersion(context, context.getPackageName()))
                .append("\n");
        out.append("Device: ").append(clean(Build.MANUFACTURER)).append(' ')
                .append(clean(Build.MODEL)).append("\n");
        out.append("Product: ").append(clean(Build.PRODUCT)).append(" / ")
                .append(clean(Build.DEVICE)).append("\n");
        out.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("UI language: ").append(UiLocale.mode(context)).append("\n");
        out.append("Accessibility service: ")
                .append(isServiceEnabled(context) ? "enabled" : "disabled").append("\n");

        String defaultPackage = AppCatalog.defaultPackage(context);
        out.append("Default route: ")
                .append(defaultPackage == null ? "Android TV system search" : defaultPackage)
                .append("\n");
        out.append("Enabled routes:\n");
        boolean any = false;
        List<AppCatalog.Entry> apps = AppCatalog.listTvApps(context);
        for (AppCatalog.Entry app : apps) {
            if (!AppCatalog.isEnabled(context, app.packageName)) continue;
            any = true;
            out.append("- ").append(clean(app.label)).append(" (")
                    .append(app.packageName).append(") ")
                    .append(packageVersion(context, app.packageName)).append("\n");
        }
        if (!any) out.append("- none\n");

        out.append("\nRecent Router events (recognized phrases are not stored):\n");
        String events = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_EVENTS, "");
        out.append(events == null || events.length() == 0 ? "- no events yet\n" : events);
        return out.toString();
    }

    private static boolean isServiceEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName component = new ComponentName(context, VoiceRouterService.class);
        return enabled.contains(component.flattenToString())
                || enabled.contains(component.flattenToShortString());
    }

    private static String packageVersion(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            long code = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            return (info.versionName == null ? "unknown" : info.versionName)
                    + " (" + code + ")";
        } catch (PackageManager.NameNotFoundException ignored) {
            return "version unavailable";
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
                .format(new Date());
    }

    private static String clean(String value) {
        if (value == null) return "";
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() > 220 ? cleaned.substring(0, 220) : cleaned;
    }
}
