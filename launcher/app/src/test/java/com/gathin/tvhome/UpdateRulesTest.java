package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdateRulesTest {
 private static final String BASE="https://updates.example", SHA="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
 @Test public void onlyPinnedEndpointIsAccepted(){assertTrue(UpdateRules.valid(BASE,BASE+"/tv.apk",SHA,10));assertFalse(UpdateRules.valid(BASE,"http://updates.example/tv.apk",SHA,10));assertFalse(UpdateRules.valid(BASE,"https://updates.example.attacker.test/tv.apk",SHA,10));assertFalse(UpdateRules.valid(BASE,BASE+"/other.apk",SHA,10));}
 @Test public void incompleteDigestAndInvalidVersionAreRejected(){assertFalse(UpdateRules.valid(BASE,BASE+"/tv.apk","bad",10));assertFalse(UpdateRules.valid(BASE,BASE+"/tv.apk",SHA,0));assertFalse(UpdateRules.valid(BASE,BASE+"/tv.apk",SHA,Long.MAX_VALUE));}
 @Test public void sameAndOlderVersionsDoNotTriggerUpdate(){assertFalse(UpdateRules.newer(10,10));assertFalse(UpdateRules.newer(9,10));assertTrue(UpdateRules.newer(11,10));}
}
