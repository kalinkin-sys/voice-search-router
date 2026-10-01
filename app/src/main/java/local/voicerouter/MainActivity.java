package local.voicerouter;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.LinkedHashMap;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String SETTINGS_STUB = "com.google.android.tv.frameworkpackagestubs";

    private final Map<String, RadioButton> defaultRadios = new LinkedHashMap<>();
    private final Map<String, CheckBox> enabledChecks = new LinkedHashMap<>();
    private RadioButton systemDefault;
    private TextView serviceStatus;
    private Button openSettingsButton;
    private TextView updateStatus;
    private Button checkUpdatesButton;
    private Button installUpdateButton;
    private UpdateChecker.UpdateInfo availableUpdate;
    private boolean updateDownloadReady;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(UiLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(80, 50, 80, 60);
        root.setBackgroundColor(Color.rgb(16, 20, 24));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text(getString(R.string.app_name), 32, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        serviceStatus = text("", 19, Color.WHITE);
        serviceStatus.setGravity(Gravity.CENTER);
        serviceStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        serviceStatus.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, 22, 0, 4);
        root.addView(serviceStatus, statusParams);

        TextView body = text(getString(R.string.app_intro), 19, Color.LTGRAY);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.setMargins(0, 26, 0, 28);
        root.addView(body, bodyParams);

        openSettingsButton = new Button(this);
        openSettingsButton.setText(getString(R.string.open_settings));
        openSettingsButton.setTextSize(18);
        openSettingsButton.setTextColor(Color.WHITE);
        openSettingsButton.setBackground(makeSettingsButtonBackground());
        openSettingsButton.setPadding(24, 18, 24, 18);
        openSettingsButton.setFocusable(true);
        openSettingsButton.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                view.animate().scaleX(focused ? 1.025f : 1f)
                        .scaleY(focused ? 1.025f : 1f).setDuration(120L).start();
            }
        });
        openSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openAccessibilitySettings(); }
        });
        root.addView(openSettingsButton, new LinearLayout.LayoutParams(-1, -2));
        updateServiceStatus();

        addLanguageSelector(root);

        TextView section = text(getString(R.string.routing_title), 24, Color.WHITE);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.setMargins(0, 34, 0, 8);
        root.addView(section, sectionParams);

        TextView hint = text(getString(R.string.routing_hint), 17, Color.LTGRAY);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.setMargins(0, 0, 0, 14);
        root.addView(hint, hintParams);

        addSupportSection(root);

        root.addView(makeHeaderRow(), new LinearLayout.LayoutParams(-1, -2));
        root.addView(makeSystemRow(), new LinearLayout.LayoutParams(-1, -2));

        final List<AppCatalog.Entry> apps = AppCatalog.listTvApps(this);
        String defaultPackage = AppCatalog.defaultPackage(this);
        systemDefault.setChecked(defaultPackage == null);
        for (final AppCatalog.Entry app : apps) {
            root.addView(makeAppRow(app, app.packageName.equals(defaultPackage)),
                    new LinearLayout.LayoutParams(-1, -2));
        }

        addUpdateSection(root);
        addAuthorLine(root);

        setContentView(scroll);
        UpdateScheduler.sync(this);
        showSavedUpdate();
        maybeCheckForUpdates();
        maybeRequestNotificationPermission();
        if (openSettingsButton.getVisibility() == View.VISIBLE) {
            openSettingsButton.requestFocus();
        } else {
            systemDefault.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceStatus();
        if (availableUpdate != null && UpdateInstaller.isDownloaded(this)) {
            updateDownloadReady = true;
            updateStatus.setText(getString(R.string.update_ready_to_install,
                    availableUpdate.version));
            installUpdateButton.setText(getString(R.string.install_update));
            installUpdateButton.setVisibility(View.VISIBLE);
        }
    }

    private void updateServiceStatus() {
        if (serviceStatus == null) return;
        boolean enabled = isRouterServiceEnabled();
        serviceStatus.setText(enabled
                ? getString(R.string.service_status_enabled)
                : getString(R.string.service_status_disabled));
        serviceStatus.setContentDescription(getString(enabled
                ? R.string.service_status_enabled
                : R.string.service_status_disabled));
        serviceStatus.setBackground(statusBackground(enabled));
        if (openSettingsButton != null) {
            openSettingsButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
    }

    private boolean isRouterServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName component = new ComponentName(this, VoiceRouterService.class);
        return enabled.contains(component.flattenToString())
                || enabled.contains(component.flattenToShortString());
    }

    private GradientDrawable statusBackground(boolean enabled) {
        return roundedButton(
                enabled ? Color.rgb(28, 92, 52) : Color.rgb(132, 45, 45),
                enabled ? Color.rgb(86, 210, 125) : Color.rgb(255, 130, 130),
                3);
    }

    private void addLanguageSelector(LinearLayout root) {
        TextView languageTitle = text(getString(R.string.language_title), 22, Color.WHITE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.setMargins(0, 26, 0, 6);
        root.addView(languageTitle, titleParams);

        final Map<Integer, String> modes = new LinkedHashMap<>();
        RadioGroup languages = new RadioGroup(this);
        languages.setOrientation(LinearLayout.HORIZONTAL);
        String current = UiLocale.mode(this);
        addLanguageRadio(languages, modes, getString(R.string.language_system),
                UiLocale.SYSTEM, current);
        addLanguageRadio(languages, modes, getString(R.string.language_russian),
                UiLocale.RUSSIAN, current);
        addLanguageRadio(languages, modes, getString(R.string.language_english),
                UiLocale.ENGLISH, current);
        languages.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup group, int checkedId) {
                String mode = modes.get(checkedId);
                if (mode != null && !mode.equals(UiLocale.mode(MainActivity.this))) {
                    UiLocale.setMode(MainActivity.this, mode);
                    DiagnosticLog.add(MainActivity.this, "UI language changed to " + mode);
                    recreate();
                }
            }
        });
        root.addView(languages, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addLanguageRadio(RadioGroup group, Map<Integer, String> modes,
                                  String label, String mode, String current) {
        RadioButton radio = new RadioButton(this);
        radio.setId(View.generateViewId());
        radio.setText(label);
        radio.setTextColor(Color.WHITE);
        radio.setTextSize(18);
        radio.setFocusable(true);
        radio.setChecked(mode.equals(current));
        modes.put(radio.getId(), mode);
        group.addView(radio, new RadioGroup.LayoutParams(0, -2, 1f));
    }

    private void addUpdateSection(LinearLayout root) {
        TextView title = text(getString(R.string.updates_title), 24, Color.WHITE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.setMargins(0, 34, 0, 8);
        root.addView(title, titleParams);

        TextView current = text(getString(R.string.current_version,
                UpdateChecker.currentVersion(this)), 17, Color.LTGRAY);
        root.addView(current, new LinearLayout.LayoutParams(-1, -2));

        final CheckBox automatic = new CheckBox(this);
        automatic.setText(getString(R.string.automatic_updates));
        automatic.setTextColor(Color.WHITE);
        automatic.setTextSize(18);
        automatic.setFocusable(true);
        automatic.setChecked(UpdateChecker.automatic(this));
        automatic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                UpdateChecker.setAutomatic(MainActivity.this, checked);
                UpdateScheduler.sync(MainActivity.this);
                if (checked) {
                    maybeRequestNotificationPermission();
                    checkForUpdates(false);
                }
            }
        });
        root.addView(automatic, new LinearLayout.LayoutParams(-1, -2));

        checkUpdatesButton = actionButton(getString(R.string.check_for_updates));
        checkUpdatesButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { checkForUpdates(true); }
        });
        root.addView(checkUpdatesButton, new LinearLayout.LayoutParams(-1, -2));

        updateStatus = text(getString(R.string.update_never_checked), 16, Color.LTGRAY);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, 8, 0, 8);
        root.addView(updateStatus, statusParams);

        installUpdateButton = actionButton(getString(R.string.download_and_install));
        installUpdateButton.setVisibility(View.GONE);
        installUpdateButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (updateDownloadReady && UpdateInstaller.isDownloaded(MainActivity.this)) {
                    if (!UpdateInstaller.install(MainActivity.this)) {
                        updateStatus.setText(getString(R.string.update_installer_unavailable));
                    }
                } else {
                    downloadUpdate();
                }
            }
        });
        root.addView(installUpdateButton, new LinearLayout.LayoutParams(-1, -2));
    }

    private void maybeCheckForUpdates() {
        if (!UpdateChecker.automatic(this)) return;
        long age = System.currentTimeMillis() - UpdateChecker.lastCheck(this);
        if (age >= UpdateChecker.CHECK_INTERVAL_MS) checkForUpdates(false);
    }

    private void checkForUpdates(final boolean manual) {
        if (checkUpdatesButton == null || !checkUpdatesButton.isEnabled()) return;
        checkUpdatesButton.setEnabled(false);
        updateStatus.setText(getString(R.string.checking_for_updates));
        new Thread(new Runnable() {
            @Override public void run() {
                final UpdateChecker.Result result = UpdateChecker.check(
                        getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        checkUpdatesButton.setEnabled(true);
                        if (result.update != null) {
                            showAvailableUpdate(result.update);
                        } else if (result.upToDate) {
                            availableUpdate = null;
                            updateDownloadReady = false;
                            installUpdateButton.setVisibility(View.GONE);
                            updateStatus.setText(getString(R.string.update_up_to_date,
                                    UpdateChecker.currentVersion(MainActivity.this)));
                        } else if (manual) {
                            updateStatus.setText(getString(R.string.update_check_failed));
                            DiagnosticLog.add(MainActivity.this,
                                    "Update check failed: " + result.error);
                        } else {
                            showLastChecked();
                        }
                    }
                });
            }
        }, "manual-update-check").start();
    }

    private void showSavedUpdate() {
        UpdateChecker.UpdateInfo saved = UpdateChecker.savedUpdate(this);
        if (saved != null) showAvailableUpdate(saved);
        else showLastChecked();
    }

    private void showAvailableUpdate(UpdateChecker.UpdateInfo update) {
        availableUpdate = update;
        updateDownloadReady = UpdateInstaller.isDownloaded(this);
        updateStatus.setText(updateDownloadReady
                ? getString(R.string.update_ready_to_install, update.version)
                : getString(R.string.update_available, update.version));
        installUpdateButton.setText(getString(updateDownloadReady
                ? R.string.install_update : R.string.download_and_install));
        installUpdateButton.setVisibility(View.VISIBLE);
    }

    private void showLastChecked() {
        long checked = UpdateChecker.lastCheck(this);
        if (checked == 0L) {
            updateStatus.setText(getString(R.string.update_never_checked));
            return;
        }
        String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(checked));
        updateStatus.setText(getString(R.string.update_last_checked, date));
    }

    private void downloadUpdate() {
        if (availableUpdate == null) return;
        checkUpdatesButton.setEnabled(false);
        installUpdateButton.setEnabled(false);
        updateStatus.setText(getString(R.string.update_downloading,
                availableUpdate.version));
        UpdateInstaller.download(getApplicationContext(), availableUpdate,
                new UpdateInstaller.Callback() {
                    @Override public void onReady(File file) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                checkUpdatesButton.setEnabled(true);
                                installUpdateButton.setEnabled(true);
                                updateDownloadReady = true;
                                installUpdateButton.setText(getString(R.string.install_update));
                                updateStatus.setText(getString(R.string.update_ready_to_install,
                                        availableUpdate.version));
                                if (!UpdateInstaller.install(MainActivity.this)) {
                                    updateStatus.setText(getString(
                                            R.string.update_installer_unavailable));
                                }
                            }
                        });
                    }

                    @Override public void onFailure(final String reason) {
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                checkUpdatesButton.setEnabled(true);
                                installUpdateButton.setEnabled(true);
                                updateStatus.setText(getString(R.string.update_download_failed));
                                DiagnosticLog.add(MainActivity.this,
                                        "Update download failed: " + reason);
                            }
                        });
                    }
                });
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || !UpdateChecker.automatic(this)
                || UpdateChecker.notificationPrompted(this)
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        UpdateChecker.setNotificationPrompted(this);
        requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1001);
    }

    private LinearLayout makeHeaderRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(20, 10, 20, 10);
        row.addView(text(getString(R.string.column_application), 17, Color.LTGRAY), columnParams(1.35f));
        TextView defaultHeader = text(getString(R.string.column_default), 17, Color.LTGRAY);
        defaultHeader.setGravity(Gravity.CENTER);
        row.addView(defaultHeader, columnParams(0.85f));
        TextView enabledHeader = text(getString(R.string.column_active), 17, Color.LTGRAY);
        enabledHeader.setGravity(Gravity.CENTER);
        row.addView(enabledHeader, columnParams(0.80f));
        return row;
    }

    private LinearLayout makeSystemRow() {
        LinearLayout row = baseRow();
        row.addView(appLabel(getString(R.string.system_search)), columnParams(1.35f));
        systemDefault = makeRadio();
        systemDefault.setContentDescription(getString(R.string.use_system_default));
        systemDefault.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { selectDefault(null); }
        });
        row.addView(centered(systemDefault), columnParams(0.85f));
        TextView unavailable = text("—", 22, Color.GRAY);
        unavailable.setGravity(Gravity.CENTER);
        row.addView(unavailable, columnParams(0.80f));
        return row;
    }

    private LinearLayout makeAppRow(final AppCatalog.Entry app, boolean isDefault) {
        LinearLayout row = baseRow();
        row.addView(appLabel(app.label), columnParams(1.35f));

        RadioButton radio = makeRadio();
        radio.setChecked(isDefault);
        radio.setContentDescription(getString(R.string.default_for, app.label));
        radio.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { selectDefault(app.packageName); }
        });
        defaultRadios.put(app.packageName, radio);
        row.addView(centered(radio), columnParams(0.85f));

        CheckBox check = new CheckBox(this);
        check.setFocusable(true);
        check.setGravity(Gravity.CENTER);
        check.setChecked(AppCatalog.isEnabled(this, app.packageName));
        check.setContentDescription(getString(R.string.active_for, app.label));
        enabledChecks.put(app.packageName, check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                boolean wasDefault = app.packageName.equals(
                        AppCatalog.defaultPackage(MainActivity.this));
                AppCatalog.setEnabled(MainActivity.this, app.packageName, checked);
                DiagnosticLog.add(MainActivity.this, "Route " + app.packageName
                        + " enabled=" + checked);
                if (!checked && wasDefault) selectDefault(null);
            }
        });
        row.addView(centered(check), columnParams(0.80f));
        return row;
    }

    private void selectDefault(String packageName) {
        if (packageName != null) {
            CheckBox check = enabledChecks.get(packageName);
            if (check != null && !check.isChecked()) check.setChecked(true);
        }
        AppCatalog.setDefaultPackage(this, packageName);
        DiagnosticLog.add(this, "Default route="
                + (packageName == null ? "system" : packageName));
        systemDefault.setChecked(packageName == null);
        for (Map.Entry<String, RadioButton> entry : defaultRadios.entrySet()) {
            entry.getValue().setChecked(entry.getKey().equals(packageName));
        }
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(20, 4, 20, 4);
        return row;
    }

    private TextView appLabel(String value) {
        TextView label = text(value, 19, Color.WHITE);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private RadioButton makeRadio() {
        RadioButton radio = new RadioButton(this);
        radio.setId(View.generateViewId());
        radio.setFocusable(true);
        radio.setGravity(Gravity.CENTER);
        return radio;
    }

    private LinearLayout centered(View child) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.addView(child, new LinearLayout.LayoutParams(-2, -2));
        return wrapper;
    }

    private LinearLayout.LayoutParams columnParams(float weight) {
        return new LinearLayout.LayoutParams(0, -2, weight);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private StateListDrawable makeSettingsButtonBackground() {
        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[] {android.R.attr.state_pressed},
                roundedButton(Color.rgb(0, 101, 170), Color.rgb(120, 235, 255), 4));
        selector.addState(new int[] {android.R.attr.state_focused},
                roundedButton(Color.rgb(0, 125, 210), Color.rgb(120, 235, 255), 4));
        selector.addState(new int[0],
                roundedButton(Color.rgb(55, 65, 75), Color.rgb(105, 120, 135), 2));
        return selector;
    }

    private void addSupportSection(LinearLayout root) {
        TextView title = text(getString(R.string.support_title), 24, Color.WHITE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.setMargins(0, 38, 0, 8);
        root.addView(title, titleParams);

        TextView description = text(getString(R.string.support_description), 17, Color.LTGRAY);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.setMargins(0, 0, 0, 14);
        root.addView(description, descriptionParams);

        Button diagnostics = actionButton(getString(R.string.send_diagnostics));
        diagnostics.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { sendDiagnostics((Button) view); }
        });
        root.addView(diagnostics, new LinearLayout.LayoutParams(-1, -2));

        TextView privacy = text(getString(R.string.diagnostics_privacy), 15, Color.GRAY);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(-1, -2);
        privacyParams.setMargins(0, 8, 0, 16);
        root.addView(privacy, privacyParams);

    }

    private void addAuthorLine(LinearLayout root) {
        TextView author = text(getString(R.string.author), 18, Color.LTGRAY);
        String authorText = getString(R.string.author);
        SpannableString authorLink = new SpannableString(authorText);
        int linkStart = authorText.indexOf("KALINKIN");
        if (linkStart >= 0) {
            int linkEnd = linkStart + "KALINKIN".length();
            authorLink.setSpan(new ForegroundColorSpan(Color.rgb(120, 235, 255)),
                    linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            authorLink.setSpan(new UnderlineSpan(), linkStart, linkEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        author.setText(authorLink);
        author.setGravity(Gravity.CENTER);
        author.setPadding(20, 18, 20, 18);
        author.setFocusable(true);
        author.setClickable(true);
        author.setContentDescription(getString(R.string.open_telegram));
        author.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openTelegram(); }
        });
        author.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                view.animate().scaleX(focused ? 1.025f : 1f)
                        .scaleY(focused ? 1.025f : 1f).setDuration(120L).start();
            }
        });
        root.addView(author, new LinearLayout.LayoutParams(-1, -2));
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setBackground(makeSettingsButtonBackground());
        button.setPadding(24, 18, 24, 18);
        button.setFocusable(true);
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                view.animate().scaleX(focused ? 1.025f : 1f)
                        .scaleY(focused ? 1.025f : 1f).setDuration(120L).start();
            }
        });
        return button;
    }

    private void openTelegram() {
        Intent telegram = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://t.me/KALINKIN"));
        try {
            startActivity(telegram);
        } catch (ActivityNotFoundException ignored) {
            copyToClipboard("@KALINKIN");
            Toast.makeText(this, getString(R.string.telegram_copied),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void sendDiagnostics(final Button button) {
        DiagnosticLog.add(this, "Diagnostic report requested");
        final String report = DiagnosticLog.report(this);
        button.setEnabled(false);
        button.setText(getString(R.string.diagnostics_sending));
        new Thread(new Runnable() {
            @Override public void run() {
                final DiagnosticsSender.Result result = DiagnosticsSender.send(
                        getApplicationContext(), report);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        button.setEnabled(true);
                        button.setText(getString(R.string.send_diagnostics));
                        int message;
                        if (result.success) {
                            message = R.string.diagnostics_sent;
                            DiagnosticLog.add(MainActivity.this,
                                    "Diagnostic report sent; status=" + result.statusCode);
                        } else if (result.notConfigured) {
                            message = R.string.diagnostics_not_configured;
                            DiagnosticLog.add(MainActivity.this,
                                    "Diagnostic endpoint is not configured");
                        } else {
                            message = R.string.diagnostics_failed;
                            DiagnosticLog.add(MainActivity.this,
                                    "Diagnostic report failed; status=" + result.statusCode
                                            + "; reason=" + result.message);
                        }
                        Toast.makeText(MainActivity.this, getString(message),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "diagnostics-sender").start();
    }

    private void copyToClipboard(String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Voice Search Router", value));
        }
    }

    private GradientDrawable roundedButton(int fill, int stroke, int strokeWidth) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(10f);
        background.setStroke(strokeWidth, stroke);
        return background;
    }

    private void openAccessibilitySettings() {
        Intent direct = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (hasDirectAccessibilitySettings()) {
            try {
                startActivity(direct);
                return;
            } catch (ActivityNotFoundException ignored) {
                // Fall through to the Android TV settings root.
            }
        }

        Intent tvSettings = new Intent(Intent.ACTION_MAIN);
        tvSettings.setComponent(new ComponentName(
                "com.android.tv.settings", "com.android.tv.settings.MainSettings"));
        try {
            Toast.makeText(this,
                    getString(R.string.shield_path_toast),
                    Toast.LENGTH_LONG).show();
            startActivity(tvSettings);
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this,
                    getString(R.string.accessibility_unavailable),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasDirectAccessibilitySettings() {
        ResolveInfo handler = getPackageManager().resolveActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                PackageManager.MATCH_DEFAULT_ONLY);
        return handler != null && handler.activityInfo != null
                && !SETTINGS_STUB.equals(handler.activityInfo.packageName);
    }
}
