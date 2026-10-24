/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/widget/RadioButton.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class RadioButton extends CompoundButton {

	public RadioButton(Context context) {
		super(context);
	}

	public RadioButton(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

	@Override
	int kind() {
		return KIND_RADIO;
	}

	/* Copyright (C) 2006 The Android Open Source Project */
	@Override
	public void toggle() {
		// we override to prevent toggle when the radio is already
		// checked (as opposed to check boxes widgets)
		if (!isChecked()) {
			super.toggle();
		}
	}
}
