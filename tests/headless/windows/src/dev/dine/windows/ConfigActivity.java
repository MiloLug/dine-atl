package dev.dine.windows;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

/* A night change while it runs ("night" from the cli): its manifest covers uiMode, so it, its view,
 * its dialogs' views (one hidden, still added) and the Application's callbacks hear it, "config:
 * <who> night <bits>" (Android's dark mode:
 * uiMode's night bits, 32 dark, 16 light), and its tint resolves again ("color"). It holds
 * drawables from both caches, which the change prunes: loaded again, they have the new color
 * ("config: drawables"). */
public class ConfigActivity extends Activity {
	private static final String TAG = "windows";
	private Drawable box, tint; // cached entries stay alive

	private String color() {
		return String.format("#%08x", getResources().getColor(R.color.tint));
	}

	// the drawable as Resources gives it now, drawn into one pixel
	private String pixel(int id) {
		Drawable drawable = getResources().getDrawable(id);
		Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
		drawable.setBounds(0, 0, 1, 1);
		drawable.draw(new Canvas(bitmap));
		return String.format("#%08x", bitmap.getPixel(0, 0));
	}

	static int night(Configuration config) {
		return config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
	}

	// a view that logs the configuration its window gives it
	static View hearing(Context context, final String who) {
		return new View(context) {
			@Override
			protected void onConfigurationChanged(Configuration config) {
				Log.i(TAG, "config: " + who + " night " + night(config));
			}
		};
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
		Dialog shown = new Dialog(this);
		shown.setContentView(hearing(this, "dialog"));
		shown.show();
		Dialog hidden = new Dialog(this);
		hidden.setContentView(hearing(this, "hidden"));
		hidden.show();
		hidden.hide();
		Log.i(TAG, "config: activity created color " + color());
	}

	@Override
	public void onConfigurationChanged(Configuration config) {
		super.onConfigurationChanged(config);
		Log.i(TAG, "config: activity night " + night(config) + " color " + color());
		Log.i(TAG, "config: drawables box " + pixel(R.drawable.box) + " tint " + pixel(R.color.tint));
	}
}
