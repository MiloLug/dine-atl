package dev.dine.nav;

import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/* Asks to run under the status bar with the navigation bar hidden, sticky immersive; logs what its
 * views hear of the flags: "nav: I ui <flags>". An orange band at the top and a teal one at the
 * bottom show by eye where the window reaches. */
public class ImmersiveActivity extends LoggingActivity {
	public ImmersiveActivity() {
		super("I");
	}

	private View band(int color, int gravity) {
		View band = new View(this);
		band.setBackgroundColor(color);
		int height = (int)(64 * getResources().getDisplayMetrics().density);
		band.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, gravity));
		return band;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		((ViewGroup)content.getParent()).removeView(content);
		FrameLayout frame = new FrameLayout(this);
		frame.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		frame.addView(band(0xffff9800, Gravity.TOP));
		frame.addView(band(0xff009688, Gravity.BOTTOM));
		setContentView(frame);
		content.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
			public void onSystemUiVisibilityChange(int visibility) {
				Log.i("nav", "nav: I ui " + visibility);
			}
		});
		content.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
	}
}
