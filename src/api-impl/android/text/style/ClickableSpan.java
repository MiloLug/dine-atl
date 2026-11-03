/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.text.TextPaint;
import android.view.View;

// drawn as a link; ATL's text views don't route taps to it (general-todo ATL-49)
public abstract class ClickableSpan extends CharacterStyle implements UpdateAppearance {
	public abstract void onClick(View widget);

	@Override
	public void updateDrawState(TextPaint ds) {
		ds.setColor(ds.linkColor);
		ds.setUnderlineText(true);
	}
}
