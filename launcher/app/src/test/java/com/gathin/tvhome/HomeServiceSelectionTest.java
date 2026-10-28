package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class HomeServiceSelectionTest {
    private static final String FULL = "com.gathin.tvhome/com.gathin.tvhome.HomeKeyService";
    private static final String SHORT = "com.gathin.tvhome/.HomeKeyService";
    private static final String OTHER = "reader.app/.ScreenReader";
    @Test public void enablingPreservesScreenReaderAndOrder() {
        assertEquals(OTHER + ":remote.app/.Helper:" + FULL,
                HomeServiceSelection.update(OTHER + ":remote.app/.Helper", FULL, SHORT, true));
    }
    @Test public void disablingRemovesOnlyOurAliases() {
        assertEquals(OTHER + ":remote.app/.Helper",
                HomeServiceSelection.update(SHORT + ":" + OTHER + ":" + FULL + ":remote.app/.Helper",
                        FULL, SHORT, false));
    }
    @Test public void enablingIsIdempotentAndNormalizesOwnAlias() {
        String first = HomeServiceSelection.update(OTHER + ":" + SHORT, FULL, SHORT, true);
        assertEquals(OTHER + ":" + FULL, first);
        assertEquals(first, HomeServiceSelection.update(first, FULL, SHORT, true));
    }
    @Test public void freshInstallAndLastServiceRemoval() {
        assertEquals(FULL, HomeServiceSelection.update(null, FULL, SHORT, true));
        assertEquals("", HomeServiceSelection.update(FULL, FULL, SHORT, false));
    }
}
