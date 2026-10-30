package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class SystemSettingsFallbackTest {
    @Test public void missingSettingsActionCanOpenInstalledAospTvSettings(){
        Application app=RuntimeEnvironment.getApplication();
        PackageInfo pkg=new PackageInfo();pkg.packageName="com.android.tv.settings";
        pkg.applicationInfo=new ApplicationInfo();pkg.applicationInfo.packageName=pkg.packageName;
        Shadows.shadowOf(app.getPackageManager()).installPackage(pkg);
        Intent target=SystemSettings.intent(app,Settings.ACTION_SETTINGS);
        assertNotNull(target.getComponent());
        assertEquals("com.android.tv.settings",target.getComponent().getPackageName());
        assertEquals("com.android.tv.settings.MainSettings",target.getComponent().getClassName());
    }
    @Test public void availableAccessibilityConsentPageIsNeverReplacedByGeneralSettings(){
        Application app=RuntimeEnvironment.getApplication();
        Intent expected=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        ResolveInfo resolved=new ResolveInfo();resolved.activityInfo=new ActivityInfo();
        resolved.activityInfo.packageName="android.settings";
        resolved.activityInfo.name="android.settings.AccessibilitySettings";
        Shadows.shadowOf(app.getPackageManager()).addResolveInfoForIntent(expected,resolved);
        Intent actual=SystemSettings.intent(app,Settings.ACTION_ACCESSIBILITY_SETTINGS);
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS,actual.getAction());
        assertNull(actual.getComponent());
    }
}
