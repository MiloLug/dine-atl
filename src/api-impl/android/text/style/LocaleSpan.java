/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.os.LocaleList;
import android.text.TextPaint;
import java.util.Locale;

// the text's locale; text runs carry none of it
public class LocaleSpan extends MetricAffectingSpan {
	private final LocaleList mLocales;

	public LocaleSpan(Locale locale) {
		mLocales = locale == null ? LocaleList.getEmptyLocaleList() : new LocaleList(locale);
	}

	public LocaleSpan(LocaleList locales) {
		mLocales = locales;
	}

	public Locale getLocale() {
		return mLocales.get(0);
	}

	public LocaleList getLocales() {
		return mLocales;
	}

	@Override
	public void updateDrawState(TextPaint ds) {}

	@Override
	public void updateMeasureState(TextPaint paint) {}
}
