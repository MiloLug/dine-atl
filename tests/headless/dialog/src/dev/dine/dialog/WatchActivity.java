package dev.dine.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/* A dialog under another that watches for outside touches without taking them: the first touch of
 * the window, inside the lower dialog and outside the upper one, reaches the lower dialog, and the
 * upper one's ACTION_OUTSIDE dismisses the lower dialog before its DOWN was delivered. The lower
 * dialog logs the touches it gets ("watch lower touch <action> <pointer count>"), the upper one its
 * ACTION_OUTSIDE ("watch outside"); the lower dialog's dismissal is "watch lower dismissed". */
public class WatchActivity extends Activity {
	private static final String TAG = "dialog";

	// a view of its own size, not the room a plain View takes under AT_MOST
	private View sized(final int width, final int height) {
		return new View(this) {
			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				setMeasuredDimension(width, height);
			}
		};
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(new FrameLayout(this));

		final Dialog lower = new Dialog(this) {
			@Override
			public boolean dispatchTouchEvent(MotionEvent event) {
				Log.i(TAG, "watch lower touch " + MotionEvent.actionToString(event.getAction()) + " " + event.getPointerCount());
				return super.dispatchTouchEvent(event);
			}
		};
		lower.setContentView(sized(500, 300));
		lower.setCancelable(false);
		lower.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
			public void onDismiss(android.content.DialogInterface d) {
				Log.i(TAG, "watch lower dismissed");
			}
		});
		lower.show();

		Dialog upper = new Dialog(this) {
			@Override
			public boolean onTouchEvent(MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
					Log.i(TAG, "watch outside");
					lower.dismiss();
				}
				return false;
			}
		};
		upper.setContentView(sized(100, 50));
		upper.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
		upper.show();
		Log.i(TAG, "watch shown");
	}
}
