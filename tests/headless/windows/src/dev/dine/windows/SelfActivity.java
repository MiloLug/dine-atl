package dev.dine.windows;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

/* For a host without -c (gtk): it drives itself through a PopupWindow below its anchor, a
 * PopupMenu (answered from ATL_DIALOG_ANSWERS), a toast, then finishes. */
public class SelfActivity extends Activity {
	private static final String TAG = "windows";
	private final Handler handler = new Handler();
	private Button anchor;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FrameLayout content = new FrameLayout(this);
		anchor = new Button(this);
		anchor.setText("anchor");
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(100, 40, Gravity.LEFT | Gravity.TOP);
		params.leftMargin = 20;
		params.topMargin = 20;
		content.addView(anchor, params);
		setContentView(content);
		anchor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				if (anchor.getWidth() == 0)
					return;
				anchor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				handler.post(new Runnable() {
					public void run() {
						popup();
					}
				});
			}
		});
	}

	private void popup() {
		final TextView text = new TextView(this);
		text.setText("popped");
		final PopupWindow popup = new PopupWindow(text, 120, 60);
		popup.showAsDropDown(anchor);
		text.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			public void onGlobalLayout() {
				if (text.getWidth() == 0)
					return;
				text.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				int[] location = new int[2];
				text.getLocationOnScreen(location);
				Log.i(TAG, "self popup at " + location[0] + "," + location[1]);
				handler.post(new Runnable() {
					public void run() {
						popup.dismiss();
						menu();
					}
				});
			}
		});
	}

	private void menu() {
		PopupMenu menu = new PopupMenu(this, anchor);
		menu.getMenu().add(0, 1, 0, "Copy");
		menu.getMenu().add(0, 2, 0, "Paste");
		menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Log.i(TAG, "self menu item " + item.getTitle());
				return true;
			}
		});
		menu.setOnDismissListener(new PopupMenu.OnDismissListener() {
			public void onDismiss(PopupMenu menu) {
				Log.i(TAG, "self menu dismissed");
				toast();
			}
		});
		menu.show();
	}

	private void toast() {
		Toast.makeText(this, "self toast", Toast.LENGTH_SHORT).show();
		handler.postDelayed(new Runnable() {
			public void run() {
				Log.i(TAG, "self done");
				finish();
			}
		}, 2500);
	}
}
