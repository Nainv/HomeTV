package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomePressSequenceTest {
    @Test public void tripleReturnsStockThenNextPressReturnsNewHome() {
        HomePressSequence taps = new HomePressSequence();
        assertFalse(taps.press(100)); assertFalse(taps.press(450)); assertTrue(taps.press(800));
        assertFalse(taps.press(1050));
    }
    @Test public void slowPressesDoNotAccumulate() {
        HomePressSequence taps = new HomePressSequence();
        assertFalse(taps.press(100)); assertFalse(taps.press(900)); assertFalse(taps.press(1700));
    }
    @Test public void wakeResetAndClockResetDiscardOldSequence() {
        HomePressSequence taps = new HomePressSequence();
        taps.press(100); taps.press(300); taps.reset(); assertFalse(taps.press(400));
        assertFalse(taps.press(10)); assertFalse(taps.press(100)); assertTrue(taps.press(200));
    }
}
