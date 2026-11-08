package dev.dine.windows;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/* The order of the windows over an activity: dialog A with a panel attached to it, then dialog B,
 * then a TYPE_TOAST window, which stays on top of both dialogs; a panel attached to the activity,
 * added last, stays under them. "raise" shows A again: it goes
 * above B with its panel; "close" dismisses A, and its panel goes with it. The dialogs aren't
 * touch-modal, so the activity's buttons take taps. */
public class OverlayActivity extends Activity {
	private static final String TAG = "windows";
	private Dialog first;

	private Dialog dialog(String text, int x, int y) {
		Dialog dialog = new Dialog(this);
		dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
		dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		dialog.getWindow().setGravity(Gravity.LEFT | Gravity.TOP);
		WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
		params.x = x;
		params.y = y;
		dialog.getWindow().setAttributes(params);
		dialog.getWindow().setLayout(300, 100);
		TextView content = new TextView(this);
		content.setText(text);
		dialog.setContentView(content);
		dialog.show();
		return dialog;
	}

	private static WindowManager.LayoutParams small(int type, int x, int y) {
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(100, 50, type, 0, PixelFormat.TRANSLUCENT);
		params.gravity = Gravity.LEFT | Gravity.TOP;
		params.x = x;
		params.y = y;
		return params;
	}

	private TextView text(String text) {
		TextView view = new TextView(this);
		view.setText(text);
		return view;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		LinearLayout buttons = new LinearLayout(this);
		buttons.setGravity(Gravity.BOTTOM);
		Button raise = new Button(this);
		raise.setText("raise");
		raise.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				first.hide();
				first.show();
				Log.i(TAG, "overlay raised");
			}
		});
		Button close = new Button(this);
		close.setText("close");
		close.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				first.dismiss();
				Log.i(TAG, "overlay closed");
			}
		});
		buttons.addView(raise);
		buttons.addView(close);
		setContentView(buttons);

		first = dialog("dialog A", 0, 0);
		WindowManager.LayoutParams panel = small(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, 10, 10);
		panel.token = first.getWindow().getDecorView().getWindowToken();
		panel.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE; // else touch-modal: it would take the activity's taps
		getWindowManager().addView(text("panel of A"), panel);
		WindowManager.LayoutParams toast = small(WindowManager.LayoutParams.TYPE_TOAST, 600, 0);
		toast.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
		getWindowManager().addView(text("toast"), toast);
		dialog("dialog B", 200, 50);
		WindowManager.LayoutParams onActivity = small(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, 400, 300);
		onActivity.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
		onActivity.token = getWindow().getDecorView().getWindowToken();
		getWindowManager().addView(text("panel of the activity"), onActivity);
		Log.i(TAG, "overlay shown");
	}
}
