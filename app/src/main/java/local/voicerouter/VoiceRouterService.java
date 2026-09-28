package local.voicerouter;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VoiceRouterService extends AccessibilityService {
    private static final String TAG = "VoiceRouter";
    private static final String KATNISS = "com.google.android.katniss";
    private static final String TV_LAUNCHER = "com.google.android.tvlauncher";
    private static final String TV_SETTINGS = "com.android.tv.settings";
    private static final String SMARTTUBE = "org.smarttube.stable";
    private static final String KINOPUB = "com.kinopub";
    private static final String NUM = "ru.yourok.num";
    private static final String OWN_PACKAGE = "local.voicerouter";
    private static final Set<String> NEVER_ROUTE = new HashSet<>(Arrays.asList(
            OWN_PACKAGE, KATNISS, TV_SETTINGS));
    private static final Set<String> IGNORE_SOURCE = new HashSet<>(Arrays.asList(
            "com.android.systemui", "com.google.android.inputmethod.latin",
            "com.google.android.tv.frameworkpackagestubs"));
    private static final long MIN_RECOGNITION_MS = 900L;
    private static final long QUEUED_ROUTE_TTL_MS = 60_000L;
    private static final String PREFS = "queued_route";
    private static final String PREF_PACKAGE = "package";
    private static final String PREF_QUERY = "query";
    private static final String PREF_TIME = "time";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastSourcePackage;
    private boolean lastSourceRoutable;
    private String pendingTarget;
    private boolean pendingLaunchTarget;
    private long pendingSince;
    private boolean routing;

    public static void queueExternalRoute(Context context, String packageName, String query) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_PACKAGE, packageName)
                .putString(PREF_QUERY, query)
                .putLong(PREF_TIME, System.currentTimeMillis())
                .apply();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Accessibility service connected");
        DiagnosticLog.add(this, "Accessibility service connected");
        Toast.makeText(this, UiLocale.text(this, R.string.service_enabled),
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        if (KATNISS.equals(pkg)) {
            handleAssistantSurface();
            return;
        }

        if (TV_LAUNCHER.equals(pkg)) {
            String className = event.getClassName() == null ? "" : event.getClassName().toString();
            if (className.contains("EntityDetailsActivity")) {
                handleAssistantSurface();
            } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && className.contains("MainActivity")) {
                rememberForeground(pkg, false);
            }
            return;
        }

        if (IGNORE_SOURCE.contains(pkg)) return;

        maybeConsumeQueuedRoute(pkg);
        if (routing) return;

        rememberForeground(pkg, AppCatalog.isEnabled(this, pkg));
    }

    private void rememberForeground(String pkg, boolean searchable) {
        if (!pkg.equals(lastSourcePackage)) {
            lastSourcePackage = pkg;
            lastSourceRoutable = searchable;
            if (!routing) clearPending();
            Log.i(TAG, "Foreground source=" + pkg + ", searchable=" + searchable);
            DiagnosticLog.add(this, "Foreground=" + pkg + ", selected=" + searchable);
        } else if (searchable != lastSourceRoutable) {
            lastSourceRoutable = searchable;
            if (!routing) clearPending();
            Log.i(TAG, "Routing preference changed for " + pkg
                    + ", selected=" + searchable);
            DiagnosticLog.add(this, "Selection changed while active: " + pkg
                    + ", selected=" + searchable);
        }
    }

    private void handleAssistantSurface() {
        if (routing) return;
        long now = SystemClock.uptimeMillis();
        if (pendingTarget == null) {
            if (lastSourceRoutable && lastSourcePackage != null
                    && !NEVER_ROUTE.contains(lastSourcePackage)) {
                pendingTarget = lastSourcePackage;
                pendingLaunchTarget = false;
            } else {
                pendingTarget = AppCatalog.defaultPackage(this);
                pendingLaunchTarget = pendingTarget != null;
            }
            if (pendingTarget == null) return;
            pendingSince = now;
            Log.i(TAG, pendingLaunchTarget
                    ? "Assistant will open default " + pendingTarget
                    : "Assistant opened from selected app " + pendingTarget);
            DiagnosticLog.add(this, pendingLaunchTarget
                    ? "Assistant route decision: launch default " + pendingTarget
                    : "Assistant route decision: return to active " + pendingTarget);
        }
        if (now - pendingSince < MIN_RECOGNITION_MS) {
            scheduleInspection(MIN_RECOGNITION_MS - (now - pendingSince) + 50L);
        } else {
            scheduleInspection(80L);
        }
        scheduleInspection(450L);
        scheduleInspection(1_200L);
    }

    private void scheduleInspection(long delayMs) {
        handler.postDelayed(new Runnable() {
            @Override public void run() { inspectAssistantResult(); }
        }, Math.max(0L, delayMs));
    }

    private void inspectAssistantResult() {
        if (routing || pendingTarget == null
                || SystemClock.uptimeMillis() - pendingSince < MIN_RECOGNITION_MS) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;
        String rootPackage = root.getPackageName().toString();
        String query;
        if (KATNISS.equals(rootPackage)) {
            query = textByViewId(root, "com.google.android.katniss:id/super_header");
        } else if (TV_LAUNCHER.equals(rootPackage)) {
            query = textByViewId(root,
                    "com.google.android.tvlauncher:id/entity_details_title");
        } else {
            return;
        }
        if (!isUsableQuery(query)) return;

        if (pendingLaunchTarget) {
            final String target = pendingTarget;
            Log.i(TAG, "Default route to " + target + " via "
                    + rootPackage + ": " + query);
            DiagnosticLog.add(this, "Recognition completed via " + rootPackage
                    + "; launch default " + target + "; textLength=" + query.length());
            routing = true;
            boolean launched = launchAndQueue(target, query);
            finishRouting(launched);
            return;
        }

        final String target = pendingTarget;
        routing = true;
        Log.i(TAG, "Recognized query for " + target + " via " + rootPackage + ": " + query);
        DiagnosticLog.add(this, "Recognition completed via " + rootPackage
                + "; return to " + target + "; textLength=" + query.length());
        Toast.makeText(this, UiLocale.text(this, R.string.search_in,
                displayName(target), query), Toast.LENGTH_SHORT).show();
        performGlobalAction(GLOBAL_ACTION_BACK);
        routeAfterBack(target, query, 0, 850L);
    }

    private void maybeConsumeQueuedRoute(String pkg) {
        if (routing) return;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String target = prefs.getString(PREF_PACKAGE, null);
        String query = prefs.getString(PREF_QUERY, null);
        long queuedAt = prefs.getLong(PREF_TIME, 0L);
        if (target == null) return;
        if (System.currentTimeMillis() - queuedAt > QUEUED_ROUTE_TTL_MS) {
            DiagnosticLog.add(this, "Queued route expired for " + target);
            prefs.edit().clear().apply();
            return;
        }
        if (!target.equals(pkg) || TextUtils.isEmpty(query)) return;
        if (!AppCatalog.isEnabled(this, target)) {
            prefs.edit().clear().apply();
            return;
        }
        prefs.edit().clear().apply();
        pendingTarget = target;
        pendingLaunchTarget = false;
        routing = true;
        Log.i(TAG, "Opened default route to " + target + ": " + query);
        DiagnosticLog.add(this, "Default app opened: " + target
                + "; textLength=" + query.length());
        routeAfterBack(target, query, 0, 1_100L);
    }

    private boolean launchAndQueue(String target, String query) {
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(target);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(target);
        if (launch == null) {
            Log.w(TAG, "No launch activity for " + target);
            DiagnosticLog.add(this, "Failure: no launch activity for " + target);
            return false;
        }
        queueExternalRoute(this, target, query);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(launch);
        return true;
    }

    private void routeAfterBack(final String target, final String query,
                                final int attempt, long delay) {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root == null || root.getPackageName() == null
                        || !target.contentEquals(root.getPackageName())) {
                    if (attempt < 3) {
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        routeAfterBack(target, query, attempt + 1, 650L);
                    } else {
                        Log.w(TAG, "Could not return to " + target);
                        DiagnosticLog.add(VoiceRouterService.this,
                                "Failure: could not return to " + target);
                        finishRouting(false);
                    }
                    return;
                }

                if (SMARTTUBE.equals(target)) {
                    routeSmartTube(root, query, attempt);
                } else if (KINOPUB.equals(target)) {
                    routeKinopub(root, query, attempt);
                } else {
                    routeGeneric(root, target, query, attempt);
                }
            }
        }, delay);
    }

    private void routeSmartTube(AccessibilityNodeInfo root, String query, int attempt) {
        AccessibilityNodeInfo editor = firstById(root,
                "org.smarttube.stable:id/lb_search_text_editor");
        if (editor == null) {
            AccessibilityNodeInfo openSearch = firstById(root,
                    "org.smarttube.stable:id/title_orb");
            if (openSearch != null && openSearch.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK) && attempt < 4) {
                routeAfterBack(SMARTTUBE, query, attempt + 1, 850L);
            } else {
                Log.w(TAG, "SmartTube search editor/control not found");
                DiagnosticLog.add(this,
                        "Failure: SmartTube search editor/control not found");
                finishRouting(false);
            }
            return;
        }

        if (!setText(editor, query)) {
            Log.w(TAG, "SmartTube rejected ACTION_SET_TEXT");
            DiagnosticLog.add(this, "Failure: SmartTube rejected ACTION_SET_TEXT");
            finishRouting(false);
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                AccessibilityNodeInfo current = getRootInActiveWindow();
                AccessibilityNodeInfo submit = current == null ? null
                        : firstById(current,
                        "org.smarttube.stable:id/lb_search_bar_search_orb");
                if (submit != null) submit.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                finishRouting(true);
            }
        }, 350L);
    }

    private void routeKinopub(AccessibilityNodeInfo root, String query, int attempt) {
        AccessibilityNodeInfo editor = firstById(root,
                "com.kinopub:id/search_bar_edit_text");
        if (editor == null) editor = firstEditable(root);
        if (editor == null) {
            AccessibilityNodeInfo search = firstSearchControl(root, null);
            if (search != null && search.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    && attempt < 4) {
                routeAfterBack(KINOPUB, query, attempt + 1, 850L);
            } else {
                Log.w(TAG, "Kinopub search editor/control not found");
                DiagnosticLog.add(this,
                        "Failure: Kinopub search editor/control not found");
                finishRouting(false);
            }
            return;
        }
        finishTextEntry(editor, KINOPUB, query);
    }

    private void routeGeneric(AccessibilityNodeInfo root, String target,
                              String query, int attempt) {
        AccessibilityNodeInfo editor = firstEditable(root);
        if (editor == null) {
            AccessibilityNodeInfo search = firstSearchControl(root, null);
            if (search != null && search.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    && attempt < 4) {
                routeAfterBack(target, query, attempt + 1, 850L);
            } else {
                Log.w(TAG, "Generic search UI not found in " + target);
                DiagnosticLog.add(this,
                        "Failure: generic search UI not found in " + target);
                finishRouting(false);
            }
            return;
        }
        finishTextEntry(editor, target, query);
    }

    private void finishTextEntry(final AccessibilityNodeInfo editor,
                                 final String target, String query) {
        if (!setText(editor, query)) {
            Log.w(TAG, target + " rejected ACTION_SET_TEXT");
            DiagnosticLog.add(this, "Failure: " + target + " rejected ACTION_SET_TEXT");
            finishRouting(false);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            editor.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
        }
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                AccessibilityNodeInfo current = getRootInActiveWindow();
                AccessibilityNodeInfo submit = current == null ? null
                        : firstSearchControl(current, editor);
                if (submit != null) submit.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                finishRouting(true);
            }
        }, 450L);
    }

    private boolean setText(AccessibilityNodeInfo node, String text) {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private AccessibilityNodeInfo firstById(AccessibilityNodeInfo root, String id) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
        return nodes == null || nodes.isEmpty() ? null : nodes.get(0);
    }

    private String textByViewId(AccessibilityNodeInfo root, String id) {
        AccessibilityNodeInfo node = firstById(root, id);
        return node == null || node.getText() == null
                ? null : node.getText().toString().trim();
    }

    private AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isEditable()
                    || "android.widget.EditText".contentEquals(node.getClassName())) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo firstSearchControl(AccessibilityNodeInfo root,
                                                      AccessibilityNodeInfo excluded) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            String id = lower(node.getViewIdResourceName());
            String text = lower(node.getText());
            String desc = lower(node.getContentDescription());
            boolean searchLike = id.contains("search") || id.contains("find")
                    || text.contains("поиск") || text.contains("найти")
                    || desc.contains("поиск") || desc.contains("найти")
                    || desc.contains("search") || desc.contains("find");
            boolean microphone = id.contains("mic") || id.contains("speech")
                    || id.contains("voice") || desc.contains("микрофон");
            if (node != excluded && node.isClickable() && searchLike && !microphone) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return null;
    }

    private boolean isUsableQuery(String query) {
        if (TextUtils.isEmpty(query)) return false;
        String q = query.trim();
        if (q.length() < 2 || q.length() > 200) return false;
        String lower = q.toLowerCase(Locale.ROOT);
        return !lower.contains("скажите") && !lower.contains("говорите")
                && !lower.equals("поиск") && !lower.equals("google assistant");
    }

    private String lower(CharSequence value) {
        return value == null ? ""
                : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private String displayName(String pkg) {
        if (SMARTTUBE.equals(pkg)) return "SmartTube";
        if (KINOPUB.equals(pkg)) return "KinoPub";
        if (NUM.equals(pkg)) return "NUM";
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
            return pkg;
        }
    }

    private void clearPending() {
        pendingTarget = null;
        pendingLaunchTarget = false;
        pendingSince = 0L;
    }

    private void finishRouting(boolean success) {
        Log.i(TAG, "Routing finished, success=" + success);
        DiagnosticLog.add(this, "Routing finished; success=" + success);
        if (!success) Toast.makeText(this,
                UiLocale.text(this, R.string.search_field_not_found),
                Toast.LENGTH_LONG).show();
        routing = false;
        clearPending();
    }

    @Override
    public void onInterrupt() {
        DiagnosticLog.add(this, "Accessibility service interrupted");
        routing = false;
        clearPending();
    }
}
