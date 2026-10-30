package com.gathin.tvhome;

import static org.junit.Assert.*;
import android.app.Dialog;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
public class HomePlatformThemeTest {
    @Test public void screenAndAmazonUseTheSameSettingsPanelAndRows(){
        ActivityController<HomeToolsActivity> controller=Robolectric.buildActivity(HomeToolsActivity.class).setup();
        try{
            HomeToolsActivity activity=controller.get();
            LinearLayout rootRows=activity.findViewById(R.id.rows);
            for(int platform=0;platform<2;platform++){
                rootRows.getChildAt(1).performClick();
                Dialog chooser=ShadowDialog.getLatestDialog();
                LinearLayout choices=chooser.findViewById(R.id.rows);
                choices.getChildAt(platform+1).performClick();
                assertFalse(chooser.isShowing());
                Dialog controls=ShadowDialog.getLatestDialog();
                LinearLayout rows=controls.findViewById(R.id.rows);
                assertNotNull(controls.findViewById(R.id.panel).getBackground());
                assertEquals(5,rows.getChildCount());
                assertEquals(activity.getString(platform==0?R.string.home_screen_title:R.string.home_amazon_title),
                        ((TextView)rows.getChildAt(0)).getText().toString());
                int[] expected={R.string.home_enable,R.string.home_disable,R.string.home_choose,R.string.home_cancel};
                for(int i=0;i<expected.length;i++){
                    assertEquals(activity.getString(expected[i]),
                            ((TextView)rows.getChildAt(i+1).findViewById(R.id.title)).getText().toString());
                    assertTrue(rows.getChildAt(i+1).isFocusable());
                    assertNotNull(rows.getChildAt(i+1).getBackground());
                }
                rows.getChildAt(4).performClick();
                assertFalse(controls.isShowing());
            }
        }finally{controller.pause().stop().destroy();}
    }
}
