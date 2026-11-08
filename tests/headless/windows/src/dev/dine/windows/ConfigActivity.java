package dev.dine.windows;

import android.app.Activity;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/* A night change while it runs ("night" from the cli): its manifest covers uiMode, so it, its view
 * and the Application's callbacks hear it, "config: <who> night <bits>" (Android's dark mode:
 * uiMode's night bits, 32 dark, 16 light), and its tint resolves again ("color"). It holds
 * drawables from both caches, which the change prunes. */
public class ConfigActivity extends Activity {
	private static final String TAG = "windows";
	private Drawable box, tint; // cached entries stay alive

	private String color() {
		return String.format("#%08x", getResources().getColor(R.color.tint));
	}

	static int night(Configuration config) {
		return config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
	}

	static void listen(Context context) {
		context.getApplicationContext().registerComponentCallbacks(new ComponentCallbacks() {
			public void onConfigurationChanged(Configuration config) {
				Log.i(TAG, "config: application night " + night(config));
			}

			public void onLowMemory() {}
		});
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		box = getResources().getDrawable(R.drawable.box);
		tint = getResources().getDrawable(R.color.tint);
		setContentView(new View(this) {
			@Override
			protected void onConfigurationChanged(Configuration config) {
				Log.i(TAG, "config: view night " + night(config));
			}
		});
		listen(this);
		Log.i(TAG, "config: activity created color " + color());
	}

	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);
		Log.i(TAG, "config: activity night " + night(config) + " color " + color());
	}
}
