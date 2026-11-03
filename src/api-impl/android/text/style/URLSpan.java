/*
 * Portions Copyright (C) 2006 The Android Open Source Project, from
 * core/java/android/text/style at android-security-10.0.0_r75, Apache License 2.0; see
 * doc/AOSP.md.
 */
package android.text.style;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.ParcelableSpan;
import android.util.Log;
import android.view.View;

public class URLSpan extends ClickableSpan implements ParcelableSpan {
	private final String mURL;

	public URLSpan(String url) {
		mURL = url;
	}

	public String getURL() {
		return mURL;
	}

	@Override
	public void onClick(View widget) {
		Uri uri = Uri.parse(getURL());
		Context context = widget.getContext();
		Intent intent = new Intent(Intent.ACTION_VIEW, uri);
		intent.putExtra("com.android.browser.application_id", context.getPackageName()); // Browser.EXTRA_APPLICATION_ID
		try {
			context.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			Log.w("URLSpan", "Actvity was not found for intent, " + intent.toString());
		}
	}
}
