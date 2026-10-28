package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Home controls and explicit download entry points. Installation remains confirmed by Android. */
public final class HomeToolsActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private File ready;
    private PersonalPackages.Target readyTarget;
    private boolean busy, waitingForInstallPermission;
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }
    private boolean alive() { return !isFinishing() && !isDestroyed(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(android.text.TextUtils.getLayoutDirectionFromLocale(
                getResources().getConfiguration().getLocales().get(0)));
        root.setBackgroundColor(Color.rgb(13, 25, 28));
        ScrollView scroll = new ScrollView(this);
        FrameLayout.LayoutParams outer = new FrameLayout.LayoutParams(
                Math.min(dp(580), getResources().getDisplayMetrics().widthPixels - dp(48)), -2, Gravity.CENTER);
        outer.topMargin = dp(18); outer.bottomMargin = dp(18);
        root.addView(scroll, outer);
        LinearLayout panel = (LinearLayout) getLayoutInflater().inflate(R.layout.popup_inputs, scroll, false);
        scroll.addView(panel);
        LinearLayout rows = panel.findViewById(R.id.rows);
        TextView heading = new TextView(this);
        heading.setText(R.string.home_settings_title);
        heading.setTextSize(24); heading.setTextColor(Color.WHITE);
        heading.setPadding(dp(16), dp(12), dp(16), dp(12));
        rows.addView(heading);
        row(rows, R.string.home_settings_title, this::chooseHomePlatform);
        row(rows, R.string.home_update, this::checkUpdate);
        row(rows, R.string.home_language, this::chooseLanguage);
        status = new TextView(this);
        status.setTextSize(15); status.setTextColor(Color.LTGRAY);
        status.setPadding(dp(16), dp(12), dp(16), dp(12));
        rows.addView(status);
        setContentView(root);
        rows.getChildAt(1).requestFocus();
        panel.setAlpha(0f); panel.setPivotY(0f); panel.setScaleY(0.92f);
        panel.setTranslationY(-dp(8));
        panel.animate().alpha(1f).scaleY(1f).translationY(0f)
                .setDuration(Config.load(this).reducedMotion ? Motion.REDUCED_MS : 220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        // Licenses remain accessible without adding another settings action.
        heading.setOnLongClickListener(v -> { showLicense(); return true; });
        String task = getIntent().getStringExtra("task");
        if ("update".equals(task)) checkUpdate();
        else if ("zikra".equals(task) || "setup".equals(task)) openZikra();
        else if ("moon".equals(task) && !PersonalPackages.openMoon(this)) startDownload(PersonalPackages.moon());
        else if ("diagnostics".equals(task)) showHomeDiagnostics();
        if (saved != null && saved.containsKey("download_url")) {
            PersonalPackages.Target target = new PersonalPackages.Target(
                    saved.getString("download_label"), saved.getString("download_pkg"),
                    saved.getString("download_url"), saved.getString("download_sha"),
                    saved.getLong("download_version"));
            startDownload(target);
        }
    }
    private void row(LinearLayout rows, int label, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(label);
        row.setOnClickListener(v -> action.run());
        rows.addView(row);
    }
    @Override protected void onResume() {
        super.onResume();
        if (waitingForInstallPermission && ready != null) {
            waitingForInstallPermission = false;
            if (android.os.Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls())
                installReady();
        }
    }
    private void chooseLanguage() {
        if (busy) return;
        String choice = getSharedPreferences("home_language", 0).getString("choice", "");
        new AlertDialog.Builder(this).setTitle(R.string.home_language)
                .setSingleChoiceItems(new String[]{getString(R.string.home_arabic),
                        getString(R.string.home_system_language)}, "ar".equals(choice) ? 0 : 1,
                        (dialog, index) -> {
                            getSharedPreferences("home_language", 0).edit()
                                    .putString("choice", index == 0 ? "ar" : "").apply();
                            dialog.dismiss(); recreate();
                        })
                .setNegativeButton(R.string.home_cancel, null).show();
    }
    private void chooseHomePlatform() {
        boolean arabic = "ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        new AlertDialog.Builder(this).setTitle(R.string.home_settings_title)
                .setItems(arabic ? new String[]{"Home على الشاشة", "Home على أمازون"}
                        : new String[]{"Home — Android TV", "Home — Amazon Fire TV"},
                        (dialog, which) -> platformControls(which == 1))
                .setNegativeButton(R.string.home_cancel, null).show();
    }
    private void platformControls(boolean amazon) {
        new AlertDialog.Builder(this).setTitle(amazon ? "Home — Amazon Fire TV" : "Home — Android TV")
                .setItems(new String[]{getString(R.string.home_enable), getString(R.string.home_disable),
                        getString(R.string.home_choose)}, (dialog, which) -> {
                    if (which == 2) { openSettings(Settings.ACTION_HOME_SETTINGS); return; }
                    if (amazon && LauncherHijackCompatService.supported()) {
                        try { LauncherHijackCompatService.setEnabled(this, which == 0); }
                        catch (RuntimeException failure) { showError(failure); }
                    } else {
                        status.setText(HomeServiceSetup.setEnabled(this, which == 0));
                    }
                }).setNegativeButton(R.string.home_cancel, null).show();
    }
    private void setHomeEnabled(boolean enable) {
        status.setText(HomeServiceSetup.setEnabled(this, enable));
        if (LauncherHijackCompatService.supported()) {
            try { LauncherHijackCompatService.setEnabled(this, enable); }
            catch (RuntimeException failure) { showError(failure); }
        }
    }
    private void openSettings(String action) {
        try { startActivity(SystemSettings.intent(this, action)); }
        catch (RuntimeException failure) { status.setText(R.string.home_unavailable); }
    }
    private void openZikra() {
        if (PersonalPackages.openZikra(this) || busy) return;
        busy = true; status.setText(getString(R.string.home_download, getString(R.string.menu_zikra)));
        worker.execute(() -> {
            try {
                PersonalPackages.Target target = PersonalPackages.zikra();
                runOnUiThread(() -> { if (alive()) { busy = false; startDownload(target); } });
            } catch (Exception failure) { showError(failure); }
        });
    }
    private void checkUpdate() {
        if (busy) return;
        busy = true; status.setText(R.string.home_checking);
        worker.execute(() -> {
            try {
                PersonalPackages.Target target = PersonalPackages.latest();
                boolean newer = UpdateRules.newer(target.version, PersonalPackages.installedVersion(this));
                runOnUiThread(() -> {
                    if (!alive()) return;
                    busy = false;
                    if (newer) startDownload(target);
                    else status.setText(R.string.home_current);
                });
            } catch (Exception failure) { showError(failure); }
        });
    }
    private void startDownload(PersonalPackages.Target target) {
        if (busy) return;
        busy = true; ready = null; readyTarget = target;
        status.setText(getString(R.string.home_download, target.label));
        worker.execute(() -> {
            try {
                File file = PersonalPackages.download(this, target, (n, total) -> runOnUiThread(() -> {
                    if (alive()) status.setText(getString(R.string.home_progress, target.label,
                            total > 0 ? (n * 100 / total) + "%" : (n / 1024) + " KB"));
                }));
                runOnUiThread(() -> {
                    if (!alive()) return;
                    busy = false; ready = file;
                    installReady();
                });
            } catch (Exception failure) { showError(failure); }
        });
    }
    private void installReady() {
        if (ready == null) return;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                status.setText(R.string.home_install_permission);
                waitingForInstallPermission = true;
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            File file = ready;
            ready = null; readyTarget = null;
            PersonalPackages.install(this, file);
        } catch (RuntimeException failure) { showError(failure); }
    }
    private void showError(Exception failure) {
        runOnUiThread(() -> {
            if (alive()) {
                busy = false;
                status.setText(getString(R.string.home_failed, String.valueOf(failure.getMessage())));
            }
        });
    }
    private void showHomeDiagnostics() {
        ScrollView scroll = new ScrollView(this);
        TextView report = new TextView(this);
        report.setText(HomeDiagnostics.report(this)); report.setTextIsSelectable(true);
        report.setPadding(dp(16), dp(12), dp(16), dp(12)); scroll.addView(report);
        new AlertDialog.Builder(this).setTitle("Home diagnostics").setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }
    private void showLicense() {
        StringBuilder text = new StringBuilder("HomeTV: GPLv3. CleanYourTV: MIT. Outfit: SIL OFL 1.1.\n");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getAssets().open("licenses/LauncherHijack-GPL-3.0.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) text.append(line).append('\n');
        } catch (java.io.IOException failure) { text.append(failure.getMessage()); }
        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this); body.setText(text); scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("GNU GPLv3").setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (readyTarget != null) {
            out.putString("download_label", readyTarget.label);
            out.putString("download_pkg", readyTarget.pkg);
            out.putString("download_url", readyTarget.url);
            out.putString("download_sha", readyTarget.sha);
            out.putLong("download_version", readyTarget.version);
        }
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
