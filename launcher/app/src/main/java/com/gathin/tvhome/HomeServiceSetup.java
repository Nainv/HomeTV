package com.gathin.tvhome;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/** Explicit user-requested service setup after a one-time WRITE_SECURE_SETTINGS grant. */
final class HomeServiceSetup {
    private HomeServiceSetup() { }

    static String setEnabled(Context context, boolean enable) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            return "يلزم منح الصلاحية مرة واحدة من اللابتوب:\npm grant "
                    + context.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
        }
        ComponentName own = new ComponentName(context, HomeKeyService.class);
        String previous = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        int previousEnabled = Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        List<String> services = new ArrayList<>();
        if (previous != null) for (String entry : previous.split(":")) {
            if (!entry.isEmpty() && !own.equals(ComponentName.unflattenFromString(entry)))
                services.add(entry);
        }
        if (enable) services.add(own.flattenToString());
        String updated = android.text.TextUtils.join(":", services);
        try {
            if (!Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated))
                throw new IllegalStateException("رفض النظام حفظ قائمة الخدمات.");
            int enabledFlag = services.isEmpty() ? 0 : (enable ? 1 : previousEnabled);
            if (!Settings.Secure.putInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, enabledFlag))
                throw new IllegalStateException("رفض النظام حفظ التفعيل.");
            if (HomeDiagnostics.selected(context) != enable)
                throw new IllegalStateException("النظام لم يحتفظ بحالة الخدمة المطلوبة.");
            if (enable && Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1)
                throw new IllegalStateException("النظام لم يحتفظ بالتفعيل العام.");
            return enable
                    ? "تم حفظ تفعيل خدمة Home. انتظر لحظات ثم اضغط زر Home الحقيقي بالريموت. ستظهر حالة الربط أدناه."
                    : "تم إيقاف خدمة Home الخاصة بتطبيقنا؛ الخدمات الأخرى محفوظة.";
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
