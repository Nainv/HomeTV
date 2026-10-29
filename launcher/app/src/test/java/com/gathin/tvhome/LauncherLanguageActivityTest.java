package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import android.app.Application;
import android.content.res.Resources;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Exercise real Activity resources, not only parsing the preference tag. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, qualifiers="de-rDE")
public class LauncherLanguageActivityTest {
    private Application app(){return RuntimeEnvironment.getApplication();}
    @After public void clearChoice(){app().getSharedPreferences("home_language",0).edit().clear().commit();}
    @Test public void arabicOverridesGermanSystemAndSurvivesRecreation(){
        app().getSharedPreferences("home_language",0).edit().putString("choice","ar").commit();
        ActivityController<HomeToolsActivity> controller=Robolectric.buildActivity(HomeToolsActivity.class).setup();
        try{
            assertEquals("ar",controller.get().getResources().getConfiguration().getLocales().get(0).getLanguage());
            assertEquals("تحديث واجهة Home",controller.get().getString(R.string.home_update));
            controller.recreate();
            assertEquals("تحديث واجهة Home",controller.get().getString(R.string.home_update));
        }finally{controller.pause().stop().destroy();}
    }
    @Test public void choosingSystemAgainUsesTheDeviceLanguage(){
        String system=Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
        app().getSharedPreferences("home_language",0).edit().putString("choice","ar").commit();
        ActivityController<HomeToolsActivity> controller=Robolectric.buildActivity(HomeToolsActivity.class).setup();
        try{
            app().getSharedPreferences("home_language",0).edit().putString("choice","").commit();
            controller.recreate();
            assertEquals(system,controller.get().getResources().getConfiguration().getLocales().get(0).getLanguage());
        }finally{controller.pause().stop().destroy();}
    }
}
