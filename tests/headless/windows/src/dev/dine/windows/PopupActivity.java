package dev.dine.windows;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

/* A PopupWindow below its anchor: its rows take taps, a tap outside it (it watches outside
 * touches, without taking them) closes it and still reaches the activity. An AutoCompleteTextView
 * at 300,20: typing shows its drop-down, Back closes only the drop-down, a row fills the field.
 * "menu" opens a PopupMenu, "toast" shows a text toast, "bye" shows one and finishes. */
public class PopupActivity extends Activity {
	private static final String TAG = "windows";
	private PopupWindow popup;

	private TextView row(final String text) {
		TextView row = new TextView(this);
		row.setText(text);
		row.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				Log.i(TAG, "popup row " + text);
			}
		});
		return row;
	}

	private Button button(FrameLayout content, String text, int left, int top) {
		Button button = new Button(this);
		button.setText(text);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(100, 40, Gravity.LEFT | Gravity.TOP);
		params.leftMargin = left;
		params.topMargin = top;
		content.addView(button, params);
		return button;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FrameLayout content = new FrameLayout(this);
		final Button anchor = new Button(this);
		anchor.setText("anchor");
		FrameLayout.LayoutParams anchorParams = new FrameLayout.LayoutParams(100, 40, Gravity.LEFT | Gravity.TOP);
		anchorParams.leftMargin = 20;
		anchorParams.topMargin = 20;
		content.addView(anchor, anchorParams);
		TextView elsewhere = new TextView(this);
		elsewhere.setText("elsewhere");
		elsewhere.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				Log.i(TAG, "popup elsewhere");
			}
		});
		content.addView(elsewhere, new FrameLayout.LayoutParams(200, 40, Gravity.LEFT | Gravity.BOTTOM));
		AutoCompleteTextView field = new AutoCompleteTextView(this) {
			@Override
			public void showDropDown() {
				super.showDropDown();
				Log.i(TAG, "actv dropdown shown");
			}
		};
		field.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, new String[] {"apple", "apricot", "banana"}));
		field.setThreshold(2);
		field.setHint("fruit");
		field.setOnDismissListener(new AutoCompleteTextView.OnDismissListener() {
			public void onDismiss() {
				Log.i(TAG, "actv dropdown dismissed");
			}
		});
		field.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
			public void afterTextChanged(Editable s) {
				Log.i(TAG, "actv text " + s);
			}
		});
		FrameLayout.LayoutParams fieldParams = new FrameLayout.LayoutParams(200, 40, Gravity.LEFT | Gravity.TOP);
		fieldParams.leftMargin = 300;
		fieldParams.topMargin = 20;
		content.addView(field, fieldParams);
		final Button menuButton = button(content, "menu", 20, 200);
		menuButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				PopupMenu popup = new PopupMenu(PopupActivity.this, menuButton);
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
			}
		});
		button(content, "toast", 20, 260).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				Toast.makeText(PopupActivity.this, "toasted", Toast.LENGTH_LONG).show();
			}
		});
		button(content, "bye", 20, 320).setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				Toast.makeText(PopupActivity.this, "bye", Toast.LENGTH_LONG).show();
				finish();
			}
		});
		setContentView(content);

		LinearLayout rows = new LinearLayout(this);
		rows.setOrientation(LinearLayout.VERTICAL);
		rows.addView(row("one"));
		rows.addView(row("two"));
		popup = new PopupWindow(rows, 200, 100);
		popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
		popup.setOutsideTouchable(true);
		popup.setOnDismissListener(new PopupWindow.OnDismissListener() {
			public void onDismiss() {
				Log.i(TAG, "popup dismissed");
			}
		});
		anchor.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				popup.showAsDropDown(anchor);
				Log.i(TAG, "popup shown");
			}
		});
		Log.i(TAG, "popup activity created");
	}
}
