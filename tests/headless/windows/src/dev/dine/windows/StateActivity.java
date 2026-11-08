package dev.dine.windows;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.Collections;

/* A dialog's window state reaches the host as the activity's does: it keeps the screen on, sets
 * the brightness and excludes a strip of the left edge, all from its onCreate before its show. Its
 * window, 200x100 px at the left edge, 100 px down, takes the part of the insets it overlaps. */
public class StateActivity extends Activity {
	private static final String TAG = "windows";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView text = new TextView(this);
		text.setText("state");
		setContentView(text);
		Dialog dialog = new Dialog(this) {
			@Override
			protected void onCreate(Bundle savedInstanceState) {
				getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				WindowManager.LayoutParams params = getWindow().getAttributes();
				params.screenBrightness = 0.3f;
				params.gravity = Gravity.LEFT | Gravity.TOP;
				params.x = 0;
				params.y = 100;
				getWindow().setAttributes(params);
				getWindow().setLayout(200, 100);
				View content = new View(getContext());
				content.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, 200, 100)));
				content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
					public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
						android.graphics.Insets gestures = insets.getSystemGestureInsets();
						Log.i(TAG, "state insets " + gestures.left + " " + gestures.top + " " + gestures.right + " " + gestures.bottom);
						return insets;
					}
				});
				content.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
					boolean logged;
					public void onGlobalLayout() {
						if (!logged)
							Log.i(TAG, "state dialog laid out");
						logged = true;
					}
				});
				setContentView(content);
			}
		};
		dialog.show();
		Log.i(TAG, "state dialog shown");
	}
}
