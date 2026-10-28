package com.gathin.tvhome;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

/** Explicit user-requested service setup after a one-time WRITE_SECURE_SETTINGS grant. */
final class HomeServiceSetup {
    private HomeServiceSetup() { }

    static String setEnabled(Context context, boolean enable) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            return context.getString(R.string.home_permission, context.getPackageName());
        }
        ComponentName own = new ComponentName(context, HomeKeyService.class);
        String previous = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        int previousEnabled = Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        String updated = HomeServiceSelection.update(previous, own.flattenToString(),
                own.flattenToShortString(), enable);
        try {
            if (!Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated))
                throw new IllegalStateException("رفض النظام حفظ قائمة الخدمات.");
            int enabledFlag = updated.isEmpty() ? 0 : (enable ? 1 : previousEnabled);
            if (!Settings.Secure.putInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, enabledFlag))
                throw new IllegalStateException("رفض النظام حفظ التفعيل.");
            if (HomeDiagnostics.selected(context) != enable)
                throw new IllegalStateException("النظام لم يحتفظ بحالة الخدمة المطلوبة.");
            if (enable && Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1)
                throw new IllegalStateException("النظام لم يحتفظ بالتفعيل العام.");
            return enable
                    ? context.getString(R.string.home_enabled)
                    : context.getString(R.string.home_disabled);
        } catch (RuntimeException failure) {
            try {
                Settings.Secure.putString(context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, previous);
                Settings.Secure.putInt(context.getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED, previousEnabled);
            } catch (RuntimeException ignored) { }
            return "تعذّر التفعيل: " + failure.getMessage();
        }
    }
}
