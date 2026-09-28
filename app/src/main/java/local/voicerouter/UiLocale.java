package local.voicerouter;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

final class UiLocale {
    static final String SYSTEM = "system";
    static final String RUSSIAN = "ru";
    static final String ENGLISH = "en";

    private static final String PREFS = "ui_settings";
    private static final String KEY_LANGUAGE = "language";

    private UiLocale() {}

    static String mode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, SYSTEM);
    }

    static void setMode(Context context, String mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LANGUAGE, mode).apply();
    }

    static Context wrap(Context context) {
        String mode = mode(context);
        if (SYSTEM.equals(mode)) return context;
        Locale locale = Locale.forLanguageTag(mode);
        Configuration configuration = new Configuration(
                context.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return context.createConfigurationContext(configuration);
    }

    static String text(Context context, int resourceId, Object... args) {
        Context localized = wrap(context);
        return args == null || args.length == 0
                ? localized.getString(resourceId)
                : localized.getString(resourceId, args);
    }
}
