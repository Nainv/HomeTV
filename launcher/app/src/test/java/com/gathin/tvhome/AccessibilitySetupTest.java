package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class AccessibilitySetupTest {
    public static class SettingsDevice extends Activity {
        final List<Intent> attempts=new ArrayList<>();
        String available;
        boolean denyStandard;
        @Override public void startActivity(Intent intent){
            attempts.add(new Intent(intent));
            if(denyStandard && Settings.ACTION_ACCESSIBILITY_SETTINGS.equals(intent.getAction()))throw new SecurityException();
            if(!java.util.Objects.equals(available,intent.getAction()) || available==null)throw new ActivityNotFoundException();
        }
    }
    @Test public void workingAccessibilityRouteIsNotChanged(){
        ActivityController<SettingsDevice> controller=Robolectric.buildActivity(SettingsDevice.class).setup();
        try{
            SettingsDevice device=controller.get();device.available=Settings.ACTION_ACCESSIBILITY_SETTINGS;
            assertEquals(AccessibilitySetup.Result.ACCESSIBILITY_PAGE,AccessibilitySetup.open(device));
            assertEquals(1,device.attempts.size());
        }finally{controller.pause().stop().destroy();}
    }
    @Test public void detailsFallbackTargetsOurServiceUsingAndroidStringExtra(){
        ActivityController<SettingsDevice> controller=Robolectric.buildActivity(SettingsDevice.class).setup();
        try{
            SettingsDevice device=controller.get();device.available=AccessibilitySetup.DETAILS;
            assertEquals(AccessibilitySetup.Result.ACCESSIBILITY_PAGE,AccessibilitySetup.open(device));
            assertEquals(2,device.attempts.size());
            assertEquals(new ComponentName(device,HomeKeyService.class).flattenToString(),
                    device.attempts.get(1).getStringExtra(AccessibilitySetup.COMPONENT_EXTRA));
        }finally{controller.pause().stop().destroy();}
    }
    @Test public void inaccessibleSpecialPageFallsBackToSettingsWithoutEnablingAnything(){
        ActivityController<SettingsDevice> controller=Robolectric.buildActivity(SettingsDevice.class).setup();
        try{
            SettingsDevice device=controller.get();device.available=Settings.ACTION_SETTINGS;device.denyStandard=true;
            Settings.Secure.putInt(device.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0);
            Settings.Secure.putString(device.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"reader.app/.Reader");
            assertEquals(AccessibilitySetup.Result.GENERAL_SETTINGS,AccessibilitySetup.open(device));
            assertEquals(3,device.attempts.size());
            assertEquals(0,Settings.Secure.getInt(device.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0));
            assertEquals("reader.app/.Reader",Settings.Secure.getString(device.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
        }finally{controller.pause().stop().destroy();}
    }
    @Test public void noSupportedPageReturnsUnavailableAndRecordsFailure(){
        ActivityController<SettingsDevice> controller=Robolectric.buildActivity(SettingsDevice.class).setup();
        try{
            SettingsDevice device=controller.get();
            assertEquals(AccessibilitySetup.Result.UNAVAILABLE,AccessibilitySetup.open(device));
            assertFalse(device.getSharedPreferences("home_settings_route",0).getString("result","").isEmpty());
        }finally{controller.pause().stop().destroy();}
    }
}
