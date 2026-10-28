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
    private static boolean running;
    private boolean registered;
    private long lastActivation;

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
                + "\nlauncherHijackLastHomeTime=" + p.getLong("home_time", 0)
                + "\nlauncherHijackLastResult=" + p.getString("result", "no-home-broadcast-yet")
                + "\nlauncherHijackError=" + p.getString("error", "");
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
    @Override public void onCreate() {
        super.onCreate();
        if (!supported() || !enabled(this)) { stopSelf(); return; }
        try {
            registerReceiver(homeWatcher, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            registered = true;
            running = true;
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
