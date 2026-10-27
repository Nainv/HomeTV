package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.util.concurrent.*;

/** Remote-friendly actions; no installation or accessibility permission is accepted on the user's behalf. */
public final class HomeToolsActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status;private Button install;private File ready;private boolean busy;
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(13,25,28));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(36),dp(18),dp(36),dp(24));box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);scroll.addView(box);setContentView(scroll);
        TextView title=new TextView(this);title.setText("تطبيقاتي وإعداد الواجهة");title.setTextColor(Color.WHITE);title.setTextSize(26);box.addView(title);
        TextView help=new TextView(this);help.setText("ذِكرى يفتح مباشرة إذا كان مثبّتاً. التنزيل والتحديث يحتاجان اتصال إنترنت، والتثبيت يحتاج موافقتك من أندرويد.");help.setTextColor(Color.LTGRAY);help.setTextSize(15);help.setPadding(0,dp(8),0,dp(12));box.addView(help);
        button(box,"ذِكرى — فتح أو تنزيل",()->{if(!PersonalPackages.openZikra(this))startDownload(PersonalPackages.zikra());});
        button(box,"البحث عن تحديث للواجهة",this::checkUpdate);
        button(box,"تفعيل زر Home وفتح الواجهة عند تشغيل التلفزيون",()->{
            new android.app.AlertDialog.Builder(this).setTitle("خدمة زر الرئيسية").setMessage("في الصفحة التالية اختر خدمة «زر الرئيسية» التابعة لهذا التطبيق وفعّلها. الخدمة تراقب تغيّر النوافذ لإرجاع الشاشة إلى هذه الواجهة؛ التقاط أزرار الريموت مطفأ للمحافظة على تكرار الصوت. اختبر Home ثم إعادة التشغيل. يمكن إيقاف الخدمة من نفس الصفحة.")
                .setPositiveButton("فتح تسهيلات الاستخدام",(d,w)->openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                .setNegativeButton("رجوع",null).show();
        });
        button(box,"إعدادات التلفزيون",()->openSettings(Settings.ACTION_SETTINGS));
        button(box,"حقوق وتراخيص البرامج",()->new android.app.AlertDialog.Builder(this).setTitle("التراخيص").setMessage("نسخة شخصية معدّلة من CleanYourTV. حقوق 2026 Gathin — ترخيص MIT. خط Outfit تحت ترخيص SIL OFL 1.1. النصوص الكاملة مضمّنة في assets/licenses داخل APK. ذِكرى تطبيق منفصل من مستودع المستخدم.").setPositiveButton("موافق",null).show());
        status=new TextView(this);status.setTextColor(Color.LTGRAY);status.setTextSize(16);status.setPadding(0,dp(14),0,dp(12));box.addView(status);
        install=button(box,"تثبيت الملف الجاهز",this::installReady);install.setVisibility(View.GONE);
        button(box,"رجوع للواجهة",this::finish);
        box.getChildAt(2).requestFocus();
        if("update".equals(getIntent().getStringExtra("task")))checkUpdate();
    }
    private Button button(LinearLayout box,String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(17);b.setPadding(dp(15),dp(7),dp(15),dp(7));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55));p.topMargin=dp(9);box.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    private void openSettings(String action){try{startActivity(new Intent(action));}catch(Exception e){status.setText("صفحة الإعدادات غير متاحة على هذا التلفزيون.");}}
    private void checkUpdate(){if(busy)return;busy=true;status.setText("جارٍ البحث عن تحديث…");worker.execute(()->{try{PersonalPackages.Target t=PersonalPackages.latest();long v=PersonalPackages.installedVersion(this);runOnUiThread(()->{if(!alive())return;busy=false;if(UpdateRules.newer(t.version,v))startDownload(t);else status.setText("الواجهة عندك على آخر نسخة متاحة.");});}catch(Exception e){showError(e);}});}
    private void startDownload(PersonalPackages.Target t){if(busy)return;busy=true;ready=null;install.setVisibility(View.GONE);status.setText("جارٍ تنزيل "+t.label+"…");worker.execute(()->{try{File f=PersonalPackages.download(this,t,(n,total)->runOnUiThread(()->{if(alive())status.setText("تنزيل "+t.label+": "+(total>0?(n*100/total)+"٪":(n/1024)+" KB"));}));runOnUiThread(()->{if(!alive())return;busy=false;ready=f;status.setText("الملف جاهز، وتم التحقق منه. اضغط تثبيت للمتابعة.");install.setVisibility(View.VISIBLE);install.requestFocus();});}catch(Exception e){showError(e);}});}
    private void showError(Exception e){runOnUiThread(()->{if(alive()){busy=false;status.setText("تعذّر الإكمال: "+(e.getMessage()==null?"جرّب مرة أخرى":e.getMessage()));}});}
    private void installReady(){if(ready==null)return;try{if(!getPackageManager().canRequestPackageInstalls()){
            status.setText("اسمح بالتثبيت لهذا التطبيق، ثم ارجع واضغط «تثبيت الملف الجاهز».");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;
        }PersonalPackages.install(this,ready);
        }catch(Exception e){status.setText("تعذّر فتح المثبّت. تأكد من السماح بالتثبيت لهذا التطبيق.");}}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
