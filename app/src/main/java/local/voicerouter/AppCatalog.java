package local.voicerouter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

final class AppCatalog {
    private static final String PREFS = "routing_settings";
    private static final String KEY_ENABLED = "enabled_packages";
    private static final String KEY_DEFAULT = "default_package";
    private static final String[] PREFERRED = {
            "org.smarttube.stable", "com.kinopub", "ru.yourok.num",
            "top.rootu.lampa", "org.courville.nova", "ru.kinopoisk.tv"
    };
    private static final Set<String> DEFAULT_ENABLED = new HashSet<>(
            Arrays.asList("org.smarttube.stable", "com.kinopub", "ru.yourok.num"));

    static final class Entry {
        final String packageName;
        final String label;

        Entry(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private AppCatalog() {}

    static boolean isEnabled(Context context, String packageName) {
        return enabledPackages(context).contains(packageName);
    }

    static void setEnabled(Context context, String packageName, boolean enabled) {
        Set<String> packages = enabledPackages(context);
        if (enabled) packages.add(packageName);
        else packages.remove(packageName);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().putStringSet(KEY_ENABLED, packages);
        if (!enabled && packageName.equals(prefs.getString(KEY_DEFAULT, null))) {
            editor.remove(KEY_DEFAULT);
        }
        editor.apply();
    }

    static String defaultPackage(Context context) {
        String pkg = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DEFAULT, null);
        return pkg != null && isEnabled(context, pkg) ? pkg : null;
    }

    static void setDefaultPackage(Context context, String packageName) {
        if (packageName == null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(KEY_DEFAULT).apply();
            return;
        }
        setEnabled(context, packageName, true);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_DEFAULT, packageName).apply();
    }

    private static Set<String> enabledPackages(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_ENABLED)) return new HashSet<>(DEFAULT_ENABLED);
        Set<String> stored = prefs.getStringSet(KEY_ENABLED, Collections.<String>emptySet());
        return stored == null ? new HashSet<String>() : new HashSet<>(stored);
    }

    static List<Entry> listTvApps(Context context) {
        final PackageManager pm = context.getPackageManager();
        LinkedHashMap<String, String> apps = new LinkedHashMap<>();
        for (String pkg : PREFERRED) addIfLaunchable(pm, apps, pkg);

        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> resolved = new ArrayList<>(pm.queryIntentActivities(leanback, 0));
        Collections.sort(resolved, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo left, ResolveInfo right) {
                return left.loadLabel(pm).toString().compareToIgnoreCase(
                        right.loadLabel(pm).toString());
            }
        });
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (context.getPackageName().equals(pkg) || apps.containsKey(pkg)
                    || "com.android.tv.settings".equals(pkg)
                    || "com.google.android.tvlauncher".equals(pkg)) continue;
            apps.put(pkg, info.loadLabel(pm).toString());
        }

        List<Entry> result = new ArrayList<>();
        for (String pkg : apps.keySet()) result.add(new Entry(pkg, apps.get(pkg)));
        return result;
    }

    private static void addIfLaunchable(PackageManager pm,
                                        LinkedHashMap<String, String> apps, String pkg) {
        try {
            if (pm.getLeanbackLaunchIntentForPackage(pkg) == null
                    && pm.getLaunchIntentForPackage(pkg) == null) return;
            apps.put(pkg,
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString());
        } catch (Exception ignored) {
            // Not installed.
        }
    }
}
