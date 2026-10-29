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
        android.os.LocaleList locales = new android.os.LocaleList(selectedLocale(localized));
        override.setLocales(locales);
        override.setLayoutDirection(locales.get(0));
        activity.applyOverrideConfiguration(override);
    }

    /** Resolve the saved choice independently of mutable framework Resources. */
    private static Locale selectedLocale(Context base) {
        String choice = base.getSharedPreferences("home_language", 0).getString("choice", null);
        Locale locale = parse(choice == null ? Config.load(base).locale : choice);
        if (locale == null) locale = android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0);
        return westernDigits(locale);
    }

    /** Reassert only this Activity's resources after framework attachment on TV firmware. */
    @SuppressWarnings("deprecation")
    static void refresh(Context context) {
        Locale locale=selectedLocale(context);
        android.content.res.Resources resources=context.getResources();
        Configuration config=new Configuration(resources.getConfiguration());
        config.setLocales(new android.os.LocaleList(locale));
        config.setLayoutDirection(locale);
        resources.updateConfiguration(config,resources.getDisplayMetrics());
        Locale.setDefault(locale);
        android.os.LocaleList.setDefault(new android.os.LocaleList(locale));
    }

    /** base with the configured language, or the system language when the choice is empty. */
    static Context wrap(Context base) {
        Locale locale=selectedLocale(base);
        Locale.setDefault(locale);
        Configuration c = new Configuration(base.getResources().getConfiguration());
        android.os.LocaleList selected=new android.os.LocaleList(locale);
        android.os.LocaleList.setDefault(selected);
        c.setLocales(selected);
        c.setLayoutDirection(locale);
        return base.createConfigurationContext(c);
    }
}
