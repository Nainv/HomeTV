package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.util.Log;

/** Opens system consent UI only; never writes accessibility settings or enables a service. */
final class AccessibilitySetup {
    static final String COMPONENT_EXTRA="android.intent.extra.COMPONENT_NAME";
    static final String DETAILS="android.settings.ACCESSIBILITY_DETAILS_SETTINGS";
    enum Result { ACCESSIBILITY_PAGE, GENERAL_SETTINGS, UNAVAILABLE }
    private AccessibilitySetup() { }

    static Result open(Activity activity) {
        if(start(activity,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))return Result.ACCESSIBILITY_PAGE;
        // Android 11 Settings consumes the flattened component as a String, not a Parcelable.
        Intent details=new Intent(DETAILS).putExtra(COMPONENT_EXTRA,
                new ComponentName(activity,HomeKeyService.class).flattenToString());
        if(start(activity,details))return Result.ACCESSIBILITY_PAGE;
        if(start(activity,new Intent(Settings.ACTION_SETTINGS)))return Result.GENERAL_SETTINGS;
        try {
            Intent fallback=SystemSettings.intent(activity,Settings.ACTION_SETTINGS);
            if(fallback.getComponent()!=null && start(activity,fallback))return Result.GENERAL_SETTINGS;
        } catch(RuntimeException unavailable) { record(activity,"fallback-resolution",unavailable); }
        // Resolve real exported launcher entries instead of assuming every TV has MainSettings.
        PackageManager pm=activity.getPackageManager();
        for(String pkg:new String[]{"com.android.tv.settings","com.android.settings"}){
            try {
                ApplicationInfo info=pm.getApplicationInfo(pkg,0);
                if(!info.enabled || (info.flags & (ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0)continue;
                Intent entry=pm.getLeanbackLaunchIntentForPackage(pkg);
                if(entry==null)entry=pm.getLaunchIntentForPackage(pkg);
                if(entry!=null && start(activity,entry))return Result.GENERAL_SETTINGS;
            } catch(PackageManager.NameNotFoundException | RuntimeException unavailable) {
                record(activity,pkg,unavailable);
            }
        }
        return Result.UNAVAILABLE;
    }
    private static boolean start(Activity activity,Intent intent){
        String route=intent.getComponent()==null?intent.getAction():intent.getComponent().flattenToShortString();
        try{
            ResolveInfo resolved=activity.getPackageManager().resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY);
            if(resolved!=null && resolved.activityInfo!=null && resolved.activityInfo.name!=null
                    && resolved.activityInfo.name.contains("CTSDummy"))return false;
            // Do not reject a null query: Android package visibility can hide an otherwise launchable handler.
            activity.startActivity(intent);
            activity.getSharedPreferences("home_settings_route",0).edit()
                    .putString("route",route).putString("result","opened").apply();
            return true;
        }catch(RuntimeException unavailable){record(activity,route,unavailable);return false;}
    }
    private static void record(Context context,String route,Exception failure){
        String error=failure.getClass().getSimpleName();
        Log.w("HomeSettingsRoute",route+": "+error);
        context.getSharedPreferences("home_settings_route",0).edit()
                .putString("route",route).putString("result",error).apply();
    }
}
