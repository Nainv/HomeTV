package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Daily foreground-triggered update check; downloads a newer verified APK, never installs silently. */
final class PersonalUpdates {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static boolean checking,showing;
    static synchronized void check(Activity activity){
        if(checking||showing)return;
        Context context=activity.getApplicationContext();SharedPreferences prefs=context.getSharedPreferences("personal_updates",0);
        String pending=prefs.getString("ready","");
        if(!pending.isEmpty())try{
            JSONObject j=new JSONObject(pending);PersonalPackages.Target t=new PersonalPackages.Target("تحديث الواجهة",PersonalPackages.SELF_PACKAGE,j.getString("url"),j.getString("sha"),j.getLong("version"));
            if(UpdateRules.valid(PersonalPackages.ORIGIN,t.url,t.sha,t.version)&&UpdateRules.newer(t.version,PersonalPackages.installedVersion(context))&&PersonalPackages.file(context,t).isFile()){
                if(activity.hasWindowFocus())prompt(activity);return;
            }
            prefs.edit().remove("ready").apply();
        }catch(Exception e){prefs.edit().remove("ready").apply();}
        long now=System.currentTimeMillis(),last=prefs.getLong("last",0);if(now>=last&&now-last<24L*60*60*1000)return;
        prefs.edit().putLong("last",now).apply();checking=true;WeakReference<Activity> ref=new WeakReference<>(activity);
        WORKER.execute(()->{try{
            PersonalPackages.Target t=PersonalPackages.latest();
            if(UpdateRules.newer(t.version,PersonalPackages.installedVersion(context))){
                PersonalPackages.download(context,t,(n,total)->{});
                JSONObject j=new JSONObject().put("url",t.url).put("sha",t.sha).put("version",t.version);
                prefs.edit().putString("ready",j.toString()).apply();
                Activity a=ref.get();if(a!=null)a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed()&&a.hasWindowFocus())prompt(a);});
            }
        }catch(Exception ignored){/* Offline: normal Home usage must stay uninterrupted. */}
        finally{synchronized(PersonalUpdates.class){checking=false;}}});
    }
    private static synchronized void prompt(Activity a){if(showing||a.isFinishing()||a.isDestroyed())return;showing=true;
        AlertDialog d=new AlertDialog.Builder(a).setTitle("تحديث الواجهة جاهز")
                .setMessage("تم تنزيل نسخة أحدث والتحقق منها. افتح صفحة التحديث لإكمال التثبيت بموافقتك.")
                .setPositiveButton("متابعة التثبيت",(dialog,w)->a.startActivity(new Intent(a,HomeToolsActivity.class).putExtra("task","update")))
                .setNegativeButton("لاحقاً",(dialog,w)->a.getSharedPreferences("personal_updates",0).edit().remove("ready").apply()).create();
        d.setOnDismissListener(dialog->{synchronized(PersonalUpdates.class){showing=false;}});d.show();
    }
}
