package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Optional add-ons are separate apps: the user chooses each install and each shown card. */
public final class AddonCatalogActivity extends Activity {
    private LinearLayout rows;
    private int focusedIndex=1;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void attachBaseContext(Context base){
        Context localized=LauncherLocale.wrap(base);
        super.attachBaseContext(localized);
        LauncherLocale.override(this,localized);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        if(saved!=null)focusedIndex=saved.getInt("focused_addon",1);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(13,25,28));
        root.setLayoutDirection(android.text.TextUtils.getLayoutDirectionFromLocale(
                getResources().getConfiguration().getLocales().get(0)));
        ScrollView scroll=new ScrollView(this);
        FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(Math.min(dp(680),
                getResources().getDisplayMetrics().widthPixels-dp(48)),-1,Gravity.CENTER);
        size.topMargin=dp(24);size.bottomMargin=dp(24);root.addView(scroll,size);
        LinearLayout panel=(LinearLayout)getLayoutInflater().inflate(R.layout.popup_inputs,scroll,false);
        scroll.addView(panel);rows=panel.findViewById(R.id.rows);setContentView(root);
    }
    @Override protected void onResume(){super.onResume();populate();}
    private void populate(){
        int restore=focusedIndex;
        rows.removeAllViews();
        TextView title=new TextView(this);title.setText(R.string.module_cards);title.setTextColor(Color.WHITE);
        title.setTextSize(24);title.setPadding(dp(16),dp(12),dp(16),dp(12));rows.addView(title);
        add(getString(R.string.addons_installed),"",true,()->{setResult(RESULT_OK);finish();});
        try{
            for(AddonCatalog.Entry entry:AddonCatalog.load(this)){
                boolean installed=installed(entry.pkg),supported=Build.VERSION.SDK_INT>=entry.minSdk;
                String state=installed?getString(R.string.addon_open):supported?getString(R.string.addon_install)
                        :getString(R.string.addon_sdk_required);
                String note=entry.note(this);
                add(entry.label(this),state+(note.isEmpty()?"":" · "+note),supported,()->{
                    if(installed){
                        if(!PersonalPackages.openAddon(this,entry))Toast.makeText(this,
                                R.string.addon_no_activity,Toast.LENGTH_LONG).show();
                    }else startActivity(new Intent(this,HomeToolsActivity.class)
                            .putExtra("task","addon").putExtra("addon_id",entry.id));
                });
            }
        }catch(Exception failure){Toast.makeText(this,R.string.addons_failed,Toast.LENGTH_LONG).show();}
        if(rows.getChildCount()>1)rows.getChildAt(Math.max(1,Math.min(restore,rows.getChildCount()-1))).requestFocus();
    }
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("focused_addon",focusedIndex);super.onSaveInstanceState(out);}
    private boolean installed(String pkg){try{getPackageManager().getPackageInfo(pkg,0);return true;}
        catch(PackageManager.NameNotFoundException missing){return false;}}
    private void add(String title,String detail,boolean enabled,Runnable action){
        View row=getLayoutInflater().inflate(R.layout.item_input,rows,false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);row.findViewById(R.id.dot).setVisibility(View.GONE);
        ((TextView)row.findViewById(R.id.title)).setText(title);
        TextView status=row.findViewById(R.id.status);status.setText(detail);
        status.setVisibility(detail.isEmpty()?View.GONE:View.VISIBLE);
        row.setEnabled(enabled);row.setAlpha(enabled?1f:.45f);row.setOnClickListener(v->action.run());rows.addView(row);
        final int index=rows.getChildCount()-1;
        row.setOnFocusChangeListener((v,on)->{if(on)focusedIndex=index;});
    }
}
