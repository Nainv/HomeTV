package com.gathin.tvhome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONObject;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Daily foreground-triggered update check; downloads a newer verified APK, then notifies without installing silently. */
final class PersonalUpdates {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final String CHANNEL_ID="home_updates";
    private static final int NOTIFICATION_ID=7301, PERMISSION_REQUEST=7302;
    private static boolean checking,showing;
    static synchronized void check(Activity activity){
        if(checking||showing)return;
        Context context=activity.getApplicationContext();SharedPreferences prefs=context.getSharedPreferences("personal_updates",0);
        String pending=prefs.getString("ready","");
        if(!pending.isEmpty())try{
            JSONObject j=new JSONObject(pending);PersonalPackages.Target t=new PersonalPackages.Target("تحديث الواجهة",PersonalPackages.SELF_PACKAGE,j.getString("url"),j.getString("sha"),j.getLong("version"));
            if(UpdateRules.valid(PersonalPackages.ORIGIN,t.url,t.sha,t.version)&&UpdateRules.newer(t.version,PersonalPackages.installedVersion(context))&&PersonalPackages.file(context,t).isFile()){
                showReady(activity,t);return;
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
                Activity a=ref.get();if(a!=null)a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())showReady(a,t);});
            }
        }catch(Exception ignored){/* Offline: normal Home usage must stay uninterrupted. */}
        finally{synchronized(PersonalUpdates.class){checking=false;}}});
    }
    private static synchronized void showReady(Activity a,PersonalPackages.Target t){
        if(a.isFinishing()||a.isDestroyed())return;
        Context context=a.getApplicationContext();SharedPreferences prefs=context.getSharedPreferences("personal_updates",0);
        if(Build.VERSION.SDK_INT>=33&&a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            if(!prefs.getBoolean("notification_permission_asked",false)){
                prefs.edit().putBoolean("notification_permission_asked",true).apply();
                a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},PERMISSION_REQUEST);
                return;
            }
            prompt(a);return;
        }
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null||(Build.VERSION.SDK_INT>=24&&!manager.areNotificationsEnabled())){prompt(a);return;}
        if(prefs.getLong("notified_version",0)==t.version)return;
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,"تحديثات Home TV",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,HomeToolsActivity.class).putExtra("task","update").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending=PendingIntent.getActivity(context,NOTIFICATION_ID,open,flags);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL_ID):new Notification.Builder(context);
        Notification notification=builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("تحديث Home TV جاهز")
                .setContentText("اضغط لفتح التحديث ومتابعة تثبيته.")
                .setCategory(Notification.CATEGORY_STATUS).setAutoCancel(true).setContentIntent(pending)
                .setPriority(Notification.PRIORITY_DEFAULT).build();
        manager.notify(NOTIFICATION_ID,notification);
        prefs.edit().putLong("notified_version",t.version).apply();
    }
    private static synchronized void prompt(Activity a){if(showing||a.isFinishing()||a.isDestroyed())return;showing=true;
        AlertDialog d=new AlertDialog.Builder(a).setTitle("تحديث الواجهة جاهز")
                .setMessage("تم تنزيل نسخة أحدث والتحقق منها. افتح صفحة التحديث لإكمال التثبيت بموافقتك.")
                .setPositiveButton("متابعة التثبيت",(dialog,w)->a.startActivity(new Intent(a,HomeToolsActivity.class).putExtra("task","update")))
                .setNegativeButton("لاحقاً",(dialog,w)->a.getSharedPreferences("personal_updates",0).edit().remove("ready").apply()).create();
        d.setOnDismissListener(dialog->{synchronized(PersonalUpdates.class){showing=false;}});d.show();
    }
}
