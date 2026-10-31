package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Local, user-requested Home diagnostics; no network transmission or account/device identifiers. */
final class HomeDiagnostics {
    private static final String TAG = "HomeDiagnostics";
    private static final String PREFS = "home_diagnostics";
    private HomeDiagnostics() { }

    private static android.content.SharedPreferences preferences(Context context) {
        // HomeKeyService is directBootAware. Credential storage is unavailable
        // during early binding, before ACTION_USER_UNLOCKED.
        return context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, 0);
    }

    static void phase(Context context, String phase, Throwable failure) {
        String detail = failure == null ? "" : Log.getStackTraceString(failure);
        android.content.SharedPreferences.Editor edit = preferences(context).edit()
                .putString("phase", phase).putLong("time", System.currentTimeMillis());
        if (failure != null) edit.putString("error", detail).putLong("errorTime", System.currentTimeMillis());
        edit.commit();
        if (failure == null) Log.i(TAG, phase);
        else Log.e(TAG, phase, failure);
    }

    static boolean selected(Context context) {
        String saved = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (saved == null) return false;
        ComponentName own = new ComponentName(context, HomeKeyService.class);
        for (String value : saved.split(":"))
            if (own.equals(ComponentName.unflattenFromString(value))) return true;
        return false;
    }

    static String summary(Context context) {
        if (HomeKeyService.isConnected()) return "خدمة Home: متصلة. اختبر زر Home الحقيقي بالريموت.";
        if (selected(context)) return "خدمة Home: التفعيل محفوظ، لكن النظام لم يربط الخدمة. افتح «تشخيص زر Home».";
        return "خدمة Home: غير محددة ضمن الخدمات المفعّلة.";
    }

    static String report(Context context) {
        StringBuilder out = new StringBuilder("HOME DIAGNOSTICS\n");
        out.append("reportTime=").append(System.currentTimeMillis()).append("\n");
        try {
            PackageManager pm = context.getPackageManager();
            out.append("appVersion=").append(pm.getPackageInfo(context.getPackageName(), 0).versionName)
                    .append("\nmodel=").append(Build.MODEL).append("\nsdk=").append(Build.VERSION.SDK_INT);
            out.append("\nsettingsRoute=").append(context.getSharedPreferences("home_settings_route",0).getString("route","never-opened"));
            out.append("\nsettingsRouteResult=").append(context.getSharedPreferences("home_settings_route",0).getString("result","none"));
            out.append("\nselected=").append(selected(context));
            out.append("\nrestoreKeyServices=").append(Config.load(context).restoreKeyServices);
            out.append("\nserviceRepairRemembered=").append(context.getSharedPreferences(
                    "accessibility_helpers",0).getBoolean(HomeKeyService.class.getName(),false));
            out.append("\nlanguageChoice=").append(context.getSharedPreferences(
                    "home_language",0).getString("choice","system"));
            out.append("\nresourceLanguage=").append(context.getResources().getConfiguration()
                    .getLocales().get(0).toLanguageTag());
            out.append("\naccessibilityEnabled=").append(Settings.Secure.getInt(
                    context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0));
            out.append("\nboundInApp=").append(HomeKeyService.isConnected());
            ComponentName own = new ComponentName(context, HomeKeyService.class);
            ServiceInfo service = pm.getServiceInfo(own, PackageManager.GET_META_DATA
                    | PackageManager.MATCH_DISABLED_COMPONENTS);
            out.append("\nserviceEnabled=").append(service.enabled)
                    .append("\nserviceExported=").append(service.exported)
                    .append("\nservicePermission=").append(service.permission)
                    .append("\ncomponentState=").append(pm.getComponentEnabledSetting(own));
            AccessibilityManager manager = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            boolean discovered = false, enabledByManager = false;
            if (manager != null) {
                out.append("\nmanagerEnabled=").append(manager.isEnabled());
                for (AccessibilityServiceInfo info : manager.getInstalledAccessibilityServiceList()) {
                    if (!own.equals(ComponentName.unflattenFromString(info.getId()))) continue;
                    discovered = true;
                    out.append("\neventTypes=").append(info.eventTypes)
                            .append("\nfeedbackType=").append(info.feedbackType)
                            .append("\nflags=").append(info.flags)
                            .append("\ncapabilities=").append(info.getCapabilities());
                }
                for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
                    if (own.equals(ComponentName.unflattenFromString(info.getId()))) enabledByManager = true;
            }
            out.append("\ndiscoveredByManager=").append(discovered)
                    .append("\nenabledByManager=").append(enabledByManager);
            ResolveInfo home = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY);
            out.append("\ndefaultHome=").append(home == null ? "none" : home.activityInfo.packageName);
            android.content.SharedPreferences prefs = preferences(context);
            out.append("\nlastPhase=").append(prefs.getString("phase", "never-created"))
                    .append("\nphaseTime=").append(prefs.getLong("time", 0))
                    .append("\nlastError=").append(prefs.getString("error", ""));
        } catch (Exception | LinkageError error) {
            out.append("\nreportError=").append(Log.getStackTraceString(error));
        }
        out.append("\n").append(LauncherHijackCompatService.report(context));
        String report = out.toString();
        Log.i(TAG, report);
        File directory = context.getExternalFilesDir(null);
        if (directory != null) {
            try (FileOutputStream file = new FileOutputStream(new File(directory, "home-diagnostics.txt"))) {
                file.write(report.getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) { Log.w(TAG, "Cannot save local report", error); }
        }
        return report;
    }
}
