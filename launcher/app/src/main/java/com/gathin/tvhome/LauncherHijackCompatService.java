/*
 * LauncherHijack-derived Home broadcast/PendingIntent integration.
 * Upstream: BaronKiko/LauncherHijack f79aee3ddd10c053d6d7c55d6f2fc29436001537.
 * HomeWatcher upstream credits Evan Gillogley's react-native-home-pressed.
 * Modified for HomeTV on 2026-10-28. GNU GPLv3; see COPYING and
 * launcher/LAUNCHERHIJACK-NOTICE.md. No warranty.
 */
package com.gathin.tvhome;

import android.app.PendingIntent;
import android.app.Service;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/** Opt-in Fire OS 6 broadcast route; does not consume remote keys or alter system settings. */
public final class LauncherHijackCompatService extends Service {
    private static final String PREFS = "launcher_hijack_compat";
    private static final String TAG = "HomeHijackCompat";
    private static volatile boolean running;
    private static volatile boolean foreground;
    private HandlerThread usageThread;
    private boolean registered;
    private long lastActivation;
    private Handler usageHandler;
    private long lastUsageLaunch;
    private final Runnable usageWatcher = new Runnable() {
        @Override public void run() {
            if (!enabled(LauncherHijackCompatService.this) || !running) return;
            try {
                AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
                boolean allowed = ops != null && ops.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(),
                        getPackageName()) == AppOpsManager.MODE_ALLOWED;
                PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                long now = System.currentTimeMillis();
                if (!allowed) {
                    getSharedPreferences(PREFS, 0).edit().putString("usage_status", "permission-required").apply();
                } else if (power != null && !power.isInteractive()) {
                    // Screen off: leave usage polling idle until the next tick.
                } else {
                    UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
                    UsageEvents events = manager == null ? null
                            : manager.queryEvents(now - 4000, now);
                    UsageEvents.Event event = new UsageEvents.Event();
                    String latestPackage = null;
                    String latestClass = null;
                    long latestTime = 0;
                    while (events != null && events.hasNextEvent()) {
                        events.getNextEvent(event);
                        if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND
                                && event.getTimeStamp() >= latestTime) {
                            latestPackage = event.getPackageName();
                            latestClass = event.getClassName();
                            latestTime = event.getTimeStamp();
                        }
                    }
                    // Avoid disk writes on every polling tick.
                    if (!"watching".equals(getSharedPreferences(PREFS, 0).getString("usage_status", "")))
                        getSharedPreferences(PREFS, 0).edit().putString("usage_status", "watching").apply();
                    // Amazon settings share the package; match only its Home activity.
                    if ("com.amazon.tv.launcher".equals(latestPackage)
                            && latestClass != null
                            && (latestClass.endsWith(".HomeActivity_vNext")
                                || latestClass.endsWith(".HomeActivity"))
                            && (lastUsageLaunch == 0
                                || SystemClock.elapsedRealtime() - lastUsageLaunch >= 1500)) {
                        lastUsageLaunch = SystemClock.elapsedRealtime();
                        getSharedPreferences(PREFS, 0).edit()
                                .putLong("detected_delay_ms", Math.max(0, now - latestTime))
                                .putLong("launch_elapsed", lastUsageLaunch)
                                .putLong("launch_delay_ms", -1).apply();
                        getSharedPreferences(PREFS, 0).edit()
                                .putLong("home_time", System.currentTimeMillis()).apply();
                        bringExistingHomeForward();
                    }
                }
            } catch (RuntimeException failure) {
                getSharedPreferences(PREFS, 0).edit().putString("usage_status", "query-failed")
                        .putString("error", Log.getStackTraceString(failure)).apply();
            }
            if (running) usageHandler.postDelayed(this, 250);
        }
    };

    /**
     * Reuse only our live MainActivity task. Starting a new activity after HOME can
     * be queued by Fire OS; AppTask.moveToFront uses the existing task instead.
     * No system package is disabled and no other application's task is touched.
     */
    private void bringExistingHomeForward() {
        android.app.ActivityManager activities =
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (activities != null) {
            for (android.app.ActivityManager.AppTask task : activities.getAppTasks()) {
                android.app.ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                if (info.baseActivity == null || info.topActivity == null
                        || !getPackageName().equals(info.baseActivity.getPackageName())
                        || !MainActivity.class.getName().equals(info.baseActivity.getClassName())
                        || !MainActivity.class.getName().equals(info.topActivity.getClassName())) continue;
                try {
                    task.moveToFront();
                    getSharedPreferences(PREFS, 0).edit()
                            .putString("result", "existing-home-task-move-request-sent")
                            .putString("error", "").apply();
                    return;
                } catch (RuntimeException failure) {
                    getSharedPreferences(PREFS, 0).edit()
                            .putString("error", Log.getStackTraceString(failure)).apply();
                    break;
                }
            }
        }
        // Cold start or Home settings open: retain the normal explicit launch.
        // This fallback may still incur the platform's Home app-switch delay.
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(open);
        getSharedPreferences(PREFS, 0).edit()
                .putString("result", "no-live-home-task-start-fallback").apply();
    }

    static boolean supported() {
        return "Amazon".equalsIgnoreCase(Build.MANUFACTURER) && Build.VERSION.SDK_INT == 25;
    }
    static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, 0).getBoolean("enabled", false);
    }
    static void setEnabled(Context context, boolean enable) {
        if (enable && !supported()) throw new IllegalStateException("هذا المسار مخصص لـFire OS 6.");
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", enable).commit();
        Intent service = new Intent(context, LauncherHijackCompatService.class);
        if (!enable) {
            context.stopService(service);
            return;
        }
        try {
            if (context.startService(service) == null) throw new IllegalStateException("تعذّر تشغيل خدمة التوافق.");
        } catch (RuntimeException failure) {
            context.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", false)
                    .putString("error", Log.getStackTraceString(failure)).commit();
            throw failure;
        }
    }
    static String report(Context context) {
        android.content.SharedPreferences p = context.getSharedPreferences(PREFS, 0);
        return "launcherHijackSupported=" + supported()
                + "\nlauncherHijackEnabled=" + enabled(context)
                + "\nlauncherHijackRunning=" + running
                + "\nusageForegroundService=" + foreground
                + "\nusageDetectionDelayMs=" + p.getLong("detected_delay_ms", -1)
                + "\nusageLaunchToResumeMs=" + p.getLong("launch_delay_ms", -1)
                + "\nusageServiceCreatedAt=" + p.getLong("created_at", 0)
                + "\nusageServiceDestroyedAt=" + p.getLong("destroyed_at", 0)
                + "\nlauncherHijackLastHomeTime=" + p.getLong("home_time", 0)
                + "\nlauncherHijackLastResult=" + p.getString("result", "no-home-broadcast-yet")
                + "\nusageWatcherStatus=" + p.getString("usage_status", "not-started")
                + "\nlauncherHijackError=" + p.getString("error", "");
    }
    /** Record observed completion, rather than treating startActivity as proof of a launch. */
    static void onHomeResumed(Context context) {
        android.content.SharedPreferences p = context.getSharedPreferences(PREFS, 0);
        long requested = p.getLong("launch_elapsed", 0);
        long delay = SystemClock.elapsedRealtime() - requested;
        if (requested > 0 && delay >= 0 && delay < 120000) {
            p.edit().putLong("launch_delay_ms", delay).remove("launch_elapsed").apply();
        }
    }

    private final BroadcastReceiver homeWatcher = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!enabled(context) || !Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())
                    || !"homekey".equals(intent.getStringExtra("reason"))) return;
            long now = SystemClock.elapsedRealtime();
            if (lastActivation != 0 && now - lastActivation < 200) return;
            lastActivation = now;
            context.getSharedPreferences(PREFS, 0).edit().putLong("home_time", System.currentTimeMillis()).apply();
            Intent open = new Intent(Intent.ACTION_MAIN).setClass(context, MainActivity.class)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try {
                PendingIntent.getActivity(context, 8096, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE).send();
                context.getSharedPreferences(PREFS, 0).edit().putString("result", "launch-request-sent")
                        .putString("error", "").apply();
                Log.i(TAG, "Home broadcast received; launcher request sent");
            } catch (PendingIntent.CanceledException | RuntimeException failure) {
                context.getSharedPreferences(PREFS, 0).edit().putString("result", "launch-failed")
                        .putString("error", Log.getStackTraceString(failure)).apply();
                Log.e(TAG, "Cannot launch HomeTV", failure);
            }
        }
    };
    // Foreground service types were introduced in API 29 and required in API 34.
    // This service deliberately never runs outside Amazon API 25.
    @android.annotation.SuppressLint("ForegroundServiceType")
    private void startFireOs6Foreground(android.app.Notification notification) {
        if (!supported()) throw new IllegalStateException("Fire OS 6 only");
        startForeground(8096, notification);
    }

    @Override public void onCreate() {
        super.onCreate();
        if (!supported() || !enabled(this)) { stopSelf(); return; }
        try {
            // This route is restricted to SDK 25, before notification channels/service types.
            android.app.Notification notification = new androidx.core.app.NotificationCompat.Builder(
                    this, "home_compat")
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle("Home TV")
                    .setContentText("توافق زر Home قيد التشغيل")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true).setShowWhen(false)
                    .setContentIntent(PendingIntent.getActivity(this, 8097,
                            new Intent(this, HomeToolsActivity.class),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                    .build();
            startFireOs6Foreground(notification);
            foreground = true;
            usageThread = new HandlerThread("HomeUsageWatcher",
                    android.os.Process.THREAD_PRIORITY_BACKGROUND);
            usageThread.start();
            usageHandler = new Handler(usageThread.getLooper());
            getSharedPreferences(PREFS, 0).edit().putLong("created_at",
                    System.currentTimeMillis()).apply();
            IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            // Amazon's launcher may send this from a privileged app UID instead of system UID.
            // The only accepted event launches our already-exported MainActivity; it grants no access.
            androidx.core.content.ContextCompat.registerReceiver(this, homeWatcher, filter,
                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
            registered = true;
            running = true;
            usageHandler.post(usageWatcher);
            Log.i(TAG, "Home broadcast watcher registered");
        } catch (RuntimeException failure) {
            getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", false)
                    .putString("error", Log.getStackTraceString(failure)).apply();
            stopSelf();
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!enabled(this) || !registered) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        running = false;
        foreground = false;
        if (usageHandler != null) usageHandler.removeCallbacksAndMessages(null);
        if (usageThread != null) usageThread.quitSafely();
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSharedPreferences(PREFS, 0).edit().putLong("destroyed_at",
                System.currentTimeMillis()).apply();
        if (registered) {
            unregisterReceiver(homeWatcher);
            registered = false;
        }
        super.onDestroy();
    }
    public static final class BootReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                    || !supported() || !enabled(context)) return;
            try { setEnabled(context, true); }
            catch (RuntimeException failure) { Log.e(TAG, "Cannot restore requested Home watcher", failure); }
        }
    }
}
