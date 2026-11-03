/*
 * Portions Copyright (C) 2007 The Android Open Source Project, from
 * core/java/android/content/ActivityNotFoundException.java at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.content;

/* Copyright (C) 2007 The Android Open Source Project */
public class ActivityNotFoundException extends RuntimeException {
	public ActivityNotFoundException() {
		super();
	}

	public ActivityNotFoundException(String name) {
		super(name);
	}
}
