/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.text.TextPaint;

public abstract class MetricAffectingSpan extends CharacterStyle implements UpdateLayout {
	public abstract void updateMeasureState(TextPaint p);

	@Override
	public MetricAffectingSpan getUnderlying() {
		return this;
	}

	static class Passthrough extends MetricAffectingSpan {
		private MetricAffectingSpan mStyle;

		Passthrough(MetricAffectingSpan cs) {
			mStyle = cs;
		}

		@Override
		public void updateDrawState(TextPaint tp) {
			mStyle.updateDrawState(tp);
		}

		@Override
		public void updateMeasureState(TextPaint tp) {
			mStyle.updateMeasureState(tp);
		}

		@Override
		public MetricAffectingSpan getUnderlying() {
			return mStyle.getUnderlying();
		}
	}
}
