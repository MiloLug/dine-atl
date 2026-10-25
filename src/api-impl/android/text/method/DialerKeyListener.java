/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * From core/java/android/text/method/DialerKeyListener.java at android-security-10.0.0_r75,
 * modified for ATL; see doc/AOSP.md.
 */

package android.text.method;

import android.text.InputType;

/**
 * For dialing-only text entry
 * <p></p>
 * As for all implementations of {@link KeyListener}, this class is only concerned
 * with hardware keyboards.  Software input methods have no obligation to trigger
 * the methods in this class.
 */
public class DialerKeyListener extends NumberKeyListener
{
	@Override
	protected char[] getAcceptedChars()
	{
		return CHARACTERS;
	}

	public static DialerKeyListener getInstance() {
		if (sInstance != null)
			return sInstance;

		sInstance = new DialerKeyListener();
		return sInstance;
	}

	public int getInputType() {
		return InputType.TYPE_CLASS_PHONE;
	}

	/**
	 * The characters that are used.
	 *
	 * @see KeyEvent#getMatch
	 * @see #getAcceptedChars
	 */
	public static final char[] CHARACTERS = new char[] {
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '#', '*',
			'+', '-', '(', ')', ',', '/', 'N', '.', ' ', ';'
		};

	private static DialerKeyListener sInstance;
}
