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
        scheduleBackgroundChecks(activity);
        if(checking||showing)return;
        Context context=activity.getApplicationContext();SharedPreferences prefs=context.getSharedPreferences("personal_updates",0);
        String pending=prefs.getString("ready","");
        if(!pending.isEmpty())try{
            JSONObject j=new JSONObject(pending);PersonalPackages.Target t=new PersonalPackages.Target(activity.getString(R.string.home_update),PersonalPackages.SELF_PACKAGE,j.getString("url"),j.getString("sha"),j.getLong("version"));
            if(UpdateRules.valid(PersonalPackages.ORIGIN,t.url,t.sha,t.version)&&UpdateRules.newer(t.version,PersonalPackages.installedVersion(context))&&PersonalPackages.file(context,t).isFile()){
                showReady(activity,t);return;
            }
            prefs.edit().remove("ready").apply();
        }catch(Exception e){prefs.edit().remove("ready").apply();}
        long now=System.currentTimeMillis(),last=prefs.getLong("last",0);if(now>=last&&now-last<15L*60*1000)return;
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

    static void scheduleBackgroundChecks(Context context) {
        android.app.job.JobScheduler scheduler = (android.app.job.JobScheduler)
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || scheduler.getPendingJob(7303) != null) return;
        try { scheduler.schedule(new android.app.job.JobInfo.Builder(7303,
                new android.content.ComponentName(context, HomeUpdateJob.class))
                .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(60L * 60 * 1000).setPersisted(true).build()); }
        catch (RuntimeException ignored) { /* Foreground checks remain available on restricted firmware. */ }
    }
    static synchronized java.util.concurrent.Future<?> checkBackground(Context base, Runnable finished) {
        if (checking) return null;
        Context context;
        try { context = LauncherLocale.wrap(base.getApplicationContext()); }
        catch (RuntimeException unavailable) { return null; }
        checking = true;
        java.util.concurrent.FutureTask<Void> task = new java.util.concurrent.FutureTask<Void>(() -> {
            try {
                PersonalPackages.Target target = PersonalPackages.latest();
                if (UpdateRules.newer(target.version, PersonalPackages.installedVersion(context))) {
                    PersonalPackages.download(context, target, (n, total) -> { });
                    JSONObject pending = new JSONObject().put("url", target.url)
                            .put("sha", target.sha).put("version", target.version);
                    context.getSharedPreferences("personal_updates", 0).edit()
                            .putString("ready", pending.toString()).apply();
                    backgroundNotice(context, target);
                }
            } catch (Exception ignored) {
                // Offline or interrupted: retain the next periodic check and normal foreground check.
            }
            return null;
        }) {
            @Override protected void done() {
                synchronized (PersonalUpdates.class) { checking = false; }
                finished.run();
            }
        };
        WORKER.execute(task);
        return task;
    }
    private static void backgroundNotice(Context context, PersonalPackages.Target target) {
        SharedPreferences prefs = context.getSharedPreferences("personal_updates", 0);
        if (prefs.getLong("notified_version", 0) == target.version) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, context.getString(R.string.home_updates_channel), NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, HomeToolsActivity.class).putExtra("task", "update")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent action = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.home_update_ready))
                .setContentText(context.getString(R.string.home_update_tap))
                .setAutoCancel(true).setContentIntent(action).build();
        manager.notify(NOTIFICATION_ID, notification);
        prefs.edit().putLong("notified_version", target.version).apply();
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
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,a.getString(R.string.home_updates_channel),NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,HomeToolsActivity.class).putExtra("task","update").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=23)flags|=PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending=PendingIntent.getActivity(context,NOTIFICATION_ID,open,flags);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL_ID):new Notification.Builder(context);
        Notification notification=builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(a.getString(R.string.home_update_ready))
                .setContentText(a.getString(R.string.home_update_tap))
                .setCategory(Notification.CATEGORY_STATUS).setAutoCancel(true).setContentIntent(pending)
                .setPriority(Notification.PRIORITY_DEFAULT).build();
        manager.notify(NOTIFICATION_ID,notification);
        prefs.edit().putLong("notified_version",t.version).apply();
    }
    private static synchronized void prompt(Activity a){if(showing||a.isFinishing()||a.isDestroyed())return;showing=true;
        AlertDialog d=new AlertDialog.Builder(a).setTitle(R.string.home_update_ready)
                .setMessage(R.string.home_update_message)
                .setPositiveButton(R.string.home_install,(dialog,w)->a.startActivity(new Intent(a,HomeToolsActivity.class).putExtra("task","update")))
                .setNegativeButton(R.string.home_later,(dialog,w)->a.getSharedPreferences("personal_updates",0).edit().remove("ready").apply()).create();
        d.setOnDismissListener(dialog->{synchronized(PersonalUpdates.class){showing=false;}});d.show();
    }
}
