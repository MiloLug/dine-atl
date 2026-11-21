package dev.dine.windows;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.TextView;

/* Where the window manager puts windows: a floating dialog in the content frame, inside the bars
 * the host reports (bottom, top), a dialog of a non-floating theme in the whole window, one asking
 * for FLAG_LAYOUT_NO_LIMITS where it says, one past the window's edge moved inside it, and a popup
 * past the right and bottom edge moved inside the window (every window is 300x100 or 200x100), and
 * a window of WRAP_CONTENT height, 300x50, at the top right. */
public class PlaceActivity extends Activity {
	private TextView text(String text) {
		TextView view = new TextView(this);
		view.setText(text);
		return view;
	}

	private Dialog dialog(int theme, int gravity, int x, int y, int flags) {
		Dialog dialog = theme != 0 ? new Dialog(this, theme) : new Dialog(this);
		dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
		dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | flags);
		dialog.getWindow().setGravity(gravity);
		WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
		params.x = x;
		params.y = y;
		dialog.getWindow().setAttributes(params);
		if (theme == 0)
			dialog.getWindow().setLayout(300, 100);
		dialog.setContentView(text("place"));
		dialog.show();
		return dialog;
	}

	// a window of WRAP_CONTENT height: its content is measured against the screen less the bars, as ViewRootImpl
	// measures against screenHeightDp, whatever the content then takes
	private void wrapDialog() {
		Dialog dialog = new Dialog(this);
		dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
		dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
		dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
		dialog.getWindow().setGravity(Gravity.RIGHT | Gravity.TOP);
		dialog.getWindow().setLayout(300, WindowManager.LayoutParams.WRAP_CONTENT);
		dialog.setContentView(new View(this) {
			private String last;

			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				String line = "place probe " + MeasureSpec.getSize(widthMeasureSpec) + "x" + MeasureSpec.getSize(heightMeasureSpec);
				if (!line.equals(last))
					Log.i("windows", line);
				last = line;
				setMeasuredDimension(100, 50);
			}
		});
		dialog.show();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final TextView content = text("place activity");
		setContentView(content);
		content.post(new Runnable() {
			public void run() {
				PopupWindow popup = new PopupWindow(text("popup"), 200, 100);
				popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
				popup.showAtLocation(content, Gravity.NO_GRAVITY, 900, 500);
				Log.i("windows", "place popup");
			}
		});
		dialog(0, Gravity.BOTTOM, 0, 0, 0);
		dialog(0, Gravity.TOP, 0, 0, 0);
		dialog(android.R.style.Theme_DeviceDefault_NoActionBar, Gravity.CENTER, 0, 0, 0);
		dialog(0, Gravity.LEFT | Gravity.TOP, -50, -20, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
		dialog(0, Gravity.LEFT | Gravity.TOP, -50, -20, 0);
		wrapDialog();
		Log.i("windows", "place shown");
	}
}
