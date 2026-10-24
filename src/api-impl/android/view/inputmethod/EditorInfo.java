/*
 * Portions Copyright (C) 2008 The Android Open Source Project, from
 * core/java/android/view/inputmethod/EditorInfo.java at android-security-10.0.0_r75, Apache License 2.0;
 * see doc/AOSP.md.
 */
package android.view.inputmethod;

import android.os.Bundle;
import android.os.LocaleList;
import android.text.InputType;

public class EditorInfo implements InputType {
	/* Copyright (C) 2008 The Android Open Source Project */
	public static final int IME_MASK_ACTION = 0x000000ff;
	public static final int IME_ACTION_UNSPECIFIED = 0x00000000;
	public static final int IME_ACTION_NONE = 0x00000001;
	public static final int IME_ACTION_GO = 0x00000002;
	public static final int IME_ACTION_SEARCH = 0x00000003;
	public static final int IME_ACTION_SEND = 0x00000004;
	public static final int IME_ACTION_NEXT = 0x00000005;
	public static final int IME_ACTION_DONE = 0x00000006;
	public static final int IME_ACTION_PREVIOUS = 0x00000007;
	public static final int IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000;
	public static final int IME_FLAG_NO_FULLSCREEN = 0x2000000;
	public static final int IME_FLAG_NAVIGATE_PREVIOUS = 0x4000000;
	public static final int IME_FLAG_NAVIGATE_NEXT = 0x8000000;
	public static final int IME_FLAG_NO_EXTRACT_UI = 0x10000000;
	public static final int IME_FLAG_NO_ACCESSORY_ACTION = 0x20000000;
	public static final int IME_FLAG_NO_ENTER_ACTION = 0x40000000;
	public static final int IME_FLAG_FORCE_ASCII = 0x80000000;
	public static final int IME_NULL = 0x00000000;

	public int actionId = 0;
	public CharSequence actionLabel = null;
	public Bundle extras = null;
	public int fieldId = 0;
	public String fieldName = null;
	public CharSequence hintText = null;
	public int imeOptions = 0x0;
	public int initialCapsMode = 0;
	public int initialSelStart = -1;
	public int initialSelEnd = -1;
	public int inputType = /*0x0*/ 0x00000001; /* TYPE_NULL */ /* TYPE_CLASS_TEXT */
	public CharSequence label = null;
	public String packageName = "com.example.FIXME";
	public String privateImeOptions = null;
	public LocaleList hintLocales = null;
}
