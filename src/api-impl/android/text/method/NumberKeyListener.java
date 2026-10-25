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
 * From core/java/android/text/method/NumberKeyListener.java at android-security-10.0.0_r75,
 * modified for ATL; see doc/AOSP.md.
 */

package android.text.method;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.icu.text.DecimalFormatSymbols;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.Collection;
import java.util.Locale;

/**
 * For numeric text entry
 * <p></p>
 * As for all implementations of {@link KeyListener}, this class is only concerned
 * with hardware keyboards.  Software input methods have no obligation to trigger
 * the methods in this class.
 */
public abstract class NumberKeyListener extends BaseKeyListener
	implements InputFilter
{
	/**
	 * You can say which characters you can accept.
	 */
	@NonNull
	protected abstract char[] getAcceptedChars();

	public CharSequence filter(CharSequence source, int start, int end,
							   Spanned dest, int dstart, int dend) {
		char[] accept = getAcceptedChars();
		boolean filter = false;

		int i;
		for (i = start; i < end; i++) {
			if (!ok(accept, source.charAt(i))) {
				break;
			}
		}

		if (i == end) {
			// It was all OK.
			return null;
		}

		if (end - start == 1) {
			// It was not OK, and there is only one char, so nothing remains.
			return "";
		}

		SpannableStringBuilder filtered =
			new SpannableStringBuilder(source, start, end);
		i -= start;
		end -= start;

		int len = end - start;
		// Only count down to i because the chars before that were all OK.
		for (int j = end - 1; j >= i; j--) {
			if (!ok(accept, source.charAt(j))) {
				filtered.delete(j, j + 1);
			}
		}

		return filtered;
	}

	protected static boolean ok(char[] accept, char c) {
		for (int i = accept.length - 1; i >= 0; i--) {
			if (accept[i] == c) {
				return true;
			}
		}

		return false;
	}

	/* package */
	@Nullable
	static boolean addDigits(@NonNull Collection<Character> collection, @Nullable Locale locale) {
		if (locale == null) {
			return false;
		}
		final String[] digits = DecimalFormatSymbols.getInstance(locale).getDigitStrings();
		for (int i = 0; i < 10; i++) {
			if (digits[i].length() > 1) { // multi-codeunit digits. Not supported.
				return false;
			}
			collection.add(Character.valueOf(digits[i].charAt(0)));
		}
		return true;
	}

	/* package */
	@NonNull
	static char[] collectionToArray(@NonNull Collection<Character> chars) {
		final char[] result = new char[chars.size()];
		int i = 0;
		for (Character ch : chars) {
			result[i++] = ch;
		}
		return result;
	}
}
