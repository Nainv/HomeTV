package com.gathin.tvhome;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/** A package's user-facing name, falling back to its launcher activity and finally its package ID. */
final class AppNames {
    private AppNames() { }
    static String load(PackageManager pm, ResolveInfo info) {
        String pkg=info.activityInfo.packageName, application=null, activity=null;
        try { application=String.valueOf(pm.getApplicationLabel(info.activityInfo.applicationInfo)); }
        catch(RuntimeException ignored) { }
        try { activity=String.valueOf(info.loadLabel(pm)); }
        catch(RuntimeException ignored) { }
        return choose(application,activity,pkg);
    }
    static String choose(String application,String activity,String pkg) {
        if(usable(application,pkg))return application.trim();
        if(usable(activity,pkg))return activity.trim();
        return pkg;
    }
    private static boolean usable(String name,String pkg){return name!=null&&!name.trim().isEmpty()&&!"null".equals(name.trim())&&!pkg.equals(name.trim());}
}
