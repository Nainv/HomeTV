package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import android.view.KeyEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Normal devices must keep native volume repeat and their TV/CEC audio routing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class VolumePassThroughTest {
    @Test public void defaultVolumeEventsStayWithTheSystem() {
        assertFalse(com.gathin.tvhome.Config.load(RuntimeEnvironment.getApplication()).consumeVolumeKeys);
        HomeKeyService home=Robolectric.buildService(HomeKeyService.class).create().get();
        InputKeyService input=Robolectric.buildService(InputKeyService.class).create().get();
        for(int key:new int[]{KeyEvent.KEYCODE_VOLUME_UP,KeyEvent.KEYCODE_VOLUME_DOWN}) {
            for(int repeat=0;repeat<4;repeat++) {
                KeyEvent event=new KeyEvent(100,100+repeat*100,KeyEvent.ACTION_DOWN,key,repeat);
                assertFalse(home.onKeyEvent(event));
                assertFalse(input.onKeyEvent(event));
            }
            KeyEvent release=new KeyEvent(100,600,KeyEvent.ACTION_UP,key,0);
            assertFalse(home.onKeyEvent(release));
            assertFalse(input.onKeyEvent(release));
        }
    }
}
