package dev.dine.windows;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupMenu;

/* A PopupMenu on a button: Copy, a disabled Paste, a checked Bold and a More submenu with Deep. */
public class MenuActivity extends Activity {
	private static final String TAG = "windows";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FrameLayout content = new FrameLayout(this);
		final Button button = new Button(this);
		button.setText("menu");
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(100, 40, Gravity.LEFT | Gravity.TOP);
		params.leftMargin = 20;
		params.topMargin = 20;
		content.addView(button, params);
		setContentView(content);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				PopupMenu popup = new PopupMenu(MenuActivity.this, button);
				Menu menu = popup.getMenu();
				menu.add(0, 1, 0, "Copy");
				menu.add(0, 2, 0, "Paste").setEnabled(false);
				menu.add(0, 3, 0, "Bold").setCheckable(true).setChecked(true);
				menu.addSubMenu(0, 4, 0, "More").add(0, 5, 0, "Deep");
				popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item) {
						Log.i(TAG, "menu item " + item.getTitle());
						return true;
					}
				});
				popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
					public void onDismiss(PopupMenu menu) {
						Log.i(TAG, "menu dismissed");
					}
				});
				popup.show();
				Log.i(TAG, "menu shown");
			}
		});
		Log.i(TAG, "menu activity created");
	}
}
