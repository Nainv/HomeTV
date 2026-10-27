package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppNamesTest {
 @Test public void realAppNameWinsOverTechnicalActivity(){assertEquals("ذِكرى",AppNames.choose("ذِكرى","MainActivity","com.zikra.tv"));}
 @Test public void missingAppLabelUsesActivity(){assertEquals("YouTube",AppNames.choose(null,"YouTube","com.google.android.youtube.tv"));assertEquals("YouTube",AppNames.choose("com.google.android.youtube.tv","YouTube","com.google.android.youtube.tv"));}
 @Test public void missingNamesFallBackToPackage(){assertEquals("com.example",AppNames.choose("null","  ","com.example"));}
}
