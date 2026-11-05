/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * opengl/java/android/opengl/GLUtils.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.opengl;
import android.graphics.Bitmap;

public class GLUtils {
	/* Copyright (C) 2006 The Android Open Source Project */
	public static void texImage2D(int target, int level, Bitmap bitmap, int border) {
		if (bitmap == null) {
			throw new NullPointerException("texImage2D can't be used with a null Bitmap");
		}
		if (bitmap.isRecycled()) {
			throw new IllegalArgumentException("bitmap is recycled");
		}
		if (native_texImage2D(target, level, -1, bitmap, -1, border) != 0) {
			throw new IllegalArgumentException("invalid Bitmap format");
		}
	}

	private static native int native_texImage2D(int target, int level, int internalformat,
	                                            Bitmap bitmap, int type, int border);
}
