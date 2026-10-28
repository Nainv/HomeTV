package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * locale: the language the launcher shows itself in, instead of the TV's. A China-market Xiaomi offers Chinese and
 * English only, so a Vietnamese owner saw the launcher in Chinese although it is translated into Vietnamese.
 * Each activity wraps its base context here; an empty or malformed tag leaves the TV's language.
 */
final class LauncherLocale {
    // Subtags after the language, so "sr-Latn" and "ar-EG-u-nu-latn" (Arabic with Western digits) pass too.
    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{1,8})*");

    private LauncherLocale() {
    }

    /** The Locale for a config tag such as "vi", "pt-BR" or "ar-EG-u-nu-latn", or null for "" or anything that is not a plain tag. */
    static Locale parse(String tag) {
        if (tag == null || !TAG.matcher(tag.trim()).matches()) return null;
        return Locale.forLanguageTag(tag.trim());
    }

    static Locale westernDigits(Locale locale) {
        return new Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build();
    }

    /** Pin the Activity resources too; some TV firmware replaces a wrapped base configuration. */
    static void override(android.app.Activity activity, Context localized) {
        Configuration override = new Configuration();
        android.os.LocaleList locales = localized.getResources().getConfiguration().getLocales();
        override.setLocales(locales);
        override.setLayoutDirection(locales.get(0));
        activity.applyOverrideConfiguration(override);
    }

    /** base with the configured language, or base itself when locale is off. */
    static Context wrap(Context base) {
        String choice = base.getSharedPreferences("home_language", 0).getString("choice", null);
        Locale locale = parse(choice == null ? Config.load(base).locale : choice);
        if (locale == null) locale = android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0);
        // Keep the selected language and direction, but use Western 0-9 in all formatters.
        locale = westernDigits(locale);
        // Formatters that take no Context (the clock's date, the lunar line) follow the default locale.
        Locale.setDefault(locale);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        android.os.LocaleList selected=new android.os.LocaleList(locale);
        android.os.LocaleList.setDefault(selected);
        c.setLocales(selected);
        return base.createConfigurationContext(c);
    }
}
