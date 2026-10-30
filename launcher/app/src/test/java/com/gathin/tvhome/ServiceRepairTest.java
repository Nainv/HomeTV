package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.Manifest;
import android.app.Application;
import android.content.ComponentName;
import android.provider.Settings;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ServiceRepairTest {
    private Application app;
    private static final String OTHER="screen.reader/.Service";
    @Before public void setup() throws Exception {
        finishRepair();
        app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("accessibility_helpers",0).edit().clear().commit();
        Settings.Secure.putString(app.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,OTHER);
        Settings.Secure.putInt(app.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,1);
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
    }
    @After public void cleanup() throws Exception {finishRepair();}
    private static void finishRepair() throws InterruptedException {
        for(Thread t:Thread.getAllStackTraces().keySet())
            if(t.getName().equals("ServiceRepair"))t.join(5000);
    }
    private String selected(){return Settings.Secure.getString(app.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);}
    @Test public void restorationDefaultIsEnabled(){
        assertTrue(com.gathin.tvhome.Config.load(app).restoreKeyServices);
    }
    @Test public void previouslyConnectedServiceRestoresWithoutRemovingOtherServices() throws Exception {
        ServiceRepair.connected(app,HomeKeyService.class);
        ServiceRepair.restore(app);finishRepair();
        String own=new ComponentName(app,HomeKeyService.class).flattenToShortString();
        assertEquals(OTHER+":"+own,selected());
        assertEquals(1,Settings.Secure.getInt(app.getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED,0));
    }
    @Test public void missingPermissionNeverWritesSettings() throws Exception {
        ServiceRepair.connected(app,HomeKeyService.class);
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.WRITE_SECURE_SETTINGS);
        ServiceRepair.restore(app);finishRepair();assertEquals(OTHER,selected());
    }
    @Test public void explicitStopBeforeRestoreStaysOff() throws Exception {
        ServiceRepair.connected(app,HomeKeyService.class);
        ServiceRepair.stopRequested(app,HomeKeyService.class);
        ServiceRepair.restore(app);finishRepair();assertEquals(OTHER,selected());
    }
    @Test public void explicitStopCancelsAnAlreadyRunningRestore() throws Exception {
        ServiceRepair.connected(app,HomeKeyService.class);
        ServiceRepair.restore(app);
        for(int attempt=0;attempt<100 && OTHER.equals(selected());attempt++)Thread.sleep(10);
        assertNotEquals(OTHER,selected());
        assertEquals(app.getString(R.string.home_disabled),HomeServiceSetup.setEnabled(app,false));
        finishRepair();assertEquals(OTHER,selected());
    }
}
