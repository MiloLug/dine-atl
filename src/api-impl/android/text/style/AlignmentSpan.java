/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style/AlignmentSpan.java at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.text.Layout;
import android.text.ParcelableSpan;

/* Copyright (C) 2006 The Android Open Source Project */
public interface AlignmentSpan extends ParagraphStyle {
	Layout.Alignment getAlignment();

	class Standard implements AlignmentSpan, ParcelableSpan {
		private final Layout.Alignment mAlignment;

		public Standard(Layout.Alignment align) {
			mAlignment = align;
		}

		@Override
		public Layout.Alignment getAlignment() {
			return mAlignment;
		}
	}
}
