package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class WesternDigitsTest {
 @Test public void arabicClockAndDateKeepTheirWords(){assertEquals("الثلاثاء 27 أكتوبر 2026 21:05",WesternDigits.text("الثلاثاء ٢٧ أكتوبر ٢٠٢٦ ٢١:٠٥"));}
 @Test public void bothNumberingSystemsUseTheSameDigits(){assertEquals("0123456789 / 0123456789",WesternDigits.text("٠١٢٣٤٥٦٧٨٩ / ۰۱۲۳۴۵۶۷۸۹"));}
 @Test public void existingWesternTextAndPunctuationAreKept(){assertEquals("HDMI 2 · 65% · 3.5",WesternDigits.text("HDMI 2 · 65% · 3.5"));assertEquals("",WesternDigits.text(""));}
}
